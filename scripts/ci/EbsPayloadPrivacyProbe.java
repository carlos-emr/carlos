/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
import io.github.carlos_emr.carlos.integration.ebs.client.ng.DownloadInInterceptor;
import jakarta.activation.DataHandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;

/** Local, synthetic-only acceptance oracle; never contacts Ministry or SRFax. */
public final class EbsPayloadPrivacyProbe {
    private static final String CANARY = "FAKE_5700_SOAP_PRIVACY_CANARY";
    private static final String LOGGER = DownloadInInterceptor.class.getName();

    private static final class Capture extends AbstractAppender {
        private boolean leaked;

        private Capture() {
            super("synthetic-ebs-privacy", null, null, false, null);
        }

        @Override
        public void append(LogEvent event) {
            leaked |= event.getMessage().getFormattedMessage().contains(CANARY);
            // Include suppressed exceptions: a fixed message with a payload-bearing
            // exception still discloses content through its rendered stack trace.
            if (event.getThrown() != null) {
                var rendered = new java.io.StringWriter();
                event.getThrown().printStackTrace(new java.io.PrintWriter(rendered));
                leaked |= rendered.toString().contains(CANARY);
            }
        }
    }

    private static final class Body extends InputStream {
        private final ByteArrayInputStream bytes = new ByteArrayInputStream(
                ("<synthetic>" + CANARY + "</synthetic>").getBytes(StandardCharsets.UTF_8));
        private boolean touched;

        @Override public int read() { touched = true; return bytes.read(); }
        @Override public int read(byte[] target, int offset, int length) {
            touched = true; return bytes.read(target, offset, length);
        }
        @Override public long skip(long length) { touched = true; return bytes.skip(length); }
        @Override public void close() { touched = true; }
    }

    private static final class Request extends OutputStream {
        private boolean touched;
        @Override public void write(int value) { touched = true; }
        @Override public void flush() { touched = true; }
        @Override public void close() { touched = true; }
    }

    private static final class Attachments extends AbstractCollection<Attachment> {
        private boolean touched;
        private final Attachment attachment = (Attachment) Proxy.newProxyInstance(
                Attachment.class.getClassLoader(), new Class<?>[]{Attachment.class}, (proxy, method, args) -> {
                    touched = true;
                    return switch (method.getName()) {
                        case "getId", "toString", "getHeader" -> CANARY;
                        case "getDataHandler" -> new DataHandler(CANARY, "text/plain");
                        case "isXOP" -> false;
                        case "getHeaderNames" -> List.of(CANARY).iterator();
                        default -> null;
                    };
                });

        @Override public Iterator<Attachment> iterator() {
            touched = true; return List.of(attachment).iterator();
        }
        @Override public int size() { touched = true; return 1; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || args.length == 2 && !args[1].equals("--logger-negative-control")) {
            throw new IllegalArgumentException("Expected output and optional logger negative control");
        }
        var body = new Body();
        var request = new Request();
        var attachments = new Attachments();
        var message = new MessageImpl();
        message.setContent(InputStream.class, body);
        message.setContent(OutputStream.class, request);
        message.put(Message.ATTACHMENTS, attachments);
        message.put(Message.RESPONSE_CODE, 200);
        message.put(Message.PROTOCOL_HEADERS, Map.of("Authorization", List.of(CANARY)));
        message.put("synthetic-private-map-value", CANARY);

        var capturedOut = new ByteArrayOutputStream();
        var capturedErr = new ByteArrayOutputStream();
        var originalOut = System.out;
        var originalErr = System.err;
        var context = (LoggerContext) LogManager.getContext(false);
        var configuration = context.getConfiguration();
        var previous = configuration.getLoggers().get(LOGGER);
        var logger = new LoggerConfig(LOGGER, Level.ALL, false);
        var capture = new Capture();
        capture.start();
        logger.addAppender(capture, Level.ALL, null);
        configuration.addLogger(LOGGER, logger);
        context.updateLoggers();
        boolean exercised = false;
        try (var output = new PrintStream(capturedOut, true, StandardCharsets.UTF_8);
             var error = new PrintStream(capturedErr, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            System.setErr(error);
            var inbound = new DownloadInInterceptor();
            var factory = DownloadInInterceptor.class.getDeclaredMethod("outbound");
            factory.setAccessible(true);
            var outbound = (DownloadInInterceptor) factory.invoke(null);
            for (var diagnostic : List.of(inbound, outbound)) {
                diagnostic.handleMessage(message);
                diagnostic.handleFault(message);
            }
            exercised = true;
            if (args.length == 2) {
                // A real logger event, with additivity disabled, proves that
                // detecting disclosures does not depend on console routing.
                LogManager.getLogger(LOGGER).error("Synthetic logger negative control: {}", CANARY);
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            configuration.removeLogger(LOGGER);
            if (previous != null) configuration.addLogger(LOGGER, previous);
            context.updateLoggers();
            capture.stop();
        }
        boolean leaked = capture.leaked || capturedOut.toString(StandardCharsets.UTF_8).contains(CANARY)
                || capturedErr.toString(StandardCharsets.UTF_8).contains(CANARY);
        boolean streamsPreserved = message.getContent(InputStream.class) == body
                && message.getContent(OutputStream.class) == request && !body.touched && !request.touched;
        boolean attachmentsUntouched = message.get(Message.ATTACHMENTS) == attachments && !attachments.touched;
        // Only booleans leave memory. Raw payload/log content is never retained.
        Files.writeString(Path.of(args[0]), "{\"schemaVersion\":2,\"syntheticOnly\":true,\"soapPayloadLeaked\":"
                + leaked + ",\"messageStreamsPreserved\":" + streamsPreserved
                + ",\"attachmentsUntouched\":" + attachmentsUntouched
                + ",\"diagnosticsExercised\":" + exercised + "}\n");
    }
}
