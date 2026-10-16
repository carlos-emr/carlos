/*
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
 * Configure Email > Clinic Email Logo (issue #3981). The logo form reloads the page, and the clinic
 * footer above it is a separate form, saved from its Edit footer window. Before the logo form is sent:
 *
 * 1. A picture CARLOS would refuse (not PNG or JPEG, over the byte limit, over the pixel limit) is
 *    refused here, with both limits named, so the page does not reload at all. The server still
 *    checks everything; a picture this script cannot read goes to the server, which says why.
 * 2. Footer text put back after a refused save, and not saved yet, travels with the form, so the
 *    page can put it back again after this reload (ClinicFooterDraft on the server).
 */
(function () {
    'use strict';

    function format(template, values) {
        return (template || '').replace(/\{(\d+)\}/g, function (match, index) {
            return values[Number(index)] !== undefined ? String(values[Number(index)]) : match;
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var form = document.getElementById('clinicLogoForm');
        if (!form) {
            return;
        }
        var input = form.querySelector('input[type="file"][name="logoFile"]');
        var message = document.getElementById('clinicLogoCheck');
        var maxBytes = Number(form.getAttribute('data-max-bytes'));
        var maxWidth = Number(form.getAttribute('data-max-width'));
        var maxHeight = Number(form.getAttribute('data-max-height'));
        var checked = false;

        function say(text) {
            if (message) {
                message.textContent = text;
                message.classList.remove('d-none');
            }
        }

        function carryFooterDraft() {
            var footer = document.getElementById('clinicFooter');
            var shown = document.querySelector('#clinicFooterForm input[name="clinicFooterFingerprint"]');
            if (footer && shown) {
                form.elements.namedItem('clinicFooterDraft').value = footer.value;
                form.elements.namedItem('clinicFooterDraftFingerprint').value = shown.value;
            }
        }

        // Sent after the picture passed; the submit handler lets this second submit through.
        function send(submitter) {
            checked = true;
            if (typeof form.requestSubmit === 'function') {
                form.requestSubmit(submitter);
                return;
            }
            var action = document.createElement('input');
            action.type = 'hidden';
            action.name = submitter.name;
            action.value = submitter.value;
            form.appendChild(action);
            form.submit();
        }

        form.addEventListener('submit', function (event) {
            carryFooterDraft();
            if (checked) {
                checked = false;
                return;
            }
            var submitter = event.submitter;
            var file = input && input.files ? input.files[0] : null;
            if (!submitter || submitter.value !== 'upload' || !file) {
                return;
            }
            if (message) {
                message.classList.add('d-none');
            }
            if (file.type && file.type !== 'image/png' && file.type !== 'image/jpeg') {
                event.preventDefault();
                say(form.getAttribute('data-not-image'));
                return;
            }
            if (file.size > maxBytes) {
                event.preventDefault();
                say(format(form.getAttribute('data-too-big'), [Math.ceil(file.size / 1024)]));
                return;
            }
            if (typeof window.createImageBitmap !== 'function') {
                return;
            }
            event.preventDefault();
            window.createImageBitmap(file).then(function (picture) {
                var width = picture.width;
                var height = picture.height;
                if (typeof picture.close === 'function') {
                    picture.close();
                }
                if (width > maxWidth || height > maxHeight) {
                    say(format(form.getAttribute('data-too-large'), [width, height]));
                    return;
                }
                send(submitter);
            }, function () {
                send(submitter);
            });
        });
    });
})();
