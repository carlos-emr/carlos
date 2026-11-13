/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.util.*;

/** Finite reviewed parent relationships for presence checks; these scopes do not qualify clinical mapping. */
final class CdsExportIndirectPatientScopeReadSupport {
    private record Step(
            String table,
            String childColumn,
            String parentColumn,
            String otherChildColumn,
            String otherParentColumn) {
        Step(String table, String childColumn, String parentColumn) {
            this(table, childColumn, parentColumn, null, null);
        }
    }

    private record Scope(List<Step> steps, String owner, boolean demographicModule) {
        Scope {
            steps = List.copyOf(steps);
        }
    }

    private static final Map<String, Scope> SCOPES = scopes();

    static boolean supports(String table) {
        return SCOPES.containsKey(table);
    }

    static Set<String> tables() {
        return SCOPES.keySet();
    }

    /** Orphans and unattributable owners block every selection; known other-patient children do not. */
    static String predicate(String table, CdsExportSourceCatalogue.Profile profile) {
        var scope = SCOPES.get(table);
        if (scope == null) throw new IllegalArgumentException("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
        var first = scope.steps().getFirst();
        StringBuilder from = new StringBuilder("select 1 from `" + first.table() + "` s1");
        String previousTable = table, previousAlias = "d";
        var links = new ArrayList<String>();
        for (int i = 0; i < scope.steps().size(); i++) {
            var step = scope.steps().get(i);
            String alias = "s" + (i + 1);
            String match =
                    numeric(profile, previousTable, previousAlias, step.childColumn())
                            + "="
                            + numeric(profile, step.table(), alias, step.parentColumn());
            if (step.otherChildColumn() != null) {
                column(profile, previousTable, step.otherChildColumn());
                column(profile, step.table(), step.otherParentColumn());
                match +=
                        " and CAST("
                                + previousAlias
                                + ".`"
                                + step.otherChildColumn()
                                + "` AS BINARY)="
                                + "CAST("
                                + alias
                                + ".`"
                                + step.otherParentColumn()
                                + "` AS BINARY)";
            }
            if (i == 0) links.add(match);
            else
                from.append(" join `")
                        .append(step.table())
                        .append("` ")
                        .append(alias)
                        .append(" on ")
                        .append(match);
            previousTable = step.table();
            previousAlias = alias;
        }
        from.append(" join `demographic` ownedPatient on ")
                .append(numeric(profile, previousTable, previousAlias, scope.owner()))
                .append("=")
                .append(numeric(profile, "demographic", "ownedPatient", "demographic_no"));
        if (scope.demographicModule()) {
            column(profile, previousTable, "module");
            links.add(
                    "CAST(" + previousAlias + ".`module` AS BINARY)=CAST('demographic' AS BINARY)");
        }
        String linked = from + " where (" + String.join(" and ", links) + ")";
        return "(exists ("
                + linked
                + " and ownedPatient.`demographic_no`=:patient) or not exists ("
                + linked
                + "))";
    }

    private static String numeric(
            CdsExportSourceCatalogue.Profile profile, String table, String alias, String name) {
        if (table.equals("HRMDocumentToDemographic") && name.equals("demographicNo")) {
            if (!column(profile, table, "cds_patient_key").type().equals("decimal(20,0)")) review();
            return alias + ".`cds_patient_key`";
        }
        String type = column(profile, table, name).type();
        String expression = alias + ".`" + name + "`";
        if (type.startsWith("varchar(") || type.startsWith("char("))
            return "CAST(" + expression + " AS DECIMAL(65,0))";
        if (!type.matches("(?:tinyint|smallint|mediumint|int|bigint)\\([0-9]+\\)(?: unsigned)?"))
            review();
        return expression;
    }

    private static io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto.Column column(
            CdsExportSourceCatalogue.Profile profile, String table, String name) {
        var schema = profile.tables().get(table);
        if (schema == null) review();
        return schema.columns().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(
                        () ->
                                new CdsExportReviewException(
                                        CdsExportReviewException.Reason
                                                .CLINICAL_SOURCE_UNREVIEWED));
    }

    private static Map<String, Scope> scopes() {
        var result = new LinkedHashMap<String, Scope>();
        add(result, "CdsClientFormData", "CdsClientForm", "cdsClientFormId", "id", "clientId");
        add(result, "EFormDocs", "eform_data", "fdid", "fdid", "demographic_no");
        add(result, "casemgmt_note_link", "casemgmt_note", "note_id", "note_id", "demographic_no");
        add(
                result,
                "consultationRequestExt",
                "consultationRequests",
                "requestId",
                "requestId",
                "demographicNo");
        add(
                result,
                "consultdocs",
                "consultationRequests",
                "requestId",
                "requestId",
                "demographicNo");
        add(
                result,
                "consultResponseDoc",
                "consultationResponse",
                "responseId",
                "responseId",
                "demographicNo");
        for (String table :
                // tickler_link is superseded (its rows were migrated to ticklerdocs; what remains is
                // quarantined) and is ruled operational, so it has no patient scope here.
                List.of("tickler_comments", "tickler_update", "ticklerdocs"))
            add(
                    result,
                    table,
                    "tickler",
                    table.equals("ticklerdocs") ? "tickler_id" : "tickler_no",
                    "tickler_no",
                    "demographic_no");
        for (String table : List.of("DocumentExtraReviewer", "document_review", "document_storage"))
            result.put(
                    table,
                    new Scope(
                            List.of(
                                    new Step(
                                            "document",
                                            table.equals("document_review")
                                                    ? "document_no"
                                                    : "documentNo",
                                            "document_no"),
                                    new Step("ctl_document", "document_no", "document_no")),
                            "module_id",
                            true));
        result.put(
                "providerLabRouting",
                new Scope(
                        List.of(
                                new Step(
                                        "patientLabRouting",
                                        "lab_no",
                                        "lab_no",
                                        "lab_type",
                                        "lab_type")),
                        "demographic_no",
                        false));
        result.put(
                "HRMDocument",
                new Scope(
                        List.of(new Step("HRMDocumentToDemographic", "id", "hrmDocumentId")),
                        "demographicNo",
                        false));
        for (String table :
                List.of("HRMDocumentComment", "HRMDocumentSubClass", "HRMDocumentToProvider"))
            result.put(
                    table,
                    new Scope(
                            List.of(
                                    new Step("HRMDocument", "hrmDocumentId", "id"),
                                    new Step("HRMDocumentToDemographic", "id", "hrmDocumentId")),
                            "demographicNo",
                            false));
        return Collections.unmodifiableMap(result);
    }

    private static void add(
            Map<String, Scope> result,
            String table,
            String parent,
            String childColumn,
            String parentColumn,
            String owner) {
        result.put(
                table,
                new Scope(List.of(new Step(parent, childColumn, parentColumn)), owner, false));
    }

    private static void review() {
        throw new CdsExportReviewException(
                CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
    }
}
