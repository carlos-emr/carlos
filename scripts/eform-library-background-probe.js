/** Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const ids = ['BGImage1', 'BGImage2'];

/** Run in Chromium: parse the editor and its decoded stored HTML without executing either. */
function storedBackgroundSnapshot(editorHtml) {
  const parser = new DOMParser();
  const editor = parser.parseFromString(editorHtml, 'text/html');
  const fields = editor.querySelectorAll('textarea[name="formHtml"]');
  if (fields.length !== 1) throw new Error('Expected exactly one stored form HTML editor');
  const template = parser.parseFromString(fields[0].value, 'text/html');
  return ['BGImage1', 'BGImage2'].map((id) => {
    const matches = template.querySelectorAll(`[id="${id}"]`);
    return { id, count: matches.length, tagName: matches[0]?.tagName, src: matches[0]?.getAttribute('src') };
  });
}

function expectedBackgrounds(snapshot) {
  if (!Array.isArray(snapshot) || snapshot.length !== ids.length) throw new Error('Missing stored background identities');
  const prefix = '${oscar_image_path}';
  return ids.map((id) => {
    const entries = snapshot.filter((entry) => entry.id === id);
    if (entries.length !== 1 || entries[0].count !== 1 || entries[0].tagName !== 'IMG') {
      throw new Error(`Stored form must have exactly one image ${id}`);
    }
    const source = entries[0].src;
    if (typeof source !== 'string' || !source.startsWith(prefix)) {
      throw new Error(`${id} must retain its stored oscar_image_path reference`);
    }
    const imageName = source.slice(prefix.length);
    if (!imageName || imageName === '.' || imageName === '..' || /[/\\\u0000-\u001f\u007f?#]/.test(imageName)
        || imageName.includes('${')) throw new Error(`${id} must reference one literal image filename`);
    return { id, imageName };
  });
}

function imageIdentity(url, baseUrl) {
  try {
    const base = new URL(baseUrl);
    const actual = new URL(url);
    const route = base.pathname.replace(/\/$/, '') + '/eform/displayImage';
    if (actual.origin !== base.origin || actual.username || actual.password || actual.hash
        || ![route, route + '.do'].includes(actual.pathname)
        || actual.searchParams.getAll('imagefile').length !== 1) return null;
    return actual.searchParams.get('imagefile');
  } catch (_) {
    return null;
  }
}

function assertLibraryBackgrounds(expected, actual, responses, baseUrl) {
  if (!Array.isArray(actual) || actual.length !== expected.length) throw new Error('Missing rendered background identities');
  for (const background of expected) {
    const matching = actual.filter((image) => image.id === background.id);
    if (matching.length !== 1 || matching[0].count !== 1) throw new Error(`Rendered ${background.id} is missing or duplicated`);
    const image = matching[0];
    if (!image.complete || !(image.width > 100 && image.height > 100)) {
      throw new Error(`${background.id} did not decode a full background image`);
    }
    if (imageIdentity(image.src, baseUrl) !== background.imageName) {
      throw new Error(`${background.id} rendered a different image from its stored template`);
    }
    const matchingResponses = responses.filter((response) => imageIdentity(response.url, baseUrl) === background.imageName);
    if (!matchingResponses.some((response) => response.status === 200 && /^image\//i.test(response.contentType || ''))
        || matchingResponses.some((response) => response.status >= 400)) {
      throw new Error(`${background.id} lacks a successful displayImage response for its exact stored filename`);
    }
  }
  return { bgImageCount: expected.length, images: expected, renderSurfaceUsable: true };
}

module.exports = { storedBackgroundSnapshot, expectedBackgrounds, imageIdentity, assertLibraryBackgrounds };
