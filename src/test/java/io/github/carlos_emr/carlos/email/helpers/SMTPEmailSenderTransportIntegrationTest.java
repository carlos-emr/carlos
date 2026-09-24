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
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.mockStatic;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private MockedStatic<SpringUtils> springUtils;
    private LoggedInInfo caller;

    @BeforeEach
    void setUp() {
        springUtils = mockStatic(SpringUtils.class);
        caller = mock(LoggedInInfo.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NioFileManager fileManager = mock(NioFileManager.class);
        springUtils.when(() -> SpringUtils.getBean(JavaMailSender.class)).thenReturn(mailSender);
        springUtils.when(() -> SpringUtils.getBean(NioFileManager.class)).thenReturn(fileManager);
    }

    @AfterEach
    void tearDown() {
        if (springUtils != null) {
            springUtils.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreservePreparedMimeBytes_whenAcknowledgementVaries(boolean dropAcknowledgement) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<byte[]> received = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(() -> receive(receiver, received, dropAcknowledgement));
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    new String[]{"recipient@example.test"}, "Synthetic archive transport test",
                    "First line\r\n.dot-stuffed line\r\nFinal line", List.of());
            byte[] archived = sender.prepareArtifactBytes();

            if (dropAcknowledgement) {
                assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                        EmailSendingException.class,
                        failure -> assertThat(failure.isDeliveryOutcomeUncertain()).isTrue());
            } else {
                sender.sendPrepared();
            }

            assertThat(received.get(10, TimeUnit.SECONDS)).isEqualTo(archived);
            assertThat(sender.getPreparedAttachments()).isEmpty();
            assertThatThrownBy(sender::sendPrepared).isInstanceOf(EmailSendingException.class)
                    .hasMessageContaining("must be prepared");
        }
    }

    /**
     * Drives the real transport into a 550 at RCPT TO (#3857). With one recipient, or with two
     * where only one is refused, the transport resets before DATA, so nothing reaches the
     * receiver and the failure must be reported as definite rather than uncertain.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReportDefiniteFailure_whenServerRefusesRecipient(boolean alsoAcceptedRecipient) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<Boolean> dataReceived = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(() -> refuseRecipient(receiver, dataReceived));
            String[] recipients = alsoAcceptedRecipient
                    ? new String[]{"accepted@example.test", "unknown@example.test"}
                    : new String[]{"unknown@example.test"};
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, localConfig(receiver.getLocalPort()),
                    recipients, "Synthetic refused recipient", "Body", List.of());
            sender.prepareArtifactBytes();

            assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(
                    EmailSendingException.class,
                    failure -> assertThat(failure.isDeliveryOutcomeUncertain()).isFalse());
            assertThat(dataReceived.get(10, TimeUnit.SECONDS)).isFalse();
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

    /** Accepts every command except RCPT TO for an address starting "unknown", and records whether DATA arrived. */
    private static void refuseRecipient(ServerSocket receiver, CompletableFuture<Boolean> dataReceived) {
        try (Socket connection = receiver.accept()) {
            connection.setSoTimeout(10_000);
            var output = connection.getOutputStream();
            var input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
            output.write("220 synthetic SMTP ready\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String line;
            while ((line = input.readLine()) != null) {
                String reply;
                if (line.startsWith("RCPT TO:<unknown")) {
                    reply = "550 5.1.1 Recipient address rejected: User unknown";
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

    private static void receive(ServerSocket receiver, CompletableFuture<byte[]> received, boolean dropAcknowledgement) {
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
                    if (dropAcknowledgement) return;
                    output.write("250 accepted\r\n".getBytes(StandardCharsets.US_ASCII));
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
