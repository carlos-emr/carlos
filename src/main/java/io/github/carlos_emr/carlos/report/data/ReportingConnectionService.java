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
 *       role and grants to {@code PUBLIC}. Anything other than {@code USAGE} and {@code SELECT} on
 *       a non-restricted table of the application schema, granted to this very account, closes the
 *       connection and fails closed. That catches a hand-made grant, a role, or a {@code PUBLIC}
 *       grant that the provisioning script did not create.</li>
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

    private static final String IDENTIFIER = "(?:[^`]|``)+";
    private static final String GRANTEE = "(?:`(?<userBacktick>(?:[^`]|``)*)`|'(?<userQuote>(?:[^']|'')*)')"
            + "@(?:`(?<hostBacktick>(?:[^`]|``)*)`|'(?<hostQuote>(?:[^']|'')*)')";
    private static final Pattern USAGE_GRANT = Pattern.compile(
            "^GRANT USAGE ON \\*\\.\\* TO " + GRANTEE + "(?<tail>.*)$");
    private static final Pattern SELECT_GRANT = Pattern.compile(
            "^GRANT SELECT(?: \\(`" + IDENTIFIER + "`(?:, `" + IDENTIFIER + "`)*\\))?"
                    + " ON `(?<schema>" + IDENTIFIER + ")`\\.`(?<table>" + IDENTIFIER + ")` TO " + GRANTEE + "$");
    private static final Pattern GRANT_OPTION = Pattern.compile("\\bGRANT\\s+OPTION\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Opens one physical connection. The default implementation asks {@link DriverManager} for
     * the driver that accepts the URL; tests substitute their own.
     */
    @FunctionalInterface
    interface DriverConnector {
        Connection connect(String url, Properties credentials) throws SQLException;
    }

    private record Settings(String url, String username, String password, String schema) {
    }

    private final DriverConnector connector;

    /** Creates the service that connects through the registered JDBC driver for {@code db_uri}. */
    public ReportingConnectionService() {
        this(ReportingConnectionService::connectWithRegisteredDriver);
    }

    ReportingConnectionService(DriverConnector connector) {
        this.connector = connector;
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
        } catch (ReportingAccountUnavailableException e) {
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
        Connection connection = connect(settings);
        try {
            verifyGrants(connection, settings);
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

    private static void verifyGrants(Connection connection, Settings settings) throws SQLException {
        String currentUser;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT CURRENT_USER()")) {
            currentUser = result.next() ? result.getString(1) : null;
        }
        int at = currentUser == null ? -1 : currentUser.lastIndexOf('@');
        if (at <= 0) {
            throw new ReportingAccountUnavailableException("The database did not report the current account");
        }
        String user = currentUser.substring(0, at);
        String host = currentUser.substring(at + 1);

        try (Statement statement = connection.createStatement();
             ResultSet grants = statement.executeQuery("SHOW GRANTS")) {
            while (grants.next()) {
                Optional<String> refusal = refusalReason(grants.getString(1), user, host, settings.schema());
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
     * {@code SELECT} (table or column list) on a table of the application schema that is not
     * restricted, each granted to the current account itself and without {@code GRANT OPTION}.
     *
     * @param grant one line of {@code SHOW GRANTS} output
     * @param user the current account's user name
     * @param host the current account's host
     * @param schema the application schema
     * @return empty when the line is acceptable, otherwise a description safe to log (it never
     *         contains the line, which can carry the password hash)
     */
    static Optional<String> refusalReason(String grant, String user, String host, String schema) {
        if (grant == null || grant.isBlank()) {
            return Optional.of("an unreadable grant");
        }
        String line = grant.trim();
        Matcher usage = USAGE_GRANT.matcher(line);
        if (usage.matches()) {
            if (!isCurrentAccount(usage, user, host)) {
                return Optional.of("a privilege granted to another account, a role or PUBLIC");
            }
            if (GRANT_OPTION.matcher(usage.group("tail")).find()) {
                return Optional.of("WITH GRANT OPTION");
            }
            return Optional.empty();
        }
        Matcher select = SELECT_GRANT.matcher(line);
        if (select.matches()) {
            if (!isCurrentAccount(select, user, host)) {
                return Optional.of("a privilege granted to another account, a role or PUBLIC");
            }
            String grantedSchema = unescapeBackticks(select.group("schema"));
            if (!grantedSchema.equals(schema)) {
                return Optional.of("SELECT on another schema (" + grantedSchema + ")");
            }
            String table = unescapeBackticks(select.group("table"));
            if (QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
                return Optional.of("SELECT on the restricted table " + table);
            }
            return Optional.empty();
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
        return Optional.of("a privilege other than SELECT on reportable tables of " + schema);
    }

    private static boolean isCurrentAccount(Matcher grant, String user, String host) {
        String grantedUser = grant.group("userBacktick") != null
                ? grant.group("userBacktick").replace("``", "`")
                : grant.group("userQuote").replace("''", "'");
        String grantedHost = grant.group("hostBacktick") != null
                ? grant.group("hostBacktick").replace("``", "`")
                : grant.group("hostQuote").replace("''", "'");
        return grantedUser.equals(user) && grantedHost.equals(host);
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
