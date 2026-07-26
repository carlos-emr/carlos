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
        } catch (RuntimeException e) {
            logger.error("Renderer APCache lookup failed: fdid={} type={}",
                    grant.fdid(), e.getClass().getName());
            if (!response.isCommitted()) {
                response.sendError(422,
                        "Renderer APCache lookup failed");
            }
        }
    }

    private static String execute(EForm form, DatabaseAP ap) {
        ParameterizedSql query = form.parameterizeAllFields(ap.getApSQL());
        String output = ap.getApOutput();
        ArrayList<String> names = DatabaseAP.parserGetNames(output);
        if (ap.isJsonOutput()) {
            return EFormUtil.getJsonValues(names, query).toString();
        }
        List<String> values = EFormUtil.getValues(names, query);
        if (values.size() != names.size()) {
            return "";
        }
        for (int i = 0; i < names.size(); i++) {
            output = DatabaseAP.parserReplace(names.get(i), values.get(i), output);
        }
        return output;
    }

    private static void hidden(PrintWriter writer, String name, String value) {
        writer.print("<input type=\"hidden\" name=\"");
        writer.print(SafeEncode.forHtmlAttribute(name));
        writer.print("\" value=\"");
        writer.print(SafeEncode.forHtmlAttribute(value));
        writer.print("\"/>");
    }

    private record ApResult(String key, String value) {
    }

    private record ApDefinition(String key, DatabaseAP ap) {
    }
}
