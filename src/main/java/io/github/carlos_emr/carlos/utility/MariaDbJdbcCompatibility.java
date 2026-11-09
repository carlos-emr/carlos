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
package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.mariadb.jdbc.util.options.OptionAliases;

/** Translates installed Connector/J configuration before passing it to MariaDB Connector/J. */
public final class MariaDbJdbcCompatibility {
    private static final String MYSQL_URL_PREFIX = "jdbc:mysql:";
    private static final String USE_SSL = "useSsl";
    private static final String USE_SSL_ALIAS = "useSSL";
    private static final String TRUST_SERVER_CERTIFICATE = "trustServerCertificate";
    private static final String DISABLE_SSL_HOSTNAME_VERIFICATION = "disableSslHostnameVerification";
    private static final String LEGACY_MYSQL_SCHEME = "carlosLegacyMysqlScheme";
    private static final String HOST_SSL_MODE = "carlosHostSslMode";
    private static final String REQUIRE_SSL = "requireSSL";
    private static final String VERIFY_SERVER_CERTIFICATE = "verifyServerCertificate";
    private static final String TRANSFORMED_BIT_IS_BOOLEAN = "transformedBitIsBoolean";
    private static final String PRESERVE_INSTANTS = "preserveInstants";
    private static final String PURE_LOCAL_DATE_TIME = "pureLocalDateTime";
    private static final String ALLOW_LOCAL_INFILE = "allowLocalInfile";
    private static final String FALLBACK_TO_SYSTEM_KEY_STORE = "fallbackToSystemKeyStore";
    private static final String FALLBACK_TO_SYSTEM_TRUST_STORE = "fallbackToSystemTrustStore";
    private static final String DNS_SRV = "dnsSrv";
    private static final String FALSE = "false";
    private static final String SSL_MODE = "sslMode";
    private static final String SSL_MODE_TOKEN = "sslmode";
    private static final String SSL_PREFERRED = "preferred";
    private static final String SSL_DISABLE = "disable";
    private static final String SSL_TRUST = "trust";
    private static final String SSL_ALLOW = "allow";
    private static final String SSL_VERIFY_CA = "verify-ca";
    private static final String SSL_VERIFY_FULL = "verify-full";
    private static final String ZERO_DATE_TIME_BEHAVIOR = "zeroDateTimeBehavior";
    private static final String ADDRESS_PREFIX = "address=";
    private static final String TLS_CIPHERS = "enabledSslCipherSuites";
    private static final Set<String> EXCLUDED_CIPHER_TOKENS = Set.of("_ANON_", "_NULL_", "_EXPORT", "_MD5",
            "_DES", "_3DES_", "_RC2_", "_RC4_", "_PSK_", "_DH_");
    // Standard cipher identifiers allowed by the replaced Connector/J 9.6 TLS policy.
    private static final Set<String> ALLOWED_CIPHER_NAMES = Set.of(
            "AES_128_CCM_8_SHA256",
            "AES_128_CCM_SHA256",
            "AES_128_GCM_SHA256",
            "AES_256_GCM_SHA384",
            "CHACHA20_POLY1305_SHA256",
            "DHE_DSS_WITH_AES_128_CBC_SHA",
            "DHE_DSS_WITH_AES_128_CBC_SHA256",
            "DHE_DSS_WITH_AES_128_GCM_SHA256",
            "DHE_DSS_WITH_AES_256_CBC_SHA",
            "DHE_DSS_WITH_AES_256_CBC_SHA256",
            "DHE_DSS_WITH_AES_256_GCM_SHA384",
            "DHE_RSA_WITH_AES_128_CBC_SHA",
            "DHE_RSA_WITH_AES_128_CBC_SHA256",
            "DHE_RSA_WITH_AES_128_CCM",
            "DHE_RSA_WITH_AES_128_CCM_8",
            "DHE_RSA_WITH_AES_128_GCM_SHA256",
            "DHE_RSA_WITH_AES_256_CBC_SHA",
            "DHE_RSA_WITH_AES_256_CBC_SHA256",
            "DHE_RSA_WITH_AES_256_CCM",
            "DHE_RSA_WITH_AES_256_CCM_8",
            "DHE_RSA_WITH_AES_256_GCM_SHA384",
            "DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "ECDHE_ECDSA_WITH_AES_128_CCM",
            "ECDHE_ECDSA_WITH_AES_128_CCM_8",
            "ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "ECDHE_ECDSA_WITH_AES_256_CCM",
            "ECDHE_ECDSA_WITH_AES_256_CCM_8",
            "ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "ECDH_RSA_WITH_AES_128_CBC_SHA",
            "ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "ECDH_RSA_WITH_AES_256_CBC_SHA",
            "ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "RSA_WITH_AES_128_CBC_SHA",
            "RSA_WITH_AES_128_CBC_SHA256",
            "RSA_WITH_AES_128_CCM",
            "RSA_WITH_AES_128_CCM_8",
            "RSA_WITH_AES_128_GCM_SHA256",
            "RSA_WITH_AES_256_CBC_SHA",
            "RSA_WITH_AES_256_CBC_SHA256",
            "RSA_WITH_AES_256_CCM",
            "RSA_WITH_AES_256_CCM_8",
            "RSA_WITH_AES_256_GCM_SHA384",
            "RSA_WITH_CAMELLIA_128_CBC_SHA",
            "RSA_WITH_CAMELLIA_256_CBC_SHA");
    private static final String TLS_PROTOCOLS = "enabledSslProtocolSuites";
    private static final String FIPS_COMPLIANT_JSSE = "fipsCompliantJsse";
    private static final Set<String> UNSUPPORTED_TLS_PROVIDERS = Set.of("keyManagerFactoryProvider",
            "trustManagerFactoryProvider", "keyStoreProvider", "sslContextProvider");
    private static final Set<String> NATIVE_SECURITY_BOOLEANS = Set.of(ALLOW_LOCAL_INFILE,
            FALLBACK_TO_SYSTEM_KEY_STORE, FALLBACK_TO_SYSTEM_TRUST_STORE);
    // Booleans this adapter reads or polices itself: a value outside true/false/yes/no/1/0 is refused
    // here. Every other option MariaDB parses as Boolean only has legacy yes/no translated (#5969).
    private static final Set<String> BOOLEAN_OPTIONS = Set.of(REQUIRE_SSL, VERIFY_SERVER_CERTIFICATE,
            "useOldAliasMetadataBehavior", TRANSFORMED_BIT_IS_BOOLEAN, PRESERVE_INSTANTS, PURE_LOCAL_DATE_TIME,
            "tinyInt1isBit", ALLOW_LOCAL_INFILE, "useInformationSchema", DNS_SRV, "useUnicode",
            "emptyStringsConvertToZero", "treatMysqlDatetimeAsTimestamp", "jdbcCompliantTruncation",
            FALLBACK_TO_SYSTEM_KEY_STORE, FALLBACK_TO_SYSTEM_TRUST_STORE, FIPS_COMPLIANT_JSSE);
    private MariaDbJdbcCompatibility() { }

