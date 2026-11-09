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

import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Proves fixed demographic projection groups against the sealed preparation and historical
 * native witnesses. A source preparation alone never proves that an existing matched chart
 * was overwritten. This is dependency evidence only: effective source values (including
 * corrections and synthetic shifts), associated rows, history and output need separate
 * reconciliation. Unproved/default/operational columns never acquire a clinical mapping.
 */
public final class CdsExportDemographicProjectionEvidence {
    public enum Group {
        GIVEN("first_name"),
        FAMILY("last_name"),
        OTHER_GIVEN("middleNames"),
        BIRTH("year_of_birth", "month_of_birth", "date_of_birth"),
        HEALTH_CARD("hin", "hc_type", "ver", "hc_renew_date"),
        GENDER("sex"),
        STATUS("patient_status", "patient_status_date"),
        CHART("chart_no"),
        EMAIL("email"),
        PREFIX("title"),
        OFFICIAL_LANGUAGE("official_lang"),
        SPOKEN_LANGUAGE("spoken_lang"),
        SOCIAL_INSURANCE("sin"),
        PRIMARY_PROVIDER("provider_no"),
        MAILING_ADDRESS("address", "city", "province", "postal"),
        RESIDENTIAL_ADDRESS(
                "residentialAddress",
                "residentialCity",
                "residentialProvince",
                "residentialPostal"),
        HOME_PHONE("phone"),
        WORK_PHONE("phone2"),
        ROSTER(
                "roster_status",
                "roster_date",
                "roster_termination_date",
                "roster_termination_reason",
                "roster_enrolled_to");
        private final Set<String> columns;

        Group(String... columns) {
            this.columns = Set.of(columns);
        }

        public Set<String> columns() {
            return columns;
        }
    }

