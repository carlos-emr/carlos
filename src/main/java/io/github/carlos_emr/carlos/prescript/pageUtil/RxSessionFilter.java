/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
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
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Selects an isolated, per-browser-flow workspace for prescription requests. */
public class RxSessionFilter implements Filter {

    public static final String CONTEXT_PARAMETER = "rxContextId";
    public static final String CONTEXT_HEADER = "X-Rx-Context";
    public static final String CONTEXT_REQUEST_ATTRIBUTE = "rxContextId";
    public static final String DEMOGRAPHIC_REQUEST_ATTRIBUTE = "rxDemographicNo";
    public static final String APPOINTMENT_REQUEST_ATTRIBUTE = "rxAppointmentNo";
    public static final String PROGRAM_REQUEST_ATTRIBUTE = "rxProgramId";
    static final String HEARTBEAT_ROUTE = "/rx/workspaceHeartbeat";
    static final String CLOSE_ROUTE = "/rx/workspaceClose";

    private static final Logger logger = MiscUtils.getLogger();
    private static final Set<String> ENTRY_ROUTES = Set.of(
            "/rx/choosePatient",
            "/rx/showAllergy",
            "/rx/ViewStaticScript2",
            "/rx/ViewPrintDrugProfile2");
    private static final Set<String> STATELESS_ROUTES = Set.of(
            "/rx/drugInfo",
            "/rx/updateDrugrefDB");
    private final LaunchContextResolver launchContextResolver;

    public RxSessionFilter() {
        this(RxSessionFilter::resolveLaunchContext);
    }

    RxSessionFilter(LaunchContextResolver launchContextResolver) {
        this.launchContextResolver = launchContextResolver;
    }

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

        ContextParameter context = readContext(request);
        if (context.invalid) {
            reject(request, response, "rx_context_conflict");
            return;
        }

        DemographicParameter demographic = readDemographic(request);
        if (demographic.invalid) {
            reject(request, response, "rx_demographic_invalid");
            return;
        }

        RxWorkspaceRegistry registry = RxWorkspaceRegistry.getOrCreate(session);
        if (context.value == null && isEntryRequest(route, request)) {
            createWorkspace(request, response, registry, demographic.value, providerNo);
            return;
        }

        if (!validUuid(context.value)) {
            reject(request, response, "rx_context_missing");
            return;
        }

        RxWorkspaceRegistry.RxWorkspace workspace = registry.acquire(context.value);
        try {
            String validationError = validateWorkspace(request, workspace, demographic.value, providerNo);
            if (validationError != null) {
                reject(request, response, validationError);
                return;
            }

            if (HEARTBEAT_ROUTE.equals(route)) {
                if (!"GET".equalsIgnoreCase(request.getMethod())) {
                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    return;
                }
                registry.heartbeat(workspace);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            if (CLOSE_ROUTE.equals(route)) {
                if (!"POST".equalsIgnoreCase(request.getMethod())) {
                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    return;
                }
                registry.markClosing(workspace);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            registry.touch(workspace);
            request.setAttribute(CONTEXT_REQUEST_ATTRIBUTE, workspace.getContextId());
            request.setAttribute(DEMOGRAPHIC_REQUEST_ATTRIBUTE, workspace.getDemographicNo());
            request.setAttribute(APPOINTMENT_REQUEST_ATTRIBUTE, workspace.getAppointmentNo());
            request.setAttribute(PROGRAM_REQUEST_ATTRIBUTE, workspace.getProgramId());

            workspace.lock();
            try {
                chain.doFilter(
                        new RxContextRequest(request, workspace),
                        new RxContextResponse(response, request.getContextPath(), workspace.getContextId()));
            } finally {
                workspace.unlock();
            }
        } finally {
            registry.release(workspace);
        }
    }

    private void createWorkspace(
            HttpServletRequest request,
            HttpServletResponse response,
            RxWorkspaceRegistry registry,
            int demographicNo,
            String providerNo) throws IOException {
        if (demographicNo <= 0) {
            reject(request, response, "rx_demographic_missing");
            return;
        }
        try {
            LaunchContext launchContext = launchContextResolver.resolve(request, demographicNo, providerNo);
            RxWorkspaceRegistry.RxWorkspace workspace = registry.create(
                    demographicNo, providerNo, launchContext.appointmentNo(), launchContext.programId());
            redirectWithContext(request, response, workspace.getContextId());
        } catch (InvalidLaunchContextException e) {
            reject(request, response, e.errorCode);
        } catch (RxWorkspaceRegistry.WorkspaceLimitException e) {
            reject(request, response, "rx_context_limit");
        }
    }

    private static String validateWorkspace(
            HttpServletRequest request,
            RxWorkspaceRegistry.RxWorkspace workspace,
            int demographicNo,
            String providerNo) {
        if (workspace == null) {
            return "rx_context_unknown";
        }
        if (!providerNo.equals(workspace.getProviderNo())) {
            return "rx_context_provider_mismatch";
        }
        if (demographicNo > 0 && demographicNo != workspace.getDemographicNo()) {
            return "rx_context_demographic_mismatch";
        }
        try {
            Integer appointmentNo = readOptionalInteger(request, false, "appointmentNo", "appointment_no");
            Integer programId = readOptionalInteger(
                    request, true, "programId", "program_id", "case_program_id");
            if (appointmentNo != null && !appointmentNo.equals(workspace.getAppointmentNo())) {
                return "rx_context_appointment_mismatch";
            }
            if (programId != null && !programId.toString().equals(workspace.getProgramId())) {
                return "rx_context_program_mismatch";
            }
        } catch (InvalidLaunchContextException e) {
            return e.errorCode;
        }
        return null;
    }

    private static LaunchContext resolveLaunchContext(
            HttpServletRequest request, int demographicNo, String providerNo)
            throws InvalidLaunchContextException {
        Integer appointmentNo = readOptionalInteger(request, false, "appointmentNo", "appointment_no");
        Integer requestedProgramId = readOptionalInteger(
                request, true, "programId", "program_id", "case_program_id");
        Integer appointmentProgramId = null;

        if (appointmentNo != null) {
            Appointment appointment = SpringUtils.getBean(OscarAppointmentDao.class).find(appointmentNo.intValue());
            if (appointment == null || appointment.getDemographicNo() != demographicNo) {
                throw new InvalidLaunchContextException("rx_appointment_invalid");
            }
            if (appointment.getProgramId() > 0) {
                appointmentProgramId = appointment.getProgramId();
            }
        }

        if (requestedProgramId != null && requestedProgramId > 0) {
            ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
            if (!programDao.programExists(requestedProgramId)) {
                throw new InvalidLaunchContextException("rx_program_invalid");
            }
        }
        if (requestedProgramId != null
                && appointmentProgramId != null
                && !appointmentProgramId.equals(requestedProgramId)) {
            throw new InvalidLaunchContextException("rx_program_appointment_mismatch");
        }

        Integer effectiveProgramId = requestedProgramId != null ? requestedProgramId : appointmentProgramId;
        String programId = effectiveProgramId == null
                ? new EctProgram(request.getSession()).getProgram(providerNo)
                : effectiveProgramId.toString();
        return new LaunchContext(appointmentNo, programId);
    }

    private static Integer readOptionalInteger(
            HttpServletRequest request, boolean allowZero, String... names)
            throws InvalidLaunchContextException {
        Integer result = null;
        for (String name : names) {
            String[] values = request.getParameterValues(name);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                String raw = trimToNull(value);
                if (raw == null) {
                    continue;
                }
                try {
                    int parsed = Integer.parseInt(raw);
                    if (parsed < 0 || (!allowZero && parsed == 0)
                            || (result != null && result != parsed)) {
                        throw new InvalidLaunchContextException("rx_launch_context_invalid");
                    }
                    result = parsed;
                } catch (NumberFormatException e) {
                    throw new InvalidLaunchContextException("rx_launch_context_invalid");
                }
            }
        }
        return result;
    }

