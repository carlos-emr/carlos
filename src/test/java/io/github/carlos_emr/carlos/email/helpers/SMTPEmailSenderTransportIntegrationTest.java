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
 */
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.mockStatic;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.EmailSendingException.Refusal;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises JavaMail's actual SMTP transport against a loopback-only synthetic receiver.
 *
 * @since 2026-09-15
 */
@Tag("integration")
class SMTPEmailSenderTransportIntegrationTest {
    @TempDir
    private Path tempDir;
    private MockedStatic<SpringUtils> springUtils;
    private LoggedInInfo caller;
    private final List<Path> generatedPdfs = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        springUtils = mockStatic(SpringUtils.class);
        caller = mock(LoggedInInfo.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NioFileManager fileManager = mock(NioFileManager.class);
        when(fileManager.createManagedTempFile(any(), any())).thenAnswer(invocation ->
                Files.createTempFile(tempDir, invocation.getArgument(0), invocation.getArgument(1)));
        springUtils.when(() -> SpringUtils.getBean(JavaMailSender.class)).thenReturn(mailSender);
        springUtils.when(() -> SpringUtils.getBean(NioFileManager.class)).thenReturn(fileManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (springUtils != null) {
            springUtils.close();
        }
        for (Path pdf : generatedPdfs) Files.deleteIfExists(pdf);
    }

    @ParameterizedTest
    @MethodSource("messageInputs")
    void shouldPreservePreparedMimeBytes_whenAcknowledgementVaries(boolean dropAcknowledgement, String body) throws Exception {
        KeyPair keyPair = PdfSigningTestSupport.createKeyPair("EC", 256);
        Path signed = PDFSigningUtil.signPDF(PdfSigningTestSupport.writeSinglePagePdf(tempDir.resolve("source.pdf")),
                PdfSigningTestSupport.enabledConfig(tempDir.resolve("fake.p12"), keyPair.getPrivate(),
                        PdfSigningTestSupport.createSelfSignedCertificate(keyPair, "SHA256withECDSA")));
        generatedPdfs.add(signed);
        byte[] signedBytes = Files.readAllBytes(signed);
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<byte[]> received = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(
                    () -> receive(receiver, received, dropAcknowledgement ? null : "250 accepted"));
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    new String[]{"recipient@example.test"}, "Synthetic archive transport test",
                    body, List.of(new EmailAttachment("signed.pdf", signed.toString(), DocumentType.DOC, 101)));
            byte[] archived = sender.prepareArtifactBytes();
            assertThat(pdfAttachmentBytes(new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(archived)))).isEqualTo(signedBytes);

