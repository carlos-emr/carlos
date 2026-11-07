#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Compile real unchanged clients and prove the API oracle detects their breaks."""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile

import release_verification as gate
from junit_evidence import EvidenceError

spec = importlib.util.spec_from_file_location('wire', Path(__file__).with_name('run-main-release-wire-compatibility.py'))
wire = importlib.util.module_from_spec(spec); spec.loader.exec_module(wire)

BEFORE = '''package fake;
public abstract class Contract {
 static { if (System.nanoTime()!=0) throw new IllegalStateException("MUST_NOT_INITIALIZE"); }
 public static final String VALUE="FAKE-before";
 protected Contract(String x) {}
 protected String protectedRead() { return "FAKE"; }
 public abstract void save();
 public String read(String x) { return x; }
 public enum Choice { OLD }
 protected static class Extension { public Extension() {} public String value() { return "FAKE"; } }
}
'''
AFTER = BEFORE.replace('FAKE-before', 'FAKE-after').replace(
    'protected String protectedRead()', 'private String protectedRead()').replace(
    'protected static class Extension', 'private static class Extension').replace(
    'public abstract void save();', 'public abstract void save(); public abstract void flush();').replace(
    'public enum Choice { OLD }', 'public String read(Integer x) { return "FAKE"; } public enum Choice { OLD, NEW }')
CLIENTS = {
    'ProtectedNestedClient': '''package client; public abstract class ProtectedNestedClient extends fake.Contract {
      protected ProtectedNestedClient() { super("FAKE"); }
      public String use() { return new Extension().value(); }
    }''',
    'SubclassClient': '''package fake; public final class SubclassClient extends Contract {
      public SubclassClient() { super("FAKE"); } public void save() {}
    }''',
    'ProtectedClient': '''package fake; public abstract class ProtectedClient extends Contract {
      protected ProtectedClient() { super("FAKE"); }
      public String use() { return protectedRead(); }
    }''',
    'OverloadClient': '''package fake; public final class OverloadClient {
      public static String use(Contract value) { return value.read(null); }
    }''',
    'EnumClient': '''package fake; public final class EnumClient {
      public static String use(Contract.Choice value) { return switch(value) { case OLD -> "FAKE"; }; }
    }''',
}
PROBE = '''import java.nio.file.*; import java.net.*;
public final class ApiFixtureProbe {
 public static void main(String[] args) throws Exception {
  var urls=new java.util.ArrayList<URL>(); urls.add(Path.of(args[0]).toUri().toURL());
  if(args.length==5) urls.add(Path.of(args[3]).toUri().toURL());
  try (var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())) {
   var method=WarWireCompatibilityProbe.class.getDeclaredMethod("publicApi",ClassLoader.class,Path.class);
   method.setAccessible(true);
   Files.writeString(Path.of(args[2]),(String)method.invoke(null,loader,Path.of(args[1])));
   if(args.length==5) Files.writeString(Path.of(args[4]),
       (String)loader.loadClass("fake.ConstantClient").getMethod("value").invoke(null));
  }
 }
}'''


def compile_java(paths, destination, log, classpath=None):
    destination.mkdir()
    return subprocess.run(['javac', '-d', str(destination)]
        + (['-cp', str(classpath)] if classpath else []) + list(map(str, paths)),
        stdout=log, stderr=log, timeout=30).returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assertions = {}
    observations = {}
    client_compilation = []
    with tempfile.TemporaryDirectory(prefix='api-oracle-', dir=args.output.parent) as temporary:
        root = Path(temporary)
        with (args.output.parent / 'private-api-oracle.log').open('xb') as log:
            helper = root / 'ApiFixtureProbe.java'; helper.write_text(PROBE)
            tooling = root / 'tooling'
            if compile_java([Path(__file__).with_name('WarWireCompatibilityProbe.java'), helper], tooling, log):
                raise EvidenceError('API oracle tooling did not compile')
            inventory = root / 'types.txt'; inventory.write_text('fake.Contract\n')
            for side, source in [('main', BEFORE), ('release', AFTER)]:
                directory = root / side; directory.mkdir()
                definition = directory / 'Contract.java'; definition.write_text(source)
                classes = directory / 'classes'
                if compile_java([definition], classes, log):
                    raise EvidenceError('API oracle synthetic contract did not compile')
                output = root / (side + '.json')
                inlined = root / 'old-client-constant.txt'
                child = subprocess.run(['java', '-cp', str(tooling), 'ApiFixtureProbe', str(classes),
                    str(inventory), str(output)] + ([str(root / 'main/ConstantClient'), str(inlined)]
                    if side == 'release' else []), stdout=log, stderr=log, timeout=30)
                if child.returncode:
                    raise EvidenceError('API oracle fixture probe failed or initialized its application class')
                observations[side] = gate.read_json(output)
                if any(member.startswith('unresolved-api:') for members in observations[side].values() for member in members):
                    raise EvidenceError('API oracle fixture inventory is incomplete')
                if side == 'main':
                    constant_client = directory / 'ConstantClient.java'
                    constant_client.write_text('package fake; public class ConstantClient { public static String value() { return Contract.VALUE; } }')
                    if compile_java([constant_client], directory / 'ConstantClient', log, classes):
                        raise EvidenceError('Unchanged constant client did not compile')
                else:
                    assertions['old-compiled-constant-remains-inlined'] = inlined.read_text() == 'FAKE-before'
                for name, code in CLIENTS.items():
                    client = directory / (name + '.java'); client.write_text(code)
                    code = compile_java([client], directory / name, log, classes)
                    client_compilation.append({'side': side, 'client': name, 'exitCode': code})
                    assertions[name.lower() + '-real-client-break'] = (
                        code == 0 if side == 'main' else assertions[name.lower() + '-real-client-break'] and code == 1)
    old, new = observations['main'], observations['release']
    removed = set(old['fake.Contract']) - set(new['fake.Contract'])
    additions = wire.risky_additions(old['fake.Contract'], new['fake.Contract'])
    enum_additions = wire.risky_additions(old['fake.Contract$Choice'], new['fake.Contract$Choice'])
    assertions.update({
        'protected-contract-removal-detected': any('protectedRead' in member for member in removed),
        'protected-nested-type-removal-detected': bool(old.get('fake.Contract$Extension'))
            and 'fake.Contract$Extension' not in new,
        'abstract-addition-detected': any('flush' in member for member in additions),
        'overload-addition-detected': any('read(java.lang.Integer)' in member for member in additions),
        'enum-addition-detected': 'enum-constant:NEW' in enum_additions,
        'constant-value-change-detected-without-initialization': any(member.startswith('constant:')
            and 'fake.Contract.VALUE:' in member for member in removed),
    })
    details = args.output.parent / 'api-oracle-observations.json'
    gate.write_json(details, {'schemaVersion': 1, 'observations': observations, 'clientCompilation': client_compilation})
    gate.write_json(args.output, {'schemaVersion': 1, 'assertions': assertions,
        'inputs': [{'path': details.name, 'sha256': gate.digest(details)}]})
    return 0 if all(assertions.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
