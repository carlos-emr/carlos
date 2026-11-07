/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// scripts/check-war-container-apis.py refuses a WAR that bundles an API Tomcat 11
// supplies itself (issue #5416: EL 5.0.1 shipped beside Tomcat 11's EL 6.0). These
// cases build small synthetic WARs, so they need neither Maven nor a real build.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const checker = path.join(__dirname, 'check-war-container-apis.py');
const rulesFile = path.join(__dirname, 'fixtures', 'tomcat-container-packages.json');

// Writes a WAR (or an exploded web application) from {jars: {name: [entries]}, classes: [entries]}.
// Entries are zip member names; a jar named with the value null is written as junk bytes.
const BUILD_WAR = String.raw`
import io, json, os, sys, zipfile
spec = json.loads(sys.argv[1])
def jar_bytes(entries):
    if entries is None:
        return b"not a zip archive"
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as jar:
        for entry in entries:
            jar.writestr(entry, b"x")
    return buf.getvalue()
if spec.get("exploded"):
    root = spec["path"]
    for entry in spec.get("classes", []):
        target = os.path.join(root, "WEB-INF", "classes", *entry.split("/"))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        open(target, "wb").write(b"x")
    lib = os.path.join(root, "WEB-INF", "lib")
    os.makedirs(lib, exist_ok=True)
    for name, entries in spec.get("jars", {}).items():
        open(os.path.join(lib, name), "wb").write(jar_bytes(entries))
else:
    with zipfile.ZipFile(spec["path"], "w") as war:
        war.writestr("WEB-INF/web.xml", b"<web-app/>")
        for entry in spec.get("classes", []):
            war.writestr("WEB-INF/classes/" + entry, b"x")
        for name, entries in spec.get("jars", {}).items():
            war.writestr("WEB-INF/lib/" + name, jar_bytes(entries))
`;

const JSTL = {
  'jakarta.servlet.jsp.jstl-api-3.0.2.jar': ['jakarta/servlet/jsp/jstl/core/Config.class',
    'jakarta/servlet/jsp/jstl/fmt/LocaleSupport.class'],
  'jakarta.servlet.jsp.jstl-3.0.1.jar': ['org/apache/taglibs/standard/tag/rt/core/ForEachTag.class',
    'META-INF/c.tld'],
};

function withTempDir(body) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-war-apis-'));
  try {
    return body(directory);
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

function build(spec) {
  const result = spawnSync('python3', ['-I', '-c', BUILD_WAR, JSON.stringify(spec)], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  return spec.path;
}

function check(...args) {
  return spawnSync('python3', ['-I', checker, ...args], { encoding: 'utf8' });
}

test('a WAR that ships JSTL and only application classes passes', () => withTempDir(directory => {
  const war = build({ path: path.join(directory, 'carlos.war'), classes: ['io/github/carlos_emr/carlos/A.class'],
    jars: { ...JSTL, 'app-1.0.jar': ['org/example/App.class', 'module-info.class'],
      // Tomcat's own filter leaves these to the application.
      'tomcat-jdbc-11.0.27.jar': ['org/apache/tomcat/jdbc/pool/DataSource.class'],
      // An EL implementation outside the container's packages, without a factory service file.
      'el-helpers-1.0.jar': ['org/glassfish/expressly/util/Helper.class', 'jakarta/el/LocalStrings.properties'] } });
  const result = check(war);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /^PASS .*carlos\.war: 5 jars, no container-supplied API bundled, JSTL present$/m);
}));

test('the EL 5 API jar that issue #5416 found is refused by name and package', () => withTempDir(directory => {
  const war = build({ path: path.join(directory, 'carlos.war'),
    jars: { ...JSTL, 'jakarta.el-api-5.0.1.jar': ['jakarta/el/ELContext.class', 'jakarta/el/ExpressionFactory.class'] } });
  const result = check(war);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /FAIL .*!WEB-INF\/lib\/jakarta\.el-api-5\.0\.1\.jar: bundles container package jakarta\.el$/m);
  assert.match(result.stderr, /scope>provided<\/scope>/);
  assert.equal(result.stdout, '');
}));

