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

package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveArtifactCensusLoader;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveEnvelope;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveKeyring;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveKeyringException;
import io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveKeyringParser;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.keyring;
import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.syntheticKeyBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Startup behaviour of the outbound email archive keyring (#3448): a fresh install creates it, a
 * lost keyring over encrypted archives stops startup, the acknowledge-loss override, rotation, and
 * that no key material reaches a log line.
 */
@Tag("unit")
@DisplayName("OutboundEmailArchiveKeyringService (#3448)")
class OutboundEmailArchiveKeyringServiceUnitTest {

    private static final String ARCHIVE_DDL = "CREATE TABLE outboundEmailArchive (id INT PRIMARY KEY, documentNo INT)";
    private static final String DOCUMENT_DDL = "CREATE TABLE document (document_no INT PRIMARY KEY, docfilename VARCHAR(255))";

    @TempDir
    Path configDir;

    @TempDir
    Path documentDir;

    private JdbcDataSource dataSource;
    private Connection keeper;
    private Path keyringFile;
    private LogCapture serviceLog;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:archive_keyring_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL");
        dataSource.setUser("sa");
        // Keeps the in-memory database alive while the code under test opens and closes connections.
        keeper = dataSource.getConnection();
        // Canonical, as the service reports it: a temp directory may sit behind a symlink.
        keyringFile = configDir.toRealPath().resolve("carlos" + OutboundEmailArchiveKeyringService.DEFAULT_KEYRING_FILE_SUFFIX);
        serviceLog = LogCapture.forLogger(OutboundEmailArchiveKeyringService.class);
    }

    @AfterEach
    void tearDown() throws SQLException {
        serviceLog.close();
        keeper.close();
    }

    @Nested
    @DisplayName("a missing keyring")
    class MissingKeyring {

        @Test
        void shouldCreateAnOwnerOnlyKeyring_onAFreshInstall() throws Exception {
            withArchiveTables();

            OutboundEmailArchiveKeyring created = resolve(settings(false, null));

            assertThat(created.currentKeyId()).isEqualTo(1);
            assertThat(keyringFile).exists();
            assertThat(parseFile().encodedKey(1)).isEqualTo(created.encodedKey(1));
            assumeThat(keyringFile.getFileSystem().supportedFileAttributeViews()).contains("posix");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(keyringFile))).isEqualTo("rw-------");
            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith("Created the outbound email archive keyring at"));
        }

        @Test
        void shouldTolerateAMissingArchiveTable_asAFreshInstall() {
            // No tables at all: the schema before Flyway has created outboundEmailArchive.
            OutboundEmailArchiveKeyring created = resolve(settings(false, null));

            assertThat(created.currentKeyId()).isEqualTo(1);
            assertThat(keyringFile).exists();
        }

        @Test
        void shouldCreateTheKeyring_whenEveryArchiveIsLegacyPlaintext() throws Exception {
            withArchiveTables();
            archiveRow(1, "legacy-1.eml", "Subject: synthetic legacy\r\n\r\nplaintext".getBytes(StandardCharsets.US_ASCII));

            resolve(settings(false, null));

            assertThat(keyringFile).exists();
        }

        @Test
        void shouldRefuseToStart_whenEncryptedArchivesExist() throws Exception {
            withArchiveTables();
            archiveRow(1, "legacy-1.eml", "Subject: plaintext".getBytes(StandardCharsets.US_ASCII));
            archiveRow(2, "sealed-2.eml", sealedArtifact(keyring(3, 3)));
            OutboundEmailArchiveKeyringService service = service(settings(false, null));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessage("Outbound email archive keyring " + keyringFile + " is missing, but archived emails"
                            + " in the database are encrypted with it (2 archived emails; encrypted artifacts found)."
                            + " Refusing to start: a new keyring cannot decrypt them. Fix: restore the keyring file"
                            + " from backup to " + keyringFile + " (or point email.archive.keyring.file or"
                            + " CARLOS_OUTBOUND_EMAIL_ARCHIVE_KEYRING_FILE at it), then restart. Only if the keyring"
                            + " is lost for good: set email.archive.keyring.acknowledge_loss=true and restart. CARLOS"
                            + " then creates a new keyring, and every archived email encrypted with the old one stays"
                            + " unreadable.");
            assertThat(keyringFile).doesNotExist();
            assertThat(serviceLog.events()).filteredOn(e -> e.getLevel().name().equals("ERROR")).hasSize(1);
        }

        @Test
        void shouldRefuseToStart_whenAnArchiveFileCannotBeChecked() throws Exception {
            withArchiveTables();
            insertRow(1, "not-on-disk.eml");
            OutboundEmailArchiveKeyringService service = service(settings(false, null));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageContaining("could not confirm that no archived email is encrypted with it (1 archived"
                            + " emails, 1 of them could not be checked")
                    .hasMessageContaining("restore the keyring file from backup");
            assertThat(keyringFile).doesNotExist();
        }

        @Test
        void shouldRefuseToStart_whenTheDatabaseCannotBeRead() throws Exception {
            DataSource unreachable = mock(DataSource.class);
            when(unreachable.getConnection()).thenThrow(new SQLException("synthetic driver text", "08001", 0));
            OutboundEmailArchiveKeyringService service = new OutboundEmailArchiveKeyringService(
                    settings(false, null), new OutboundEmailArchiveArtifactCensusLoader(unreachable));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageContaining("could not check the database for archived emails encrypted with it"
                            + " (could not read outboundEmailArchive: SQLState 08001, error 0, java.sql.SQLException)")
                    .hasMessageContaining("If the database could not be reached, fix that")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("synthetic driver text"));
            assertThat(keyringFile).doesNotExist();
        }

        @Test
        void shouldCreateANewKeyringAboveTheLostKeyIds_whenLossIsAcknowledged() throws Exception {
            withArchiveTables();
            archiveRow(1, "sealed-1.eml", sealedArtifact(keyring(1, 1)));
            archiveRow(2, "sealed-2.eml", sealedArtifact(keyring(3, 1, 3)));

            OutboundEmailArchiveKeyring created = resolve(settings(true, null));

            // Key 4, not 1: the lost keys 1 and 3 can still be merged back if the old keyring turns up.
            assertThat(created.currentKeyId()).isEqualTo(4);
            assertThat(parseFile().keyIds()).containsExactly(4);
            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith(
                    "email.archive.keyring.acknowledge_loss is set: created a new outbound email archive keyring at "
                            + keyringFile + " (key 4) although 2 archived emails exist"));
        }

        @Test
        void shouldStartAtARandomHighKeyId_whenLossIsAcknowledgedButTheDatabaseCannotBeRead() throws Exception {
            DataSource unreachable = mock(DataSource.class);
            when(unreachable.getConnection()).thenThrow(new SQLException("synthetic driver text", "08001", 0));
            OutboundEmailArchiveKeyringService service = new OutboundEmailArchiveKeyringService(
                    settings(true, null), new OutboundEmailArchiveArtifactCensusLoader(unreachable));

            OutboundEmailArchiveKeyring created = service.resolveKeyring();

            assertRandomHighKeyId(created.currentKeyId());
            assertThat(parseFile().keyIds()).containsExactly(created.currentKeyId());
            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith("The check for archived emails encrypted"
                    + " with the lost outbound email archive keyring was incomplete, so the new keyring at "
                    + keyringFile + " starts at a random key id, " + created.currentKeyId() + ","));
        }

        @Test
        void shouldStartAtARandomHighKeyId_whenLossIsAcknowledgedButNoArchiveFileCanBeRead() throws Exception {
            withArchiveTables();
            // DOCUMENT_DIR not mounted: rows exist, their files do not.
            insertRow(1, "sealed-under-a-lost-key-1.eml");
            insertRow(2, "sealed-under-a-lost-key-2.eml");

            OutboundEmailArchiveKeyring created = resolve(settings(true, null));

            assertRandomHighKeyId(created.currentKeyId());
        }

        @Test
        void shouldStartAboveTheHighestKeyId_whenLossIsAcknowledgedAndEveryArchiveWasRead() throws Exception {
            withArchiveTables();
            archiveRow(1, "sealed-1.eml", sealedArtifact(keyring(7, 7)));
            archiveRow(2, "legacy-2.eml", "Subject: plaintext".getBytes(StandardCharsets.US_ASCII));

            OutboundEmailArchiveKeyring created = resolve(settings(true, null));

            assertThat(created.currentKeyId()).isEqualTo(8);
            assertThat(serviceLog.messages()).noneMatch(m -> m.contains("starts at a random key id"));
        }

        @Test
        void shouldNeverReplaceAKeyringCreatedMeanwhile_andLoadThatOneInstead() throws Exception {
            // Stands in for another process winning the race after the existence check.
            OutboundEmailArchiveArtifactCensusLoader racing = new OutboundEmailArchiveArtifactCensusLoader(dataSource) {
                @Override
                public Census load(String documentDirectory, Scan scan) {
                    try {
                        writeKeyring(keyring(5, 5));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return super.load(documentDirectory, scan);
                }
            };

            OutboundEmailArchiveKeyring loaded = new OutboundEmailArchiveKeyringService(settings(false, null), racing)
                    .resolveKeyring();

            assertThat(loaded.currentKeyId()).isEqualTo(5);
            assertThat(parseFile().encodedKey(5)).isEqualTo(keyring(5, 5).encodedKey(5));
            try (var leftovers = Files.list(configDir)) {
                assertThat(leftovers.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".tmp"));
            }
        }

        @Test
        void shouldWarnThatTheOverrideWasUnused_onAFreshInstall() {
            resolve(settings(true, null));

            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith(
                    "email.archive.keyring.acknowledge_loss is set but no archived email was encrypted"));
        }
    }

    @Nested
    @DisplayName("an existing keyring")
    class ExistingKeyring {

        @Test
        void shouldLoadIt_withoutRewritingTheFile() throws Exception {
            writeKeyring(keyring(2, 1, 2));
            byte[] before = Files.readAllBytes(keyringFile);

            OutboundEmailArchiveKeyring loaded = resolve(settings(false, null));

            assertThat(loaded.currentKeyId()).isEqualTo(2);
            assertThat(loaded.keyIds()).containsExactly(1, 2);
            assertThat(Files.readAllBytes(keyringFile)).isEqualTo(before);
        }

        @Test
        void shouldStillStart_whenTheDatabaseCannotBeReadForTheNewestKeyCheck() throws Exception {
            writeKeyring(keyring(1, 1));
            DataSource unreachable = mock(DataSource.class);
            when(unreachable.getConnection()).thenThrow(new SQLException("synthetic driver text", "08001", 0));

            OutboundEmailArchiveKeyring loaded = new OutboundEmailArchiveKeyringService(settings(false, null),
                    new OutboundEmailArchiveArtifactCensusLoader(unreachable)).resolveKeyring();

            assertThat(loaded.currentKeyId()).isEqualTo(1);
        }

        @Test
        void shouldReportAnError_whenTheNewestArchiveUsesAKeyTheKeyringLacks() throws Exception {
            withArchiveTables();
            archiveRow(1, "sealed-1.eml", sealedArtifact(keyring(1, 1)));
            archiveRow(2, "sealed-2.eml", sealedArtifact(keyring(2, 1, 2)));
            // An old backup copy: key 2 was added after it was taken.
            writeKeyring(keyring(1, 1));

            OutboundEmailArchiveKeyring loaded = resolve(settings(false, null));

            assertThat(loaded.currentKeyId()).isEqualTo(1);
            assertThat(serviceLog.events())
                    .filteredOn(e -> e.getLevel().name().equals("ERROR"))
                    .extracting(e -> e.getMessage().getFormattedMessage())
                    .containsExactly("Outbound email archive keyring " + keyringFile + " does not hold key 2, which"
                            + " encrypted the most recent encrypted archived email. Archived emails encrypted with key 2"
                            + " cannot be read. This keyring may be an out-of-date copy: restore the newest keyring file"
                            + " from backup, then restart.");
        }

        @Test
        void shouldReportNothing_whenTheNewestArchiveKeyIsInTheKeyring() throws Exception {
            withArchiveTables();
            archiveRow(1, "sealed-1.eml", sealedArtifact(keyring(1, 1)));
            writeKeyring(keyring(1, 1));

            resolve(settings(false, null));

            assertThat(serviceLog.events()).noneMatch(e -> e.getLevel().name().equals("ERROR"));
        }

        @Test
        void shouldWarnThatTheOverrideHasNoEffect_whileAKeyringExists() throws Exception {
            writeKeyring(keyring(1, 1));

            resolve(settings(true, null));

            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith(
                    "email.archive.keyring.acknowledge_loss is set but has no effect"));
        }

        @Test
        void shouldRefuseToStartAndLeaveTheFileAlone_whenTheKeyringIsMalformed() throws Exception {
            byte[] damaged = "format=1\ncurrent=2\nkey.1=AAAA\n".getBytes(StandardCharsets.US_ASCII);
            Files.write(keyringFile, damaged);
            // Even with the override: an existing keyring file is never replaced.
            OutboundEmailArchiveKeyringService service = service(settings(true, null));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessage("Outbound email archive keyring " + keyringFile + " is not a valid keyring (line 3:"
                            + " key.1 is not a Base64-encoded 32-byte key). Refusing to start. Fix: restore the keyring"
                            + " file from backup, then restart. Do not replace it with a new keyring: archived emails"
                            + " encrypted with it would become unreadable.");
            assertThat(Files.readAllBytes(keyringFile)).isEqualTo(damaged);
        }

        @Test
        void shouldRefuseToStart_whenTheKeyringCannotBeRead() throws Exception {
            assumeThat(System.getProperty("user.name")).as("root reads any file").isNotEqualTo("root");
            assumeThat(keyringFile.getFileSystem().supportedFileAttributeViews()).contains("posix");
            writeKeyring(keyring(1, 1));
            Files.setPosixFilePermissions(keyringFile, PosixFilePermissions.fromString("---------"));
            OutboundEmailArchiveKeyringService service = service(settings(false, null));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageContaining("exists but could not be read (AccessDeniedException)");
        }

        @Test
        void shouldRefuseToStart_whenThePathIsADirectory() throws Exception {
            Files.createDirectory(keyringFile);
            OutboundEmailArchiveKeyringService service = service(settings(false, null));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageContaining("is not a usable file path");
        }

        @Test
        void shouldRestrictTheKeyringToItsOwner_whenGroupOrOthersCanReadIt() throws Exception {
            assumeThat(keyringFile.getFileSystem().supportedFileAttributeViews()).contains("posix");
            writeKeyring(keyring(1, 1));
            Files.setPosixFilePermissions(keyringFile, PosixFilePermissions.fromString("rw-r--r--"));

            resolve(settings(false, null));

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(keyringFile))).isEqualTo("rw-------");
        }
    }

    @Nested
    @DisplayName("rotation")
    class Rotation {

        @Test
        void shouldAddTheNewKeyAndKeepTheOldOne_whenRotateToNamesANewKey() throws Exception {
            writeKeyring(keyring(1, 1));

            OutboundEmailArchiveKeyring rotated = resolve(settings(false, "2"));

            OutboundEmailArchiveKeyring onDisk = parseFile();
            assertThat(rotated.currentKeyId()).isEqualTo(2);
            assertThat(onDisk.currentKeyId()).isEqualTo(2);
            assertThat(onDisk.keyIds()).containsExactly(1, 2);
            assertThat(onDisk.encodedKey(1)).isEqualTo(keyring(1, 1).encodedKey(1));
            assertThat(onDisk.encodedKey(2)).isEqualTo(rotated.encodedKey(2));
            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith(
                    "Rotated the outbound email archive keyring at " + keyringFile + ": key 2 now encrypts"));
        }

        @Test
        void shouldKeepIdenticalKeyMaterial_whenTwoServicesRotateTogether() throws Exception {
            writeKeyring(keyring(1, 1));
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return resolve(settings(false, "2"));
                });
                var second = executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return resolve(settings(false, "2"));
                });
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                OutboundEmailArchiveKeyring firstResult = first.get(10, TimeUnit.SECONDS);
                OutboundEmailArchiveKeyring secondResult = second.get(10, TimeUnit.SECONDS);
                assertThat(firstResult.encodedKey(2)).isEqualTo(secondResult.encodedKey(2));
                assertThat(parseFile().encodedKey(2)).isEqualTo(firstResult.encodedKey(2));
                assertThat(parseFile().encodedKey(1)).isEqualTo(keyring(1, 1).encodedKey(1));
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }

        @Test
        void shouldReloadTheKeyring_whenAnotherProcessRotatedAfterInitialLoad() throws Exception {
            OutboundEmailArchiveKeyring staleSnapshot = keyring(1, 1);
            writeKeyring(keyring(2, 1, 2));
            OutboundEmailArchiveKeyringService service = service(settings(false, "2"));

            OutboundEmailArchiveKeyring resolved = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    service, "rotateIfRequested", keyringFile, keyringFile.toString(), staleSnapshot);

            assertThat(resolved.encodedKey(2)).isEqualTo(keyring(2, 1, 2).encodedKey(2));
            assertThat(parseFile().encodedKey(2)).isEqualTo(resolved.encodedKey(2));
        }

        @Test
        void shouldRefuseToRotate_whenAnArchivedFileCannotBeChecked() throws Exception {
            withArchiveTables();
            archiveRow(1, "missing.eml", sealedArtifact(keyring(2, 2)));
            Files.delete(documentDir.resolve("missing.eml"));
            writeKeyring(keyring(1, 1));
            byte[] before = Files.readAllBytes(keyringFile);

            assertThatThrownBy(() -> resolve(settings(false, "2")))
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageContaining("1 archived emails could not be checked");
            assertThat(Files.readAllBytes(keyringFile)).isEqualTo(before);
        }

        @Test
        void shouldTreatUnknownEnvelopeKeyAsUncheckable_whenHeaderIsDamaged() throws Exception {
            withArchiveTables();
            byte[] damaged = sealedArtifact(keyring(2, 2));
            damaged[8] = 99; // Unknown format; the marker still identifies encrypted data.
            archiveRow(1, "damaged.eml", damaged);
            var census = new OutboundEmailArchiveArtifactCensusLoader(dataSource)
                    .load(documentDir.toString(), OutboundEmailArchiveArtifactCensusLoader.Scan.ALL);
            assertThat(census.encryptedFound()).isTrue();
            assertThat(census.complete()).isFalse();
            assertThat(census.uncheckable()).isEqualTo(1);

            OutboundEmailArchiveKeyring recovered = resolve(settings(true, null));
            assertThat(recovered.currentKeyId()).isGreaterThanOrEqualTo(
                    OutboundEmailArchiveKeyringService.RANDOM_KEY_ID_FLOOR);
        }

        @Test
        void shouldChangeNothing_whenTheRequestedKeyIsAlreadyCurrent() throws Exception {
            writeKeyring(keyring(1, 1));
            resolve(settings(false, "2"));
            byte[] afterFirstStart = Files.readAllBytes(keyringFile);

            resolve(settings(false, "2"));

            assertThat(Files.readAllBytes(keyringFile)).isEqualTo(afterFirstStart);
        }

        @Test
        void shouldIgnoreARequestToGoBack_toAnOlderKey() throws Exception {
            writeKeyring(keyring(3, 1, 3));
            byte[] before = Files.readAllBytes(keyringFile);

            OutboundEmailArchiveKeyring loaded = resolve(settings(false, "2"));

            assertThat(loaded.currentKeyId()).isEqualTo(3);
            assertThat(Files.readAllBytes(keyringFile)).isEqualTo(before);
            assertThat(serviceLog.messages()).anyMatch(m -> m.startsWith("email.archive.keyring.rotate_to=2 is ignored"));
        }

        @Test
        void shouldRefuseToRotate_whenAnArchiveAlreadyUsesThatKeyIdOrAbove() throws Exception {
            withArchiveTables();
            archiveRow(1, "sealed-1.eml", sealedArtifact(keyring(1, 1)));
            archiveRow(2, "sealed-3.eml", sealedArtifact(keyring(3, 1, 3)));
            writeKeyring(keyring(1, 1));
            byte[] before = Files.readAllBytes(keyringFile);
            OutboundEmailArchiveKeyringService service = service(settings(false, "2"));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessage("Could not rotate the outbound email archive keyring at " + keyringFile + " to key 2:"
                            + " archived emails are already encrypted with key ids [3], which this keyring does not"
                            + " hold. It looks like an out-of-date copy, and a new key 2 could reuse an id already in"
                            + " use. Refusing to start. Fix: restore the newest keyring file from backup, then restart."
                            + " Only if those keys are lost for good: set email.archive.keyring.rotate_to above 3 and"
                            + " restart.");
            assertThat(Files.readAllBytes(keyringFile)).isEqualTo(before);
        }

        @Test
        void shouldRotate_whenOnlyKeysBelowTheTargetAreMissing() throws Exception {
            withArchiveTables();
            archiveRow(1, "sealed-1.eml", sealedArtifact(keyring(1, 1)));
            archiveRow(2, "sealed-2.eml", sealedArtifact(keyring(2, 2)));
            // Key 1 is lost, but nothing uses 3 or above: key 3 cannot reuse a lost id.
            writeKeyring(keyring(2, 2));

            OutboundEmailArchiveKeyring rotated = resolve(settings(false, "3"));

            assertThat(rotated.currentKeyId()).isEqualTo(3);
            assertThat(parseFile().keyIds()).containsExactly(2, 3);
        }

        @Test
        void shouldRefuseToRotate_whenTheDatabaseCannotBeChecked() throws Exception {
            writeKeyring(keyring(1, 1));
            DataSource unreachable = mock(DataSource.class);
            when(unreachable.getConnection()).thenThrow(new SQLException("synthetic driver text", "08001", 0));
            OutboundEmailArchiveKeyringService service = new OutboundEmailArchiveKeyringService(
                    settings(false, "2"), new OutboundEmailArchiveArtifactCensusLoader(unreachable));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageContaining("could not read the database to check that no archived email already uses"
                            + " that key id (SQLState 08001")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("synthetic driver text"));
        }

        @Test
        void shouldRotateToAHandAddedKey_withoutScanningTheArchive() throws Exception {
            writeKeyring(keyring(1, 1, 2));
            DataSource untouched = mock(DataSource.class);

            OutboundEmailArchiveKeyring rotated = new OutboundEmailArchiveKeyringService(settings(false, "2"),
                    new OutboundEmailArchiveArtifactCensusLoader(untouched) {
                        @Override
                        public Census load(String documentDirectory, Scan scan) {
                            throw new AssertionError("no scan is needed when the key already exists");
                        }
                    }).resolveKeyring();

            assertThat(rotated.currentKeyId()).isEqualTo(2);
            assertThat(rotated.encodedKey(2)).isEqualTo(keyring(1, 1, 2).encodedKey(2));
        }

        @Test
        void shouldRefuseToStart_whenRotateToIsNotAKeyId() throws Exception {
            writeKeyring(keyring(1, 1));
            OutboundEmailArchiveKeyringService service = service(settings(false, "two"));

            assertThatThrownBy(service::resolveKeyring)
                    .isInstanceOf(OutboundEmailArchiveKeyringException.class)
                    .hasMessageStartingWith("email.archive.keyring.rotate_to must be a positive whole number, but is 'two'");
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        private static final String[] KEYS = {OutboundEmailArchiveKeyringService.KEYRING_FILE_PROPERTY,
                OutboundEmailArchiveKeyringService.ACKNOWLEDGE_LOSS_PROPERTY,
                OutboundEmailArchiveKeyringService.ROTATE_TO_PROPERTY, "DOCUMENT_DIR"};
        private final java.util.Map<String, Object> saved = new java.util.HashMap<>();

        @BeforeEach
        void saveProperties() {
            CarlosProperties properties = CarlosProperties.getInstance();
            for (String key : KEYS) {
                saved.put(key, properties.get(key));
                properties.remove(key);
            }
        }

        @AfterEach
        void restoreProperties() {
            CarlosProperties properties = CarlosProperties.getInstance();
            for (String key : KEYS) {
                if (saved.get(key) == null) {
                    properties.remove(key);
                } else {
                    properties.put(key, saved.get(key));
                }
            }
        }

        @Test
        void shouldPreferTheEnvironmentVariable_overTheProperty() {
            CarlosProperties.getInstance().setProperty(OutboundEmailArchiveKeyringService.KEYRING_FILE_PROPERTY,
                    "/from/property.keyring");

            OutboundEmailArchiveKeyringService.Settings settings = OutboundEmailArchiveKeyringService.Settings
                    .fromConfiguration(CarlosProperties.getInstance(), " /from/environment.keyring ", "carlos");

            assertThat(settings.keyringFile()).isEqualTo("/from/environment.keyring");
        }

        @Test
        void shouldUseTheProperty_whenTheEnvironmentVariableIsBlank() {
            CarlosProperties.getInstance().setProperty(OutboundEmailArchiveKeyringService.KEYRING_FILE_PROPERTY,
                    "/from/property.keyring");

            OutboundEmailArchiveKeyringService.Settings settings = OutboundEmailArchiveKeyringService.Settings
                    .fromConfiguration(CarlosProperties.getInstance(), "  ", "carlos");

            assertThat(settings.keyringFile()).isEqualTo("/from/property.keyring");
        }

        @Test
        void shouldDefaultToTheUsersHomeDirectory_whenNothingIsConfigured() {
            CarlosProperties.getInstance().setProperty(OutboundEmailArchiveKeyringService.ACKNOWLEDGE_LOSS_PROPERTY, "yes");
            CarlosProperties.getInstance().setProperty(OutboundEmailArchiveKeyringService.ROTATE_TO_PROPERTY, " 3 ");

            OutboundEmailArchiveKeyringService.Settings settings = OutboundEmailArchiveKeyringService.Settings
                    .fromConfiguration(CarlosProperties.getInstance(), null, "carlos2");

            // The context name keeps two CARLOS webapps in one Tomcat from sharing a keyring.
            assertThat(settings.keyringFile()).isEqualTo(System.getProperty("user.home") + java.io.File.separator
                    + "carlos2-outbound-email-archive.keyring");
            assertThat(settings.acknowledgeLoss()).isTrue();
            assertThat(settings.rotateTo()).isEqualTo("3");
        }
    }

    @ParameterizedTest
    @CsvSource({"/carlos,carlos", "/carlos2,carlos2", "'',ROOT", "/a/b c,a_b_c"})
    void shouldDeriveAFileNameSafeContextName_fromTheContextPath(String contextPath, String expected) {
        assertThat(OutboundEmailArchiveKeyringService.contextNameOf(contextPath)).isEqualTo(expected);
    }

    @Test
    void shouldNeverLogKeyMaterial_acrossCreateRotateAndRefuse() throws Exception {
        withArchiveTables();
        OutboundEmailArchiveKeyring created = resolve(settings(false, null));
        OutboundEmailArchiveKeyring rotated = resolve(settings(false, "2"));
        Files.write(keyringFile, ("format=1\ncurrent=1\nkey.1=" + syntheticKeyBase64(1) + "\nkey.1="
                + syntheticKeyBase64(2) + "\n").getBytes(StandardCharsets.US_ASCII));
        OutboundEmailArchiveKeyringService duplicate = service(settings(false, null));
        assertThatThrownBy(duplicate::resolveKeyring).isInstanceOf(OutboundEmailArchiveKeyringException.class);

        List<String> secrets = new ArrayList<>(List.of(syntheticKeyBase64(1), syntheticKeyBase64(2),
                Base64.getEncoder().encodeToString(created.encodedKey(1)),
                Base64.getEncoder().encodeToString(rotated.encodedKey(2))));
        assertThat(serviceLog.messages()).isNotEmpty();
        for (String message : serviceLog.messages()) {
            for (String secret : secrets) {
                assertThat(message).doesNotContain(secret);
            }
        }
    }

    // ------------------------------------------------------------------ fixtures

    private OutboundEmailArchiveKeyringService.Settings settings(boolean acknowledgeLoss, String rotateTo) {
        return new OutboundEmailArchiveKeyringService.Settings(keyringFile.toString(), acknowledgeLoss, rotateTo,
                documentDir.toString());
    }

    private static void assertRandomHighKeyId(int keyId) {
        assertThat(keyId).isBetween(OutboundEmailArchiveKeyringService.RANDOM_KEY_ID_FLOOR,
                OutboundEmailArchiveKeyringService.RANDOM_KEY_ID_BOUND - 1);
    }

    private OutboundEmailArchiveKeyringService service(OutboundEmailArchiveKeyringService.Settings settings) {
        return new OutboundEmailArchiveKeyringService(settings, new OutboundEmailArchiveArtifactCensusLoader(dataSource));
    }

    private OutboundEmailArchiveKeyring resolve(OutboundEmailArchiveKeyringService.Settings settings) {
        return service(settings).resolveKeyring();
    }

    private OutboundEmailArchiveKeyring parseFile() throws Exception {
        return OutboundEmailArchiveKeyringParser.parse(Files.readAllBytes(keyringFile));
    }

    private void writeKeyring(OutboundEmailArchiveKeyring keyring) throws Exception {
        Files.write(keyringFile, OutboundEmailArchiveKeyringParser.format(keyring));
    }

    private void withArchiveTables() throws SQLException {
        execute(ARCHIVE_DDL);
        execute(DOCUMENT_DDL);
    }

    private void archiveRow(int id, String fileName, byte[] stored) throws Exception {
        Files.write(documentDir.resolve(fileName), stored);
        insertRow(id, fileName);
    }

    private void insertRow(int id, String fileName) throws SQLException {
        try (PreparedStatement document = keeper.prepareStatement("INSERT INTO document VALUES (?, ?)");
             PreparedStatement archive = keeper.prepareStatement("INSERT INTO outboundEmailArchive VALUES (?, ?)")) {
            document.setInt(1, id);
            document.setString(2, fileName);
            document.executeUpdate();
            archive.setInt(1, id);
            archive.setInt(2, id);
            archive.executeUpdate();
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = keeper.createStatement()) {
            statement.execute(sql);
        }
    }

    private static byte[] sealedArtifact(OutboundEmailArchiveKeyring keyring) throws Exception {
        byte[] plaintext = "Subject: synthetic sealed\r\n\r\nNot a real patient.".getBytes(StandardCharsets.US_ASCII);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plaintext));
        return OutboundEmailArchiveEnvelope.seal(keyring,
                new OutboundEmailArchiveEnvelope.ArtifactContext(1, 1, "message/rfc822", hash, plaintext.length),
                plaintext);
    }
}
