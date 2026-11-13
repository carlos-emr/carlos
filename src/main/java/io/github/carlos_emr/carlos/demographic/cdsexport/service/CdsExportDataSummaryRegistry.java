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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The one reviewed grouping of data-summary (DOCUMENT) tables into exported records. Every
 * DOCUMENT table of the source catalogue has exactly one {@link Rule}; there is no fallback, and
 * {@code CdsExportDataSummaryRegistryUnitTest} fails the build when the catalogue and this registry
 * disagree.
 *
 * <p>A <em>root</em> table makes one record per row: an encounter-form revision, an eForm instance,
 * a consultation request or response, a tickler, an e-mail, a fax, a message, a consent and so on.
 * A <em>child</em> table's row joins the one record its link columns name (a tickler comment
 * joins its tickler; a Rourke checkbox value joins the Rourke revision its form_name and form_id
 * name). A child row whose parent is not part of this chart, or whose link names more than one
 * candidate record, becomes its own record instead of being guessed into one.
 *
 * <p>The CDS 5.2 {@code ReportClass} of each kind is a reviewed choice recorded here; the maintainer
 * may change it per kind without touching capture or emission.
 *
 * @since 2026-11-13
 */
public final class CdsExportDataSummaryRegistry {
    /** One exported record kind: its SubClass label and its CDS 5.2 ReportClass. */
    public enum Kind {
        ENCOUNTER_FORM("Encounter form", "Medical Records Report"),
        EFORM("eForm", "Medical Records Report"),
        CONSULTATION_REQUEST("Consultation request", "Other Letter"),
        CONSULTATION_RESPONSE("Consultation response", "Consultant Report"),
        DIGITAL_SIGNATURE("Digital signature", "Medical Records Report"),
        TICKLER("Tickler", "Medical Records Report"),
        EMAIL("E-mail", "Other Letter"),
        FAX("Fax transmission", "Other Letter"),
        MESSAGE("Message", "Other Letter"),
        PATIENT_PROFILE("Cumulative patient profile", "Medical Records Report"),
        PATIENT_PHOTO("Patient photo", "Medical Records Report"),
        CONSENT("Consent", "Medical Records Report"),
        WAITING_LIST("Waiting list entry", "Medical Records Report"),
        WORKFLOW("Workflow", "Medical Records Report"),
        PROGRAM_REFERRAL("Program referral", "Medical Records Report"),
        JOINT_ADMISSION("Joint admission", "Medical Records Report"),
        PROGRAM_RESTRICTION("Program restriction", "Medical Records Report"),
        PROGRAM_QUEUE("Program queue entry", "Medical Records Report");

        private final String label;
        private final String reportClass;

        Kind(String label, String reportClass) {
            this.label = label;
            this.reportClass = reportClass;
        }

        public String label() {
            return label;
        }

        /** A value of the CDS 5.2 {@code cdsd:reportClass} enumeration. */
        public String reportClass() {
            return reportClass;
        }
    }

    /**
     * How a binary column is shown. Every binary value also shows its stored size and SHA-256.
     * {@code IMAGE}: the bytes, or their strict Base64 decoding, are embedded when they carry a PNG,
     * JPEG or GIF signature. {@code BASE64_TEXT}: the strict Base64 decoding is shown as text when it
     * is valid UTF-8 (CARLOS stores e-mail bodies this way).
     */
    public enum BinaryPresentation {
        IMAGE,
        BASE64_TEXT
    }

    /**
     * A child row joins the record of {@code parent} whose {@code parentColumns} equal its
     * {@code childColumns} (exact captured text); {@code constantColumn}, when set, must hold the
     * parent table's name.
     */
    public record Link(
            String parent,
            List<String> childColumns,
            List<String> parentColumns,
            String constantColumn) {
        public Link {
            Objects.requireNonNull(parent);
            childColumns = List.copyOf(childColumns);
            parentColumns = List.copyOf(parentColumns);
            if (childColumns.isEmpty() || childColumns.size() != parentColumns.size())
                throw new IllegalArgumentException("CDS_EXPORT_DATA_SUMMARY_LINK_INVALID");
        }
    }

    /**
     * The clinical date of a record: {@code dateColumn} (a date or date-time) and, when the table
     * stores the time separately, {@code timeColumn}.
     */
    public record Event(String dateColumn, String timeColumn) {}

    /**
     * The reviewed rule of one table. A root has no links; a child has one or more candidate
     * parents. {@code revisioned} roots number their rows "revision n of m" in identity order.
     */
    public record Rule(
            String table,
            Kind kind,
            boolean revisioned,
            List<Link> links,
            Event event,
            Map<String, BinaryPresentation> binaries) {
        public Rule {
            Objects.requireNonNull(table);
            Objects.requireNonNull(kind);
            links = List.copyOf(links);
            binaries = Map.copyOf(binaries);
        }

        public boolean root() {
            return links.isEmpty();
        }
    }

