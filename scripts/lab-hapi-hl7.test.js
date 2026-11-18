/* SPDX-License-Identifier: GPL-2.0-or-later */
// Pins the synthetic hostile HL7 v2 XML that lab-hapi-hl7-playwright-checks.js uploads (#5414)
// and the loopback listener it counts requests on, so the check cannot pass by uploading
// messages that no longer ask the parser for anything.
const test = require('node:test');
const assert = require('node:assert/strict');
const { hostileMessages, hostileXml, startObserver } = require('./lab-hapi-hl7-playwright-checks');

const URL_BASE = 'http://127.0.0.1:9';
const messages = hostileMessages(URL_BASE);

test('builds the four hostile messages, each an HL7 v2 XML ADT^A01 with a DOCTYPE', () => {
  assert.deepEqual(Object.keys(messages), ['external-entity', 'parameter-entity', 'external-dtd', 'entity-expansion']);
  for (const [kind, xml] of Object.entries(messages)) {
    assert.match(xml, /^<\?xml version="1\.0" encoding="UTF-8"\?>\n<!DOCTYPE ADT_A01 /, `${kind} must declare a DOCTYPE`);
    assert.ok(xml.includes('<ADT_A01 xmlns="urn:hl7-org:v2xml">') && xml.includes('</ADT_A01>'), `${kind} is not an ADT_A01`);
    assert.ok(xml.includes('<CM_MSG.1>ADT</CM_MSG.1><CM_MSG.2>A01</CM_MSG.2>'), `${kind} has no ADT^A01 MSH-9`);
    assert.ok(!/[\u0000-\u0008]/.test(xml), `${kind} carries control characters`);
  }
});

test('each external message points the parser at the listener', () => {
  assert.match(messages['external-entity'], /<!ENTITY xxe SYSTEM "http:\/\/127\.0\.0\.1:9\/pw5414-entity">/);
  assert.ok(messages['external-entity'].includes('<XPN.1>&xxe;</XPN.1>'));
  assert.match(messages['parameter-entity'], /<!ENTITY % remote SYSTEM "http:\/\/127\.0\.0\.1:9\/pw5414-parameter\.dtd"> %remote;/);
  assert.match(messages['external-dtd'], /<!DOCTYPE ADT_A01 SYSTEM "http:\/\/127\.0\.0\.1:9\/pw5414-external\.dtd">/);
});

test('the expansion message multiplies by ten for nine levels and uses the last entity', () => {
  const xml = messages['entity-expansion'];
  assert.equal((xml.match(/<!ENTITY lol\d /g) || []).length, 10);
  assert.ok(xml.includes('<!ENTITY lol9 "&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;">'));
  assert.ok(xml.includes('<XPN.1>&lol9;</XPN.1>'));
});

test('every message names only fictitious data', () => {
  for (const xml of Object.values(messages)) assert.ok(xml.includes('FAKE-PW5414'));
  assert.ok(!hostileXml('', 'x').includes('<!DOCTYPE'));
});

test('the listener records the requests made to it and answers a harmless DTD fragment', async () => {
  const observer = await startObserver();
  try {
    assert.equal(observer.requests.length, 0);
    const reply = await fetch(`${observer.url}/probe`);
    assert.equal(reply.status, 200);
    assert.equal(await reply.text(), '<!ELEMENT any ANY>');
    assert.deepEqual(observer.requests, ['/probe']);
  } finally {
    await observer.close();
  }
});
