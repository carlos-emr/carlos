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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;

/**
 * HylaFax client-mode integration backed by the standard HylaFax command-line tools.
 *
 * <p>The {@code sendfax} and {@code faxstat} tools speak the HylaFax client protocol to
 * the server (default TCP port 4559) and avoid adding third-party Java protocol dependencies.</p>
 *
 * @since 2026-05-05
 */
public class HylaFaxClientProtocol {

    private static final java.util.regex.Pattern SAFE_HOST =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]{0,252}");
    private static final java.util.regex.Pattern SAFE_MODEM =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{0,32}");

    private final HylaFaxJobParser parser;

    public HylaFaxClientProtocol() {
        this(new HylaFaxJobParser());
    }

    HylaFaxClientProtocol(HylaFaxJobParser parser) {
        this.parser = parser;
    }

    /**
     * Sends a fax through HylaFax using {@code sendfax}.
     *
     * @param faxConfig HylaFax connection configuration
     * @param faxJob outbound fax job
     * @param filePath resolved document path
     * @return provider response fax job
     * @throws HylaFaxException when command execution fails
     */
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws HylaFaxException {
        validateSendRequest(faxConfig, faxJob, filePath);
        String host = hostArgument(faxConfig);
        if (faxConfig.getHylafaxModem() != null && !faxConfig.getHylafaxModem().trim().isEmpty()) {
            host = host + "@" + faxConfig.getHylafaxModem().trim();
        }
        List<String> command = new ArrayList<>();
        command.add("sendfax");
        command.add("-h");
        command.add(host);
        command.add("-n");
        command.add("-d");
        command.add(faxJob.getDestination().trim());
        command.add(filePath.toString());

        CommandResult result = runCommand(command, null);
        FaxJob response = new FaxJob();
        response.setJobId(parser.parseSubmittedJobId(result.output()));
        response.setStatus(FaxJob.STATUS.SENT);
        response.setStatusString(response.getJobId() == null
                ? "Queued with HylaFax"
                : "Queued with HylaFax job " + response.getJobId());
        return response;
    }

    /**
     * Fetches HylaFax status for a submitted job.
     *
     * @param faxConfig HylaFax connection configuration
     * @param faxJob fax job containing provider job id
     * @return updated fax job
     * @throws HylaFaxException when command execution fails
     */
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws HylaFaxException {
        if (faxJob.getJobId() == null) {
            throw new HylaFaxException("HylaFax job id is required for status polling");
        }
        List<String> command = List.of("faxstat", "-h", hostArgument(faxConfig), "-s");
        CommandResult result = runCommand(command, null);
        return parser.parseStatus(result.output(), faxJob.getJobId());
    }

    CommandResult runCommand(List<String> command, byte[] stdin) throws HylaFaxException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            if (stdin != null) {
                try (java.io.OutputStream outputStream = process.getOutputStream()) {
                    outputStream.write(stdin);
                }
            } else {
                process.getOutputStream().close();
            }
            CompletableFuture<byte[]> stdout = readAsync(process.getInputStream());
            CompletableFuture<byte[]> stderr = readAsync(process.getErrorStream());
            int exit = process.waitFor();
            byte[] output = stdout.join();
            stderr.join();
            if (exit != 0) {
                throw new HylaFaxException("HylaFax command failed with exit " + exit);
            }
            return new CommandResult(exit, output);
        } catch (IOException e) {
            throw new HylaFaxException("HylaFax command execution failed: " + e.getMessage(), e,
                    FaxProviderException.isTransientNetworkCause(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HylaFaxException("HylaFax command execution was interrupted", e, true);
        } catch (CompletionException e) {
            throw new HylaFaxException("Failed reading HylaFax command output", e);
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

    String hostArgument(FaxConfig faxConfig) {
        int port = faxConfig.getHylafaxPort() == null ? 4559 : faxConfig.getHylafaxPort();
        return faxConfig.getHylafaxHost().trim() + ":" + port;
    }

    private void validateSendRequest(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws HylaFaxException {
        if (faxJob.getDestination() == null || faxJob.getDestination().trim().isEmpty()) {
            throw new HylaFaxException("HylaFax destination number is required");
        }
        if (filePath == null || !Files.isReadable(filePath)) {
            throw new HylaFaxException("HylaFax fax document file is not readable");
        }
        if (faxConfig.getHylafaxHost() == null || faxConfig.getHylafaxHost().trim().isEmpty()) {
            throw new HylaFaxException("HylaFax host is required");
        }
        if (!SAFE_HOST.matcher(faxConfig.getHylafaxHost().trim()).matches()) {
            throw new HylaFaxException("HylaFax host contains invalid characters");
        }
        if (faxConfig.getHylafaxModem() != null
                && !SAFE_MODEM.matcher(faxConfig.getHylafaxModem().trim()).matches()) {
            throw new HylaFaxException("HylaFax modem contains invalid characters");
        }
    }

    record CommandResult(int exitCode, byte[] outputBytes) {
        String output() {
            return new String(outputBytes);
        }
    }
}
