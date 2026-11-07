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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Owned, chunk-authenticated AES-256-GCM staging. Per-file keys are derived by HMAC-SHA256
 * from a fresh 256-bit salt and the exact job/attempt/member/purpose binding. Each 64KiB
 * chunk authenticates its header, index and length before any plaintext is returned. A
 * mandatory authenticated terminator and expected whole-file hash reject truncation and
 * appended data. This avoids the JDK GCM input stream's whole-file authentication buffer.
 * Publication and active-download retention are the job service's separate responsibility.
 */
public final class CdsExportStagingService {
    private static final byte[] MAGIC = "CDSSTG01".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK = 65_536;
    private static final int TAG = 16;
    private static final int HEADER = 8 + 32 + 32 + 8;
    private static final long MAX_CHUNKS = 0xffff_ffffL;
    private final Path root;
    private final String scope;
    private final SecretKey master;
    private final long maxBytes;
    private final long minimumFreeBytes;
    private final Runnable checkpoint;
    private final SecureRandom random = new SecureRandom();

    @FunctionalInterface public interface Content {
        void write(OutputStream plaintext) throws IOException;
    }

    /** The caller verifies the configured root and root-owned key before constructing a job attempt. */
    public CdsExportStagingService(Path root, String runId, long epoch, SecretKey master,
            long maxBytes, long minimumFreeBytes, Runnable checkpoint) {
        if (runId == null || !runId.matches("[0-9a-f-]{36}") || epoch < 1 || maxBytes < 1 || minimumFreeBytes < 0) {
            throw new IllegalArgumentException("CDS_EXPORT_STAGING_SCOPE_INVALID");
        }
        Objects.requireNonNull(master);
        byte[] encoded = master.getEncoded();
        if (encoded == null || encoded.length != 32 || !master.getAlgorithm().equals("AES")) {
            throw new IllegalArgumentException("CDS_EXPORT_AES256_KEY_REQUIRED");
        }
        this.master = new SecretKeySpec(encoded, "HmacSHA256"); Arrays.fill(encoded, (byte) 0);
        this.root = Objects.requireNonNull(root); scope = runId + "/" + epoch;
        this.maxBytes = maxBytes; this.minimumFreeBytes = minimumFreeBytes;
        this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    /** Creates one opaque, exclusive file; only a fully closed and fsynced write returns evidence. */
    public CdsExportStagedFileDto stage(String purpose, Content content) throws IOException {
        if (purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("CDS_EXPORT_STAGING_PURPOSE_INVALID");
        }
        Objects.requireNonNull(content); checkDisk();
        byte[] id = new byte[32]; random.nextBytes(id);
        String member = HexFormat.of().formatHex(id);
        Path file = memberPath(member);
        byte[] header = header(member, purpose);
        MessageDigest digest = sha256();
        ChunkOutput output = null;
        boolean complete = false;
        boolean created = false;
        try {
            try (FileChannel channel = FileChannel.open(file,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                created = true;
                output = new ChunkOutput(new DataOutputStream(Channels.newOutputStream(channel)), header, digest);
                content.write(output);
                output.finish(); channel.force(true);
            }
            try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                directory.force(true);
            }
            complete = true;
            return new CdsExportStagedFileDto(member, purpose, HexFormat.of().formatHex(digest.digest()), output.total);
        } finally {
            if (output != null) output.erase();
            if (created && !complete) Files.deleteIfExists(file);
        }
    }

    /**
     * Reads authenticate chunk by chunk and check the expected digest/length at EOF. Callers
     * must consume EOF before accepting the bytes; closing a partial read does not attest it.
     */
    public InputStream open(CdsExportStagedFileDto file) throws IOException {
        Objects.requireNonNull(file); checkpoint.run();
        DataInputStream input = new DataInputStream(Files.newInputStream(memberPath(file.memberId()),
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        try {
            byte[] header = input.readNBytes(HEADER);
            if (header.length != HEADER || !MessageDigest.isEqual(MAGIC, Arrays.copyOf(header, MAGIC.length))
                    || !MessageDigest.isEqual(binding(file.memberId(), file.purpose()), Arrays.copyOfRange(header, 40, 72))) {
                throw new IOException("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
            }
            return new ChunkInput(input, header, file);
        } catch (IOException | RuntimeException | Error failure) {
            try { input.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /**
     * Opens one pinned, completely authenticated input for bounded random field reads.
     * Its close authenticates the entire member again, including its terminator and EOF.
     * A field read attests only that field; callers must close this scope before accepting
     * any derived output. This does not change the stored v1 chunk format.
     */
    public VerifiedRanges verifiedRanges(CdsExportStagedFileDto file) throws IOException {
        Objects.requireNonNull(file); checkpoint.run();
        if (file.plaintextBytes() > maxBytes) throw new IOException("CDS_EXPORT_STAGED_BYTE_LIMIT");
        var channel = FileChannel.open(memberPath(file.memberId()), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try { return new VerifiedRanges(channel, file); }
        catch (IOException | RuntimeException | Error failure) {
            try { channel.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); } throw failure;
        }
    }
    /** One thread and one open field at a time; no plaintext outside a requested range is exposed. */
    public final class VerifiedRanges implements AutoCloseable {
        private final FileChannel channel;
        private final CdsExportStagedFileDto expected;
        private final byte[] header;
        private final SecretKey key;
        private final long encryptedBytes;
        private final Thread owner = Thread.currentThread();
        private FieldRange open;
        private boolean closed;
        private volatile boolean failed;
        private VerifiedRanges(FileChannel channel, CdsExportStagedFileDto expected) throws IOException {
            this.channel = channel; this.expected = expected;
            long chunks = expected.plaintextBytes() / CHUNK + (expected.plaintextBytes() % CHUNK == 0 ? 0 : 1);
            if (chunks > MAX_CHUNKS) throw new IOException("CDS_EXPORT_STAGING_CHUNK_LIMIT");
            try { encryptedBytes = Math.addExact(HEADER, Math.addExact(expected.plaintextBytes(), Math.multiplyExact(chunks + 1, 4L + TAG))); }
            catch (ArithmeticException overflow) { throw new IOException("CDS_EXPORT_STAGED_BYTE_LIMIT"); }
            header = new byte[HEADER]; readAt(ByteBuffer.wrap(header), 0);
            if (!MessageDigest.isEqual(MAGIC, Arrays.copyOf(header, MAGIC.length))
                    || !MessageDigest.isEqual(binding(expected.memberId(), expected.purpose()), Arrays.copyOfRange(header, 40, 72)))
                throw new IOException("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
            key = fileKey(header); verifyWhole();
        }
        public InputStream open(long offset, long bytes, String sha256) throws IOException {
            try {
                check();
                if (open != null || offset < 0 || bytes < 0 || offset > expected.plaintextBytes() || bytes > expected.plaintextBytes() - offset
                        || sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IOException("CDS_EXPORT_VALUE_RANGE_INVALID");
                open = new FieldRange(offset, bytes, sha256); return open;
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        private void check() throws IOException {
            if (closed || failed || Thread.currentThread() != owner) throw new IOException("CDS_EXPORT_VERIFIED_RANGE_SCOPE_INVALID");
            checkpoint.run();
            if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
            if (channel.size() != encryptedBytes) throw new IOException("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
        }
        private void readAt(ByteBuffer target, long position) throws IOException {
            while (target.hasRemaining()) {
                checkpoint.run(); int count = channel.read(target, position + target.position());
                if (count <= 0) throw new IOException("CDS_EXPORT_STAGED_TRUNCATED");
            }
        }
        private void verifyWhole() throws IOException {
            if (channel.size() != encryptedBytes) throw new IOException("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
            channel.position(0);
            // Closing the verifier erases its plaintext but leaves this scope's descriptor pinned.
            var stream = new java.io.FilterInputStream(Channels.newInputStream(channel)) { @Override public void close() { } };
            var input = new DataInputStream(stream); byte[] actual = input.readNBytes(HEADER);
            if (!MessageDigest.isEqual(actual, header)) throw new IOException("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
            try (var clear = new ChunkInput(input, actual, expected, true)) {
                byte[] buffer = new byte[CHUNK];
                try { int count; while ((count = clear.read(buffer)) != -1) if (count == 0) throw new IOException("CDS_EXPORT_VALUE_RANGE_STALLED"); }
                finally { Arrays.fill(buffer, (byte) 0); }
            }
        }
        @Override public void close() throws IOException {
            if (closed) return;
            Throwable primary = null;
            try {
                check(); if (open != null) throw new IOException("CDS_EXPORT_VERIFIED_RANGE_INCOMPLETE"); verifyWhole();
            } catch (IOException | RuntimeException | Error failure) {
                primary = failure; throw failure;
            } finally {
                closed = true; if (open != null) open.erase(); Arrays.fill(header, (byte) 0);
                try { channel.close(); }
                catch (IOException cleanup) { if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
            }
        }
        private final class FieldRange extends InputStream {
            private final MessageDigest digest = sha256();
            private final byte[] expectedHash;
            private long position, remaining;
            private byte[] clear = new byte[0];
            private int at;
            private boolean done, fieldClosed;
            private FieldRange(long offset, long bytes, String sha256) { position = offset; remaining = bytes; expectedHash = HexFormat.of().parseHex(sha256); }
            @Override public int read() throws IOException { byte[] one = new byte[1]; int count = read(one, 0, 1); return count < 0 ? -1 : one[0] & 255; }
            @Override public int read(byte[] target, int offset, int length) throws IOException {
                try {
                    Objects.checkFromIndexSize(offset, length, target.length); check();
                    if (fieldClosed || open != this) throw new IOException("CDS_EXPORT_VERIFIED_RANGE_INCOMPLETE");
                    if (length == 0) return 0;
                    if (remaining == 0) {
                        if (!done && !MessageDigest.isEqual(digest.digest(), expectedHash)) throw new IOException("CDS_EXPORT_VALUE_RANGE_DIGEST_MISMATCH");
                        done = true; return -1;
                    }
                    if (at == clear.length) load();
                    int count = (int) Math.min(Math.min(length, clear.length - at), remaining);
                    if (count <= 0) throw new IOException("CDS_EXPORT_STAGED_TRUNCATED");
                    System.arraycopy(clear, at, target, offset, count); digest.update(target, offset, count);
                    at += count; position += count; remaining -= count; return count;
                } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
            }
            private void load() throws IOException {
                Arrays.fill(clear, (byte) 0); long index = position / CHUNK;
                long start = Math.addExact(HEADER, Math.multiplyExact(index, CHUNK + 4L + TAG));
                int expectedLength = (int) Math.min(CHUNK, expected.plaintextBytes() - index * CHUNK);
                var size = ByteBuffer.allocate(4); readAt(size, start); int length = size.flip().getInt();
                if (length != expectedLength || length < 1) throw new IOException("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
                byte[] encrypted = new byte[length + TAG];
                try { readAt(ByteBuffer.wrap(encrypted), start + 4); clear = crypt(Cipher.DECRYPT_MODE, key, header, index, length, encrypted, encrypted.length); }
                finally { Arrays.fill(encrypted, (byte) 0); }
                at = (int) (position % CHUNK);
            }
            private void erase() { fieldClosed = true; Arrays.fill(clear, (byte) 0); }
            @Override public void close() throws IOException {
                if (fieldClosed) return;
                try {
                    if (!failed) {
                        byte[] buffer = new byte[CHUNK];
                        try { while (read(buffer) != -1) { /* Authenticate the complete requested field, even on early close. */ } }
                        finally { Arrays.fill(buffer, (byte) 0); }
                    }
                } finally { erase(); if (open == this) open = null; }
            }
        }
    }

    /**
     * Erases a caller-owned, unpublished staging member after authenticating its scope and
     * first chunk. The caller must exclude published/active-reader members through its
     * durable retention protocol. Cleanup remains possible after cancellation or fencing.
     */
    public void discardUnpublished(CdsExportStagedFileDto file) throws IOException {
        Objects.requireNonNull(file); Path path = memberPath(file.memberId());
        try (var input = new DataInputStream(Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            byte[] header = input.readNBytes(HEADER);
            if (header.length != HEADER || !MessageDigest.isEqual(MAGIC, Arrays.copyOf(header, MAGIC.length))
                    || !MessageDigest.isEqual(binding(file.memberId(), file.purpose()), Arrays.copyOfRange(header, 40, 72))) {
                throw new IOException("CDS_EXPORT_STAGED_SCOPE_MISMATCH");
            }
            int length = input.readInt(); if (length < 0 || length > CHUNK) throw new IOException("CDS_EXPORT_STAGED_BYTE_LIMIT");
            byte[] encrypted = input.readNBytes(length + TAG);
            if (encrypted.length != length + TAG) throw new IOException("CDS_EXPORT_STAGED_TRUNCATED");
            byte[] clear = crypt(Cipher.DECRYPT_MODE, fileKey(header), header, 0, length, encrypted, encrypted.length);
            Arrays.fill(clear, (byte) 0); Files.delete(path);
        } catch (java.nio.file.NoSuchFileException absent) { return; }
        try (var directory = FileChannel.open(root, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { directory.force(true); }
    }

    private byte[] header(String member, String purpose) {
        byte[] salt = new byte[32], nonce = new byte[8]; random.nextBytes(salt); random.nextBytes(nonce);
        return ByteBuffer.allocate(HEADER).put(MAGIC).put(salt).put(binding(member, purpose)).put(nonce).array();
    }
    private byte[] binding(String member, String purpose) {
        return sha256().digest(("CARLOS-CDS-STAGE-1\n" + scope + "\n" + member + "\n" + purpose)
                .getBytes(StandardCharsets.US_ASCII));
    }
    private SecretKey fileKey(byte[] header) throws IOException {
        try {
            Mac derive = Mac.getInstance("HmacSHA256"); derive.init(master);
            derive.update("CARLOS-CDS-STAGE-KEY-1\n".getBytes(StandardCharsets.US_ASCII));
            derive.update(header, 8, 64);
            byte[] bytes = derive.doFinal();
            try { return new SecretKeySpec(bytes, "AES"); }
            finally { Arrays.fill(bytes, (byte) 0); }
        } catch (GeneralSecurityException failure) { throw new IOException("CDS_EXPORT_STAGING_CRYPTO_UNAVAILABLE"); }
    }
    private byte[] crypt(int mode, SecretKey key, byte[] header, long index, int length,
            byte[] value, int size) throws IOException {
        if (index < 0 || index > MAX_CHUNKS) throw new IOException("CDS_EXPORT_STAGING_CHUNK_LIMIT");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] nonce = ByteBuffer.allocate(12).put(header, 72, 8).putInt((int) index).array();
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(header); cipher.updateAAD(ByteBuffer.allocate(12).putLong(index).putInt(length).array());
            return cipher.doFinal(value, 0, size);
        } catch (GeneralSecurityException failure) { throw new IOException("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED"); }
    }
    private Path memberPath(String member) throws IOException {
        if (member == null || !member.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("CDS_EXPORT_STAGED_MEMBER_INVALID");
        Path absolute = root.toAbsolutePath().normalize();
        if (!absolute.equals(root) || !absolute.equals(root.toRealPath())
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS)
                        .equals(PosixFilePermissions.fromString("rwx------"))) {
            throw new IOException("CDS_EXPORT_STAGING_ROOT_NOT_PRIVATE");
        }
        Path candidate = root.resolve(member + ".stage");
        if (Files.isSymbolicLink(candidate)) throw new IOException("CDS_EXPORT_STAGED_LINK_REFUSED");
        return PathValidationUtils.validatePrivateImportPath(candidate.toFile(), root.toFile()).toPath();
    }
    private void checkDisk() throws IOException {
        checkpoint.run();
        if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
        if (Files.getFileStore(root).getUsableSpace() < minimumFreeBytes) throw new IOException("CDS_EXPORT_DISK_RESERVE");
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private final class ChunkOutput extends OutputStream {
        private final DataOutputStream output;
        private final byte[] header;
        private final SecretKey key;
        private final MessageDigest digest;
        private final byte[] buffer = new byte[CHUNK];
        private int buffered;
        private long index;
        private long total;
        private boolean finished;
        private boolean failed;
        ChunkOutput(DataOutputStream output, byte[] header, MessageDigest digest) throws IOException {
            this.output = output; this.header = header; this.digest = digest; key = fileKey(header); output.write(header);
        }
        @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                if (failed) throw new IOException("CDS_EXPORT_STAGED_WRITE_FAILED");
                Objects.checkFromIndexSize(offset, length, bytes.length);
                if (finished) throw new IOException("CDS_EXPORT_STAGED_WRITE_CLOSED");
                if (length > maxBytes - total) throw new IOException("CDS_EXPORT_STAGED_BYTE_LIMIT");
                digest.update(bytes, offset, length); total += length;
                while (length > 0) {
                    int n = Math.min(length, CHUNK - buffered);
                    System.arraycopy(bytes, offset, buffer, buffered, n); buffered += n; offset += n; length -= n;
                    if (buffered == CHUNK) chunk();
                }
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        private void chunk() throws IOException {
            checkDisk(); output.writeInt(buffered);
            output.write(crypt(Cipher.ENCRYPT_MODE, key, header, index++, buffered, buffer, buffered)); buffered = 0;
        }
        void finish() throws IOException {
            try {
                if (failed) throw new IOException("CDS_EXPORT_STAGED_WRITE_FAILED");
                if (finished) return;
                if (buffered > 0) chunk();
                chunk(); output.flush(); finished = true;
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        @Override public void close() throws IOException { finish(); }
        void erase() { Arrays.fill(buffer, (byte) 0); }
    }
    private final class ChunkInput extends InputStream {
        private final DataInputStream input;
        private final byte[] header;
        private final SecretKey key;
        private final CdsExportStagedFileDto expected;
        private final MessageDigest digest = sha256();
        private final boolean canonical;
        private byte[] plain = new byte[0];
        private int offset;
        private long index;
        private long total;
        private boolean finished;
        private boolean closed;
        private boolean failed;
        ChunkInput(DataInputStream input, byte[] header, CdsExportStagedFileDto expected) throws IOException { this(input, header, expected, false); }
        ChunkInput(DataInputStream input, byte[] header, CdsExportStagedFileDto expected, boolean canonical) throws IOException {
            this.input = input; this.header = header; this.expected = expected; this.canonical = canonical; key = fileKey(header);
        }
        @Override public int read() throws IOException {
            byte[] single = new byte[1]; int n = read(single, 0, 1); return n < 0 ? -1 : single[0] & 255;
        }
        @Override public int read(byte[] bytes, int start, int length) throws IOException {
            try {
                if (failed) throw new IOException("CDS_EXPORT_STAGED_READ_FAILED");
                Objects.checkFromIndexSize(start, length, bytes.length);
                if (closed) throw new IOException("CDS_EXPORT_STAGED_READ_CLOSED");
                if (length == 0) return 0;
                if (offset == plain.length && !next()) return -1;
                int n = Math.min(length, plain.length - offset);
                System.arraycopy(plain, offset, bytes, start, n); offset += n; return n;
            } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
        }
        private boolean next() throws IOException {
            if (finished) return false;
            checkpoint.run();
            if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
            Arrays.fill(plain, (byte) 0); offset = 0;
            final int length;
            try { length = input.readInt(); }
            catch (EOFException truncated) { throw new IOException("CDS_EXPORT_STAGED_TRUNCATED"); }
            if (length < 0 || length > CHUNK || length > maxBytes - total) throw new IOException("CDS_EXPORT_STAGED_BYTE_LIMIT");
            if (canonical && length != (int) Math.min(CHUNK, expected.plaintextBytes() - total)) throw new IOException("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
            byte[] encrypted = input.readNBytes(length + TAG);
            if (encrypted.length != length + TAG) throw new IOException("CDS_EXPORT_STAGED_TRUNCATED");
            plain = crypt(Cipher.DECRYPT_MODE, key, header, index++, length, encrypted, encrypted.length);
            if (length == 0) {
                if (input.read() != -1 || total != expected.plaintextBytes()
                        || !HexFormat.of().formatHex(digest.digest()).equals(expected.plaintextSha256())) {
                    throw new IOException("CDS_EXPORT_STAGED_IDENTITY_MISMATCH");
                }
                finished = true;
                return false;
            }
            digest.update(plain); total += length;
            return true;
        }
        @Override public void close() throws IOException {
            closed = true; Arrays.fill(plain, (byte) 0); input.close();
        }
    }
}
