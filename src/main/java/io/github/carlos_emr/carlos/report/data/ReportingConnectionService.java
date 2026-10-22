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
package io.github.carlos_emr.carlos.report.data;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Opens JDBC connections as the read-only reporting database account that Query by Example runs
 * under, and refuses an account that can do more than read reportable tables (issue #3372).
 *
 * <p>The application account owns the whole CARLOS schema, so a query that slipped past
 * {@link QueryByExampleSqlValidator} could read anything it can. The reporting account is the
 * independent boundary: MariaDB grants it {@code SELECT} only on the tables that
 * {@code db/reporting/carlos_report_grants.sql} lists as reportable. This class adds two
 * guarantees on top of that grant list:</p>
 * <ul>
 *   <li>It never falls back to the application account. Missing or incomplete settings, a
 *       reporting username equal to the application's or {@code root}, a refused login or a
 *       missing JDBC driver all raise {@link ReportingAccountUnavailableException} instead.</li>
 *   <li>Before handing a connection out it reads the account's own {@code SHOW GRANTS}. MariaDB
 *       prints there everything the session can use: its own grants, granted roles, the default
 *       role and grants to {@code PUBLIC}. Each line is checked against the same grant list
 *       that provisioned the account: only {@code USAGE} and {@code SELECT} granted to this very
 *       account on a {@code reportable} table of the application schema pass, and a table with
 *       hidden columns passes only as a column-list grant that names none of them. Anything else
 *       (a restricted or unclassified table, a table-wide grant on a table with hidden columns,
 *       a hidden column, a write privilege, a role, a {@code PUBLIC} grant) closes the connection
 *       and fails closed. That catches whatever was granted by hand or left behind outside the
 *       provisioning script.</li>
 * </ul>
 *
 * <p>Each call opens its own physical connection rather than keeping a pool: Query by Example is
 * an occasional administrative tool, the account's {@code MAX_USER_CONNECTIONS} caps concurrency,
 * and no idle connection holding the reporting credential outlives the request.</p>
 *
 * @since 2026-10-22
 */
public class ReportingConnectionService {
    /** carlos.properties key naming the reporting database account. */
    public static final String USERNAME_PROPERTY = "db_reporting_username";
    /** carlos.properties key holding the reporting database account's password. */
    public static final String PASSWORD_PROPERTY = "db_reporting_password";

    private static final Logger logger = MiscUtils.getLogger();

    /** MariaDB/MySQL: access denied for the login (1045) or for the default schema (1044). */
    private static final int ER_ACCESS_DENIED = 1045;
    private static final int ER_DB_ACCESS_DENIED = 1044;

    // Every repetition over a group is possessive: Java matches a backtracking repetition of a
    // group by recursion, one frame per iteration, so a long grant line could overflow the stack.
    // Possessive matching gives up nothing here, since a quoted name always ends at its quote.
    private static final String IDENTIFIER = "(?:[^`]|``)++";
    private static final Pattern USAGE_GRANT = Pattern.compile("^GRANT USAGE ON \\*\\.\\* TO (?<grantee>.+)$");
    private static final Pattern SELECT_GRANT = Pattern.compile(
            "^GRANT SELECT(?: \\((?<columns>`" + IDENTIFIER + "`(?:, `" + IDENTIFIER + "`)*+)\\))?+"
                    + " ON `(?<schema>" + IDENTIFIER + ")`\\.`(?<table>" + IDENTIFIER + ")` TO (?<grantee>.+)$");
    private static final Pattern COLUMN = Pattern.compile("`(" + IDENTIFIER + ")`");
    // MariaDB 10.4+ and MySQL 8 quote the grantee with backticks, older releases with single quotes.
    private static final Pattern GRANTEE = Pattern.compile("`(?<user>(?:[^`]|``)*+)`@`(?<host>(?:[^`]|``)*+)`");
    private static final Pattern LEGACY_GRANTEE = Pattern.compile("'(?<user>(?:[^']|'')*+)'@'(?<host>(?:[^']|'')*+)'");
    private static final Pattern GRANT_OPTION = Pattern.compile("\\bGRANT\\s+OPTION\\b", Pattern.CASE_INSENSITIVE);
    private static final String ANOTHER_GRANTEE = "a privilege granted to another account, a role or PUBLIC";

    /**
     * Opens one physical connection. The default implementation asks {@link DriverManager} for
     * the driver that accepts the URL; tests substitute their own.
     */
    @FunctionalInterface
    interface DriverConnector {
        Connection connect(String url, Properties credentials) throws SQLException;
    }

    /** Supplies the grant list the audit checks against; tests substitute their own. */
    @FunctionalInterface
    interface GrantListSource {
        ReportingGrantList load() throws IOException;
    }

    /**
     * What each {@code SHOW GRANTS} line is checked against: the account the session runs as, the
     * application schema, the grant list, and whether the server folds table names to lower case.
     */
    record GrantAudit(String user, String host, String schema, ReportingGrantList grantList,
                      boolean caseInsensitiveTableNames) {
    }

    private record Settings(String url, String username, String password, String schema) {
    }

    /** The account the session authenticated as, and whether the server folds table names. */
    private record SessionAccount(String user, String host, boolean caseInsensitiveTableNames) {
    }

    /** The grantee of a grant line and whatever follows it (options on a USAGE line). */
    private record Grantee(String user, String host, String tail) {
    }

    private final DriverConnector connector;
    private final GrantListSource grantListSource;

    /** Creates the service that connects through the registered JDBC driver for {@code db_uri}. */
    public ReportingConnectionService() {
        this(ReportingConnectionService::connectWithRegisteredDriver);
    }

    ReportingConnectionService(DriverConnector connector) {
        this(connector, ReportingGrantList::fromClasspath);
    }

    ReportingConnectionService(DriverConnector connector, GrantListSource grantListSource) {
        this.connector = connector;
        this.grantListSource = grantListSource;
    }

    /**
     * Whether carlos.properties names a reporting account at all. It does not contact the
     * database, so a {@code true} here can still end in {@link ReportingAccountUnavailableException}
     * from {@link #openConnection(Properties)}.
     *
     * @param properties application properties
     * @return {@code true} when the reporting username and password and the database location are set
     */
    public static boolean isConfigured(Properties properties) {
        try {
            settings(properties);
            return true;
        } catch (ReportingAccountUnavailableException _) {
            return false;
        }
    }

    /**
     * Opens a connection as the reporting account after confirming it holds nothing beyond
     * {@code SELECT} on reportable tables of the application schema. The caller owns the
     * returned connection and must close it.
     *
     * @param properties application properties: {@code db_uri}, {@code db_name},
     *        {@code db_username}, {@value #USERNAME_PROPERTY} and {@value #PASSWORD_PROPERTY}
     * @return an open connection authenticated as the reporting account
     * @throws ReportingAccountUnavailableException if the account is not configured, is refused,
     *         cannot be audited, or holds a privilege beyond reportable {@code SELECT}
     * @throws SQLException if the database cannot be reached for another reason
     */
    public Connection openConnection(Properties properties) throws SQLException {
        Settings settings = settings(properties);
        ReportingGrantList grantList = grantList();
        Connection connection = connect(settings);
        try {
            verifyGrants(connection, settings, grantList);
        } catch (ReportingAccountUnavailableException refused) {
            closeAfterRefusal(connection, refused);
            throw refused;
        } catch (SQLException | RuntimeException failure) {
            ReportingAccountUnavailableException refused = new ReportingAccountUnavailableException(
                    "The reporting account's privileges could not be verified", failure);
            closeAfterRefusal(connection, refused);
            throw refused;
        }
        return connection;
    }

    private static Settings settings(Properties properties) throws ReportingAccountUnavailableException {
        if (properties == null) {
            throw new ReportingAccountUnavailableException("Application properties are not available");
        }
        String username = required(properties, USERNAME_PROPERTY);
        String password = properties.getProperty(PASSWORD_PROPERTY);
        if (password == null || password.isBlank()) {
            throw new ReportingAccountUnavailableException(PASSWORD_PROPERTY + " is not set");
        }
        String uri = required(properties, "db_uri");
        String name = required(properties, "db_name");
        String applicationUser = properties.getProperty("db_username", "").trim();
        if (isSameAccount(username, applicationUser) || isSameAccount(username, "root")) {
            throw new ReportingAccountUnavailableException(USERNAME_PROPERTY
                    + " must name a dedicated read-only account, not the application account or root");
        }
        String schema;
        try {
            schema = QueryByExampleSqlValidator.applicationSchema(properties);
        } catch (QueryByExampleValidationException e) {
            throw new ReportingAccountUnavailableException("The application database schema is not configured", e);
        }
        return new Settings(uri + name, username, password, schema);
    }

    // Without the list there is nothing to check the account's grants against, so fail closed
    // before connecting at all.
    private ReportingGrantList grantList() throws ReportingAccountUnavailableException {
        try {
            return grantListSource.load();
        } catch (IOException | IllegalArgumentException e) {
            throw new ReportingAccountUnavailableException(
                    "The reporting grant list shipped with the application cannot be read", e);
        }
    }

    private static String required(Properties properties, String key) throws ReportingAccountUnavailableException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ReportingAccountUnavailableException(key + " is not set");
        }
        return value.trim();
    }

    // MariaDB compares account names exactly, but a reporting username that differs from the
    // application account only by case is still almost certainly that account mistyped.
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Case-insensitive comparison of operator-configured ASCII database account names")
    private static boolean isSameAccount(String candidate, String account) {
        // FindSecBugs IMPROPER_UNICODE: database account names are ASCII operator configuration.
        return !account.isEmpty() && candidate.equalsIgnoreCase(account);
    }

    private Connection connect(Settings settings) throws SQLException {
        Properties credentials = new Properties();
        credentials.setProperty("user", settings.username());
        credentials.setProperty("password", settings.password());
        Connection connection;
        try {
            connection = connector.connect(settings.url(), credentials);
        } catch (ReportingAccountUnavailableException e) {
            throw e;
        } catch (SQLException e) {
            if (e.getErrorCode() == ER_ACCESS_DENIED || e.getErrorCode() == ER_DB_ACCESS_DENIED) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Query-by-Example reporting account login refused errorCode={} sqlState={}; "
                            + "provision it with carlos-ctl db-users (see docs/query-by-example-least-privilege.md)",
                            e.getErrorCode(), LogSafe.sanitize(e.getSQLState()));
                }
                throw new ReportingAccountUnavailableException("MariaDB refused the reporting account", e);
            }
            throw e;
        }
        if (connection == null) {
            throw new ReportingAccountUnavailableException("The JDBC driver did not accept the database URL");
        }
        return connection;
    }

    // The application DataSource loads db_driver when CARLOS starts, which registers that driver
    // with DriverManager under this webapp's class loader, so the reporting connection uses the
    // very same driver without loading classes by name here.
    private static Connection connectWithRegisteredDriver(String url, Properties credentials) throws SQLException {
        Driver driver;
        try {
            driver = DriverManager.getDriver(url);
        } catch (SQLException e) {
            throw new ReportingAccountUnavailableException("No registered JDBC driver accepts the database URL", e);
        }
        return driver.connect(url, credentials);
    }

    // Both audit statements are constant SQL. Semgrep taints the whole connection because its
    // JDBC URL is assembled from the operator-configured db_uri and db_name.
    private static SessionAccount sessionAccount(Connection connection) throws SQLException {
        String currentUser = null;
        boolean caseInsensitiveTableNames = false;
        try (Statement statement = connection.createStatement();
             // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string -- constant SQL; taint is the configured JDBC URL
             ResultSet result = statement.executeQuery("SELECT CURRENT_USER(), @@lower_case_table_names")) {
            if (result.next()) {
                currentUser = result.getString(1);
                // Other than 0, the server stores and prints table names in lower case, the way
                // the grant script matched them.
                caseInsensitiveTableNames = result.getInt(2) != 0;
            }
        }
        int at = currentUser == null ? -1 : currentUser.lastIndexOf('@');
        if (at <= 0) {
            throw new ReportingAccountUnavailableException("The database did not report the current account");
        }
        return new SessionAccount(currentUser.substring(0, at), currentUser.substring(at + 1), caseInsensitiveTableNames);
    }

    private static void verifyGrants(Connection connection, Settings settings, ReportingGrantList grantList)
            throws SQLException {
        SessionAccount account = sessionAccount(connection);
        GrantAudit audit = new GrantAudit(account.user(), account.host(), settings.schema(), grantList,
                account.caseInsensitiveTableNames());

        try (Statement statement = connection.createStatement();
             // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string -- constant SQL; taint is the configured JDBC URL
             ResultSet grants = statement.executeQuery("SHOW GRANTS")) {
            while (grants.next()) {
                Optional<String> refusal = refusalReason(grants.getString(1), audit);
                if (refusal.isPresent()) {
                    // Never log the grant line itself: the USAGE line carries the password hash.
                    if (logger.isWarnEnabled()) {
                        logger.warn("Query-by-Example refused the reporting account: it holds {}. Re-run the "
                                + "reporting grant sync (carlos-ctl db-users) and remove grants made by hand; "
                                + "see docs/query-by-example-least-privilege.md", LogSafe.sanitize(refusal.get()));
                    }
                    throw new ReportingAccountUnavailableException(
                            "The reporting account holds " + refusal.get());
                }
            }
        }
    }

    /**
     * Classifies one {@code SHOW GRANTS} line. Accepted are {@code USAGE ON *.*} and
     * {@code SELECT} on a table of the application schema that the grant list classifies as
     * reportable, each granted to the current account itself and without {@code GRANT OPTION}.
     * A table with hidden columns is accepted only as a column-list grant naming none of them.
     *
     * @param grant one line of {@code SHOW GRANTS} output
     * @param audit the account, schema and grant list the line is checked against
     * @return empty when the line is acceptable, otherwise a description safe to log (it never
     *         contains the line, which can carry the password hash)
     */
    static Optional<String> refusalReason(String grant, GrantAudit audit) {
        if (grant == null || grant.isBlank()) {
            return Optional.of("an unreadable grant");
        }
        String line = grant.trim();
        Matcher usage = USAGE_GRANT.matcher(line);
        if (usage.matches()) {
            Optional<Grantee> grantee = grantee(usage.group("grantee"));
            if (grantee.isEmpty() || !isCurrentAccount(grantee.get(), audit)) {
                return Optional.of(ANOTHER_GRANTEE);
            }
            if (GRANT_OPTION.matcher(grantee.get().tail()).find()) {
                return Optional.of("WITH GRANT OPTION");
            }
            return Optional.empty();
        }
        Matcher select = SELECT_GRANT.matcher(line);
        if (select.matches()) {
            Optional<Grantee> grantee = grantee(select.group("grantee"));
            if (grantee.isEmpty() || !isCurrentAccount(grantee.get(), audit)) {
                return Optional.of(ANOTHER_GRANTEE);
            }
            if (!grantee.get().tail().isEmpty()) {
                return Optional.of(GRANT_OPTION.matcher(grantee.get().tail()).find()
                        ? "WITH GRANT OPTION" : "a SELECT grant with options");
            }
            String grantedSchema = unescapeBackticks(select.group("schema"));
            if (!grantedSchema.equals(audit.schema())) {
                return Optional.of("SELECT on another schema (" + grantedSchema + ")");
            }
            return tableRefusal(unescapeBackticks(select.group("table")), select.group("columns"), audit);
        }
        if (line.startsWith("SET DEFAULT ROLE")) {
            return Optional.of("a default role");
        }
        if (line.endsWith(" TO PUBLIC")) {
            return Optional.of("a privilege granted to PUBLIC");
        }
        if (line.startsWith("GRANT `") || line.startsWith("GRANT '")) {
            return Optional.of("a role");
        }
        return Optional.of("a privilege other than SELECT on reportable tables of " + audit.schema());
    }

    // A SELECT grant on one table, with its column list when it has one, against the grant list.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Matches ASCII table names against the restricted-table list, which is kept in lower case")
    private static Optional<String> tableRefusal(String table, String columnList, GrantAudit audit) {
        // FindSecBugs IMPROPER_UNICODE: table names are ASCII; the restricted list is lower case.
        if (QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
            return Optional.of("SELECT on the restricted table " + table);
        }
        Optional<ReportingGrantList.Entry> row = audit.grantList().find(table, audit.caseInsensitiveTableNames());
        if (row.isEmpty()) {
            return Optional.of("SELECT on " + table + ", a table the reporting grant list does not classify");
        }
        if (!row.get().reportable()) {
            return Optional.of("SELECT on the restricted table " + table);
        }
        if (row.get().excludedColumns().isEmpty()) {
            return Optional.empty();
        }
        if (columnList == null) {
            return Optional.of("SELECT on every column of " + table + ", hidden columns included");
        }
        Matcher column = COLUMN.matcher(columnList);
        while (column.find()) {
            String name = unescapeBackticks(column.group(1));
            if (row.get().hides(name)) {
                return Optional.of("SELECT on the hidden column " + table + "." + name);
            }
        }
        return Optional.empty();
    }

    // The quoted user@host at the start of the text after TO, and what follows it.
    private static Optional<Grantee> grantee(String text) {
        Matcher quoted = GRANTEE.matcher(text);
        if (quoted.lookingAt()) {
            return Optional.of(new Grantee(quoted.group("user").replace("``", "`"),
                    quoted.group("host").replace("``", "`"), text.substring(quoted.end())));
        }
        Matcher legacy = LEGACY_GRANTEE.matcher(text);
        if (legacy.lookingAt()) {
            return Optional.of(new Grantee(legacy.group("user").replace("''", "'"),
                    legacy.group("host").replace("''", "'"), text.substring(legacy.end())));
        }
        return Optional.empty();
    }

    private static boolean isCurrentAccount(Grantee grantee, GrantAudit audit) {
        return grantee.user().equals(audit.user()) && grantee.host().equals(audit.host());
    }

    private static String unescapeBackticks(String identifier) {
        return identifier.replace("``", "`");
    }

    private static void closeAfterRefusal(Connection connection, SQLException refusal) {
        try {
            connection.close();
        } catch (SQLException | RuntimeException closeFailure) {
            refusal.addSuppressed(closeFailure);
        }
    }
}
