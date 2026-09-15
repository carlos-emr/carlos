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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
class SMTPEmailSenderTransportIntegrationTest extends CarlosUnitTestBase {
    private LoggedInInfo caller;

    @BeforeEach
    void setUp() {
        caller = mock(LoggedInInfo.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        createAndRegisterMock(JavaMailSender.class);
        createAndRegisterMock(NioFileManager.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreservePreparedMimeBytes_andReportMissingAcknowledgement(boolean dropAcknowledgement) throws Exception {
        try (ServerSocket receiver = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            receiver.setSoTimeout(10_000);
            CompletableFuture<byte[]> received = new CompletableFuture<>();
            Thread.ofPlatform().daemon(true).start(() -> receive(receiver, received, dropAcknowledgement));
            EmailConfig config = new EmailConfig();
            config.setEmailType(EmailConfig.EmailType.SMTP);
            config.setEmailProvider(EmailConfig.EmailProvider.LOCAL);
            config.setSenderEmail("sender@example.test");
            config.setSenderFirstName("Synthetic");
            config.setSenderLastName("Sender");
            config.setConfigDetailsJson("{\"host\":\"127.0.0.1\",\"port\":\"" + receiver.getLocalPort() + "\"}");
            SMTPEmailSender sender = new LocalSMTPEmailSender(caller, config,
                    new String[]{"recipient@example.test"}, "Synthetic archive transport test",
                    "First line\r\n.dot-stuffed line\r\nFinal line", List.of());
            byte[] archived = sender.prepareMessageBytes();

            if (dropAcknowledgement) {
                assertThatThrownBy(sender::sendPreparedMessage).isInstanceOfSatisfying(
                        EmailSendingException.class,
                        failure -> assertThat(failure.isDeliveryOutcomeUncertain()).isTrue());
            } else {
                sender.sendPreparedMessage();
            }

            assertThat(received.get(10, TimeUnit.SECONDS)).isEqualTo(archived);
            assertThat(sender.getPreparedAttachments()).isEmpty();
            assertThatThrownBy(sender::sendPreparedMessage).isInstanceOf(EmailSendingException.class)
                    .hasMessageContaining("must be prepared");
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
