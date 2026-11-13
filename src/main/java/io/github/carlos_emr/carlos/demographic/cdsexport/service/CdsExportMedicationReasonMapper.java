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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The indications (reasons, {@code drugReason}) of one prescription. Every reason the chart shows
 * is exported and every other one is accounted; none holds the patient.
 *
 * <ul>
 *   <li>CARLOS shows a reason while its {@code archivedFlag} is 0 (the Rx profile, the
 *       prescription record, the flowsheet and BPMH all read {@code archivedFlag = false}).
 *       Each such reason travels whole in {@code CARLOS.Medication.Reasons.v1}: every column but
 *       the row id, the drug id and the patient, SQL NULL and empty text distinct, spaces escaped
 *       so the {@code xs:token} residual keeps them.</li>
 *   <li>CDS 5.2 ProblemCode is one ten-character code with no coding system. It carries the
 *       code of the reason that is the prescription's indication: the one active reason flagged
 *       primary, or, when none is flagged (the Rx page files its reasons unflagged), the only
 *       active reason. The code is written only when it is an ICD-9 code (coding system
 *       {@code icd9}, the system ProblemCode is read as) of one to ten printable characters
 *       without spaces; any other code, and every other reason, stays only in the residual.</li>
 *   <li>An archived reason (any other {@code archivedFlag}; the Rx reason page writes 1 with the
 *       archive reason) is shown nowhere in CARLOS and was never exported by the legacy exporter:
 *       every field is accounted {@code ARCHIVED_DRUG_REASON_NOT_SHOWN_IN_CHART}.</li>
 * </ul>
 *
 * <p>A reason of another drug or patient, or a malformed captured row, is a capture-scope
 * failure: the owning DAO already refuses reasons whose patient and drug owner disagree.</p>
 *
 * @since 2026-11-13
 */
