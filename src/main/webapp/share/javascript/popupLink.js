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

/**
 * Opens links marked with the "js-popup" class in a small, script-opened
 * popup window instead of navigating normally.
 *
 * A script-opened window is required because some popup target pages
 * (e.g. encounter/About, encounter/License) close themselves via
 * window.close(). Browsers only honor window.close() on windows opened
 * by script — not on a window/tab reached through a normal navigation
 * or target="_blank" — so the link must stay real (for middle-click,
 * copy-link, and no-JS fallback) while still being opened via
 * window.open() when JS is available.
 *
 * Usage:
 *   <a href="/some/path" class="js-popup"
 *      data-popup-width="400" data-popup-height="300">Link text</a>
 *
 * @since 2026-08-31
 */
document.addEventListener('click', function (event) {
    var link = event.target.closest('a.js-popup');
    if (!link) return;

    event.preventDefault();

    var width = link.getAttribute('data-popup-width') || 400;
    var height = link.getAttribute('data-popup-height') || 300;
    var windowProps = 'height=' + height + ',width=' + width +
        ',location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0';

    var popup = window.open(link.href, '', windowProps);
    if (popup && popup.opener == null) {
        popup.opener = window;
    }
});
