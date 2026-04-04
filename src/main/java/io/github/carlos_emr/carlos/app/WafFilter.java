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
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Web Application Firewall (WAF) filter that inspects incoming requests for common attack patterns.
 *
 * <p>Provides defense-in-depth by detecting and blocking requests that match signatures for:
 * <ul>
 *   <li>SQL injection (OWASP CRS 942xxx equivalent)</li>
 *   <li>Cross-site scripting / XSS (OWASP CRS 941xxx equivalent)</li>
 *   <li>Path traversal (OWASP CRS 930xxx equivalent)</li>
 *   <li>Command injection (OWASP CRS 932xxx equivalent)</li>
 *   <li>HTTP header injection / CRLF (OWASP CRS 921xxx equivalent)</li>
 *   <li>Known vulnerability scanners and attack tools (OWASP CRS 913xxx equivalent)</li>
 *   <li>Request size and complexity limits (OWASP CRS 920xxx equivalent)</li>
 *   <li>Protocol enforcement — TRACE/TRACK methods (OWASP CRS 911xxx equivalent)</li>
 * </ul>
 *
 * <h3>Standards Compliance</h3>
 * <ul>
 *   <li><strong>NIST SP 800-53 Rev. 5</strong>: SC-7 (Boundary Protection), SI-3 (Malicious Code
 *       Protection), SI-4 (System Monitoring), SI-10 (Information Input Validation), CM-7 (Least
 *       Functionality), AU-3/AU-6 (Audit Records)</li>
 *   <li><strong>NIST SP 800-44 Rev. 2</strong>: Section 8.2 (Web Application Firewalls),
 *       Section 8.3 (Intrusion Detection)</li>
 *   <li><strong>OWASP CRS v4.x</strong>: Pattern signatures derived from Core Rule Set at
 *       Paranoia Level 1 (low false positives, broad attack coverage)</li>
 *   <li><strong>OWASP Top 10 (2021)</strong>: A01, A03, A04, A05, A07, A09 coverage</li>
 *   <li><strong>PIPEDA / HIPAA</strong>: PHI-safe logging — parameter values never logged</li>
 * </ul>
 *
 * <h3>Healthcare Awareness</h3>
 * <p>Clinical notes, prescriptions, and medical forms contain text that can trigger naive WAF
 * rules (e.g. "SELECT-ive serotonin reuptake inhibitor", "patient OR family history").
 * The filter supports configurable relaxed paths where only structural checks (request limits,
 * protocol enforcement) apply, and injection patterns are skipped for POST body parameters.
 * This is consistent with NIST SI-10 guidance for context-aware input validation.</p>
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li>{@code enforce} — blocks matching requests with HTTP 403 (default)</li>
 *   <li>{@code detect} — logs violations but allows the request to proceed (NIST SI-4
 *       monitoring mode, recommended for initial deployment)</li>
 * </ul>
 *
 * <h3>Activation</h3>
 * <p>This filter is controlled by the {@code WAF_ENABLED} property in {@code carlos.properties}.
 * When the property is absent or set to any value other than {@code true}, {@code yes}, or
 * {@code on}, the filter passes all requests through without inspection. This allows the WAF
 * to be deployed in web.xml but remain inactive until explicitly enabled.</p>
 *
 * <h3>Configuration</h3>
 * <p>Rules are loaded from {@code /WEB-INF/waf-rules.properties}. Individual modules can be
 * enabled/disabled, paths can be allowlisted or relaxed, and scanner signatures are configurable.
 * See the properties file for full NIST/OWASP mapping of each setting.</p>
 *
 * <p><strong>Important:</strong> WAF logs never include parameter values or request bodies to
 * prevent PHI from appearing in log files (NIST SP 800-53 AU-3, PIPEDA Principle 7). Only the
 * client IP, request URI, and matched rule category are logged.</p>
 *
 * @since 2026-04-04
 * @see GeoIpFilter
 */
