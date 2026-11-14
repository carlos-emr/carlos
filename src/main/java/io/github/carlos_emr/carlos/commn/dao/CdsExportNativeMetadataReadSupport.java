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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;

/** Value-free actual-driver receipt metadata, read by the owning DAO within the patient snapshot. */
public final class CdsExportNativeMetadataReadSupport {
    public enum Table {
        DOCUMENTS("document"),
        DOCUMENT_LINKS("ctl_document"),
        MEASUREMENTS_CURRENT("measurements"),
        MEASUREMENTS_EXT("measurementsExt"),
        MEASUREMENTS_DELETED("measurementsDeleted"),
        MEASUREMENT_TYPES("measurementType"),
        LABORATORY_RESULTS("cds_laboratory_result"),
        LABORATORY_MESSAGES("hl7TextMessage"),
        LABORATORY_INFORMATION("hl7TextInfo"),
        LABORATORY_ROUTING("patientLabRouting"),
        CASE_ISSUES("casemgmt_issue"),
        DISEASE_REGISTRY("dxresearch"),
        EPISODES("Episode"),
        ISSUE_CATALOGUE("issue"),
        RETAINED_FILES("cds_import_file"),
        RETAINED_FILE_REFERENCES("cds_import_file_reference"),
        CONTACT("Contact"),
        DEMOGRAPHIC_CONTACT("DemographicContact"),
        PHARMACY_LINK("demographicPharmacy"),
        PHARMACY("pharmacyInfo"),
        CASE_NOTE_ISSUE_LINK("casemgmt_issue_notes"),
        CASE_NOTE_EXT("casemgmt_note_ext"),
        CASE_NOTE_LINK("casemgmt_note_link"),
        CASE_NOTES("casemgmt_note"),
        PREVENTIONS_EXT("preventionsExt"),
        PREVENTIONS("preventions"),
        CVC_IMMUNIZATION("CVCImmunization"),
        CVC_MAPPING("CVCMapping"),
        DEMOGRAPHIC("demographic"),
        DEMOGRAPHIC_ARCHIVE("demographicArchive"),
        DEMOGRAPHIC_MERGES("demographic_merged"),
        DRUGS("drugs"),
        PRESCRIPTIONS("prescription"),
        ALLERGIES("allergies"),
        PARTIAL_DATE("partial_date"),
        APPOINTMENT("appointment"),
        DEMOGRAPHIC_EXT("demographicExt"),
        DEMOGRAPHIC_CUST("demographiccust"),
        RETAINED_FIELDS("cds_import_field");
        private final String name;

        Table(String name) {
            this.name = name;
        }
    }

    private CdsExportNativeMetadataReadSupport() {}

    // The only interpolated identifier is a fixed Table enum literal; no request or row value
    // enters SQL.
    @SuppressWarnings("java:S2077")
    public static List<Column> read(EntityManager em, Table table) {
        if (table == null) throw new IllegalArgumentException("CDS_EXPORT_RECEIPT_TABLE_INVALID");
        // Reuse the transaction/deadline checks without reading a single protected value.
        CdsExportPatientTableReadSupport.query(em, "select d.* from `" + table.name + "` d limit 0")
                .getResultList();
        return em.unwrap(Session.class)
                .doReturningWork(
                        connection ->
                                CdsExportStatementWaitReadSupport.await(
                                        em, connection, seconds -> columns(connection, table, seconds)));
    }

    // A named method, not the lambda it was: SpotBugs does not apply a method annotation to a
    // lambda body.
    // FindSecBugs SQL_INJECTION_JDBC: The only interpolated identifier is the name of a fixed Table enum constant; no request, file or row value reaches the SQL text.
    // FindSecBugs SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING: The statement text varies only by the name of a fixed Table enum constant.
    // FindSecBugs IMPROPER_UNICODE: Matches the fixed ASCII column name verified_epoch in JDBC metadata of a CARLOS table; not an authorization decision.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING", "IMPROPER_UNICODE"}, justification = "SQL_INJECTION_JDBC: the only interpolated identifier is the name of a fixed Table enum constant; no request, file or row value reaches the SQL text; SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING: the statement text varies only by the name of a fixed Table enum constant; IMPROPER_UNICODE: matches the fixed ASCII column name verified_epoch in JDBC metadata of a CARLOS table; not an authorization decision")
    @SuppressWarnings("java:S2077")
    private static List<Column> columns(java.sql.Connection connection, Table table, int seconds)
            throws java.sql.SQLException {
        var columns = new ArrayList<Column>();
        try (var statement =
                connection.prepareStatement(
                        "SELECT * FROM `" + table.name + "` WHERE 1=0")) {
            statement.setQueryTimeout(seconds);
            try (var result = statement.executeQuery()) {
                var metadata = result.getMetaData();
                if (metadata.getColumnCount() < 1
                        || metadata.getColumnCount() > 512) {
                    throw new IllegalStateException(
                            "CDS_EXPORT_RECEIPT_COLUMNS_REQUIRES_REVIEW");
                }
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    // Preserve the importer's exact historical receipt recipe:
                    // verification
                    // advances this operational epoch after the unit receipt is
                    // sealed.
                    if (table == Table.RETAINED_FILES
                            && metadata.getColumnName(i)
                                    .equalsIgnoreCase("verified_epoch"))
                        continue;
                    columns.add(
                            new Column(
                                    metadata.getColumnName(i),
                                    metadata.getColumnTypeName(i),
                                    metadata.getPrecision(i),
                                    metadata.getScale(i)));
                }
            }
        }
        return List.copyOf(columns);
    }
}