    /** Encounter-form tables: every saved row is one revision of the form. */
    static final List<String> FORMS =
            List.of(
                    "form2MinWalk",
                    "formAdfV2",
                    "formAlpha",
                    "formAnnual",
                    "formAnnualV2",
                    "formBCAR",
                    "formBCAR2007",
                    "formBCAR2012",
                    "formBCAR2020",
                    "formBCBirthSumMo",
                    "formBCBirthSumMo2008",
                    "formBCClientChartChecklist",
                    "formBCHP",
                    "formBCINR",
                    "formBCNewBorn",
                    "formBCNewBorn2008",
                    "formBPMH",
                    "formCaregiver",
                    "formCESD",
                    "formchf",
                    "formConsult",
                    "formCostQuestionnaire",
                    "formCounseling",
                    "formDischargeSummary",
                    "formFalls",
                    "formGripStrength",
                    "formGrowth0_36",
                    "formGrowthChart",
                    "formHomeFalls",
                    "formImmunAllergy",
                    "formIntakeInfo",
                    "formInternetAccess",
                    "formLabReq",
                    "formLabReq07",
                    "formLabReq10",
                    "formLateLifeFDIDisability",
                    "formLateLifeFDIFunction",
                    "formMentalHealth",
                    "formMentalHealthForm1",
                    "formMentalHealthForm14",
                    "formMentalHealthForm42",
                    "formMMSE",
                    "formPalliativeCare",
                    "formPeriMenopausal",
                    "formPositionHazard",
                    "formreceptionassessment",
                    "formRhImmuneGlobulin",
                    "formRourke",
                    "formRourke2006",
                    "formRourke2009",
                    "formRourke2017",
                    "formRourke2020",
                    "formSatisfactionScale",
                    "formSelfAdministered",
                    "formSelfAssessment",
                    "formSelfEfficacy",
                    "formSelfManagement",
                    "formSF36",
                    "formSF36Caregiver",
                    "formTreatmentPref",
                    "formVTForm",
                    "wcb");

    private static final Map<String, Rule> RULES = rules();

    private CdsExportDataSummaryRegistry() {}

    /** Every registered table, in registration order (roots before their children). */
    public static Set<String> tables() {
        return RULES.keySet();
    }

    /**
     * The rule of a registered table.
     *
     * @throws IllegalArgumentException for a table without a reviewed rule
     */
    public static Rule rule(String table) {
        var rule = RULES.get(table);
        if (rule == null)
            throw new IllegalArgumentException("CDS_EXPORT_DATA_SUMMARY_TABLE_UNREGISTERED");
        return rule;
    }

