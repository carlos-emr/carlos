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
 * ChartSpace layout as plain data: which block sits in which region, and which
 * regions a block lands in once its load status is known. No DOM access, so it
 * can be tested with node --test.
 *
 * The preset is chosen by id (resolvePreset); persisting a per-user layout
 * (UserProperty) is a later PR.
 */
(function (root) {
  'use strict';

  var TOP_MAX = 4;
  var REGIONS = ['top', 'right', 'hidden'];

  /*
   * Layout presets, keyed by the persisted id. The keys must equal the ids of the
   * Java enum ChartSpaceLayout (a test reads the source to check it). Ids of blocks
   * that are not registered yet are dropped by normalizeLayout, so a preset may name
   * blocks that arrive in later PRs.
   */
  var PRESETS = Object.freeze({
    familyPractice: Object.freeze({
      top: Object.freeze(['socialHistory', 'medicalHistory', 'ongoingConcerns', 'reminders']),
      right: Object.freeze(['allergies'])
    }),
    eyeCare: Object.freeze({
      top: Object.freeze(['ocularHistory', 'impressionHistory', 'ocularMedications', 'iopTrend']),
      right: Object.freeze(['allergies'])
    })
  });
  var DEFAULT_PRESET_ID = 'familyPractice';

  function asArray(value) {
    return Array.isArray(value) ? value : [];
  }

  /**
   * Returns a fresh layout containing only known ids, each at most once (the
   * first region in top, right, hidden order wins). Top is capped at TOP_MAX;
   * overflow goes to the start of right. Never mutates its input.
   */
  function normalizeLayout(raw, knownIds) {
    var known = asArray(knownIds);
    var source = raw && typeof raw === 'object' ? raw : {};
    var seen = Object.create(null);
    var out = { top: [], right: [], hidden: [] };

    REGIONS.forEach(function (region) {
      asArray(source[region]).forEach(function (id) {
        if (known.indexOf(id) === -1 || seen[id]) {
          return;
        }
        seen[id] = true;
        out[region].push(id);
      });
    });

    if (out.top.length > TOP_MAX) {
      out.right = out.top.slice(TOP_MAX).concat(out.right);
      out.top = out.top.slice(0, TOP_MAX);
    }
    return out;
  }

  /**
   * Applies load statuses to a layout. Only EMPTY blocks are auto-hidden:
   * NO_ACCESS, ERROR and LOADING stay visible so "no access" is never mistaken
   * for "no data". hiddenHasData is true when a hidden block holds data (OK),
   * so the UI can flag it.
   */
  function partition(layout, statuses) {
    var state = statuses || {};
    var src = layout || {};
    var out = { top: [], right: [], hidden: asArray(src.hidden).slice() };

    ['top', 'right'].forEach(function (region) {
      asArray(src[region]).forEach(function (id) {
        if (state[id] === 'EMPTY') {
          out.hidden.push(id);
        } else {
          out[region].push(id);
        }
      });
    });

    out.hiddenHasData = out.hidden.some(function (id) {
      return state[id] === 'OK';
    });
    return out;
  }

  /**
   * Returns the preset for id, or the default preset for any other value
   * (undefined, unknown text, or inherited names such as '__proto__' and
   * 'constructor'). Own-property lookup is the allow-list: the id may come from
   * the page and is never trusted as a key.
   */
  function resolvePreset(id) {
    if (typeof id === 'string' && Object.prototype.hasOwnProperty.call(PRESETS, id)) {
      return PRESETS[id];
    }
    return PRESETS[DEFAULT_PRESET_ID];
  }

  var api = {
    PRESETS: PRESETS,
    DEFAULT_PRESET_ID: DEFAULT_PRESET_ID,
    resolvePreset: resolvePreset,
    TOP_MAX: TOP_MAX,
    normalizeLayout: normalizeLayout,
    partition: partition
  };

  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.ChartSpace = root.ChartSpace || {};
    root.ChartSpace.layout = api;
  }
})(typeof window !== 'undefined' ? window : this);
