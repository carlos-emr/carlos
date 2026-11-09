/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Durable, private staging journal for legacy Teleplan files. Recovery consults committed activity
 * before removing any published file, including after a lost database commit acknowledgement.
 * @since 2026-11-09
 */
final class LegacyTeleplanFiles {
    private record Artifact(int activityId, String hash) { }
    private static final String MANIFEST = "manifest.properties";
    private final Path home;
    private final Path journal;

    LegacyTeleplanFiles(Path home) throws IOException {
        this.home = home.toRealPath();
        if (!Files.isDirectory(this.home) || !Files.isWritable(this.home)) throw new IOException("Teleplan output directory is unavailable");
        journal = this.home.resolve(".carlos-bc-teleplan");
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(journal, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        if (Files.isSymbolicLink(journal) || !Files.isDirectory(journal, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(journal).equals(PosixFilePermissions.fromString("rwx------"))) {
            throw new IOException("Invalid Teleplan staging directory");
        }
    }

    Path stage(Map<String, String> artifacts, Map<String, Integer> activityIds) throws IOException {
        Path batch = Files.createDirectory(journal.resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Properties manifest = new Properties();
        manifest.setProperty("version", "2");
        for (var artifact : artifacts.entrySet()) {
            Path target = target(artifact.getKey());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Teleplan output filename is already in use");
            byte[] bytes = (artifact.getValue() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            Path staged = child(batch, artifact.getKey());
            writeNew(staged, bytes);
            Integer activityId = activityIds.get(artifact.getKey());
            if (activityId == null || activityId <= 0) throw new IOException("A batch activity identity is required");
            manifest.setProperty(artifact.getKey(), activityId + ":" + hash(bytes));
        }
        var serialized = new java.io.ByteArrayOutputStream();
        manifest.store(serialized, "CARLOS legacy Teleplan recovery journal");
        writeNew(batch.resolve(MANIFEST), serialized.toByteArray());
        forceDirectory(batch);
        forceDirectory(journal);
        return batch;
    }

    void publish(Path batch) throws IOException {
        for (var artifact : manifest(batch).entrySet()) publishOne(batch, artifact.getKey(), artifact.getValue().hash());
        forceDirectory(home);
    }

    boolean exists(String filename) throws IOException {
        return Files.exists(target(filename), LinkOption.NOFOLLOW_LINKS);
    }

    /** Must run under the database generation lock, outside the export transaction. */
    void recover(BiPredicate<Integer, String> hasCommittedActivity) throws IOException {
        try (var batches = Files.list(journal)) {
            for (Path batch : batches.toList()) {
                if (!batch.getFileName().toString().matches("[0-9a-f-]{36}")
                        || Files.isSymbolicLink(batch) || !Files.isDirectory(batch, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unexpected Teleplan staging entry; operator review is required");
                }
                if (!Files.exists(batch.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS)) {
                    // Publication starts only after a complete, forced manifest exists.
                    removeBatch(batch);
                    continue;
                }
                Map<String, Artifact> artifacts = manifest(batch);
                boolean committed = false;
                boolean uncommitted = false;
                for (var entry : artifacts.entrySet()) {
                    if (hasCommittedActivity.test(entry.getValue().activityId(), entry.getKey())) committed = true;
                    else uncommitted = true;
                }
                if (committed && uncommitted) throw new IOException("Inconsistent Teleplan batch; operator review is required");
                for (var artifact : artifacts.entrySet()) {
                    if (committed) publishOne(batch, artifact.getKey(), artifact.getValue().hash());
                    else {
                        Path target = target(artifact.getKey());
                        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                            verify(target, artifact.getValue().hash());
                            Files.delete(target);
                        }
                    }
                }
                forceDirectory(home);
                removeBatch(batch);
            }
        }
        forceDirectory(journal);
    }

    private void publishOne(Path batch, String name, String expected) throws IOException {
        Path destination = target(name);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verify(destination, expected);
            return;
        }
        Path source = child(batch, name);
        verify(source, expected);
        // A hard link publishes the already forced complete bytes atomically, without replacing anything.
        try {
            Files.createLink(destination, source);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            // Some otherwise supported HOME_DIR filesystems do not implement hard links.
            // Copy still uses CREATE_NEW; no activity can expose it before the complete file is forced.
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw unavailable;
            Files.copy(source, destination);
            try (var channel = FileChannel.open(destination, StandardOpenOption.WRITE)) { channel.force(true); }
        }
    }

    private Map<String, Artifact> manifest(Path batch) throws IOException {
        Properties data = new Properties();
        Path path = batch.resolve(MANIFEST);
        if (Files.isSymbolicLink(path)) throw new IOException("Invalid Teleplan journal");
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { data.load(input); }
        if (!"2".equals(data.remove("version"))) throw new IOException("Unsupported Teleplan journal");
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (String name : data.stringPropertyNames()) {
            target(name);
            String encoded = data.getProperty(name);
            if (!encoded.matches("[1-9][0-9]{0,9}:[a-f0-9]{64}")) throw new IOException("Invalid Teleplan journal witness");
            String[] parts = encoded.split(":", 2);
            try { artifacts.put(name, new Artifact(Integer.parseInt(parts[0]), parts[1])); }
            catch (NumberFormatException invalid) { throw new IOException("Invalid Teleplan journal activity identity", invalid); }
        }
        if (artifacts.isEmpty()) throw new IOException("Empty Teleplan journal");
        return artifacts;
    }

    private Path target(String filename) throws IOException {
        if (!filename.matches("H[A-L][0-9]{4,5}[_\\.][0-9]{3}(?:\\.htm)?")) {
            throw new IOException("Invalid Teleplan output filename");
        }
        return child(home, filename);
    }

    private static Path child(Path directory, String filename) throws IOException {
        Path resolved = directory.resolve(filename).normalize();
        if (!resolved.startsWith(directory) || !directory.equals(resolved.getParent())) {
            throw new IOException("Teleplan output path must be a direct child");
        }
        return resolved;
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(path, java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void verify(Path path, String expected) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !expected.equals(hash(Files.readAllBytes(path)))) {
            throw new IOException("Teleplan artifact changed; operator review is required");
        }
    }

    private static String hash(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void removeBatch(Path batch) throws IOException {
        // Retire the recovery witness before deleting staged bytes. Once this deletion is
        // durable, an interrupted cleanup is just an unpublished directory on the next run.
        Files.deleteIfExists(batch.resolve(MANIFEST));
        forceDirectory(batch);
        try (var entries = Files.list(batch)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unexpected Teleplan journal content");
                }
                Files.delete(entry);
            }
        }
        Files.delete(batch);
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
}