test('container classes are refused under any jar name, multi-release path or WEB-INF/classes', () => withTempDir(directory => {
  const war = build({ path: path.join(directory, 'carlos.war'),
    classes: ['jakarta/servlet/http/Shadow.class'],
    jars: { ...JSTL,
      'uber-1.0.jar': ['org/example/App.class', 'META-INF/versions/17/jakarta/el/BeanELResolver.class'],
      'annotations-2.1.jar': ['jakarta/annotation/PostConstruct.class'],
      'embed-11.0.jar': ['org/apache/jasper/JspC.class', 'org/apache/tomcat/util/Thing.class'] } });
  const result = check(war);
  assert.equal(result.status, 1);
  for (const expected of [
    /WEB-INF\/classes\/: bundles container package jakarta\.servlet\.http$/m,
    /uber-1\.0\.jar: bundles container package jakarta\.el$/m,
    /annotations-2\.1\.jar: bundles container package jakarta\.annotation$/m,
    /embed-11\.0\.jar: bundles container package org\.apache\.jasper$/m,
    /embed-11\.0\.jar: bundles container package org\.apache\.tomcat\.util$/m,
  ]) {
    assert.match(result.stderr, expected);
  }
}));

test('a competing EL or WebSocket implementation is refused by its service file', () => withTempDir(directory => {
  const war = build({ path: path.join(directory, 'carlos.war'),
    jars: { ...JSTL, 'expressly-6.0.0.jar': ['org/glassfish/expressly/ExpressionFactoryImpl.class',
      'META-INF/services/jakarta.el.ExpressionFactory'],
    'tyrus-container-2.2.0.jar': ['org/glassfish/tyrus/container/Provider.class',
      'META-INF/services/jakarta.websocket.ContainerProvider'],
    // An application's own ServletContainerInitializer is not a container API implementation.
    'spring-web-7.0.10.jar': ['org/springframework/web/SpringServletContainerInitializer.class',
      'META-INF/services/jakarta.servlet.ServletContainerInitializer'] } });
  const result = check(war);
  assert.equal(result.status, 1);
  assert.match(result.stderr,
    /expressly-6\.0\.0\.jar: META-INF\/services\/jakarta\.el\.ExpressionFactory installs a competing container implementation$/m);
  assert.match(result.stderr,
    /tyrus-container-2\.2\.0\.jar: META-INF\/services\/jakarta\.websocket\.ContainerProvider installs a competing container implementation$/m);
  assert.doesNotMatch(result.stderr, /spring-web/);
}));

test('a WAR without JSTL is refused, because Tomcat provides none', () => withTempDir(directory => {
  const war = build({ path: path.join(directory, 'carlos.war'),
    jars: { 'jakarta.servlet.jsp.jstl-api-3.0.2.jar': JSTL['jakarta.servlet.jsp.jstl-api-3.0.2.jar'] } });
  const result = check(war);
  assert.equal(result.status, 1);
  assert.match(result.stderr,
    /org\/apache\/taglibs\/standard\/tag\/rt\/core\/ForEachTag\.class is missing; Tomcat provides no JSTL/);
  assert.doesNotMatch(result.stderr, /jakarta\/servlet\/jsp\/jstl\/core\/Config\.class is missing/);
}));

test('an exploded web application is checked like the WAR it came from', () => withTempDir(directory => {
  const clean = build({ exploded: true, path: path.join(directory, 'clean'), classes: ['io/github/A.class'], jars: JSTL });
  const result = check(clean);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /^PASS .*clean: 2 jars/m);

  const bundled = build({ exploded: true, path: path.join(directory, 'bundled'),
    classes: ['jakarta/el/Shadow.class'], jars: { ...JSTL, 'jakarta.el-api-5.0.1.jar': ['jakarta/el/ELContext.class'] } });
  const refused = check(clean, bundled);
  assert.equal(refused.status, 1, 'one failing artifact fails the run');
  assert.match(refused.stdout, /^PASS .*clean/m);
  assert.match(refused.stderr, /bundled[/\\]WEB-INF[/\\]lib[/\\]jakarta\.el-api-5\.0\.1\.jar: bundles container package jakarta\.el$/m);
  assert.match(refused.stderr, /bundled[/\\]WEB-INF[/\\]classes.*: bundles container package jakarta\.el$/m);
}));

