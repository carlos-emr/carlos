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
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.CarlosProperties;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Application-layer Web Application Firewall (WAF) providing defense-in-depth for CARLOS EMR.
 *
 * <p>Inspects incoming HTTP requests for common attack patterns and either blocks (enforce mode)
 * or logs (detect mode) violations. All configuration is read from {@code carlos.properties}
 * using {@code WAF_*} property keys.</p>
 *
 * <h3>Detection Modules</h3>
 * <table border="1">
 *   <tr><th>Module</th><th>OWASP CRS</th><th>Property</th></tr>
 *   <tr><td>SQL Injection</td><td>942xxx</td><td>WAF_SQLI_ENABLED</td></tr>
 *   <tr><td>Cross-Site Scripting</td><td>941xxx</td><td>WAF_XSS_ENABLED</td></tr>
 *   <tr><td>Path Traversal</td><td>930xxx</td><td>WAF_PATH_TRAVERSAL_ENABLED</td></tr>
 *   <tr><td>Command Injection</td><td>932xxx</td><td>WAF_CMD_INJECTION_ENABLED</td></tr>
 *   <tr><td>Header Injection (CRLF)</td><td>921xxx</td><td>WAF_HEADER_INJECTION_ENABLED</td></tr>
 *   <tr><td>Scanner Detection</td><td>913xxx</td><td>WAF_SCANNER_DETECTION_ENABLED</td></tr>
 *   <tr><td>Request Limits</td><td>920xxx</td><td>WAF_REQUEST_LIMITS_ENABLED</td></tr>
 *   <tr><td>Protocol Enforcement</td><td>911xxx</td><td>WAF_PROTOCOL_ENFORCEMENT_ENABLED</td></tr>
 * </table>
 *
 * <h3>Three-Tier Path Model</h3>
 * <ul>
 *   <li><strong>Allowlist paths</strong>: Skip ALL WAF checks (static assets)</li>
 *   <li><strong>Relaxed paths</strong>: Skip injection checks on POST body params;
 *       URI and query string still fully checked (clinical content endpoints)</li>
 *   <li><strong>Hardened paths</strong>: Tighter request limits (WAF_MAX_PARAM_COUNT_HARDENED,
 *       WAF_MAX_PARAM_VALUE_LENGTH_HARDENED) — login, admin, export endpoints</li>
 * </ul>
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><strong>detect</strong> (default): Logs violations but does not block requests</li>
 *   <li><strong>enforce</strong>: Logs violations and returns HTTP 400 Bad Request</li>
 * </ul>
 *
 * <h3>PHI Safety</h3>
 * <p>Log entries contain only: remote IP, request URI, and rule category. Parameter names
 * and values are NEVER logged to protect Patient Health Information.</p>
 *
 * <h3>Double-Encoding Detection</h3>
 * <p>Parameter values are URL-decoded up to twice if a {@code %} character remains after
 * the first decode, catching double-encoded attack vectors (e.g., {@code %252e%252e%252f}).</p>
 *
 * <p>Must be mapped <strong>before CSRFGuard and before Struts</strong> in the filter chain.</p>
 *
 * @see <a href="https://owasp.org/www-project-modsecurity-core-rule-set/">OWASP CRS</a>
 * @since 2026-04-05
 */
