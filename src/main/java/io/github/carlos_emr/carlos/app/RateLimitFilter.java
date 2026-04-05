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
 *       {@code /login.do=10/60,/ws/=200/60} (prefix matching)</li>
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

    // --- Runtime state ---

    /**
     * Counter storage. Key format:
     * <ul>
     *   <li>Global counter: {@code clientIp}</li>
     *   <li>Path-specific counter: {@code clientIp|pathKey}</li>
     * </ul>
     */
    private final ConcurrentHashMap<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();

    /** Background thread for evicting stale counters to prevent unbounded memory growth. */
    private ScheduledExecutorService cleanupScheduler;

    /**
     * Initialises the filter by loading all configuration from {@link CarlosProperties}.
     *
     * @param filterConfig the servlet container filter configuration (not used; all config
     *                     comes from {@code CarlosProperties})
     * @throws ServletException if initialisation fails
     */
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
        enforcing = !"detect".equalsIgnoreCase(mode.trim());

        defaultRequests = getIntProperty(props, "WAF_RATE_LIMIT_DEFAULT_REQUESTS", 100);
        defaultWindowSeconds = getIntProperty(props, "WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS", 60);

        String pathsConfig = props.getProperty("WAF_RATE_LIMIT_PATHS");
        pathRates = parsePathRates(pathsConfig != null ? pathsConfig : "");

        String exemptConfig = props.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS");
        exemptIps = parseCsv(exemptConfig != null ? exemptConfig : "127.0.0.1,::1,0:0:0:0:0:0:0:1");

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
     *   <li>Determine effective rate: check path-specific tiers first, then global default.</li>
     *   <li>Increment counter(s); if limit exceeded, log and (in enforce mode) send 429.</li>
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
        FixedWindowCounter globalCounter = counters.computeIfAbsent(
                clientIp,
                k -> new FixedWindowCounter(defaultRequests, defaultWindowSeconds * 1000L));

        boolean globalAllowed = globalCounter.tryAcquire();

        if (!globalAllowed) {
            long retryAfterSeconds = globalCounter.retryAfterSeconds();
            if (enforcing) {
                logger.warn("RATE BLOCK: ip={} uri={} rule=rate-limit-global", clientIp, requestUri);
                httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                httpResponse.sendError(429, "Too Many Requests");
                return;
            } else {
                logger.warn("RATE DETECT: ip={} uri={} rule=rate-limit-global", clientIp, requestUri);
            }
        }

        // Check path-specific counter (defense-in-depth: count against both global and path)
        String matchedPath = findMatchingPath(path);
        if (matchedPath != null) {
            RateConfig pathConfig = pathRates.get(matchedPath);
            String counterKey = clientIp + "|" + matchedPath;
            FixedWindowCounter pathCounter = counters.computeIfAbsent(
                    counterKey,
                    k -> new FixedWindowCounter(pathConfig.requests, pathConfig.windowSeconds * 1000L));

            boolean pathAllowed = pathCounter.tryAcquire();

            if (!pathAllowed) {
                long retryAfterSeconds = pathCounter.retryAfterSeconds();
                if (enforcing) {
                    logger.warn("RATE BLOCK: ip={} uri={} rule=rate-limit-path:{}", clientIp, requestUri, matchedPath);
                    httpResponse.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                    httpResponse.sendError(429, "Too Many Requests");
                    return;
                } else {
                    logger.warn("RATE DETECT: ip={} uri={} rule=rate-limit-path:{}", clientIp, requestUri, matchedPath);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Shuts down the cleanup scheduler and releases resources.
     */
    @Override
    public void destroy() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
    }

    // --- Package-visible for testing ---

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

    // --- Private helpers ---

    /**
     * Finds the most specific path prefix in {@link #pathRates} that matches the given path.
     *
     * @param path the request path (without context path)
     * @return the matching path key, or {@code null} if no path-specific rate applies
     */
    private String findMatchingPath(String path) {
        String bestMatch = null;
        for (String prefix : pathRates.keySet()) {
            if (path.startsWith(prefix)) {
                if (bestMatch == null || prefix.length() > bestMatch.length()) {
                    bestMatch = prefix;
                }
            }
        }
        return bestMatch;
    }

    /**
     * Parses a comma-separated list of path rate configurations.
     *
     * <p>Format: {@code /path=requests/windowSeconds} (e.g. {@code /login.do=10/60}).</p>
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
                logger.warn("Rate limit: skipping non-numeric rate for path '{}': {}", pathPrefix, rateStr);
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
            logger.warn("Rate limit: property '{}' is not a valid integer '{}'; using default {}", key, value, defaultValue);
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
        long now = System.currentTimeMillis();
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
        private final int maxRequests;
        private final long windowMillis;

        /**
         * Creates a new counter.
         *
         * @param maxRequests  maximum requests allowed per window
         * @param windowMillis window duration in milliseconds
         */
        FixedWindowCounter(int maxRequests, long windowMillis) {
            this.maxRequests = maxRequests;
            this.windowMillis = windowMillis;
            this.windowStart = System.currentTimeMillis();
        }

        /**
         * Attempts to consume one request token.
         *
         * @return {@code true} if within the rate limit; {@code false} if the limit is exceeded
         */
        boolean tryAcquire() {
            long now = System.currentTimeMillis();
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
            long remaining = (windowStart + windowMillis - System.currentTimeMillis()) / 1000L;
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
            return now - windowStart > windowMillis * 2;
        }
    }
}
