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
package io.github.carlos_emr.carlos.fax.hylafax;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Monitors a HylaFax {@code recvq} directory for inbound fax files.
 *
 * @since 2026-05-05
 */
public class HylaFaxRecvqMonitor {

    /**
     * Lists supported fax files from a local recvq directory.
     *
     * @param faxConfig HylaFax configuration containing recvq path
     * @return inbound fax metadata
     * @throws HylaFaxException when the directory cannot be read
     */
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws HylaFaxException {
        Path recvq = resolveRecvqDirectory(faxConfig);
        List<FaxJob> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(recvq)) {
            for (Path candidate : stream) {
                if (!Files.isRegularFile(candidate) || !isSupportedFaxFile(candidate.getFileName().toString())) {
                    continue;
                }
                PathValidationUtils.validateExistingPath(candidate.toFile(), recvq.toFile());
                FaxJob faxJob = new FaxJob();
                faxJob.setFile_name(candidate.getFileName().toString());
                faxJob.setStatus(FaxJob.STATUS.RECEIVED);
                faxJob.setStatusString("Ready for HylaFax download");
                faxJob.setStamp(new Date(Files.getLastModifiedTime(candidate).toMillis()));
                result.add(faxJob);
            }
            return result;
        } catch (IOException | SecurityException e) {
            throw new HylaFaxException("Failed to list HylaFax recvq directory", e);
        }
    }

    /**
     * Downloads and PDF-normalizes a local recvq fax file for CARLOS import.
     *
     * @param faxConfig HylaFax configuration containing recvq path
     * @param fax inbound fax metadata
     * @return fax job with base64 encoded PDF document content
     * @throws HylaFaxException when the file cannot be read or converted
     */
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws HylaFaxException {
        Path recvq = resolveRecvqDirectory(faxConfig);
        try {
            File validated = PathValidationUtils.validatePath(fax.getFile_name(), recvq.toFile());
            Path file = validated.toPath();
            PathValidationUtils.validateExistingPath(file.toFile(), recvq.toFile());
            byte[] bytes = readAsPdf(file);
            FaxJob result = new FaxJob(fax);
            result.setDocument(Base64.getMimeEncoder().encodeToString(bytes));
            if (result.getStamp() == null) {
                result.setStamp(new Date(Files.getLastModifiedTime(file).toMillis()));
            }
            return result;
        } catch (IOException | SecurityException e) {
            throw new HylaFaxException("Failed to download HylaFax recvq fax", e);
        }
    }

    /**
     * Archives a processed local recvq fax to prevent duplicate imports.
     *
     * @param faxConfig HylaFax configuration containing recvq path
     * @param fax inbound fax metadata
     * @throws HylaFaxException when the file cannot be archived
     */
    public void archiveFax(FaxConfig faxConfig, FaxJob fax) throws HylaFaxException {
        Path recvq = resolveRecvqDirectory(faxConfig);
        try {
            File source = PathValidationUtils.validatePath(fax.getFile_name(), recvq.toFile());
            if (!source.exists()) {
                return;
            }
            Path archiveDir = recvq.resolve("processed-carlos").normalize();
            Files.createDirectories(archiveDir);
            PathValidationUtils.validateExistingPath(archiveDir.toFile(), recvq.toFile());
            File target = PathValidationUtils.validatePath(source.getName(), archiveDir.toFile());
            Files.move(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            throw new HylaFaxException("Failed to archive processed HylaFax recvq fax", e);
        }
    }

    byte[] readAsPdf(Path file) throws IOException, HylaFaxException {
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            return Files.readAllBytes(file);
        }
        if (lowerName.endsWith(".tif") || lowerName.endsWith(".tiff")) {
            return convertTiffToPdf(file);
        }
        throw new HylaFaxException("Unsupported HylaFax recvq file type");
    }

    boolean isSupportedFaxFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".pdf") || lowerName.endsWith(".tif") || lowerName.endsWith(".tiff");
    }

    private Path resolveRecvqDirectory(FaxConfig faxConfig) throws HylaFaxException {
        String recvqPath = faxConfig.getHylafaxRecvqPath();
        if (recvqPath == null || recvqPath.trim().isEmpty()) {
            throw new HylaFaxException("HylaFax recvq path is not configured");
        }
        Path recvq = Path.of(recvqPath.trim()).toAbsolutePath().normalize();
        if (recvqPath.contains("..") || recvqPath.contains("//") || recvq.getFileName() == null
                || !"recvq".equals(recvq.getFileName().toString())) {
            throw new HylaFaxException("HylaFax recvq path must point to a recvq directory");
        }
        try {
            recvq = recvq.toRealPath();
        } catch (IOException e) {
            throw new HylaFaxException("HylaFax recvq path cannot be resolved");
        }
        if (!Files.isDirectory(recvq) || !Files.isReadable(recvq)) {
            throw new HylaFaxException("HylaFax recvq path is not a readable directory");
        }
        return recvq;
    }

    private byte[] convertTiffToPdf(Path file) throws IOException, HylaFaxException {
        ProcessBuilder processBuilder = new ProcessBuilder("tiff2pdf", "-o", "-", file.toString());
        Process process = processBuilder.start();
        CompletableFuture<byte[]> stdout = readAsync(process.getInputStream());
        CompletableFuture<byte[]> stderr = readAsync(process.getErrorStream());
        try {
            int exit = process.waitFor();
            byte[] pdfBytes = stdout.join();
            stderr.join();
            if (exit != 0 || pdfBytes.length == 0) {
                throw new HylaFaxException("tiff2pdf failed converting HylaFax TIFF");
            }
            return pdfBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HylaFaxException("Interrupted while converting HylaFax TIFF", e, true);
        } catch (CompletionException e) {
            throw new HylaFaxException("Failed reading tiff2pdf output", e);
        }
    }

    private CompletableFuture<byte[]> readAsync(java.io.InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (java.io.InputStream stream = inputStream) {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
}
