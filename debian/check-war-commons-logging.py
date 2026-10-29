#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Refuse a CARLOS WAR that ships more than one Commons Logging implementation.

CARLOS routes Commons Logging (JCL) through commons-logging:commons-logging,
whose LogFactory binds to the Log4j 2 backend (pom.xml, Logging section;
issue #5413). org.slf4j:jcl-over-slf4j once reached the WAR through CXF next
to it: both jars define org.apache.commons.logging.*, and the bridge also
registers a LogFactory service that reroutes JCL through SLF4J, so which
implementation won was left to Tomcat's class-loader order.

The Maven build bans the known duplicates (maven-enforcer-plugin), and
CommonsLoggingRoutingUnitTest checks the test classpath. This checks the
artifact that is actually installed, which also covers a prebuilt CARLOS_WAR
and a library that shades its own copy of the classes. It fails when:

  * no jar, or more than one jar or WEB-INF/classes, defines a class in
    org.apache.commons.logging (multi-release META-INF/versions/N/ copies
    included);
  * the one definer is not WEB-INF/lib/commons-logging-<version>.jar, or is
    one without Log4jApiLogFactory (commons-logging before 1.3 cannot bind
    to the Log4j API, so JCL would not reach Log4j 2);
  * anything carries META-INF/services/org.apache.commons.logging.LogFactory
    or commons-logging.properties, which would override LogFactory discovery.

Versioned copies count whether or not their jar declares Multi-Release: the
rule is that the package ships no second definition, and a manifest edit is
all it takes to activate one.

Only jars directly in WEB-INF/lib are read: that is all Tomcat puts on the web
application's class path. Each is read as a stream from the WAR, so memory
stays bounded by the largest central directory, not the largest jar.

Usage: check-war-commons-logging.py <carlos.war>
Exit status: 0 clean, 1 the WAR breaks the rule, 2 the WAR cannot be read.
"""
import re
import sys
import zipfile
from pathlib import PurePosixPath

PROG = "check-war-commons-logging"
JCL_PACKAGE = "org/apache/commons/logging/"
LOG_FACTORY = JCL_PACKAGE + "LogFactory.class"
# Added in commons-logging 1.3.0; the factory discovery binds to reach Log4j 2.
LOG4J_API_FACTORY = JCL_PACKAGE + "impl/Log4jApiLogFactory.class"
MULTI_RELEASE = re.compile(r"^META-INF/versions/[0-9]+/")
DISCOVERY_OVERRIDES = (
    "META-INF/services/org.apache.commons.logging.LogFactory",
    "commons-logging.properties",
)
LIB = PurePosixPath("WEB-INF/lib")
CLASSES = "WEB-INF/classes/"
# commons-logging-1.4.0.jar, not commons-logging-api-1.1.jar (the old split API)
EXPECTED_JAR = re.compile(r"^commons-logging-[0-9][0-9A-Za-z.\-]*\.jar$")


class Unreadable(Exception):
    """The WAR, or a jar inside it, is not a readable zip archive."""


# What zipfile raises for input it cannot read: a damaged archive (BadZipFile,
# OSError, EOFError), an encrypted member (RuntimeError) or an unsupported
# compression method (NotImplementedError). Each means the WAR cannot be
# verified, which must stop the build rather than escape as a traceback.
UNREADABLE = (zipfile.BadZipFile, OSError, EOFError, RuntimeError, NotImplementedError)


def scan_names(names):
    """Split class-path entry names into JCL class definitions and discovery overrides."""
    definitions, overrides = [], []
    for name in names:
        unversioned = MULTI_RELEASE.sub("", name)
        if unversioned.startswith(JCL_PACKAGE) and unversioned.endswith(".class"):
            definitions.append(name)
        elif name in DISCOVERY_OVERRIDES:
            overrides.append(name)
    return definitions, overrides


def scan_war(war_path):
    """Map each class-path source in the WAR to its (definitions, overrides)."""
    sources = {}
    try:
        with zipfile.ZipFile(war_path) as war:
            names = war.namelist()
            loose = [n[len(CLASSES):] for n in names if n.startswith(CLASSES)]
            sources["WEB-INF/classes"] = scan_names(loose)
            for name in sorted(set(names)):
                path = PurePosixPath(name)
                if path.parent != LIB or not path.name.lower().endswith(".jar"):
                    continue
                try:
                    # A seekable stream over the member: ZipFile reads only the
                    # jar's central directory instead of inflating it into memory.
                    with war.open(name) as member, zipfile.ZipFile(member) as jar:
                        sources[name] = scan_names(jar.namelist())
                except UNREADABLE as exc:
                    raise Unreadable(f"{name} is not a readable jar: {exc}") from exc
    except UNREADABLE as exc:
        raise Unreadable(f"{war_path} is not a readable WAR: {exc}") from exc
    return sources


def violations(sources):
    """Return (problems, the single compliant definer or None)."""
    problems = []
    definers = sorted(s for s, (defs, _) in sources.items() if defs)
    for source, (_, overrides) in sorted(sources.items()):
        for entry in overrides:
            problems.append(
                f"{source} carries {entry}, which overrides Commons Logging discovery")
    if not definers:
        problems.append(
            "no jar in WEB-INF/lib defines org.apache.commons.logging; Spring 7 and the "
            "JSPs need commons-logging:commons-logging")
    elif len(definers) > 1:
        problems.append(
            "org.apache.commons.logging is defined by more than one class-path source: "
            + ", ".join(f"{s} ({len(sources[s][0])} classes)" for s in definers))
    for source in definers:
        path = PurePosixPath(source)
        if path.parent != LIB or not EXPECTED_JAR.match(path.name):
            problems.append(
                f"{source} defines org.apache.commons.logging classes; only "
                "WEB-INF/lib/commons-logging-<version>.jar may")
        else:
            classes = {MULTI_RELEASE.sub("", d) for d in sources[source][0]}
            for required, why in ((LOG_FACTORY, ""),
                                  (LOG4J_API_FACTORY, " (commons-logging 1.3 or later "
                                   "is needed to bind JCL to Log4j 2)")):
                if required not in classes:
                    problems.append(f"{source} has no {required}{why}")
    single = definers[0] if len(definers) == 1 and not problems else None
    return problems, single


def main(argv):
    """Check the WAR named in argv[1]; return the exit status (0 clean, 1 refused, 2 unreadable)."""
    if len(argv) != 2:
        print(f"usage: {PROG}.py <carlos.war>", file=sys.stderr)
        return 2
    try:
        sources = scan_war(argv[1])
    except Unreadable as exc:
        print(f"{PROG}: {exc}", file=sys.stderr)
        return 2
    problems, single = violations(sources)
    if problems:
        for problem in problems:
            print(f"{PROG}: {problem}", file=sys.stderr)
        print(f"{PROG}: exclude the extra implementation in pom.xml (see its Logging "
              "section, issue #5413)", file=sys.stderr)
        return 1
    print(f"{PROG}: OK, org.apache.commons.logging comes only from {single} "
          f"({len(sources[single][0])} classes)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
