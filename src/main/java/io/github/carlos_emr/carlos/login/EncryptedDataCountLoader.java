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
package io.github.carlos_emr.carlos.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.email.core.EmailConfigSecrets;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Counts the stored records that hold data encrypted with the application key
 * {@code encryption.util.secret.key}, so {@link Startup} can tell a fresh install (nothing to
 * orphan: generate a key) from a server whose key has gone missing (refuse to start).
 *
 * <p>Startup runs before the Spring context, so this uses plain JDBC with the same {@code db_*}
 * settings, and the same URL assembly, as the {@code dataSource} bean in {@code spring_jpa.xml}.
 * It only ever runs {@code SELECT}s on a connection marked read-only.</p>
 *
 * <p>What is counted, one record per account, user or image:</p>
 * <ul>
 *   <li>{@code emailConfig.configDetails}: the JSON fields named by
 *       {@link EmailConfigSecrets#secretFieldNames()}.</li>
 *   <li>{@code fax_config.passwd} and {@code fax_config.faxPasswd} ({@code FaxConfig}).</li>
 *   <li>{@code property.value} where {@code name = 'teleplan_password'} ({@code TeleplanUserPassDAO}).
 *       The table is common to every province; only BC installs have the row.</li>
 *   <li>{@code security.mfaSecret} ({@code MfaManagerImpl}).</li>
 *   <li>{@code DigitalSignature.signatureImage} ({@code DigitalSignatureManagerImpl}).</li>
 * </ul>
 *
 * <p>String values must pass
 * {@link EncryptionUtils#isWellFormedCiphertext(String)} (the {@code {ENC}} marker plus Base64 of at
 * least an IV and a GCM tag). Signature images carry no marker, and their random IV can start
 * with any image magic bytes. Every signature long enough to be ciphertext is therefore counted
 * conservatively, including legacy plaintext images: without the key they cannot be safely
 * distinguished. Only byte lengths are fetched; image contents are not loaded or decoded.</p>
 *
 * <p>A table or column that this schema does not have (an empty schema before Flyway runs, an older
 * or partial schema) counts as holding nothing. Any other failure is reported in the result, never swallowed,
 * so the caller can refuse to start rather than guess.</p>
 *
 * <p><strong>Security:</strong> no stored value, credential, JSON or ciphertext is logged or put in
 * a failure description. Failures are described by SQLState, vendor error code and exception class
 * only: driver messages can echo connection settings.</p>
 *
 * @since 2026-09-29
 */
final class EncryptedDataCountLoader {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * SQLStates meaning "no such table or column". 42S02 and 42S22 are the X/Open codes MariaDB and
     * MySQL Connector/J report; 42S03 and 42S04 are H2's variants of 42S02.
     */
    private static final Set<String> ABSENT_OBJECT_STATES = Set.of("42S02", "42S03", "42S04", "42S22");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** The kinds of data encrypted with the application key, with operator-facing wording. */
    enum Kind {
        EMAIL_CREDENTIALS("email sender accounts",
                "re-enter the SMTP password or API key of each email sender account"),
        FAX_CREDENTIALS("fax accounts",
                "re-enter each fax account's password in Administration > Faxes > Configure Fax"),
        TELEPLAN_CREDENTIALS("Teleplan passwords",
                "re-enter the Teleplan password"),
        MFA_SECRETS("users with an MFA secret",
                "reset MFA for each of those users, who cannot log in until it is reset"),
        DIGITAL_SIGNATURES("stored digital signature images",
                "signature images encrypted with the old key cannot be recovered; legacy plaintext images are unaffected");

        private final String label;
        private final String remedy;

        Kind(String label, String remedy) {
            this.label = label;
            this.remedy = remedy;
        }

        String label() {
            return label;
        }

        String remedy() {
            return remedy;
        }
    }

    /** Opens the connection to count with. The loader closes it. */
    @FunctionalInterface
    interface ConnectionSource {
        Connection open() throws SQLException, ClassNotFoundException;
    }

    /**
     * What was counted. {@link #failures()} names each place that could not be read; when it is not
     * empty the counts are only a lower bound.
     *
     * @param counts   records holding ciphertext, per kind; kinds with none are absent
     * @param failures sanitized descriptions of what could not be read
     */
    record Result(Map<Kind, Integer> counts, List<String> failures) {

        Result {
            Map<Kind, Integer> copy = new EnumMap<>(Kind.class);
            counts.forEach((kind, count) -> {
                if (count != null && count > 0) {
                    copy.put(kind, count);
                }
            });
            counts = Collections.unmodifiableMap(copy);
            failures = List.copyOf(failures);
        }

        /** @return records that may hold ciphertext, across all kinds */
        int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** @return true when signature images are the only kind found, in a complete check */
        boolean onlySignatures() {
            return complete() && counts.size() == 1 && counts.containsKey(Kind.DIGITAL_SIGNATURES);
        }

        /** @return true when every place was checked; signature counts remain conservative */
        boolean complete() {
            return failures.isEmpty();
        }

        /** @return e.g. {@code "email sender accounts: 2, fax accounts: 1"}, or {@code "none"} */
        String describeCounts() {
            if (counts.isEmpty()) {
                return "none";
            }
            StringJoiner joiner = new StringJoiner(", ");
            counts.forEach((kind, count) -> joiner.add(kind.label() + ": " + count));
            return joiner.toString();
        }

        /** @return the remedy for each kind found, joined into one sentence fragment */
        String describeRemedies() {
            StringJoiner joiner = new StringJoiner("; ");
            counts.keySet().forEach(kind -> joiner.add(kind.remedy()));
            return joiner.toString();
        }

        /** @return the unread places, e.g. {@code "fax_config.passwd (SQLState 42000, ...)"} */
        String describeFailures() {
            return String.join("; ", failures);
        }
    }

    /** A test on one result row: does its value hold ciphertext? */
    @FunctionalInterface
    private interface CiphertextTest {
        boolean matches(ResultSet row) throws SQLException;
    }

    /**
     * One read-only query. Column 1 is the row's primary key, so a record with several encrypted
     * columns (a fax account with both passwords) is counted once.
     */
    private record Probe(Kind kind, String location, String sql, List<String> parameters, CiphertextTest test) {
    }

    /** LIKE pattern for a value that starts with the {@code {ENC}} marker. */
    private static final String STARTS_WITH_MARKER = "{ENC}%";

    /*
     * Fixed SQL, no user input. The {ENC} patterns are bound as parameters, not written as literals,
     * so no driver can mistake the brace for a JDBC escape. LIKE only narrows the rows fetched; each
     * value is then checked exactly (utf8mb4_general_ci makes LIKE case-insensitive, which only
     * fetches more rows).
     */
    private static final List<Probe> PROBES = List.of(
            new Probe(Kind.EMAIL_CREDENTIALS, "emailConfig.configDetails",
                    "SELECT id, configDetails FROM emailConfig WHERE configDetails LIKE ?",
                    List.of("%{ENC}%"),
                    row -> emailConfigHoldsCiphertext(row.getString(2))),
            new Probe(Kind.FAX_CREDENTIALS, "fax_config.passwd",
                    "SELECT id, passwd FROM fax_config WHERE passwd LIKE ?",
                    List.of(STARTS_WITH_MARKER),
                    row -> EncryptionUtils.isWellFormedCiphertext(row.getString(2))),
            new Probe(Kind.FAX_CREDENTIALS, "fax_config.faxPasswd",
                    "SELECT id, faxPasswd FROM fax_config WHERE faxPasswd LIKE ?",
                    List.of(STARTS_WITH_MARKER),
                    row -> EncryptionUtils.isWellFormedCiphertext(row.getString(2))),
            new Probe(Kind.TELEPLAN_CREDENTIALS, "property.value (teleplan_password)",
                    "SELECT id, value FROM property WHERE name = ? AND value LIKE ?",
                    List.of("teleplan_password", STARTS_WITH_MARKER), // NOSONAR java:S2068 - property name, not a credential
                    row -> EncryptionUtils.isWellFormedCiphertext(row.getString(2))),
            new Probe(Kind.MFA_SECRETS, "security.mfaSecret",
                    "SELECT security_no, mfaSecret FROM security WHERE mfaSecret LIKE ?",
                    List.of(STARTS_WITH_MARKER),
                    row -> EncryptionUtils.isWellFormedCiphertext(row.getString(2))),
            // A markerless ciphertext IV can match any image header. Do not infer plaintext
            // from that prefix and silently generate a replacement key over encrypted signatures.
            new Probe(Kind.DIGITAL_SIGNATURES, "DigitalSignature.signatureImage",
                    "SELECT id, OCTET_LENGTH(signatureImage)"
                            + " FROM DigitalSignature WHERE signatureImage IS NOT NULL",
                    List.of(),
                    row -> row.getLong(2) >= EncryptionUtils.MIN_CIPHERTEXT_BYTES));

    private final ConnectionSource connectionSource;

    EncryptedDataCountLoader(ConnectionSource connectionSource) {
        this.connectionSource = connectionSource;
    }

    /**
     * Builds a loader that connects with the {@code db_driver}, {@code db_uri}, {@code db_name},
     * {@code db_username} and {@code db_password} settings the application's data source uses.
     *
     * @param properties the loaded application properties
     * @return a loader for that database
     */
    static EncryptedDataCountLoader fromProperties(Properties properties) {
        return new EncryptedDataCountLoader(() -> openConnection(properties));
    }

    /**
     * Counts the records holding ciphertext. Never throws: every failure is in the result.
     *
     * @return the counts, and what could not be read
     */
    Result load() {
        Connection connection;
        try {
            connection = connectionSource.open();
        } catch (Exception | LinkageError e) {
            // LinkageError: a driver class that fails to load or initialise.
            return new Result(Map.of(), List.of("database connection (" + describe(e) + ")"));
        }

        Map<Kind, Set<Long>> found = new EnumMap<>(Kind.class);
        List<String> failures = new ArrayList<>();
        try {
            markReadOnly(connection);
            for (Probe probe : PROBES) {
                runProbe(connection, probe, found, failures);
            }
        } finally {
            closeQuietly(connection);
        }

        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        found.forEach((kind, ids) -> counts.put(kind, ids.size()));
        return new Result(counts, failures);
    }

    // FindSecBugs SQL_INJECTION_JDBC / SpotBugs SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING:
    // probe.sql() is only ever one of the fixed PROBES strings above, and every value is a bound
    // parameter; nothing from a request, the properties file or the database reaches the SQL.
    @SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
            justification = "SQL is one of the fixed PROBES constants; values are bound parameters")
    private static void runProbe(Connection connection, Probe probe, Map<Kind, Set<Long>> found,
                                 List<String> failures) {
        Set<Long> ids = found.computeIfAbsent(probe.kind(), kind -> new HashSet<>());
        try (PreparedStatement statement = connection.prepareStatement(probe.sql())) {
            statement.setQueryTimeout(30);
            for (int i = 0; i < probe.parameters().size(); i++) {
                statement.setString(i + 1, probe.parameters().get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (probe.test().matches(rows)) {
                        ids.add(rows.getLong(1));
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getSQLState() != null && ABSENT_OBJECT_STATES.contains(e.getSQLState())) {
                // Not in this schema (e.g. an older one without DigitalSignature): nothing is stored
                // there for a missing key to orphan.
                logger.debug("Encryption key check: {} is not in this schema; counted as empty",
                        probe.location());
                return;
            }
            failures.add(probe.location() + " (" + describe(e) + ")");
        } catch (RuntimeException e) {
            failures.add(probe.location() + " (" + describe(e) + ")");
        }
    }

    /**
     * True when a {@code configDetails} value holds ciphertext in a credential field. A value that
     * is not a JSON object is not counted: no sender can read a credential out of it with any key.
     * The parser's message can quote the value, so it is dropped, never logged.
     */
    private static boolean emailConfigHoldsCiphertext(String configDetails) {
        if (configDetails == null || configDetails.isBlank()) {
            return false;
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(configDetails);
        } catch (Exception e) {
            return false;
        }
        if (root == null || !root.isObject()) {
            return false;
        }
        for (String field : EmailConfigSecrets.secretFieldNames()) {
            JsonNode value = root.get(field);
            if (value != null && value.isValueNode() && EncryptionUtils.isWellFormedCiphertext(value.asText())) {
                return true;
            }
        }
        return false;
    }

    /** Connector/J connect timeout for the startup check, in milliseconds. */
    private static final String CONNECT_TIMEOUT_MILLIS = "15000";

    private static Connection openConnection(Properties properties) throws SQLException, ClassNotFoundException {
        String driver = properties.getProperty("db_driver");
        String uri = properties.getProperty("db_uri");
        if (driver == null || driver.isBlank() || uri == null || uri.isBlank()) {
            throw new MissingSettingsException("db_driver or db_uri is not set");
        }
        String name = properties.getProperty("db_name");
        registerDriver(driver.trim());
        String url = uri + (name == null ? "" : name);
        Properties connection = new Properties();
        putIfSet(connection, "user", properties.getProperty("db_username"));
        putIfSet(connection, "password", properties.getProperty("db_password"));
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            // An unreachable host would otherwise hang startup until the OS gives up on TCP;
            // this check only runs when the key is missing, and it fails closed either way.
            connection.setProperty("connectTimeout", CONNECT_TIMEOUT_MILLIS);
            connection.setProperty("socketTimeout", "30000");
        }
        return DriverManager.getConnection(url, connection);
    }

    /**
     * Registers the configured driver through this webapp's class loader, as the data source
     * would: a driver in WEB-INF/lib is not visible to DriverManager's own service lookup. Only
     * known driver names are loaded, by literal; any other name is left to DriverManager, so a
     * configured string never chooses which class is loaded.
     */
    private static void registerDriver(String driver) throws ClassNotFoundException {
        switch (driver) {
            case "com.mysql.cj.jdbc.Driver" -> Class.forName("com.mysql.cj.jdbc.Driver");
            case "com.mysql.jdbc.Driver" -> Class.forName("com.mysql.jdbc.Driver");
            case "org.mariadb.jdbc.Driver" -> Class.forName("org.mariadb.jdbc.Driver");
            case "org.h2.Driver" -> Class.forName("org.h2.Driver");
            default -> logger.debug("Encryption key check: db_driver is not one it loads itself; relying on"
                    + " DriverManager");
        }
    }

    private static void putIfSet(Properties target, String key, String value) {
        if (value != null) {
            target.setProperty(key, value);
        }
    }

    private static void markReadOnly(Connection connection) {
        try {
            connection.setReadOnly(true);
        } catch (SQLException | RuntimeException e) {
            // A hint only: every statement here is a SELECT, so the check stays read-only without it.
            logger.debug("Encryption key check: could not mark the connection read-only ({})", describe(e));
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException | RuntimeException e) {
            logger.debug("Encryption key check: closing the connection failed ({})", describe(e));
        }
    }

    /** SQLState, vendor code and class only: driver messages can echo URLs and settings. */
    private static String describe(Throwable failure) {
        if (failure instanceof MissingSettingsException) {
            return failure.getMessage();
        }
        if (failure instanceof SQLException sqlException) {
            return "SQLState " + LogSafe.sanitize(String.valueOf(sqlException.getSQLState()))
                    + ", error " + sqlException.getErrorCode()
                    + ", " + failure.getClass().getName();
        }
        return failure.getClass().getName();
    }

    /** A connection that cannot be attempted; its message is fixed text and safe to show. */
    private static final class MissingSettingsException extends SQLException {
        private static final long serialVersionUID = 1L;

        MissingSettingsException(String message) {
            super(message);
        }
    }
}
