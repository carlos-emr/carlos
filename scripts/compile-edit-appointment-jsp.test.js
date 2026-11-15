/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const script = path.join(__dirname, 'compile-edit-appointment-jsp.sh');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-jspc-tools-'));
  t.after(() => fs.rmSync(root, {recursive:true, force:true}));
  const webapp = path.join(root, 'web app');
  const tomcat = path.join(root, 'tomcat');
  const bin = path.join(root, 'bin');
  const temporary = path.join(root, 'temporary');
  for (const dir of [bin, temporary, path.join(root, 'jdk/bin'), path.join(webapp, 'WEB-INF/classes'),
    path.join(webapp, 'WEB-INF/lib'), path.join(webapp, 'WEB-INF/jsp/appointment'), path.join(tomcat, 'lib')]) {
    fs.mkdirSync(dir, {recursive:true});
  }
  fs.writeFileSync(path.join(webapp, 'WEB-INF/jsp/appointment/editappointment.jsp'), 'fixture');
  fs.writeFileSync(path.join(tomcat, 'lib/jasper.jar'), 'fixture');
  const javaLog = path.join(root, 'java.json');
  const mavenLog = path.join(root, 'maven.jsonl');
  function executable(filename, body) {
    fs.writeFileSync(filename, '#!' + process.execPath + '\n' + body, {mode:0o755});
  }
  executable(path.join(root, 'jdk/bin/java'), `
    const fs=require('node:fs');const path=require('node:path');const args=process.argv.slice(2);
    fs.writeFileSync(process.env.JAVA_LOG,JSON.stringify(args));
    const output=args[args.indexOf('-d')+1];
    const target=path.join(output,'org/apache/jsp/WEB_002dINF/jsp/appointment/editappointment_jsp.class');
    fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,'bytecode');
  `);
  executable(path.join(bin, 'mvn'), `
    const fs=require('node:fs');const path=require('node:path');const args=process.argv.slice(2);
    fs.appendFileSync(process.env.MAVEN_LOG,JSON.stringify(args)+'\\n');
    const coordinate=args.find(x=>x.startsWith('-Dartifact=')).slice(11).split(':');
    if(process.env.FAIL_MAVEN==='true' && coordinate[1]==='ant-launcher')process.exit(9);
    const output=args.find(x=>x.startsWith('-DoutputDirectory=')).slice(18);
    fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,coordinate[1]+'-'+coordinate[2]+'.jar'),'tool jar');
  `);
  const env = {...process.env, CATALINA_HOME:tomcat, JAVA_HOME:path.join(root,'jdk'), TMPDIR:temporary,
    PATH:bin+path.delimiter+process.env.PATH, JAVA_LOG:javaLog, MAVEN_LOG:mavenLog};
  delete env.JSPC_ANT_CLASSPATH;
  // The mock executables are ordinary Node programs, not test-runner children.
  delete env.NODE_TEST_CONTEXT;
  return {root, webapp, temporary, javaLog, mavenLog,
    run(extra={}) { return spawnSync('bash',[script,webapp],{env:{...env,...extra},encoding:'utf8'}); }};
}

test('standalone Jasper resolves Ant only into temporary build tooling', t => {
  const f=fixture(t);const result=f.run();
  assert.equal(result.status,0,result.stderr);
  const copies=fs.readFileSync(f.mavenLog,'utf8').trim().split('\n').map(JSON.parse);
  assert.equal(copies.length,2);
  assert.deepEqual(copies.map(x=>x.find(arg=>arg.startsWith('-Dartifact='))),
    ['-Dartifact=org.apache.ant:ant:1.10.18','-Dartifact=org.apache.ant:ant-launcher:1.10.18']);
  for(const args of copies) assert.ok(args.includes('org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy'));
  const args=JSON.parse(fs.readFileSync(f.javaLog,'utf8'));const cp=args[args.indexOf('-cp')+1];
  assert.ok(cp.includes(path.join(f.temporary, 'tmp.')));
  assert.ok(cp.includes('/ant/*'));
  assert.deepEqual(fs.readdirSync(path.join(f.webapp,'WEB-INF/lib')),[]);
  assert.deepEqual(fs.readdirSync(f.temporary),[]);
});

test('offline Jasper compilation uses the explicitly supplied tool classpath', t => {
  const f=fixture(t);const tools=path.join(f.root,'offline ant.jar');fs.writeFileSync(tools,'tool jar');
  const result=f.run({JSPC_ANT_CLASSPATH:tools});
  assert.equal(result.status,0,result.stderr);
  assert.equal(fs.existsSync(f.mavenLog),false);
  const args=JSON.parse(fs.readFileSync(f.javaLog,'utf8'));
  assert.ok(args[args.indexOf('-cp')+1].includes(tools));
  assert.deepEqual(fs.readdirSync(path.join(f.webapp,'WEB-INF/lib')),[]);
});

test('partial tool-resolution failure stops before Jasper and cleans temporary files', t => {
  const f=fixture(t);const result=f.run({FAIL_MAVEN:'true'});
  assert.equal(result.status,9);
  assert.equal(fs.existsSync(f.javaLog),false);
  assert.deepEqual(fs.readdirSync(f.temporary),[]);
  assert.deepEqual(fs.readdirSync(path.join(f.webapp,'WEB-INF/lib')),[]);
});