test('an unreadable artifact or rules file is an error, never a pass', () => withTempDir(directory => {
  const cases = [];
  cases.push([check(path.join(directory, 'absent.war')), /does not exist/]);
  const notZip = path.join(directory, 'not-a.war');
  fs.writeFileSync(notZip, 'plain text');
  cases.push([check(notZip), /cannot open/]);
  const corruptJar = build({ path: path.join(directory, 'corrupt.war'), jars: { ...JSTL, 'broken.jar': null } });
  cases.push([check(corruptJar), /broken\.jar is not a readable jar/]);
  // A jar member whose compressed bytes are damaged inside an otherwise valid WAR.
  const damaged = path.join(directory, 'damaged.war');
  const damage = spawnSync('python3', ['-I', '-c', String.raw`
import io, sys, zipfile
inner = io.BytesIO()
with zipfile.ZipFile(inner, "w") as jar:
    jar.writestr("jakarta/servlet/jsp/jstl/core/Config.class", b"x" * 4096)
with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as war:
    war.writestr("WEB-INF/lib/app.jar", inner.getvalue() + b"y" * 4096)
data = bytearray(open(sys.argv[1], "rb").read())
start = data.index(b"WEB-INF/lib/app.jar") + len("WEB-INF/lib/app.jar")
for offset in range(start + 8, start + 64):
    data[offset] ^= 0xFF
open(sys.argv[1], "wb").write(bytes(data))
`, damaged], { encoding: 'utf8' });
  assert.equal(damage.status, 0, damage.stderr);
  cases.push([check(damaged), /cannot read WEB-INF\/lib\/app\.jar from .*damaged\.war/]);
  const noLib = build({ path: path.join(directory, 'empty.war') });
  cases.push([check(noLib), /has no WEB-INF\/lib\/\*\.jar/]);
  fs.mkdirSync(path.join(directory, 'not-webapp'));
  cases.push([check(path.join(directory, 'not-webapp')), /not an exploded web application/]);
  const war = build({ path: path.join(directory, 'carlos.war'), jars: JSTL });
  for (const [name, rules] of [
    ['malformed', '{'],
    ['missing-key', JSON.stringify({ containerPackages: ['jakarta/el/'], applicationPackages: [], containerServices: [] })],
    ['empty', JSON.stringify({ containerPackages: [], applicationPackages: [], containerServices: [], requiredClasses: ['A.class'] })],
    ['dotted', JSON.stringify({ containerPackages: ['jakarta.el'], applicationPackages: [], containerServices: [],
      requiredClasses: ['A.class'] })],
  ]) {
    const file = path.join(directory, `${name}.json`);
    fs.writeFileSync(file, rules);
    cases.push([check('--rules', file, war), /ERROR (cannot read )?rules .*\.json/]);
  }
  for (const [result, message] of cases) {
    assert.equal(result.status, 2, `${message}: ${result.stdout}${result.stderr}`);
    assert.match(result.stderr, message);
    assert.doesNotMatch(result.stdout, /PASS/);
  }
}));

test('the shared rules mirror Tomcat 11\'s class-loader filter and keep JSTL with the application', () => {
  const rules = JSON.parse(fs.readFileSync(rulesFile, 'utf8'));
  // WebappClassLoaderBase.filter in Tomcat 11.0.x: always loaded from the container. The exact set,
  // so a prefix that is too broad (org/apache/) or too narrow fails here, not in a real build.
  assert.deepEqual([...rules.containerPackages].sort(), ['jakarta/annotation/', 'jakarta/el/',
    'jakarta/security/auth/message/', 'jakarta/servlet/', 'jakarta/websocket/', 'javax/websocket/',
    'org/apache/catalina/', 'org/apache/coyote/', 'org/apache/el/', 'org/apache/jasper/', 'org/apache/juli/',
    'org/apache/naming/', 'org/apache/tomcat/']);
  assert.deepEqual([...rules.applicationPackages].sort(), ['jakarta/servlet/jsp/jstl/', 'org/apache/tomcat/jdbc/']);
  assert.deepEqual([...rules.containerServices].sort(), ['META-INF/services/jakarta.el.ExpressionFactory',
    'META-INF/services/jakarta.websocket.ContainerProvider',
    'META-INF/services/jakarta.websocket.server.ServerEndpointConfig$Configurator']);
  assert.ok(rules.requiredClasses.includes('jakarta/servlet/jsp/jstl/core/Config.class'));
});