    /** Query-free driver URL and decoded options, owned by this connection preparation operation. */
    public record Settings(String url, Properties properties) { }

    /** Whether the configured scheme belongs to a database supported by this compatibility boundary. */
    public static boolean isMariaDbUrl(String url) {
        return url != null && (url.startsWith(MYSQL_URL_PREFIX) || url.startsWith("jdbc:mariadb:"));
    }

    /** Keep old rendered configurations usable without retaining the Oracle driver jar. */
    public static String normalizeDriverClassName(String driver) {
        return "com.mysql.cj.jdbc.Driver".equals(driver) || "com.mysql.jdbc.Driver".equals(driver)
                || "org.mariadb.jdbc.Driver".equals(driver) ? MariaDbCompatibilityDriver.class.getName() : driver;
    }

    /** Escaped URL for storage; native spelling is retained until connection properties are available. */
    public static String normalizeUrl(String url) {
        if (!isMariaDbUrl(url)) return url;
        try {
            Settings settings = parse(url, new Properties());
            Properties options = settings.properties().containsKey(LEGACY_MYSQL_SCHEME)
                    ? settings.properties() : nativeOptions(queryOptions(url), new Properties());
            StringBuilder normalized = new StringBuilder(settings.url());
            for (String key : new TreeSet<>(options.stringPropertyNames())) {
                normalized.append(normalized.indexOf("?") < 0 ? '?' : '&')
                        .append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(options.getProperty(key), StandardCharsets.UTF_8));
            }
            return normalized.toString();
        } catch (SQLException invalid) {
            // parse() emits only our credential-free configuration error.
            throw new IllegalArgumentException(invalid.getMessage());
        }
    }

    /**
     * MySQL connection properties override URL options; native MariaDB URL options
     * override connection properties. Legacy host credentials/options override both;
     * native host descriptors retain MariaDB's supported field semantics.
     * Returned properties are a fresh copy; the caller may pass them to MariaDB's mutating parser.
     */
    public static Settings settings(String url, Properties supplied) throws SQLException {
        Settings parsed;
        try {
            parsed = resolveHostOptions(parse(url, supplied));
        } catch (IllegalArgumentException malformed) {
            throw invalid(malformed);
        }
        Properties properties = parsed.properties();
        validateTlsSettings(properties);
        if (Boolean.parseBoolean(properties.getProperty(DNS_SRV, FALSE))) throw invalid();
        // These differ between the drivers and affect existing clinical values and metadata.
        properties.putIfAbsent(TRANSFORMED_BIT_IS_BOOLEAN, FALSE);
        properties.putIfAbsent(PRESERVE_INSTANTS, "true");
        properties.putIfAbsent(PURE_LOCAL_DATE_TIME, "true");
        // MariaDB enables LOCAL INFILE by default; Connector/J did not.
        properties.putIfAbsent(ALLOW_LOCAL_INFILE, FALSE);
        // Connector/J does not follow MariaDB's server redirection extension.
        properties.putIfAbsent("permitRedirect", FALSE);
        resolveTlsMode(properties);
        if (SSL_PREFERRED.equals(properties.getProperty(SSL_MODE))) {
            // MariaDB ALLOW verifies TLS identities. MySQL PREFERRED negotiates
            // TLS without identity verification, and allows servers without TLS.
            properties.setProperty(SSL_MODE, SSL_ALLOW);
            properties.setProperty("carlosLegacyPreferredTls", "true");
        }
        properties.putIfAbsent("tlsSocketType", "carlos-preferred");
        properties.remove(USE_SSL);
        properties.remove(USE_SSL_ALIAS);
        properties.remove(TRUST_SERVER_CERTIFICATE);
        properties.remove(DISABLE_SSL_HOSTNAME_VERIFICATION);
        properties.remove(REQUIRE_SSL);
        properties.remove(VERIFY_SERVER_CERTIFICATE);
        String zero = asciiLowerCase(properties.getProperty(ZERO_DATE_TIME_BEHAVIOR, "EXCEPTION"));
        zero = switch (zero) {
            case "round" -> "ROUND";
            case "exception" -> "EXCEPTION";
            case "converttonull", "convert_to_null" -> "CONVERT_TO_NULL";
            default -> throw invalid();
        };
        properties.setProperty(ZERO_DATE_TIME_BEHAVIOR, zero);
        return parsed;
    }

    /** Keep each driver's deprecated TLS flags distinct, including MariaDB's exact token semantics. */
    private static void resolveTlsMode(Properties properties) {
        boolean legacy = properties.remove(LEGACY_MYSQL_SCHEME) != null;
        String hostMode = (String) properties.remove(HOST_SSL_MODE);
        String mode = properties.getProperty(SSL_MODE);
        if (!legacy && hasNativeTlsFlags(properties)) {
            mode = nativeTlsMode(properties, mode);
        } else if (mode == null) {
            mode = legacyTlsMode(properties);
        }
        // Native MariaDB evaluates a host's explicit mode after its global deprecated flags.
        properties.setProperty(SSL_MODE, hostMode == null ? mode : hostMode);
    }

    private static boolean hasNativeTlsFlags(Properties properties) {
        return properties.containsKey(USE_SSL) || properties.containsKey(USE_SSL_ALIAS)
                || properties.containsKey(TRUST_SERVER_CERTIFICATE)
                || properties.containsKey(DISABLE_SSL_HOSTNAME_VERIFICATION);
    }

    private static String nativeTlsMode(Properties properties, String explicitMode) {
        if (nativeFlagEnabled(properties, USE_SSL) || nativeFlagEnabled(properties, USE_SSL_ALIAS)) {
            if (nativeFlagEnabled(properties, TRUST_SERVER_CERTIFICATE)) return SSL_TRUST;
            if (nativeFlagEnabled(properties, DISABLE_SSL_HOSTNAME_VERIFICATION)) return SSL_VERIFY_CA;
            return SSL_VERIFY_FULL;
        }
        return explicitMode == null ? SSL_DISABLE : explicitMode;
    }

    private static boolean nativeFlagEnabled(Properties properties, String name) {
        String value = properties.getProperty(name);
        return value != null && (value.equals("true") || value.equals("1") || value.isEmpty());
    }

    private static String legacyTlsMode(Properties properties) {
        // Existing application defaults follow Connector/J PREFERRED for either URL scheme.
        if (!Boolean.parseBoolean(properties.getProperty(USE_SSL, "true"))) return SSL_DISABLE;
        if (Boolean.parseBoolean(properties.getProperty(VERIFY_SERVER_CERTIFICATE, FALSE))) return SSL_VERIFY_CA;
        if (Boolean.parseBoolean(properties.getProperty(REQUIRE_SSL, FALSE))) return SSL_TRUST;
        return SSL_PREFERRED;
    }

    /** Decode storage syntax without materializing defaults before all property sources are available. */
    private static Settings parse(String url, Properties supplied) throws SQLException {
        if (!isMariaDbUrl(url)) throw invalid();
        try {
            int query = url.indexOf('?');
            String base = query < 0 ? url : url.substring(0, query);
            base = normalizeBase(base);
            Properties properties = new Properties();
            String queryOptions = queryOptions(url);
            boolean normalizedLegacy = hasLegacySchemeMarker(queryOptions);
            boolean legacy = url.startsWith(MYSQL_URL_PREFIX) || normalizedLegacy;
            if (legacy) {
                properties.setProperty(LEGACY_MYSQL_SCHEME, "true");
                mergeQueryOptions(properties, queryOptions);
                mergeSuppliedOptions(properties, supplied);
            } else {
                Properties nativeOptions = nativeOptions(queryOptions, supplied);
                validateNativeSpellings(nativeOptions);
                mergeSuppliedOptions(properties, nativeOptions);
            }
            return new Settings(base, properties);
        } catch (IllegalArgumentException malformed) {
            throw invalid(malformed);
        }
    }

    private static String queryOptions(String url) {
        int query = url.indexOf('?');
        return query < 0 ? "" : url.substring(query + 1);
    }

    /** Merge exact native names first; alternate spellings must not hide a stronger supplied policy. */
    private static Properties nativeOptions(String query, Properties supplied) {
        Properties options = new Properties();
        for (String name : supplied.stringPropertyNames()) options.setProperty(name, supplied.getProperty(name));
        for (String option : query.split("&")) {
            if (option.isEmpty()) continue;
            String[] pair = option.split("=", 2);
            options.setProperty(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length == 1 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return options;
    }

    /** Native reflective mapping gives conflicting aliases an iteration-dependent winner. Fail closed. */
    private static void validateNativeSpellings(Properties options) throws SQLException {
        Map<String, Properties> spellings = new HashMap<>();
        for (String name : options.stringPropertyNames()) {
            Properties normalized = new Properties();
            put(normalized, name, options.getProperty(name));
            validateTlsSettings(normalized);
            Properties previous = spellings.putIfAbsent(canonicalOptionKey(name), normalized);
            if (previous != null && !previous.equals(normalized)) throw invalid();
        }
    }

    private static void mergeSuppliedOptions(Properties properties, Properties supplied) throws SQLException {
        Properties preferredTls = new Properties();
        for (String key : supplied.stringPropertyNames()) {
            putSourceOption(properties, preferredTls, key, supplied.getProperty(key));
        }
        properties.putAll(preferredTls);
    }

    private static void mergeQueryOptions(Properties properties, String query) throws SQLException {
        Properties preferredTls = new Properties();
        for (String option : query.split("&")) {
            if (option.isEmpty()) continue;
            String[] pair = option.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (!LEGACY_MYSQL_SCHEME.equals(name)) {
                putSourceOption(properties, preferredTls, name,
                        pair.length == 1 ? "true" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        properties.putAll(preferredTls);
    }

    /** The normalized URL owns provenance; connection or host properties cannot override it. */
    private static boolean hasLegacySchemeMarker(String query) throws SQLException {
        boolean legacy = false;
        for (String option : query.split("&")) {
            String[] pair = option.split("=", 2);
            if (LEGACY_MYSQL_SCHEME.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                if (pair.length != 2 || !"true".equals(URLDecoder.decode(pair[1], StandardCharsets.UTF_8))) throw invalid();
                legacy = true;
            }
        }
        return legacy;
    }

    /** Connector/J's canonical TLS names beat their legacy aliases within the same property source. */
    private static void putSourceOption(Properties properties, Properties preferredTls, String key, String value) throws SQLException {
        put(properties, key, value);
        if (asciiEquals(key, "tlsVersions") || asciiEquals(key, "tlsCiphersuites")) put(preferredTls, key, value);
    }

    private static String normalizeBase(String base) {
        boolean legacy = base.startsWith(MYSQL_URL_PREFIX);
        base = base.replaceFirst("^jdbc:mysql:", "jdbc:mariadb:");
        if (legacy && base.startsWith("jdbc:mariadb://") && hasMultipleHosts(base)) {
            base = base.replaceFirst("^jdbc:mariadb:", "jdbc:mariadb:sequential:");
        }
        base = base.replaceFirst("^jdbc:mariadb:loadbalance:", "jdbc:mariadb:load-balance:");
        if (base.startsWith("jdbc:mariadb:///")) base = "jdbc:mariadb://localhost/" + base.substring("jdbc:mariadb:///".length());
        return base;
    }

    /** Normalize application and JDBC options before TLS-specific aliases. */
    private static String canonicalOptionKey(String name) {
        String token = asciiLowerCase(name);
        return switch (token) {
            case "servertimezone", "connectiontimezone" -> "connectionTimeZone";
            case "zerodatetimebehavior" -> ZERO_DATE_TIME_BEHAVIOR;
            case "useoldaliasmetadatabehavior" -> "useOldAliasMetadataBehavior";
            case "transformedbitisboolean" -> TRANSFORMED_BIT_IS_BOOLEAN;
            case "preserveinstants" -> PRESERVE_INSTANTS;
            case "purelocaldatetime" -> PURE_LOCAL_DATE_TIME;
            case "connecttimeout" -> "connectTimeout";
            case "sockettimeout" -> "socketTimeout";
            case "dnssrv" -> DNS_SRV;
            case "useinformationschema" -> "useInformationSchema";
            case "tinyint1isbit" -> "tinyInt1isBit";
            case "useunicode" -> "useUnicode";
            case "characterencoding" -> "characterEncoding";
            case "connectioncollation" -> "connectionCollation";
            case "allowloadlocalinfile", "allowlocalinfile" -> ALLOW_LOCAL_INFILE;
            case "emptystringsconverttozero" -> "emptyStringsConvertToZero";
            case "treatmysqldatetimeastimestamp" -> "treatMysqlDatetimeAsTimestamp";
            case "jdbccomplianttruncation" -> "jdbcCompliantTruncation";
            default -> canonicalTlsKey(token, name);
        };
    }

    /** Keep certificate and transport aliases together without altering unknown option names. */
    private static String canonicalTlsKey(String token, String original) {
        return switch (token) {
            case SSL_MODE_TOKEN -> SSL_MODE;
            case "tlsversions", "enabledtlsprotocols", "enabledsslprotocolsuites" -> TLS_PROTOCOLS;
            case "tlsciphersuites", "enabledsslciphersuites" -> TLS_CIPHERS;
            case "clientcertificatekeystoreurl", "keystore" -> "keyStore";
            case "clientcertificatekeystoretype", "keystoretype" -> "keyStoreType";
            case "clientcertificatekeystorepassword", "keystorepassword" -> "keyStorePassword";
            case "trustcertificatekeystoreurl", "truststore" -> "trustStore";
            case "trustcertificatekeystoretype", "truststoretype" -> "trustStoreType";
            case "trustcertificatekeystorepassword", "truststorepassword" -> "trustStorePassword";
            case "fallbacktosystemkeystore" -> FALLBACK_TO_SYSTEM_KEY_STORE;
            case "fallbacktosystemtruststore" -> FALLBACK_TO_SYSTEM_TRUST_STORE;
            case "fipscompliantjsse" -> FIPS_COMPLIANT_JSSE;
            case "keymanagerfactoryprovider" -> "keyManagerFactoryProvider";
            case "trustmanagerfactoryprovider" -> "trustManagerFactoryProvider";
            case "keystoreprovider" -> "keyStoreProvider";
            case "sslcontextprovider" -> "sslContextProvider";
            case "requiressl" -> REQUIRE_SSL;
            case "verifyservercertificate" -> VERIFY_SERVER_CERTIFICATE;
            default -> original;
        };
    }

    /** Canonicalize recognized aliases and reject ambiguous values before the driver can ignore them. */
    private static void put(Properties properties, String name, String value) throws SQLException {
        rejectReservedOption(name);
        String key = canonicalOptionKey(name);
        boolean legacyUseSsl = properties.containsKey(LEGACY_MYSQL_SCHEME) && asciiEquals(name, USE_SSL);
        if (legacyUseSsl) key = USE_SSL;
        if (BOOLEAN_OPTIONS.contains(key) || legacyUseSsl) {
            value = normalizedBoolean(properties, key, value);
        } else if (properties.containsKey(LEGACY_MYSQL_SCHEME) && NativeBooleanOptions.contains(key)) {
            value = legacyYesNo(value);
        }
        if (asciiEquals(key, "allowLoadLocalInfileInPath") && !value.isEmpty()) throw invalid();
        if (key.equals(SSL_MODE)) {
            value = switch (asciiLowerCase(value).replace('-', '_')) {
                case "disabled", SSL_DISABLE -> SSL_DISABLE;
                case SSL_PREFERRED -> SSL_PREFERRED;
                case SSL_ALLOW -> SSL_ALLOW;
                case "required", SSL_TRUST -> SSL_TRUST;
                case "verify_ca" -> SSL_VERIFY_CA;
                case "verify_identity", "verify_full" -> SSL_VERIFY_FULL;
                default -> throw invalid();
            };
        }
        // MariaDB always uses UTF-8; accepting a different requested encoder would be silent data loss.
        if (key.equals("characterEncoding") && !asciiEquals(value, "UTF-8")
                && !asciiEquals(value, "UTF8") && !asciiEquals(value, "utf8mb4")) throw invalid();
        if (key.equals("connectionCollation")) {
            putCollation(properties, key, value);
        } else {
            properties.setProperty(key, value);
        }
    }

    private static void rejectReservedOption(String name) throws SQLException {
        if (asciiEquals(name, LEGACY_MYSQL_SCHEME) || asciiEquals(name, HOST_SSL_MODE)) throw invalid();
    }

    private static String normalizedBoolean(Properties properties, String key, String value) throws SQLException {
        if (!properties.containsKey(LEGACY_MYSQL_SCHEME) && NATIVE_SECURITY_BOOLEANS.contains(key)) {
            return switch (asciiLowerCase(value)) {
                case "", "true", "1" -> "true";
                case FALSE, "0" -> FALSE;
                default -> throw invalid();
            };
        }
        return booleanValue(value);
    }

    private static String booleanValue(String value) throws SQLException {
        return switch (asciiLowerCase(value)) {
            case "true", "yes", "1" -> "true";
            case FALSE, "no", "0" -> FALSE;
            default -> throw invalid();
        };
    }

    /**
     * Connector/J accepted YES and NO, in any case, for every Boolean property. MariaDB accepts only
     * true, false, 1, 0 or an empty value and refuses the whole connection otherwise (#5969). Other
     * values are left unchanged so MariaDB keeps its own validation of them.
     */
    private static String legacyYesNo(String value) {
        if (asciiEquals(value, "yes")) return "true";
        if (asciiEquals(value, "no")) return FALSE;
        return value;
    }

    /**
     * Option names MariaDB parses as Boolean, read from the driver so that a connector upgrade cannot
     * leave a newly added option out. MariaDB resolves its alias table first, then matches
     * {@code Configuration.Builder} fields by name without regard to case; this mirrors both steps.
     * Loaded on first use so callers that only classify URLs do not need the driver's internals.
     */
    private static final class NativeBooleanOptions {
        private static final Set<String> NAMES = load();

        private NativeBooleanOptions() { }

        private static boolean contains(String key) {
            return NAMES.contains(asciiLowerCase(key));
        }

        private static Set<String> load() {
            Set<String> names = new HashSet<>();
            for (Field field : org.mariadb.jdbc.Configuration.Builder.class.getDeclaredFields()) {
                if (field.getType() == Boolean.class) names.add(asciiLowerCase(field.getName()));
            }
            for (Map.Entry<String, String> alias : OptionAliases.OPTIONS_ALIASES.entrySet()) {
                if (names.contains(asciiLowerCase(alias.getValue()))) names.add(asciiLowerCase(alias.getKey()));
            }
            return Set.copyOf(names);
        }
    }

    /** Never silently replace an explicitly selected JSSE security implementation. */
    private static void validateTlsSettings(Properties properties) throws SQLException {
        if (Boolean.parseBoolean(properties.getProperty(FIPS_COMPLIANT_JSSE, FALSE))) throw invalid();
        String protocols = properties.getProperty(TLS_PROTOCOLS);
        if (protocols != null) properties.setProperty(TLS_PROTOCOLS, validatedTlsProtocols(protocols));
        String ciphers = properties.getProperty(TLS_CIPHERS);
        if (ciphers != null && !ciphers.isEmpty()) properties.setProperty(TLS_CIPHERS, validatedTlsCiphers(ciphers));
        for (String provider : UNSUPPORTED_TLS_PROVIDERS) {
            if (!properties.getProperty(provider, "").isEmpty()) throw invalid();
        }
        normalizeStorePaths(properties);
    }

    private static String validatedTlsProtocols(String protocols) throws SQLException {
        List<String> requested = List.of(protocols.split("[,;\\s]+"));
        List<String> accepted = new ArrayList<>();
        for (String protocol : new String[]{"TLSv1.3", "TLSv1.2"}) {
            if (requested.stream().anyMatch(value -> value.trim().equals(protocol))) accepted.add(protocol);
        }
        if (accepted.isEmpty()) throw invalid();
        return String.join(",", accepted);
    }

    /** Retain the old connector's weak-cipher exclusions even when JVM security policy enables them. */
    private static String validatedTlsCiphers(String ciphers) throws SQLException {
        List<String> accepted = new ArrayList<>();
        for (String cipher : ciphers.split("[,;\\s]+")) {
            if (isAllowedCipher(cipher)) accepted.add(cipher);
        }
        if (accepted.isEmpty()) throw invalid();
        return String.join(",", accepted);
    }

    private static boolean isAllowedCipher(String cipher) {
        return (cipher.startsWith("TLS_") || cipher.startsWith("SSL_"))
                && ALLOWED_CIPHER_NAMES.contains(cipher.substring(4))
                && EXCLUDED_CIPHER_TOKENS.stream().noneMatch(cipher::contains);
    }

    private static void normalizeStorePaths(Properties properties) throws SQLException {
        for (String store : new String[]{"keyStore", "trustStore"}) {
            String value = properties.getProperty(store);
            if (value != null) properties.setProperty(store, normalizeStoreLocation(value));
        }
    }

    /** Share local URI handling with explicit JSSE system-store selection without changing plain paths. */
    static String normalizeStoreLocation(String value) throws SQLException {
        return value != null && value.length() >= 5 && asciiEquals(value.substring(0, 5), "file:")
                ? localStorePath(value) : value;
    }

    /** Retain Connector/J's local file URI support, including encoded and unescaped spaces. */
    private static String localStorePath(String value) throws SQLException {
        String normalized = "file:" + value.substring(5);
        try {
            URI uri = new URI(normalized.replace(" ", "%20"));
            if (uri.getAuthority() != null && !uri.getAuthority().isEmpty()
                    && !asciiEquals(uri.getAuthority(), "localhost")) throw invalid();
            // File URL handlers historically ignore query/fragment components. Decode only the path;
            // URLDecoder would incorrectly convert a literal plus sign into a space.
            String path = uri.isOpaque() ? new URI("file:/" + uri.getRawSchemeSpecificPart()).getPath().substring(1) : uri.getPath();
            if (path == null || path.isEmpty() || path.startsWith("//")) throw invalid();
            return Path.of(path).toString();
        } catch (URISyntaxException | IllegalArgumentException malformed) {
            SQLException sanitized = invalid();
            sanitized.setStackTrace(malformed.getStackTrace());
            throw sanitized;
        }
    }

    /** Mirror the native utf8mb4-only contract before its diagnostics can echo option values. */
    private static void putCollation(Properties properties, String key, String value) throws SQLException {
        if (value.trim().isEmpty()) {
            properties.remove(key);
        } else {
            String normalized = asciiLowerCase(value);
            if (!normalized.startsWith("utf8mb4_") || !normalized.matches("[a-z0-9_]+")) throw invalid();
            properties.setProperty(key, normalized);
        }
    }

    /** Count only top-level host separators, ignoring IPv6 and address-descriptor contents. */
    private static boolean hasMultipleHosts(String url) {
        String hosts = url.substring(url.indexOf("//") + 2).split("/", 2)[0];
        int nesting = 0;
        for (int index = 0; index < hosts.length(); index++) {
            char current = hosts.charAt(index);
            if (current == '(' || current == '[') nesting++;
            if (current == ')' || current == ']') nesting--;
            if (current == ',' && nesting == 0) return true;
        }
        return false;
    }

    /** MariaDB understands host/port/type/sslMode/pipe/localSocket descriptors and ignores other fields. */
    private static Settings resolveHostOptions(Settings settings) throws SQLException {
        String url = settings.url();
        int start = url.indexOf("//") + 2;
        if (start < 2) throw invalid();
        int end = authorityEnd(url, start);
        String authority = url.substring(start, end);
        List<String> hosts = splitHosts(authority);
        boolean multiple = hosts.size() > 1;
        List<String> normalized = new ArrayList<>();
        for (String host : hosts) {
            normalized.add(host.startsWith(ADDRESS_PREFIX)
                    ? normalizeDescriptorHost(host, settings.properties(), multiple)
                    : normalizeSimpleHost(host, settings.properties(), multiple));
        }
        return new Settings(url.substring(0, start) + String.join(",", normalized) + url.substring(end), settings.properties());
    }

    /** Socket paths inside a native address descriptor are not database-name separators. */
    private static int authorityEnd(String url, int start) throws SQLException {
        HostNesting nesting = new HostNesting();
        for (int index = start; index < url.length(); index++) {
            char current = url.charAt(index);
            if (current == '/' && nesting.isTopLevel()) return index;
            nesting.accept(current);
        }
        return url.length();
    }

    private static String normalizeSimpleHost(String host, Properties properties, boolean multiple) throws SQLException {
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            // Per-host credentials cannot be represented by one native global property.
            if (multiple) throw invalid();
            String[] credentials = host.substring(0, at).split(":", 2);
            put(properties, "user", URLDecoder.decode(credentials[0], StandardCharsets.UTF_8));
            if (credentials.length == 2) put(properties, "password", URLDecoder.decode(credentials[1], StandardCharsets.UTF_8));
            host = host.substring(at + 1);
        }
        if (host.indexOf('(') >= 0 || host.indexOf(')') >= 0 || host.indexOf('=') >= 0) throw invalid();
        return host;
    }

    private static String normalizeDescriptorHost(String host, Properties properties, boolean multiple) throws SQLException {
        var fields = Pattern.compile("\\(([^=()]+)=([^()]*)\\)").matcher(host);
        StringBuilder retained = new StringBuilder(ADDRESS_PREFIX);
        int consumed = ADDRESS_PREFIX.length();
        Properties preferredTls = new Properties();
        while (fields.find()) {
            if (fields.start() != consumed) throw invalid();
            consumed = fields.end();
            appendHostField(retained, properties, preferredTls, multiple, fields.group(1), fields.group(2));
        }
        if (consumed != host.length() || retained.length() == ADDRESS_PREFIX.length()) throw invalid();
        properties.putAll(preferredTls);
        return retained.toString();
    }

    private static void appendHostField(StringBuilder retained, Properties properties, Properties preferredTls, boolean multiple,
                                        String encodedKey, String encodedValue) throws SQLException {
        if (!properties.containsKey(LEGACY_MYSQL_SCHEME)) {
            rejectReservedOption(encodedKey);
            appendNativeHostField(retained, properties, multiple, asciiLowerCase(encodedKey), encodedValue);
            return;
        }
        String key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
        String value = URLDecoder.decode(encodedValue, StandardCharsets.UTF_8);
        String token = asciiLowerCase(key);
        rejectReservedOption(key);
        if (token.equals(SSL_MODE_TOKEN)) {
            appendHostTlsPolicy(retained, properties, multiple, value);
        } else if (Set.of("host", "port", "type").contains(token)) {
            retained.append('(').append(token).append('=').append(encodedValue).append(')');
        } else if (token.equals("protocol")) {
            if (!asciiEquals(value, "tcp")) throw invalid();
        } else {
            // A global property cannot represent different policies on different hosts.
            if (multiple) throw invalid();
            putSourceOption(properties, preferredTls, key, value);
        }
    }

    /** Native HostAddress ignores unsupported fields; promoting them could weaken global policies. */
    private static void appendNativeHostField(StringBuilder retained, Properties properties, boolean multiple,
                                              String token, String value) throws SQLException {
        if (token.equals(SSL_MODE_TOKEN)) {
            appendHostTlsPolicy(retained, properties, multiple, value);
        } else if (Set.of("host", "port", "type", "pipe", "localsocket").contains(token)) {
            retained.append('(').append(token).append('=').append(value).append(')');
        }
    }

    private static void appendHostTlsPolicy(StringBuilder retained, Properties properties, boolean multiple,
                                            String value) throws SQLException {
        if (!multiple) {
            put(properties, SSL_MODE, value);
            properties.setProperty(HOST_SSL_MODE, properties.getProperty(SSL_MODE));
        } else {
            Properties hostPolicy = new Properties();
            put(hostPolicy, SSL_MODE, value);
            String mode = hostPolicy.getProperty(SSL_MODE);
            if (mode.equals(SSL_PREFERRED)) throw invalid();
            retained.append("(sslMode=").append(mode).append(')');
        }
    }

    /** Split only outside descriptor fields and IPv6 literals, rejecting incomplete descriptors. */
    private static List<String> splitHosts(String authority) throws SQLException {
        List<String> hosts = new ArrayList<>();
        HostNesting nesting = new HostNesting();
        int start = 0;
        for (int index = 0; index < authority.length(); index++) {
            char current = authority.charAt(index);
            nesting.accept(current);
            if (current == ',' && nesting.isTopLevel()) {
                if (index == start) throw invalid();
                hosts.add(authority.substring(start, index));
                start = index + 1;
            }
        }
        if (!nesting.isTopLevel() || start == authority.length()) throw invalid();
        hosts.add(authority.substring(start));
        return hosts;
    }

    private static final class HostNesting {
        private int parentheses;
        private int brackets;

        private void accept(char current) throws SQLException {
            if (current == '(') {
                parentheses++;
            } else if (current == ')') {
                parentheses--;
            } else if (parentheses == 0) {
                if (current == '[') brackets++;
                else if (current == ']') brackets--;
            }
            if (parentheses < 0 || parentheses > 1 || brackets < 0 || brackets > 1) throw invalid();
        }

        private boolean isTopLevel() { return parentheses == 0 && brackets == 0; }
    }

    /** Match ASCII protocol/configuration tokens without Unicode case-equivalence aliases. */
    static boolean asciiEquals(String first, String second) {
        if (first == null || second == null || first.length() != second.length()) return false;
        for (int index = 0; index < first.length(); index++) {
            char left = first.charAt(index);
            char right = second.charAt(index);
            if (left > 127 || right > 127) return false;
            if (left >= 'A' && left <= 'Z') left += 'a' - 'A';
            if (right >= 'A' && right <= 'Z') right += 'a' - 'A';
            if (left != right) return false;
        }
        return true;
    }

    private static String asciiLowerCase(String token) {
        StringBuilder folded = new StringBuilder(token.length());
        for (int index = 0; index < token.length(); index++) {
            char current = token.charAt(index);
            folded.append(current >= 'A' && current <= 'Z' ? (char) (current + 'a' - 'A') : current);
        }
        return folded.toString();
    }

    /** Retain the parsing location without retaining exception messages that can contain credentials. */
    private static SQLException invalid(IllegalArgumentException parsingFailure) {
        SQLException sanitized = invalid();
        sanitized.setStackTrace(parsingFailure.getStackTrace());
        return sanitized;
    }

    /** Configuration errors must never echo connection credentials into application logs. */
    private static SQLException invalid() {
        return new SQLException("Invalid database connection configuration", "08001");
    }
}