public class WafFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(WafFilter.class);
    private static final Logger WAF_LOGGER = LoggerFactory.getLogger("waf.filter");

    // -----------------------------------------------------------------------
    // SQL Injection patterns — OWASP CRS 942xxx
    // -----------------------------------------------------------------------

    /** UNION-based injection: UNION SELECT / UNION ALL SELECT */
    private static final Pattern SQLI_UNION_SELECT =
            Pattern.compile("(?i)\\bunion\\b[\\s\\S]{0,30}\\bselect\\b");

    /**
     * Tautology patterns: numeric ({@code OR 1=1}), single-quoted numeric ({@code AND '1'='1'}),
     * and string tautologies ({@code OR 'x'='x}, {@code OR 'admin'='admin'}).
     */
    private static final Pattern SQLI_TAUTOLOGY =
            Pattern.compile("(?i)\\b(or|and)\\b\\s*("
                    + "['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?"   // numeric: OR 1=1, AND '1'='1'
                    + "|['\"]\\w+['\"]\\s*=\\s*['\"]\\w+['\"]"       // string:  OR 'x'='x, OR 'admin'='admin'
                    + ")");

    /** Stacked queries: '; DROP TABLE, ; DELETE FROM, ; INSERT INTO, ; TRUNCATE */
    private static final Pattern SQLI_STACKED =
            Pattern.compile("(?i);\\s*(drop|truncate|delete\\s+from|insert\\s+into|update\\s+\\w)\\b");

    /** Time-based blind injection: SLEEP(), WAITFOR DELAY, BENCHMARK() */
    private static final Pattern SQLI_TIME_BASED =
            Pattern.compile("(?i)\\b(sleep|waitfor\\s+delay|benchmark)\\s*\\(");

    /** Direct file write: INTO OUTFILE / INTO DUMPFILE */
    private static final Pattern SQLI_INTO_OUTFILE =
            Pattern.compile("(?i)\\binto\\s+(outfile|dumpfile)\\b");

    /** File read function: LOAD_FILE() */
    private static final Pattern SQLI_LOAD_FILE =
            Pattern.compile("(?i)\\bload_file\\s*\\(");

    /** Schema enumeration: information_schema */
    private static final Pattern SQLI_INFO_SCHEMA =
            Pattern.compile("(?i)\\binformation_schema\\b");

    /** Comment-based termination: closing quote followed by SQL comment */
    private static final Pattern SQLI_COMMENT =
            Pattern.compile("(?i)'\\s*(--|/\\*)");

    private static final Pattern[] SQLI_PATTERNS = {
        SQLI_UNION_SELECT, SQLI_TAUTOLOGY, SQLI_STACKED, SQLI_TIME_BASED,
        SQLI_INTO_OUTFILE, SQLI_LOAD_FILE, SQLI_INFO_SCHEMA, SQLI_COMMENT
    };

    // -----------------------------------------------------------------------
    // XSS patterns — OWASP CRS 941xxx
    // -----------------------------------------------------------------------

    private static final Pattern XSS_SCRIPT_OPEN  = Pattern.compile("(?i)<\\s*script\\b");
    private static final Pattern XSS_SCRIPT_CLOSE = Pattern.compile("(?i)<\\s*/\\s*script\\b");
    /**
     * HTML event handler attributes. Possessive quantifier ({@code ++}) eliminates backtracking
     * to prevent ReDoS on user-controlled input (CodeQL CWE-1333). Pattern is limited to
     * known HTML event handler prefixes to avoid false positives on medical terms like
     * {@code onset=}, {@code ongoing=}, {@code only=} that begin with "on".
     */
    private static final Pattern XSS_EVENT_HANDLER = Pattern.compile(
            "(?i)\\bon(?:click|dbl|mouse|key|submit|change|input|focus|blur|load|unload"
            + "|error|scroll|resize|select|abort|contextmenu|drag|drop|touch"
            + "|pointer|wheel|animation|transition|message|copy|cut|paste|reset"
            + "|search|toggle|canplay|ended|pause|play|progress|stall|suspend"
            + "|timeupdate|volumechange|waiting|cuechange|seek|ratechange"
            + "|fullscreen|lostpointercapture|gotpointercapture|afterprint|beforeprint"
            + "|beforeunload|hashchange|pagehide|pageshow|popstate|storage)\\w*+\\s*=");
    private static final Pattern XSS_JAVASCRIPT_URI = Pattern.compile("(?i)javascript\\s*:");
    private static final Pattern XSS_VBSCRIPT_URI  = Pattern.compile("(?i)vbscript\\s*:");
    private static final Pattern XSS_DATA_HTML_URI = Pattern.compile("(?i)data\\s*:[^,]{0,30}text/html");
    private static final Pattern XSS_IFRAME        = Pattern.compile("(?i)<\\s*iframe\\b");
    private static final Pattern XSS_OBJECT        = Pattern.compile("(?i)<\\s*object\\b");
    private static final Pattern XSS_EMBED         = Pattern.compile("(?i)<\\s*embed\\b");
    private static final Pattern XSS_SVG           = Pattern.compile("(?i)<\\s*svg\\b");
    private static final Pattern XSS_MATH          = Pattern.compile("(?i)<\\s*math\\b");
    private static final Pattern XSS_EXPRESSION    = Pattern.compile("(?i)\\bexpression\\s*\\(");
    private static final Pattern XSS_EVAL          = Pattern.compile("(?i)\\beval\\s*\\(");
    private static final Pattern XSS_HTML_ENTITY   = Pattern.compile("&#[xX]?[0-9a-fA-F]{1,6};");

    private static final Pattern[] XSS_PATTERNS = {
        XSS_SCRIPT_OPEN, XSS_SCRIPT_CLOSE, XSS_EVENT_HANDLER, XSS_JAVASCRIPT_URI,
        XSS_VBSCRIPT_URI, XSS_DATA_HTML_URI, XSS_IFRAME, XSS_OBJECT, XSS_EMBED,
        XSS_SVG, XSS_MATH, XSS_EXPRESSION, XSS_EVAL, XSS_HTML_ENTITY
    };

    // -----------------------------------------------------------------------
    // Path Traversal patterns — OWASP CRS 930xxx
    // -----------------------------------------------------------------------

    /** Unix/Windows directory traversal: ../ or ..\ */
    private static final Pattern PT_DOTDOT_SLASH =
            Pattern.compile("\\.\\.[\\/\\\\]");

    /** URL-encoded traversal: %2e%2e or %2E%2E followed by / or % */
    private static final Pattern PT_ENCODED_DOTDOT =
            Pattern.compile("(?i)%2e%2e[\\/%5c]");

    /** Double URL-encoded traversal: %252e%252e */
    private static final Pattern PT_DOUBLE_ENCODED =
            Pattern.compile("(?i)%252e%252e");

    /** Sensitive Linux files */
    private static final Pattern PT_ETC_PASSWD =
            Pattern.compile("(?i)/etc/(passwd|shadow|hosts|group|sudoers)");

    /** Linux proc filesystem */
    private static final Pattern PT_PROC_SELF =
            Pattern.compile("(?i)/proc/self");

    /** Windows system directory */
    private static final Pattern PT_WINDOWS_SYSTEM32 =
            Pattern.compile("(?i)windows[/\\\\]+system32");

    /** Overlong UTF-8 slash encoding */
    private static final Pattern PT_OVERLONG_UTF8 =
            Pattern.compile("(?i)(%c0%af|%c1%9c)");

    /** File protocol URI */
    private static final Pattern PT_FILE_PROTO =
            Pattern.compile("(?i)file://");

    private static final Pattern[] PATH_TRAVERSAL_PATTERNS = {
        PT_DOTDOT_SLASH, PT_ENCODED_DOTDOT, PT_DOUBLE_ENCODED, PT_ETC_PASSWD,
        PT_PROC_SELF, PT_WINDOWS_SYSTEM32, PT_OVERLONG_UTF8, PT_FILE_PROTO
    };

    // -----------------------------------------------------------------------
    // Command Injection patterns — OWASP CRS 932xxx
    // -----------------------------------------------------------------------

    /** Shell metacharacter followed by a dangerous command */
    private static final Pattern CMD_SHELL_COMMAND =
            Pattern.compile("(?i)[;&|]\\s*(?:id|whoami|uname|ls|dir|cat|type|echo|wget|curl|nc|netcat|bash|sh|cmd|powershell)\\b");

    /** Command substitution subshell: $( */
    private static final Pattern CMD_SUBSHELL =
            Pattern.compile("\\$\\(");

    /** JNDI / Log4Shell injection: ${jndi: */
    private static final Pattern CMD_JNDI =
            Pattern.compile("(?i)\\$\\{jndi:");

    /** Backtick command execution: `command` */
    private static final Pattern CMD_BACKTICK =
            Pattern.compile("`[^`\\r\\n]{1,200}`");

    private static final Pattern[] CMD_INJECTION_PATTERNS = {
        CMD_SHELL_COMMAND, CMD_SUBSHELL, CMD_JNDI, CMD_BACKTICK
    };

    // -----------------------------------------------------------------------
    // Header Injection / CRLF patterns — OWASP CRS 921xxx
    // -----------------------------------------------------------------------

    /** URL-encoded CR or LF */
    private static final Pattern CRLF_ENCODED =
            Pattern.compile("(?i)%0[da]");

    /** Literal CR or LF characters */
    private static final Pattern CRLF_LITERAL =
            Pattern.compile("[\\r\\n]");

    private static final Pattern[] CRLF_PATTERNS = {
        CRLF_ENCODED, CRLF_LITERAL
    };

    // -----------------------------------------------------------------------
    // Scanner User-Agent signatures — OWASP CRS 913xxx
    // -----------------------------------------------------------------------

    private static final Pattern SCANNER_UA = Pattern.compile(
        "(?i)(sqlmap|nikto|nmap|masscan|zgrab|nuclei|dirbuster|gobuster|wfuzz|hydra"
        + "|burpsuite|burp\\s*suite|zaproxy|zap\\s*proxy|w3af|arachni"
        + "|skipfish|vega\\b|grabber|fimap|commix)"
    );

    // -----------------------------------------------------------------------
    // Blocked HTTP methods — OWASP CRS 911xxx
    // -----------------------------------------------------------------------

    private static final Set<String> BLOCKED_METHODS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("TRACE", "TRACK")));

    // -----------------------------------------------------------------------
    // Path model
    // -----------------------------------------------------------------------

    /**
     * Allowlist path prefixes — ALL WAF checks are skipped for requests matching these.
     * Intended for static assets that cannot contain injection payloads.
     */
    private static final Set<String> ALLOWLIST_PREFIXES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "/images/", "/css/", "/js/", "/fonts/", "/favicon.ico", "/csrfguard")));

    /**
     * Relaxed path prefixes — injection pattern checks on POST body params are skipped.
     * URI and query string are still fully inspected. Used for clinical content endpoints
     * that handle free-text medical notes and would otherwise generate false positives.
     */
    private static final Set<String> RELAXED_PREFIXES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "/CaseManagementEntry.do", "/SaveNote.do",
                    "/oscarEncounter/", "/eform/", "/annotation/")));

    /**
     * Hardened path prefixes — tighter request limits apply (WAF_MAX_PARAM_COUNT_HARDENED
     * and WAF_MAX_PARAM_VALUE_LENGTH_HARDENED). Intended for sensitive endpoints.
     */
    private static final Set<String> HARDENED_PREFIXES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "/login.do", "/mfa/", "/lab/CMLlabUpload.do",
                    "/admin/MergeRecords.do")));

    // -----------------------------------------------------------------------
    // Instance configuration (loaded from CarlosProperties in init())
    // -----------------------------------------------------------------------

    /** Master WAF on/off switch. Default: {@code false}. */
    private boolean enabled;

    /** When {@code true}, violations result in HTTP 400; when {@code false}, violations are only logged. */
    private boolean enforceMode;

    private boolean sqliEnabled;
    private boolean xssEnabled;
    private boolean pathTraversalEnabled;
    private boolean cmdInjectionEnabled;
    private boolean headerInjectionEnabled;
    private boolean scannerDetectionEnabled;
    private boolean requestLimitsEnabled;
    private boolean protocolEnforcementEnabled;

    private int maxUriLength;
    private int maxParamCount;
    private int maxParamCountHardened;
    private int maxParamValueLength;
    private int maxParamValueLengthHardened;

    // -----------------------------------------------------------------------
    // Filter lifecycle
    // -----------------------------------------------------------------------

    /**
     * Initialises the WAF by reading all {@code WAF_*} properties from {@link CarlosProperties}.
     *
     * @param filterConfig the filter configuration (unused — all config from CarlosProperties)
     * @throws ServletException if filter initialisation fails
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        CarlosProperties props = CarlosProperties.getInstance();

        enabled                   = getPropBool(props, "WAF_ENABLED",                    false);
        enforceMode               = "enforce".equalsIgnoreCase(getPropString(props, "WAF_MODE", "detect"));

        sqliEnabled               = getPropBool(props, "WAF_SQLI_ENABLED",               true);
        xssEnabled                = getPropBool(props, "WAF_XSS_ENABLED",                true);
        pathTraversalEnabled      = getPropBool(props, "WAF_PATH_TRAVERSAL_ENABLED",      true);
        cmdInjectionEnabled       = getPropBool(props, "WAF_CMD_INJECTION_ENABLED",       true);
        headerInjectionEnabled    = getPropBool(props, "WAF_HEADER_INJECTION_ENABLED",    true);
        scannerDetectionEnabled   = getPropBool(props, "WAF_SCANNER_DETECTION_ENABLED",   true);
        requestLimitsEnabled      = getPropBool(props, "WAF_REQUEST_LIMITS_ENABLED",      true);
        protocolEnforcementEnabled = getPropBool(props, "WAF_PROTOCOL_ENFORCEMENT_ENABLED", true);

        maxUriLength              = getPropInt(props, "WAF_MAX_URI_LENGTH",              2048);
        maxParamCount             = getPropInt(props, "WAF_MAX_PARAM_COUNT",             100);
        maxParamCountHardened     = getPropInt(props, "WAF_MAX_PARAM_COUNT_HARDENED",    10);
        maxParamValueLength       = getPropInt(props, "WAF_MAX_PARAM_VALUE_LENGTH",      65536);
        maxParamValueLengthHardened = getPropInt(props, "WAF_MAX_PARAM_VALUE_LENGTH_HARDENED", 1024);

        if (enabled) {
            LOGGER.info("WafFilter initialised in {} mode", enforceMode ? "ENFORCE" : "DETECT");
        } else {
            LOGGER.info("WafFilter is DISABLED (set WAF_ENABLED=true in carlos.properties to activate)");
        }
    }

    /**
     * Inspects the incoming request and either blocks it (enforce mode) or logs violations
     * (detect mode) before passing to the next filter in the chain.
     *
     * @param request  the incoming servlet request
     * @param response the outgoing servlet response
     * @param chain    the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Derive the path relative to the context root for path-model matching.
        // Normalize requestUri to empty string on the (spec-prohibited but defensive) null case.
        String contextPath = httpReq.getContextPath();
        String requestUri  = httpReq.getRequestURI();
        if (requestUri == null) {
            requestUri = "";
        }
        String relativePath = (contextPath != null && requestUri.startsWith(contextPath))
                ? requestUri.substring(contextPath.length())
                : requestUri;

        // --- Master switch: if WAF is disabled, skip everything ---
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        // --- Protocol enforcement: block TRACE / TRACK regardless of path ---
        // Runs BEFORE the allowlist so that TRACE to /images/, /csrfguard, etc. is still blocked.
        if (protocolEnforcementEnabled) {
            String method = httpReq.getMethod();
            if (method != null && BLOCKED_METHODS.contains(method.toUpperCase())) {
                if (block(httpReq, httpResp, "protocol", method)) {
                    return;
                }
            }
        }

        // --- Allowlist check: skip content-inspection checks for static assets ---
        if (isAllowlistedPath(relativePath)) {
            chain.doFilter(request, response);
            return;
        }

        boolean relaxed  = isRelaxedPath(relativePath);
        boolean hardened = isHardenedPath(relativePath);

        // --- URI + query-string length check ---
        // getRequestURI() excludes the query string per Servlet spec; combine both to get the
        // full request target length and prevent bypass via a very long query string.
        String queryString = httpReq.getQueryString();
        String fullRequestTarget = requestUri + (queryString != null ? "?" + queryString : "");
        if (requestLimitsEnabled && fullRequestTarget.length() > maxUriLength) {
            if (block(httpReq, httpResp, "req_limits", "uri_too_long")) {
                return;
            }
        }

        // --- Scanner User-Agent detection ---
        if (scannerDetectionEnabled) {
            String ua = httpReq.getHeader("User-Agent");
            if (ua != null && SCANNER_UA.matcher(ua).find()) {
                if (block(httpReq, httpResp, "scanner", "ua_match")) {
                    return;
                }
            }
        }

        // --- Injection checks on the URI itself ---
        if (!relativePath.isEmpty()) {
            String decodedUri = decode(relativePath);
            if (checkInjectionPatterns(httpReq, httpResp, decodedUri, "uri")) {
                return;
            }
        }

        // --- Injection checks on the raw query string ---
        if (queryString != null) {
            String decodedQs = decode(queryString);
            if (checkInjectionPatterns(httpReq, httpResp, decodedQs, "query")) {
                return;
            }
        }

        // --- Parameter-level checks ---
        boolean isPost = "POST".equalsIgnoreCase(httpReq.getMethod());
        Set<String> queryStringParamNames = parseQueryStringParamNames(queryString);

        Enumeration<String> paramNames = httpReq.getParameterNames();
        if (paramNames == null) {
            chain.doFilter(request, response);
            return;
        }
        int paramCount = 0;
        int paramLimit = hardened ? maxParamCountHardened : maxParamCount;
        int valueLimit = hardened ? maxParamValueLengthHardened : maxParamValueLength;

        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            paramCount++;

            // --- Request limits: parameter count ---
            if (requestLimitsEnabled && paramCount > paramLimit) {
                if (block(httpReq, httpResp, "req_limits", "too_many_params")) {
                    return;
                }
                break;
            }

            // Inspect ALL values for this parameter name. Using getParameterValues() instead of
            // getParameter() prevents bypass via repeated params (e.g. foo=safe&foo=<script>).
            String[] rawValues = httpReq.getParameterValues(paramName);
            if (rawValues == null) {
                continue;
            }

            for (String rawValue : rawValues) {
                if (rawValue == null) {
                    continue;
                }

                // --- Request limits: parameter value length ---
                if (requestLimitsEnabled && rawValue.length() > valueLimit) {
                    if (block(httpReq, httpResp, "req_limits", "param_value_too_long")) {
                        return;
                    }
                }

                // On relaxed paths for POST requests, skip injection checks on body params.
                // Query string params (identifiable from the raw query string) are still checked.
                if (relaxed && isPost && !queryStringParamNames.contains(paramName)) {
                    continue;
                }

                // --- Injection checks on the parameter value ---
                String decodedValue = decode(rawValue);

                if (headerInjectionEnabled) {
                    for (Pattern p : CRLF_PATTERNS) {
                        if (p.matcher(decodedValue).find()) {
                            if (block(httpReq, httpResp, "crlf", "header_injection")) {
                                return;
                            }
                            break;
                        }
                    }
                }

                if (checkInjectionPatterns(httpReq, httpResp, decodedValue, "param")) {
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Releases any resources held by this filter. No-op for WafFilter as all configuration
     * is read-only after {@link #init(FilterConfig)}.
     *
     * @since 2026-04-05
     */
    @Override
    public void destroy() {
        // nothing to release
    }

    // -----------------------------------------------------------------------
    // Pattern-checking helpers
    // -----------------------------------------------------------------------

    /**
     * Runs all enabled injection pattern checks (SQLi, XSS, path traversal, command injection)
     * against the supplied value. Returns {@code true} if a violation was detected AND the request
     * was blocked (enforce mode), meaning the caller should stop filter chain processing.
     *
     * @param req     the HTTP request (for logging and blocking)
     * @param resp    the HTTP response (for sending 400 in enforce mode)
     * @param value   the string value to inspect
     * @param context a label describing what is being inspected (e.g. "uri", "query", "param")
     * @return {@code true} if the request was blocked and processing should stop
     * @throws IOException if an I/O error occurs while sending the error response
     */
    private boolean checkInjectionPatterns(HttpServletRequest req, HttpServletResponse resp,
                                           String value, String context)
            throws IOException {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (sqliEnabled) {
            for (Pattern p : SQLI_PATTERNS) {
                if (p.matcher(value).find()) {
                    return block(req, resp, "sqli", context);
                }
            }
        }

        if (xssEnabled) {
            for (Pattern p : XSS_PATTERNS) {
                if (p.matcher(value).find()) {
                    return block(req, resp, "xss", context);
                }
            }
        }

        if (pathTraversalEnabled) {
            for (Pattern p : PATH_TRAVERSAL_PATTERNS) {
                if (p.matcher(value).find()) {
                    return block(req, resp, "path_traversal", context);
                }
            }
        }

        if (cmdInjectionEnabled) {
            for (Pattern p : CMD_INJECTION_PATTERNS) {
                if (p.matcher(value).find()) {
                    return block(req, resp, "cmd_injection", context);
                }
            }
        }

        return false;
    }

    /**
     * Handles a WAF violation: always logs the violation (PHI-safe), and optionally
     * sends HTTP 400 in enforce mode.
     *
     * @param req      the HTTP request
     * @param resp     the HTTP response
     * @param rule     the rule category that matched (e.g. "sqli", "xss")
     * @param detail   additional context label (e.g. "param", "uri")
     * @return {@code true} if the request was blocked (enforce mode), {@code false} if only logged
     * @throws IOException if an I/O error occurs while sending the error response
     */
    private boolean block(HttpServletRequest req, HttpServletResponse resp,
                          String rule, String detail) throws IOException {
        // PHI-safe: log IP + URI + rule category only — never log parameter values or body content
        WAF_LOGGER.warn("WAF [{}:{}] {} {}",
                rule, detail,
                req.getRemoteAddr(),
                Encode.forJava(req.getRequestURI()));

        if (enforceMode) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
            return true;
        }
        return false; // detect mode — log but continue
    }

    // -----------------------------------------------------------------------
    // Path model helpers
    // -----------------------------------------------------------------------

    private static boolean isAllowlistedPath(String path) {
        for (String prefix : ALLOWLIST_PREFIXES) {
            // For directory prefixes (ending with '/') use startsWith so any resource under
            // that directory is matched. For exact-path entries (e.g. '/favicon.ico',
            // '/csrfguard') require an exact match to avoid inadvertently allowlisting paths
            // that share the same prefix (e.g. '/csrfguardAdmin').
            if (prefix.endsWith("/")) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else {
                if (path.equals(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRelaxedPath(String path) {
        for (String prefix : RELAXED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHardenedPath(String path) {
        for (String prefix : HARDENED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    /**
     * URL-decodes a value up to twice. If a {@code %} character remains after the first
     * decode pass, a second decode is attempted to catch double-encoded attack vectors
     * (e.g., {@code %252e%252e%252f} → {@code %2e%2e%2f} → {@code ../}).
     *
     * @param value the raw value to decode
     * @return the decoded string, or the original value if decoding fails
     */
    static String decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (!decoded.contains("%")) {
                return decoded;
            }
            // Second pass to catch double-encoded payloads (e.g. %252e%252e%252f → %2e%2e%2f → ../).
            // If the second decode fails (e.g. the remaining % is not a valid escape), preserve the
            // first-decoded result — never fall back to the original, which would hide the
            // partially-exposed payload.
            try {
                return URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                return decoded;
            }
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    /**
     * Parses parameter names from a raw query string.
     * Used to distinguish query-string params from POST body params on relaxed paths.
     *
     * @param queryString the raw query string (may be {@code null})
     * @return a set of parameter names present in the query string (decoded)
     */
    static Set<String> parseQueryStringParamNames(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> names = new HashSet<>();
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String name = (eq >= 0) ? pair.substring(0, eq) : pair;
            if (!name.isEmpty()) {
                names.add(decode(name));
            }
        }
        return names;
    }

    /**
     * Reads a boolean property from {@link CarlosProperties}. Returns {@code defaultValue}
     * when the property is absent or blank.
     *
     * @param props        the CarlosProperties instance
     * @param key          the property key
     * @param defaultValue the fallback value
     * @return the parsed boolean value
     */
    private static boolean getPropBool(CarlosProperties props, String key, boolean defaultValue) {
        String val = props.getProperty(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        String trimmed = val.trim().toLowerCase();
        return "true".equals(trimmed) || "yes".equals(trimmed) || "on".equals(trimmed);
    }

    /**
     * Reads an integer property from {@link CarlosProperties}. Returns {@code defaultValue}
     * when the property is absent, blank, or not a valid integer.
     *
     * @param props        the CarlosProperties instance
     * @param key          the property key
     * @param defaultValue the fallback value
     * @return the parsed integer value
     */
    private static int getPropInt(CarlosProperties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("WafFilter: invalid integer for property {}: {} — using default {}",
                    key, val, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Reads a string property from {@link CarlosProperties}. Returns {@code defaultValue}
     * when the property is absent or blank.
     *
     * @param props        the CarlosProperties instance
     * @param key          the property key
     * @param defaultValue the fallback value
     * @return the property value, or {@code defaultValue}
     */
    private static String getPropString(CarlosProperties props, String key, String defaultValue) {
        String val = props.getProperty(key);
        return (val == null || val.trim().isEmpty()) ? defaultValue : val.trim();
    }
}
