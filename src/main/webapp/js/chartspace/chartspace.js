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
 * ChartSpace page shell: builds one card per block, loads each block, and
 * places the cards in their region (or the hidden panel) from the load state.
 * DOM is built with createElement/textContent only.
 */
(function (root) {
  'use strict';

  function el(tag, className, content) {
    var node = root.document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (content !== undefined) {
      node.textContent = content;
    }
    return node;
  }

  function init(options) {
    var doc = root.document;
    var cs = root.ChartSpace;
    var i18n = options.i18n || {};
    var titles = i18n.titles || {};

    // Block registry: the second block is registered here.
    var blocks = { allergies: cs.allergies };
    var ids = Object.keys(blocks);
    var layout = cs.layout.normalizeLayout(cs.layout.PRESET, ids);

    var regions = {
      top: doc.getElementById('cs-top'),
      right: doc.getElementById('cs-right'),
      hidden: doc.getElementById('cs-hidden-body')
    };
    var toggle = doc.getElementById('cs-hidden-toggle');
    var panel = doc.getElementById('cs-hidden-panel');
    var closeBtn = doc.getElementById('cs-hidden-close');
    var statuses = {};
    var cards = {};
    var bodies = {};

    ids.forEach(function (id) {
      var card = el('article', 'cs-block');
      card.setAttribute('data-block', id);
      card.setAttribute('data-state', 'LOADING');
      card.appendChild(el('h3', 'cs-block-title', titles[id] || ''));
      var body = el('div', 'cs-block-body');
      // Announce loading/empty/error transitions to assistive technology.
      body.setAttribute('role', 'status');
      body.setAttribute('aria-live', 'polite');
      body.appendChild(el('p', 'cs-state cs-state-loading', i18n.msgLoading || ''));
      card.appendChild(body);
      cards[id] = card;
      bodies[id] = body;
      statuses[id] = 'LOADING';
    });

    function isOpen() {
      return !panel.hidden;
    }

    function setOpen(open) {
      panel.hidden = !open;
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    function updateToggle(parts) {
      var count = parts.hidden.length;
      var hasData = parts.hiddenHasData;
      toggle.setAttribute('data-has-data', hasData ? 'true' : 'false');
      toggle.className = 'btn btn-sm ' + (hasData ? 'btn-danger' : 'btn-outline-secondary');
      toggle.textContent = (i18n.btnHidden || '') + ' (' + count + ')';
      if (hasData) {
        toggle.appendChild(el('span', 'cs-hidden-flag', i18n.msgHiddenHasData || ''));
      }
      if (count === 0) {
        toggle.disabled = true;
        if (isOpen()) {
          setOpen(false);
        }
      } else {
        toggle.disabled = false;
      }
    }

    function place() {
      var parts = cs.layout.partition(layout, statuses);
      var focusWasInPanel = panel.contains(doc.activeElement);
      ['top', 'right', 'hidden'].forEach(function (region) {
        parts[region].forEach(function (id) {
          if (cards[id].parentNode !== regions[region]) {
            regions[region].appendChild(cards[id]);
          }
        });
      });
      updateToggle(parts);
      if (focusWasInPanel && !panel.contains(doc.activeElement)) {
        toggle.focus();
      }
    }

    place();

    ids.forEach(function (id) {
      var block = blocks[id];
      block.load(options.contextPath, options.demographicNo).then(function (json) {
        var vm = block.toViewModel(json);
        block.render(bodies[id], vm, i18n);
        statuses[id] = vm.state;
        cards[id].setAttribute('data-state', vm.state);
        place();
      }).catch(function () {
        // A failed load, view-model or render must never leave the card in LOADING.
        while (bodies[id].firstChild) {
          bodies[id].removeChild(bodies[id].firstChild);
        }
        bodies[id].appendChild(el('p', 'cs-state cs-state-error', i18n.msgError || ''));
        statuses[id] = 'ERROR';
        cards[id].setAttribute('data-state', 'ERROR');
        place();
      });
    });

    toggle.addEventListener('click', function () {
      if (!toggle.disabled) {
        setOpen(!isOpen());
      }
    });
    closeBtn.addEventListener('click', function () {
      setOpen(false);
      toggle.focus();
    });
    // Esc only acts while the panel is open, and leaves Bootstrap modals alone.
    doc.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape' || !isOpen() || event.defaultPrevented) {
        return;
      }
      if (doc.querySelector('.modal.show')) {
        return;
      }
      setOpen(false);
      toggle.focus();
    });
  }

  root.ChartSpace = root.ChartSpace || {};
  root.ChartSpace.init = init;
})(typeof window !== 'undefined' ? window : this);
