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

/**
 * Finite reviewed parent relationships. {@link #predicate} is the presence check of a table that
 * must stay empty for the selected patient (an orphan or unattributable row blocks every
 * selection); {@link #owned} is the read scope of a data-summary or listed child table, which
 * selects exactly the rows whose parent belongs to the selected patient. A scope never qualifies
 * a clinical mapping by itself.
 */
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

    /**
     * One parent chain. {@code constantColumn}/{@code constantValue}, when present, restrict the
     * child row to the parent table it names (form_boolean_value.form_name names its Rourke
     * table), compared as exact bytes.
     */
    private record Scope(
            List<Step> steps,
            String owner,
            boolean demographicModule,
            String constantColumn,
            String constantValue) {
        Scope {
            steps = List.copyOf(steps);
        }

        Scope(List<Step> steps, String owner, boolean demographicModule) {
            this(steps, owner, demographicModule, null, null);
        }
    }

    private static final Map<String, List<Scope>> SCOPES = scopes();

    static boolean supports(String table) {
        return SCOPES.containsKey(table);
    }

    static Set<String> tables() {
        return SCOPES.keySet();
    }

    /** Orphans and unattributable owners block every selection; known other-patient children do not. */
    static String predicate(String table, CdsExportSourceCatalogue.Profile profile) {
        var scopes = SCOPES.get(table);
        if (scopes == null)
            throw new IllegalArgumentException("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
        var owned = new ArrayList<String>();
        var linked = new ArrayList<String>();
        for (var scope : scopes) {
            String chain = chain(table, scope, profile, true);
            owned.add("exists (" + chain + " and ownedPatient.`demographic_no`=:patient)");
            linked.add("exists (" + chain + ")");
        }
        return "(("
                + String.join(" or ", owned)
                + ") or not ("
                + String.join(" or ", linked)
                + "))";
    }

    /**
     * Rows of {@code table} (alias {@code d}) whose reviewed parent chain ends at the selected
     * patient ({@code :patient}). Orphans and other patients' children are excluded; they are not
     * part of this chart.
     */
    static String owned(String table, CdsExportSourceCatalogue.Profile profile) {
        var scopes = SCOPES.get(table);
        if (scopes == null)
            throw new IllegalArgumentException("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
        var owned = new ArrayList<String>();
        for (var scope : scopes) owned.add("exists (" + chain(table, scope, profile, false) + ")");
        return "(" + String.join(" or ", owned) + ")";
    }

    /** The select over one parent chain; {@code withOwner} joins the owning demographic row. */
    private static String chain(
            String table, Scope scope, CdsExportSourceCatalogue.Profile profile, boolean withOwner) {
        var first = scope.steps().getFirst();
        StringBuilder from = new StringBuilder("select 1 from `" + first.table() + "` s1");
        String previousTable = table, previousAlias = "d";
        var links = new ArrayList<String>();
        if (scope.constantColumn() != null) {
            column(profile, table, scope.constantColumn());
            // The literal comes from the compiled scope list below, never from data.
            links.add(
                    "CAST(d.`"
                            + scope.constantColumn()
                            + "` AS BINARY)=CAST('"
                            + scope.constantValue()
                            + "' AS BINARY)");
        }
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
        if (withOwner)
            from.append(" join `demographic` ownedPatient on ")
                    .append(numeric(profile, previousTable, previousAlias, scope.owner()))
                    .append("=")
                    .append(numeric(profile, "demographic", "ownedPatient", "demographic_no"));
        else links.add(numeric(profile, previousTable, previousAlias, scope.owner()) + "=:patient");
        if (scope.demographicModule()) {
            column(profile, previousTable, "module");
            links.add(
                    "CAST(" + previousAlias + ".`module` AS BINARY)=CAST('demographic' AS BINARY)");
        }
        return from + " where (" + String.join(" and ", links) + ")";
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

    private static Map<String, List<Scope>> scopes() {
        var result = new LinkedHashMap<String, List<Scope>>();
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
                    List.of(
                            new Scope(
                                    List.of(
                                            new Step(
                                                    "document",
                                                    table.equals("document_review")
                                                            ? "document_no"
                                                            : "documentNo",
                                                    "document_no"),
                                            new Step(
                                                    "ctl_document", "document_no", "document_no")),
                                    "module_id",
                                    true)));
        result.put(
                "providerLabRouting",
                List.of(
                        new Scope(
                                List.of(
                                        new Step(
                                                "patientLabRouting",
                                                "lab_no",
                                                "lab_no",
                                                "lab_type",
                                                "lab_type")),
                                "demographic_no",
                                false)));
        result.put(
                "HRMDocument",
                List.of(
                        new Scope(
                                List.of(
                                        new Step(
                                                "HRMDocumentToDemographic", "id", "hrmDocumentId")),
                                "demographicNo",
                                false)));
        for (String table :
                List.of("HRMDocumentComment", "HRMDocumentSubClass", "HRMDocumentToProvider"))
            result.put(
                    table,
                    List.of(
                            new Scope(
                                    List.of(
                                            new Step("HRMDocument", "hrmDocumentId", "id"),
                                            new Step(
                                                    "HRMDocumentToDemographic",
                                                    "id",
                                                    "hrmDocumentId")),
                                    "demographicNo",
                                    false)));
        // Data-summary children (DOCUMENT): each row belongs to the chart of its parent record.
        add(result, "emailAttachment", "emailLog", "logId", "id", "demographicNo");
        add(result, "formBCAR2020Data", "formBCAR2020", "form_id", "id", "demographic_no");
        add(result, "formBCAR2020Text", "formBCAR2020", "form_id", "id", "demographic_no");
        // A message belongs to every chart msgDemoMap links it to.
        add(result, "messagetbl", "msgDemoMap", "messageid", "messageID", "demographic_no");
        // Rourke checkbox values name their form table in form_name; the same form_id in the
        // other Rourke table is a different record.
        result.put(
                "form_boolean_value",
                List.of(
                        new Scope(
                                List.of(new Step("formRourke2017", "form_id", "ID")),
                                "demographic_no",
                                false,
                                "form_name",
                                "formRourke2017"),
                        new Scope(
                                List.of(new Step("formRourke2020", "form_id", "ID")),
                                "demographic_no",
                                false,
                                "form_name",
                                "formRourke2020")));
        // Listed children of removed features (LISTED): counted per chart, never exported.
        add(result, "caisi_form_data", "caisi_form_instance", "instance_id", "id", "client_id");
        add(result, "mdsZFR", "mdsMSH", "segmentID", "segmentID", "demographic_no");
        add(result, "mdsZLB", "mdsMSH", "segmentID", "segmentID", "demographic_no");
        return Collections.unmodifiableMap(result);
    }

    private static void add(
            Map<String, List<Scope>> result,
            String table,
            String parent,
            String childColumn,
            String parentColumn,
            String owner) {
        result.put(
                table,
                List.of(
                        new Scope(
                                List.of(new Step(parent, childColumn, parentColumn)),
                                owner,
                                false)));
    }

    private static void review() {
        throw new CdsExportReviewException(
                CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
    }
}
