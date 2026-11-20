/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const { randomInt } = require('node:crypto');

// This becomes the installed administrator credential, so use the OS-backed
// generator, including unbiased selection of each alphabet character.
function generatedPassword() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
  let password = '';
  for (let index = 0; index < 16; index += 1) {
    password += alphabet[randomInt(alphabet.length)];
  }
  return `${password}!Aa1`;
}

async function fillResetPasswords(page, currentPassword, nextPassword) {
  for (const [name, expected] of [['oldPassword', currentPassword],
    ['newPassword', nextPassword], ['confirmPassword', nextPassword]]) {
    const field = page.locator(`input[name="${name}"]`);
    await field.fill(expected);
    if (await field.inputValue() !== expected) {
      throw new Error(`Reset field ${name} changed or truncated its input; no credential change was submitted`);
    }
  }
}

module.exports = { generatedPassword, fillResetPasswords };
