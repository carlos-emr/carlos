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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Serves a patient's measurement history to stored eForms that still fetch it from a pre-migration
 * route, by inlining it in the page they are about to run in.
 *
 * <p>Used by <strong>both</strong> surfaces, which is the point of the class existing. The PDF
 * renderer calls it through {@code EFormRenderPdfHtmlComposer}; the two viewer JSPs call it directly.
 * When only the renderer embedded the payload, the same growth chart plotted correctly in a
 * downloaded PDF and stayed empty on the clinician's screen — a disagreement between what is filled
 * in and what is filed that is its own kind of defect.</p>
 *
 * <p>The route the forms call still exists ({@code encounter/oscarMeasurements/SetupDisplayHistory}),
 * renamed from the {@code oscarEncounter/...do} spelling they hardcode. Aliasing the old URL onto it
 * would not help: it reads its demographic from {@code EctSessionBean} in the HTTP session, requires
 * {@code _measurement} write, and returns a full JSP page, and the render browser holds no session
 * by design.</p>
 *
 * <p>Callers must invoke this during the string phase, before any {@code EForm.add*()} call —
 * {@code EFormBase.getFormHtml()} re-serializes a cached jsoup document over later string edits, so
 * a payload embedded after a DOM mutation is silently discarded.</p>
 *
 * @since 2026-07-27
 */
public final class LegacyMeasurementHistory {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Path tail of the pre-migration measurements-history route some stored forms still fetch.
     *
     * <p>Matched on the tail rather than the whole URL because the forms build the URL from
     * {@code window.location} at runtime; only this much of it is stable.</p>
     */
    private static final String LEGACY_MEASUREMENT_ROUTE = "oscarMeasurements/SetupDisplayHistory";
    /** Element the runtime shim reads the embedded history out of. */
    public static final String LEGACY_MEASUREMENT_ELEMENT_ID = "carlos-legacy-measurement-history";
    private static final String LEGACY_MEASUREMENT_AP = "who_measurements";
    /**
     * Column offsets into a {@code who_measurements} row ({@code date|ht|wt|circ|comments}), keyed by
     * the {@code type} the legacy route was called with.
     */
    private static final Map<String, Integer> LEGACY_MEASUREMENT_COLUMNS =
            Map.of("HT", 1, "WT", 2, "HEAD", 3);

    /**
     * Inlines the patient's measurement history for forms that fetch it from the pre-migration
     * {@code oscarEncounter/oscarMeasurements/SetupDisplayHistory.do} route.
     *
     * <p>The route was renamed, not removed ({@code encounter/oscarMeasurements/SetupDisplayHistory}),
     * but the render surface cannot use it: the action reads its demographic from {@code EctSessionBean}
     * in the HTTP session, requires {@code _measurement} write, and returns a full JSP page — and the
     * render browser holds no session by design. Aliasing the old URL would yield a {@code 401}.</p>
     *
     * <p>Embedding is not merely the tidier option, it is the only workable one: the forms issue a
     * <strong>synchronous</strong> {@code XMLHttpRequest} and read the parsed result on the next line,
     * so nothing that resolves asynchronously — a promise, a deferred handler, an APCache round trip —
     * can satisfy them. Only data already present in the captured HTML can be served in time.</p>
     *
     * <p>On any failure this returns the HTML untouched, which is deliberate: the shim then finds no
     * payload, the request goes to the network, and the resulting failed load is counted by the
     * completeness gate. A silently blank chart on a passing render is the outcome worth avoiding.</p>
     *
     * <p>The {@code measurementsPermitted} gate exists because this is measurement data reached
     * through an eForm: the eForm viewer requires only {@code _eform} read, so embedding
     * unconditionally handed the full dated HT/WT/HEAD history to a user who could not have
     * requested it directly. The caller decides, because only the caller knows who is asking — the
     * viewer has a {@code LoggedInInfo}, the renderer has a grant minted for the provider who
     * initiated it.</p>
     *
     * <p><strong>The gate is deliberately weaker than the route it replaces.</strong>
     * {@code EctSetupDisplayHistory2Action:58} demands {@code _measurement} <em>write</em>; all three
     * call sites here demand {@code read}. That is a considered choice, not an oversight: requiring
     * write to look at a chart is stricter than read-only access to the data warrants, and the legacy
     * route's own {@code "w"} on a read-only view is itself questionable. Anyone tightening this to
     * write should change the legacy route in the same pass rather than only this adapter.</p>
     *
     * @param html the stored form markup; returned untouched when it does not fetch the legacy route
     * @param eForm the form being rendered, supplying the demographic and {@code fdid}
     * @param measurementsPermitted whether the requester may read this patient's measurements;
     *        {@code false} embeds nothing and leaves the fail-visible path above
     * @return the markup with the payload inlined, or the input unchanged on any failure
     */
    public static String embed(String html, EForm eForm, boolean measurementsPermitted) {
        if (html == null || eForm == null || !html.contains(LEGACY_MEASUREMENT_ROUTE)) {
            return html;
        }
        if (!measurementsPermitted) {
            logger.info("Legacy measurement history not embedded: requester lacks measurement read");
            return html;
        }
        String series;
        try {
            // getAP reads a static list populated only by getInstance()/parseXML. Reaching here
            // after an EForm has been built makes that true in practice today, but only by ordering.
            EFormLoader.getInstance();
            DatabaseAP ap = EFormLoader.getAP(LEGACY_MEASUREMENT_AP);
            if (ap == null) {
                logger.warn("Form fetches the legacy measurement route but AP '{}' is not configured",
                        LEGACY_MEASUREMENT_AP);
                return html;
            }
            ArrayList<String> names = DatabaseAP.parserGetNames(ap.getApOutput());
            // getValuesOrNull distinguishes a failed query from one that matched nothing; getValues
            // reports both as empty, which here would embed an empty chart for a database error and
            // present it as "this patient has no measurements".
            List<String> values = EFormUtil.getValuesOrNull(
                    names, eForm.parameterizeAllFields(ap.getApSQL()));
            if (values == null) {
                logger.error("Legacy measurement history query failed for fdid={}",
                        LogSafe.sanitize(eForm.getFdid()));
                return html;
            }
            // One GROUP_CONCAT column. who_measurements is an unGROUPed aggregate, so it returns
            // exactly one row even for a patient with nothing recorded (the column comes back NULL,
            // which Misc.getString trims to ""). A genuinely empty list therefore should not occur;
            // it is handled as "no measurements" rather than as a failure because the query
            // demonstrably ran — a query that did NOT run returns null above and aborts the embed.
            series = values.isEmpty() ? "" : String.valueOf(values.get(0));
        } catch (RuntimeException e) {
            logger.error("Legacy measurement history lookup failed for fdid={}",
                    LogSafe.sanitize(eForm.getFdid()), e);
            return html;
        }
        return embedSeries(html, series);
    }

