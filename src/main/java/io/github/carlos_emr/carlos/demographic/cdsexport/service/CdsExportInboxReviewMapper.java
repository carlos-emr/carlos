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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;

/**
 * Who reviewed a lab or document report, from the inbox and document review rows CARLOS keeps
 * beside it, with an explicit disposition for every captured field.
 *
 * <p>An inbox routing row ({@code providerLabRouting}) names the provider a report was routed to
 * and that provider's status: {@code N} new, {@code F} filed without acknowledging, {@code X}
 * removed from the inbox, a blank legacy default, or {@code A} acknowledged
 * ({@code CommonLabResultData.updateReportStatus}). Provider {@code "0"}
 * ({@code ProviderLabRoutingDao.UNCLAIMED_PROVIDER}) or a blank provider is the unclaimed-inbox
 * placeholder. Only an acknowledgement by a real provider is a review: it becomes a reviewer
 * element with the provider's public name, OHIP number when it is a six-digit OHIP number, and
 * the acknowledgement time. Every other route is inbox workflow and is operational. Any other
 * status value holds the patient: its meaning is not known.
 *
 * <p>A provider's acknowledgement or inbox comment is clinical text written about the report.
 * It is never dropped and never squeezed into a field the receiver shows as something else:
 * every route with a comment travels whole, with its provider, status and time, in the report's
 * source-context carrier (the native laboratory residual record or the document report's Notes),
 * and the route's other fields are then accounted as carried with it.
 *
 * <p>A document review row ({@code document_review}, {@code DocumentExtraReviewer}) is a review
 * by its provider at its time; one without a time is kept whole in the source context, as an
 * undated primary reviewer is.
 */
final class CdsExportInboxReviewMapper {
    /** Columns of the captured inbox routing projection. */
    static final Set<String> ROUTING_COLUMNS =
            Set.of(
                    "comment",
                    "id",
                    "lab_no",
                    "lab_type",
                    "mrpDemographicNo",
                    "provider_no",
                    "status",
                    "timestamp");

    /** Inbox statuses that record workflow, not a review. */
    private static final Set<String> WORKFLOW = Set.of("", "N", "F", "X");

    /** Rule of every field of a route that is carried whole with its comment. */
    static final String CARRIED_RULE = "CARRIED_WHOLE_WITH_ITS_INBOX_REVIEW_COMMENT";

    /** One review: the provider's public values, the review time and its local provider number. */
    record Review(String providerNo, CdsExportPublicProviderDto provider, CdsExportDateDto reviewed) {
        @Override
        public String toString() {
            return "Review[protected]";
        }
    }

    /**
     * The reviews a report's rows state, the rows that travel whole in its source context, every
     * field decision, and every row those decisions cover (the review rows and each provider
     * row once).
     */
    record Reviews(
            List<Review> reviewers,
            List<CdsExportCapturedTextRowDto> carried,
            Map<String, CdsExportPublicProviderDto> providers,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            List<CdsExportCapturedTextRowDto> rows) {
        static final Reviews NONE =
                new Reviews(List.of(), List.of(), Map.of(), Map.of(), List.of());

        Reviews {
            reviewers = List.copyOf(reviewers);
            carried = List.copyOf(carried);
            providers = Collections.unmodifiableMap(new TreeMap<>(providers));
            fields = Map.copyOf(fields);
            rows = List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "Reviews[protected]";
        }
    }

    private final CdsExportPublicProviderMapper.Target target;
    private final String reviewer, reviewedElement, carrier;
    private final boolean dateOnly;

    private CdsExportInboxReviewMapper(
            CdsExportPublicProviderMapper.Target target,
            String reviewer,
            String reviewedElement,
            String carrier,
            boolean dateOnly) {
        this.target = target;
        this.reviewer = reviewer;
        this.reviewedElement = reviewedElement;
        this.carrier = carrier;
        this.dateOnly = dateOnly;
    }

