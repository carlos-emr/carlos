/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const assert = require('node:assert/strict');

// The caller must restart Tomcat after cleanup to release its in-memory JSP wrapper.
function createJasperProbe(webappDirectory, classDirectory) {
  const webapp = fs.realpathSync(webappDirectory);
  const classes = fs.realpathSync(classDirectory);
  assert(fs.statSync(webapp).isDirectory() && fs.statSync(classes).isDirectory());
  const stem = `carlosjasperprobe${randomBytes(16).toString('hex')}`;
  const target = path.join(webapp, `${stem}.jsp`);
  const compiled = new RegExp(`^${stem}_jsp(?:\\.(?:java|class|smap)|\\$[^/]+\\.class)$`);
  assert(!fs.readdirSync(classes).some(name => compiled.test(name)), 'Probe compiled path already exists');
  const source = version => `<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html><html><head><title>Synthetic Jasper probe</title></head><body>
<p id="probe-token">${stem}</p><p id="probe-version">${version}</p>
<% jakarta.servlet.ServletRegistration jsp = application.getServletRegistration("jsp"); %>
<p id="jsp-development"><%= !"false".equalsIgnoreCase(jsp.getInitParameter("development")) %></p>
<p id="jsp-mappedfile"><%= !"false".equalsIgnoreCase(jsp.getInitParameter("mappedfile")) %></p>
</body></html>
`;
  const original = source('one');
  const replacement = source('two');
  fs.writeFileSync(target, original, { flag: 'wx', mode: 0o644 });
  const owned = fs.lstatSync(target);
  function verifyOwner() {
    const stat = fs.lstatSync(target);
    assert(stat.isFile() && stat.dev === owned.dev && stat.ino === owned.ino,
      'Probe path was replaced; retain it for recovery');
    const text = fs.readFileSync(target, 'utf8');
    assert(text === original || text === replacement, 'Probe bytes changed outside this fixture; retain them for recovery');
  }
  let cleaned = false;
  return {
    filename: path.basename(target), stem,
    replace() {
      verifyOwner();
      fs.writeFileSync(target, replacement);
      // Guarantee the change remains visible to development-mode timestamp checks.
      const modified = new Date(Math.max(Date.now(), owned.mtimeMs + 1000));
      fs.utimesSync(target, modified, modified);
    },
    cleanup() {
      if (cleaned) return;
      verifyOwner();
      const generated = fs.readdirSync(classes).filter(name => compiled.test(name));
      for (const name of generated) {
        assert(fs.lstatSync(path.join(classes, name)).isFile(), 'Refuse a replaced compiled probe path');
      }
      fs.unlinkSync(target);
      for (const name of generated) fs.unlinkSync(path.join(classes, name));
      cleaned = true;
    },
  };
}
module.exports = { createJasperProbe };
