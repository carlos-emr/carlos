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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mechanical projections for finite patient-owned source tables. Each DAO supplies its own
 * compile-time plan; no client SQL, identifier or generic cross-entity query is accepted.
 * Character/date/integer values use exact SQL binary casts. Prescription FLOAT columns
 * additionally require the owning DAO's server-binary read; SQL display alone is insufficient.
 * Binary and session-dependent timestamp mappings still require their own reviewed codecs.
 */
public final class CdsExportPatientTableReadSupport {
    public enum Plan {
        CASE_NOTES("casemgmt_note", "note_id", "demographic_no", List.of("appointmentNo", "archived", "billing_code", "demographic_no", "encounter_type", "history", "hourOfEncTransportationTime", "hourOfEncounterTime", "include_issue_innote", "locked", "minuteOfEncTransportationTime", "minuteOfEncounterTime", "note", "note_id", "observation_date", "password", "position", "program_no", "provider_no", "reporter_caisi_role", "reporter_program_team", "signed", "signing_provider_no", "update_date", "uuid")),
        PREVENTIONS_FULL("preventions", "id", null, List.of("creation_date", "creator", "deleted", "demographic_no", "id", "lastUpdateDate", "never", "next_date", "prevention_date", "prevention_type", "programNo", "provider_name", "provider_no", "refused", "restrictToProgram", "snomedId")),
        PREVENTIONS_EXT("preventionsExt", "id", null, List.of("id", "keyval", "prevention_id", "val")),
        PREVENTION_PRECISION("partial_date", "id", null, List.of("field_name", "format", "id", "table_id", "table_name")),
        PREVENTION_SCOPE("preventions", "id", "demographic_no", List.of("demographic_no", "id", "prevention_type", "programNo", "restrictToProgram", "snomedId")),
        CVC_IMMUNIZATION("CVCImmunization", "id", null, List.of("displayName", "generic", "id", "ispa", "parentConceptId", "picklistName", "prevalence", "snomedConceptId", "versionId")),
        CVC_MAPPING("CVCMapping", "id", null, List.of("cvcSnomedId", "id", "oscarName", "preferCVC")),
        PUBLIC_PROVIDERS("provider", "provider_no", null, List.of("first_name", "last_name", "ohip_no", "practitionerNo", "practitionerNoType", "provider_no")),
        DEMOGRAPHIC_MERGES("demographic_merged", "id", null, List.of("deleted", "demographic_no", "id", "lastUpdateDate", "lastUpdateUser", "merged_to")),
        DEMOGRAPHIC_ARCHIVE("demographicArchive", "id", "demographic_no", List.of(
                "address", "alias", "anonymous", "chart_no", "children", "citizenship", "city", "country_of_origin",
                "date_joined", "date_of_birth", "demographic_no", "eff_date", "email", "end_date", "family_doctor", "first_name",
                "gender", "genderId", "hc_renew_date", "hc_type", "hin", "id", "lastUpdateDate", "lastUpdateUser",
                "last_name", "middleNames", "month_of_birth", "myOscarUserName", "newsletter", "official_lang", "patient_status", "patient_status_date",
                "pcn_indicator", "phone", "phone2", "postal", "pref_name", "previousAddress", "pronoun", "pronounId",
                "provider_no", "province", "residentialAddress", "residentialCity", "residentialPostal", "residentialProvince", "roster_date", "roster_enrolled_to",
                "roster_status", "roster_termination_date", "roster_termination_reason", "sex", "sin", "sourceOfIncome", "spoken_lang", "title",
                "ver", "year_of_birth")),
        APPOINTMENT_ARCHIVE("appointmentArchive", "id", "demographic_no", List.of(
                "appointment_date", "appointment_no", "billing", "bookingSource", "createdatetime", "creator", "creatorSecurityId", "demographic_no", "end_time",
                "id", "imported_status", "lastupdateuser", "location", "name", "notes", "program_id", "provider_no", "reason", "remarks", "resources",
                "start_time", "status", "style", "type", "updatedatetime", "urgency")),
        APPOINTMENT_STATUSES("appointment_status", "id", null, List.of(
                "active", "color", "description", "editable", "icon", "id", "short_letter_colour", "short_letters", "status")),
        APPOINTMENTS("appointment", "appointment_no", "demographic_no", List.of(
                "appointment_date", "appointment_no", "billing", "bookingSource", "createdatetime", "creator", "creatorSecurityId", "demographic_no",
                "end_time", "imported_status", "lastupdateuser", "location", "name", "notes", "program_id", "provider_no", "reason", "reasonCode",
                "remarks", "resources", "start_time", "status", "style", "type", "updatedatetime", "urgency")),
        DRUGS("drugs", "drugid", "demographic_no", List.of(
                "ATC", "BN", "GCN_SEQNO", "GN", "archived", "archived_date",
                "archived_reason", "cds_currentness", "cds_dose_text", "cds_projection_incomplete", "comment", "create_date",
                "customName", "custom_instructions", "custom_note", "demographic_contact_id", "demographic_no", "dispenseInternal",
                "dispense_interval", "dosage", "drug_form", "drugid", "duration", "durunit",
                "eTreatmentType", "end_date", "freqcode", "hide_cpp", "hide_from_drug_profile", "lastUpdateDate",
                "last_refill_date", "long_term", "method", "non_authoritative", "nosubs", "outside_provider_name",
                "outside_provider_ohip", "past_med", "patient_compliance", "pharmacyId", "pickup_datetime", "position",
                "priorRxProtocol", "prn", "protocol", "provider_no", "quantity", "refill_duration",
                "refill_quantity", "regional_identifier", "repeat", "route", "rxStatus", "rx_date",
                "script_no", "short_term", "special", "special_instruction", "start_date_unknown", "takemax",
                "takemin", "unit", "unitName", "written_date")),
        ALLERGIES("allergies", "allergyid", "demographic_no", List.of(
                "AGCCS", "AGCSP", "DESCRIPTION", "HICL_SEQNO", "HIC_SEQNO", "TYPECODE", "age_of_onset", "allergyid",
                "archived", "atc", "demographic_no", "drugref_id", "entry_date", "lastUpdateDate", "life_stage", "nonDrug",
                "onset_of_reaction", "position", "providerNo", "reaction", "reaction_type", "regional_identifier", "severity_of_reaction", "start_date")),
        PARTIAL_DATE_ALLERGIES("partial_date", "id", null, List.of("field_name", "format", "id", "table_id", "table_name")),
        PARTIAL_DATE_MEDICATIONS("partial_date", "id", null, List.of("field_name", "format", "id", "table_id", "table_name")),
        PARTIAL_DATE_ALLERGIES_AND_MEDICATIONS("partial_date", "id", null, List.of("field_name", "format", "id", "table_id", "table_name")),
        DEMOGRAPHIC_CONTACT("DemographicContact", "id", "demographicNo", List.of("active", "category", "consentToContact", "contactId", "created", "creator", "deleted", "demographicNo", "ec", "facilityId", "id", "mrp", "note", "programNo", "role", "sdm", "type", "updateDate")),
        CONTACT("Contact", "id", null, List.of("address", "address2", "cellPhone", "city", "country", "cpso", "deleted", "email", "fax", "firstName", "id", "lastName", "note", "postal", "province", "residencePhone", "specialty", "systemId", "type", "updateDate", "workPhone", "workPhoneExtension")),
        PHARMACY_LINK("demographicPharmacy", "id", "demographic_no", List.of("addDate", "consentToContact", "demographic_no", "id", "pharmacyID", "preferredOrder", "status")),
        PHARMACY("pharmacyInfo", "recordID", null, List.of("addDate", "address", "city", "email", "fax", "name", "notes", "phone1", "phone2", "postalCode", "province", "recordID", "serviceLocationIdentifier", "status", "uid")),
        DEMOGRAPHIC_EXT("demographicExt", "id", "demographic_no", List.of(
                "dashboard_single_value_key", "date_time", "demographic_no", "hidden", "id", "key_val", "provider_no", "value")),
        DEMOGRAPHIC_CUST("demographiccust", "demographic_no", "demographic_no", List.of(
                "content", "cust1", "cust2", "cust3", "cust4", "demographic_no")),
        DEMOGRAPHIC_CUST_DEMOGRAPHICS("demographiccust", "demographic_no", "demographic_no", List.of(
                "content", "cust1", "cust2", "cust4", "demographic_no")),
        IMPORT_FIELDS("cds_import_field", "id", "demographic_no", List.of(
                "category", "date_precision", "demographic_no", "id", "normalized_value", "occurrence", "original_value", "patient_key",
                "payload_digest", "payload_length", "payload_location", "payload_offset", "run_id", "source_namespace", "source_path", "unit_key", "value_type")),
        IMPORT_FILES("cds_import_file", "id", null, List.of("byte_length", "digest", "generation", "id", "location", "location_key", "run_id", "state", "verified_epoch")),
        IMPORT_FILE_REFERENCES("cds_import_file_reference", "id", null, List.of("file_id", "id", "run_id", "unit_key")),
        IMPORT_UNITS("cds_import_unit", "id", null, List.of(
                "category", "committed_at", "id", "lineage_key", "native_baseline", "ordinal", "patient_key", "run_id", "source_digest", "spool_location",
                "state", "target_digest", "target_references", "unit_key")),
        IMPORT_PREPARATIONS("cds_import_preparation", "id", null, List.of(
                "digest", "id", "options_digest", "payload", "payload_type", "ruleset_digest", "run_id", "source_digest", "unit_key")),
        IMPORT_DEPENDENCIES("cds_import_dependency", "id", null, List.of("depends_on_unit_key", "id", "run_id", "unit_key")),
        IMPORT_PATIENTS("cds_import_patient", "id", "target_demographic_no", List.of(
                "copy_key", "execution_digest", "expected_units", "held", "id", "lineage_key", "patient_key", "run_id", "source_digest",
                "source_patient_id", "state", "target_demographic_no")),
        IMPORT_RUNS("cds_import_run", "id", null, List.of(
                "cancel_requested", "cleanup_state", "created_at", "discovered_files", "discovery_complete", "dry_run", "execution_digest",
                "expected_patients", "expected_units", "id", "manifest_digest", "operator", "options_digest", "owner_epoch", "patient_xml_files",
                "profile", "provider_no", "report_digest", "report_generation", "report_location", "report_state", "resume_requested", "review_version",
                "ruleset_digest", "safe_error_code", "snapshot_location", "source_bytes", "source_digest", "source_namespace", "state", "submission_key", "updated_at"));
        private final String table, key, patient;
        private final List<String> columns;
        Plan(String table, String key, String patient, List<String> columns) {
            this.table = table; this.key = key; this.patient = patient; this.columns = List.copyOf(columns);
        }
        public String table() { return table; }
        public List<String> columns() { return columns; }
        /** Exact original identifiers; UUID run keys and digest field/unit keys never undergo numeric coercion. */
        public boolean validRetainedKey(String value) {
            return value != null && (this == IMPORT_RUNS || this == IMPORT_FILES ? value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") : value.matches("[0-9a-f]{64}"));
        }
        /** Only explicit patient-owned native date domains may broaden the precision scope. */
        public static Plan partialDates(Set<io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory> categories) {
            var allergy = io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS;
            var medication = io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.MEDICATIONS_AND_TREATMENTS;
            if (categories == null || categories.isEmpty() || !Set.of(allergy, medication).containsAll(categories)) {
                throw new IllegalArgumentException("CDS_EXPORT_PRECISION_SCOPE_INVALID");
            }
            return categories.size() == 2 ? PARTIAL_DATE_ALLERGIES_AND_MEDICATIONS
                    : categories.contains(allergy) ? PARTIAL_DATE_ALLERGIES : PARTIAL_DATE_MEDICATIONS;
        }
        private boolean retained() {
            return switch (this) {
                case IMPORT_FILES, IMPORT_FILE_REFERENCES, IMPORT_FIELDS, IMPORT_UNITS, IMPORT_PREPARATIONS, IMPORT_DEPENDENCIES, IMPORT_PATIENTS, IMPORT_RUNS -> true;
                default -> false;
            };
        }
        private String patientPredicate() {
            // No activity/deletion filtering: historical associations remain captured and require an explicit disposition.
            // Exact text reference matching avoids MySQL's permissive string-to-number coercion.
            if (this == CONTACT) return "exists (select 1 from `DemographicContact` p where p.`demographicNo`=:patient"
                    + " and p.`type`=2 and CAST(p.`contactId` AS BINARY)=CAST(CAST(d.`id` AS CHAR) AS BINARY))";
            if (this == PHARMACY) return "exists (select 1 from `demographicPharmacy` p where p.`demographic_no`=:patient and p.`pharmacyID`=d.`recordID`)";
            if (this == PREVENTIONS_FULL) return "d.`demographic_no`=:patient and d.`id` in (:preventionIds)";
            if (this == PREVENTIONS_EXT) return "exists (select 1 from `preventions` p where p.`demographic_no`=:patient and p.`id`=d.`prevention_id` and p.`id` in (:preventionIds))";
            if (this == PREVENTION_PRECISION) return "d.`table_name`=4 and exists (select 1 from `preventions` p where p.`demographic_no`=:patient and p.`id`=d.`table_id` and p.`id` in (:preventionIds))";
            // Public vocabulary definitions are global; the caller must still hold an issued positive patient scope.
            if (this == CVC_IMMUNIZATION || this == CVC_MAPPING) return ":patient > 0";
            if (this == DEMOGRAPHIC_MERGES) return "(d.`demographic_no`=:patient or d.`merged_to`=:patient)";
            if (this == APPOINTMENT_STATUSES) {
                return "(exists (select 1 from `appointment` a where a.`demographic_no`=:patient and a.`status`=d.`status`)"
                        + " or exists (select 1 from `appointmentArchive` a where a.`demographic_no`=:patient and a.`status`=d.`status`))";
            }
            if (this == PARTIAL_DATE_ALLERGIES || this == PARTIAL_DATE_MEDICATIONS || this == PARTIAL_DATE_ALLERGIES_AND_MEDICATIONS) {
                String allergies = "(d.`table_name`=1 and exists (select 1 from `allergies` a where a.`allergyid`=d.`table_id` and a.`demographic_no`=:patient))";
                String medications = "(d.`table_name`=2 and exists (select 1 from `drugs` a where a.`drugid`=d.`table_id` and a.`demographic_no`=:patient))";
                return this == PARTIAL_DATE_ALLERGIES ? allergies : this == PARTIAL_DATE_MEDICATIONS ? medications
                        : "(" + allergies + " or " + medications + ")";
            }
            if (this == IMPORT_FIELDS) return "d.`demographic_no`=:patient and d.`category` in (:categories)";
            if (this == IMPORT_RUNS) return "exists (select 1 from `cds_import_patient` p where p.`run_id`=d.`id` and p.`target_demographic_no`=:patient)";
            if (this == IMPORT_UNITS) return "d.`category` in (:categories) and " + importedPatient("d");
            if (this == IMPORT_FILES) {
                return "exists (select 1 from `cds_import_file_reference` r join `cds_import_unit` u"
                        + " on u.`run_id`=r.`run_id` and u.`unit_key`=r.`unit_key` where r.`file_id`=d.`id`"
                        + " and u.`category` in (:categories) and " + importedPatient("u") + ")";
            }
            if (this == IMPORT_FILE_REFERENCES || this == IMPORT_PREPARATIONS || this == IMPORT_DEPENDENCIES) {
                return "exists (select 1 from `cds_import_unit` u where u.`run_id`=d.`run_id` and u.`unit_key`=d.`unit_key`"
                        + " and u.`category` in (:categories) and " + importedPatient("u") + ")";
            }
            return "d.`" + patient + "`=:patient";
        }
        private static String importedPatient(String unitAlias) {
            return "exists (select 1 from `cds_import_patient` p where p.`run_id`=" + unitAlias + ".`run_id`"
                    + " and p.`patient_key`=" + unitAlias + ".`patient_key` and p.`target_demographic_no`=:patient)";
        }
    }
    private record Bindings(Set<String> categories, Set<Integer> preventionIds){ }
    private static boolean selectedPreventions(Plan plan){return plan==Plan.PREVENTIONS_FULL||plan==Plan.PREVENTIONS_EXT||plan==Plan.PREVENTION_PRECISION; }
    private static Set<Integer> requireSelected(Plan plan, int patient, Set<Integer> ids){
        queryTimeoutMillis(); patient(patient); if (!selectedPreventions(plan)||ids==null||ids.size()>10_000||ids.stream().anyMatch(java.util.Objects::isNull))invalid(); return Set.copyOf(ids);
    }
    public static long preventionCount(EntityManager em, Plan plan, int patient, Set<Integer> ids){
        var selected=requireSelected(plan, patient, ids); if (selected.isEmpty())return 0; return countBound(em, plan, patient, new Bindings(null, selected));
    }
    public static List<CdsExportRawRowDto> preventionPage(EntityManager em, Plan plan, int patient, Set<Integer> ids, Long after, int limit){
        var selected=requireSelected(plan, patient, ids); if (limit<1||limit>32)invalid(); if (selected.isEmpty())return List.of();
        return pageBound(em, plan, patient, after, limit, new Bindings(null, selected)).stream().map(row-> new CdsExportRawRowDto(Long.parseLong(row.id()), row.cells())).toList();
    }
    public static byte[] preventionChunk(EntityManager em, Plan plan, int patient, Set<Integer> ids, long row, String column, long offset, int length){
        var selected=requireSelected(plan, patient, ids); if (selected.isEmpty())invalid(); return chunkBound(em, plan, patient, row, column, offset, length, new Bindings(null, selected));
    }
    private CdsExportPatientTableReadSupport() { }
    public static CdsExportTableSchemaDto schema(EntityManager em, Plan plan) {
        // Verify whole-table column visibility, including invisible columns, before trusting
        // the privilege-filtered information_schema catalogue. No source values are returned.
        query(em, "select d.* from `" + plan.table + "` d limit 0").getResultList();
        var engines = rows(query(em, "select ENGINE,TABLE_NAME from information_schema.TABLES where TABLE_SCHEMA=DATABASE() "
                + "and TABLE_NAME=:table and TABLE_TYPE='BASE TABLE'").setParameter("table", plan.table).setMaxResults(2));
        var columns = rows(query(em, "select COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME,"
                + "NULLIF(GENERATION_EXPRESSION,'') from information_schema.COLUMNS where TABLE_SCHEMA=DATABASE() and TABLE_NAME=:table")
                .setParameter("table", plan.table).setMaxResults(4097));
        var keys = rows(query(em, "select COLUMN_NAME,ORDINAL_POSITION from information_schema.KEY_COLUMN_USAGE where TABLE_SCHEMA=DATABASE() "
                + "and TABLE_NAME=:table and CONSTRAINT_NAME='PRIMARY' order by ORDINAL_POSITION")
                .setParameter("table", plan.table).setMaxResults(17));
        if (engines.size() != 1 || columns.isEmpty() || columns.size() > 4096 || keys.size() > 16) invalid();
        var captured = new ArrayList<CdsExportTableSchemaDto.Column>();
        for (var column : columns) {
            if (column.length != 6) invalid();
            captured.add(new CdsExportTableSchemaDto.Column((String) column[0], (String) column[1], (String) column[2],
                    (String) column[3], (String) column[4], (String) column[5]));
        }
        captured.sort(java.util.Comparator.comparing(CdsExportTableSchemaDto.Column::name));
        var primary = new ArrayList<String>(); for (var key : keys) primary.add((String) key[0]);
        query(em, "select d.* from `" + plan.table + "` d limit 0").getResultList();
        return new CdsExportTableSchemaDto(plan.table, (String) engines.getFirst()[0], captured, primary);
    }
    /** Reads only restriction metadata, before any prevention type or clinical value is loaded. */
    public static boolean unresolvedPreventionProgram(EntityManager em, int patient) {
        patient(patient);
        var found = rows(query(em, "select count(*),1 from `preventions` d where d.`demographic_no`=:patient and "
                + "((d.`restrictToProgram` is not null and d.`restrictToProgram`<>0) or (d.`restrictToProgram` is null and d.`programNo` is not null))").setParameter("patient", patient));
        if (found.size() != 1) invalid(); return number(found.getFirst()[0]) != 0;
    }
    public static long count(EntityManager em, Plan plan, int patient) {
        if (plan.retained() || plan == Plan.PUBLIC_PROVIDERS || selectedPreventions(plan)) invalid();
        return count(em, plan, patient, null);
    }
    public static long retainedCount(EntityManager em, Plan plan, int patient, Set<String> categories) {
        requireRetained(plan, categories); patient(patient);
        if (plan == Plan.IMPORT_FIELDS || plan == Plan.IMPORT_UNITS) {
            var known = new java.util.TreeSet<>(io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.wireNames());
            if (plan == Plan.IMPORT_UNITS) for (String category : io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.wireNames()) {
                known.add(category + "_FIELDS"); known.add(category + "_CONTACT"); known.add(category + "_CARE");
            }
            String scope = plan == Plan.IMPORT_FIELDS ? "d.`demographic_no`=:patient" : Plan.importedPatient("d");
            var unclassified = rows(query(em, "select count(*),1 from `" + plan.table + "` d where " + scope
                    + " and d.`category` not in (:knownCategories)").setParameter("patient", patient)
                    .setParameter("knownCategories", List.copyOf(known)));
            if (unclassified.size() != 1 || number(unclassified.getFirst()[0]) != 0) {
                throw new IllegalStateException("CDS_EXPORT_RETAINED_CATEGORY_REQUIRES_REVIEW");
            }
        }
        return count(em, plan, patient, categories);
    }
    private static long count(EntityManager em, Plan plan, int patient, Set<String> categories) {
        return countBound(em, plan, patient, new Bindings(categories, null));
    }
    private static long countBound(EntityManager em, Plan plan, int patient, Bindings bindings){
        patient(patient); var found = rows(query(em, "select count(*),1 from `" + plan.table + "` d where " + plan.patientPredicate())
                .setParameter("patient", patient), plan, bindings);
        if (found.size() != 1) invalid(); long count = number(found.getFirst()[0]); if (count < 0) invalid(); return count;
    }
    public static List<CdsExportRawRowDto> page(EntityManager em, Plan plan, int patient, Long after, int limit) {
        if (plan.retained() || plan == Plan.PUBLIC_PROVIDERS || selectedPreventions(plan)) invalid();
        return page(em, plan, patient, after, limit, null).stream()
                .map(row -> new CdsExportRawRowDto(Long.parseLong(row.id()), row.cells())).toList();
    }
    public static List<CdsExportRawKeyRowDto> retainedPage(EntityManager em, Plan plan, int patient, Set<String> categories, String after, int limit) {
        requireRetained(plan, categories); if (after != null) retainedKey(plan, after);
        return page(em, plan, patient, after, limit, categories);
    }
    private static List<CdsExportRawKeyRowDto> page(EntityManager em, Plan plan, int patient, Object after, int limit, Set<String> categories) {
        return pageBound(em, plan, patient, after, limit, new Bindings(categories, null));
    }
    private static List<CdsExportRawKeyRowDto> pageBound(EntityManager em, Plan plan, int patient, Object after, int limit, Bindings bindings){
        patient(patient); if (limit < 1 || limit > 32) invalid(); int inline = Math.min(16_384, 1_048_576 / plan.columns.size());
        var projection = new StringBuilder("select d.`" + plan.key + "`");
        for (String column : plan.columns) {
            String value = "CAST(d.`" + column + "` AS BINARY)";
            projection.append(",d.`").append(column).append("` IS NULL,OCTET_LENGTH(").append(value).append("),SHA2(")
                    .append(value).append(",256),CASE WHEN OCTET_LENGTH(").append(value).append(")<=:inline THEN ")
                    .append(value).append(" ELSE NULL END");
        }
        projection.append(" from `").append(plan.table).append("` d where ").append(plan.patientPredicate());
        if (after != null) projection.append(" and d.`").append(plan.key).append("`>:after");
        projection.append(" order by d.`").append(plan.key).append('`');
        var query = query(em, projection.toString()).setParameter("patient", patient).setParameter("inline", inline).setMaxResults(limit);
        if (after != null) query.setParameter("after", after);
        var result = new ArrayList<CdsExportRawKeyRowDto>(); Object previous = after;
        for (var row : rows(query, plan, bindings)) {
            if (row.length != 1 + 4 * plan.columns.size()) invalid();
            String id;
            if (plan.retained()) {
                if (!(row[0] instanceof String)) invalid(); id = (String) row[0]; retainedKey(plan, id);
                if (previous != null && id.compareTo((String) previous) <= 0) invalid(); previous = id;
            } else {
                long number = number(row[0]); if (previous != null && number <= (Long) previous) invalid();
                previous = number; id = Long.toString(number);
            }
            var values = new TreeMap<String, CdsExportRawCellDto>();
            int index = 1;
            for (String column : plan.columns) {
                boolean missing = missing(row[index]);
                long bytes = missing ? 0 : number(row[index + 1]); String hash = missing ? CdsExportRawCellDto.EMPTY_SHA256 : (String) row[index + 2];
                byte[] data = (byte[]) row[index + 3];
                if (missing && (row[index + 1] != null || row[index + 2] != null || data != null)
                        || !missing && bytes <= inline && data == null || !missing && bytes > inline && data != null) invalid();
                values.put(column, new CdsExportRawCellDto(missing, bytes, hash, data)); index += 4;
            }
            result.add(new CdsExportRawKeyRowDto(id, values));
        }
        return List.copyOf(result);
    }
    public static byte[] chunk(EntityManager em, Plan plan, int patient, long rowId, String column, long offset, int length) {
        if (plan.retained() || plan == Plan.PUBLIC_PROVIDERS || selectedPreventions(plan)) invalid(); return chunk(em, plan, patient, rowId, column, offset, length, null);
    }
    public static byte[] retainedChunk(EntityManager em, Plan plan, int patient, Set<String> categories, String rowId, String column, long offset, int length) {
        requireRetained(plan, categories); retainedKey(plan, rowId); return chunk(em, plan, patient, rowId, column, offset, length, categories);
    }
    private static byte[] chunk(EntityManager em, Plan plan, int patient, Object rowId, String column, long offset, int length, Set<String> categories) {
        return chunkBound(em, plan, patient, rowId, column, offset, length, new Bindings(categories, null));
    }
    private static byte[] chunkBound(EntityManager em, Plan plan, int patient, Object rowId, String column, long offset, int length, Bindings bindings){
        patient(patient);
        if (column == null || !plan.columns.contains(column) || offset < 0 || offset == Long.MAX_VALUE || length < 1 || length > 65_536) invalid();
        var found = rows(query(em, "select d.`" + plan.key + "`,SUBSTRING(CAST(d.`" + column
                + "` AS BINARY),:position,:length) from `" + plan.table + "` d where " + plan.patientPredicate() + " and d.`" + plan.key + "`=:row")
                .setParameter("position", offset + 1).setParameter("length", length).setParameter("patient", patient)
                .setParameter("row", rowId).setMaxResults(2), plan, bindings);
        if (found.size() != 1 || (plan.retained() ? !rowId.equals(found.getFirst()[0]) : number(found.getFirst()[0]) != (Long) rowId)
                || !(found.getFirst()[1] instanceof byte[] data) || data.length > length) invalid();
        return ((byte[]) found.getFirst()[1]).clone();
    }
    static Query query(EntityManager em, String sql) {
        int timeout = queryTimeoutMillis();
        return em.createNativeQuery(sql, Object[].class).setFlushMode(FlushModeType.COMMIT)
                .setHint("jakarta.persistence.query.timeout", timeout);
    }
    /** Shared read-only/deadline boundary for ORM projections and value-free JDBC metadata. */
    static int queryTimeoutMillis() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
        }
        long milliseconds = 30_000;
        for (var value : TransactionSynchronizationManager.getResourceMap().values()) {
            if (value instanceof ResourceHolderSupport resource && resource.hasTimeout()) milliseconds = Math.min(milliseconds, resource.getTimeToLiveInMillis());
        }
        return Math.max(1, Math.toIntExact(milliseconds));
    }
    @SuppressWarnings("unchecked") private static List<Object[]> rows(Query query) { return query.getResultList(); }
    private static List<Object[]> rows(Query query, Plan plan, Set<String> categories) {
        return rows(query, plan, new Bindings(categories, null));
    }
    private static List<Object[]> rows(Query query, Plan plan, Bindings bindings){
        var categories=bindings.categories();
        if (selectedPreventions(plan)){if (bindings.preventionIds()==null||bindings.preventionIds().isEmpty())invalid(); query.setParameter("preventionIds", bindings.preventionIds().stream().sorted().toList()); }
        if (plan.retained() && plan != Plan.IMPORT_PATIENTS && plan != Plan.IMPORT_RUNS) {
            var selected = new java.util.TreeSet<>(categories);
            if (plan != Plan.IMPORT_FIELDS) for (String category : categories) {
                selected.add(category + "_FIELDS"); selected.add(category + "_CONTACT"); selected.add(category + "_CARE");
            }
            query.setParameter("categories", List.copyOf(selected));
        }
        return rows(query);
    }
    private static void requireRetained(Plan plan, Set<String> categories) {
        if (plan == null || !plan.retained() || categories == null || categories.isEmpty()
                || !io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.wireNames().containsAll(categories)) invalid();
    }
    private static void retainedKey(Plan plan, String value) { if (!plan.validRetainedKey(value)) invalid(); }
    private static long number(Object value) {
        if (!(value instanceof Number)) invalid();
        try { return new BigDecimal(value.toString()).longValueExact(); }
        catch (NumberFormatException | ArithmeticException invalid) { throw new IllegalStateException("CDS_EXPORT_NATIVE_SCALAR_INVALID"); }
    }
    private static boolean missing(Object value) {
        if (value instanceof Boolean flag) return flag;
        long number = number(value); if (number != 0 && number != 1) invalid(); return number == 1;
    }
    private static void patient(int id) { if (id < 1) invalid(); }
    private static void invalid() { throw new IllegalStateException("CDS_EXPORT_NATIVE_SOURCE_INVALID"); }
}