            if (dropAcknowledgement) {
                assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                        EmailSendingException.class,
                        failure -> assertThat(failure.isDeliveryOutcomeUncertain()).isTrue());
            } else {
                sender.sendPrepared();
            }

            byte[] captured = received.get(10, TimeUnit.SECONDS);
            assertThat(captured).isEqualTo(archived);
            byte[] receivedPdf = pdfAttachmentBytes(new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(captured)));
            assertThat(receivedPdf).isEqualTo(signedBytes);
            Path delivered = Files.write(tempDir.resolve("delivered.pdf"), receivedPdf);
            assertThat(PdfSigningTestSupport.verifyDetachedSignature(delivered, null)).isTrue();
            assertThat(sender.getPreparedAttachments()).isEmpty();
            assertThatThrownBy(sender::sendPrepared).isInstanceOf(EmailSendingException.class)
                    .hasMessageContaining("must be prepared");
        }
    }

    private static Stream<Arguments> messageInputs() {
        return Stream.of("First line\r\n.dot-stuffed line\r\nFinal line",
                "First line\n.dot-stuffed line\nFinal line",
                "First line\r.dot-stuffed line\rFinal line",
                "First line\r\n.dot-stuffed line\nAnother line\rFinal line")
                .flatMap(body -> Stream.of(Arguments.of(false, body), Arguments.of(true, body)));
    }

    private static byte[] pdfAttachmentBytes(Part part) throws Exception {
        if ("signed.pdf".equals(part.getFileName())) return part.getInputStream().readAllBytes();
        if (part.isMimeType("multipart/*")) {
            Multipart body = (Multipart) part.getContent();
            for (int i = 0; i < body.getCount(); i++) {
                byte[] found = pdfAttachmentBytes(body.getBodyPart(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Drives the real transport into a rejection at the end of the content: the server answers
     * 354 to DATA, reads the whole message, then refuses it. The message reached the server, so
     * the outcome must stay uncertain (PENDING), not FAILED: a resend could duplicate it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"554 5.7.1 Message rejected by policy", "451 4.3.0 Temporary queue failure"})
    void shouldKeepOutcomeUncertain_whenServerRejectsAfterContent(String rejection) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<byte[]> received = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(() -> receive(receiver, received, rejection));
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    new String[]{"recipient@example.test"}, "Synthetic rejected content", "Body", List.of());
            byte[] archived = sender.prepareArtifactBytes();

            assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                    EmailSendingException.class, failure -> assertThat(failure.isDeliveryOutcomeUncertain()).isTrue());
            assertThat(received.get(10, TimeUnit.SECONDS)).isEqualTo(archived);
        }
    }

    /**
     * Drives the real transport into a refusal at RCPT TO (#3857): permanent (550) or temporary
     * (450). With one recipient, or with two where only one is refused, the transport resets
     * before DATA, so nothing reaches the receiver and the failure must be reported as definite
     * rather than uncertain.
     */
    @ParameterizedTest
    @CsvSource({"false, 550 5.1.1 Recipient address rejected: User unknown", "true, 550 5.1.1 Recipient address rejected: User unknown",
            "false, 450 4.2.0 Greylisted, try again later"})
    void shouldReportDefiniteFailure_whenServerRefusesRecipient(boolean alsoAcceptedRecipient, String refusal) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<Boolean> dataReceived = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(() -> refuseCommand(receiver, dataReceived, "RCPT TO:<unknown", refusal));
            String[] recipients = alsoAcceptedRecipient
                    ? new String[]{"accepted@example.test", "unknown@example.test"}
                    : new String[]{"unknown@example.test"};
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    recipients, "Synthetic refused recipient", "Body", List.of());
            sender.prepareArtifactBytes();

            assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                    EmailSendingException.class, failure -> {
                        assertThat(failure.isDeliveryOutcomeUncertain()).isFalse();
                        assertThat(failure.getRefusal()).isEqualTo(Refusal.RECIPIENT);
                    });
            assertThat(dataReceived.get(10, TimeUnit.SECONDS)).isFalse();
        }
    }

    /**
     * Drives the real transport into a refusal at MAIL FROM, as from a relay that will not send
     * for the sending address: permanent (553) or temporary (451). Angus stops before RCPT TO,
     * so nothing reaches the receiver and the failure must be definite.
     */
    @ParameterizedTest
    @ValueSource(strings = {"553 5.7.1 Sender address rejected: not owned by user",
            "451 4.3.0 Temporary sender lookup failure"})
    void shouldReportDefiniteFailure_whenServerRefusesSender(String refusal) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<Boolean> dataReceived = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(() -> refuseCommand(receiver, dataReceived, "MAIL FROM:", refusal));
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    new String[]{"recipient@example.test"}, "Synthetic refused sender", "Body", List.of());
            sender.prepareArtifactBytes();

            assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                    EmailSendingException.class, failure -> {
                        assertThat(failure.isDeliveryOutcomeUncertain()).isFalse();
                        assertThat(failure.getRefusal()).isEqualTo(Refusal.SENDER);
                    });
            assertThat(dataReceived.get(10, TimeUnit.SECONDS)).isFalse();
        }
    }

    /**
     * Drives the real transport into a refused DATA command after the sender and recipient were
     * accepted: temporary (451) or policy (554). The content is sent only after a 354, so the
     * failure must be definite, with no address refused.
     */
    @ParameterizedTest
    @ValueSource(strings = {"451 4.3.0 Temporary queue failure", "554 5.7.1 Message refused by policy"})
    void shouldReportDefiniteFailure_whenServerRefusesData(String refusal) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<Boolean> sessionEnded = new CompletableFuture<>();
            List<String> afterRefusal = new CopyOnWriteArrayList<>();
            Thread.ofPlatform().daemon(true).start(
                    () -> refuseCommand(receiver, sessionEnded, "DATA", refusal, afterRefusal));
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    new String[]{"recipient@example.test"}, "Synthetic refused data", "Body", List.of());
            sender.prepareArtifactBytes();

            assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                    EmailSendingException.class, failure -> {
                        assertThat(failure.isDeliveryOutcomeUncertain()).isFalse();
                        assertThat(failure.getRefusal()).isEqualTo(Refusal.NONE);
                    });
            sessionEnded.get(10, TimeUnit.SECONDS);
            // The premise of the change: after a refused DATA the client resets and quits, and no
            // header, body line or end-of-data "." ever reaches the server.
            assertThat(afterRefusal).containsExactly("RSET", "QUIT");
        }
    }

    private static EmailConfig localConfig(int port) {
        EmailConfig config = new EmailConfig();
        config.setEmailType(EmailConfig.EmailType.SMTP);
        config.setEmailProvider(EmailConfig.EmailProvider.LOCAL);
        config.setSenderEmail("sender@example.test");
        config.setSenderFirstName("Synthetic");
        config.setSenderLastName("Sender");
        config.setConfigDetailsJson("{\"host\":\"127.0.0.1\",\"port\":\"" + port + "\"}");
        return config;
    }

    /**
     * Answers {@code refusal} to any command starting {@code refusedCommand} and accepts the rest.
     * Records whether a DATA it was not told to refuse arrived; it answers that one with 554 at
     * once and never sends 354, so no message content can follow.
     */
    private static void refuseCommand(ServerSocket receiver, CompletableFuture<Boolean> dataReceived,
            String refusedCommand, String refusal) {
        refuseCommand(receiver, dataReceived, refusedCommand, refusal, new CopyOnWriteArrayList<>());
    }

    /** As above, also recording every line the client sends after the refused command. */
    private static void refuseCommand(ServerSocket receiver, CompletableFuture<Boolean> dataReceived,
            String refusedCommand, String refusal, List<String> afterRefusal) {
        try (Socket connection = receiver.accept()) {
            connection.setSoTimeout(10_000);
            var output = connection.getOutputStream();
            var input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
            output.write("220 synthetic SMTP ready\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String line;
            boolean refused = false;
            while ((line = input.readLine()) != null) {
                if (refused) {
                    afterRefusal.add(line);
                }
                String reply;
                if (line.startsWith(refusedCommand)) {
                    reply = refusal;
                    refused = true;
                } else if (line.equals("DATA")) {
                    // Refuse at once so a regression fails fast instead of waiting out the I/O timeout.
                    dataReceived.complete(true);
                    output.write("554 test receiver: DATA must not be reached\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    return;
                } else if (line.equals("QUIT")) {
                    output.write("221 bye\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    break;
                } else {
                    reply = "250 OK";
                }
                output.write((reply + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.flush();
            }
            dataReceived.complete(false);
        } catch (Exception failure) {
            dataReceived.completeExceptionally(failure);
        }
    }

    /**
     * Accepts every command and the message content, then answers the end of the content with
     * {@code acknowledgement}, or closes the connection without replying when it is null.
     */
    private static void receive(ServerSocket receiver, CompletableFuture<byte[]> received, String acknowledgement) {
        try (Socket connection = receiver.accept()) {
            connection.setSoTimeout(10_000);
            var output = connection.getOutputStream();
            var input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
            output.write("220 synthetic SMTP ready\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String line;
            while ((line = input.readLine()) != null) {
                if (line.equals("DATA")) {
                    output.write("354 continue\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        String content = line.startsWith("..") ? line.substring(1) : line;
                        bytes.write((content + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    }
                    if (line == null) throw new java.io.EOFException("Incomplete SMTP DATA");
                    received.complete(bytes.toByteArray());
                    if (acknowledgement == null) return;
                    output.write((acknowledgement + "\r\n").getBytes(StandardCharsets.US_ASCII));
                } else if (line.equals("QUIT")) {
                    output.write("221 bye\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    return;
                } else {
                    output.write("250 OK\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                output.flush();
            }
        } catch (Exception failure) {
            received.completeExceptionally(failure);
        }
    }
}
