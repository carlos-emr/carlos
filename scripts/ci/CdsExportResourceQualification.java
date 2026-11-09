/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDeliveryComposer;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPackageComposer;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPackageMembers;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportStagingService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.crypto.spec.SecretKeySpec;

/** Byte-layer qualification only: clinical readiness is tested by the installed CLI/browser workflow. */
public final class CdsExportResourceQualification {
    private static final long GIB = 1L << 30;
    private static final long MEMBER_BYTES = 4 * GIB + 65536;
    private static final int MEMBERS = 5;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long DEADLINE = System.nanoTime() + 2700_000_000_000L;

    private CdsExportResourceQualification() { }

    public static void main(String[] args) throws Exception {
        require(Runtime.version().feature() == 25, "JAVA25_REQUIRED");
        require(Runtime.getRuntime().maxMemory() <= (300L << 20), "BOUNDED_HEAP_REQUIRED");
        require(args.length == 3, "MODE_ROOT_PROOF_REQUIRED");
        Path root = Path.of(args[1]).toRealPath();
        require(Files.getPosixFilePermissions(root).equals(PosixFilePermissions.fromString("rwx------")), "PRIVATE_ROOT_REQUIRED");
        Map<String, Object> proof = switch (args[0]) {
            case "large" -> large(root);
            case "disk" -> disk(root);
            case "verify" -> recipient(root);
            default -> throw new IllegalArgumentException("UNKNOWN_MODE");
        };
        proof.put("maximumHeapBytes", Runtime.getRuntime().maxMemory());
        proof.put("byteLayerOnly", true);
        proof.put("status", "PASSED");
        write(Path.of(args[2]), proof);
        System.out.println("PASS export resource " + args[0]);
    }

    private static Map<String, Object> large(Path root) throws Exception {
        require(Files.getFileStore(root).getUsableSpace() >= 56 * GIB, "LARGE_RUNNER_STORAGE_REQUIRED");
        var store = store(root, 32 * GIB, GIB);
        byte[] block = new byte[65536];
        for (int i = 0; i < block.length; i++) block[i] = (byte) (i * 31 + 7);
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        var source = store.stage("SYNTHETIC_RESOURCE", output -> {
            for (long left = MEMBER_BYTES; left > 0;) {
                checkpoint();
                int count = (int) Math.min(block.length, left);
                output.write(block, 0, count);
                expected.update(block, 0, count);
                left -= count;
            }
        });
        String expectedHash = HexFormat.of().formatHex(expected.digest());
        require(source.plaintextSha256().equals(expectedHash), "SOURCE_HASH_DIFFERS");
        var archive = new CdsExportPackageComposer().compose(store, members(source, MEMBERS), 24 * GIB, 10,
                CdsExportResourceQualification::checkpoint);
        require(archive.expandedBytes() == MEMBER_BYTES * MEMBERS, "AGGREGATE_LENGTH_DIFFERS");
        try (var input = store.open(archive.archive())) {
            verifyZip(input, MEMBERS, MEMBER_BYTES, expectedHash, archive.archive().plaintextSha256());
        }
        Path plain = root.resolve("synthetic.zip");
        try (var input = store.open(archive.archive()); var output = Files.newOutputStream(plain, StandardOpenOption.CREATE_NEW)) {
            input.transferTo(output);
        }
        try (var zip = new ZipFile(plain.toFile())) {
            require(zip.size() == MEMBERS, "CENTRAL_DIRECTORY_COUNT_DIFFERS");
            for (int i = 0; i < MEMBERS; i++) {
                var entry = zip.getEntry(name(i));
                require(entry != null && entry.getSize() == MEMBER_BYTES, "ZIP64_LENGTH_DIFFERS");
            }
        } finally {
            Files.deleteIfExists(plain);
        }
        X509Certificate certificate;
        try (var input = Files.newInputStream(root.resolve("recipient.pem"))) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        String pin = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        var result = new LinkedHashMap<String, Object>();
        try (var input = store.open(archive.archive());
             var output = Files.newOutputStream(root.resolve("delivery.cms"), StandardOpenOption.CREATE_NEW)) {
            var delivery = new CdsExportDeliveryComposer().compose(input, output, certificate, pin,
                    archive.archive().plaintextBytes(), archive.archive().plaintextSha256(), Instant.now(), 24 * GIB,
                    CdsExportResourceQualification::checkpoint);
            result.put("ciphertextBytes", delivery.ciphertextBytes());
            result.put("ciphertextSha256", delivery.ciphertextSha256());
        }
        result.put("members", MEMBERS);
        result.put("memberBytes", MEMBER_BYTES);
        result.put("aggregateBytes", archive.expandedBytes());
        result.put("memberSha256", expectedHash);
        result.put("zipSha256", archive.archive().plaintextSha256());
        result.put("zipBytes", archive.archive().plaintextBytes());
        write(root.resolve("expected.json"), result);
        store.discardUnpublished(archive.archive());
        store.discardUnpublished(source);
        countBoundary(store);
        require(stageFiles(root) == 0, "LARGE_RUN_LEFT_STAGE_FILE");
        result.put("zip64EntriesVerified", 65537);
        result.put("independentZipReaders", 2);
        return result;
    }

    private static void countBoundary(CdsExportStagingService store) throws Exception {
        var empty = store.stage("EMPTY_RESOURCE", output -> { });
        var archive = new CdsExportPackageComposer().compose(store, members(empty, 65537), 64L << 20, 100000,
                CdsExportResourceQualification::checkpoint);
        try (var input = store.open(archive.archive())) {
            verifyZip(input, 65537, 0, empty.plaintextSha256(), archive.archive().plaintextSha256());
        }
        store.discardUnpublished(archive.archive());
        store.discardUnpublished(empty);
    }

