/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.util;

import org.apache.commons.text.StringEscapeUtils;

public final class DemographicXml {

    private DemographicXml() {
    }

    public static String familyDoctor(String referralDoctorOhip, String referralDoctor, String familyDoctor) {
        return "<rdohip>" + escapeXmlText(referralDoctorOhip) + "</rdohip>" +
                "<rd>" + escapeXmlText(referralDoctor) + "</rd>" +
                (familyDoctor != null
                        ? "<family_doc>" + escapeXmlText(familyDoctor) + "</family_doc>"
                        : "");
    }

    public static String userNotes(String notes) {
        return "<unotes>" + escapeXmlText(notes) + "</unotes>";
    }

    private static String escapeXmlText(String value) {
        return StringEscapeUtils.escapeXml11(value);
    }
}
