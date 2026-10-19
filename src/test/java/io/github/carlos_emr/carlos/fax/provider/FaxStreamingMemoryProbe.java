/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.core.FaxImporter;

/** Executed in a separate 48 MiB JVM: the fixture is deliberately much larger than its heap. */
public final class FaxStreamingMemoryProbe {
    private FaxStreamingMemoryProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        System.setProperty("carlos.fax.middleware.allowedHosts", "127.0.0.1");
        AtomicReference<Path> currentFile = new AtomicReference<>();
        AtomicReference<byte[]> receivedDigest = new AtomicReference<>();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String route = exchange.getRequestURI().getPath();
                boolean middleware = route.startsWith("/fax");
                boolean outbound = middleware && route.contains("/send/");
                // SRFax's action appears before its binary field. For that endpoint read a
                // small form prefix once, then decide whether this is send or Retrieve_Fax.
                InputStream request = new java.io.BufferedInputStream(exchange.getRequestBody());
                if (!middleware) {
                    seek(request, "action=", 16384);
                    String action = readUntil(request, '&', 16384);
                    outbound = action.equals("Queue_Fax");
                    if (outbound) {
                        seek(request, "sFileContent_1=", 16384);
                    } else {
                        request.transferTo(OutputStream.nullOutputStream());
                    }
                }
                if (outbound) {
                    InputStream binary;
                    if (middleware) {
                        seek(request, "<document>", 16384);
                        binary = new FilterInputStream(request) {
                            @Override
                            public int read() throws java.io.IOException {
                                int value = in.read();
                                return value == '<' ? -1 : value;
                            }
                            @Override
                            public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                                int value = read();
                                if (value < 0) {
                                    return -1;
                                }
                                bytes[offset] = (byte) value;
                                return 1;
                            }
                        };
                    } else {
                        binary = new FilterInputStream(request) {
                            @Override
                            public int read() throws java.io.IOException {
                                int value = in.read();
                                if (value == '%') {
                                    return (Character.digit(in.read(), 16) << 4) | Character.digit(in.read(), 16);
                                }
                                return value == '+' ? ' ' : value;
                            }
                            @Override
                            public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                                int value = read();
                                if (value < 0) {
                                    return -1;
                                }
                                bytes[offset] = (byte) value;
                                return 1;
                            }
                        };
                    }
                    receivedDigest.set(digest(Base64.getDecoder().wrap(binary)));
                    request.transferTo(OutputStream.nullOutputStream());
                    if (middleware) {
                        exchange.sendResponseHeaders(200, 0);
                        OutputStream response = exchange.getResponseBody();
                        response.write("<FaxJob><document>".getBytes(StandardCharsets.UTF_8));
                        writeBase64(response, currentFile.get());
                        response.write(("</document><jobId>3512</jobId><status>SENT</status></FaxJob>")
                                .getBytes(StandardCharsets.UTF_8));
                    } else {
                        byte[] response = "{\"Status\":\"Success\",\"Result\":\"3512\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, response.length);
                        exchange.getResponseBody().write(response);
                    }
                } else {
                    request.transferTo(OutputStream.nullOutputStream());
                    exchange.sendResponseHeaders(200, 0); // Deliberately chunked: no honest Content-Length shortcut.
                    OutputStream response = exchange.getResponseBody();
                    response.write((middleware ? "{\"document\":\"" : "{\"Result\":\"").getBytes(StandardCharsets.UTF_8));
                    writeBase64(response, currentFile.get());
                    response.write((middleware ? "\",\"status\":\"RECEIVED\"}"
                            : "\",\"Status\":\"Success\"}").getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable failure) {
                serverFailure.set(failure);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            for (int size : new int[] {8, 256}) {
                Path original = directory.resolve("original-" + size + ".pdf");
                createPdf(original, size * 1024L * 1024L);
                currentFile.set(original);
                byte[] expected;
                try (var input = Files.newInputStream(original)) {
                    expected = digest(input);
                }
                for (boolean middleware : new boolean[] {false, true}) {
                    FaxProviderClient client = middleware ? new MiddlewareFaxProviderClient() : new SRFaxProviderClient(endpoint);
                    FaxConfig config = new FaxConfig() {
                        @Override
                        public String getFaxPasswd() {
                            return "dummy";
                        }
                        @Override
                        public String getPasswd() {
                            return "dummy";
                        }
                    };
                    config.setProviderType(middleware ? FaxConfig.ProviderType.MIDDLEWARE : FaxConfig.ProviderType.SRFAX);
                    config.setFaxUser("dummy");
                    config.setSiteUser("dummy");
                    config.setFaxNumber("9055551234");
                    config.setSenderEmail("fax@example.invalid");
                    config.setUrl(endpoint);
                    config.setId(3512);
                    FaxJob fax = new FaxJob();
                    fax.setFile_name("synthetic.pdf");
                    fax.setDestination("9055551234");
                    fax.setUser("dummy");
                    measure((middleware ? "middleware" : "srfax") + " inbound sizeMiB=" + size, () -> {
                        FaxJob downloaded = client.downloadFax(config, fax);
                        if (downloaded.getDocument() != null) {
                            throw new AssertionError("Download materialized base64");
                        }
                        Path incoming = quarantine(directory, config, fax, downloaded);
                        if (incoming == null) {
                            throw new AssertionError("PDF did not pass quarantine validation");
                        }
                        try (var input = Files.newInputStream(incoming)) {
                            if (!MessageDigest.isEqual(expected, digest(input))) {
                                throw new AssertionError("Inbound digest mismatch");
                            }
                        } finally {
                            Files.delete(incoming);
                        }
                    });
                    measure((middleware ? "middleware" : "srfax") + " outbound sizeMiB=" + size, () -> {
                        FaxJob sent = client.sendFax(config, fax, original);
                        if (!Long.valueOf(3512).equals(sent.getJobId())
                                || !MessageDigest.isEqual(expected, receivedDigest.get())) {
                            throw new AssertionError("Outbound digest/job mismatch");
                        }
                    });
                    if (serverFailure.get() != null) {
                        throw new AssertionError("Mock provider failed", serverFailure.get());
                    }
                }
                Files.delete(original);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void writeBase64(OutputStream response, Path file) throws Exception {
        OutputStream shield = new FilterOutputStream(response) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws java.io.IOException {
                out.write(bytes, offset, length);
            }
            @Override
            public void close() throws java.io.IOException {
                flush();
            }
        };
        try (var encoded = Base64.getEncoder().wrap(shield);
                var input = Files.newInputStream(file)) {
            input.transferTo(encoded);
        }
    }

    private static Path quarantine(Path directory, FaxConfig config, FaxJob metadata, FaxJob downloaded) throws Exception {
        var constructor = FaxImporter.class.getDeclaredConstructors();
        var selected = java.util.Arrays.stream(constructor).filter(c -> c.getParameterCount() == 6).findFirst().orElseThrow();
        selected.setAccessible(true);
        FaxImporter importer = (FaxImporter) selected.newInstance(null, null, null, null, null, directory.toString());
        var incoming = FaxImporter.class.getDeclaredField("faxIncomingDir");
        incoming.setAccessible(true);
        incoming.set(importer, directory.resolve("incoming"));
        var save = FaxImporter.class.getDeclaredMethod("saveToIncoming", FaxConfig.class, FaxJob.class, FaxJob.class);
        save.setAccessible(true);
        return (Path) save.invoke(importer, config, metadata, downloaded);
    }

    private static void createPdf(Path file, long bytes) throws Exception {
        try (var output = Files.newOutputStream(file)) {
            long[] offsets = new long[6];
            long position = write(output, "%PDF-1.4\n");
            String[] objects = {"", "<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] /Contents 4 0 R >>"};
            for (int i = 1; i <= 3; i++) {
                offsets[i] = position;
                position += write(output, i + " 0 obj\n" + objects[i] + "\nendobj\n");
            }
            offsets[4] = position;
            position += write(output, "4 0 obj\n<< /Length " + bytes + " >>\nstream\n");
            byte[] chunk = new byte[8192];
            java.util.Arrays.fill(chunk, (byte) ' ');
            for (long remaining = bytes; remaining > 0;) {
                int count = (int) Math.min(remaining, chunk.length);
                output.write(chunk, 0, count);
                remaining -= count;
            }
            position += bytes + write(output, "\nendstream\nendobj\n");
            long xref = position;
            write(output, "xref\n0 5\n0000000000 65535 f \n");
            for (int i = 1; i <= 4; i++) {
                write(output, String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets[i]));
            }
            write(output, "trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        }
    }

    private static long write(OutputStream output, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes);
        return bytes.length;
    }

    private static byte[] digest(InputStream input) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        new DigestInputStream(input, hash).transferTo(OutputStream.nullOutputStream());
        return hash.digest();
    }

    private static String seek(InputStream input, String marker, int limit) throws Exception {
        StringBuilder prefix = new StringBuilder();
        while (prefix.length() < limit) {
            int value = input.read();
            if (value < 0) {
                throw new java.io.IOException("Missing request marker " + marker);
            }
            prefix.append((char) value);
            if (prefix.toString().endsWith(marker)) {
                return prefix.toString();
            }
        }
        throw new java.io.IOException("Unexpectedly large request metadata");
    }

    private static String readUntil(InputStream input, char delimiter, int limit) throws Exception {
        StringBuilder text = new StringBuilder();
        int value;
        while ((value = input.read()) >= 0 && value != delimiter && text.length() < limit) {
            text.append((char) value);
        }
        return text.toString();
    }

    private interface Operation {
        void run() throws Exception;
    }

    private static void measure(String label, Operation operation) throws Exception {
        System.gc();
        AtomicLong peak = new AtomicLong();
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Runtime runtime = Runtime.getRuntime();
                    peak.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), Math::max);
                    Thread.sleep(2);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            operation.run();
        } finally {
            sampler.interrupt();
            sampler.join();
        }
        System.out.println("STREAMING_HEAP " + label + " peakUsedMiB=" + peak.get() / (1024 * 1024)
                + " maxHeapMiB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }
}
