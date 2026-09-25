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
 * tokens, unlocking accounts, revealing passphrases — to a workload holding the service token and
 * a provider identity and permission set signed by CARLOS. The assertion does not contain a
 * patient identifier; patient scope is enforced in CARLOS before the call. That makes this class a
 * security boundary, not plumbing, and it enforces these properties:
 *
 * <ul>
 *   <li><b>TLS only.</b> The base URL must be {@code https://}. The token is a bearer credential, so
 *       one plaintext hop hands clinic-wide portal access to anyone on the path.
 *   <li><b>Config-pinned destination.</b> The URL is read from deployment properties and never from
 *       request input, so no CARLOS request can redirect portal calls at an attacker's host.
 *   <li><b>Pinned server identity.</b> At least one portal TLS public-key pin is required in
 *       addition to certificate and hostname validation. There is no CA-only fallback.
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
        PortalSecret staffAssertionPrivateKey,
        String staffAssertionKeyId,
        Duration connectTimeout,
        Duration readTimeout,
        Duration requestTimeout,
        Set<String> certificatePins) {

    /**
     * The master switch. The portal is off unless this is exactly {@code true}, so a clinic that
     * does not use it needs no other setting, and one that does can switch it off without removing
     * its credentials.
     */
    public static final String ENABLED_KEY = "patient_portal.enabled";
    public static final String BASE_URL_KEY = "patient_portal.base_url";
    public static final String CLINIC_ID_KEY = "patient_portal.clinic_id";
    public static final String SERVICE_TOKEN_KEY = "patient_portal.service_token";
    public static final String STAFF_ASSERTION_KEY =
            "patient_portal.staff_assertion.private_key";
    public static final String STAFF_ASSERTION_KEY_ID = "patient_portal.staff_assertion.key_id";
    public static final String CONNECT_TIMEOUT_KEY = "patient_portal.timeout.connect.ms";
    public static final String READ_TIMEOUT_KEY = "patient_portal.timeout.read.ms";
    public static final String REQUEST_TIMEOUT_KEY = "patient_portal.timeout.request.ms";
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Required portal TLS public-key pins, comma separated.
     *
     * <p>Obtain these from the TLS terminator's certificate through a trusted administration
     * channel. Missing pins disable the integration rather than trusting any certificate
     * accepted by the JVM truststore. See {@link PortalCertificatePinning}.
     */
    public static final String CERTIFICATE_PINS_KEY = "patient_portal.certificate.pins";

    private static final String REQUIRED_SCHEME_PREFIX = "https://";
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 5000L;
    private static final long DEFAULT_READ_TIMEOUT_MS = 15000L;
    /**
     * Matches {@code MAX_CONFIG_CLINIC_ID_LENGTH} in the portal's {@code config.py}. Every call must
     * carry exactly the portal's configured clinic id, and the portal accepts only 1 to 20 characters
     * there; its database column is wider (64), but no configured id can use the extra room. Checking
     * 20 here makes a mistyped id fail when CARLOS reads its settings instead of on every call.
     */
    private static final int MAX_CLINIC_ID_LENGTH = 20;
    private static final int MIN_SERVICE_TOKEN_LENGTH = 32;
    static final Duration MAX_REQUEST_TIMEOUT = PortalStaffAssertionSigner.LIFETIME.minusSeconds(1);

    private static final String BAD_PIN_MESSAGE =
            "%s entries must look like sha256/<base64 sha-256 of the public key>";
    private static final String MISSING_MESSAGE = "patient portal is not configured: %s is required";
    private static final String NOT_ENABLED_MESSAGE =
            "patient portal is not enabled: set " + ENABLED_KEY + "=true to use it";
    /** A fixed message naming only the key, so callers may log it; it never carries a value. */
    public static final String ENABLED_VALUE_MESSAGE = ENABLED_KEY + " must be true or false";
    private static final String PLAINTEXT_MESSAGE =
            "%s must begin with a lowercase https:// ; refusing to send the portal token over"
                    + " plaintext";
    private static final String MALFORMED_MESSAGE = "%s is not a valid URL";
    private static final String NO_HOST_MESSAGE = "%s must name a host";
    private static final String PORT_MESSAGE = "%s must use a port between 1 and 65535";
    private static final String USER_INFO_MESSAGE = "%s must not embed credentials";
    private static final String QUERY_MESSAGE = "%s must not carry a query string or fragment";
    private static final String TIMEOUT_MESSAGE = "%s must be a positive number of milliseconds";
    private static final String CLINIC_ID_MESSAGE =
            "%s must contain 1 to 20 ASCII letters, digits, dots, underscores, or hyphens";
    private static final String SERVICE_TOKEN_MESSAGE =
            "%s must contain at least 32 visible ASCII characters";
    private static final String DESCRIPTION =
            "PatientPortalSettings[baseUrl=%s, clinicId=%s, token=%s, assertionKey=%s,"
                    + " connect=%s, read=%s]";

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
        return fromDeploymentProperties(key -> CarlosProperties.getInstance().getProperty(key));
    }

    /**
     * Reads the settings a deployment runs with: the master switch must be on, then the connection
     * settings are validated as {@link #fromProperties(Function)} does.
     *
     * @throws PatientPortalConfigurationException if {@link #ENABLED_KEY} is not {@code true}, or
     *     the connection settings are absent or invalid
     */
    static PatientPortalSettings fromDeploymentProperties(Function<String, String> lookup) {
        String enabled = switchValue(lookup);
        if (enabled.isEmpty() || "false".equals(enabled)) {
            throw new PatientPortalConfigurationException(NOT_ENABLED_MESSAGE);
        }
        if (!"true".equals(enabled)) {
            throw new PatientPortalConfigurationException(ENABLED_VALUE_MESSAGE);
        }
        return fromProperties(lookup);
    }

    /**
     * Reports whether the clinic has switched the portal on. Absent, blank or {@code false} means
     * off, the normal state for a clinic that does not use it, whatever connection settings are
     * present.
     *
     * <p>Any other value counts as on, so that constructing the settings reports it: a mistyped
     * switch, like a partial configuration, must surface as a configuration error rather than
     * quietly looking like an absent portal. Once on, missing connection settings are reported the
     * same way.
     */
    public static boolean isConfigured() {
        return isConfigured(key -> (String) CarlosProperties.getInstance().get(key));
    }

    static boolean isConfigured(Function<String, String> lookup) {
        String enabled = switchValue(lookup);
        return !enabled.isEmpty() && !"false".equals(enabled);
    }

    private static String switchValue(Function<String, String> lookup) {
        String value = lookup.apply(ENABLED_KEY);
        return value == null ? "" : value.strip();
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
                lookup.apply(BASE_URL_KEY),
                lookup.apply(CLINIC_ID_KEY),
                PortalSecret.of(requireValue(lookup.apply(SERVICE_TOKEN_KEY), SERVICE_TOKEN_KEY)),
                PortalSecret.of(
                        requireValue(lookup.apply(STAFF_ASSERTION_KEY), STAFF_ASSERTION_KEY)),
                lookup.apply(STAFF_ASSERTION_KEY_ID),
                timeout(lookup, CONNECT_TIMEOUT_KEY, DEFAULT_CONNECT_TIMEOUT_MS),
                timeout(lookup, READ_TIMEOUT_KEY, DEFAULT_READ_TIMEOUT_MS),
                timeout(lookup, REQUEST_TIMEOUT_KEY, DEFAULT_REQUEST_TIMEOUT.toMillis()),
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
        validateClinicId(clinicId);
        serviceToken = validatedServiceToken(serviceToken);
        if (staffAssertionPrivateKey == null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING_MESSAGE, STAFF_ASSERTION_KEY));
        }
        PortalStaffAssertionSigner.validate(staffAssertionPrivateKey);
        staffAssertionKeyId = requireValue(staffAssertionKeyId, STAFF_ASSERTION_KEY_ID);
        if (staffAssertionKeyId.length() > 64
                || staffAssertionKeyId.chars().anyMatch(character -> !isClinicIdCharacter(character))) {
            throw new PatientPortalConfigurationException(
                    STAFF_ASSERTION_KEY_ID + " must contain 1 to 64 ASCII letters, digits, dots, underscores, or hyphens");
        }
        requirePositive(connectTimeout, CONNECT_TIMEOUT_KEY);
        requirePositive(readTimeout, READ_TIMEOUT_KEY);
        requirePositive(requestTimeout, REQUEST_TIMEOUT_KEY);
        // Keep the complete exchange inside the assertion's validity window. The assertion's times are
        // whole seconds, rounded down, so it can expire up to a second earlier than LIFETIME after the
        // request starts; the deadline keeps that second in hand.
        if (requestTimeout.compareTo(MAX_REQUEST_TIMEOUT) > 0) {
            throw new PatientPortalConfigurationException(REQUEST_TIMEOUT_KEY + " must be at most "
                    + MAX_REQUEST_TIMEOUT.toMillis() + " milliseconds");
        }
        if (certificatePins == null || certificatePins.isEmpty()) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING_MESSAGE, CERTIFICATE_PINS_KEY));
        }
        // Format is checked here rather than only where the socket factory is built, so a typo in
        // carlos.properties fails when the settings are read — with the deployment's other
        // configuration errors — instead of surviving until the first handshake and surfacing as
        // "did not match any configured pin", which reads like a rotated key rather than a typo.
        for (String pin : certificatePins) {
            if (!PortalCertificatePinning.isWellFormed(pin)) {
                throw new PatientPortalConfigurationException(
                        String.format(Locale.ROOT, BAD_PIN_MESSAGE, CERTIFICATE_PINS_KEY));
            }
        }
        certificatePins = Set.copyOf(certificatePins);
    }

    /** Missing or blank pins are a configuration failure, never an unpinned connection. */
    private static Set<String> pins(Function<String, String> lookup) {
        String configured = requireValue(lookup.apply(CERTIFICATE_PINS_KEY), CERTIFICATE_PINS_KEY);
        Set<String> parsed = new LinkedHashSet<>();
        for (String pin : configured.split(",")) {
            String trimmed = pin.strip();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        if (parsed.isEmpty()) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, BAD_PIN_MESSAGE, CERTIFICATE_PINS_KEY));
        }
        return Set.copyOf(parsed);
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
        try {
            if (duration.toMillis() == 0) {
                throw new PatientPortalConfigurationException(
                        String.format(Locale.ROOT, TIMEOUT_MESSAGE, key));
            }
        } catch (ArithmeticException exception) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, TIMEOUT_MESSAGE, key), exception);
        }
    }

    private static void validateClinicId(String clinicId) {
        if (clinicId.length() > MAX_CLINIC_ID_LENGTH
                || clinicId.chars().anyMatch(character -> !isClinicIdCharacter(character))) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, CLINIC_ID_MESSAGE, CLINIC_ID_KEY));
        }
    }

    private static boolean isClinicIdCharacter(int character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '.'
                || character == '_'
                || character == '-';
    }

    /** Mirrors the portal's minimum and normalizes direct record construction like the factory. */
    private static PortalSecret validatedServiceToken(PortalSecret serviceToken) {
        if (serviceToken == null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING_MESSAGE, SERVICE_TOKEN_KEY));
        }
        String value = serviceToken.expose().strip();
        if (value.length() < MIN_SERVICE_TOKEN_LENGTH
                || value.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, SERVICE_TOKEN_MESSAGE, SERVICE_TOKEN_KEY));
        }
        return value.equals(serviceToken.expose()) ? serviceToken : PortalSecret.of(value);
    }

    /**
     * Rejects anything that is not a safe {@code https://} base URL.
     *
     * <p>The scheme is matched as an exact lowercase prefix rather than case-insensitively on
     * purpose. This is a TLS enforcement decision, and locale-dependent case folding is the
     * CVE-2024-38827 class of defect that CARLOS tracks in issue #2496; an operator writing {@code
     * HTTPS://} gets a clear error rather than a silently locale-sensitive comparison.
     *
     * <p>A path prefix is supported for a portal mounted below its origin. User-info is rejected
     * because credentials in a URL leak into logs and proxy traces. A query or fragment is rejected
     * because endpoint paths are appended by string concatenation, so {@code https://host?a=1}
     * would swallow the entire endpoint path into the query string.
     */
    private static String validatedBaseUrl(String configured) {
        if (!configured.startsWith(REQUIRED_SCHEME_PREFIX)) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, PLAINTEXT_MESSAGE, BASE_URL_KEY));
        }
        URI uri;
        try {
            uri = new URI(configured);
        } catch (URISyntaxException ignored) {
            // URISyntaxException repeats the complete input, including malformed user-info. Keep a
            // bad URL from carrying an embedded password into a later log through its cause chain.
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MALFORMED_MESSAGE, BASE_URL_KEY));
        }
        if (uri.getHost() == null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, NO_HOST_MESSAGE, BASE_URL_KEY));
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, PORT_MESSAGE, BASE_URL_KEY));
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
                Locale.ROOT,
                DESCRIPTION,
                baseUrl,
                clinicId,
                serviceToken,
                staffAssertionPrivateKey,
                connectTimeout,
                readTimeout);
    }
}
