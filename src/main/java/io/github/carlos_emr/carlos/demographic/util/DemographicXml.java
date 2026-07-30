/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.util;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Builds legacy demographic XML fragments with escaped text nodes so
 * demographic form and import values cannot alter the stored XML structure.
 * The fragments are stored in VARCHAR columns rather than XML columns, so
 * callers must escape each text node before insertion. Escaping is performed
 * with Apache Commons Text {@link StringEscapeUtils#escapeXml11(String)} to
 * prevent XML injection.
 *
 * @since 2026-05-29
 */
public final class DemographicXml {

    private DemographicXml() {
    }

    /**
     * Builds the stored family doctor fragment for the demographic table.
     * The returned fragment is stored in {@code demographic.family_doctor};
     * all text content is XML-escaped before it is inserted into the fragment.
     *
     * @param referralDoctorOhip the referring doctor's OHIP number; null becomes empty text
     * @param referralDoctor the referring doctor's display name; null becomes empty text
     * @param familyDoctor the family doctor display name; null omits the optional element
     * @return a {@code <rdohip>}, {@code <rd>}, and optional {@code <family_doc>} XML fragment
     * @since 2026-05-29
     */
    public static String familyDoctor(String referralDoctorOhip, String referralDoctor, String familyDoctor) {
        return "<rdohip>" + escapeXmlText(referralDoctorOhip) + "</rdohip>" +
                "<rd>" + escapeXmlText(referralDoctor) + "</rd>" +
                (familyDoctor != null
                        ? "<family_doc>" + escapeXmlText(familyDoctor) + "</family_doc>"
                        : "");
    }

    /**
     * Wraps demographic notes in the legacy unotes element.
     * The returned fragment is stored in {@code demographiccust.notes};
     * the note text is XML-escaped before it is inserted into the fragment.
     *
     * @param notes the demographic note text; null becomes empty text
     * @return a {@code <unotes>} XML fragment
     * @since 2026-05-29
     */
    public static String userNotes(String notes) {
        return "<unotes>" + escapeXmlText(notes) + "</unotes>";
    }

    private static String escapeXmlText(String value) {
        if (value == null) {
            return "";
        }
        return StringEscapeUtils.escapeXml11(value);
    }
}
