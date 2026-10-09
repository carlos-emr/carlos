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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.model.OutboundEmailArchive;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;

/** Redacts decoded invitation text in every alternative of an archived copy, never the sent copy. */
public final class EmailArchiveRedactor {
    private EmailArchiveRedactor() { }

    public static byte[] redact(String type, byte[] original, List<String> values) throws Exception {
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("Redaction values are missing");
        }
        if (OutboundEmailArchive.ARTIFACT_TYPE_SMTP_RFC822.equals(type)) {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(original));
            String messageId = message.getMessageID();
            if (redactPart(message, values, 0) == 0) {
                throw new IllegalArgumentException("Email text is missing");
            }
            message.saveChanges();
            if (messageId != null) {
                message.setHeader("Message-ID", messageId);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            byte[] result = output.toByteArray();
            verifyPart(new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(result)), values, 0);
            return result;
        }
        if (OutboundEmailArchive.ARTIFACT_TYPE_API_PAYLOAD.equals(type)) {
            ObjectMapper mapper = new ObjectMapper();
            var root = mapper.readTree(original);
            var contents = root.path("content");
            if (!contents.isArray() || contents.isEmpty()) {
                throw new IllegalArgumentException("Email text is missing");
            }
            for (var content : contents) {
                if (!(content instanceof ObjectNode node)
                        || !("text/plain".equals(node.path("type").asText())
                        || "text/html".equals(node.path("type").asText()))) {
                    throw new IllegalArgumentException("Unknown email content");
                }
                node.put("value", replace(node.path("value").asText(), values));
            }
            return mapper.writeValueAsBytes(root);
        }
        throw new IllegalArgumentException("Unknown email archive format");
    }

    private static int redactPart(Part part, List<String> values, int depth) throws Exception {
        if (depth > 64) { throw new IllegalArgumentException("Email nesting limit exceeded"); }
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) { return 0; }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            int count = 0;
            for (int i = 0; i < multipart.getCount(); i++) {
                count += redactPart(multipart.getBodyPart(i), values, depth + 1);
            }
            return count;
        }
        if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (!(content instanceof String text)) {
                throw new IllegalArgumentException("Email text could not be decoded");
            }
            part.setContent(replace(text, values), part.getContentType());
            part.removeHeader("Content-Transfer-Encoding");
            return 1;
        }
        return 0;
    }

    private static String replace(String text, List<String> values) {
        for (String value : values) {
            // Each body alternative must be redacted successfully; finding one raw occurrence
            // cannot prove another transfer-encoded alternative no longer holds the credential.
            if (!text.contains(value)) {
                throw new IllegalArgumentException("Required value is missing from an email alternative");
            }
            text = text.replace(value, "[redacted]");
        }
        return text;
    }

    private static void verifyPart(Part part, List<String> values, int depth) throws Exception {
        if (depth > 64) { throw new IllegalArgumentException("Email nesting limit exceeded"); }
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) { return; }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                verifyPart(multipart.getBodyPart(i), values, depth + 1);
            }
        } else if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            Object decoded = part.getContent();
            if (!(decoded instanceof String text) || values.stream().anyMatch(text::contains)) {
                throw new IllegalArgumentException("Email redaction verification failed");
            }
        }
    }
}
