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
 * Script-opened windows support the target page's Close control. Real URLs,
 * target="_blank" and rel="noopener" preserve native modified-click/no-JS
 * behavior without replacing the data-entry window. The scripted path also
 * uses noopener so the destination cannot access the source's unsaved form.
 *
 * Usage:
 *   <a href="/some/path" class="js-popup"
 *      data-popup-width="400" data-popup-height="300">Link text</a>
 *
 * @since 2026-08-31
 */
document.addEventListener('click', function (event) {
    if (event.defaultPrevented || event.button !== 0) return;
    if (!event.target || typeof event.target.closest !== 'function') return;
    var link = event.target.closest('a.js-popup');
    if (!link) return;

    // Leave modified clicks (open in new tab/window, etc.) to native
    // browser handling instead of forcing them through the popup.
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

    var width = link.getAttribute('data-popup-width') || 400;
    var height = link.getAttribute('data-popup-height') || 300;
    var windowProps = 'height=' + height + ',width=' + width +
        ',location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0';

    // Prevent navigation before opening: a blocked popup or browser error must
    // never replace the source form and discard its unsaved values.
    event.preventDefault();
    // With noopener, window.open returns null even when the popup opens. Do not
    // interpret that return value as failure or restore opener access.
    window.open(link.href, '_blank', windowProps + ',noopener');
});