    private static Map<String, Object> recipient(Path root) throws Exception {
        var expected = JSON.readTree(root.resolve("expected.json").toFile());
        require(expected.get("memberBytes").asLong() == MEMBER_BYTES && expected.get("members").asInt() == MEMBERS,
                "RECIPIENT_EXPECTATION_DIFFERS");
        verifyZip(System.in, MEMBERS, MEMBER_BYTES, expected.get("memberSha256").asText(), expected.get("zipSha256").asText());
        return new LinkedHashMap<>(Map.of("recipientDecryptionVerified", true, "aggregateBytes", MEMBER_BYTES * MEMBERS));
    }

    private static void verifyZip(InputStream source, int entries, long bytes, String hash, String zipHash) throws Exception {
        MessageDigest whole = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(source, whole); var zip = new ZipInputStream(input)) {
            byte[] buffer = new byte[65536];
            for (int i = 0; i < entries; i++) {
                var entry = zip.getNextEntry();
                require(entry != null && entry.getName().equals(name(i)) && entry.getSize() == bytes, "ZIP_ENTRY_DIFFERS");
                MessageDigest actual = MessageDigest.getInstance("SHA-256");
                long length = 0;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    checkpoint();
                    require(count > 0, "ZIP_READ_STALLED");
                    length += count;
                    actual.update(buffer, 0, count);
                }
                require(length == bytes && HexFormat.of().formatHex(actual.digest()).equals(hash), "ZIP_CONTENT_DIFFERS");
                zip.closeEntry();
            }
            require(zip.getNextEntry() == null, "UNEXPECTED_ZIP_ENTRY");
            // The standard reader stops at the central directory. Consume remaining underlying bytes
            // before accepting the exact ZIP digest or allowing the external decryptor to finish.
            input.transferTo(OutputStream.nullOutputStream());
        }
        require(HexFormat.of().formatHex(whole.digest()).equals(zipHash), "WHOLE_ZIP_DIFFERS");
    }

    private static Map<String, Object> disk(Path root) throws Exception {
        var fileStore = Files.getFileStore(root);
        require(fileStore.type().equals("tmpfs") && fileStore.getTotalSpace() == (32L << 20), "OWNED_32M_TMPFS_REQUIRED");
        long initial = fileStore.getUsableSpace();
        require(initial > (30L << 20), "EMPTY_TMPFS_REQUIRED");
        var reserve = store(root, 64L << 20, initial + 1);
        boolean reserveRefused = false;
        try {
            reserve.stage("RESERVE_FAILURE", output -> output.write(1));
        } catch (IOException failure) {
            require("CDS_EXPORT_DISK_RESERVE".equals(failure.getMessage()), "WRONG_RESERVE_FAILURE");
            reserveRefused = true;
        }
        require(reserveRefused && stageFiles(root) == 0, "RESERVE_ACCEPTED_OR_LEFT_PARTIAL_FILE");
        var store = store(root, 64L << 20, 0);
        boolean[] physicalFailure = {false};
        boolean enospcRefused = false;
        try {
            store.stage("PHYSICAL_FAILURE", output -> {
                byte[] buffer = new byte[65536];
                try {
                    for (int i = 0; i < 1024; i++) output.write(buffer);
                } catch (IOException failure) {
                    physicalFailure[0] = fileStore.getUsableSpace() < 65536;
                    throw failure;
                }
            });
        } catch (IOException failure) {
            require(physicalFailure[0], "FAILURE_DID_NOT_EXHAUST_PHYSICAL_FILESYSTEM");
            enospcRefused = true;
        }
        require(enospcRefused && stageFiles(root) == 0, "ENOSPC_ACCEPTED_OR_LEFT_PARTIAL_FILE");
        var recovered = store.stage("RECOVERY", output -> output.write(7));
        try (var input = store.open(recovered)) {
            require(input.read() == 7 && input.read() == -1, "POST_FAILURE_RECOVERY_DIFFERS");
        }
        store.discardUnpublished(recovered);
        require(stageFiles(root) == 0, "RECOVERY_LEFT_STAGE_FILE");
        return new LinkedHashMap<>(Map.of("reserveRefused", true, "physicalEnospcRefused", true,
                "partialStageFiles", 0, "postFailureRecoveryVerified", true, "filesystemBytes", fileStore.getTotalSpace()));
    }

    private static long stageFiles(Path root) throws IOException {
        try (var files = Files.list(root)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".stage")).count();
        }
    }

    private static CdsExportStagingService store(Path root, long maximum, long reserve) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new CdsExportStagingService(root, UUID.randomUUID().toString(), 1, new SecretKeySpec(key, "AES"), maximum,
                reserve, CdsExportResourceQualification::checkpoint);
    }

    private static CdsExportPackageMembers members(CdsExportStagedFileDto file, int count) {
        return () -> new CdsExportPackageMembers.Cursor() {
            private int index;
            @Override public CdsExportPackageMemberDto next() {
                return index == count ? null : new CdsExportPackageMemberDto(name(index++), file);
            }
            @Override public void close() { }
        };
    }

    private static String name(int index) { return "files/f" + String.format(java.util.Locale.ROOT, "%05d", index); }

    private static void checkpoint() {
        require(System.nanoTime() < DEADLINE && !Thread.currentThread().isInterrupted(), "RESOURCE_DEADLINE_OR_INTERRUPTION");
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalStateException(code);
    }

    private static void write(Path path, Object value) throws IOException {
        try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            JSON.writeValue(output, value);
        }
    }
}
