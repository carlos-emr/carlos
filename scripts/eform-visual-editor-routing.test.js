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
const fn = slice('        function isVisualEditorAuthored(html) {', '        /**');
const context = vm.createContext({});
vm.runInContext(`${constants}\n${fn}`, context);
const authored = html => context.isVisualEditorAuthored(html);

test('the marker matches the Java resolver constants', () => {
  const java = fs.readFileSync(path.join(__dirname,
    '../src/main/java/io/github/carlos_emr/carlos/eform/EFormEditorResolver.java'), 'utf8');
  assert.match(java, new RegExp(`VISUAL_EDITOR_MARKER_NAME = "${context.VISUAL_EDITOR_MARKER_NAME}"`));
  assert.match(java, new RegExp(`VISUAL_EDITOR_MARKER_CONTENT = "${context.VISUAL_EDITOR_MARKER_CONTENT}"`));
});

test('missing or blank markup is not the editor\'s', () => {
  for (const html of [null, undefined, '', '   ']) assert.equal(authored(html), false);
});

test('the marker is recognised in any quoting, case or spacing', () => {
  for (const marker of [
    "<meta name='carlos-eform-editor' content='visual'>",
    '<meta name="carlos-eform-editor" content="visual">',
    "<META NAME='CARLOS-EFORM-EDITOR' CONTENT='VISUAL'>",
    "<meta  name = 'carlos-eform-editor'   content = 'visual' />",
  ]) {
    assert.equal(authored(`<html><head>${marker}</head><body></body></html>`), true, marker);
  }
});

test('a form saved before the marker existed is recognised by its page shell', () => {
  assert.equal(authored("<div id='eform_container' style='max-width: 750px'>\n<form id=\"inputForm\" action=\"get\"></form></div>"), true);
});

test('lookalikes and other forms are not the editor\'s', () => {
  for (const html of [
    "<meta name='carlos-eform-editor' content='html'>",
    "<div id='eform_container'><form id='myForm'></form></div>",
    "<div data-id='eform_container'><form data-id='inputForm'></form></div>",
    '<div id="page1" style="position:relative;"><img id="BGImage1" src="a.png"></div>',
    "<form method='post'><textarea name='letter'></textarea></form>",
  ]) {
    assert.equal(authored(html), false, html);
  }
});
