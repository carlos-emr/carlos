/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. */
/*
 * The Visual eForm Editor decides for itself whether a template it loads is one it wrote
 * (isVisualEditorAuthored in visualEformEditor.jsp), and warns that a converted form loses its
 * scripts. The eForm Library makes the same call in Java (EFormEditorResolver) to choose where the
 * Edit pencil goes (#3166). This runs the editor's real function against the same cases as
 * EFormEditorResolverUnitTest, so the two cannot drift apart unnoticed.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/eform/visualEformEditor.jsp'), 'utf8');

function slice(startMarker, endMarker) {
  const start = jsp.indexOf(startMarker);
  const end = jsp.indexOf(endMarker, start);
  assert(start >= 0 && end > start, `could not find ${startMarker}`);
  return jsp.slice(start, end);
}

const constants = slice('        var VISUAL_EDITOR_MARKER_NAME', '\n\n');
const fn = slice('        function eformFingerprint(text) {', '        /**\n         * Loads a stored template');
const context = vm.createContext({});
vm.runInContext(`${constants}\n${fn}`, context);
const authored = html => context.isVisualEditorAuthored(html);

const GENERATED = "<!DOCTYPE html>\n<html>\n<head>\n<META http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n"
  + "<title>Visual</title>\n</head>\n<body>\n<div id='eform_container' style='max-width: 750px'>\n"
  + '<form id="inputForm" action="get">\n<div id="page_1" class="page_container"></div>\n</form>\n</div>\n</body>\n</html>';

test('the marker matches the Java resolver constants', () => {
  const java = fs.readFileSync(path.join(__dirname,
    '../src/main/java/io/github/carlos_emr/carlos/eform/EFormEditorResolver.java'), 'utf8');
  assert.match(java, new RegExp(`VISUAL_EDITOR_MARKER_NAME = "${context.VISUAL_EDITOR_MARKER_NAME}"`));
  assert.match(java, new RegExp(`VISUAL_EDITOR_MARKER_CONTENT = "${context.VISUAL_EDITOR_MARKER_CONTENT}"`));
});

test('the fingerprint is the 32-bit FNV-1a the Java resolver computes', () => {
  // The same reference values EFormEditorResolverUnitTest asserts for the Java side.
  assert.equal(context.eformFingerprint(''), '811c9dc5');
  assert.equal(context.eformFingerprint('a'), 'e40c292c');
  assert.equal(context.eformFingerprint('foobar'), 'bf9cf968');
  // Over UTF-16 code units, so a surrogate pair hashes as two units in both languages.
  assert.equal(context.eformFingerprint('\u00e9\u20ac\ud83d\ude00'), '0c5e230d');
});

test('a stamped form is the editor\'s, and removing the marker gives back exactly what was hashed', () => {
  const stamped = context.stampVisualEditorMarker(GENERATED);
  assert.match(stamped, /<meta name='carlos-eform-editor' content='visual' data-fingerprint='[0-9a-f]{8}'><title>/);
  assert.equal(stamped.replace(/<meta name='carlos-eform-editor'[^>]*>/, ''), GENERATED);
  assert.equal(authored(stamped), true);
});

test('any change after the visual save makes the form unverified', () => {
  const stamped = context.stampVisualEditorMarker(GENERATED);
  for (const edited of [
    stamped.replace('</head>', '<script>function clinicCustom(){}</script></head>'),
    stamped.replace(/\n/g, '\r\n'),
    stamped.replace("<meta name='carlos-eform-editor'", '<meta name="carlos-eform-editor"'),
  ]) {
    assert.equal(authored(edited), false, edited.slice(0, 160));
  }
});

test('missing markup, the legacy shell and other forms are not proven the editor\'s', () => {
  for (const html of [null, undefined, '', '   ', GENERATED,
    "<meta name='carlos-eform-editor' content='visual' data-fingerprint='00000000'><title>t</title>",
    '<div id="page1" style="position:relative;"><img id="BGImage1" src="a.png"></div>',
    "<form method='post'><textarea name='letter'></textarea></form>",
  ]) {
    assert.equal(authored(html), false, String(html));
  }
});
