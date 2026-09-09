/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Selects an isolated, per-browser-flow workspace for prescription requests. */
public class RxSessionFilter implements Filter {

    public static final String CONTEXT_PARAMETER = "rxContextId";
    public static final String CONTEXT_HEADER = "X-Rx-Context";
    public static final String CONTEXT_REQUEST_ATTRIBUTE = "rxContextId";
    public static final String DEMOGRAPHIC_REQUEST_ATTRIBUTE = "rxDemographicNo";

    private static final Logger logger = MiscUtils.getLogger();
    private static final Set<String> ENTRY_ROUTES = Set.of(
            "/rx/choosePatient",
            "/rx/showAllergy",
            "/rx/ViewStaticScript2",
            "/rx/ViewPrintDrugProfile2");
    private static final Set<String> STATELESS_ROUTES = Set.of(
            "/rx/drugInfo",
            "/rx/updateDrugrefDB");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String route = route(request);

        if (isPublicRxResource(route) || STATELESS_ROUTES.contains(route)) {
            chain.doFilter(req, res);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            chain.doFilter(req, res);
            return;
        }

        String providerNo = currentProviderNo(request);
        if (providerNo == null || providerNo.isBlank()) {
            reject(request, response, "rx_context_provider_missing");
            return;
        }

        String parameterContext = trimToNull(request.getParameter(CONTEXT_PARAMETER));
        String headerContext = trimToNull(request.getHeader(CONTEXT_HEADER));
        if (parameterContext != null && headerContext != null && !parameterContext.equals(headerContext)) {
            reject(request, response, "rx_context_conflict");
            return;
        }
        String contextId = parameterContext != null ? parameterContext : headerContext;

        DemographicParameter demographic = readDemographic(request);
        if (demographic.invalid) {
            reject(request, response, "rx_demographic_invalid");
            return;
        }

        RxWorkspaceRegistry registry = RxWorkspaceRegistry.getOrCreate(session);
        if (contextId == null && ENTRY_ROUTES.contains(route) && "GET".equalsIgnoreCase(request.getMethod())) {
            if (demographic.value <= 0) {
                reject(request, response, "rx_demographic_missing");
                return;
            }
            RxWorkspaceRegistry.RxWorkspace workspace = registry.create(demographic.value, providerNo);
            redirectWithContext(request, response, workspace.getContextId());
            return;
        }

        if (!validUuid(contextId)) {
            reject(request, response, "rx_context_missing");
            return;
        }

        RxWorkspaceRegistry.RxWorkspace workspace = registry.find(contextId);
        if (workspace == null) {
            reject(request, response, "rx_context_unknown");
            return;
        }
        if (!providerNo.equals(workspace.getProviderNo())) {
            reject(request, response, "rx_context_provider_mismatch");
            return;
        }
        if (demographic.value > 0 && demographic.value != workspace.getDemographicNo()) {
            reject(request, response, "rx_context_demographic_mismatch");
            return;
        }

        request.setAttribute(CONTEXT_REQUEST_ATTRIBUTE, workspace.getContextId());
        request.setAttribute(DEMOGRAPHIC_REQUEST_ATTRIBUTE, workspace.getDemographicNo());

        workspace.lock();
        try {
            chain.doFilter(
                    new RxContextRequest(request, workspace),
                    new RxContextResponse(response, request.getContextPath(), workspace.getContextId()));
        } finally {
            workspace.unlock();
        }
    }

    private static String currentProviderNo(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo != null && loggedInInfo.getLoggedInProviderNo() != null) {
            return loggedInInfo.getLoggedInProviderNo();
        }
        Object user = request.getSession().getAttribute("user");
        return user == null ? null : user.toString();
    }

    private static DemographicParameter readDemographic(HttpServletRequest request) {
        int result = -1;
        for (String name : new String[] {"demographicNo", "demographic_no", "demoNo"}) {
            String raw = trimToNull(request.getParameter(name));
            if (raw == null) {
                continue;
            }
            int parsed;
            try {
                parsed = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                return DemographicParameter.invalid();
            }
            if (parsed <= 0 || result > 0 && result != parsed) {
                return DemographicParameter.invalid();
            }
            result = parsed;
        }
        return DemographicParameter.valid(result);
    }

    private static boolean validUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String route(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private static boolean isPublicRxResource(String route) {
        return route.startsWith("/rx/img/")
                || route.equals("/rx/styles.css")
                || route.equals("/rx/close.html")
                || route.equals("/rx/error.html")
                || route.equals("/rx/index.html");
    }

    private static void redirectWithContext(
            HttpServletRequest request, HttpServletResponse response, String contextId) throws IOException {
        StringBuilder location = new StringBuilder(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            location.append('?').append(query).append('&');
        } else {
            location.append('?');
        }
        location.append(CONTEXT_PARAMETER).append('=')
                .append(URLEncoder.encode(contextId, StandardCharsets.UTF_8));
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", response.encodeRedirectURL(location.toString()));
    }

    private static void reject(
            HttpServletRequest request, HttpServletResponse response, String errorCode) throws IOException {
        logger.warn("Rejected prescription request {}: {}", request.getRequestURI(), errorCode);
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (isAjax(request)) {
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + errorCode + "\"}");
        } else {
            response.setContentType("text/html");
            response.getWriter().write("<!doctype html><html><body><h1>Prescription context unavailable</h1>"
                    + "<p>This prescription window is no longer valid. Close it and reopen Prescriptions "
                    + "from the patient chart.</p></body></html>");
        }
    }

    private static boolean isAjax(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || accept != null && accept.contains("application/json");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class DemographicParameter {
        private final int value;
        private final boolean invalid;

        private DemographicParameter(int value, boolean invalid) {
            this.value = value;
            this.invalid = invalid;
        }

        private static DemographicParameter valid(int value) {
            return new DemographicParameter(value, false);
        }

        private static DemographicParameter invalid() {
            return new DemographicParameter(-1, true);
        }
    }
}
