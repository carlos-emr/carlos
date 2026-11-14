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
 * must stay empty for the selected patient; {@link #owned} is the read scope of a data-summary or
 * listed child table, which selects exactly the rows whose parent chain ends at the selected
 * patient. A scope never qualifies a clinical mapping by itself.
 *
 * <p>Every row of a scoped table is one of three things:
 *
 * <ul>
 *   <li><b>linked</b>: its exact parent chain reaches an existing {@code demographic} row. It
 *       belongs to that chart only.
 *   <li><b>unlinked</b> (inbox scopes only): it is an inbox item that names no patient yet. Its
 *       parent rows exist, and the only patient links they have, if any, are the placeholder
 *       CARLOS writes for "not matched" (see {@link Unlinked}). It is not part of any chart, so it
 *       holds no chart; {@link #unlinked} lets the job count it for the operator instead.
 *   <li><b>unattributable</b>: anything else, such as a missing parent, a link to a patient that
 *       does not exist, a module other than a patient chart, or a type spelled like the link's
 *       type under the column collation but not byte for byte. The row may belong to any chart, so
 *       it holds every selected chart, exactly as before.
 * </ul>
 */
final class CdsExportIndirectPatientScopeReadSupport {
    /**
     * One join of a chain. {@code otherChildColumn}/{@code otherParentColumn}, when present, must
     * also match (the routing type of a provider route). {@code parentTypeColumn}/{@code
     * parentTypeValue}, when present, restrict the parent row to one literal from this compiled
     * list, never data: a legacy lab number is a {@code patientLabRouting} row of exactly that
     * {@code lab_type}, since numeric lab ids are local to each type.
     */
    private record Step(
            String table,
            String childColumn,
            String parentColumn,
            String otherChildColumn,
            String otherParentColumn,
            String parentTypeColumn,
            String parentTypeValue) {
        Step(String table, String childColumn, String parentColumn) {
            this(table, childColumn, parentColumn, null, null, null, null);
        }

        Step(
                String table,
                String childColumn,
                String parentColumn,
                String otherChildColumn,
                String otherParentColumn) {
            this(table, childColumn, parentColumn, otherChildColumn, otherParentColumn, null, null);
        }

        Step {
            if ((parentTypeColumn == null) != (parentTypeValue == null)
                    || parentTypeValue != null && !parentTypeValue.matches("[A-Za-z0-9_]{1,64}"))
                throw new IllegalStateException("CDS_EXPORT_INDIRECT_SCOPE_INVALID");
        }

        /** A routing row of exactly one lab type, reached through its lab number. */
        static Step route(String childColumn, String type) {
            return new Step(
                    "patientLabRouting", childColumn, "lab_no", null, null, "lab_type", type);
        }
    }

    /**
     * The owner value that means "no patient yet" on the last link of an inbox chain, or null when
     * any link row names a patient. CARLOS writes 0 in {@code patientLabRouting.demographic_no}
     * when a lab is unlinked from a chart ({@code PatientLabRoutingDao.UNMATCHED}) and an incoming
     * document's {@code ctl_document} link is {@code ('demographic', -1)} until it is filed; an
     * unmatched HL7 upload or HRM report simply has no patient link row at all.
     */
    private record Unlinked(Long marker) {}

    /**
     * One parent chain. {@code constantColumn}/{@code constantValue}, when present, restrict the
     * scoped row to the parent table it names (form_boolean_value.form_name names its Rourke
     * table), compared as exact bytes; the literal comes from the compiled scope list, never data.
     */
    private record Chain(
            List<Step> steps,
            String owner,
            boolean demographicModule,
            Unlinked unlinked,
            String constantColumn,
            String constantValue) {
        Chain(List<Step> steps, String owner, boolean demographicModule, Unlinked unlinked) {
            this(steps, owner, demographicModule, unlinked, null, null);
        }

        Chain {
            steps = List.copyOf(steps);
            if ((constantColumn == null) != (constantValue == null)
                    || constantValue != null && !constantValue.matches("[A-Za-z0-9_]{1,64}"))
                throw new IllegalStateException("CDS_EXPORT_INDIRECT_SCOPE_INVALID");
            // Only the first step may compare a second column: its NOT NULL guard is on d.
            for (int i = 1; i < steps.size(); i++)
                if (steps.get(i).otherChildColumn() != null)
                    throw new IllegalStateException("CDS_EXPORT_INDIRECT_SCOPE_INVALID");
        }
    }

    /** One chain, chosen by a null-safe exact condition on the scoped row; null means otherwise. */
    private record Variant(String condition, Chain chain) {}

    private record Scope(List<Variant> variants) {
        Scope {
            variants = List.copyOf(variants);
            if (variants.isEmpty() || variants.getLast().condition() != null)
                throw new IllegalStateException("CDS_EXPORT_INDIRECT_SCOPE_INVALID");
        }

        boolean inbox() {
            return variants.stream().allMatch(variant -> variant.chain().unlinked() != null);
        }
    }

    private static final Map<String, Scope> SCOPES = scopes();

    static boolean supports(String table) {
        return SCOPES.containsKey(table);
    }

    static Set<String> tables() {
        return SCOPES.keySet();
    }

    /** Inbox tables whose unlinked rows hold no chart and are counted for the job instead. */
    static SortedSet<String> unlinkedTables() {
        var result = new TreeSet<String>();
        SCOPES.forEach((table, scope) -> {
            if (scope.inbox()) result.add(table);
        });
        return Collections.unmodifiableSortedSet(result);
    }

    /**
     * Rows that hold the selected patient's chart: rows linked to it, and unattributable rows,
     * which may belong to any chart. Known other-patient rows and unlinked inbox rows do not.
     */
    static String predicate(String table, CdsExportSourceCatalogue.Profile profile) {
        return combine(table, chain -> presence(chain, profile, table));
    }

    /**
     * Rows of a mapped indirect table that no capture plan can own: neither linked to an existing
     * patient nor an unlinked inbox row. Patient-free, so the same rows hold every chart.
     */
    static String unattributable(String table, CdsExportSourceCatalogue.Profile profile) {
        return combine(table, chain -> unattributable(chain, profile, table));
    }

    /**
     * Rows of {@code table} (alias {@code d}) whose reviewed parent chain ends at the selected
     * patient ({@code :patient}). Orphans, unlinked rows and other patients' children are
     * excluded; they are not part of this chart.
     */
    static String owned(String table, CdsExportSourceCatalogue.Profile profile) {
        return combine(table, chain -> "exists (" + select(chain, profile, table, false) + ")");
    }

    /** Unlinked inbox rows: excluded from every chart and counted for the job, never read. */
    static String unlinked(String table, CdsExportSourceCatalogue.Profile profile) {
        var scope = scope(table);
        if (!scope.inbox()) throw new IllegalArgumentException("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
        return combine(table, chain -> unlinked(chain, profile, table));
    }

    private interface Predicate {
        String of(Chain chain);
    }

    private static Scope scope(String table) {
        var scope = SCOPES.get(table);
        if (scope == null) throw new IllegalArgumentException("CDS_EXPORT_INDIRECT_SCOPE_UNKNOWN");
        return scope;
    }

    private static String combine(String table, Predicate predicate) {
        var scope = scope(table);
        if (scope.variants().size() == 1) return predicate.of(scope.variants().getFirst().chain());
        var parts = new ArrayList<String>();
        var earlier = new ArrayList<String>();
        for (var variant : scope.variants()) {
            var guard = new ArrayList<String>();
            for (String previous : earlier) guard.add("not (" + previous + ")");
            if (variant.condition() != null) {
                guard.add("(" + variant.condition() + ")");
                earlier.add(variant.condition());
            }
            parts.add("(" + String.join(" and ", guard) + " and " + predicate.of(variant.chain()) + ")");
        }
        return "(" + String.join(" or ", parts) + ")";
    }

    private static String presence(
            Chain chain, CdsExportSourceCatalogue.Profile profile, String table) {
        String linked = linked(chain, profile, table);
        if (chain.unlinked() == null)
            return "(exists ("
                    + linked
                    + " and ownedPatient.`demographic_no`=:patient) or not exists ("
                    + linked
                    + "))";
        return "(exists ("
                + linked
                + " and ownedPatient.`demographic_no`=:patient) or (not exists ("
                + linked
                + ") and not ("
                + inbox(chain, profile, table)
                + ")))";
    }

    private static String unattributable(
            Chain chain, CdsExportSourceCatalogue.Profile profile, String table) {
        String linked = linked(chain, profile, table);
        if (chain.unlinked() == null) return "not exists (" + linked + ")";
        return "(not exists (" + linked + ") and not (" + inbox(chain, profile, table) + "))";
    }

    private static String unlinked(
            Chain chain, CdsExportSourceCatalogue.Profile profile, String table) {
        return "(not exists ("
                + linked(chain, profile, table)
                + ") and "
                + inbox(chain, profile, table)
                + ")";
    }

    /** The exact chain to an existing patient, as "select 1 from ... where ...". */
    private static String linked(
            Chain chain, CdsExportSourceCatalogue.Profile profile, String table) {
        return select(chain, profile, table, true);
    }

    /**
     * The select over one exact chain: {@code withOwner} joins the owning demographic row (the
     * caller compares it); otherwise the owner is compared with {@code :patient} directly.
     */
    private static String select(
            Chain chain,
            CdsExportSourceCatalogue.Profile profile,
            String table,
            boolean withOwner) {
        var first = chain.steps().getFirst();
        StringBuilder from = new StringBuilder("select 1 from `" + first.table() + "` s1");
        String previousTable = table, previousAlias = "d";
        var links = new ArrayList<String>();
        if (chain.constantColumn() != null) {
            column(profile, table, chain.constantColumn());
            links.add(
                    "CAST(d.`"
                            + chain.constantColumn()
                            + "` AS BINARY)=CAST('"
                            + chain.constantValue()
                            + "' AS BINARY)");
        }
        for (int i = 0; i < chain.steps().size(); i++) {
            var step = chain.steps().get(i);
            String alias = "s" + (i + 1);
            String match = match(profile, previousTable, previousAlias, step, alias, true);
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
                    .append(numeric(profile, previousTable, previousAlias, chain.owner()))
                    .append("=")
                    .append(numeric(profile, "demographic", "ownedPatient", "demographic_no"));
        else links.add(numeric(profile, previousTable, previousAlias, chain.owner()) + "=:patient");
        if (chain.demographicModule()) {
            column(profile, previousTable, "module");
            links.add(
                    "CAST(" + previousAlias + ".`module` AS BINARY)=CAST('demographic' AS BINARY)");
        }
        return from + " where (" + String.join(" and ", links) + ")";
    }

    /**
     * An inbox row that names no patient: every parent before the patient link exists exactly,
     * and no link row, matched as leniently as the column collation allows, names anything other
     * than the unmatched placeholder. A lenient candidate keeps a type spelled differently, a
     * dangling patient number or another module unattributable instead of unlinked.
     */
    private static String inbox(
            Chain chain, CdsExportSourceCatalogue.Profile profile, String table) {
        var steps = chain.steps();
        var conditions = new ArrayList<String>();
        var first = steps.getFirst();
        if (first.otherChildColumn() != null) {
            column(profile, table, first.otherChildColumn());
            conditions.add("d.`" + first.otherChildColumn() + "` is not null");
        }
        String previousTable = table, previousAlias = "d";
        if (steps.size() > 1) {
            var anchor = new StringBuilder("select 1 from `" + first.table() + "` s1");
            String where = null;
            for (int i = 0; i < steps.size() - 1; i++) {
                var step = steps.get(i);
                String alias = "s" + (i + 1);
                String match = match(profile, previousTable, previousAlias, step, alias, true);
                if (i == 0) where = match;
                else
                    anchor.append(" join `")
                            .append(step.table())
                            .append("` ")
                            .append(alias)
                            .append(" on ")
                            .append(match);
                previousTable = step.table();
                previousAlias = alias;
            }
            conditions.add("exists (" + anchor + " where " + where + ")");
        }
        var candidate = new StringBuilder("select 1 from `" + first.table() + "` s1");
        String where = null;
        previousTable = table;
        previousAlias = "d";
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            String alias = "s" + (i + 1);
            String match =
                    match(profile, previousTable, previousAlias, step, alias, i < steps.size() - 1);
            if (i == 0) where = match;
            else
                candidate
                        .append(" join `")
                        .append(step.table())
                        .append("` ")
                        .append(alias)
                        .append(" on ")
                        .append(match);
            previousTable = step.table();
            previousAlias = alias;
        }
        Long marker = chain.unlinked().marker();
        if (marker != null) {
            String owner = numeric(profile, previousTable, previousAlias, chain.owner());
            String placeholder = owner + " is not null and " + owner + "=" + marker;
            if (chain.demographicModule()) {
                column(profile, previousTable, "module");
                placeholder +=
                        " and "
                                + previousAlias
                                + ".`module` is not null and CAST("
                                + previousAlias
                                + ".`module` AS BINARY)=CAST('demographic' AS BINARY)";
            }
            where += " and not (" + placeholder + ")";
        }
        conditions.add("not exists (" + candidate + " where " + where + ")");
        return "(" + String.join(" and ", conditions) + ")";
    }

    private static String match(
            CdsExportSourceCatalogue.Profile profile,
            String previousTable,
            String previousAlias,
            Step step,
            String alias,
            boolean exact) {
        String match =
                numeric(profile, previousTable, previousAlias, step.childColumn())
                        + "="
                        + numeric(profile, step.table(), alias, step.parentColumn());
        if (step.otherChildColumn() != null) {
            column(profile, previousTable, step.otherChildColumn());
            column(profile, step.table(), step.otherParentColumn());
            String child = previousAlias + ".`" + step.otherChildColumn() + "`",
                    parent = alias + ".`" + step.otherParentColumn() + "`";
            match +=
                    exact
                            ? " and CAST(" + child + " AS BINARY)=CAST(" + parent + " AS BINARY)"
                            : " and " + child + "=" + parent;
        }
        if (step.parentTypeColumn() != null) {
            column(profile, step.table(), step.parentTypeColumn());
            // The collation comparison lets the type index drive the join; the byte comparison
            // keeps a type spelled otherwise ('mds') out, as the routing scopes do.
            String type = alias + ".`" + step.parentTypeColumn() + "`";
            match += " and " + type + "='" + step.parentTypeValue() + "'";
            if (exact)
                match +=
                        " and CAST("
                                + type
                                + " AS BINARY)=CAST('"
                                + step.parentTypeValue()
                                + "' AS BINARY)";
        }
        return match;
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
        // Extension values copied with an archived request version name that version's Id.
        add(
                result,
                "consultationRequestExtArchive",
                "consultationRequestsArchive",
                "consultationRequestArchiveId",
                "Id",
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
        // A reviewer of an incoming document that has not been filed to a chart yet is an inbox
        // row; the document's stored file (document_storage) is not, and keeps holding.
        for (String table : List.of("DocumentExtraReviewer", "document_review", "document_storage"))
            result.put(
                    table,
                    new Scope(
                            List.of(
                                    new Variant(
                                            null,
                                            document(
                                                    table.equals("document_review")
                                                            ? "document_no"
                                                            : "documentNo",
                                                    table.equals("document_storage")
                                                            ? null
                                                            : new Unlinked(-1L))))));
        // A DOC routing row belongs to the document it routes, whose chart is its ctl_document
        // link (a reassigned document keeps its earlier patientLabRouting row). Every other type
        // is a lab whose chart is its patientLabRouting row of exactly the same type.
        result.put(
                "providerLabRouting",
                new Scope(
                        List.of(
                                new Variant(
                                        "d.`lab_type` is not null and CAST(d.`lab_type` AS"
                                                + " BINARY)=CAST('DOC' AS BINARY)",
                                        document("lab_no", new Unlinked(-1L))),
                                new Variant(
                                        null,
                                        new Chain(
                                                List.of(
                                                        new Step(
                                                                "patientLabRouting",
                                                                "lab_no",
                                                                "lab_no",
                                                                "lab_type",
                                                                "lab_type")),
                                                "demographic_no",
                                                false,
                                                new Unlinked(0L))))));
        // An HRM report no patient has been matched to has no HRMDocumentToDemographic row.
        result.put(
                "HRMDocument",
                new Scope(
                        List.of(
                                new Variant(
                                        null,
                                        new Chain(
                                                List.of(
                                                        new Step(
                                                                "HRMDocumentToDemographic",
                                                                "id",
                                                                "hrmDocumentId")),
                                                "demographicNo",
                                                false,
                                                new Unlinked(null))))));
        for (String table :
                List.of("HRMDocumentComment", "HRMDocumentSubClass", "HRMDocumentToProvider"))
            result.put(
                    table,
                    new Scope(
                            List.of(
                                    new Variant(
                                            null,
                                            new Chain(
                                                    List.of(
                                                            new Step(
                                                                    "HRMDocument",
                                                                    "hrmDocumentId",
                                                                    "id"),
                                                            new Step(
                                                                    "HRMDocumentToDemographic",
                                                                    "id",
                                                                    "hrmDocumentId")),
                                                    "demographicNo",
                                                    false,
                                                    new Unlinked(null))))));
        // Data-summary children (DOCUMENT): each row belongs to the chart of its parent record.
        add(result, "emailAttachment", "emailLog", "logId", "id", "demographicNo");
        add(result, "formBCAR2020Data", "formBCAR2020", "form_id", "id", "demographic_no");
        add(result, "formBCAR2020Text", "formBCAR2020", "form_id", "id", "demographic_no");
        // A message belongs to every chart msgDemoMap links it to.
        add(result, "messagetbl", "msgDemoMap", "messageid", "messageID", "demographic_no");
        // Rourke checkbox values name their form table in form_name; the same form_id in the
        // other Rourke table is a different record, and a row naming neither belongs to no chart.
        result.put(
                "form_boolean_value",
                new Scope(
                        List.of(
                                new Variant(
                                        "d.`form_name` is not null and CAST(d.`form_name` AS"
                                                + " BINARY)=CAST('formRourke2017' AS BINARY)",
                                        rourke("formRourke2017")),
                                new Variant(null, rourke("formRourke2020")))));
        // Listed children of removed features (LISTED): counted per chart, never exported.
        add(result, "caisi_form_data", "caisi_form_instance", "instance_id", "id", "client_id");
        legacyLaboratories(result);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Legacy laboratory reports (data summaries, plus the two listed MDS segments). CARLOS shows
     * such a report in the chart its {@code patientLabRouting} row of exactly the report's type
     * names, as it does for HL7 reports: MDS (lab_no is {@code mdsMSH.segmentID}), CML (lab_no is
     * {@code labPatientPhysicianInfo.id}) and the BC PathNet format BCP (lab_no is {@code
     * hl7_message.message_id}). {@code mdsMSH.demographic_no} and {@code hl7_link.demographic_no}
     * are copies the chart never reads, so they decide nothing here. Every row is scoped straight
     * to the report's route, not through the report's header row, so a segment whose header row
     * is missing is still exported with the chart its lab number is routed to (as its own record)
     * instead of being dropped.
     */
    private static void legacyLaboratories(Map<String, Scope> result) {
        for (String table :
                List.of(
                        "mdsMSH", "mdsPID", "mdsPV1", "mdsOBR", "mdsOBX", "mdsNTE", "mdsZMC",
                        "mdsZMN", "mdsZRG", "mdsZFR", "mdsZLB"))
            chain(result, table, Step.route("segmentID", "MDS"));
        chain(result, "labPatientPhysicianInfo", Step.route("id", "CML"));
        chain(result, "labTestResults", Step.route("labPatientPhysicianInfo_id", "CML"));
        // An upload's header row (labReportInformation) is shared by the CML reports of that
        // upload; it belongs to the chart of each report that names it.
        chain(
                result,
                "labReportInformation",
                new Step("labPatientPhysicianInfo", "id", "labReportInfo_id"),
                Step.route("id", "CML"));
        for (String table : List.of("hl7_message", "hl7_msh", "hl7_pid"))
            chain(result, table, Step.route("message_id", "BCP"));
        for (String table : List.of("hl7_link", "hl7_obr", "hl7_orc"))
            chain(
                    result,
                    table,
                    new Step("hl7_pid", "pid_id", "pid_id"),
                    Step.route("message_id", "BCP"));
        chain(
                result,
                "hl7_obx",
                new Step("hl7_obr", "obr_id", "obr_id"),
                new Step("hl7_pid", "pid_id", "pid_id"),
                Step.route("message_id", "BCP"));
        // A requisition link belongs to the chart of the report it names (report_table is the
        // report's table, LabRequestReportLink): a CML report, or an HL7 report routed as HL7. A
        // link naming any other table belongs to no chart.
        result.put(
                "labRequestReportLink",
                new Scope(
                        List.of(
                                new Variant(
                                        "d.`report_table` is not null and CAST(d.`report_table`"
                                                + " AS BINARY)=CAST('labPatientPhysicianInfo' AS"
                                                + " BINARY)",
                                        new Chain(
                                                List.of(Step.route("report_id", "CML")),
                                                "demographic_no",
                                                false,
                                                null)),
                                new Variant(
                                        null,
                                        new Chain(
                                                List.of(Step.route("report_id", "HL7")),
                                                "demographic_no",
                                                false,
                                                null,
                                                "report_table",
                                                "hl7TextMessage")))));
    }

    private static void chain(Map<String, Scope> result, String table, Step... steps) {
        result.put(
                table,
                new Scope(
                        List.of(
                                new Variant(
                                        null,
                                        new Chain(List.of(steps), "demographic_no", false, null)))));
    }

    private static Chain rourke(String form) {
        return new Chain(
                List.of(new Step(form, "form_id", "ID")),
                "demographic_no",
                false,
                null,
                "form_name",
                form);
    }

    private static Chain document(String childColumn, Unlinked unlinked) {
        return new Chain(
                List.of(
                        new Step("document", childColumn, "document_no"),
                        new Step("ctl_document", "document_no", "document_no")),
                "module_id",
                true,
                unlinked);
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
                new Scope(
                        List.of(
                                new Variant(
                                        null,
                                        new Chain(
                                                List.of(
                                                        new Step(
                                                                parent, childColumn, parentColumn)),
                                                owner,
                                                false,
                                                null)))));
    }

    private static void review() {
        throw new CdsExportReviewException(
                CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED);
    }
}
