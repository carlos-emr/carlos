/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

/*
 * Renders the CSRFGuard client template the way CSRFGuard's JavaScriptServlet
 * does, with the option values src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties
 * configures. Shared by the isolated browser check and the vm-based unit test of
 * the CARLOS-patched template (issue #4130).
 */

const fs = require('node:fs');
const path = require('node:path');

const PATCHED_TEMPLATE = path.join(__dirname, '..', '..',
  'src/main/resources/csrfguard/carlos-csrfguard.js');

/**
 * @param {object} options
 * @param {string} options.host        the page's hostname (DOMAIN_ORIGIN)
 * @param {string} options.token       the master token value
 * @param {string} [options.contextPath] the application context path ('' for root)
 * @param {string} [options.templatePath] template to render (default: the CARLOS copy)
 * @returns {string} JavaScript ready to serve or evaluate
 */
function renderCsrfGuardTemplate({host, token, contextPath = '', templatePath = PATCHED_TEMPLATE}) {
  // The servlet substitutes these into JavaScript string literals unescaped; the
  // fixtures only ever pass plain values, and anything else is refused rather than
  // rendered into a script.
  for (const [name, value] of Object.entries({host, token, contextPath})) {
    if (typeof value !== 'string' || (name !== 'contextPath' && value.length === 0)
      || !/^[A-Za-z0-9._:\/-]*$/.test(value)) {
      throw new Error(`renderCsrfGuardTemplate: ${name} must be a plain host, token or path`);
    }
  }
  const values = {
    "'%DOMAIN_STRICT%'": 'true',
    "'%INJECT_ATTRIBUTES%'": 'false',
    "'%INJECT_FORM_ATTRIBUTES%'": 'false',
    "'%INJECT_GET_FORMS%'": 'false',
    "'%INJECT_FORMS%'": 'true',
    "'%INJECT_DYNAMIC_NODES%'": 'true',
    "'%INJECT_XHR%'": 'true',
    "'%TOKENS_PER_PAGE%'": 'false',
    "'%ASYNC_XHR%'": 'true',
    '%DYNAMIC_NODE_CREATION_EVENT_NAME%': '',
    '%DOMAIN_ORIGIN%': host,
    '%TOKEN_NAME%': 'CSRF-TOKEN',
    '%TOKEN_VALUE%': token,
    '%CONTEXT_PATH%': contextPath,
    '%SERVLET_PATH%': `${contextPath}/csrfguard`,
    '%UNPROTECTED_EXTENSIONS%': 'css,js,png,jpg,gif,svg,ico,woff,woff2,ttf,eot',
  };
  let js = fs.readFileSync(templatePath, 'utf8');
  for (const [placeholder, value] of Object.entries(values)) {
    js = js.split(placeholder).join(value);
  }
  return js;
}

module.exports = {PATCHED_TEMPLATE, renderCsrfGuardTemplate};
