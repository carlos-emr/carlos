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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Admission;

import java.util.List;

/**
 * Static helper methods for demographic edit and add JSP pages.
 *
 * <p>These methods were extracted from {@code <%! %>} declaration blocks in
 * {@code demographiceditdemographic.jsp} so they can be shared across
 * {@code <jsp:include>} fragments (which compile as separate classes and
 * cannot access the parent JSP's declaration-scope methods).</p>
 *
 * @since 2026-04-04
 */
public final class DemographicEditHelper {

    private DemographicEditHelper() {
        // utility class
    }

    /**
     * Returns an HTML {@code disabled="disabled"} attribute string if the given
     * demographic field is configured as disabled in {@code CarlosProperties}.
     *
     * <p>Checks the property {@code demographic.edit.<fieldName>} for the value
     * {@code "disabled"}.</p>
     *
     * @param fieldName String the demographic field name (e.g. "lastName", "hin")
     * @return String either {@code " disabled=\"disabled\" "} or {@code ""}
     */
    public static String getDisabled(String fieldName) {
        String val = CarlosProperties.getInstance().getProperty("demographic.edit." + fieldName, "");
        if (val != null && val.equals("disabled")) {
            return " disabled=\"disabled\" ";
        }
        return "";
    }

    /**
     * Returns an HTML {@code selected="selected"} attribute if the given admission
     * matches the specified program ID.
     *
     * @param admission Admission the admission to check (may be null)
     * @param programId Integer the program ID to match against
     * @return String either {@code " selected=\"selected\" "} or {@code ""}
     */
    public static String isProgramSelected(Admission admission, Integer programId) {
        if (admission != null && admission.getProgramId() != null && admission.getProgramId().equals(programId)) {
            return " selected=\"selected\" ";
        }
        return "";
    }

    /**
     * Returns an HTML {@code checked="checked"} attribute if any admission in the
     * list matches the specified program ID.
     *
     * @param admissions List&lt;Admission&gt; the admissions to search
     * @param programId Integer the program ID to match against
     * @return String either {@code " checked=\"checked\" "} or {@code ""}
     */
    public static String isProgramSelected(List<Admission> admissions, Integer programId) {
        for (Admission admission : admissions) {
            if (admission.getProgramId() != null && admission.getProgramId().equals(programId)) {
                return " checked=\"checked\" ";
            }
        }
        return "";
    }

    /**
     * Converts a time string in {@code HH:mm:ss} format to elapsed minutes
     * from midnight.
     *
     * @param timeStr String the time in {@code HH:mm:ss} format
     * @return int the elapsed minutes from 00:00:00
     */
    public static int timeStrToMins(String timeStr) {
        String[] temp = timeStr.split(":");
        return Integer.parseInt(temp[0]) * 60 + Integer.parseInt(temp[1]);
    }
}
