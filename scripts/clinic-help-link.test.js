/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const page = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/provider/mainMenu.jsp'), 'utf8');
const handler = page.match(/data-help-url="[^\n]+"\s+onclick="([\s\S]*?)">/)[1];

function openHelp(value) {
  const opened = [];
  const click = vm.runInNewContext(`(function () { ${handler} })`, {
    URL,
    window: {location: {href: 'https://clinic.invalid/carlos/administration'}},
    popupPage: (height, width, url) => opened.push({height, width, url}),
  });
  assert.equal(click.call({dataset: {helpUrl: value}}), false);
  return opened;
}

for (const value of ['https://help.invalid/clinic?a=1&b=2', 'http://help.invalid/', '/help/clinic', "https://help.invalid/?q='\"><img src=x>"]) {
  test(`Help opens the literal HTTP(S) destination: ${value}`, () => {
    assert.deepEqual(openHelp(value), [{height: 600, width: 750,
      url: new URL(value, 'https://clinic.invalid/carlos/administration').href}]);
  });
}

for (const value of ['', '   ', 'javascript:alert(1)', 'JaVaScRiPt:alert(1)', 'data:text/html,hello', 'file:///tmp/help', 'http://[']) {
  test(`Help refuses empty, malformed or executable destinations: ${value}`, () => {
    assert.deepEqual(openHelp(value), []);
  });
}
