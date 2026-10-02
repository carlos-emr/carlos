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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
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
 * file is opened and only its header bytes are read. The newest rows are checked first and a
 * {@link Scan#UNTIL_FIRST_ENCRYPTED} scan stops at the first encrypted artifact, so a server that has
 * lost its keyring is recognised after one small read. A whole scan happens when no archive is
 * encrypted (the one-off upgrade of a server holding only pre-#3448 plaintext archives), or when the
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

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM outboundEmailArchive";

    /** Deleted archives included: controlled deletion keeps the bytes, and they may be encrypted. */
    private static final String FILE_NAMES_SQL = """
            SELECT d.docfilename FROM outboundEmailArchive a
            LEFT JOIN document d ON d.document_no = a.documentNo
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

    /** @return the number of archive rows, or empty when this schema has no archive table */
    private static OptionalLong countArchives(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet result = statement.executeQuery()) {
            return OptionalLong.of(result.next() ? result.getLong(1) : 0);
        } catch (SQLException e) {
            if (e.getSQLState() != null && ABSENT_OBJECT_STATES.contains(e.getSQLState())) {
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
