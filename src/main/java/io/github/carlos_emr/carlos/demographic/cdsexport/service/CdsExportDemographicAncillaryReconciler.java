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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptDto;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;

/**
 * Closes the explicitly reviewed ancillary base-row field identities after demographic
 * reconcilers. Absent values remain absent; populated values without a qualified mapping
 * require review. Operational identity metadata is never presented as clinical history.
 * This boundary does not claim associated-row or retained-source completeness.
 */
public final class CdsExportDemographicAncillaryReconciler {
    private static final Set<String> OPERATIONAL =
            Set.of(
                    "demographic_no",
                    "date_joined",
                    "lastUpdateDate",
                    "lastUpdateUser",
                    "cds_identity_hin",
                    "cds_identity_issuer");
    private static final Set<String> ANCILLARY =
            Set.of(
                    "alias",
                    "anonymous",
                    "children",
                    "citizenship",
                    "consentToUseEmailForCare",
                    "country_of_origin",
                    "eff_date",
                    "end_date",
                    "family_doctor",
                    "gender",
                    "genderId",
                    "myOscarUserName",
                    "newsletter",
                    "pcn_indicator",
                    "pref_name",
                    "previousAddress",
                    "pronoun",
                    "pronounId",
                    "sourceOfIncome");

    /**
     * Requires the exact captured row witness and disjoint prior ownership. Every base-row
     * field must already be decided or explicitly deferred; missing fields cannot be added
     * silently by this final base-row pass. Other tables and source IDs remain mandatory
     * hand-offs. Populated ancillary values withhold every proposed XML change.
     */
    public CdsExportDemographicRetainedCoreReconciler.Result reconcile(
            CdsExportDemographicRetainedCoreReconciler.Result prior,
            int patient,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(prior);
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || current == null
                || !"demographic".equals(current.table())
                || !Integer.toString(patient).equals(current.fields().get("demographic_no")))
            scope();
        CdsExportCapturedTextWitness.require(current, witness, "demographic_no", checkpoint);
        var decisions = assess(current, checkpoint);
        var fields = new LinkedHashMap<>(prior.fields());
        var deferred = new HashSet<>(prior.deferredNativeFields());
        for (String column : current.fields().keySet()) {
            checkpoint.run();
            var key = new CdsExportNativeFieldKeyDto(current.table(), current.recordKey(), column);
            boolean decided = fields.containsKey(key), handedOff = deferred.contains(key);
            if (decided && handedOff || !decided && !handedOff && !column.equals("demographic_no"))
                scope();
            if (decided) {
                String value = current.fields().get(column);
                Presence actual =
                        value == null
                                ? Presence.SQL_NULL
                                : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
                var assessment = fields.get(key);
                if (assessment.presence() != actual
                        || assessment.rule().isBlank()
                        || assessment.outcome() == Outcome.ABSENT && actual == Presence.VALUE
                        || assessment.outcome() == Outcome.MAPPED
                                && assessment.destination().isBlank()) scope();
            }
            var candidate = decisions.get(key);
            if (candidate == null) {
                if (!decided) scope();
            } else {
                if (decided) scope();
                fields.put(key, candidate);
                deferred.remove(key);
            }
        }
        for (var key : fields.keySet()) {
            checkpoint.run();
            if (key.table().equals("demographic")
                    && (!key.recordKey().equals(current.recordKey())
                            || !current.fields().containsKey(key.column()))) scope();
        }
        for (var key : deferred) {
            checkpoint.run();
            if (key.table().equals("demographic")) scope();
        }
        boolean ready =
                prior.coreRepresentable()
                        && decisions.values().stream()
                                .noneMatch(value -> value.outcome() == Outcome.REQUIRES_REVIEW);
        var result =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        prior.sourceManifestSha256(),
                        prior.unitAddress(),
                        prior.decidedSourceFields(),
                        prior.deferredSourceFields(),
                        fields,
                        deferred,
                        prior.blockers(),
                        ready ? prior.coreChanges() : List.of(),
                        prior.providerUses());
        checkpoint.run();
        return result;
    }

    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assess(
            CdsExportCapturedTextRowDto current, Runnable checkpoint) throws IOException {
        if (!"demographic".equals(current.table())) scope();
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var entry : current.fields().entrySet()) {
            checkpoint.run();
            String column = entry.getKey(), value = entry.getValue();
            if (!OPERATIONAL.contains(column) && !ANCILLARY.contains(column)) continue;
            Presence presence =
                    value == null
                            ? Presence.SQL_NULL
                            : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
            Outcome outcome =
                    OPERATIONAL.contains(column)
                            ? Outcome.OPERATIONAL
                            : presence == Presence.VALUE ? Outcome.REQUIRES_REVIEW : Outcome.ABSENT;
            String rule =
                    OPERATIONAL.contains(column)
                            ? "CAPTURED_OPERATIONAL_OR_DERIVED_IDENTITY"
                            : presence == Presence.VALUE
                                    ? "ANCILLARY_NATIVE_VALUE_REQUIRES_REVIEW"
                                    : "ABSENT_ANCILLARY_NATIVE_VALUE";
            if (column.equals("family_doctor")
                    && ("<rdohip></rdohip><rd></rd>".equals(value)
                            || "<rdohip></rdohip><rd></rd><family_doc></family_doc>"
                                    .equals(value))) {
                outcome = Outcome.OPERATIONAL;
                rule = "NATIVE_EMPTY_REFERRING_PHYSICIAN_WRAPPER";
            }
            fields.put(
                    new CdsExportNativeFieldKeyDto(current.table(), current.recordKey(), column),
                    new CdsExportFieldAssessmentDto(presence, outcome, "", rule));
        }
        checkpoint.run();
        return Map.copyOf(fields);
    }

    /**
     * Migration guide 18.2 transfers the current demographic snapshot. Superseded snapshot
     * attributes stay in the separately approved audit arrangement; they must not overwrite
     * the current chart. Roster episodes are clinical history and are deliberately excluded
     * here so that the enrolment mapper must account for every one of them.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessArchive(
            int patient, CdsExportCapturedTextRowDto archive, Runnable checkpoint)
            throws IOException {
        CdsExportEnrolmentMapper.requireRow(patient, archive);
        if (!archive.table().equals("demographicArchive")) scope();
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var entry : archive.fields().entrySet()) {
            checkpoint.run();
            if (CdsExportEnrolmentMapper.COLUMNS.containsKey(entry.getKey())) continue;
            String value = entry.getValue();
            result.put(
                    new CdsExportNativeFieldKeyDto(
                            archive.table(), archive.recordKey(), entry.getKey()),
                    new CdsExportFieldAssessmentDto(
                            value == null
                                    ? Presence.SQL_NULL
                                    : value.isEmpty() ? Presence.EMPTY : Presence.VALUE,
                            Outcome.OPERATIONAL,
                            "",
                            "SUPERSEDED_DEMOGRAPHIC_SNAPSHOT_IN_APPROVED_AUDIT"));
        }
        return Map.copyOf(result);
    }

    /**
     * A former most-responsible physician is named only by a superseded archive snapshot's
     * {@code provider_no}: {@code DemographicUpdate2Action} archives the patient row on every
     * edit, so a change of physician leaves the previous physician in older archive rows. The
     * captured public provider row belongs to the same approved audit arrangement as that
     * snapshot column ({@link #assessArchive}) and is accounted for under it, instead of being
     * left without a decision and failing the job's field closure. A physician who is also the
     * current or an enrolled physician keeps that mapped use when the assemblers combine the
     * uses of one shared provider row.
     *
     * @return one OPERATIONAL decision per captured column of each provider an archive snapshot
     *     names exactly; nothing for an unmatched reference, which stays decided by the archive
     *     column itself
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>
            assessArchivedPrimaryPhysicians(
                    int patient,
                    List<CdsExportCapturedTextRowDto> archives,
                    Map<String, CdsExportCapturedTextRowDto> providers,
                    Runnable checkpoint)
                    throws IOException {
        Objects.requireNonNull(providers);
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var archive : archives) {
            checkpoint.run();
            CdsExportEnrolmentMapper.requireRow(patient, archive);
            if (!archive.table().equals("demographicArchive")) scope();
            String id = archive.fields().get("provider_no");
            var provider = id == null || id.isEmpty() ? null : providers.get(id);
            if (provider == null) continue;
            if (!provider.table().equals("provider")
                    || !id.equals(provider.fields().get("provider_no"))) scope();
            for (var entry : provider.fields().entrySet()) {
                String value = entry.getValue();
                result.putIfAbsent(
                        new CdsExportNativeFieldKeyDto(
                                provider.table(), provider.recordKey(), entry.getKey()),
                        new CdsExportFieldAssessmentDto(
                                value == null
                                        ? Presence.SQL_NULL
                                        : value.isEmpty() ? Presence.EMPTY : Presence.VALUE,
                                Outcome.OPERATIONAL,
                                "",
                                "SUPERSEDED_PRIMARY_PHYSICIAN_IN_APPROVED_AUDIT"));
            }
        }
        return Map.copyOf(result);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ANCILLARY_SCOPE_INVALID");
    }
}
