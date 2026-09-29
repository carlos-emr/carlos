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
import io.github.carlos_emr.carlos.utility.LogSafe;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Per-IP rate-limiting filter for CARLOS EMR.
 *
 * <p>Implements a fixed-window counter algorithm to limit the number of HTTP requests
 * per client IP address within a configurable time window. Supports per-path rate
 * tiers, detect/enforce mode, IP exemptions, and PHI-safe audit logging.</p>
 *
 * <p><strong>Standards Alignment:</strong></p>
 * <ul>
 *   <li>NIST SP 800-53 SC-5 — Denial-of-Service Protection</li>
 *   <li>NIST SP 800-53 SI-4 — System Monitoring (detect mode)</li>
 *   <li>OWASP Top 10 A07 — Identification and Authentication Failures (brute force)</li>
 *   <li>OWASP CRS 912xxx — DoS Protection rules</li>
 *   <li>OWASP ASVS 4.0 §11.1.4 — Rate limiting on authentication endpoints</li>
 * </ul>
 *
 * <p><strong>Configuration</strong> (via {@code carlos.properties}):</p>
 * <ul>
 *   <li>{@code WAF_RATE_LIMIT_ENABLED} — master toggle (default: false)</li>
 *   <li>{@code WAF_RATE_LIMIT_MODE} — {@code enforce} (block with 429) or {@code detect}
 *       (log only, default: detect)</li>
 *   <li>{@code WAF_RATE_LIMIT_DEFAULT_REQUESTS} — global requests per window (default: 100)</li>
 *   <li>{@code WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS} — global window duration (default: 60)</li>
 *   <li>{@code WAF_RATE_LIMIT_PATHS} — comma-separated path-specific tiers, format:
 *       {@code /login=10/60,/ws/=200/60} (prefix matching)</li>
 *   <li>{@code WAF_RATE_LIMIT_EXEMPT_IPS} — comma-separated IPs exempt from limiting
 *       (default: loopback addresses)</li>
 *   <li>{@code WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS} — stale counter eviction interval
 *       (default: 300)</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> Counter storage uses {@link ConcurrentHashMap} for
 * lock-free reads. Each {@link FixedWindowCounter} uses an {@link AtomicInteger} for the
 * request count and {@code synchronized} only on the rare window-reset path.</p>
 *
 * <p><strong>Filter Chain Position:</strong> Should be first in the WAF layer, after the
 * monitoring filter. If the application is behind a reverse proxy, ensure
 * {@code XforwardHeaderFilter} is mapped before this filter so rate limiting uses the
 * real client IP.</p>
 *
 * @since 2026-04-05
 */
public final class RateLimitFilter implements Filter {

    /**
     * Dedicated SLF4J logger category for WAF rate-limit events.
     * PHI-safe: logs IP, URI, and rule name only — never request parameter values.
     */
    private static final Logger logger = LoggerFactory.getLogger("waf.ratelimit");
    private static final int DEFAULT_MAX_COUNTERS = 50_000;

    // --- Configuration fields (effectively final after init()) ---

    /** Whether rate limiting is enabled (WAF_RATE_LIMIT_ENABLED). */
    private boolean enabled;

    /**
     * Whether the filter is in enforce mode ({@code true}) or detect mode ({@code false}).
     * In enforce mode, rate-limited requests receive a 429 response.
     * In detect mode, violations are logged but the request continues.
     */
    private boolean enforcing;

    /** Global default maximum requests per window (WAF_RATE_LIMIT_DEFAULT_REQUESTS). */
    private int defaultRequests;

    /** Global default window duration in seconds (WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS). */
    private int defaultWindowSeconds;

    /**
     * Per-path rate configurations. Key is the URL path prefix; value is the rate config.
     * Checked before the global default. More specific paths take precedence.
     */
    private Map<String, RateConfig> pathRates;

    /** Set of client IP addresses that are exempt from rate limiting. */
    private Set<String> exemptIps;

    /** Maximum retained per-client counter entries before least-recently-used eviction. */
    private int maxCounterEntries;

    /** Trusted reverse-proxy addresses used to decide whether X-Forwarded-For mismatch warnings are actionable. */
    private Set<String> trustedProxyIps = Collections.emptySet();
    private Set<XforwardHeaderFilter.CidrRange> trustedProxyCidrs = Collections.emptySet();

