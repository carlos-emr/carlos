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
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.utility.SafeEncode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the eChart header bar ({@code casemgmt/newEncounterHeader.jsp}) shows for one patient and
 * one viewer, already filtered by the viewer's eChart display settings.
 *
 * <p>Values are raw (unencoded) text; the JSP encodes each one for its output context. Optional
 * items are {@code ""} or empty when hidden by a setting or not on file, so the JSP only tests for
 * emptiness and never re-applies a setting.</p>
 *
 * @param headerBackground the viewer's provider colour ({@code #RRGGBB}), the bar's background
 * @param headerText       black or white, whichever reads on {@code headerBackground}
 * @param headerLink       link colour: OSCAR 19's {@code #0088CC} darkened to 4.5:1 on the provider
 *                         colour, or white on a dark one
 * @param mrpName          the most responsible provider's name, {@code ""} when none is assigned
 * @param mrpUnderline     the MRP's provider colour ({@code #RRGGBB}), underlining the MRP name
 * @param patientName      "LAST, FIRST (alias)"
 * @param sex              the administrative sex code
 * @param gender           gender identity, {@code ""} when not recorded
 * @param pronoun          pronouns, {@code ""} when not recorded
 * @param showDateOfBirth  whether {@code dateOfBirth} is the visible value ({@code age} is then the tooltip),
 *                         or the reverse
 * @param dateOfBirth      yyyy-mm-dd
 * @param age              localized age, e.g. "45 years"
 * @param hin              health insurance number, {@code ""} when hidden or not on file
 * @param hinVersion       its version code, {@code ""} when none
 * @param phones           the phone numbers to show, in display order; may be empty
 * @param phoneTelLinks    whether each phone is a {@code tel:} click-to-dial link rather than a copy button
 * @param email            email address, {@code ""} when hidden or not on file
 * @param emailConsented   whether the patient consented to email for care (the address is then a mailto link)
 * @param nextAppointment  the next appointment date, {@code ""} when none is booked
 * @since 2026-10-16
 */
public record EchartHeaderViewModel(
        String headerBackground,
        String headerText,
        String headerLink,
        String mrpName,
        String mrpUnderline,
        String patientName,
        String sex,
        String gender,
        String pronoun,
        boolean showDateOfBirth,
        String dateOfBirth,
        String age,
        String hin,
        String hinVersion,
        List<Phone> phones,
        boolean phoneTelLinks,
        String email,
        boolean emailConsented,
        String nextAppointment) {

    public EchartHeaderViewModel {
        phones = List.copyOf(phones);
    }

    /**
     * The {@code mailto:} URI for {@link #email()}, still to be encoded for its HTML attribute.
     *
     * <p>The local part and the domain are percent-encoded separately around a literal {@code @}:
     * RFC 6068 leaves that separator unencoded rather than relying on the client to decode
     * {@code %40}, and a stored {@code ?}, {@code &} or {@code =} must stay part of the address
     * rather than add {@code cc}/{@code body} header fields to the link.</p>
     *
     * @return the URI, or {@code ""} when no address is shown
     */
    public String mailtoHref() {
        if (email == null || email.isEmpty()) {
            return "";
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return "mailto:" + SafeEncode.forUriComponent(email);
        }
        return "mailto:" + SafeEncode.forUriComponent(email.substring(0, at))
                + "@" + SafeEncode.forUriComponent(email.substring(at + 1));
    }

    /** Which number a {@link Phone} is; selects its icon and its tooltip label. */
    public enum PhoneKind { CELL, HOME, WORK }

    /**
     * One phone number in the header.
     *
     * @param kind            which number this is
     * @param number          the number as recorded (a trailing {@code *} marks the patient's preferred number)
     * @param alternateKind   which number the tooltip offers instead, {@code null} when none
     * @param alternateNumber that number, {@code ""} when none
     */
    public record Phone(PhoneKind kind, String number, PhoneKind alternateKind, String alternateNumber) {

        /**
         * A trailing extension as people type it: "x123", "ext 123", "ext. 123", "ext: 123", "extension 123",
         * "#123". The separator class and the digits are disjoint, so matching stays linear.
         */
        private static final Pattern EXTENSION = Pattern.compile("(?i)(?:extension|ext|x|#)[\\s.:]*(\\d+)$");

        /**
         * The {@code tel:} URI that dials {@link #number()}, still to be encoded for its HTML attribute.
         *
         * <p>Only digits survive, with a leading {@code +} kept, so spaces, dashes, brackets and the
         * trailing {@code *} preferred mark drop out. An extension is split off as RFC 3966's
         * {@code ;ext=} rather than run into the number, which would dial the wrong line.</p>
         *
         * @return the URI, or {@code ""} when the number has no digits to dial
         */
        public String telHref() {
            String main = number == null ? "" : number.strip();
            if (main.endsWith("*")) {
                main = main.substring(0, main.length() - 1).strip();
            }
            String extension = "";
            Matcher matcher = EXTENSION.matcher(main);
            if (matcher.find()) {
                extension = matcher.group(1);
                main = main.substring(0, matcher.start());
            }
            String digits = main.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                return "";
            }
            return "tel:" + (main.startsWith("+") ? "+" : "") + digits
                    + (extension.isEmpty() ? "" : ";ext=" + extension);
        }
    }
}
