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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Per-window, patient-bound staging of an email's prepared attachments (#4425). */
@Tag("unit")
@Tag("security")
@Tag("email")
@DisplayName("EmailAttachmentStaging")
class EmailAttachmentStagingUnitTest {
    private static final String ATTRIBUTE = EmailAttachmentStaging.class.getName() + ".entries";

    private static EmailAttachment eForm(int fdid) {
        EmailAttachment attachment = new EmailAttachment("eform-" + fdid + ".pdf", "/tmp/eform-" + fdid + ".pdf",
                DocumentType.EFORM, fdid, 1024L);
        attachment.setPreviewToken("token-" + fdid);
        return attachment;
    }

    @Test
    @DisplayName("should give each window its own patient's attachments, whichever window composed last")
    void shouldTakeEachWindowsOwnAttachments_whenAnotherWindowComposedLater() {
        MockHttpSession session = new MockHttpSession();
        String windowX = EmailAttachmentStaging.stage(session, 10001, List.of(eForm(501))).key();
        String windowY = EmailAttachmentStaging.stage(session, 10002, List.of(eForm(502))).key();

        assertThat(windowX).isNotEqualTo(windowY);
        EmailAttachmentStaging.Prepared x = EmailAttachmentStaging.take(session, windowX);
        assertThat(x.demographicNo()).isEqualTo(10001);
        assertThat(x.attachments()).extracting(EmailAttachment::getDocumentId).containsExactly(501);
        EmailAttachmentStaging.Prepared y = EmailAttachmentStaging.take(session, windowY);
        assertThat(y.demographicNo()).isEqualTo(10002);
        assertThat(y.attachments()).extracting(EmailAttachment::getDocumentId).containsExactly(502);
        assertThat(session.getAttribute(ATTRIBUTE)).as("nothing left behind").isNull();
    }

    @Test
    @DisplayName("should yield nothing for a reused, unknown or malformed key")
    void shouldReturnNull_forReusedUnknownOrMalformedKey() {
        MockHttpSession session = new MockHttpSession();
        String key = EmailAttachmentStaging.stage(session, 10001, List.of(eForm(501))).key();

        assertThat(EmailAttachmentStaging.take(session, key)).isNotNull();
        assertThat(EmailAttachmentStaging.take(session, key)).as("a reused key").isNull();
        assertThat(EmailAttachmentStaging.take(session, "AAAAAAAAAAAAAAAAAAAAAA")).as("an unknown key").isNull();
        assertThat(EmailAttachmentStaging.take(session, null)).isNull();
        assertThat(EmailAttachmentStaging.take(session, "")).isNull();
        assertThat(EmailAttachmentStaging.take(session, "../emailAttachmentList")).isNull();
    }

    @Test
    @DisplayName("should stage an empty entry when the email has no attachments")
    void shouldStageEmptyEntry_withNoAttachments() {
        MockHttpSession session = new MockHttpSession();
        String key = EmailAttachmentStaging.stage(session, 10001, null).key();

        EmailAttachmentStaging.Prepared prepared = EmailAttachmentStaging.take(session, key);
        assertThat(prepared.demographicNo()).isEqualTo(10001);
        assertThat(prepared.attachments()).isEmpty();
    }

    @Test
    @DisplayName("should stage detached copies that later changes to the prepared list cannot reach")
    void shouldStageDetachedCopies_withoutEmailLogReference() {
        MockHttpSession session = new MockHttpSession();
        EmailAttachment source = new EmailAttachment(new EmailLog(), "lab.pdf", "/tmp/lab.pdf", DocumentType.LAB, 77);
        source.setFileSize(2048L);
        source.setPreviewToken("preview");
        List<EmailAttachment> prepared = new ArrayList<>(List.of(source));
        EmailAttachmentStaging.Staged staged = EmailAttachmentStaging.stage(session, 10001, prepared);

        prepared.add(eForm(999));
        source.setFilePath("/tmp/other.pdf");

        EmailAttachment copy = EmailAttachmentStaging.take(session, staged.key()).attachments().get(0);
        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getEmailLog()).isNull();
        assertThat(copy.getFileName()).isEqualTo("lab.pdf");
        assertThat(copy.getFilePath()).isEqualTo("/tmp/lab.pdf");
        assertThat(copy.getDocumentType()).isEqualTo(DocumentType.LAB);
        assertThat(copy.getDocumentId()).isEqualTo(77);
        assertThat(copy.getFileSize()).isEqualTo(2048L);
        assertThat(copy.getPreviewToken()).isEqualTo("preview");
        List<EmailAttachment> stagedList = staged.prepared().attachments();
        EmailAttachment another = eForm(1);
        assertThat(stagedList).hasSize(1);
        assertThatThrownBy(() -> stagedList.add(another)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should drop the oldest window's entry once the session holds the maximum")
    void shouldDropOldestEntry_whenMaximumExceeded() {
        MockHttpSession session = new MockHttpSession();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i <= EmailAttachmentStaging.MAX_ENTRIES; i++) {
            keys.add(EmailAttachmentStaging.stage(session, 10000 + i, List.of(eForm(500 + i))).key());
        }

        assertThat(EmailAttachmentStaging.take(session, keys.get(0))).as("the oldest window").isNull();
        for (int i = 1; i <= EmailAttachmentStaging.MAX_ENTRIES; i++) {
            assertThat(EmailAttachmentStaging.take(session, keys.get(i)).demographicNo()).isEqualTo(10000 + i);
        }
    }

    @Test
    @DisplayName("should replace the session value rather than change it in place")
    void shouldPublishUnmodifiableMap_forSessionReplication() {
        MockHttpSession session = new MockHttpSession();
        EmailAttachmentStaging.stage(session, 10001, List.of(eForm(501)));
        Object first = session.getAttribute(ATTRIBUTE);
        EmailAttachmentStaging.stage(session, 10002, List.of(eForm(502)));

        assertThat(session.getAttribute(ATTRIBUTE)).isNotSameAs(first);
        assertThat(first).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) first;
        assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should survive session serialization and redact its string form")
    void shouldSerializeAndRedact_forPersistedSessions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String key = EmailAttachmentStaging.stage(session, 10001, List.of(eForm(501))).key();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(session.getAttribute(ATTRIBUTE));
        }
        Object restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = in.readObject();
        }
        MockHttpSession restoredSession = new MockHttpSession();
        restoredSession.setAttribute(ATTRIBUTE, restored);

        EmailAttachmentStaging.Prepared prepared = EmailAttachmentStaging.take(restoredSession, key);
        assertThat(prepared.attachments()).extracting(EmailAttachment::getDocumentId).containsExactly(501);
        assertThat(prepared.toString()).doesNotContain("10001").doesNotContain("501").doesNotContain("eform");
    }
}
