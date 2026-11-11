/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.logging.log4j.Logger;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.export.SslMode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Backs up locked, bounded cohorts before deleting their exact identifiers.
 * A server-visible random lock proves the dump reads the same server session's
 * database, even if DNS changes. Unbacked or incompletely dumped rows survive.
 * The public manager retains its existing entry point and retention contract.
 */
@Service
public class AuditLogPurgeService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final int COHORT_SIZE = 1000;
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern INSERT = Pattern.compile(
            "^INSERT INTO `log` \\(`id`(?:, `[A-Za-z_][A-Za-z0-9_]{0,63}`){0,255}\\) VALUES \\((-?[0-9]+),");
    private final DataSource source;
    private final Duration commandTimeout;

    @Autowired
    public AuditLogPurgeService(DataSource source) {
        this(source, COMMAND_TIMEOUT);
    }

    AuditLogPurgeService(DataSource source, Duration commandTimeout) {
        this.source = source;
        this.commandTimeout = commandTimeout;
    }

    /**
     * Executes the configured dump without a shell or credentials in argv.
     * Every committed cohort has a durable SQL file, identity manifest and hash.
     * Failed attempts retain their private artifacts for diagnosis/recovery.
     */
    public int purge(Properties settings, java.util.Date through, Runnable recordAudit) throws Exception {
        LocalDate date;
        LocalDateTime cutoff;
        String executable;
        Path job;
        Path options;
        String preparation = "retention policy (log.purge.minDays)";
        try {
            ZoneId zone = ZoneId.systemDefault();
            // java.sql.Date is a supported Date subtype but rejects toInstant().
            date = through == null ? null : java.time.Instant.ofEpochMilli(through.getTime()).atZone(zone).toLocalDate();
            validateRetention(settings.getProperty("log.purge.minDays", "3650"), date, LocalDate.now(zone));
            // DATETIME is a wall clock; avoid connection-zone Timestamp conversion.
            cutoff = date.plusDays(1).atStartOfDay();
            preparation = "dump executable (log.purge.mysqldump)";
            executable = settings.getProperty("log.purge.mysqldump");
            if (executable == null || executable.isBlank()) throw new IOException("No dump command is configured");
            executable = executable(executable);
            preparation = "backup directory (log.purge.outputdir / DOCUMENT_DIR)";
            String output = settings.getProperty("log.purge.outputdir", settings.getProperty("DOCUMENT_DIR"));
            Path directory = PathValidationUtils.resolveConfiguredDirectory(output, "audit purge output directory").toPath();
            if (!Files.isDirectory(directory)) throw new IOException("Audit purge output directory does not exist");
            job = Files.createDirectory(directory.resolve("OSCAR_AUDIR_LOG_PURGE_" + UUID.randomUUID()),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            force(directory);
            options = privateFile(job, "connection.cnf");
        } catch (Exception failure) {
            // The form directs operators to the log. Preparation failures must
            // leave an actionable entry too, without echoing configured values.
            LOGGER.error("Audit purge refused during {}; no rows were deleted ({})", preparation, failure.getClass().getSimpleName());
            throw new IOException("Audit purge could not prepare " + preparation + "; no rows were deleted. Consult the server log.");
        }
        int removed = 0;
        try (PrivateCredentials credentials = new PrivateCredentials(options);
             Connection connection = source.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.isBlank()) throw new IOException("Audit purge connection has no database");
            String serialLock = "carlos.audit-purge:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(catalog.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
            String witness = "carlos.audit-backup:" + UUID.randomUUID();
            try (PurgeSession session = new PurgeSession(connection, serialLock, witness)) {
                if (!connection.getAutoCommit()) throw new IOException("Audit purge requires its own database transaction");
                if (!lock(connection, serialLock)) throw new IOException("Another audit purge owns this database");
                session.serialized = true;
                if (!lock(connection, witness)) throw new IOException("Audit backup identity could not be established");
                session.witnessed = true;
                long owner = scalar(connection, "SELECT CONNECTION_ID()");
                long highWater = scalar(connection, "SELECT COALESCE(MAX(id), 0) FROM log");
                // The actual live driver owns host, credentials and TLS policy;
                // do not reparse a possibly shadowed db_uri or use localhost defaults.
                org.mariadb.jdbc.Connection nativeConnection = connection.unwrap(org.mariadb.jdbc.Connection.class);
                Configuration live = nativeConnection.getContext().getConf();
                HostAddress address = nativeConnection.getClient().getHostAddress();
                SslMode tlsMode = address.sslMode == null ? live.sslMode() : address.sslMode;
                boolean requireTls = tlsMode != SslMode.DISABLE && tlsMode != SslMode.ALLOW;
                Path version = privateFile(job, "client-version.txt");
                Path errors = privateFile(job, "client-errors.txt");
                run(List.of(executable, "--no-defaults", "--version"), version, errors, connection);
                if (Files.size(version) > 65536) throw new IOException("Dump client identification was oversized");
                boolean mariaClient = Files.readString(version).contains("MariaDB");
                Files.writeString(options, clientOptions(live, address, mariaClient));
                // Lock selected rows without a supremum/gap lock that would
                // block the synchronous audit record's separate connection.
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                session.changedIsolation = true;
                connection.setAutoCommit(false);
                session.transactional = true;
                boolean audited = false;
                int cohort = 0;
                while (true) {
                    List<Long> ids = select(connection, cutoff, highWater);
                    if (ids.isEmpty()) {
                        if (!audited) recordAudit.run();
                        connection.rollback();
                        break;
                    }
                    String label = String.format(java.util.Locale.ROOT, "%06d", ++cohort);
                    Path partial = privateFile(job, label + ".sql.partial");
                    Path stderr = privateFile(job, label + ".stderr");
                    List<String> command = new ArrayList<>(List.of(executable, "--defaults-file=" + options,
                            "--single-transaction", "--skip-lock-tables", "--no-create-info", "--skip-triggers",
                            "--skip-extended-insert", "--complete-insert", "--quote-names", "--hex-blob"));
                    if (!mariaClient) command.add("--no-login-paths");
                    command.add("--where=" + dumpPredicate(ids, witness, owner)
                            + (requireTls ? " AND EXISTS (SELECT 1 FROM information_schema.SESSION_STATUS "
                            + "WHERE VARIABLE_NAME='Ssl_cipher' AND VARIABLE_VALUE<>'')" : ""));
                    command.add("--");
                    command.add(catalog);
                    command.add("log");
                    run(command, partial, stderr, connection);
                    verifyDump(partial, ids);
                    force(partial);
                    Path sql = job.resolve(label + ".sql");
                    Files.move(partial, sql, StandardCopyOption.ATOMIC_MOVE);
                    Path manifest = privateFile(job, label + ".manifest");
                    String digest;
                    try (var input = Files.newInputStream(sql)) {
                        MessageDigest sha = MessageDigest.getInstance("SHA-256");
                        byte[] buffer = new byte[8192];
                        for (int read; (read = input.read(buffer)) != -1;) sha.update(buffer, 0, read);
                        digest = HexFormat.of().formatHex(sha.digest());
                    }
                    Files.writeString(manifest, "sha256=" + digest + "\nthrough=" + date + "\nids=" + ids + "\n");
                    force(manifest);
                    force(job);
                    if (!audited) {
                        recordAudit.run();
                        audited = true;
                    }
                    int deleted = delete(connection, ids, cutoff);
                    if (deleted != ids.size()) throw new IOException("Audit cohort changed before deletion; the cohort was rolled back");
                    connection.commit();
                    removed = Math.addExact(removed, deleted);
                }
            }
        } catch (Exception failure) {
            // SQL/client errors can contain data or credentials. Keep their raw
            // output private, and expose only stage-independent recovery facts.
            LOGGER.error("Audit purge did not finish ({}); confirmed deletions={}, retained backups={}",
                    failure.getClass().getSimpleName(), removed, LogSafe.sanitize(job.toString()));
            if (failure instanceof IOException) LOGGER.error("Audit purge refusal: {}", LogSafe.sanitize(failure.getMessage()));
            throw new IOException("Audit purge did not finish. Completed backups were retained; consult the server log before retrying.");
        }
        LOGGER.info("Audit purge completed; removed={}, backups={}", removed, LogSafe.sanitize(job.toString()));
        return removed;
    }

    static void validateRetention(String configured, LocalDate through, LocalDate today) throws IOException {
        int minimum;
        try { minimum = Integer.parseInt(configured); }
        catch (NumberFormatException invalid) { throw new IOException("log.purge.minDays must be a nonnegative integer"); }
        if (minimum < 0 || through == null || java.time.temporal.ChronoUnit.DAYS.between(through, today) < minimum) {
            throw new IOException("Audit purge date is missing or within the configured retention period");
        }
    }

    private static List<Long> select(Connection connection, LocalDateTime cutoff, long highWater) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM log WHERE dateTime < ? AND id <= ? ORDER BY id LIMIT ? FOR UPDATE")) {
            query.setObject(1, cutoff);
            query.setLong(2, highWater);
            query.setInt(3, COHORT_SIZE);
            query.setQueryTimeout(30);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) ids.add(rows.getLong(1)); }
            return ids;
        }
    }

    // SQL text varies only by a count of literal '?' placeholders; every date and ID is bound.
    @SuppressWarnings("java:S2077")
    @SuppressFBWarnings(value = {"SQL_INJECTION_JDBC", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"},
            justification = "Only placeholder count changes; cutoff and every JDBC ID are bound parameters")
    private static int delete(Connection connection, List<Long> ids, LocalDateTime cutoff) throws SQLException {
        // Only placeholder structure is generated; every value is a JDBC bind.
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (PreparedStatement command = connection.prepareStatement(
                "DELETE FROM log WHERE dateTime < ? AND id IN (" + placeholders + ")")) {
            command.setObject(1, cutoff);
            for (int index = 0; index < ids.size(); index++) command.setLong(index + 2, ids.get(index));
            command.setQueryTimeout(30);
            return command.executeUpdate();
        }
    }

    static String dumpPredicate(List<Long> ids, String witness, long owner) {
        if (ids.isEmpty() || ids.size() > COHORT_SIZE || !witness.matches("carlos[.]audit-backup:[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Invalid internal audit cohort");
        }
        // mysqldump --where has no bind API. These are JDBC longs and a generated
        // UUID, never SQL fragments, clinical text, names or request parameters.
        String numericIds = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return "id IN (" + numericIds + ") AND IS_USED_LOCK('" + witness + "')=" + owner;
    }

    static void verifyDump(Path sql, List<Long> expected) throws IOException {
        Set<Long> remaining = new HashSet<>(expected);
        try (BufferedReader input = Files.newBufferedReader(sql, StandardCharsets.UTF_8)) {
            for (String line; (line = input.readLine()) != null;) {
                if (line.startsWith("INSERT INTO")) {
                    var match = INSERT.matcher(line);
                    if (!match.find() || !remaining.remove(Long.valueOf(match.group(1)))) {
                        throw new IOException("Dump did not contain the exact audit cohort");
                    }
                }
            }
        } catch (NumberFormatException invalid) {
            throw new IOException("Dump audit identifier was invalid");
        }
        if (!remaining.isEmpty()) throw new IOException("Dump omitted audit rows; nothing from this cohort was deleted");
    }

    static String clientOptions(Configuration live, HostAddress address, boolean maria) throws IOException {
        if (address.pipe != null || live.socketFactory() != null || live.keyStore() != null || live.trustStore() != null) {
            throw new IOException("Dump client cannot reproduce the configured socket/Java key-store policy");
        }
        StringBuilder file = new StringBuilder("[client]\ndefault-character-set=utf8mb4\n");
        option(file, "user", live.user());
        option(file, "password", live.password());
        if (address.localSocket != null) {
            option(file, "protocol", "SOCKET");
            option(file, "socket", address.localSocket);
        } else {
            option(file, "protocol", "TCP");
            option(file, "host", address.host);
            option(file, "port", Integer.toString(address.port));
        }
        SslMode mode = address.sslMode == null ? live.sslMode() : address.sslMode;
        if (maria) {
            if (mode == SslMode.VERIFY_CA) throw new IOException("The installed dump client cannot preserve CA-only TLS policy");
            option(file, "ssl", mode == SslMode.DISABLE ? "0" : "1");
            option(file, "ssl-verify-server-cert", mode == SslMode.VERIFY_FULL ? "1" : "0");
        } else {
            option(file, "ssl-mode", switch (mode) {
                case DISABLE -> "DISABLED";
                case ALLOW -> "PREFERRED";
                case TRUST -> "REQUIRED";
                case VERIFY_CA -> "VERIFY_CA";
                case VERIFY_FULL -> "VERIFY_IDENTITY";
            });
        }
        if (live.serverSslCert() != null) {
            Path ca = PathValidationUtils.resolveConfiguredFile(live.serverSslCert(), "dump TLS CA").toPath();
            if (!Files.isRegularFile(ca)) throw new IOException("Dump TLS CA must be a readable PEM file");
            option(file, "ssl-ca", ca.toString());
        }
        if (live.enabledSslProtocolSuites() != null) option(file, "tls-version", live.enabledSslProtocolSuites());
        if (live.enabledSslCipherSuites() != null) throw new IOException("Dump client cannot translate Java cipher-suite selections");
        return file.toString();
    }

    private static void option(StringBuilder output, String name, String value) throws IOException {
        if (value == null) value = "";
        if (value.indexOf('\0') >= 0) throw new IOException("Dump option cannot contain a NUL character");
        output.append(name).append("=\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")).append("\"\n");
    }

    private static String executable(String configured) throws IOException {
        if (configured.contains("/")) {
            Path path = PathValidationUtils.resolveConfiguredFile(configured, "audit dump executable").toPath();
            if (Files.isRegularFile(path) && Files.isExecutable(path)) return path.toString();
        } else {
            String component = PathValidationUtils.validatePathComponent(configured, "audit dump executable");
            for (String directory : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
                if (directory.isBlank()) continue;
                Path path = PathValidationUtils.resolveConfiguredFile(Path.of(directory, component).toString(), "audit dump executable").toPath();
                if (Files.isRegularFile(path) && Files.isExecutable(path)) return path.toString();
            }
        }
        throw new IOException("Configured audit dump executable is unavailable");
    }

    // FindSecBugs SQL_INJECTION_JDBC: this private helper has only fixed literal SQL callers.
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "Private helper accepts only fixed literal SQL at all call sites")
    private static long scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setQueryTimeout(5);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Audit purge scalar result was missing");
                return row.getLong(1);
            }
        }
    }

    private static boolean lock(Connection connection, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            query.setString(1, name);
            query.setQueryTimeout(5);
            try (ResultSet row = query.executeQuery()) { return row.next() && row.getInt(1) == 1; }
        }
    }

    private static void unlock(Connection connection, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            query.setString(1, name);
            query.setQueryTimeout(5);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || row.getInt(1) != 1) throw new SQLException("Audit purge lock release was not confirmed");
            }
        }
    }

    private static Path privateFile(Path directory, String name) throws IOException {
        return Files.createFile(directory.resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) { channel.force(true); }
    }

    // FindSecBugs COMMAND_INJECTION: direct argv, no shell; validated administrator executable, fixed options and internal numeric/UUID predicates.
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "Direct argv without a shell; validated administrator executable, fixed options, and internal JDBC longs/UUID predicates")
    private void run(List<String> command, Path stdout, Path stderr, Connection connection) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()); // nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder -- argv list only, no shell; executable resolves from administrator configuration, options are fixed, and predicates contain JDBC longs plus an internal UUID
        builder.environment().remove("MYSQL_PWD");
        builder.environment().remove("MYSQL_HOME");
        builder.environment().put("LC_ALL", "C");
        Process process = builder.start();
        long deadline = System.nanoTime() + commandTimeout.toNanos();
        long heartbeat = System.nanoTime();
        try (DumpProcess cleanup = new DumpProcess(process)) {
            while (!process.waitFor(1, TimeUnit.SECONDS)) {
                if (System.nanoTime() >= deadline) throw new IOException("Audit dump timed out; the cohort was retained");
                if (System.nanoTime() - heartbeat >= Duration.ofSeconds(5).toNanos()) {
                    // Keep an executing purge lease from being reclaimed as an
                    // abandoned idle connection while the external reader runs.
                    scalar(connection, "SELECT 1");
                    heartbeat = System.nanoTime();
                }
            }
            if (process.exitValue() != 0) throw new IOException("Audit dump exited unsuccessfully; the cohort was retained");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Audit dump was interrupted; the cohort was retained", interrupted);
        }
    }

    /** Try-with-resources preserves the primary failure when session cleanup also fails. */
    final class PurgeSession implements AutoCloseable {
        private final Connection connection;
        private final String serialLock;
        private final String witness;
        private final int isolation;
        boolean serialized;
        boolean witnessed;
        boolean transactional;
        boolean changedIsolation;

        PurgeSession(Connection connection, String serialLock, String witness) throws SQLException {
            this.connection = connection;
            this.serialLock = serialLock;
            this.witness = witness;
            this.isolation = connection.getTransactionIsolation();
        }

        @Override public void close() throws SQLException {
            try {
                if (transactional) connection.rollback();
                if (witnessed) unlock(connection, witness);
                if (serialized) unlock(connection, serialLock);
                if (transactional) connection.setAutoCommit(true);
                if (changedIsolation) connection.setTransactionIsolation(isolation);
            } catch (SQLException failure) {
                // Never return a session still holding a purge lock to circulation.
                try {
                    if (source instanceof org.apache.commons.dbcp2.BasicDataSource pool) pool.invalidateConnection(connection);
                    else connection.abort(Runnable::run);
                } catch (SQLException | RuntimeException invalidation) {
                    if (invalidation != failure) failure.addSuppressed(invalidation);
                    try { connection.abort(Runnable::run); }
                    catch (SQLException | RuntimeException abort) {
                        if (abort != failure) failure.addSuppressed(abort);
                    }
                }
                LOGGER.error("Audit purge session cleanup failed; connection invalidation/abort was attempted; inspect datasource health before retrying");
                throw failure;
            }
        }
    }

    /** Reaping errors are suppressed onto the original dump failure, never substituted for it. */
    static final class DumpProcess implements AutoCloseable {
        private final Process process;
        DumpProcess(Process process) { this.process = process; }

        @Override public void close() throws IOException {
            if (!process.isAlive()) return;
            process.destroyForcibly();
            boolean interrupted = Thread.interrupted();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    LOGGER.error("Audit dump could not be reaped after forced termination; inspect retained partial output");
                    throw new IOException("Audit dump could not be reaped; inspect retained partial output");
                }
            } catch (InterruptedException failure) {
                interrupted = true;
                LOGGER.error("Audit dump reaping was interrupted; inspect retained partial output");
                throw new IOException("Audit dump reaping was interrupted; inspect retained partial output", failure);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    /** Remove temporary credentials even if opening the database or releasing its session fails. */
    static final class PrivateCredentials implements AutoCloseable {
        private final Path options;
        PrivateCredentials(Path options) { this.options = options; }

        @Override public void close() throws IOException {
            try { Files.deleteIfExists(options); }
            catch (IOException failure) {
                LOGGER.error("Audit purge temporary credentials could not be removed; inspect protected file {}", LogSafe.sanitize(options.toString()));
                throw new IOException("Audit purge temporary credentials could not be removed; inspect the protected job directory", failure);
            }
        }
    }
}
