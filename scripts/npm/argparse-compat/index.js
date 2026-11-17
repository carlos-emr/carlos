/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const upstream = require('argparse-upstream');

// argparse 2 retains v1 aliases but its legacy constructor reads this.version
// before assigning it, so js-yaml 3's --version silently prints nothing.
// Translate only that option to the public v2 API. The pinned upstream code
// and its Python-2.0 license remain untouched in the registry dependency.
// Source: https://github.com/nodeca/argparse/blob/2.0.1/argparse.js
// Remove this adapter when gray-matter no longer needs the js-yaml 3 CLI API.
/** Preserve the legacy version option while delegating parsing to argparse 2. */
function ArgumentParser(options = {}) {
  const {version, ...rest} = options;
  const parser = new upstream.ArgumentParser(rest);
  if (version) {
    parser.version = version;
    const prefix = parser.prefix_chars.includes('-') ? '-' : parser.prefix_chars[0];
    parser.add_argument(`${prefix}v`, `${prefix}${prefix}version`, {
      action: 'version', version, help: "show program's version number and exit",
    });
  }
  return parser;
}

ArgumentParser.prototype = upstream.ArgumentParser.prototype;
Object.setPrototypeOf(ArgumentParser, upstream.ArgumentParser);
// Const and several v1 aliases are non-enumerable getters; object spread
// would discard them and break js-yaml 3's argparse.Const.SUPPRESS lookup.
module.exports = Object.defineProperties({}, Object.getOwnPropertyDescriptors(upstream));
module.exports.ArgumentParser = ArgumentParser;
