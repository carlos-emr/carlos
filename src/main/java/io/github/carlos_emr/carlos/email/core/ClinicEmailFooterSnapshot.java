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
package io.github.carlos_emr.carlos.email.core;

/**
 * Trusted clinic content captured at compose preparation, never populated from form fields.
 *
 * @param html the clinic footer, cleaned; empty when none is set or the footer is turned off
 * @param logo the logo the email carries, or {@code null}
 * @param clinicFooterOff whether an administrator turned the clinic footer off: the email then
 *     carries no clinic footer and no logo, and may be sent without one
 */
public record ClinicEmailFooterSnapshot(String html, EmailInlineImage logo, boolean clinicFooterOff) {

    /** What an email carries while the clinic footer is turned off: nothing from the clinic. */
    public static final ClinicEmailFooterSnapshot OFF = new ClinicEmailFooterSnapshot("", null, true);

    public ClinicEmailFooterSnapshot {
        html = clinicFooterOff ? "" : EmailFooterHtml.clean(html);
        if (clinicFooterOff) {
            logo = null;
        } else if (logo != null) {
            logo = new EmailInlineImage(logo.contentId(), logo.contentType(), logo.bytes().clone());
        }
    }

    /** A clinic footer that is required (the default): an email needs it to be sent. */
    public ClinicEmailFooterSnapshot(String html, EmailInlineImage logo) {
        this(html, logo, false);
    }

    /** @return whether an email with this snapshot must wait for an administrator to set a footer */
    public boolean missingRequiredFooter() {
        return !clinicFooterOff && html.isEmpty();
    }

    @Override
    public EmailInlineImage logo() {
        return logo == null ? null : new EmailInlineImage(logo.contentId(), logo.contentType(), logo.bytes().clone());
    }
}