    /** LaboratoryResults/ResultReviewer, with comments in the native laboratory residual. */
    static CdsExportInboxReviewMapper laboratory() {
        return new CdsExportInboxReviewMapper(
                CdsExportPublicProviderMapper.Target.RESULT_REVIEWER,
                "LaboratoryResults/ResultReviewer",
                "DateTimeResultReviewed",
                "NewCategory/ResidualInfo",
                false);
    }

    /**
     * Reports/ReportReviewed, whose review date has no time of day: every review row and every
     * acknowledged route also travels whole in the report Notes, so the exact time is kept.
     */
    static CdsExportInboxReviewMapper report() {
        return new CdsExportInboxReviewMapper(
                CdsExportPublicProviderMapper.Target.REPORT_REVIEWER,
                "Reports/ReportReviewed",
                "DateTimeReportReviewed",
                "Reports/Notes",
                true);
    }

    /**
     * Decides every inbox routing row of one report and every review row of one document.
     *
     * @param routes the report's {@code providerLabRouting} rows
     * @param reviews the document's {@code document_review} and {@code DocumentExtraReviewer} rows
     * @param providers captured public provider rows by provider number
     * @throws CdsExportMappingReviewException when a row cannot be represented; only this patient
     *     is held
     */
    Reviews map(
            List<CdsExportCapturedTextRowDto> routes,
            List<CdsExportCapturedTextRowDto> reviews,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone)
            throws IOException {
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var found = new ArrayList<Review>();
        var carried = new ArrayList<CdsExportCapturedTextRowDto>();
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var used = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var values = new TreeMap<String, CdsExportPublicProviderDto>();
        for (var route : sorted(routes)) {
            rows.add(route);
            var review = route(route, providers, zone, fields, used, values);
            if (review != null) found.add(review);
            String comment = route.fields().get("comment");
            if (comment != null && !comment.isEmpty() || dateOnly && review != null)
                carried.add(route);
        }
        for (var row : sorted(reviews)) {
            rows.add(row);
            var review = review(row, providers, zone, fields, used, values);
            if (review != null) found.add(review);
            if (review == null || dateOnly) carried.add(row);
        }
        rows.addAll(used.values());
        // A provider named only by an undated review is exported in the source context, not as a
        // reviewer element, so its public values are accounted there.
        var dated = new HashSet<String>();
        for (var review : found) dated.add(review.providerNo());
        for (var entry : used.entrySet())
            if (!dated.contains(entry.getKey()))
                for (var key : List.copyOf(fields.keySet()))
                    if (key.table().equals("provider")
                            && key.recordKey().equals(entry.getValue().recordKey())
                            && fields.get(key).outcome() == Outcome.MAPPED) {
                        var assessment = fields.get(key);
                        fields.put(
                                key,
                                new CdsExportFieldAssessmentDto(
                                        assessment.presence(),
                                        assessment.outcome(),
                                        carrier,
                                        "UNDATED_PUBLIC_REVIEWER_IN_SOURCE_CONTEXT"));
                    }
        // Two rows that state the same reviewer at the same time would write the same element.
        var distinct = new LinkedHashMap<String, Review>();
        found.sort(
                Comparator.comparing((Review review) -> review.reviewed().lexical())
                        .thenComparing(Review::providerNo));
        for (var review : found)
            distinct.putIfAbsent(review.providerNo() + "\u0000" + review.reviewed().lexical(), review);
        return new Reviews(List.copyOf(distinct.values()), carried, values, fields, rows);
    }

