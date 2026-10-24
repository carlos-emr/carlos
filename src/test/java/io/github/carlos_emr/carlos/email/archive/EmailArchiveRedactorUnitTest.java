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
package io.github.carlos_emr.carlos.email.archive;

import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class EmailArchiveRedactorUnitTest {
    private static final String CODE = "Xy7kQ2mN9pR4tV8wZ1bC5dF0gH3jK6lM-_aBcDeFgHi";

    @Test
    void shouldRedactEveryDecodedAlternative_whenOnlyOneCodeOccursVerbatimInMime() throws Exception {
        String html = "<p>" + "Préparation à la clinique — ".repeat(20) + CODE + "</p><b>FAKE Clinic</b>";
        String raw = "Message-ID: <fake-original@test>\r\nMIME-Version: 1.0\r\nContent-Type: multipart/alternative; boundary=FAKE\r\n\r\n"
                + "--FAKE\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 7bit\r\n\r\n"
                + "Code: " + CODE + "\r\nFAKE Clinic\r\n"
                + "--FAKE\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                + Base64.getMimeEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)) + "\r\n--FAKE--\r\n";
        byte[] original = raw.getBytes(StandardCharsets.US_ASCII);
        byte[] saved = EmailArchiveRedactor.redact(OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822, original, List.of(CODE));
        MimeMessage decoded = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(saved));
        assertThat(decoded.getMessageID()).isEqualTo("<fake-original@test>");
        Multipart alternatives = (Multipart) decoded.getContent();
        assertThat(alternatives.getCount()).isEqualTo(2);
        for (int i = 0; i < alternatives.getCount(); i++) {
            assertThat((String) alternatives.getBodyPart(i).getContent())
                    .contains("[redacted]", "FAKE Clinic").doesNotContain(CODE);
        }
        assertThat((String) alternatives.getBodyPart(1).getContent()).contains("Préparation à la clinique —");
        assertThat(original).isEqualTo(raw.getBytes(StandardCharsets.US_ASCII));
        MimeMessage sent = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(original));
        Multipart sentAlternatives = (Multipart) sent.getContent();
        assertThat((String) sentAlternatives.getBodyPart(1).getContent()).contains(CODE);
    }

    @Test
    void shouldRefuseWhenAnAlternativeCannotBeRedacted_evenIfAnotherContainsCode() {
        String raw = "MIME-Version: 1.0\r\nContent-Type: multipart/alternative; boundary=FAKE\r\n\r\n"
                + "--FAKE\r\nContent-Type: text/plain\r\n\r\n" + CODE
                + "\r\n--FAKE\r\nContent-Type: text/html\r\n\r\n<p>Missing value</p>\r\n--FAKE--\r\n";
        assertThatThrownBy(() -> EmailArchiveRedactor.redact(OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822,
                raw.getBytes(StandardCharsets.US_ASCII), List.of(CODE))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRedactDecodedSendGridPlainAndHtml_andRetainOtherPayloadFields() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.createObjectNode();
        payload.put("subject", "Your invitation");
        var content = payload.putArray("content");
        content.addObject().put("type", "text/plain").put("value", "Code: " + CODE + "\nFAKE Clinic");
        content.addObject().put("type", "text/html").put("value", "<p>" + CODE + "</p><b>Clinique É</b>");
        byte[] original = mapper.writeValueAsBytes(payload);
        var saved = mapper.readTree(EmailArchiveRedactor.redact(OutboundEmailArchive.ARTIFACT_TYPE_API_PAYLOAD,
                original, List.of(CODE)));
        for (var item : saved.path("content")) {
            assertThat(item.path("value").asText()).contains("[redacted]").doesNotContain(CODE);
        }
        assertThat(saved.path("subject").asText()).isEqualTo("Your invitation");
        assertThat(mapper.readTree(original).path("content").get(1).path("value").asText()).contains(CODE);
    }
}
