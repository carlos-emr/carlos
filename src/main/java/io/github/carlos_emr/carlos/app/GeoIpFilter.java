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

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * GeoIP and IP reputation filter that restricts access by country and blocks known-malicious IPs.
 *
 * <h3>GeoIP Filtering</h3>
 * <p>Uses MaxMind GeoLite2-Country database for country-level geolocation. By default, only
 * Canadian IPs are allowed. The database file path is configurable via the {@code geoipDatabase}
 * init-param. Private/loopback IPs (RFC 1918, RFC 4193, ::1, 127.x) always pass through
 * without GeoIP lookup, which is essential for Docker/devcontainer environments.</p>
 *
 * <h3>IP Reputation — Spamhaus DROP List</h3>
 * <p>On startup, downloads the Spamhaus DROP list (JSON format) and loads CIDR ranges into
 * memory for O(n) prefix matching. The list is refreshed every 4 hours via a background
 * scheduled task. If the download fails, the filter continues operating with the last
 * successfully loaded list (fail-open on reputation).</p>
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li>{@code enforce} — blocks non-Canadian IPs and DROP-listed IPs with HTTP 403</li>
 *   <li>{@code detect} — logs violations but allows the request through</li>
 * </ul>
 *
 * <h3>Activation</h3>
 * <p>This filter is controlled by the {@code WAF_GEOIP_ENABLED} property in {@code carlos.properties}.
 * When the property is absent or set to any value other than {@code true}, {@code yes}, or
 * {@code on}, the filter passes all requests through without inspection.</p>
 *
 * <p>The Spamhaus DROP list is independently controlled by the {@code WAF_DROP_LIST_ENABLED}
 * property (also defaults to {@code false}).</p>
 *
 * <h3>Configuration (init-params)</h3>
 * <ul>
 *   <li>{@code geoipDatabase} — path to GeoLite2-Country.mmdb (default: {@code /opt/geoip/GeoLite2-Country.mmdb})</li>
 *   <li>{@code allowedCountries} — comma-separated ISO country codes (default: {@code CA})</li>
 *   <li>{@code mode} — {@code enforce} or {@code detect} (default: {@code enforce})</li>
 * </ul>
 *
 * <p><strong>Note:</strong> If the GeoIP database file is not found, the filter disables GeoIP
 * checking (logs a warning) but still performs DROP list checks if enabled. This allows
 * development environments to run without a GeoIP database.</p>
 *
 * @since 2026-04-04
 * @see WafFilter
 */