    /**
     * The half of {@link #embed} below the database, split out so the markup contract — which is
     * what actually breaks — is testable without a data source.
     *
     * @param series one {@code who_measurements} value: {@code date|ht|wt|circ|comments;…}
     */
    static String embedSeries(String html, String series) {
        if (html == null || !html.contains(LEGACY_MEASUREMENT_ROUTE)) {
            return html;
        }
        return insertBeforeBodyEnd(html, payloadElement(series == null ? "" : series));
    }

    /**
     * Renders one type's history as the cell pairs the calling forms scrape out of {@code responseText}:
     *
     * <pre>{@code
     * /<td title="data">([\d,\.,\/]+)<\/td>/g          -> measureArray
     * /<td title="observed date">([0-9,-]+)<\/td>/g    -> measureDateArray
     * }</pre>
     *
     * <p>Emitted flush against the tags on purpose. {@code DisplayHistory.jsp} still produces both
     * cells, but pretty-prints the date one — a newline between {@code >} and the value — which those
     * regexes do not match. Proxying the live page would therefore fill {@code measureArray} while
     * leaving {@code measureDateArray} empty, and the form dereferences the two in parallel.</p>
     *
     * <p>Rows missing a value for this type are skipped entirely so the two arrays stay aligned.</p>
     */
    static String markupFor(String series, int column) {
        StringBuilder markup = new StringBuilder();
        for (String row : series.split(";")) {
            String[] cells = row.split("\\|", -1);
            if (cells.length <= column) {
                continue;
            }
            String observed = cells[0].trim();
            String value = cells[column].trim();
            if (observed.isEmpty() || value.isEmpty()) {
                continue;
            }
            markup.append("<td title=\"data\">").append(SafeEncode.forHtmlContent(value))
                    .append("</td><td title=\"observed date\">")
                    .append(SafeEncode.forHtmlContent(observed)).append("</td>");
        }
        return markup.toString();
    }

    /**
     * Wraps the per-type markup in a JSON script block.
     *
     * <p>A {@code <script type="application/json">} rather than a hidden {@code <div>} because the
     * payload is {@code <td>} markup, and an HTML parser discards table cells that are not inside a
     * table. Script content is raw text, so it survives parsing intact. {@code <} is escaped to
     * {@code <}, which JSON decodes back and which makes a {@code </script>} sequence
     * unrepresentable in the payload.</p>
     */
    static String payloadElement(String series) {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        for (Map.Entry<String, Integer> column : LEGACY_MEASUREMENT_COLUMNS.entrySet()) {
            payload.put(column.getKey(), markupFor(series, column.getValue()));
        }
        return "<script type=\"application/json\" id=\"" + LEGACY_MEASUREMENT_ELEMENT_ID + "\">"
                + payload.toString().replace("<", "\\u003c")
                + "</script>";
    }

    private static String insertBeforeBodyEnd(String html, String fragment) {
        int bodyEnd = html.lastIndexOf("</body>");
        if (bodyEnd < 0) {
            bodyEnd = html.lastIndexOf("</BODY>");
        }
        return bodyEnd < 0
                ? html + fragment
                : html.substring(0, bodyEnd) + fragment + html.substring(bodyEnd);
    }

    private LegacyMeasurementHistory() {
    }
}
