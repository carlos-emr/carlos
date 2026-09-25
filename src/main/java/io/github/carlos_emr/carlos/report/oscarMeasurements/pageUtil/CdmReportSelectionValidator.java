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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Checks the measurement type and measuring instruction a CDM report form posts for each row
 * against the rows the server rendered for the selected CDM group.
 *
 * <p>The report forms carry the type and instruction of every row as hidden
 * {@code value(measurementTypeN)} and {@code value(mInstrcsCheckboxNM)} fields. They are only
 * echoes of what {@link RptSelectCDMReport2Action} stored in the session as
 * {@code measurementTypes}, but a {@code _report} reader could alter the POST and have a report
 * action run its patient-wide queries for any type or instruction. The actions therefore accept a
 * row's selectors only when they match the session definitions for that row index and skip the
 * row otherwise, as they already do for an unsupported above/below comparator.</p>
 *
 * @since 2026-09-25
 */
public final class CdmReportSelectionValidator {

    /** Session attribute holding the {@link RptMeasurementTypesBeanHandler} of the selected group. */
    static final String SESSION_MEASUREMENT_TYPES = "measurementTypes";

    private final RptMeasurementTypesBeanHandler definitions;

    CdmReportSelectionValidator(RptMeasurementTypesBeanHandler definitions) {
        this.definitions = definitions;
    }

    /**
     * Builds a validator from the {@code measurementTypes} the CDM setup page stored in the
     * session. Without a session or a stored handler no row can be accepted.
     */
    public static CdmReportSelectionValidator fromSession(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        Object stored = session == null ? null : session.getAttribute(SESSION_MEASUREMENT_TYPES);
        return new CdmReportSelectionValidator(
                stored instanceof RptMeasurementTypesBeanHandler handler ? handler : null);
    }

    /**
     * Whether {@code measurementType} is the type the server rendered for form row {@code row}.
     *
     * @param row the 0-based row index the form posted (a {@code *Checkbox} value)
     * @param measurementType the posted {@code value(measurementTypeN)} field
     */
    public boolean isMeasurementType(int row, String measurementType) {
        if (definitions == null || measurementType == null) {
            return false;
        }
        List<RptMeasurementTypesBean> rows = definitions.getMeasurementTypeVector();
        return row >= 0 && row < rows.size() && measurementType.equals(rows.get(row).getType());
    }

    /**
     * Whether {@code measuringInstruction} is one of the instructions the server rendered for form
     * row {@code row}.
     *
     * @param row the 0-based row index the form posted
     * @param measuringInstruction the posted {@code value(mInstrcsCheckboxNM)} field
     */
    public boolean isMeasuringInstruction(int row, String measuringInstruction) {
        if (definitions == null || measuringInstruction == null) {
            return false;
        }
        List<RptMeasuringInstructionBeanHandler> rows = definitions.getMeasuringInstrcBeanVector();
        if (row < 0 || row >= rows.size()) {
            return false;
        }
        for (RptMeasuringInstructionBean bean : rows.get(row).getMeasuringInstrcVector()) {
            if (measuringInstruction.equals(bean.getMeasuringInstrc())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience for the report loops: the posted type when it matches the row, else
     * {@code null} after a warning. The warning names no value, since the value is untrusted.
     */
    public String acceptedMeasurementType(int row, String measurementType) {
        if (isMeasurementType(row, measurementType)) {
            return measurementType;
        }
        MiscUtils.getLogger().warn("CDM report: rejected a measurement type that is not part of the selected group");
        return null;
    }

    /**
     * Convenience for the report loops: the posted instruction when it matches the row, else
     * {@code null}. An unposted (unticked) instruction is {@code null} in and out, silently.
     */
    public String acceptedMeasuringInstruction(int row, String measuringInstruction) {
        if (measuringInstruction == null || isMeasuringInstruction(row, measuringInstruction)) {
            return measuringInstruction;
        }
        MiscUtils.getLogger().warn("CDM report: rejected a measuring instruction that is not part of the selected group");
        return null;
    }
}
