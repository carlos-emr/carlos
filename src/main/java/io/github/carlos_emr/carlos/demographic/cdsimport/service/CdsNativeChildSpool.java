/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;

/** Shared append-only derived fields and values; no file or retained object per native child. */
final class CdsNativeChildSpool implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path directory;
    private final Path metadataPath;
    private final Path valuesPath;
    private final FileChannel metadata;
    private final FileChannel values;
    private final FileLock lock;
    private final CdsInputLimitsDto limits;
    private long nextDiskCheck;

    CdsNativeChildSpool(CdsRecordDto parent, CdsInputLimitsDto limits) throws IOException {
        directory = parent.fieldsFile().toAbsolutePath().getParent();
        metadataPath = CdsPaths.within(directory.resolve("native-extension-fields.jsonl"), directory);
        valuesPath = CdsPaths.within(directory.resolve("native-extension-values.bin"), directory);
        this.limits = limits;
        metadata = open(metadataPath);
        FileLock acquired = null;
        FileChannel opened = null;
        try {
            acquired = metadata.tryLock();
            if (acquired == null) throw new CdsInputException("CDS_DERIVED_SPOOL_BUSY", "", "Derived native child spool unavailable");
            opened = open(valuesPath);
            metadata.position(metadata.size());
            opened.position(opened.size());
        } catch (IOException | RuntimeException failed) {
            for (AutoCloseable resource : new AutoCloseable[] {opened, acquired, metadata}) {
                if (resource == null) continue;
                try { resource.close(); }
                catch (Exception closeFailure) { failed.addSuppressed(closeFailure); }
            }
            if (failed instanceof OverlappingFileLockException)
                throw new CdsInputException("CDS_DERIVED_SPOOL_BUSY", "", "Derived native child spool unavailable");
            throw failed;
        }
        lock = acquired;
        values = opened;
    }

    private static FileChannel open(Path path) throws IOException {
        return FileChannel.open(path,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    CdsRecordDto append(CdsRecordDto parent, long ordinal, Map<String, String> members) throws IOException {
        long start = metadata.position();
        long count = 0;
        var digest = CdsPaths.sha256();
        for (var member : members.entrySet()) {
            if (Thread.currentThread().isInterrupted()) throw new CdsInputException("CDS_INPUT_CANCELLED", "", "Derived native child spool unavailable");
            if (member.getValue() == null) continue;
            byte[] bytes = member.getValue().getBytes(StandardCharsets.UTF_8);
            var field = new CdsFieldDto(member.getKey() + "[1]", "cds", "TEXT", values.position(),
                    bytes.length, CdsPaths.hex(CdsPaths.sha256().digest(bytes)), "string", "preserve");
            byte[] metadataBytes = JSON.writeValueAsBytes(field);
            long size = Math.addExact(values.position(), metadata.position());
            long addition = Math.addExact(bytes.length, metadataBytes.length + 1L);
            if (Math.addExact(size, addition) >= nextDiskCheck) {
                long available = Files.getFileStore(directory).getUsableSpace();
                if (available < limits.minimumFreeDiskBytes()
                        || available - limits.minimumFreeDiskBytes() < addition)
                    throw new CdsInputException("CDS_INPUT_DISK_RESERVE", "", "Derived native child spool unavailable");
                nextDiskCheck = Math.addExact(size, 16L << 20);
            }
            write(values, bytes);
            write(metadata, metadataBytes);
            write(metadata, new byte[] {'\n'});
            digest.update(CdsFieldParser.identity(field));
            count++;
        }
        values.force(false);
        metadata.force(false);
        return new CdsRecordDto("HistoryExtension", ordinal,
                parent.sourceXPath() + "/CARLOSNativeExtension[" + ordinal + "]", metadataPath,
                start, metadata.position() - start, valuesPath, CdsPaths.hex(digest.digest()), count);
    }

    private static void write(FileChannel channel, byte[] bytes) throws IOException {
        var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    @Override public void close() throws IOException {
        try (metadata; values; lock) {
            values.force(true);
            metadata.force(true);
            CdsPaths.forceDirectory(directory);
        }
    }
}
