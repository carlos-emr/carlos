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
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.CarlosProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Deployment configuration for the CARLOS to patient-portal channel.
 *
 * <p>The portal's {@code /internal/carlos/**} API grants clinic-wide staff powers — issuing invite
 * tokens, unlocking accounts, revealing passphrases — to any caller holding the service token. That
 * makes this class a security boundary, not plumbing, and it enforces three properties:
 *
 * <ul>
 *   <li><b>TLS only.</b> The base URL must be {@code https://}. The token is a bearer credential, so
 *       one plaintext hop hands clinic-wide portal access to anyone on the path.
 *   <li><b>Config-pinned destination.</b> The URL is read from deployment properties and never from
 *       request input, so no CARLOS request can redirect portal calls at an attacker's host.
 *   <li><b>Fail closed.</b> Missing or malformed configuration throws. Portal calls never silently
 *       become no-ops that a clinic would misread as "the invite was sent".
 * </ul>
 *
 * <p>CARLOS sends exactly one token. Rotation is driven from the portal side, which accepts both
 * {@code PATIENT_PORTAL_INTERNAL_API_TOKEN} and {@code ..._PREVIOUS} at once: set the portal's
 * previous token to the outgoing value, then move CARLOS to the new one. There is deliberately no
 * "previous token" property here — CARLOS would have no way to choose between two.
 *
 * @since 2026-08-19
 */
public record PatientPortalSettings(
        String baseUrl,
        String clinicId,
        PortalSecret serviceToken,
        Duration connectTimeout,
        Duration readTimeout,
        Set<String> certificatePins) {

    public static final String BASE_URL_KEY = "patient_portal.base_url";
    public static final String CLINIC_ID_KEY = "patient_portal.clinic_id";
    public static final String SERVICE_TOKEN_KEY = "patient_portal.service_token";
    public static final String CONNECT_TIMEOUT_KEY = "patient_portal.timeout.connect.ms";
    public static final String READ_TIMEOUT_KEY = "patient_portal.timeout.read.ms";

    /**
     * Optional public-key pins, comma separated.
     *
     * <p>Absent means standard TLS validation only, which trusts every CA in the JVM truststore.
     * Set this where a TLS-inspecting proxy CA may be installed on clinic machines — see {@link
     * PortalCertificatePinning}.
     */
    public static final String CERTIFICATE_PINS_KEY = "patient_portal.certificate.pins";

    private static final String REQUIRED_SCHEME_PREFIX = "https://";
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 5000L;
    private static final long DEFAULT_READ_TIMEOUT_MS = 15000L;

    private static final String MISSING_MESSAGE = "patient portal is not configured: %s is required";
    private static final String PLAINTEXT_MESSAGE =
            "%s must begin with a lowercase https:// ; refusing to send the portal token over"
                    + " plaintext";
    private static final String MALFORMED_MESSAGE = "%s is not a valid URL";
    private static final String NO_HOST_MESSAGE = "%s must name a host";
    private static final String USER_INFO_MESSAGE = "%s must not embed credentials";
    private static final String QUERY_MESSAGE = "%s must not carry a query string or fragment";
    private static final String TIMEOUT_MESSAGE = "%s must be a positive number of milliseconds";
    private static final String DESCRIPTION =
            "PatientPortalSettings[baseUrl=%s, clinicId=%s, token=%s, connect=%s, read=%s]";

    /**
     * Reads and validates the channel configuration.
     *
     * @param properties deployment properties, typically {@code carlos.properties} overlaid with
     *     {@code over_ride_config.properties}
     * @return validated settings; never partially populated
     * @throws PatientPortalConfigurationException if a required value is absent or blank, the base
     *     URL is not a plain {@code https://} origin, or a timeout is not a positive integer
     */
    public static PatientPortalSettings fromProperties(Map<String, String> properties) {
        return fromProperties(properties::get);
    }

    /**
     * Reads the channel configuration from CARLOS deployment properties.
     *
     * <p>The Spring wiring's entry point. Kept separate from {@link #fromProperties(Function)} so
     * the validation stays testable without the {@code CarlosProperties} singleton.
     *
     * @throws PatientPortalConfigurationException if the portal is unconfigured or misconfigured
     */
    public static PatientPortalSettings fromCarlosProperties() {
        return fromProperties(key -> CarlosProperties.getInstance().getProperty(key));
    }

    /**
     * Reports whether a portal is configured at all, without throwing.
     *
     * <p>Distinct from construction on purpose. Most CARLOS deployments will never use the portal,
     * and for them "unconfigured" is the normal state rather than an error — the panel should not
     * render and the actions should say so plainly. Construction stays fail-closed for the case
     * where someone <em>has</em> configured it and got it wrong, which is the dangerous one.
     *
     * <p>This checks <em>presence</em> only, and the distinction matters more than it looks. A key
     * that is present but malformed — a plaintext base URL, a non-numeric timeout — reports as
     * configured here and throws on construction, which is the intended fail-closed path. A key
     * that is present but <em>blank</em> does not: a portal with a base URL and a clinic id but an
     * empty service token reports as absent, and the actions answer "no portal on this server"
     * rather than naming the missing credential. That is the textbook half-configured deployment,
     * and it is the one case this method does not distinguish.
     */
    public static boolean isConfigured() {
        return isConfigured(key -> CarlosProperties.getInstance().getProperty(key));
    }

    static boolean isConfigured(Function<String, String> lookup) {
        for (String key : new String[] {BASE_URL_KEY, CLINIC_ID_KEY, SERVICE_TOKEN_KEY}) {
            String value = lookup.apply(key);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads and validates the channel configuration from an arbitrary property lookup.
     *
     * <p>Taking a lookup function rather than a concrete properties object keeps this testable
     * without the {@code CarlosProperties} singleton, and lets callers overlay sources.
     *
     * @param lookup resolves a property key to its configured value, or {@code null} if unset
     */
    public static PatientPortalSettings fromProperties(Function<String, String> lookup) {
        return new PatientPortalSettings(
                required(lookup, BASE_URL_KEY),
                required(lookup, CLINIC_ID_KEY),
                PortalSecret.of(requireValue(required(lookup, SERVICE_TOKEN_KEY), SERVICE_TOKEN_KEY)),
                timeout(lookup, CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT_MS),
                timeout(lookup, READ_TIMEOUT_KEY, DEFAULT_READ_TIMEOUT_MS),
                pins(lookup));
    }

    /**
     * Validates on every construction path, not only through {@link #fromProperties}.
     *
     * <p>An earlier revision validated in the factory alone, which left the canonical constructor
     * public and unchecked — {@code new PatientPortalSettings("http://evil.example", ...)} compiled
     * and produced a plaintext destination for the service token. A class whose own Javadoc calls
     * itself a security boundary cannot leave the front door open.
     */
    public PatientPortalSettings {
        baseUrl = validatedBaseUrl(requireValue(baseUrl, BASE_URL_KEY));
        clinicId = requireValue(clinicId, CLINIC_ID_KEY);
        if (serviceToken == null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING_MESSAGE, SERVICE_TOKEN_KEY));
        }
        requirePositive(connectTimeout, CONNECT_TIMEOUT_KEY);
        requirePositive(readTimeout, READ_TIMEOUT_KEY);
        certificatePins = certificatePins == null ? Set.of() : Set.copyOf(certificatePins);
    }

    /** Splits the optional pin list; an absent value means no pinning. */
    private static Set<String> pins(Function<String, String> lookup) {
        String configured = lookup.apply(CERTIFICATE_PINS_KEY);
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String pin : configured.split(",")) {
            String trimmed = pin.strip();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return Set.copyOf(parsed);
    }

    /**
     * @return {@code true} when the deployment requires a specific portal public key
     */
    public boolean isPinned() {
        return !certificatePins.isEmpty();
    }

    private static String requireValue(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING_MESSAGE, key));
        }
        return value.strip();
    }

    private static void requirePositive(Duration duration, String key) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, TIMEOUT_MESSAGE, key));
        }
    }

    private static String required(Function<String, String> lookup, String key) {
        return lookup.apply(key);
    }

    /**
     * Rejects anything that is not a bare {@code https://} origin.
     *
     * <p>The scheme is matched as an exact lowercase prefix rather than case-insensitively on
     * purpose. This is a TLS enforcement decision, and locale-dependent case folding is the
     * CVE-2024-38827 class of defect that CARLOS tracks in issue #2496; an operator writing {@code
     * HTTPS://} gets a clear error rather than a silently locale-sensitive comparison.
     *
     * <p>User-info is rejected because credentials in a URL leak into logs and proxy traces. A
     * query or fragment is rejected because endpoint paths are appended by string concatenation, so
     * {@code https://host?a=1} would yield {@code https://host?a=1/internal/carlos/...} — the entire
     * endpoint path swallowed into the query string, and every call landing on the portal root.
     */
    private static String validatedBaseUrl(String configured) {
        if (!configured.startsWith(REQUIRED_SCHEME_PREFIX)) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, PLAINTEXT_MESSAGE, BASE_URL_KEY));
        }
        URI uri;
        try {
            uri = new URI(configured);
        } catch (URISyntaxException exception) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MALFORMED_MESSAGE, BASE_URL_KEY), exception);
        }
        if (uri.getHost() == null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, NO_HOST_MESSAGE, BASE_URL_KEY));
        }
        if (uri.getUserInfo() != null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, USER_INFO_MESSAGE, BASE_URL_KEY));
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, QUERY_MESSAGE, BASE_URL_KEY));
        }
        return stripTrailingSlashes(configured);
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > REQUIRED_SCHEME_PREFIX.length() && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static Duration timeout(
            Function<String, String> lookup, String key, long defaultMilliseconds) {
        String configured = lookup.apply(key);
        if (configured == null || configured.isBlank()) {
            return Duration.ofMillis(defaultMilliseconds);
        }
        long milliseconds;
        try {
            milliseconds = Long.parseLong(configured.strip());
        } catch (NumberFormatException exception) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, TIMEOUT_MESSAGE, key), exception);
        }
        if (milliseconds <= 0) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, TIMEOUT_MESSAGE, key));
        }
        return Duration.ofMillis(milliseconds);
    }

    /**
     * Renders the endpoint without the credential.
     *
     * <p>A record's generated {@code toString} prints every component, which would put the service
     * token into any log line, stack trace, or debugger view that happened to render these
     * settings.
     */
    @Override
    public String toString() {
        return String.format(
                Locale.ROOT, DESCRIPTION, baseUrl, clinicId, serviceToken, connectTimeout,
                readTimeout);
    }
}
