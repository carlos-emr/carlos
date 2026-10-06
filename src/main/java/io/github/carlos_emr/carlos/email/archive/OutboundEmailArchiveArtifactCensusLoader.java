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

package io.github.carlos_emr.carlos.email.archive;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Reads which archived artifacts are encrypted, and with which key ids, for the archive keyring's
 * startup and rotation checks (#3448).
 *
 * <p>There is no database column recording which artifacts are encrypted (the key id lives in each
 * artifact's envelope header), so the answer comes from the stored files: every archive row's eDoc
 * file is opened and only its header bytes are read. ({@link #verifyNewest} is the exception: it
 * reads and decrypts a few whole recent files, in memory, to prove a loaded keyring's keys.) The
 * newest rows are checked first and a {@link Scan#UNTIL_FIRST_ENCRYPTED} scan stops at the first
 * encrypted artifact, so a server that has lost its keyring is recognised after one small read. A
 * whole scan happens when no archive is encrypted (the one-off upgrade of a server holding only
 * pre-#3448 plaintext archives), or when the
 * caller asks for {@link Scan#ALL} to learn every key id in use: after an acknowledged keyring loss,
 * and before a rotation generates a new key.</p>
 *
 * <p>A missing {@code outboundEmailArchive} table counts as no archives, as in the #3939 key check
 * (#4098, pending): the table does not exist before Flyway creates it. Any other database failure is
 * reported, never swallowed, so the caller can refuse rather than guess. Only {@code SELECT}s are run,
 * on a pooled connection that is left exactly as it was borrowed.</p>
 *
 * <p>Nothing read from a file is logged or kept beyond the marker comparison and the key id.
 * Failures are described by SQLState, vendor code and exception class only.</p>
 *
 * @since 2026-09-30
 */
public class OutboundEmailArchiveArtifactCensusLoader {

    private static final Logger logger = MiscUtils.getLogger();

    /** "No such table or column": the X/Open codes MariaDB reports, and H2's variants. */
    private static final Set<String> ABSENT_OBJECT_STATES = Set.of("42S02", "42S03", "42S04", "42S22");

    /** "No such table" (MariaDB's, and H2's variants) only: the trial treats a missing column as schema drift. */
    private static final Set<String> TABLE_ABSENT_STATES = Set.of("42S02", "42S03", "42S04");

    /** Rows per page of the older-rows search, so a large archive is never held in memory at once. */
    public static final int OLDER_ROWS_PAGE = 500;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM outboundEmailArchive";

    /** Deleted archives included: controlled deletion keeps the bytes, and they may be encrypted. */
    private static final String FILE_NAMES_SQL = """
            SELECT d.docfilename FROM outboundEmailArchive a
            LEFT JOIN document d ON d.document_no = a.documentNo
            ORDER BY a.id DESC
            """;

    /** The newest archive rows with what their envelopes are bound to, for the startup trial open. */
    private static final String TRIAL_SQL = """
            SELECT a.id, d.docfilename, a.emailLogId, a.demographicNo, a.contentType, a.sha256Hash, a.byteSize
            FROM outboundEmailArchive a
            LEFT JOIN document d ON d.document_no = a.documentNo
            ORDER BY a.id DESC
            """;

    /** Largest artifact the startup trial open reads: the archive's own 50 MiB read limit. */
    static final long MAX_TRIAL_PLAINTEXT_BYTES = 50L * 1024 * 1024;

    /** {@link #TRIAL_SQL} below a given archive id, for the older-rows search. */
    private static final String TRIAL_PAGE_SQL = """
            SELECT a.id, d.docfilename, a.emailLogId, a.demographicNo, a.contentType, a.sha256Hash, a.byteSize
            FROM outboundEmailArchive a
            LEFT JOIN document d ON d.document_no = a.documentNo
            WHERE a.id < ?
            ORDER BY a.id DESC
            """;

    private final DataSource dataSource;

    /** @param dataSource the application data source */
    public OutboundEmailArchiveArtifactCensusLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** How far to read. */
    public enum Scan {
        /** Stop at the first encrypted artifact: enough to decide whether a keyring was lost. */
        UNTIL_FIRST_ENCRYPTED,
        /** Read every artifact's header, to learn every key id in use. */
        ALL
    }

    /**
     * What the check found. When {@link #failure()} is non-null the database could not be read and
     * the other fields mean nothing.
     *
     * @param archiveRows    archive rows, deleted ones included
     * @param encryptedFound true once an envelope marker was seen
     * @param uncheckable    rows whose file was missing, unreadable, or not a valid eDoc name, among
     *                       those scanned
     * @param keyIdsFound    key ids read from envelope headers among those scanned, ascending
     * @param failure        sanitized description of a database failure, or null
     */
    public record Census(long archiveRows, boolean encryptedFound, long uncheckable, SortedSet<Integer> keyIdsFound,
                         String failure) {

        public Census {
            keyIdsFound = Collections.unmodifiableSortedSet(new TreeSet<>(keyIdsFound));
        }

        static Census empty() {
            return new Census(0, false, 0, new TreeSet<>(), null);
        }

        static Census failed(String failure) {
            return new Census(0, false, 0, new TreeSet<>(), failure);
        }

        /** @return true when nothing can be lost: no archive is encrypted and every one was checked */
        public boolean nothingToOrphan() {
            return failure == null && !encryptedFound && uncheckable == 0;
        }

        /**
         * @return true when the database was read and every scanned file was checked, so the key ids
         *         found are all the key ids in use (for an {@link Scan#ALL} scan)
         */
        public boolean complete() {
            return failure == null && uncheckable == 0;
        }

        /** @return the highest key id found, or 0 when none was */
        public int highestKeyId() {
            return keyIdsFound.isEmpty() ? 0 : keyIdsFound.last();
        }
    }

    /**
     * Runs the check.
     *
     * @param documentDirectory the configured {@code DOCUMENT_DIR}; may be null or blank, in which
     *        case no file can be checked
     * @param scan              how far to read
     * @return the census; never throws
     */
    public Census load(String documentDirectory, Scan scan) {
        try (Connection connection = dataSource.getConnection()) {
            OptionalLong rows = countArchives(connection);
            if (rows.isEmpty()) {
                logger.debug("Archive keyring check: outboundEmailArchive is not in this schema; counted as empty");
                return Census.empty();
            }
            if (rows.getAsLong() == 0) {
                return Census.empty();
            }
            return scanFiles(connection, rows.getAsLong(), resolveDirectory(documentDirectory), scan);
        } catch (SQLException | RuntimeException e) {
            return Census.failed(describe(e));
        }
    }

    /**
     * Finds the key id of the most recent encrypted artifact, reading at most {@code maxFiles} of the
     * newest archive files. A cheap startup probe: it answers "is the newest key in use in this
     * keyring?" without a full scan, even on a server holding many plaintext archives.
     *
     * @param documentDirectory the configured {@code DOCUMENT_DIR}; may be null or blank
     * @param maxFiles          how many of the newest archive files to read at most
     * @return that key id, or empty when none of those files is an envelope with a readable header,
     *         or the database could not be read (logged at DEBUG; the full checks report failures)
     */
    public OptionalInt newestEncryptedKeyId(String documentDirectory, int maxFiles) {
        File directory = resolveDirectory(documentDirectory);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FILE_NAMES_SQL)) {
            statement.setMaxRows(maxFiles);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Inspection inspection = inspect(directory, result.getString(1));
                    if (inspection.state() == FileState.ENCRYPTED && inspection.keyId() > 0) {
                        return OptionalInt.of(inspection.keyId());
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            String failure = describe(e);
            logger.debug("Archive keyring check: newest archived key id not read ({})", failure);
        }
        return OptionalInt.empty();
    }

    /** What {@link #verifyNewest} found. */
    public enum TrialOutcome {
        /** An encrypted artifact opened with the keyring: its keys are the ones that sealed it. */
        VERIFIED,
        /** No encrypted artifact could be tried with a key the keyring holds. */
        NOTHING_TO_CHECK,
        /** Every encrypted artifact tried with a key the keyring holds failed authentication. */
        WRONG_KEYS
    }

    /**
     * @param outcome      what the trial found
     * @param archiveId    the archive row that opened, for {@link TrialOutcome#VERIFIED}; otherwise 0
     * @param keyId        the key that opened it, for {@link TrialOutcome#VERIFIED}; otherwise 0
     * @param tried        artifacts that failed authentication with a key the keyring holds; with
     *                     {@link TrialOutcome#VERIFIED}, those newer than the one that opened
     * @param failedKeyIds the key ids of those artifacts
     * @param skipped      recent rows that could not be checked: the file missing, unreadable, the wrong
     *                     size or over {@link #MAX_TRIAL_PLAINTEXT_BYTES}, a damaged header, incomplete or
     *                     malformed metadata, or a provider fault. Not evidence about the keys either way.
     * @param failure      sanitized description of a database failure, or null
     */
    public record Trial(TrialOutcome outcome, long archiveId, int keyId, int tried, SortedSet<Integer> failedKeyIds,
                        int skipped, String failure) {

        public Trial {
            failedKeyIds = Collections.unmodifiableSortedSet(new TreeSet<>(failedKeyIds));
        }
    }

    /** One archive row, read before any whole file is opened so the connection is not held meanwhile. */
    private record TrialRow(long archiveId, String fileName, OutboundEmailArchiveEnvelope.ArtifactContext context) {
    }

    private enum TrialResult { OPENED, AUTHENTICATION_FAILED, SKIPPED }

    /** Running counts across the newest rows and, when needed, the deeper search. */
    private static final class Tally {
        private int tried;
        private int skipped;
        private boolean unheldKeySeen;
        private final TreeSet<Integer> failedKeyIds = new TreeSet<>();

        private Trial result(String failure) {
            return new Trial(tried == 0 ? TrialOutcome.NOTHING_TO_CHECK : TrialOutcome.WRONG_KEYS, 0, 0, tried,
                    failedKeyIds, skipped, failure);
        }
    }

    /**
     * Opens encrypted artifacts with {@code keyring}, one at a time and in memory, until one
     * authenticates. A keyring with the right key ids but different key material (one created on a
     * fresh start against the wrong database, by another server, or swapped in by hand) passes every
     * id check, and new archives would then be sealed under a second, different key for an id already
     * in use. This catches it before anything is written.
     *
     * <p>The newest {@code maxFiles} rows come first. When none of them could be tried (every encrypted
     * one uses a key this keyring lacks, or their files are missing or damaged), older rows are
     * searched, header by header, for up to {@code maxFiles} artifacts sealed with a key it holds, so
     * a foreign keyring is tried even then. That search only runs in those states, which are already
     * reported.</p>
     *
     * <p>Only an authentication failure with a key the keyring holds counts against the keyring. A
     * file that is missing, unreadable, the wrong size or too large, a damaged header, a provider fault,
     * or a row whose metadata the read path would refuse anyway, is counted as skipped: none of that
     * says anything about the keys. Nothing read is kept or logged, the decrypted bytes are cleared at
     * once, and no read-audit row is written, since no person reads the artifact.</p>
     *
     * @param documentDirectory the configured {@code DOCUMENT_DIR}; may be null or blank
     * @param keyring           the loaded keyring
     * @param maxFiles          how many of the newest rows to look at, and how many older candidates to
     *                          collect at most
     * @return {@link TrialOutcome#WRONG_KEYS} only when at least one artifact failed authentication and
     *         none opened. A missing archive table is nothing to check; any other database failure is
     *         nothing to check with {@link Trial#failure()} set.
     */
    public Trial verifyNewest(String documentDirectory, OutboundEmailArchiveKeyring keyring, int maxFiles) {
        File directory = resolveDirectory(documentDirectory);
        Tally tally = new Tally();
        List<TrialRow> newest = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(TRIAL_SQL)) {
            statement.setMaxRows(maxFiles);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    newest.add(trialRow(result));
                }
            }
        } catch (SQLException e) {
            // Only a missing table is a fresh install here; a missing column is schema drift, reported.
            // A pool or driver failure often has no SQLState at all; Set.of rejects a null lookup.
            String state = e.getSQLState();
            return state != null && TABLE_ABSENT_STATES.contains(state) ? tally.result(null) : tally.result(describe(e));
        } catch (RuntimeException e) {
            return tally.result(describe(e));
        }
        Trial opened = tryRows(directory, keyring, newest, tally);
        // Search further back when nothing among the newest could be tried: they all use keys this
        // keyring lacks, or their files were missing or damaged.
        if (opened != null || tally.tried > 0 || directory == null || newest.size() < maxFiles
                || (!tally.unheldKeySeen && tally.skipped == 0)) {
            return opened != null ? opened : tally.result(null);
        }
        List<TrialRow> older = new ArrayList<>();
        // Keyset pages below the oldest of the newest rows: bounded memory however large the archive.
        long below = newest.stream().mapToLong(TrialRow::archiveId).min().orElse(0);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(TRIAL_PAGE_SQL)) {
            statement.setMaxRows(OLDER_ROWS_PAGE);
            boolean more = below > 0;
            while (more && older.size() < maxFiles) {
                statement.setLong(1, below);
                int rowsInPage = 0;
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rowsInPage++;
                        TrialRow row = trialRow(result);
                        below = row.archiveId();
                        if (older.size() < maxFiles && holdsKeyOf(directory, keyring, row, tally)) {
                            older.add(row);
                        }
                    }
                }
                more = rowsInPage == OLDER_ROWS_PAGE;
            }
        } catch (SQLException | RuntimeException e) {
            return tally.result(describe(e));
        }
        opened = tryRows(directory, keyring, older, tally);
        return opened != null ? opened : tally.result(null);
    }

    /** @return whether the row's file is an envelope sealed with a key the keyring holds; header only */
    private static boolean holdsKeyOf(File directory, OutboundEmailArchiveKeyring keyring, TrialRow row, Tally tally) {
        try {
            Inspection inspection = inspect(directory, row.fileName());
            return inspection.state() == FileState.ENCRYPTED && inspection.keyId() > 0
                    && keyring.key(inspection.keyId()).isPresent();
        } catch (RuntimeException e) {
            // A platform path quirk or the like: the row is not evidence about the keys.
            tally.skipped++;
            return false;
        }
    }

    /** @return a VERIFIED trial for the first row that opens, or null; the tally records the rest */
    private static Trial tryRows(File directory, OutboundEmailArchiveKeyring keyring, List<TrialRow> rows,
                                 Tally tally) {
        for (TrialRow row : rows) {
            try {
                Trial opened = tryRow(directory, keyring, row, tally);
                if (opened != null) {
                    return opened;
                }
            } catch (RuntimeException e) {
                // A platform path quirk or the like: the row is not evidence about the keys.
                tally.skipped++;
            }
        }
        return null;
    }

    /** @return a VERIFIED trial when this row opens, otherwise null; the tally records the rest */
    private static Trial tryRow(File directory, OutboundEmailArchiveKeyring keyring, TrialRow row, Tally tally) {
        Inspection inspection = inspect(directory, row.fileName());
        if (inspection.state() == FileState.PLAINTEXT) {
            return null;
        }
        if (inspection.state() == FileState.UNCHECKABLE || inspection.keyId() <= 0) {
            tally.skipped++;
            return null;
        }
        if (keyring.key(inspection.keyId()).isEmpty()) {
            tally.unheldKeySeen = true;
            return null;
        }
        TrialResult result = row.context() == null
                ? TrialResult.SKIPPED
                : open(directory, row.fileName(), keyring, row.context());
        if (result == TrialResult.OPENED) {
            return new Trial(TrialOutcome.VERIFIED, row.archiveId(), inspection.keyId(), tally.tried,
                    tally.failedKeyIds, tally.skipped, null);
        }
        if (result == TrialResult.AUTHENTICATION_FAILED) {
            tally.tried++;
            tally.failedKeyIds.add(inspection.keyId());
        } else {
            tally.skipped++;
        }
        return null;
    }

    /** Reads one {@link #TRIAL_SQL} row, columns left to right. */
    private static TrialRow trialRow(ResultSet row) throws SQLException {
        long archiveId = row.getLong(1);
        String fileName = row.getString(2);
        long emailLogId = row.getLong(3);
        boolean emailLogMissing = row.wasNull();
        int demographicNo = row.getInt(4);
        boolean demographicMissing = row.wasNull();
        String contentType = row.getString(5);
        String sha256Hex = normalizedSha256(row.getString(6));
        long byteSize = row.getLong(7);
        boolean byteSizeMissing = row.wasNull();
        OutboundEmailArchiveEnvelope.ArtifactContext context = emailLogMissing || demographicMissing || byteSizeMissing
                || contentType == null || sha256Hex == null || byteSize < 0 || byteSize > MAX_TRIAL_PLAINTEXT_BYTES
                ? null
                : new OutboundEmailArchiveEnvelope.ArtifactContext(emailLogId, demographicNo, contentType, sha256Hex,
                        byteSize);
        return new TrialRow(archiveId, fileName, context);
    }

    /** The read path's normalization: 64 hex digits, trimmed and lower-cased; anything else is null. */
    private static String normalizedSha256(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() != 64) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.digit(trimmed.charAt(i), 16) < 0 || trimmed.charAt(i) > 'f') {
                return null;
            }
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static boolean absentTable(SQLException e) {
        return e.getSQLState() != null && ABSENT_OBJECT_STATES.contains(e.getSQLState());
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The stored eDoc filename is validated as one path component and resolved inside DOCUMENT_DIR; links are not followed.")
    private static TrialResult open(File directory, String fileName, OutboundEmailArchiveKeyring keyring,
                                    OutboundEmailArchiveEnvelope.ArtifactContext context) {
        byte[] stored = null;
        byte[] plaintext = null;
        try {
            String safeName = PathValidationUtils.validatePathComponent(fileName, "archive eDoc filename");
            Path path = new File(directory, safeName).toPath();
            long expected = context.byteSize() + OutboundEmailArchiveEnvelope.OVERHEAD_BYTES;
            // Sized first, so a wrong-size file is skipped without reading it; and a regular file only,
            // which narrows the window in which something swapped in (a FIFO, say) could block startup.
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() != expected) {
                return TrialResult.SKIPPED;
            }
            stored = new byte[(int) expected];
            try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                if (input.readNBytes(stored, 0, stored.length) != stored.length || input.read() != -1) {
                    return TrialResult.SKIPPED;
                }
            }
            plaintext = OutboundEmailArchiveEnvelope.open(keyring, context, stored);
            return TrialResult.OPENED;
        } catch (OutboundEmailArchiveEnvelopeException e) {
            return e.getReason() == OutboundEmailArchiveEnvelopeException.Reason.AUTHENTICATION_FAILED
                    ? TrialResult.AUTHENTICATION_FAILED
                    : TrialResult.SKIPPED;
        } catch (IOException | SecurityException e) {
            return TrialResult.SKIPPED;
        } finally {
            if (stored != null) {
                Arrays.fill(stored, (byte) 0);
            }
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    /** @return the number of archive rows, or empty when this schema has no archive table */
    private static OptionalLong countArchives(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet result = statement.executeQuery()) {
            return OptionalLong.of(result.next() ? result.getLong(1) : 0);
        } catch (SQLException e) {
            if (absentTable(e)) {
                return OptionalLong.empty();
            }
            throw e;
        }
    }

    private static Census scanFiles(Connection connection, long rows, File directory, Scan scan) throws SQLException {
        long uncheckable = 0;
        boolean encryptedFound = false;
        TreeSet<Integer> keyIds = new TreeSet<>();
        try (PreparedStatement statement = connection.prepareStatement(FILE_NAMES_SQL);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Inspection inspection = inspect(directory, result.getString(1));
                if (inspection.state() == FileState.UNCHECKABLE) {
                    uncheckable++;
                } else if (inspection.state() == FileState.ENCRYPTED) {
                    encryptedFound = true;
                    if (inspection.keyId() > 0) {
                        keyIds.add(inspection.keyId());
                    } else {
                        // The marker proves encryption, but the damaged header hides its key id.
                        uncheckable++;
                    }
                    if (scan == Scan.UNTIL_FIRST_ENCRYPTED) {
                        break;
                    }
                }
            }
        }
        return new Census(rows, encryptedFound, uncheckable, keyIds, null);
    }

    private enum FileState { PLAINTEXT, ENCRYPTED, UNCHECKABLE }

    /** @param keyId the envelope's key id; 0 when not encrypted or the header cannot be parsed */
    private record Inspection(FileState state, int keyId) {
        private static final Inspection PLAINTEXT = new Inspection(FileState.PLAINTEXT, 0);
        private static final Inspection UNCHECKABLE = new Inspection(FileState.UNCHECKABLE, 0);
    }

    // FindSecBugs PATH_TRAVERSAL_IN: the stored eDoc filename is validated as one path component and
    // resolved inside DOCUMENT_DIR, and links are not followed, as on the archive service's read path.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The stored eDoc filename is validated as one path component and resolved inside DOCUMENT_DIR; links are not followed.")
    private static Inspection inspect(File directory, String fileName) {
        if (directory == null || fileName == null || fileName.isBlank()) {
            return Inspection.UNCHECKABLE;
        }
        byte[] prefix;
        try {
            String safeName = PathValidationUtils.validatePathComponent(fileName, "archive eDoc filename");
            Path path = new File(directory, safeName).toPath();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return Inspection.UNCHECKABLE;
            }
            try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                prefix = input.readNBytes(OutboundEmailArchiveEnvelope.HEADER_BYTES);
            }
        } catch (IOException | SecurityException e) {
            return Inspection.UNCHECKABLE;
        }
        if (!OutboundEmailArchiveEnvelope.hasEnvelopeMagic(prefix)) {
            return Inspection.PLAINTEXT;
        }
        try {
            return new Inspection(FileState.ENCRYPTED, OutboundEmailArchiveEnvelope.readKeyId(prefix));
        } catch (OutboundEmailArchiveEnvelopeException e) {
            // Still an envelope, so still encrypted; only its key id is unknown.
            return new Inspection(FileState.ENCRYPTED, 0);
        }
    }

    private static File resolveDirectory(String documentDirectory) {
        if (documentDirectory == null || documentDirectory.isBlank()) {
            return null;
        }
        try {
            File directory = PathValidationUtils.resolveConfiguredDirectory(documentDirectory, "DOCUMENT_DIR");
            return directory.isDirectory() ? directory : null;
        } catch (SecurityException e) {
            return null;
        }
    }

    /** SQLState, vendor code and class only: driver messages can echo connection settings. */
    private static String describe(Exception failure) {
        if (failure instanceof SQLException sqlException) {
            return "SQLState " + LogSafe.sanitize(String.valueOf(sqlException.getSQLState()))
                    + ", error " + sqlException.getErrorCode()
                    + ", " + failure.getClass().getName();
        }
        return failure.getClass().getName();
    }
}