public final class WafFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger("waf.filter");

    private static final String PROPERTIES_PATH = "/WEB-INF/waf-rules.properties";

    /** Master switch — when false, all requests pass through without inspection. */
    private boolean enabled = false;

    private boolean enforcing = true;

    private boolean sqliEnabled = true;
    private boolean xssEnabled = true;
    private boolean pathTraversalEnabled = true;
    private boolean commandInjectionEnabled = true;
    private boolean scannerDetectionEnabled = true;
    private boolean requestLimitsEnabled = true;
    private boolean protocolEnforcementEnabled = true;
    private boolean headerInjectionEnabled = true;

    private int maxUriLength = 2048;
    private int maxParameterCount = 100;
    private int maxParameterValueLength = 65536;

    private int hardenedMaxParameterCount = 10;
    private int hardenedMaxParameterValueLength = 1024;

    private Set<String> allowlistPaths = Set.of();
    private Set<String> relaxedPaths = Set.of();
    private Set<String> hardenedPaths = Set.of();
    private Set<String> scannerSignatures = Set.of();

    /*
     * SQL injection patterns — require multiple SQL keywords together to reduce false positives
     * on clinical text that may contain individual keywords like "SELECT" or "OR".
     */
    private static final List<Pattern> SQLI_PATTERNS = List.of(
            // UNION-based injection: UNION ... SELECT
            Pattern.compile("(?i)\\bunion\\b[^a-z]*\\bselect\\b"),
            // Tautology: OR/AND 1=1, OR 'a'='a', etc.
            Pattern.compile("(?i)\\b(or|and)\\b\\s+['\"]?\\w+['\"]?\\s*=\\s*['\"]?\\w+['\"]?\\s*(--|#|/\\*)"),
            // Stacked queries with dangerous keywords
            Pattern.compile("(?i);\\s*\\b(drop|delete|update|insert|alter|create|truncate)\\b"),
            // Time-based blind injection
            Pattern.compile("(?i)\\b(sleep|benchmark|waitfor\\s+delay|pg_sleep)\\s*\\("),
            // Comment-based injection: SQL keyword immediately followed by comment syntax
            Pattern.compile("(?i)\\b(select|union|drop|insert|delete|update)\\b\\s+.{0,40}(/\\*|--\\s|#)"),
            // INTO OUTFILE / DUMPFILE (data exfiltration)
            Pattern.compile("(?i)\\binto\\s+(out|dump)file\\b"),
            // LOAD_FILE function
            Pattern.compile("(?i)\\bload_file\\s*\\("),
            // Information schema access
            Pattern.compile("(?i)\\binformation_schema\\b")
    );

    /*
     * XSS patterns — detect script injection, event handlers, and dangerous URI schemes.
     */
    private static final List<Pattern> XSS_PATTERNS = List.of(
            Pattern.compile("(?i)<\\s*script\\b"),
            Pattern.compile("(?i)<\\s*/\\s*script\\b"),
            Pattern.compile("(?i)\\bon(error|load|click|mouseover|focus|blur|submit|change|input|keydown|keyup|keypress|mousedown|mouseup|dblclick|contextmenu|wheel|pointermove)\\s*="),
            Pattern.compile("(?i)javascript\\s*:"),
            Pattern.compile("(?i)vbscript\\s*:"),
            Pattern.compile("(?i)data\\s*:\\s*text/html"),
            Pattern.compile("(?i)<\\s*iframe\\b"),
            Pattern.compile("(?i)<\\s*object\\b"),
            Pattern.compile("(?i)<\\s*embed\\b"),
            Pattern.compile("(?i)<\\s*svg\\b[^>]*\\bon"),
            Pattern.compile("(?i)<\\s*math\\b[^>]*\\bon"),
            Pattern.compile("(?i)<\\s*img\\b[^>]*\\bon(error|load)\\s*="),
            Pattern.compile("(?i)expression\\s*\\("),
            Pattern.compile("(?i)\\beval\\s*\\(\\s*['\"`]")
    );

    /*
     * Path traversal patterns — detect directory traversal in both raw and encoded forms.
     */
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = List.of(
            Pattern.compile("\\.\\.[\\\\/]"),
            Pattern.compile("(?i)%2e%2e[/\\\\%]"),
            Pattern.compile("(?i)%252e%252e"),
            Pattern.compile("(?i)\\.\\.%2f"),
            Pattern.compile("(?i)%2e\\./"),
            Pattern.compile("(?i)/etc/(passwd|shadow|hosts|issue)"),
            Pattern.compile("(?i)\\\\windows\\\\system32"),
            Pattern.compile("(?i)/proc/self/")
    );

    /*
     * Command injection patterns — detect shell metacharacters and common command sequences.
     */
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)[;|`]\\s*(cat|ls|dir|whoami|id|uname|pwd|wget|curl|nc|ncat|bash|sh|cmd|powershell)\\b"),
            Pattern.compile("(?i)\\$\\((cat|ls|whoami|id|uname|pwd)\\)"),
            Pattern.compile("(?i)\\$\\{[^}]*(jndi|runtime|exec|process|class\\.for|getruntime)[^}]*\\}"),
            Pattern.compile("(?i)`[^`]*(cat|ls|whoami|id|uname|pwd)[^`]*`")
    );

    /*
     * CRLF / header injection patterns.
     */
    private static final List<Pattern> HEADER_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)%0[da]"),
            Pattern.compile("[\\r\\n]")
    );

    /** Blocked HTTP methods. */
    private static final Set<String> BLOCKED_METHODS = Set.of("TRACE", "TRACK");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        enabled = CarlosProperties.getInstance().isPropertyActive("WAF_ENABLED");
        if (!enabled) {
            logger.info("WAF: disabled (WAF_ENABLED is not set to true/yes/on in carlos.properties)");
            return;
        }

        Properties props = new Properties();
        try (InputStream is = filterConfig.getServletContext().getResourceAsStream(PROPERTIES_PATH)) {
            if (is != null) {
                props.load(is);
                logger.info("WAF: loaded configuration from {}", PROPERTIES_PATH);
            } else {
                logger.warn("WAF: {} not found, using defaults", PROPERTIES_PATH);
            }
        } catch (IOException e) {
            logger.warn("WAF: failed to load {}, using defaults", PROPERTIES_PATH, e);
        }

        enforcing = !"detect".equalsIgnoreCase(props.getProperty("waf.mode", "enforce"));

        sqliEnabled = getBool(props, "waf.module.sqli.enabled", true);
        xssEnabled = getBool(props, "waf.module.xss.enabled", true);
        pathTraversalEnabled = getBool(props, "waf.module.path-traversal.enabled", true);
        commandInjectionEnabled = getBool(props, "waf.module.command-injection.enabled", true);
        scannerDetectionEnabled = getBool(props, "waf.module.scanner-detection.enabled", true);
        requestLimitsEnabled = getBool(props, "waf.module.request-limits.enabled", true);
        protocolEnforcementEnabled = getBool(props, "waf.module.protocol-enforcement.enabled", true);
        headerInjectionEnabled = getBool(props, "waf.module.header-injection.enabled", true);

        maxUriLength = getInt(props, "waf.limits.max-uri-length", 2048);
        maxParameterCount = getInt(props, "waf.limits.max-parameter-count", 100);
        maxParameterValueLength = getInt(props, "waf.limits.max-parameter-value-length", 65536);

        hardenedMaxParameterCount = getInt(props, "waf.hardened.max-parameter-count", 10);
        hardenedMaxParameterValueLength = getInt(props, "waf.hardened.max-parameter-value-length", 1024);

        allowlistPaths = parseCsvPreserveCase(props.getProperty("waf.allowlist.paths", ""));
        relaxedPaths = parseCsvPreserveCase(props.getProperty("waf.relaxed.paths", ""));
        hardenedPaths = parseCsvPreserveCase(props.getProperty("waf.hardened.paths", ""));
        scannerSignatures = parseCsvLowerCase(props.getProperty("waf.scanners",
                "sqlmap,nikto,nmap,masscan,zgrab,nuclei,dirbuster,gobuster,wfuzz,hydra,burp,zap"));

        logger.info("WAF: mode={}, sqli={}, xss={}, path-traversal={}, cmd-injection={}, "
                        + "scanner={}, limits={}, protocol={}, header-injection={}, hardened-paths={}",
                enforcing ? "enforce" : "detect",
                sqliEnabled, xssEnabled, pathTraversalEnabled, commandInjectionEnabled,
                scannerDetectionEnabled, requestLimitsEnabled, protocolEnforcementEnabled,
                headerInjectionEnabled, hardenedPaths.size());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        // 1. Allowlisted paths skip all WAF checks
        if (isPathMatch(uri, allowlistPaths)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();

        // 2. Protocol enforcement — block TRACE/TRACK (always block, even in detect mode)
        if (protocolEnforcementEnabled && BLOCKED_METHODS.contains(method)) {
            handleViolation(response, clientIp, uri, "protocol", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 3. Request limits (use tighter limits for hardened paths)
        boolean hardened = isPathMatch(uri, hardenedPaths);
        if (requestLimitsEnabled) {
            int effectiveMaxParams = hardened ? hardenedMaxParameterCount : maxParameterCount;
            int effectiveMaxValueLen = hardened ? hardenedMaxParameterValueLength : maxParameterValueLength;
            String violation = checkRequestLimits(request, uri, effectiveMaxParams, effectiveMaxValueLen);
            if (violation != null) {
                if (handleViolation(response, clientIp, uri, violation,
                        uri.length() > maxUriLength ? 414 : HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)) {
                    return;
                }
            }
        }

        // 4. Scanner/bot detection via User-Agent
        if (scannerDetectionEnabled) {
            String ua = request.getHeader("User-Agent");
            if (isScanner(ua)) {
                if (handleViolation(response, clientIp, uri, "scanner", HttpServletResponse.SC_FORBIDDEN)) {
                    return;
                }
            }
        }

        // 5. Header injection (CRLF) — check all header values
        if (headerInjectionEnabled && checkHeaderInjection(request)) {
            if (handleViolation(response, clientIp, uri, "header-injection", HttpServletResponse.SC_FORBIDDEN)) {
                return;
            }
        }

        // 6. URI-level path traversal check (always applies, even on relaxed paths)
        if (pathTraversalEnabled) {
            String decodedUri = decodeValue(uri);
            if (matchesAny(decodedUri, PATH_TRAVERSAL_PATTERNS)) {
                if (handleViolation(response, clientIp, uri, "path-traversal", HttpServletResponse.SC_FORBIDDEN)) {
                    return;
                }
            }
        }

        // 7. Parameter inspection (skip injection checks on relaxed paths for POST)
        boolean relaxed = isPathMatch(uri, relaxedPaths) && "POST".equals(method);
        if (!relaxed) {
            String paramViolation = checkParameters(request);
            if (paramViolation != null) {
                if (handleViolation(response, clientIp, uri, paramViolation, HttpServletResponse.SC_FORBIDDEN)) {
                    return;
                }
            }
        } else {
            // Even on relaxed paths, check query string parameters (GET params in a POST URL)
            String queryString = request.getQueryString();
            if (queryString != null) {
                String decoded = decodeValue(queryString);
                String qsViolation = checkValue(decoded);
                if (qsViolation != null) {
                    if (handleViolation(response, clientIp, uri, qsViolation + " (query-string)",
                            HttpServletResponse.SC_FORBIDDEN)) {
                        return;
                    }
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        logger.info("WAF: filter destroyed");
    }

    /**
     * Checks all request parameters against injection patterns.
     * Parameter size limits are enforced by {@link #checkRequestLimits} which runs first.
     *
     * @return the violation category name, or {@code null} if clean
     */
    private String checkParameters(HttpServletRequest request) {
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String[] values = request.getParameterValues(name);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                String decoded = decodeValue(value);
                String violation = checkValue(decoded);
                if (violation != null) {
                    return violation;
                }
            }
        }
        return null;
    }

    /**
     * Checks a single decoded value against all enabled injection pattern sets.
     *
     * @return the violation category, or {@code null} if clean
     */
    private String checkValue(String decoded) {
        if (sqliEnabled && matchesAny(decoded, SQLI_PATTERNS)) {
            return "sqli";
        }
        if (xssEnabled && matchesAny(decoded, XSS_PATTERNS)) {
            return "xss";
        }
        if (pathTraversalEnabled && matchesAny(decoded, PATH_TRAVERSAL_PATTERNS)) {
            return "path-traversal";
        }
        if (commandInjectionEnabled && matchesAny(decoded, COMMAND_INJECTION_PATTERNS)) {
            return "command-injection";
        }
        if (headerInjectionEnabled && matchesAny(decoded, HEADER_INJECTION_PATTERNS)) {
            return "header-injection";
        }
        return null;
    }

    /**
     * Checks request limits: URI length and parameter count.
     *
     * @param effectiveMaxParams the maximum parameter count for this request's path
     * @param effectiveMaxValueLen the maximum parameter value length for this request's path
     */
    private String checkRequestLimits(HttpServletRequest request, String uri,
                                      int effectiveMaxParams, int effectiveMaxValueLen) {
        if (uri.length() > maxUriLength) {
            return "uri-too-long";
        }
        int paramCount = 0;
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            paramCount++;
            if (paramCount > effectiveMaxParams) {
                return "too-many-params";
            }
            // Check parameter value lengths as part of request limits (hardened paths)
            String[] values = request.getParameterValues(name);
            if (values != null) {
                for (String value : values) {
                    if (value != null && value.length() > effectiveMaxValueLen) {
                        return "param-size-exceeded";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if the User-Agent matches a known scanner/attack tool signature.
     */
    private boolean isScanner(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        String lowerUa = userAgent.toLowerCase(Locale.ROOT);
        for (String sig : scannerSignatures) {
            if (lowerUa.contains(sig)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks request headers for CRLF injection sequences.
     */
    private boolean checkHeaderInjection(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            if (value != null && matchesAny(value, HEADER_INJECTION_PATTERNS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a WAF violation by logging and optionally blocking the request.
     * Never logs parameter values to prevent PHI exposure.
     *
     * @return {@code true} if the request was blocked (enforce mode), {@code false} if it
     *         should continue through the filter chain (detect mode)
     */
    private boolean handleViolation(HttpServletResponse response, String clientIp, String uri,
                                    String rule, int statusCode) throws IOException {
        if (enforcing) {
            logger.warn("WAF BLOCK: ip={} uri={} rule={}", clientIp, sanitizeLogValue(uri), rule);
            response.sendError(statusCode);
            return true;
        } else {
            logger.info("WAF DETECT: ip={} uri={} rule={}", clientIp, sanitizeLogValue(uri), rule);
            return false;
        }
    }

    /**
     * URL-decodes a value, handling double-encoding. Returns the original value if decoding fails.
     */
    private static String decodeValue(String value) {
        try {
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
            // Check for double encoding
            if (decoded.contains("%")) {
                decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static boolean matchesAny(String input, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPathMatch(String uri, Set<String> paths) {
        for (String path : paths) {
            if (uri.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sanitizes a value for safe logging — truncates long values and strips control characters.
     */
    private static String sanitizeLogValue(String value) {
        if (value == null) {
            return "null";
        }
        String safe = value.replaceAll("[\\r\\n\\t]", "_");
        return safe.length() > 200 ? safe.substring(0, 200) + "..." : safe;
    }

    private static Set<String> parseCsvPreserveCase(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> parseCsvLowerCase(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean getBool(Properties props, String key, boolean defaultValue) {
        String val = props.getProperty(key);
        return val != null ? Boolean.parseBoolean(val) : defaultValue;
    }

    private static int getInt(Properties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return defaultValue;
    }
}
