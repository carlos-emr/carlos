/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
import io.github.carlos_emr.carlos.integration.ebs.client.ng.DownloadInInterceptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.cxf.message.MessageImpl;

/** Local, synthetic-only acceptance oracle; never contacts Ministry or SRFax. */
public final class EbsPayloadPrivacyProbe {
    public static void main(String[] args) throws Exception {
        String canary = "FAKE_5700_SOAP_PRIVACY_CANARY";
        var message = new MessageImpl();
        message.setContent(java.io.InputStream.class, new ByteArrayInputStream(
                ("<synthetic>" + canary + "</synthetic>").getBytes(StandardCharsets.UTF_8)));
        var captured = new ByteArrayOutputStream();
        var original = System.out;
        try (var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            new DownloadInInterceptor().handleMessage(message);
        } finally {
            System.setOut(original);
        }
        boolean leaked = captured.toString(StandardCharsets.UTF_8).contains(canary);
        // Only boolean metadata leaves memory. Raw payload/log content is never retained.
        Files.writeString(Path.of(args[0]), "{\"schemaVersion\":1,\"syntheticOnly\":true,\"soapPayloadLeaked\":" + leaked + "}\n");
        try (var replay = message.getContent(java.io.InputStream.class)) {
            if (replay != null) replay.readAllBytes();
        }
    }
}