    public record Projection(
            Set<String> columns, boolean preparedProjectionProven, Set<String> changedColumns) {
        public Projection {
            columns = Set.copyOf(columns);
            changedColumns = Set.copyOf(changedColumns);
        }

        public boolean unchanged() {
            return preparedProjectionProven && changedColumns.isEmpty();
        }

        @Override
        public String toString() {
            return "CdsExportDemographicProjectionEvidence.Projection[protected]";
        }
    }

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsDemographicNativeDto preparation,
            Map<Group, Projection> groups,
            Set<String> deferredColumns,
            Set<String> unqualifiedOperationalColumns) {
        public Result {
            groups = Map.copyOf(groups);
            deferredColumns = Set.copyOf(deferredColumns);
            unqualifiedOperationalColumns = Set.copyOf(unqualifiedOperationalColumns);
        }

        @Override
        public String toString() {
            return "CdsExportDemographicProjectionEvidence.Result[protected]";
        }
    }

    public Result verify(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || address == null
                || !address.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || index == null
                || checked == null
                || !index.sourceManifestSha256().equals(checked.sourceManifestSha256())
                || !index.appliedUnits().equals(index.references().keySet())
                || !index.appliedUnits().equals(checked.units().keySet())
                || !index.appliedUnits().contains(address)) scope();
        var preparation =
                new CdsExportRetainedPreparationReader().demographic(index, address).value();
        String run = address.substring(0, 36), key = address.substring(37);
        var unit =
                index.tables()
                        .getOrDefault("cds_import_unit", Map.of())
                        .get(CdsImportKeys.hash(run, key));
        if (unit == null || !"1".equals(unit.fields().get("ordinal"))) scope();
        String patientKey = unit.fields().get("patient_key");
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}")) scope();
        var patientRow =
                index.tables()
                        .getOrDefault("cds_import_patient", Map.of())
                        .get(CdsImportKeys.hash(run, patientKey));
        var origin = index.tables().getOrDefault("cds_import_run", Map.of()).get(run);
        if (patientRow == null
                || origin == null
                || !run.equals(patientRow.fields().get("run_id"))
                || !patientKey.equals(patientRow.fields().get("patient_key"))
                || !Integer.toString(patient)
                        .equals(patientRow.fields().get("target_demographic_no"))
                || !"0".equals(patientRow.fields().get("held"))
                || preparation.sourceId() == null
                || !preparation.sourceId().equals(patientRow.fields().get("source_patient_id")))
            scope();
        String lineage = patientRow.fields().get("lineage_key");
        if (lineage == null
                || !lineage.equals(unit.fields().get("lineage_key"))
                || origin.fields().get("source_namespace") == null
                || patientRow.fields().get("copy_key") == null
                || !lineage.equals(
                        CdsImportKeys.hash(
                                origin.fields().get("source_namespace"),
                                preparation.sourceId(),
                                patientRow.fields().get("copy_key")))
                || !key.equals(
                        CdsImportKeys.hash(
                                lineage,
                                "/OmdCds[1]/PatientRecord[1]/Demographics[1]",
                                "NATIVE",
                                "0"))) scope();
        var references = checked.units().get(address);
        if (references == null
                || !references.stream()
                        .map(CdsExportRetainedReceiptVerificationDto.CheckedReference::expected)
                        .toList()
                        .equals(index.references().get(address))) scope();
        var demographic =
                references.stream()
                        .filter(ref -> ref.expected().table().equals("demographic"))
                        .toList();
        if (demographic.size() != 1) scope();
        var reference = demographic.getFirst();
        if (reference.reason() == Reason.ROW_NOT_IN_CAPTURE)
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_DELETION_REQUIRES_REVIEW");
        if (current == null
                || witness == null
                || witness.state() == null
                || witness.partial()
                || reference.currentDigest() == null)
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NATIVE_WITNESS_REQUIRED");
        String id = Integer.toString(patient);
        if (!"demographic".equals(current.table())
                || !"demographic".equals(witness.table())
                || !id.equals(witness.id())
                || !id.equals(current.fields().get("demographic_no"))
                || !current.recordKey().equals(String.format(Locale.ROOT, "%010d", patient))
                || !current.recordKey().equals(witness.recordKey())
                || !id.equals(reference.expected().id())
                || !witness.digest().equals(reference.currentDigest())
                || !current.exactNumbers().isEmpty()
                || !current.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        for (var column : witness.state().columns()) {
            checkpoint.run();
            if (!matches(witness.state(), column.name(), current.fields().get(column.name())))
                scope();
        }
        for (var entry : index.references().entrySet()) {
            checkpoint.run();
            if (!entry.getKey().equals(address)
                    && entry.getValue().stream()
                            .anyMatch(
                                    ref -> ref.table().equals("demographic") && ref.id().equals(id))
                    && !association(
                            patient,
                            address,
                            patientKey,
                            lineage,
                            entry.getKey(),
                            index,
                            checked,
                            witness.digest()))
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_ALIAS_REQUIRES_REVIEW");
        }
        CdsNativeRowState baseline = null;
        if (unit.fields().get("native_baseline") != null) {
            try {
                var decoded =
                        CdsNativeBaselineCodec.decode(
                                unit.fields().get("native_baseline"),
                                unit.fields().get("target_references"),
                                unit.fields().get("target_digest"));
                baseline =
                        decoded.rows().stream()
                                .filter(
                                        row ->
                                                row.table().equals("demographic")
                                                        && row.id().equals(id))
                                .findFirst()
                                .orElse(null);
            } catch (IllegalArgumentException malformed) {
                scope();
            }
        } else if (reference.reason() == Reason.UNCHANGED_ROW) baseline = witness.state();
        if (baseline == null || !baseline.digest().equals(reference.expected().digest()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_FIELD_BASELINE_REQUIRED");
        var difference = CdsNativeRowComparison.compare(baseline, witness.state());
        if (difference.schemaChanged()
                || !Set.of("lastUpdateDate").containsAll(difference.unqualifiedColumns()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_FIELD_CODEC_REQUIRES_REVIEW");
        var expected = expected(preparation);
        var groups = new EnumMap<Group, Projection>(Group.class);
        var deferred = new HashSet<>(current.fields().keySet());
        deferred.remove("demographic_no");
        if (!matches(baseline, "demographic_no", id)) scope();
        for (var group : Group.values()) {
            checkpoint.run();
            boolean proven = true;
            var changed = new HashSet<String>();
            for (String column : group.columns()) {
                proven &= matches(baseline, column, expected.get(column));
                if (difference.changedColumns().contains(column)) changed.add(column);
            }
            deferred.removeAll(group.columns());
            groups.put(group, new Projection(group.columns(), proven, changed));
        }
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                address,
                preparation,
                groups,
                deferred,
                difference.unqualifiedColumns());
    }

    /** This mirrors only the reviewed writer assignments. No chart-creation claim follows from matching a subset. */
    private static Map<String, String> expected(CdsDemographicNativeDto value) {
        var result = new HashMap<String, String>();
        var scalars = value.scalars();
        result.put("first_name", value.firstName());
        result.put("last_name", value.lastName());
        result.put("middleNames", scalars.get(CdsDemographicNativeDto.MIDDLE_NAMES_SCALAR));
        var birth = LocalDate.parse(value.birthDate());
        result.put("year_of_birth", String.format(Locale.ROOT, "%04d", birth.getYear()));
        result.put("month_of_birth", String.format(Locale.ROOT, "%02d", birth.getMonthValue()));
        result.put("date_of_birth", String.format(Locale.ROOT, "%02d", birth.getDayOfMonth()));
        result.put("hin", value.healthNumber() == null ? "" : value.healthNumber());
        result.put("hc_type", value.issuer() == null ? "" : value.issuer());
        result.put("ver", scalars.get("HealthCard/Version"));
        result.put("hc_renew_date", value.details().healthCardExpiry());
        result.put("sex", value.gender());
        result.put("patient_status", value.status() == null ? "" : value.status());
        result.put("patient_status_date", value.details().statusDate());
        result.put("chart_no", scalars.getOrDefault("ChartNumber", ""));
        result.put("email", scalars.get("Email"));
        result.put("title", scalars.get("Names/NamePrefix"));
        result.put("sin", scalars.get("SIN"));
        String official = scalars.get("PreferredOfficialLanguage");
        result.put(
                "official_lang",
                "ENG".equals(official) ? "English" : "FRE".equals(official) ? "French" : official);
        result.put("spoken_lang", value.details().spokenLanguage());
        result.put("provider_no", value.primaryProvider() == null ? "" : value.primaryProvider());
        address(result, value.mailing(), false);
        address(result, value.residential(), true);
        result.put("phone", value.home() == null ? "" : value.home().number());
        result.put("phone2", value.work() == null ? "" : value.work().number());
        var roster = value.details().roster();
        result.put(
                "roster_status", roster == null || roster.status() == null ? "" : roster.status());
        result.put("roster_date", roster == null ? null : roster.date());
        result.put("roster_termination_date", roster == null ? null : roster.terminationDate());
        result.put("roster_termination_reason", roster == null ? null : roster.terminationReason());
        result.put("roster_enrolled_to", roster == null ? null : roster.enrolledProvider());
        return result;
    }

    private static void address(
            Map<String, String> result,
            CdsDemographicNativeDto.Address address,
            boolean residential) {
        result.put(
                residential ? "residentialAddress" : "address",
                address == null ? null : address.address());
        result.put(
                residential ? "residentialCity" : "city", address == null ? null : address.city());
        String province = address == null ? null : address.province();
        if (!residential) {
            province = org.apache.commons.lang3.StringUtils.trimToNull(province);
            if (province != null) province = province.toUpperCase(Locale.ROOT);
        }
        result.put(residential ? "residentialProvince" : "province", province);
        result.put(
                residential ? "residentialPostal" : "postal",
                address == null ? null : address.postal());
    }

    private static boolean matches(CdsNativeRowState state, String column, String expected)
            throws IOException {
        int ordinal = -1;
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(column)) ordinal = i;
        if (ordinal < 0) scope();
        var cell = state.cells().get(ordinal);
        if (expected == null) return cell.sqlNull();
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    /**
     * The importer does not mutate demographics for these two structural parent units.
     * Their single receipt proves patient association and must never select demographic
     * source values or weaken the rejection of a second actual demographic projection.
     */
    static boolean association(
            int patient,
            String demographicAddress,
            String patientKey,
            String lineage,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            String currentDigest) {
        if (patient < 1
                || address == null
                || !address.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || patientKey == null
                || lineage == null
                || demographicAddress == null
                || !demographicAddress.matches(
                        "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || !address.substring(0, 36).equals(demographicAddress.substring(0, 36))
                || !index.appliedUnits().contains(address)
                || currentDigest == null) return false;
        String run = address.substring(0, 36),
                key = address.substring(37),
                id = CdsImportKeys.hash(run, key);
        var unit = index.tables().getOrDefault("cds_import_unit", Map.of()).get(id);
        if (unit == null
                || !id.equals(unit.recordKey())
                || !id.equals(unit.fields().get("id"))
                || !run.equals(unit.fields().get("run_id"))
                || !key.equals(unit.fields().get("unit_key"))
                || !Objects.equals(patientKey, unit.fields().get("patient_key"))
                || !Objects.equals(lineage, unit.fields().get("lineage_key"))
                || !("COMMITTED".equals(unit.fields().get("state"))
                        || "ALREADY_COMPLETE".equals(unit.fields().get("state")))) return false;
        String category = unit.fields().get("category"), ordinal = unit.fields().get("ordinal");
        if (category == null || !Set.of("CareElements", "NewCategory").contains(category))
            return false;
        try {
            long value = Long.parseLong(ordinal);
            if (value < 1 || !Long.toString(value).equals(ordinal)) return false;
        } catch (NumberFormatException malformed) {
            return false;
        }
        String path = "/OmdCds[1]/PatientRecord[1]/" + category + "[" + ordinal + "]";
        if (!key.equals(CdsImportKeys.hash(lineage, path, "NATIVE", "0"))) return false;
        var refs = index.references().get(address);
        var proof = checked.units().get(address);
        if (refs == null
                || refs.size() != 1
                || !"demographic".equals(refs.getFirst().table())
                || !Integer.toString(patient).equals(refs.getFirst().id())
                || proof == null
                || proof.size() != 1
                || !refs.equals(
                        proof.stream()
                                .map(
                                        CdsExportRetainedReceiptVerificationDto.CheckedReference
                                                ::expected)
                                .toList())
                || !currentDigest.equals(proof.getFirst().currentDigest())
                || proof.getFirst().reason() != Reason.UNCHANGED_ROW
                        && proof.getFirst().reason() != Reason.CHANGED_ROW) return false;
        String parent = demographicAddress.substring(37),
                edgeId = CdsImportKeys.hash(run, key, parent);
        var edge = index.tables().getOrDefault("cds_import_dependency", Map.of()).get(edgeId);
        return edge != null
                && edgeId.equals(edge.recordKey())
                && edgeId.equals(edge.fields().get("id"))
                && run.equals(edge.fields().get("run_id"))
                && key.equals(edge.fields().get("unit_key"))
                && parent.equals(edge.fields().get("depends_on_unit_key"));
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PROJECTION_SCOPE_INVALID");
    }
}
