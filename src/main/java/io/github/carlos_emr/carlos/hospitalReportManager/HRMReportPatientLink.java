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
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.util.List;

import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;

/**
 * Which of an HRM report's patient links the report page acts for.
 *
 * <p>The report page's patient buttons (Tickler, Msg, E-Chart, Master Record, Appt History) act for one
 * linked patient. A report can be linked to more than one patient (older or imported data; the page itself
 * replaces the link). The links come back in no set order, so taking the first one could act for the other
 * patient when the report was opened from a patient's own chart (#5060). The patient's own HRM links say
 * which patient they came from; that patient is used when the report is linked to them.</p>
 *
 * @since 2026-10-19
 */
public final class HRMReportPatientLink {

    private HRMReportPatientLink() {
    }

    /**
     * The link for the requested patient when the report is linked to them, otherwise the first link.
     *
     * @param links the report's patient links, possibly empty or null
     * @param requestedDemographicNo the patient the page was opened for ({@code demographicNo}), or null
     * @return the chosen link, or null when the report has no patient link
     */
    public static HRMDocumentToDemographic choose(List<HRMDocumentToDemographic> links, String requestedDemographicNo) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        Integer requested = parsePatientNumber(requestedDemographicNo);
        if (requested != null) {
            for (HRMDocumentToDemographic link : links) {
                if (requested.equals(link.getDemographicNo())) {
                    return link;
                }
            }
        }
        // Not given, not a patient number, or not one of this report's patients: as before.
        return links.get(0);
    }

    private static Integer parsePatientNumber(String value) {
        if (value == null || !value.matches("\\d{1,9}")) {
            return null;
        }
        return Integer.valueOf(value);
    }
}