public final class GeoIpFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger("waf.geoip");

    private static final String DEFAULT_DB_PATH = "/opt/geoip/GeoLite2-Country.mmdb";
    private static final String DROP_LIST_URL = "https://www.spamhaus.org/drop/drop.txt";
    private static final int DROP_REFRESH_HOURS = 4;
    private static final int DOWNLOAD_TIMEOUT_MS = 10_000;

    private DatabaseReader geoIpReader;
    private Set<String> allowedCountries = Set.of("CA");
    private boolean enforcing = true;
    private boolean enabled = true;
    private boolean dropListEnabled = true;
    private boolean geoIpAvailable = false;

    /** CIDR entries from the Spamhaus DROP list loaded into memory. */
    private final CopyOnWriteArrayList<CidrRange> dropList = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        CarlosProperties carlosProps = CarlosProperties.getInstance();
        enabled = carlosProps.isPropertyActive("WAF_GEOIP_ENABLED");
        if (!enabled) {
            logger.info("GeoIP filter: disabled (WAF_GEOIP_ENABLED is not set to true/yes/on in carlos.properties)");
            return;
        }

        String modeParam = filterConfig.getInitParameter("mode");
        enforcing = !"detect".equalsIgnoreCase(modeParam);

        String countriesParam = filterConfig.getInitParameter("allowedCountries");
        if (countriesParam != null && !countriesParam.isBlank()) {
            allowedCountries = Arrays.stream(countriesParam.split(","))
                    .map(String::trim)
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        dropListEnabled = carlosProps.isPropertyActive("WAF_DROP_LIST_ENABLED");

        // Initialize GeoIP database
        String dbPath = filterConfig.getInitParameter("geoipDatabase");
        if (dbPath == null || dbPath.isBlank()) {
            dbPath = DEFAULT_DB_PATH;
        }

        File dbFile = new File(dbPath);
        if (dbFile.exists() && dbFile.isFile()) {
            try {
                geoIpReader = new DatabaseReader.Builder(dbFile).build();
                geoIpAvailable = true;
                logger.info("GeoIP filter: loaded database from {}", dbPath);
            } catch (IOException e) {
                logger.warn("GeoIP filter: failed to load database from {}, GeoIP checking disabled", dbPath, e);
            }
        } else {
            logger.warn("GeoIP filter: database not found at {}, GeoIP checking disabled. "
                    + "Download GeoLite2-Country.mmdb from MaxMind and place it at this path.", dbPath);
        }

        // Initialize DROP list
        if (dropListEnabled) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "waf-drop-list-updater");
                t.setDaemon(true);
                return t;
            });
            // Load immediately, then refresh every 4 hours
            scheduler.execute(this::refreshDropList);
            scheduler.scheduleAtFixedRate(this::refreshDropList,
                    DROP_REFRESH_HOURS, DROP_REFRESH_HOURS, TimeUnit.HOURS);
        }

        logger.info("GeoIP filter: mode={}, countries={}, geoip={}, drop-list={}",
                enforcing ? "enforce" : "detect",
                allowedCountries,
                geoIpAvailable ? "active" : "unavailable",
                dropListEnabled ? "active" : "disabled");
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

        String clientIp = request.getRemoteAddr();
        InetAddress addr;
        try {
            addr = InetAddress.getByName(clientIp);
        } catch (UnknownHostException e) {
            chain.doFilter(request, response);
            return;
        }

        // Private/loopback IPs always pass through (essential for devcontainer/Docker)
        if (isPrivateOrLoopback(addr)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        // Check Spamhaus DROP list first (known malicious IPs)
        if (dropListEnabled && isDropListed(addr)) {
            handleViolation(response, clientIp, uri, "drop-list");
            return;
        }

        // GeoIP country check
        if (geoIpAvailable) {
            String country = lookupCountry(addr);
            if (country != null && !allowedCountries.contains(country)) {
                handleViolation(response, clientIp, uri, "geoip-blocked (country=" + country + ")");
                return;
            }
            // If country is null (not found in DB), fail open — allow the request
            if (country == null) {
                logger.debug("GeoIP filter: no country found for {}, allowing request", clientIp);
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (geoIpReader != null) {
            try {
                geoIpReader.close();
            } catch (IOException e) {
                logger.debug("GeoIP filter: error closing database reader", e);
            }
        }
        logger.info("GeoIP filter: destroyed");
    }

    /**
     * Looks up the ISO country code for an IP address.
     *
     * @return ISO 3166-1 alpha-2 country code (e.g. "CA"), or {@code null} if not found
     */
    private String lookupCountry(InetAddress addr) {
        try {
            CountryResponse resp = geoIpReader.country(addr);
            return resp.getCountry().getIsoCode();
        } catch (AddressNotFoundException e) {
            return null;
        } catch (IOException | GeoIp2Exception e) {
            logger.debug("GeoIP filter: lookup failed for {}", addr.getHostAddress(), e);
            return null;
        }
    }

    /**
     * Checks if the IP is in the Spamhaus DROP list.
     */
    private boolean isDropListed(InetAddress addr) {
        byte[] addrBytes = addr.getAddress();
        for (CidrRange range : dropList) {
            if (range.contains(addrBytes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Downloads and parses the Spamhaus DROP list (text format with CIDR entries).
     * Each line is either a comment (starting with ;) or a CIDR range followed by a SBL identifier.
     */
    private void refreshDropList() {
        try {
            URL url = URI.create(DROP_LIST_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(DOWNLOAD_TIMEOUT_MS);
            conn.setReadTimeout(DOWNLOAD_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "CARLOS-EMR-WAF/1.0");

            if (conn.getResponseCode() != 200) {
                logger.warn("GeoIP filter: DROP list download returned HTTP {}", conn.getResponseCode());
                return;
            }

            List<CidrRange> newRanges;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                newRanges = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith(";"))
                        .map(line -> {
                            // Format: "cidr ; SBLnnn" — take only the CIDR part
                            int semicolon = line.indexOf(';');
                            return semicolon > 0 ? line.substring(0, semicolon).trim() : line.trim();
                        })
                        .map(CidrRange::parse)
                        .filter(r -> r != null)
                        .collect(Collectors.toList());
            }

            if (!newRanges.isEmpty()) {
                dropList.clear();
                dropList.addAll(newRanges);
                logger.info("GeoIP filter: loaded {} CIDR ranges from Spamhaus DROP list", newRanges.size());
            }
        } catch (IOException e) {
            logger.warn("GeoIP filter: failed to refresh DROP list (will retry in {} hours): {}",
                    DROP_REFRESH_HOURS, e.getMessage());
        }
    }

    private void handleViolation(HttpServletResponse response, String clientIp, String uri, String rule)
            throws IOException {
        if (enforcing) {
            logger.warn("GeoIP BLOCK: ip={} uri={} rule={}", clientIp, sanitizeLogUri(uri), rule);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
            logger.info("GeoIP DETECT: ip={} uri={} rule={}", clientIp, sanitizeLogUri(uri), rule);
        }
    }

    /**
     * Checks if an address is private (RFC 1918 / RFC 4193) or loopback.
     */
    private static boolean isPrivateOrLoopback(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress();
    }

    private static String sanitizeLogUri(String value) {
        if (value == null) {
            return "null";
        }
        String safe = value.replaceAll("[\\r\\n\\t]", "_");
        return safe.length() > 200 ? safe.substring(0, 200) + "..." : safe;
    }

    /**
     * Represents a CIDR network range for efficient IP containment checks.
     */
    static final class CidrRange {
        private final byte[] network;
        private final int prefixLength;

        CidrRange(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        /**
         * Parses a CIDR string like "192.168.1.0/24" into a CidrRange.
         *
         * @return the parsed range, or {@code null} if the string is invalid
         */
        static CidrRange parse(String cidr) {
            try {
                int slash = cidr.indexOf('/');
                if (slash < 0) {
                    return null;
                }
                String addrPart = cidr.substring(0, slash).trim();
                int prefix = Integer.parseInt(cidr.substring(slash + 1).trim());
                InetAddress addr = InetAddress.getByName(addrPart);
                byte[] bytes = addr.getAddress();
                if (prefix < 0 || prefix > bytes.length * 8) {
                    return null;
                }
                return new CidrRange(bytes, prefix);
            } catch (UnknownHostException | NumberFormatException e) {
                return null;
            }
        }

        /**
         * Checks if the given IP address bytes fall within this CIDR range.
         */
        boolean contains(byte[] addrBytes) {
            if (addrBytes.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addrBytes[i] != network[i]) {
                    return false;
                }
            }
            if (remainBits > 0 && fullBytes < network.length) {
                int mask = (0xFF << (8 - remainBits)) & 0xFF;
                if ((addrBytes[fullBytes] & mask) != (network[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