    private static ContextParameter readContext(HttpServletRequest request) {
        String[] parameterValues = request.getParameterValues(CONTEXT_PARAMETER);
        String parameterContext = parameterValues == null || parameterValues.length != 1
                ? null : trimToNull(parameterValues[0]);
        String headerContext = trimToNull(request.getHeader(CONTEXT_HEADER));
        boolean invalid = (parameterValues != null && (parameterValues.length != 1 || parameterContext == null))
                || (request.getHeader(CONTEXT_HEADER) != null && headerContext == null)
                || (parameterContext != null
                        && headerContext != null
                        && !parameterContext.equals(headerContext));
        return new ContextParameter(parameterContext != null ? parameterContext : headerContext, invalid);
    }

    private static boolean isEntryRequest(String route, HttpServletRequest request) {
        return ENTRY_ROUTES.contains(route) && "GET".equalsIgnoreCase(request.getMethod());
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
            String[] values = request.getParameterValues(name);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                String raw = trimToNull(value);
                int parsed;
                try {
                    parsed = Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                    return DemographicParameter.invalid();
                }
                if (parsed <= 0 || (result > 0 && result != parsed)) {
                    return DemographicParameter.invalid();
                }
                result = parsed;
            }
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

    @SuppressFBWarnings(
            value = "UNVALIDATED_REDIRECT",
            justification = "Location retains the current mapped Rx request path and adds an opaque context UUID")
    private static void redirectWithContext(
            HttpServletRequest request, HttpServletResponse response, String contextId) {
        StringBuilder location = new StringBuilder(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            location.append('?').append(query).append('&');
        } else {
            location.append('?');
        }
        location.append(CONTEXT_PARAMETER).append('=')
                .append(contextId);
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        // nosemgrep: java.servlets.security.audit.url-rewriting.url-rewriting,java.lang.security.audit.url-rewriting.url-rewriting -- Rx workspace UUID, not a login session ID.
        response.setHeader("Location", location.toString()); // NOSONAR -- same-origin request URI; not an open redirect.
    }

    @SuppressFBWarnings(
            value = "XSS_SERVLET",
            justification = "HTML and JSON bodies contain only constant application-defined error codes")
    private static void reject(
            HttpServletRequest request, HttpServletResponse response, String errorCode) throws IOException {
        logger.warn("Rejected prescription request {}: {}", request.getRequestURI(), errorCode);
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("X-Rx-Error", errorCode);
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

    @FunctionalInterface
    interface LaunchContextResolver {
        LaunchContext resolve(HttpServletRequest request, int demographicNo, String providerNo)
                throws InvalidLaunchContextException;
    }

    record LaunchContext(Integer appointmentNo, String programId) {}

    private record ContextParameter(String value, boolean invalid) {}

    static final class InvalidLaunchContextException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String errorCode;

        private InvalidLaunchContextException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }
    }
}
