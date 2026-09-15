/* SPDX-License-Identifier: GPL-2.0-or-later */

// Browser exception messages/stacks and even custom names may contain clinical
// text. Persist only a standard error class, never arbitrary page-controlled data.
function browserErrorClass(error) {
  const name = error?.name;
  return /^(?:Eval|Range|Reference|Syntax|Type|URI|Aggregate)?Error$/.test(name || '')
    ? name : 'Error';
}

module.exports = { browserErrorClass };
