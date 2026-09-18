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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            handleGet(request, response);
        } catch (IOException e) {
            // The client may disconnect while an error page or the APCache response is being
            // written. There is no useful second response to send once response I/O itself fails.
            logger.debug("Renderer APCache response I/O failed: cause={}",
                    RenderLogRedaction.stackSummary(e));
        }
    }

    private static void handleGet(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        RenderRequest renderRequest = validateRequest(request, response);
        if (renderRequest == null) {
            return;
        }

        EFormRenderTokenService.RenderGrant grant = renderRequest.grant();
        try {
            List<ApDefinition> definitions = loadDefinitions(renderRequest.requestedKeys());
            List<ApResult> results = executeQueries(grant, definitions);
            writeResponse(response, renderRequest.lookupType(), results);
        } catch (ApDefinitionException e) {
            // Definition failures are expected server-specific incompatibilities (an AP absent from
            // apconfig.xml, or one that needs appointment context this render cannot supply), so
            // they keep their actionable fixed messages and are not logged as lookup failures.
            // WARN, with the key: the browser only ever sees the fixed 422 text and the render
            // report only counts a failed data resource, so this line is the operator's one
            // pointer to WHICH configured AP the form depends on. The key passed KEY_PATTERN and
            // the grant allowlist and names configuration, not a patient.
            logger.warn("Renderer APCache key cannot be executed for this render: fdid={} key={} "
                    + "reason={}",
                    LogSafe.sanitize(String.valueOf(grant.fdid())),
                    LogSafe.sanitize(e.key()),
                    e.getMessage());
            sendErrorIfUncommitted(response, 422, e.getMessage());
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
            sendErrorIfUncommitted(
                    response, 422, "Renderer APCache lookup returned an unusable result");
        } catch (RuntimeException e) {
            // Log the throwable: the stack trace is class names and line numbers only (PHI-free),
            // and without it this line reads "type=java.lang.NullPointerException" and nothing else.
            logger.error("Renderer APCache lookup failed: fdid={} cause={}",
                    LogSafe.sanitize(String.valueOf(grant.fdid())),
                    RenderLogRedaction.stackSummary(e));
            sendErrorIfUncommitted(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Renderer APCache lookup failed");
        }
    }

    private static RenderRequest validateRequest(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Nothing has been written yet on this path, so isCommitted() is always false here; every
        // rejection still goes through the one helper so the servlet has a single response-error
        // policy rather than two that can drift.
        if (!EFormRendererRequestAuthorization.isLoopback(request.getRemoteAddr())) {
            sendErrorIfUncommitted(response, HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        EFormRenderTokenService.RenderGrant grant =
                EFormRendererRequestAuthorization.grantFromCookie(request);
        if (grant == null) {
            sendErrorIfUncommitted(response, HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        String[] requestedKeys = request.getParameterValues("key");
        if (requestedKeys == null || requestedKeys.length == 0 || requestedKeys.length > MAX_KEYS) {
            sendErrorIfUncommitted(
                    response, HttpServletResponse.SC_BAD_REQUEST, "Invalid APCache key count");
            return null;
        }
        for (String key : requestedKeys) {
            if (key == null || !key.matches(KEY_PATTERN)) {
                sendErrorIfUncommitted(
                        response, HttpServletResponse.SC_BAD_REQUEST, "Invalid APCache key");
                return null;
            }
            if (!grant.allowsApKey(key)) {
                sendErrorIfUncommitted(response, HttpServletResponse.SC_FORBIDDEN);
                return null;
            }
        }

        String lookupType = request.getParameter("oscarAPCacheLookupType");
        if (lookupType != null && !lookupType.matches(KEY_PATTERN)) {
            sendErrorIfUncommitted(
                    response, HttpServletResponse.SC_BAD_REQUEST, "Invalid APCache lookup type");
            return null;
        }
        return new RenderRequest(grant, List.of(requestedKeys), lookupType);
    }

    private static List<ApDefinition> loadDefinitions(List<String> requestedKeys)
            throws ApDefinitionException {
        // getAP reads a static list that only getInstance() populates (via parseXML). This servlet
        // resolves keys before it constructs its EForm, so on a JVM whose first eForm touch is a
        // scheduled render every key would otherwise resolve null and be reported as "not
        // configured" — misdirecting the operator to apconfig.xml for a load-order bug.
        EFormLoader.getInstance();
        List<ApDefinition> definitions = new ArrayList<>(requestedKeys.size());
        for (String key : requestedKeys) {
            DatabaseAP ap = EFormLoader.getAP(key);
            if (ap == null) {
                throw new ApDefinitionException(key, "APCache key is not configured");
            }
            if (DatabaseAP.parserGetNames(ap.getApSQL()).contains("appt_no")) {
                throw new ApDefinitionException(
                        key, "APCache key requires unavailable appointment context");
            }
            definitions.add(new ApDefinition(key, ap));
        }
        return definitions;
    }

    private static List<ApResult> executeQueries(
            EFormRenderTokenService.RenderGrant grant, List<ApDefinition> definitions)
            throws ApLookupException {
        EForm form = new EForm(String.valueOf(grant.fdid()));
        form.setProviderNo(grant.providerNo());
        List<ApResult> results = new ArrayList<>(definitions.size());
        for (ApDefinition definition : definitions) {
            results.add(new ApResult(definition.key(), execute(form, definition.ap())));
        }
        return results;
    }

    private static void writeResponse(
            HttpServletResponse response, String lookupType, List<ApResult> results)
            throws IOException {
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
    }

    /**
     * Sends an error response unless the response is already committed. Once bytes have gone out
     * there is no second response to send, and calling {@code sendError} then only raises an
     * {@code IllegalStateException} that would replace the real failure in the logs.
     */
    private static void sendErrorIfUncommitted(
            HttpServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) {
            response.sendError(status, message);
        }
    }

    /** Same guard, for rejections that use the container's default error text. */
    private static void sendErrorIfUncommitted(
            HttpServletResponse response, int status) throws IOException {
        if (!response.isCommitted()) {
            response.sendError(status);
        }
    }

    /** Raised when an AP resolves to a result the renderer cannot turn into field content. */
    private static final class ApLookupException extends Exception {
        private static final long serialVersionUID = 1L;

        private ApLookupException(String message) {
            super(message);
        }
    }

    /**
     * Raised when this render request cannot safely execute a configured AP definition. The
     * message is one of the fixed operator-facing texts sent as the 422 body; the key is kept
     * separately so the log can name the AP without that name ever reaching the response.
     */
    private static final class ApDefinitionException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String key;

        private ApDefinitionException(String key, String message) {
            super(message);
            this.key = key;
        }

        private String key() {
            return key;
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

    private record RenderRequest(
            EFormRenderTokenService.RenderGrant grant,
            List<String> requestedKeys,
            String lookupType) {
    }
}