public final class CdsExportMedicationReasonMapper {
    static final String TABLE = "drugReason";
    static final String RESIDUAL = "MedicationsAndTreatments/ResidualInfo";
    static final String PROBLEM_CODE = "ProblemCode";
    static final String ARCHIVED = "ARCHIVED_DRUG_REASON_NOT_SHOWN_IN_CHART";
    static final String CARRIED = "EXACT_NATIVE_DRUG_REASON";
    static final String SELECTED = "INDICATION_ICD9_CODE";
    static final String RETAINED = "ORIGINAL_PROBLEM_CODE_RETAINED";
    private static final Set<String> IDENTITY = Set.of("id", "drugId", "demographicNo");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * The reasons of one prescription: the ProblemCode candidate (or null), the residual content
     * (or null when the chart shows no reason) and a decision for every captured field. The
     * candidate's code is assessed as carried in the residual until the caller says whether
     * ProblemCode holds it ({@link #fields(boolean)}).
     */
    public record Result(
            String problemCode,
            String residual,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            CdsExportNativeFieldKeyDto problemCodeField) {
        /** A prescription without reasons. */
        public static final Result NONE = new Result(null, null, Map.of(), null);

        public Result {
            assessments = Map.copyOf(assessments);
            if ((problemCode == null) != (problemCodeField == null)
                    || problemCodeField != null && !assessments.containsKey(problemCodeField))
                throw new IllegalArgumentException("CDS_EXPORT_MEDICATION_REASON_RESULT_INVALID");
        }

        /**
         * Every field decision once the caller has decided ProblemCode. When it writes the
         * candidate, the candidate's code is mapped to ProblemCode; when an imported medication
         * keeps its original ProblemCode, the code stays in the residual only.
         */
        public Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields(
                boolean problemCodeWritten) {
            if (problemCodeField == null) {
                if (problemCodeWritten)
                    throw new IllegalStateException("CDS_EXPORT_MEDICATION_REASON_RESULT_INVALID");
                return assessments;
            }
            var result = new LinkedHashMap<>(assessments);
            var prior = assessments.get(problemCodeField);
            result.put(
                    problemCodeField,
                    new CdsExportFieldAssessmentDto(
                            prior.presence(),
                            Outcome.MAPPED,
                            problemCodeWritten ? PROBLEM_CODE : RESIDUAL,
                            problemCodeWritten ? SELECTED : RETAINED));
            return Map.copyOf(result);
        }

        @Override
        public String toString() {
            return "CdsExportMedicationReasonMapper.Result[protected]";
        }
    }

    public Result map(
            int patient, CdsExportCapturedTextRowDto drug, List<CdsExportCapturedTextRowDto> reasons)
            throws IOException {
        Objects.requireNonNull(reasons);
        if (patient < 1
                || drug == null
                || !drug.table().equals("drugs")
                || !Integer.toString(patient).equals(drug.fields().get("demographic_no"))) scope();
        String drugId = drug.fields().get("drugid");
        if (drugId == null || !CdsExportPatientTableSourceReader.key(integer(drugId)).equals(drug.recordKey()))
            scope();
        if (reasons.isEmpty()) return Result.NONE;
        var assessments = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var shown = new ArrayList<CdsExportCapturedTextRowDto>();
        var ids = new HashSet<String>();
        var columns = Set.copyOf(CdsExportPatientTableReadSupport.Plan.DRUG_REASONS.columns());
        for (var reason :
                reasons.stream()
                        .sorted(Comparator.comparing(CdsExportCapturedTextRowDto::recordKey))
                        .toList()) {
            if (reason == null
                    || !reason.table().equals(TABLE)
                    || !reason.fields().keySet().equals(columns)
                    || !ids.add(reason.recordKey())
                    || !CdsExportPatientTableSourceReader.key(integer(reason.fields().get("id")))
                            .equals(reason.recordKey())
                    || !drugId.equals(reason.fields().get("drugId"))
                    || !Integer.toString(patient).equals(reason.fields().get("demographicNo")))
                scope();
            var fields = new CdsExportNativeFieldAssessments(reason, assessments);
            fields.operational("id", "NATIVE_DRUG_REASON_IDENTITY");
            fields.operational("drugId", "VERIFIED_MEDICATION_PARENT_IDENTITY");
            fields.operational("demographicNo", "SEALED_PATIENT_IDENTITY");
            boolean active = "0".equals(reason.fields().get("archivedFlag"));
            for (String column : reason.fields().keySet()) {
                if (IDENTITY.contains(column)) continue;
                if (active) fields.set(column, Outcome.MAPPED, RESIDUAL, CARRIED);
                else fields.operational(column, ARCHIVED);
            }
            if (active) shown.add(reason);
        }
        if (shown.isEmpty()) return new Result(null, null, assessments, null);
        var carried = new ArrayList<Map<String, String>>();
        for (var reason : shown) {
            var fields = new TreeMap<String, String>();
            reason.fields()
                    .forEach(
                            (column, value) -> {
                                if (!IDENTITY.contains(column)) fields.put(column, value);
                            });
            carried.add(fields);
        }
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("reasons", carried);
        String residual = CdsExportScreeningMapper.tokenSafe(JSON.writeValueAsString(context));
        if (!CdsExportNativeFieldAssessments.legal(residual, 1 << 24))
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException("CDS_EXPORT_MEDICATION_REASON_REQUIRES_REVIEW");
        var indication = indication(shown);
        String code = indication == null ? null : indication.fields().get("code");
        if (indication == null || !problemCode(indication.fields().get("codingSystem"), code))
            return new Result(null, residual, assessments, null);
        return new Result(
                code,
                residual,
                assessments,
                new CdsExportNativeFieldKeyDto(TABLE, indication.recordKey(), "code"));
    }

    /**
     * The reason that is the prescription's indication: the only active reason flagged primary,
     * else the only active reason when none is flagged. Several candidates, or a primary flag
     * CARLOS does not write (anything but 0 or 1), name no single indication.
     */
    private static CdsExportCapturedTextRowDto indication(List<CdsExportCapturedTextRowDto> shown) {
        var primary = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var reason : shown) {
            String flag = reason.fields().get("primaryReasonFlag");
            if ("1".equals(flag)) primary.add(reason);
            else if (!"0".equals(flag)) return null;
        }
        if (primary.size() == 1) return primary.getFirst();
        return primary.isEmpty() && shown.size() == 1 ? shown.getFirst() : null;
    }

    /**
     * An ICD-9 code ProblemCode carries exactly: CARLOS stores the coding system as
     * {@code icd9}, and the code fits the ten-character element with no character a receiver
     * could trim or normalize.
     */
    static boolean problemCode(String codingSystem, String code) {
        return ("icd9".equals(codingSystem) || "ICD9".equals(codingSystem))
                && code != null
                && code.matches("[\\x21-\\x7E]{1,10}");
    }

    private static long integer(String value) throws IOException {
        if (value == null) scope();
        try {
            int number = Integer.parseInt(value);
            if (!Integer.toString(number).equals(value)) scope();
            return number;
        } catch (NumberFormatException invalid) {
            scope();
            return 0;
        }
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_MEDICATION_REASON_SCOPE_INVALID");
    }
}