    // --- Runtime state ---

    /**
     * Counter storage. Key format:
     * <ul>
     *   <li>Global counter: {@code clientIp}</li>
     *   <li>Path-specific counter: {@code clientIp|pathKey}</li>
     * </ul>
     */
    private final ConcurrentHashMap<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();
    private final Set<String> forwardedAddressWarningIps = ConcurrentHashMap.newKeySet();

    /** Background thread for evicting stale counters to prevent unbounded memory growth. */
    private ScheduledExecutorService cleanupScheduler;

    /**
     * Clock supplier used to obtain the current time in milliseconds.
     * Defaults to {@link System#currentTimeMillis()}; may be replaced in tests for
     * deterministic timing control without wall-clock delays.
     */
    private LongSupplier clock = System::currentTimeMillis;
    private static final int MAX_EVICTION_SAMPLE_SIZE = 64;

    /**
     * Initialises the filter by loading all configuration from {@link CarlosProperties}.
     *
     * @param filterConfig the servlet container filter configuration (not used; all config
     *                     comes from {@code CarlosProperties})
     * @throws ServletException if initialisation fails
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        CarlosProperties props = CarlosProperties.getInstance();

        enabled = props.isPropertyActive("WAF_RATE_LIMIT_ENABLED");
        if (!enabled) {
            logger.info("Rate limit filter: disabled (WAF_RATE_LIMIT_ENABLED is not set to true/yes/on)");
            return;
        }

        String mode = props.getProperty("WAF_RATE_LIMIT_MODE");
        if (mode == null || mode.isEmpty()) {
            mode = "detect";
        }
        String normalizedMode = mode.trim();
        if ("enforce".equalsIgnoreCase(normalizedMode)) {
            enforcing = true;
        } else if ("detect".equalsIgnoreCase(normalizedMode)) {
            enforcing = false;
        } else {
            enforcing = false;
            logger.warn("Rate limit filter: unrecognized WAF_RATE_LIMIT_MODE '{}'; defaulting to detect mode",
                    normalizedMode);
        }

        defaultRequests = getIntProperty(props, "WAF_RATE_LIMIT_DEFAULT_REQUESTS", 100);
        defaultWindowSeconds = getIntProperty(props, "WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS", 60);
        maxCounterEntries = getIntProperty(props, "WAF_RATE_LIMIT_MAX_COUNTERS", DEFAULT_MAX_COUNTERS);

        String pathsConfig = props.getProperty("WAF_RATE_LIMIT_PATHS");
        pathRates = parsePathRates(pathsConfig != null ? pathsConfig : "");

        String exemptConfig = props.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS");
        // Both ::1 and 0:0:0:0:0:0:0:1 represent IPv6 loopback — both are included because
        // different JVMs/platforms may return either compressed or full form from getRemoteAddr().
        exemptIps = parseCsv(exemptConfig != null ? exemptConfig : "127.0.0.1,::1,0:0:0:0:0:0:0:1");
        String trustedIpConfig = props.getProperty(XforwardHeaderFilter.TRUSTED_PROXY_IPS_PROPERTY);
        String trustedCidrConfig = props.getProperty(XforwardHeaderFilter.TRUSTED_PROXY_CIDRS_PROPERTY);
        trustedProxyIps = XforwardHeaderFilter.parseCsv(trustedIpConfig != null ? trustedIpConfig : "");
        trustedProxyCidrs = XforwardHeaderFilter.parseCidrs(trustedCidrConfig != null ? trustedCidrConfig : "");

        int cleanupInterval = getIntProperty(props, "WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS", 300);
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "waf-ratelimit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(
                this::evictStaleCounters, cleanupInterval, cleanupInterval, TimeUnit.SECONDS);

        logger.info("Rate limit filter: mode={}, default={}/{}, paths={}, exempt-ips={}",
                enforcing ? "enforce" : "detect",
                defaultRequests, defaultWindowSeconds,
                pathRates.size(), exemptIps.size());
    }

    /**
     * Applies rate limiting to the incoming request.
     *
     * <p>Processing steps:</p>
     * <ol>
     *   <li>If disabled, pass through immediately.</li>
     *   <li>Extract client IP from {@code request.getRemoteAddr()}.</li>
     *   <li>If IP is in the exempt list, pass through.</li>
     *   <li>Increment the global counter first and, if a path-specific rule matches, increment
     *       that counter as well (defense-in-depth — both are always checked); if either limit
     *       is exceeded, log and (in enforce mode) send 429.</li>
     * </ol>
     *
     * <p>PHI-safe logging: only IP, URI, and rule name are logged — never parameter values.</p>
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

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = httpRequest.getRemoteAddr();
        warnIfForwardedAddressWasNotApplied(httpRequest, clientIp);

        if (exemptIps.contains(clientIp)) {
            chain.doFilter(request, response);
            return;
        }

        String requestUri = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        // Strip context path for path matching
        String path = (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath))
                ? requestUri.substring(contextPath.length())
                : requestUri;

        // Check global counter
        FixedWindowCounter globalCounter =
                counterFor(clientIp, defaultRequests, defaultWindowSeconds * 1000L, "global");

        boolean globalAllowed = globalCounter.tryAcquire();

        if (!globalAllowed) {
            long retryAfterSeconds = globalCounter.retryAfterSeconds();
            if (enforcing) {
                logger.warn("RATE BLOCK: ip={} uri={} rule=rate-limit-global",
                        LogSafe.sanitize(clientIp), LogSafe.sanitizeUri(requestUri));
                if (!httpResponse.isCommitted()) {
                    httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                    httpResponse.sendError(429, "Too Many Requests");
                }
                return;
            } else {
                logger.warn("RATE DETECT: ip={} uri={} rule=rate-limit-global",
                        LogSafe.sanitize(clientIp), LogSafe.sanitizeUri(requestUri));
            }
        }

        // Check path-specific counter (defense-in-depth: count against both global and path)
        String matchedPath = findMatchingPath(path);
        if (matchedPath != null) {
            RateConfig pathConfig = pathRates.get(matchedPath);
            String counterKey = clientIp + "|" + matchedPath;
            FixedWindowCounter pathCounter = counterFor(
                    counterKey, pathConfig.requests, pathConfig.windowSeconds * 1000L, "path:" + matchedPath);

            boolean pathAllowed = pathCounter.tryAcquire();

            if (!pathAllowed) {
                long retryAfterSeconds = pathCounter.retryAfterSeconds();
                if (enforcing) {
                    logger.warn("RATE BLOCK: ip={} uri={} rule=rate-limit-path:{}",
                            LogSafe.sanitize(clientIp), LogSafe.sanitizeUri(requestUri),
                            LogSafe.sanitize(matchedPath));
                    if (!httpResponse.isCommitted()) {
                        httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                        httpResponse.sendError(429, "Too Many Requests");
                    }
                    return;
                } else {
                    logger.warn("RATE DETECT: ip={} uri={} rule=rate-limit-path:{}",
                            LogSafe.sanitize(clientIp), LogSafe.sanitizeUri(requestUri),
                            LogSafe.sanitize(matchedPath));
                }
            }
        }

        chain.doFilter(request, response);
    }

    private void warnIfForwardedAddressWasNotApplied(HttpServletRequest request, String clientIp) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return;
        }
        String firstForwardedIp = forwardedFor.split(",", 2)[0].trim();
        if (!firstForwardedIp.isEmpty() && !firstForwardedIp.equals(clientIp)) {
            boolean trustedProxy = XforwardHeaderFilter.isTrustedProxy(clientIp, trustedProxyIps, trustedProxyCidrs);
            if (trustedProxy && rememberForwardedAddressWarningIp(clientIp)) {
                logger.warn(
                        "RATE CONFIG: X-Forwarded-For present but rate limit is using remoteAddr; "
                                + "verify XforwardHeaderFilter ordering/trusted proxy configuration. remoteAddr={} xForwardedForFirst={}",
                        LogSafe.sanitize(clientIp),
                        LogSafe.sanitize(firstForwardedIp));
            } else if (!trustedProxy && rememberForwardedAddressWarningIp(clientIp)) {
                logger.debug(
                        "RATE CONFIG: untrusted remoteAddr supplied X-Forwarded-For; ignoring forwarded value. remoteAddr={} xForwardedForFirst={}",
                        LogSafe.sanitize(clientIp),
                        LogSafe.sanitize(firstForwardedIp));
            } else if (trustedProxy) {
                logger.debug(
                        "RATE CONFIG: repeated X-Forwarded-For mismatch suppressed for remoteAddr={} xForwardedForFirst={}",
                        LogSafe.sanitize(clientIp),
                        LogSafe.sanitize(firstForwardedIp));
            }
        }
    }

    private boolean rememberForwardedAddressWarningIp(String clientIp) {
        if (forwardedAddressWarningIps.contains(clientIp)) {
            return false;
        }
        if (forwardedAddressWarningIps.size() >= maxCounterEntries) {
            forwardedAddressWarningIps.clear();
            logger.debug(
                    "RATE CONFIG: forwarded-address warning suppression set reached {} entries; cleared remembered IPs",
                    maxCounterEntries);
        }
        return forwardedAddressWarningIps.add(clientIp);
    }

    /**
     * Shuts down the cleanup scheduler and releases resources.
     */
    @Override
    public void destroy() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
        forwardedAddressWarningIps.clear();
    }

    // --- Package-visible for testing ---

    /**
     * Overrides the clock supplier used for time measurements (for testing only).
     * Must be called before {@link #init(FilterConfig)} so that counters created during
     * the filter's lifetime use the injected clock.
     *
     * @param clock a supplier that returns the current time in milliseconds
     */
    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Returns a snapshot copy of the current counter map (for testing).
     * Returns a copy to avoid exposing the mutable internal map.
     *
     * @return snapshot copy of the counter map
     */
    Map<String, FixedWindowCounter> getCounters() {
        return new HashMap<>(counters);
    }

    /**
     * Returns the configured path rates map (for testing).
     *
     * @return unmodifiable view of the path rates map
     */
    Map<String, RateConfig> getPathRates() {
        return Collections.unmodifiableMap(pathRates);
    }

    /**
     * Returns the exempt IPs set (for testing).
     *
     * @return unmodifiable view of the exempt IPs set
     */
    Set<String> getExemptIps() {
        return Collections.unmodifiableSet(exemptIps);
    }

    /**
     * Returns whether the filter is in enforcing mode (for testing).
     *
     * @return {@code true} if enforcing, {@code false} if in detect mode
     */
    boolean isEnforcing() {
        return enforcing;
    }

    /**
     * Returns whether the filter is enabled (for testing).
     *
     * @return {@code true} if enabled
     */
    boolean isEnabled() {
        return enabled;
    }

    int forwardedAddressWarningIpCount() {
        return forwardedAddressWarningIps.size();
    }

    // --- Private helpers ---

    private FixedWindowCounter counterFor(String key, int requests, long windowMillis, String tier) {
        FixedWindowCounter existing = counters.get(key);
        if (existing != null) {
            return existing;
        }
        return createCounterForMissingKey(key, requests, windowMillis);
    }

    private synchronized FixedWindowCounter createCounterForMissingKey(String key, int requests, long windowMillis) {
        FixedWindowCounter existing = counters.get(key);
        if (existing != null) {
            return existing;
        }
        if (counters.size() >= maxCounterEntries) {
            evictStaleCounters();
        }
        if (counters.size() >= maxCounterEntries) {
            evictSampledLeastRecentlySeenCounter();
        }
        return counters.computeIfAbsent(key,
                k -> new FixedWindowCounter(requests, windowMillis, clock));
    }

    private void evictSampledLeastRecentlySeenCounter() {
        String evictKey = null;
        long oldestSeen = Long.MAX_VALUE;
        int sampled = 0;
        for (Map.Entry<String, FixedWindowCounter> entry : counters.entrySet()) {
            long lastSeen = entry.getValue().lastSeenMillis();
            if (lastSeen < oldestSeen) {
                oldestSeen = lastSeen;
                evictKey = entry.getKey();
            }
            sampled++;
            if (sampled >= MAX_EVICTION_SAMPLE_SIZE) {
                break;
            }
        }
        if (evictKey != null) {
            counters.remove(evictKey);
            logger.warn("Rate limit: counter map at max {} entries; evicted sampled least-recently-seen counter",
                    maxCounterEntries);
        }
    }

    /**
     * Finds the most specific path prefix in {@link #pathRates} that matches the given path.
     *
     * @param path the request path (without context path)
     * @return the matching path key, or {@code null} if no path-specific rate applies
     */
    private String findMatchingPath(String path) {
        // Path matching rules:
        //  - A pattern ending with "/" is a prefix match (e.g. "/mfa/" matches
        //    "/mfa/whatever").
        //  - A pattern NOT ending with "/" matches the exact path or a path
        //    that has the pattern followed by '/' or ';' (e.g. "/login"
        //    matches "/login", "/login/something", and "/login;jsessionid=..."
        //    but NOT "/loginfailed" or "/loginResource/foo"). The ';' boundary
        //    closes the path-parameter bypass where attackers append
        //    ";jsessionid=…" or other matrix params to dodge the rate limit.
        //  - When multiple patterns match, prefer the longest (most specific).
        String bestMatch = null;
        for (String prefix : pathRates.keySet()) {
            boolean matches;
            if (prefix.endsWith("/")) {
                matches = path.startsWith(prefix);
            } else if (path.equals(prefix)) {
                matches = true;
            } else if (path.startsWith(prefix)) {
                char nextChar = path.charAt(prefix.length());
                matches = nextChar == '/' || nextChar == ';';
            } else {
                matches = false;
            }
            if (matches && (bestMatch == null || prefix.length() > bestMatch.length())) {
                bestMatch = prefix;
            }
        }
        return bestMatch;
    }

    /**
     * Parses a comma-separated list of path rate configurations.
     *
     * <p>Format: {@code /path=requests/windowSeconds} (e.g. {@code /login=10/60}).</p>
     * Invalid entries are skipped with a warning log.
     *
     * @param config the raw configuration string from {@code carlos.properties}
     * @return an unmodifiable map from path prefix to {@link RateConfig}
     */
    Map<String, RateConfig> parsePathRates(String config) {
        Map<String, RateConfig> result = new HashMap<>();
        if (config == null || config.trim().isEmpty()) {
            return Collections.unmodifiableMap(result);
        }
        for (String entry : config.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eqIdx = entry.indexOf('=');
            if (eqIdx <= 0 || eqIdx == entry.length() - 1) {
                logger.warn("Rate limit: skipping invalid path config entry (missing '='): {}", entry);
                continue;
            }
            String pathPrefix = entry.substring(0, eqIdx).trim();
            // No warning needed for non-"/"-terminated prefixes: findMatchingPath
            // applies a boundary-aware match (exact, or followed by '/' or
            // ';'), so '/login' will NOT match '/loginRedirect' or
            // '/login-recovery'. The historical warning that fired here
            // pre-dated the boundary fix and produced noisy startup logs for
            // valid configs.
            String rateStr = entry.substring(eqIdx + 1).trim();
            int slashIdx = rateStr.indexOf('/');
            if (slashIdx <= 0 || slashIdx == rateStr.length() - 1) {
                logger.warn("Rate limit: skipping invalid rate format for path '{}': {}", pathPrefix, rateStr);
                continue;
            }
            try {
                int requests = Integer.parseInt(rateStr.substring(0, slashIdx).trim());
                int windowSecs = Integer.parseInt(rateStr.substring(slashIdx + 1).trim());
                if (requests <= 0 || windowSecs <= 0) {
                    logger.warn("Rate limit: skipping non-positive rate for path '{}': {}", pathPrefix, rateStr);
                    continue;
                }
                result.put(pathPrefix, new RateConfig(requests, windowSecs));
            } catch (NumberFormatException e) {
                logger.warn("Rate limit: skipping non-numeric rate for path '{}': {}", pathPrefix, rateStr, e);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Parses a comma-separated string into an unmodifiable set of trimmed, non-empty strings.
     *
     * @param csv the comma-separated input
     * @return an unmodifiable set of values
     */
    Set<String> parseCsv(String csv) {
        Set<String> result = new HashSet<>();
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.unmodifiableSet(result);
        }
        for (String item : csv.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Reads an integer property from {@link CarlosProperties} with a fallback default.
     *
     * @param props        the properties instance
     * @param key          the property key
     * @param defaultValue the fallback value if the property is absent or not parseable
     * @return the parsed integer value, or {@code defaultValue} on error
     */
    private int getIntProperty(CarlosProperties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                logger.warn("Rate limit: property '{}' must be positive; using default {}", key, defaultValue);
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            logger.warn("Rate limit: property '{}' is not a valid integer '{}'; using default {}", key, value, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Evicts stale counter entries from the counter map.
     *
     * <p>An entry is considered stale when its window expired more than one full window
     * duration ago. This prevents unbounded memory growth from transient client IPs.</p>
     */
    void evictStaleCounters() {
        long now = clock.getAsLong();
        int sizeBefore = counters.size();
        counters.entrySet().removeIf(entry -> entry.getValue().isStale(now));
        int removed = sizeBefore - counters.size();
        if (removed > 0) {
            logger.debug("Rate limit: evicted {} stale counter(s)", removed);
        }
    }

    // --- Inner classes ---

    /**
     * Immutable configuration for a single rate tier (requests per window).
     */
    static final class RateConfig {
        final int requests;
        final int windowSeconds;

        /**
         * Creates a new rate configuration.
         *
         * @param requests      maximum allowed requests within the window
         * @param windowSeconds duration of the window in seconds
         */
        RateConfig(int requests, int windowSeconds) {
            this.requests = requests;
            this.windowSeconds = windowSeconds;
        }
    }

    /**
     * Fixed-window request counter for a single (IP, rate-tier) combination.
     *
     * <p>Thread safety:</p>
     * <ul>
     *   <li>{@link AtomicInteger} for the request count (lock-free increment)</li>
     *   <li>{@code volatile} on {@code windowStart} for cross-thread visibility</li>
     *   <li>{@code synchronized} only on the window-reset path (rare, amortised)</li>
     * </ul>
     *
     * <p>Algorithm: Fixed window. When {@code System.currentTimeMillis() - windowStart >= windowMillis},
     * the window has expired and the counter resets to zero before counting the new request.</p>
     */
    static final class FixedWindowCounter {

        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart;
        private volatile long lastSeenMillis;
        private final int maxRequests;
        private final long windowMillis;
        private final LongSupplier clock;

        /**
         * Creates a new counter using the system wall clock.
         *
         * @param maxRequests  maximum requests allowed per window
         * @param windowMillis window duration in milliseconds
         */
        FixedWindowCounter(int maxRequests, long windowMillis) {
            this(maxRequests, windowMillis, System::currentTimeMillis);
        }

        /**
         * Creates a new counter with a custom clock supplier (for testing).
         *
         * @param maxRequests  maximum requests allowed per window
         * @param windowMillis window duration in milliseconds
         * @param clock        supplier of current time in milliseconds
         */
        FixedWindowCounter(int maxRequests, long windowMillis, LongSupplier clock) {
            this.maxRequests = maxRequests;
            this.windowMillis = windowMillis;
            this.clock = clock;
            this.windowStart = clock.getAsLong();
            this.lastSeenMillis = this.windowStart;
        }

        /**
         * Attempts to consume one request token.
         *
         * @return {@code true} if within the rate limit; {@code false} if the limit is exceeded
         */
        boolean tryAcquire() {
            long now = clock.getAsLong();
            lastSeenMillis = now;
            if (now - windowStart >= windowMillis) {
                // Window has expired — reset under lock to avoid multiple simultaneous resets
                synchronized (this) {
                    if (now - windowStart >= windowMillis) {
                        count.set(0);
                        windowStart = now;
                    }
                }
            }
            return count.incrementAndGet() <= maxRequests;
        }

        /**
         * Returns the number of seconds until the current window expires.
         * Used to populate the {@code Retry-After} response header.
         *
         * @return seconds until window expiry, minimum 1
         */
        long retryAfterSeconds() {
            long remaining = (windowStart + windowMillis - clock.getAsLong()) / 1000L;
            return Math.max(1L, remaining);
        }

        /**
         * Returns {@code true} if this counter has been inactive for more than two full
         * window durations and is safe to evict.
         *
         * @param now current time in milliseconds
         * @return {@code true} if the entry is stale
         */
        boolean isStale(long now) {
            return now - lastSeenMillis > windowMillis * 2;
        }

        long lastSeenMillis() {
            return lastSeenMillis;
        }
    }
}
