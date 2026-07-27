/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/** Read-only, capability-scoped APCache bridge for the browser PDF renderer. */
public final class EFormApCacheForPdfGenerationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = MiscUtils.getLogger();
    private static final int MAX_KEYS = 32;
    private static final String KEY_PATTERN = "[A-Za-z0-9_$.-]{1,128}";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!EFormRendererRequestAuthorization.isLoopback(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        EFormRenderTokenService.RenderGrant grant =
                EFormRendererRequestAuthorization.grantFromCookie(request);
        if (grant == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String[] requestedKeys = request.getParameterValues("key");
        if (requestedKeys == null || requestedKeys.length == 0 || requestedKeys.length > MAX_KEYS) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid APCache key count");
            return;
        }
        for (String key : requestedKeys) {
            if (key == null || !key.matches(KEY_PATTERN)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid APCache key");
                return;
            }
            if (!grant.allowsApKey(key)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        String lookupType = request.getParameter("oscarAPCacheLookupType");
        if (lookupType != null && !lookupType.matches(KEY_PATTERN)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid APCache lookup type");
            return;
        }

        try {
            // getAP reads a static list that only getInstance() populates (via parseXML). This
            // servlet resolves keys before it constructs its EForm, so on a JVM whose first eForm
            // touch is a scheduled render every key would otherwise resolve null and be reported as
            // "not configured" — misdirecting the operator to apconfig.xml for a load-order bug.
            EFormLoader.getInstance();
            List<ApDefinition> definitions = new ArrayList<>(requestedKeys.length);
            for (String key : requestedKeys) {
                DatabaseAP ap = EFormLoader.getAP(key);
                if (ap == null) {
                    response.sendError(422,
                            "APCache key is not configured");
                    return;
                }
                if (DatabaseAP.parserGetNames(ap.getApSQL()).contains("appt_no")) {
                    response.sendError(422,
                            "APCache key requires unavailable appointment context");
                    return;
                }
                definitions.add(new ApDefinition(key, ap));
            }

            EForm form = new EForm(String.valueOf(grant.fdid()));
            form.setProviderNo(grant.providerNo());
            List<ApResult> results = new ArrayList<>(definitions.size());
            for (ApDefinition definition : definitions) {
                results.add(new ApResult(
                        definition.key(), execute(form, definition.ap())));
            }

            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter writer = response.getWriter()) {
                hidden(writer, "oscarAPCacheLookupType", lookupType);
                for (ApResult result : results) {
                    hidden(writer, result.key(), result.value());
                }
            }
        } catch (ApLookupException e) {
            // A key that resolved to the wrong shape is a defect, not "no data". Refusing the whole
            // batch makes the browser's XHR a 4xx, which the render network gate counts as a failed
            // content resource — the alternative (emitting an empty value) prints a blank clinical
            // field on a document nobody is watching.
            // Summary, not the throwable: every sibling render surface routes through
            // RenderLogRedaction because a third-party exception MESSAGE (not just its stack) can
            // embed URLs, and for JDBC/Hibernate the SQL text and its parameter values.
            logger.error("Renderer APCache lookup returned an unusable result: fdid={} cause={}",
                    LogSafe.sanitize(String.valueOf(grant.fdid())),
                    RenderLogRedaction.stackSummary(e));
            if (!response.isCommitted()) {
                response.sendError(422, "Renderer APCache lookup returned an unusable result");
            }
        } catch (RuntimeException e) {
            // Log the throwable: the stack trace is class names and line numbers only (PHI-free),
            // and without it this line reads "type=java.lang.NullPointerException" and nothing else.
            logger.error("Renderer APCache lookup failed: fdid={} cause={}",
                    LogSafe.sanitize(String.valueOf(grant.fdid())),
                    RenderLogRedaction.stackSummary(e));
            if (!response.isCommitted()) {
                response.sendError(422,
                        "Renderer APCache lookup failed");
            }
        }
    }

    /** Raised when an AP resolves to a result the renderer cannot turn into field content. */
    private static final class ApLookupException extends Exception {
        private static final long serialVersionUID = 1L;

        private ApLookupException(String message) {
            super(message);
        }
    }

    private static String execute(EForm form, DatabaseAP ap) throws ApLookupException {
        ParameterizedSql query = form.parameterizeAllFields(ap.getApSQL());
        String output = ap.getApOutput();
        ArrayList<String> names = DatabaseAP.parserGetNames(output);
        if (ap.isJsonOutput()) {
            // The JSON branch bypassed the guard below entirely: getJsonValues reports a blocked or
            // failed query as an empty array, and writes an unreadable column into the JSON as the
            // literal <(name)NotFound>. Both reached the document — one as a blank clinical field
            // over HTTP 200, the other as that text rendered where a value belongs.
            com.fasterxml.jackson.databind.node.ArrayNode jsonValues =
                    EFormUtil.getJsonValuesOrNull(names, query);
            if (jsonValues == null) {
                throw new ApLookupException("AP JSON query could not be executed or read");
            }
            return jsonValues.toString();
        }
        // getValuesOrNull, not getValues: the latter reports a failed or blocked query as an empty
        // list, which is the same value a healthy query returns for a patient with no matching data.
        // Reading it as "no data" printed a blank clinical field over HTTP 200 — invisible to every
        // render gate and to the clinician receiving the document.
        List<String> values = EFormUtil.getValuesOrNull(names, query);
        if (values == null) {
            throw new ApLookupException("AP query could not be executed or read");
        }
        if (names.isEmpty()) {
            // A constant AP output declares no ${name} markers, so there is nothing to substitute
            // and no row is required. Checked before the empty test below, which would otherwise
            // blank the literal.
            return output;
        }
        if (values.isEmpty()) {
            // Genuinely no rows ("no active allergies"). That is data, and an empty field is the
            // correct rendering of it.
            return "";
        }
        if (values.size() != names.size()) {
            // Defensive only, and deliberately kept. getValuesOrNull returns either null, an empty
            // list, or exactly names.size() values, so this cannot fire today — but it is the assert
            // that a future change to that contract must trip rather than silently mis-substitute.
            throw new ApLookupException("AP output declares " + names.size()
                    + " names but the query returned " + values.size() + " values");
        }
        for (int i = 0; i < names.size(); i++) {
            output = DatabaseAP.parserReplace(names.get(i), values.get(i), output);
        }
        return output;
    }

    /**
     * Emits one hidden input. Both the name and the value are HTML-attribute encoded on the way
     * out; the surrounding markup is a fixed literal, so nothing unencoded reaches the writer.
     */
    private static void hidden(PrintWriter writer, String name, String value) {
        writer.print("<input type=\"hidden\" name=\"");
        writer.print(SafeEncode.forHtmlAttribute(name)); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- HTML-attribute encoded with SafeEncode on this line
        writer.print("\" value=\"");
        writer.print(SafeEncode.forHtmlAttribute(value)); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- HTML-attribute encoded with SafeEncode on this line
        writer.print("\"/>");
    }

    private record ApResult(String key, String value) {
    }

    private record ApDefinition(String key, DatabaseAP ap) {
    }
}
