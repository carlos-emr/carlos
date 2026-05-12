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
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;

/**
 * SSH command execution path for clinics that run HylaFax tools only on the fax server host.
 *
 * @since 2026-05-05
 */
public class HylaFaxSshClient {

    private static final java.util.regex.Pattern SAFE_DESTINATION =
            java.util.regex.Pattern.compile("[+0-9() .-]{3,64}");
    private static final java.util.regex.Pattern SAFE_REMOTE_PATH =
            java.util.regex.Pattern.compile("/[A-Za-z0-9_./-]{1,240}");
    private static final java.util.regex.Pattern SAFE_HOST =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]{0,252}");
    private static final java.util.regex.Pattern SAFE_USERNAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String SSH_HOST_ALIAS_PROPERTY = "hylafax.ssh.host.alias";

    private final HylaFaxJobParser parser;

    public HylaFaxSshClient() {
        this(new HylaFaxJobParser());
    }

    HylaFaxSshClient(HylaFaxJobParser parser) {
        this.parser = parser;
    }

    /**
     * Sends a fax by streaming the local document to a remote {@code sendfax} command over SSH.
     *
     * @param faxConfig HylaFax SSH configuration
     * @param faxJob outbound fax job
     * @param filePath resolved local document path
     * @return provider response fax job
     * @throws HylaFaxException when SSH execution fails
     */
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws HylaFaxException {
        validateSshConfig(faxConfig);
        validateDestination(faxJob.getDestination());
        if (filePath == null || !Files.isReadable(filePath)) {
            throw new HylaFaxException("HylaFax fax document file is not readable");
        }
        try {
            byte[] document = Files.readAllBytes(filePath);
            String remoteCommand = "umask 077; tmp=$(mktemp /tmp/carlos-hylafax.XXXXXX.pdf); "
                    + "cat > \"$tmp\"; sendfax -n -d " + shellQuote(faxJob.getDestination().trim())
                    + " \"$tmp\"; rc=$?; rm -f \"$tmp\"; exit $rc";
            HylaFaxClientProtocol.CommandResult result = runSshCommand(faxConfig, remoteCommand, document);
            FaxJob response = new FaxJob();
            response.setJobId(parser.parseSubmittedJobId(result.output()));
            response.setStatus(FaxJob.STATUS.SENT);
            response.setStatusString(response.getJobId() == null
                    ? "Queued with HylaFax over SSH"
                    : "Queued with HylaFax SSH job " + response.getJobId());
            return response;
        } catch (IOException e) {
            throw new HylaFaxException("Failed to read fax document for HylaFax SSH send", e);
        }
    }

    /**
     * Fetches job status through {@code faxstat -s} on the remote HylaFax host.
     *
     * @param faxConfig HylaFax SSH configuration
     * @param faxJob fax job containing provider job id
     * @return updated fax job
     * @throws HylaFaxException when SSH execution fails
     */
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws HylaFaxException {
        validateSshConfig(faxConfig);
        if (faxJob.getJobId() == null) {
            throw new HylaFaxException("HylaFax job id is required for status polling");
        }
        HylaFaxClientProtocol.CommandResult result = runSshCommand(faxConfig, "faxstat -s", null);
        return parser.parseStatus(result.output(), faxJob.getJobId());
    }

    /**
     * Lists remote recvq files over SSH.
     *
     * @param faxConfig HylaFax SSH configuration
     * @return inbound fax metadata
     * @throws HylaFaxException when recvq cannot be listed
     */
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws HylaFaxException {
        validateSshConfig(faxConfig);
        String recvq = validateRemotePath(faxConfig.getHylafaxRecvqPath());
        String command = "find " + shellQuote(recvq) + " -maxdepth 1 -type f "
                + "\\( -name '*.pdf' -o -name '*.tif' -o -name '*.tiff' -o -name '*.PDF' -o -name '*.TIF' -o -name '*.TIFF' \\) "
                + "-printf '%T@\\t%s\\t%f\\n'";
        HylaFaxClientProtocol.CommandResult result = runSshCommand(faxConfig, command, null);
        String millisListing = secondsToMillisListing(result.output());
        return parser.parseRecvqListing(millisListing);
    }

    /**
     * Downloads a remote recvq file and returns base64 encoded PDF content.
     *
     * @param faxConfig HylaFax SSH configuration
     * @param fax inbound fax metadata
     * @return fax job with document content
     * @throws HylaFaxException when download fails
     */
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws HylaFaxException {
        validateSshConfig(faxConfig);
        String recvq = validateRemotePath(faxConfig.getHylafaxRecvqPath());
        String fileName = Path.of(fax.getFile_name()).getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".tif") && !lowerName.endsWith(".tiff")) {
            throw new HylaFaxException("Unsupported HylaFax recvq file type");
        }
        String remotePath = recvq.endsWith("/") ? recvq + fileName : recvq + "/" + fileName;
        String containmentCheck = "recvq=" + shellQuote(recvq) + "; path=" + shellQuote(remotePath)
                + "; real=$(realpath -- \"$path\") || exit 2; "
                + "case \"$real\" in \"$recvq\"/*) ;; *) exit 3;; esac; ";
        String command = lowerName.endsWith(".pdf")
                ? containmentCheck + "cat -- \"$real\""
                : containmentCheck + "tiff2pdf -o - \"$real\"";
        HylaFaxClientProtocol.CommandResult result = runSshCommand(faxConfig, command, null);
        FaxJob downloaded = new FaxJob(fax);
        downloaded.setDocument(Base64.getMimeEncoder().encodeToString(result.outputBytes()));
        return downloaded;
    }

    /**
     * Archives a processed remote recvq fax to prevent duplicate imports.
     *
     * @param faxConfig HylaFax SSH configuration
     * @param fax inbound fax metadata
     * @throws HylaFaxException when archive command fails
     */
    public void archiveFax(FaxConfig faxConfig, FaxJob fax) throws HylaFaxException {
        validateSshConfig(faxConfig);
        String recvq = validateRemotePath(faxConfig.getHylafaxRecvqPath());
        String fileName = Path.of(fax.getFile_name()).getFileName().toString();
        String remotePath = recvq.endsWith("/") ? recvq + fileName : recvq + "/" + fileName;
        String archiveDir = recvq.endsWith("/") ? recvq + "processed-carlos" : recvq + "/processed-carlos";
        String command = "recvq=" + shellQuote(recvq) + "; path=" + shellQuote(remotePath)
                + "; name=" + shellQuote(fileName)
                + "; [ -e \"$path\" ] || exit 0; real=$(realpath -- \"$path\") || exit 2; "
                + "case \"$real\" in \"$recvq\"/*) ;; *) exit 3;; esac; "
                + "archive=" + shellQuote(archiveDir)
                + "; mkdir -p -- \"$archive\"; archiveReal=$(realpath -m -- \"$archive\") || exit 4; "
                + "case \"$archiveReal\" in \"$recvq\"/*) ;; *) exit 5;; esac; "
                + "mv -f -- \"$real\" \"$archiveReal/$name\"";
        runSshCommand(faxConfig, command, null);
    }

    HylaFaxClientProtocol.CommandResult runSshCommand(FaxConfig faxConfig, String remoteCommand, byte[] stdin)
            throws HylaFaxException {
        try {
            List<String> command = new ArrayList<>();
            command.add("ssh");
            command.add("-o");
            command.add("BatchMode=yes");
            command.add("-o");
            command.add("StrictHostKeyChecking=yes");
            command.add("-o");
            command.add("IdentitiesOnly=yes");
            command.add("--");
            command.add(configuredSshAlias());
            command.add(remoteCommand);
            return runProcess(command, stdin);
        } catch (IOException e) {
            throw new HylaFaxException("HylaFax SSH execution failed: " + e.getMessage(), e,
                    FaxProviderException.isTransientNetworkCause(e));
        }
    }

    private HylaFaxClientProtocol.CommandResult runProcess(List<String> command, byte[] stdin)
            throws IOException, HylaFaxException {
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
                throw new HylaFaxException("HylaFax SSH command failed with exit " + exit);
            }
            return new HylaFaxClientProtocol.CommandResult(exit, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HylaFaxException("HylaFax SSH command was interrupted", e, true);
        } catch (CompletionException e) {
            throw new HylaFaxException("Failed reading HylaFax SSH command output", e);
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

    private void validateSshConfig(FaxConfig faxConfig) throws HylaFaxException {
        configuredSshAlias();
        if (faxConfig.getHylafaxHost() == null || faxConfig.getHylafaxHost().trim().isEmpty()) {
            throw new HylaFaxException("HylaFax SSH host is required");
        }
        if (!SAFE_HOST.matcher(faxConfig.getHylafaxHost().trim()).matches()) {
            throw new HylaFaxException("HylaFax SSH host contains invalid characters");
        }
        if (faxConfig.getHylafaxUsername() == null || faxConfig.getHylafaxUsername().trim().isEmpty()) {
            throw new HylaFaxException("HylaFax SSH username is required");
        }
        if (!SAFE_USERNAME.matcher(faxConfig.getHylafaxUsername().trim()).matches()) {
            throw new HylaFaxException("HylaFax SSH username contains invalid characters");
        }
    }

    private String configuredSshAlias() throws HylaFaxException {
        String alias = CarlosProperties.getInstance().getProperty(SSH_HOST_ALIAS_PROPERTY);
        if (alias == null || alias.trim().isEmpty()) {
            throw new HylaFaxException("HylaFax SSH host alias is not configured");
        }
        String trimmedAlias = alias.trim();
        if (!SAFE_HOST.matcher(trimmedAlias).matches()) {
            throw new HylaFaxException("HylaFax SSH host alias contains invalid characters");
        }
        return trimmedAlias;
    }

    private void validateDestination(String destination) throws HylaFaxException {
        if (destination == null || !SAFE_DESTINATION.matcher(destination.trim()).matches()) {
            throw new HylaFaxException("HylaFax destination number is invalid");
        }
    }

    private String validateRemotePath(String remotePath) throws HylaFaxException {
        if (remotePath == null || !SAFE_REMOTE_PATH.matcher(remotePath.trim()).matches()
                || remotePath.contains("..") || remotePath.contains("//")
                || !remotePath.endsWith("/recvq")) {
            throw new HylaFaxException("HylaFax recvq path must be an absolute safe remote path");
        }
        return remotePath.trim();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String secondsToMillisListing(String output) {
        StringBuilder builder = new StringBuilder();
        if (output == null) {
            return "";
        }
        for (String line : output.split("\\R")) {
            String[] parts = line.split("\\t", 3);
            if (parts.length < 3) {
                continue;
            }
            long millis;
            try {
                millis = (long) (Double.parseDouble(parts[0]) * 1000L);
            } catch (NumberFormatException e) {
                millis = System.currentTimeMillis();
            }
            builder.append(millis).append('\t').append(parts[1]).append('\t').append(parts[2]).append('\n');
        }
        return builder.toString();
    }
}
