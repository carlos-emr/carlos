/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.email.archive.EmailArchiveRedactor;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSender;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The device code email (clinic code v1, mycarlos#141) through the real outbound archive: the
 * message CARLOS would send is built by {@link EmailSender} and redacted by the real
 * {@link EmailArchiveRedactor}, in every language. The redactor refuses a value missing from any
 * text alternative, and that refusal stops the send, so this is what lets the code email go out.
 */
@Tag("unit")
class PortalMyCarlosDeviceCodeEmailArchiveUnitTest extends CarlosUnitTestBase {
    private static final String DISPLAY = "482 193 644";

    @TempDir
    Path tempDir;

    private LoggedInInfo system;

    @BeforeEach
    void setUp() throws Exception {
        var security = mock(SecurityInfoManager.class);
        var files = mock(NioFileManager.class);
        system = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(NioFileManager.class, files);
        registerMock(JavaMailSender.class, mock(JavaMailSender.class));
        when(files.createManagedTempFile(anyString(), anyString()))
                .thenAnswer(call -> Files.createTempFile(tempDir, call.getArgument(0), call.getArgument(1)));
        when(security.hasPrivilege(system, "_email", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    private static EmailConfig smtp() {
        var config = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.GMAIL, "FAKE.clinic@example.com");
        config.setSenderFirstName("FAKE");
        config.setSenderLastName("Clinic");
        config.setConfigDetailsJson("{\"host\":\"smtp.example.com\",\"port\":\"587\",\"username\":\"u\",\"password\":\"p\"}");
        return config;
    }

    private static List<String> textParts(Part part) throws Exception {
        var texts = new ArrayList<String>();
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) texts.addAll(textParts(multipart.getBodyPart(i)));
        } else if (content instanceof String text && (part.isMimeType("text/plain") || part.isMimeType("text/html"))) {
            texts.add(text);
        }
        return texts;
    }

    @Test
    void shouldArchiveCodeEmailWithoutCode_inEveryLanguage() throws Exception {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.of("pl"), Locale.of("pt", "BR"))) {
            EmailData email = PortalMyCarlosDeviceCodeEmailComposer.request(42, "fake.patient@example.com", 3, "-1",
                    PortalMyCarlosDeviceCodeEmailComposer.subject(locale),
                    PortalMyCarlosDeviceCodeEmailComposer.body(locale, "FAKE Clinic", DISPLAY, 10), DISPLAY);
            // A clinic footer, as every real send carries one.
            email.setFooter("FAKE Clinic<br>Not monitored for urgent issues.");
            var log = new EmailLog(smtp(), "FAKE.clinic@example.com", email.getRecipients(), email.getSubject(),
                    email.getBody(), EmailLog.EmailStatus.PENDING);
            var sender = new EmailSender(system, smtp(), email);
            try {
                var archive = sender.prepareOutboundArchive(log);
                var sent = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(archive.getArtifactBytes()));
                assertThat(textParts(sent)).as(locale.toString()).isNotEmpty().allMatch(text -> text.contains(DISPLAY));

                byte[][] redacted = new byte[1][];
                assertThatCode(() -> redacted[0] = EmailArchiveRedactor.redact(archive.getArtifactType(),
                        archive.getArtifactBytes(), email.getArchiveRedactions())).as(locale.toString()).doesNotThrowAnyException();
                var kept = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(redacted[0]));
                assertThat(textParts(kept)).as(locale.toString()).isNotEmpty()
                        .allMatch(text -> text.contains("[redacted]") && !text.contains(DISPLAY)
                                && !text.contains(DISPLAY.replace(" ", "")));
            } finally {
                sender.discardPrepared();
            }
        }
    }
}