    private static Map<String, Rule> rules() {
        var rules = new LinkedHashMap<String, Rule>();
        for (String form : FORMS)
            root(rules, form, Kind.ENCOUNTER_FORM, true, new Event("formCreated", null), Map.of());
        // Children of encounter-form revisions.
        child(
                rules,
                "form_boolean_value",
                Kind.ENCOUNTER_FORM,
                null,
                new Link("formRourke2017", List.of("form_id"), List.of("ID"), "form_name"),
                new Link("formRourke2020", List.of("form_id"), List.of("ID"), "form_name"));
        child(
                rules,
                "formBCAR2020Data",
                Kind.ENCOUNTER_FORM,
                new Event("field_edited", null),
                new Link("formBCAR2020", List.of("form_id"), List.of("id"), null));
        child(
                rules,
                "formBCAR2020Text",
                Kind.ENCOUNTER_FORM,
                new Event("field_edited", null),
                new Link("formBCAR2020", List.of("form_id"), List.of("id"), null));
        // The annual review planner is opened from these three forms with their revision id.
        child(
                rules,
                "desannualreviewplan",
                Kind.ENCOUNTER_FORM,
                new Event("des_date", "des_time"),
                new Link("formAnnual", List.of("form_no"), List.of("ID"), null),
                new Link("formAnnualV2", List.of("form_no"), List.of("ID"), null),
                new Link("formAdfV2", List.of("form_no"), List.of("ID"), null));
        // The antenatal planner is opened from the BC antenatal record pages with their id.
        child(
                rules,
                "desaprisk",
                Kind.ENCOUNTER_FORM,
                new Event("desaprisk_date", "desaprisk_time"),
                new Link("formBCAR", List.of("form_no"), List.of("ID"), null),
                new Link("formBCAR2007", List.of("form_no"), List.of("ID"), null),
                new Link("formBCAR2012", List.of("form_no"), List.of("ID"), null));
        root(rules, "workflow", Kind.WORKFLOW, false, new Event("create_date_time", null), Map.of());
        // eForms.
        root(rules, "eform_data", Kind.EFORM, false, new Event("form_date", "form_time"), Map.of());
        child(
                rules,
                "eform_values",
                Kind.EFORM,
                null,
                new Link("eform_data", List.of("fdid"), List.of("fdid"), null));
        child(
                rules,
                "EFormDocs",
                Kind.EFORM,
                new Event("attach_date", null),
                new Link("eform_data", List.of("fdid"), List.of("fdid"), null));
        // Consultations.
        root(
                rules,
                "consultationRequests",
                Kind.CONSULTATION_REQUEST,
                false,
                new Event("referalDate", null),
                Map.of());
        child(
                rules,
                "consultationRequestExt",
                Kind.CONSULTATION_REQUEST,
                new Event("dateCreated", null),
                new Link(
                        "consultationRequests",
                        List.of("requestId"),
                        List.of("requestId"),
                        null));
        child(
                rules,
                "consultdocs",
                Kind.CONSULTATION_REQUEST,
                new Event("attach_date", null),
                new Link(
                        "consultationRequests",
                        List.of("requestId"),
                        List.of("requestId"),
                        null));
        root(
                rules,
                "consultationResponse",
                Kind.CONSULTATION_RESPONSE,
                false,
                new Event("responseDate", null),
                Map.of());
        child(
                rules,
                "consultResponseDoc",
                Kind.CONSULTATION_RESPONSE,
                new Event("attachDate", null),
                new Link(
                        "consultationResponse",
                        List.of("responseId"),
                        List.of("responseId"),
                        null));
        // The signature row names its module but not the record; it stays its own summary.
        root(
                rules,
                "DigitalSignature",
                Kind.DIGITAL_SIGNATURE,
                false,
                new Event("dateSigned", null),
                Map.of("signatureImage", BinaryPresentation.IMAGE));
        // Ticklers with their comments, updates and attachment references.
        root(rules, "tickler", Kind.TICKLER, false, new Event("service_date", null), Map.of());
        for (String table : List.of("tickler_comments", "tickler_update"))
            child(
                    rules,
                    table,
                    Kind.TICKLER,
                    new Event("update_date", null),
                    new Link("tickler", List.of("tickler_no"), List.of("tickler_no"), null));
        child(
                rules,
                "ticklerdocs",
                Kind.TICKLER,
                new Event("attach_date", null),
                new Link("tickler", List.of("tickler_id"), List.of("tickler_no"), null));
        // Messages.
        root(
                rules,
                "emailLog",
                Kind.EMAIL,
                false,
                new Event("timestamp", null),
                Map.of(
                        "body", BinaryPresentation.BASE64_TEXT,
                        "internalComment", BinaryPresentation.BASE64_TEXT));
        child(
                rules,
                "emailAttachment",
                Kind.EMAIL,
                null,
                new Link("emailLog", List.of("logId"), List.of("id"), null));
        root(rules, "faxes", Kind.FAX, false, new Event("stamp", null), Map.of());
        root(rules, "messagetbl", Kind.MESSAGE, false, new Event("thedate", "theime"), Map.of());
        child(
                rules,
                "msgDemoMap",
                Kind.MESSAGE,
                null,
                new Link("messagetbl", List.of("messageID"), List.of("messageid"), null));
        // Patient records with no CDS element of their own.
        root(
                rules,
                "casemgmt_cpp",
                Kind.PATIENT_PROFILE,
                false,
                new Event("update_date", null),
                Map.of());
        root(
                rules,
                "client_image",
                Kind.PATIENT_PHOTO,
                false,
                new Event("update_date", null),
                Map.of("contents", BinaryPresentation.IMAGE));
        root(rules, "Consent", Kind.CONSENT, false, new Event("consent_date", null), Map.of());
        root(
                rules,
                "waitingList",
                Kind.WAITING_LIST,
                false,
                new Event("onListSince", null),
                Map.of());
        root(
                rules,
                "client_referral",
                Kind.PROGRAM_REFERRAL,
                false,
                new Event("referral_date", null),
                Map.of());
        root(
                rules,
                "joint_admissions",
                Kind.JOINT_ADMISSION,
                false,
                new Event("admission_date", null),
                Map.of());
        root(
                rules,
                "program_client_restriction",
                Kind.PROGRAM_RESTRICTION,
                false,
                new Event("start_date", null),
                Map.of());
        root(
                rules,
                "program_queue",
                Kind.PROGRAM_QUEUE,
                false,
                new Event("referral_date", null),
                Map.of());
        return Collections.unmodifiableMap(rules);
    }

    private static void root(
            Map<String, Rule> rules,
            String table,
            Kind kind,
            boolean revisioned,
            Event event,
            Map<String, BinaryPresentation> binaries) {
        if (rules.putIfAbsent(table, new Rule(table, kind, revisioned, List.of(), event, binaries))
                != null) throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_RULE_DUPLICATE");
    }

    private static void child(
            Map<String, Rule> rules, String table, Kind kind, Event event, Link... links) {
        if (rules.putIfAbsent(table, new Rule(table, kind, false, List.of(links), event, Map.of()))
                != null) throw new IllegalStateException("CDS_EXPORT_DATA_SUMMARY_RULE_DUPLICATE");
    }
}
