/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * ChartSpace Allergies block: turns the JSON of encounter/chartspace/allergies
 * into a view model and renders it. Never throws and never rejects: anything
 * that is not the expected JSON (HTML error page, expired session redirect, 500,
 * network failure) becomes the ERROR state, which the page shows as text.
 *
 * All server strings are written with textContent only, so allergy text such as
 * "<img onerror=...>" is displayed, never interpreted. Severity is always
 * carried as a text label (never colour alone); the cs-sev-<level> class only
 * adds styling.
 */
(function (root) {
  'use strict';

  var STATES = ['OK', 'EMPTY', 'NO_ACCESS'];
  // Mirrors Allergy.getSeverityOfReactionDesc: anything else is unknown.
  var SEVERITY = { '1': 'mild', '2': 'moderate', '3': 'severe', '5': 'none' };
  var STATE_MESSAGE = { EMPTY: 'msgEmpty', NO_ACCESS: 'msgNoAccess', ERROR: 'msgError' };
  var STATE_ICON = { EMPTY: 'cs-icon-empty', NO_ACCESS: 'cs-icon-no-access', ERROR: 'cs-icon-error' };

  function text(value) {
    return typeof value === 'string' ? value : '';
  }

  function severityLevel(code) {
    var key = typeof code === 'string' ? code : '';
    return Object.prototype.hasOwnProperty.call(SEVERITY, key) ? SEVERITY[key] : 'unknown';
  }

  /**
   * Maps the server JSON to {state, items}. Unknown shapes and unknown status
   * values give ERROR; an OK without an items array is also ERROR.
   */
  function toViewModel(json) {
    if (!json || typeof json !== 'object' || STATES.indexOf(json.status) === -1) {
      return { state: 'ERROR', items: [] };
    }
    if (json.status !== 'OK') {
      return { state: json.status, items: [] };
    }
    if (!Array.isArray(json.items)) {
      return { state: 'ERROR', items: [] };
    }
    return {
      state: 'OK',
      items: json.items.map(function (row) {
        var r = row && typeof row === 'object' ? row : {};
        return {
          description: text(r.description),
          reaction: text(r.reaction),
          startDate: text(r.startDate),
          severityLevel: severityLevel(r.severityCode)
        };
      })
    };
  }

  /**
   * GET the block data. Always resolves: the parsed JSON on success, or
   * {status: 'ERROR'} for a non-ok status, a non-JSON content type, a body that
   * does not parse, or a network failure.
   */
  function load(contextPath, demographicNo, fetchImpl) {
    var doFetch = fetchImpl || (typeof root.fetch === 'function' ? root.fetch.bind(root) : null);
    var failure = { status: 'ERROR' };
    if (!doFetch) {
      return Promise.resolve(failure);
    }
    var url = contextPath + '/encounter/chartspace/allergies?demographicNo=' +
      encodeURIComponent(demographicNo);
    return Promise.resolve()
      .then(function () {
        return doFetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
      })
      .then(function (response) {
        var type = response && response.headers && response.headers.get('content-type');
        if (!response || !response.ok || !type || type.toLowerCase().indexOf('application/json') !== 0) {
          return failure;
        }
        return response.json();
      })
      .catch(function () {
        return failure;
      });
  }

  function element(tag, className, content) {
    var el = root.document.createElement(tag);
    if (className) {
      el.className = className;
    }
    if (content !== undefined) {
      el.textContent = content;
    }
    return el;
  }

  /**
   * Renders the view model into bodyEl (replacing its children). i18n supplies
   * severity labels (i18n.severity[level]) and msgEmpty, msgNoAccess, msgError.
   */
  function render(bodyEl, viewModel, i18n) {
    var labels = i18n || {};
    while (bodyEl.firstChild) {
      bodyEl.removeChild(bodyEl.firstChild);
    }

    if (viewModel.state !== 'OK') {
      var note = element('p', 'cs-state cs-state-' + viewModel.state.toLowerCase().replace('_', '-'));
      var icon = element('span', 'cs-icon ' + (STATE_ICON[viewModel.state] || ''));
      icon.setAttribute('aria-hidden', 'true');
      note.appendChild(icon);
      note.appendChild(element('span', 'cs-state-text', text(labels[STATE_MESSAGE[viewModel.state]])));
      bodyEl.appendChild(note);
      return;
    }

    var list = element('ul', 'cs-allergy-list');
    viewModel.items.forEach(function (item) {
      var row = element('li', 'cs-allergy');
      var severity = labels.severity || {};
      row.appendChild(element('span', 'cs-allergy-name', item.description));
      row.appendChild(element('span', 'cs-sev cs-sev-' + item.severityLevel, text(severity[item.severityLevel])));
      if (item.reaction) {
        row.appendChild(element('span', 'cs-allergy-reaction', item.reaction));
      }
      if (item.startDate) {
        row.appendChild(element('span', 'cs-allergy-date', item.startDate));
      }
      list.appendChild(row);
    });
    bodyEl.appendChild(list);
  }

  var api = {
    id: 'allergies',
    toViewModel: toViewModel,
    load: load,
    render: render
  };

  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.ChartSpace = root.ChartSpace || {};
    root.ChartSpace.allergies = api;
  }
})(typeof window !== 'undefined' ? window : this);