    /** The routes that travel whole in the source context: those with a comment, in row order. */
    static List<CdsExportCapturedTextRowDto> carried(List<CdsExportCapturedTextRowDto> routes) {
        var result = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var route : sorted(routes)) {
            String comment = route.fields().get("comment");
            if (comment != null && !comment.isEmpty()) result.add(route);
        }
        return List.copyOf(result);
    }

    /** Every field of a carried route, as accounted by the record that carries it whole. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> carriedFields(
            List<CdsExportCapturedTextRowDto> carried, String destination) {
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var route : carried) {
            var state = new CdsExportNativeFieldAssessments(route, fields);
            for (String column : route.fields().keySet())
                state.set(column, Outcome.MAPPED, destination, CARRIED_RULE);
        }
        return fields;
    }

    /**
     * Whether {@link #report()} carries a row whole in the report's source context: every review
     * row, every acknowledgement by a real provider (its element keeps only the date) and every
     * route with a comment. The inventory uses the same rule to expect the supplementary record of
     * an imported report.
     */
    static boolean carriedByReport(CdsExportCapturedTextRowDto row) {
        if (!row.table().startsWith("providerLabRouting")) return true;
        String comment = row.fields().get("comment"), provider = row.fields().get("provider_no");
        return comment != null && !comment.isEmpty()
                || "A".equals(row.fields().get("status"))
                        && provider != null
                        && !provider.isBlank()
                        && !provider.equals("0");
    }

    /**
     * The next unused ordinal of {@code element} directly under a retained occurrence's root, so
     * a review recorded in CARLOS after the import is appended after the imported ones and every
     * imported element keeps its exact bytes.
     */
    static long nextOrdinal(
            List<CdsExportRetainedFieldDto> fields,
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory category,
            String element)
            throws IOException {
        var pattern =
                java.util.regex.Pattern.compile(
                        java.util.regex.Pattern.quote(element) + "\\[([1-9][0-9]{0,17})\\](?:/.*)?");
        long highest = 0;
        for (var field : fields) {
            String root = CdsExportRetainedOccurrencePath.root(field, category);
            if (field.sourcePath().length() == root.length()) continue;
            var match = pattern.matcher(field.sourcePath().substring(root.length() + 1));
            if (match.matches()) highest = Math.max(highest, Long.parseLong(match.group(1)));
        }
        return Math.addExact(highest, 1);
    }

    /**
     * The rows that travel whole, in the fixed JSON shape of a report's source context: the
     * carried rows (inbox routes named {@code providerLabRouting}) and the public names of the
     * providers they name.
     */
    static Map<String, Object> carriedContext(Reviews reviews) {
        var details = new LinkedHashMap<String, Object>();
        details.put("version", 1);
        var carried = new ArrayList<Map<String, Object>>();
        for (var row : reviews.carried()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put(
                    "table",
                    row.table().startsWith("providerLabRouting") ? "providerLabRouting" : row.table());
            entry.put("fields", new TreeMap<>(row.fields()));
            carried.add(entry);
        }
        details.put("reviews", carried);
        var named = new LinkedHashMap<String, Object>();
        reviews.providers()
                .forEach(
                        (number, value) -> {
                            var reviewer = new LinkedHashMap<String, String>();
                            reviewer.put("firstName", value.firstName());
                            reviewer.put("lastName", value.lastName());
                            if (value.ohipNumber() != null) reviewer.put("ohip", value.ohipNumber());
                            named.put(number, reviewer);
                        });
        if (!named.isEmpty()) details.put("reviewers", named);
        return details;
    }

    /**
     * The same decisions with every field accounted in {@code from} moved to {@code to}: the
     * rows of an imported report travel in its supplementary record instead of its Notes.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> relocated(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String from,
            String to) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        fields.forEach(
                (key, value) ->
                        result.put(
                                key,
                                from.equals(value.destination())
                                        ? new CdsExportFieldAssessmentDto(
                                                value.presence(), value.outcome(), to, value.rule())
                                        : value));
        return result;
    }

    /**
     * Every field of the reviewer and routing rows of a document the chart no longer shows: the
     * same not-exported disposition as the document itself.
     */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> removed(
            List<CdsExportCapturedTextRowDto> rows, String rule) {
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : rows) {
            var state = new CdsExportNativeFieldAssessments(row, fields);
            for (String column : row.fields().keySet()) state.operational(column, rule);
        }
        return fields;
    }

    private Review route(
            CdsExportCapturedTextRowDto row,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Map<String, CdsExportCapturedTextRowDto> used,
            Map<String, CdsExportPublicProviderDto> values)
            throws IOException {
        if (!row.table().startsWith("providerLabRouting")
                || !row.fields().keySet().equals(ROUTING_COLUMNS)) invalid();
        var state = new CdsExportNativeFieldAssessments(row, fields);
        String status = row.fields().get("status"), provider = row.fields().get("provider_no");
        if (status != null && !status.equals("A") && !WORKFLOW.contains(status))
            review("CDS_EXPORT_INBOX_ROUTING_STATUS_REQUIRES_REVIEW");
        boolean unclaimed = provider == null || provider.isBlank() || provider.equals("0");
        for (String column : List.of("id", "lab_no", "lab_type"))
            state.operational(column, "LOCAL_INBOX_ROUTING_IDENTITY_AND_REPORT_LINK");
        state.operational("mrpDemographicNo", "MRP_RULE_ROUTING_PROVENANCE");
        Review result = null;
        if ("A".equals(status) && !unclaimed) {
            result =
                    reviewer(
                            provider,
                            row.fields().get("timestamp"),
                            true,
                            providers,
                            zone,
                            fields,
                            used,
                            values);
            state.set("provider_no", Outcome.MAPPED, reviewer + "/Name", "ACKNOWLEDGING_PROVIDER");
            state.set("status", Outcome.MAPPED, reviewer, "ACKNOWLEDGED_INBOX_STATUS");
            state.set(
                    "timestamp",
                    Outcome.MAPPED,
                    reviewer + "/" + reviewedElement,
                    dateOnly
                            ? "ACKNOWLEDGEMENT_DATE_ON_THE_WIRE_EXACT_TIME_IN_SOURCE_CONTEXT"
                            : "ACKNOWLEDGEMENT_TIME_IN_CLINIC_ZONE");
        } else {
            String rule =
                    unclaimed
                            ? "UNCLAIMED_INBOX_ROUTING_PLACEHOLDER"
                            : "UNACKNOWLEDGED_INBOX_ROUTING_WORKFLOW";
            for (String column : List.of("provider_no", "status", "timestamp"))
                state.operational(column, rule);
        }
        String comment = row.fields().get("comment");
        if (comment == null || comment.isEmpty())
            state.set("comment", Outcome.ABSENT, "", "NO_INBOX_REVIEW_COMMENT");
        else {
            if (!CdsExportNativeFieldAssessments.legal(comment, 1 << 24))
                review("CDS_EXPORT_INBOX_REVIEW_COMMENT_REQUIRES_REVIEW");
            state.set("comment", Outcome.MAPPED, carrier, "INBOX_REVIEW_COMMENT_IN_SOURCE_CONTEXT");
            // The route travels whole with its comment, so its workflow context is carried too.
            for (String column : ROUTING_COLUMNS) {
                var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
                if (fields.get(key).outcome() == Outcome.OPERATIONAL)
                    state.set(column, Outcome.MAPPED, carrier, CARRIED_RULE);
            }
        }
        return result;
    }

    private Review review(
            CdsExportCapturedTextRowDto row,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Map<String, CdsExportCapturedTextRowDto> used,
            Map<String, CdsExportPublicProviderDto> values)
            throws IOException {
        String provider, time, document;
        switch (row.table()) {
            case "document_review" -> {
                if (!row.fields().keySet().equals(Set.of("date_reviewed", "document_no", "id", "provider_no")))
                    invalid();
                provider = "provider_no";
                time = "date_reviewed";
                document = "document_no";
            }
            case "DocumentExtraReviewer" -> {
                if (!row.fields()
                        .keySet()
                        .equals(Set.of("documentNo", "id", "reviewDateTime", "reviewerProviderNo")))
                    invalid();
                provider = "reviewerProviderNo";
                time = "reviewDateTime";
                document = "documentNo";
            }
            default -> {
                invalid();
                return null;
            }
        }
        var state = new CdsExportNativeFieldAssessments(row, fields);
        String number = row.fields().get(provider), reviewed = row.fields().get(time);
        // A review row attests a provider; one without a provider cannot be attributed.
        if (number == null || number.isBlank() || number.equals("0"))
            review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
        for (String column : List.of("id", document))
            state.operational(column, "LOCAL_DOCUMENT_REVIEW_IDENTITY_AND_DOCUMENT_LINK");
        if (reviewed == null || reviewed.isEmpty()) {
            // The standard element requires a date; the undated review stays whole in Notes.
            requireProvider(number, providers, fields, used, values);
            state.set(provider, Outcome.MAPPED, carrier, "UNDATED_PUBLIC_REVIEWER_IN_SOURCE_CONTEXT");
            state.set(time, Outcome.ABSENT, "", "UNDATED_DOCUMENT_REVIEW");
            return null;
        }
        // DocumentExtraReviewer.reviewDateTime is a TIMESTAMP captured as its instant;
        // document_review.date_reviewed is a DATETIME, a clinic wall-clock time.
        var result =
                reviewer(
                        number,
                        reviewed,
                        time.equals("reviewDateTime"),
                        providers,
                        zone,
                        fields,
                        used,
                        values);
        state.set(provider, Outcome.MAPPED, reviewer + "/Name", "DOCUMENT_REVIEWER");
        state.set(
                time,
                Outcome.MAPPED,
                reviewer + "/" + reviewedElement,
                dateOnly ? "REVIEW_DATE_ON_THE_WIRE_EXACT_TIME_IN_SOURCE_CONTEXT" : "REVIEW_TIME_IN_CLINIC_ZONE");
        return result;
    }

    private Review reviewer(
            String number,
            String reviewed,
            boolean instant,
            Map<String, CdsExportCapturedTextRowDto> providers,
            ZoneId zone,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Map<String, CdsExportCapturedTextRowDto> used,
            Map<String, CdsExportPublicProviderDto> values)
            throws IOException {
        var value = requireProvider(number, providers, fields, used, values);
        if (reviewed == null || reviewed.isEmpty())
            review("CDS_EXPORT_INBOX_REVIEW_TIME_REQUIRED");
        return new Review(
                number,
                value,
                instant
                        ? CdsExportNativeHistoryMapper.instant(reviewed, zone)
                        : CdsExportNativeHistoryMapper.clock(reviewed, zone));
    }

    private CdsExportPublicProviderDto requireProvider(
            String number,
            Map<String, CdsExportCapturedTextRowDto> providers,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Map<String, CdsExportCapturedTextRowDto> used,
            Map<String, CdsExportPublicProviderDto> values)
            throws IOException {
        var provider = providers.get(number);
        if (provider == null) review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
        var mapped = new CdsExportPublicProviderMapper().map(provider, target);
        if (!mapped.representable()) review("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRES_REVIEW");
        fields.putAll(mapped.fields());
        used.putIfAbsent(number, provider);
        values.putIfAbsent(number, mapped.value());
        return mapped.value();
    }

    /** Ascending native row identity, so the same chart always produces the same output. */
    private static List<CdsExportCapturedTextRowDto> sorted(List<CdsExportCapturedTextRowDto> rows) {
        var result = new ArrayList<>(rows);
        result.sort(
                Comparator.comparing(CdsExportCapturedTextRowDto::table)
                        .thenComparing(CdsExportCapturedTextRowDto::recordKey));
        return result;
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_INBOX_REVIEW_SCOPE_INVALID");
    }
}
