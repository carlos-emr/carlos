/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.util;

import org.apache.commons.text.StringEscapeUtils;

/**
 * Builds legacy demographic XML fragments with escaped text nodes.
 */
public final class DemographicXml {

    private DemographicXml() {
    }

    /**
     * Builds the stored family doctor fragment for the demographic table.
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
