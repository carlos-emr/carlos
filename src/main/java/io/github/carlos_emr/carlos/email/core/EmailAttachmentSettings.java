/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.email.core;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Data Transfer Object to encapsulate email attachment settings and IDs.
 * Reduces parameter count and centralizes parsing logic for email composition.
 * Used to pass attachment configuration between eForm actions and email composition.
 *
 * @since 2025-11-13
 */
public record EmailAttachmentSettings(
    String fdid,
    String demographicNo,
    String[] attachedEForms,
    String[] attachedDocuments,
    String[] attachedLabs,
    String[] attachedHRMDocuments,
    String[] attachedForms,
    boolean attachEFormItSelf,
    boolean openAfterEmail,
    boolean isEmailEncrypted,
    boolean isEmailAttachmentEncrypted,
    boolean isEmailAutoSend,
    boolean deleteEFormAfterEmail,
    String emailPDFPassword,
    String emailPDFPasswordClue,
    String senderEmail,
    String subjectEmail,
    String bodyEmail,
    String encryptedMessageEmail,
    String emailPatientChartOption
) {

    /** Maximum length for email password fields. */
    private static final int MAX_PASSWORD_LENGTH = 128;

    /** Maximum length for email subject field. */
    private static final int MAX_SUBJECT_LENGTH = 998;

    /** Maximum length for email body field. */
    private static final int MAX_BODY_LENGTH = 100_000;

    /** Maximum length for sender email address. */
    private static final int MAX_EMAIL_LENGTH = 254;

    /** Maximum length for encrypted message field. */
    private static final int MAX_ENCRYPTED_MSG_LENGTH = 100_000;

    /**
     * Creates an EmailAttachmentSettings instance from an HTTP request.
     *
     * <p>Boolean parameters are validated via {@code "true".equals()} / {@code !"false".equals()}
     * patterns (safe against arbitrary input). String parameters are sanitized: control characters
     * are stripped from password/subject fields, email addresses are format-validated, and all
     * string fields are length-limited to prevent unbounded session storage.</p>
     *
     * @param req The HTTP request containing the parameters.
     * @param fdid The eForm data ID.
     * @param demographicNo The demographic number.
     * @param eForms Array of attached eForm IDs.
     * @param docs Array of attached document IDs.
     * @param labs Array of attached lab IDs.
     * @param hrmDocs Array of attached HRM document IDs.
     * @param forms Array of attached form IDs.
     * @return A new EmailAttachmentSettings instance.
     */
    public static EmailAttachmentSettings of(
        HttpServletRequest req,
        String fdid,
        String demographicNo,
        String[] eForms,
        String[] docs,
        String[] labs,
        String[] hrmDocs,
        String[] forms
    ) {
        return new EmailAttachmentSettings(
            fdid,
            demographicNo,
            eForms,
            docs,
            labs,
            hrmDocs,
            forms,
            !"false".equals(req.getParameter("attachEFormToEmail")),
            "true".equals(req.getParameter("openEFormAfterSendingEmail")),
            !"false".equals(req.getParameter("enableEmailEncryption")),
            !"false".equals(req.getParameter("encryptEmailAttachments")),
            "true".equals(req.getParameter("autoSendEmail")),
            "true".equals(req.getParameter("deleteEFormAfterSendingEmail")),
            stripControlChars(req.getParameter("passwordEmail"), MAX_PASSWORD_LENGTH),
            stripControlChars(req.getParameter("passwordClueEmail"), MAX_PASSWORD_LENGTH),
            sanitizeEmail(req.getParameter("senderEmail")),
            stripControlChars(req.getParameter("subjectEmail"), MAX_SUBJECT_LENGTH),
            limitLength(req.getParameter("bodyEmail"), MAX_BODY_LENGTH),
            limitLength(req.getParameter("encryptedMessageEmail"), MAX_ENCRYPTED_MSG_LENGTH),
            req.getParameter("emailPatientChartOption")
        );
    }

    /**
     * Strips ASCII control characters (below 0x20, except space) and limits length.
     * Used for fields where control characters have no legitimate purpose.
     *
     * @param value the raw input, may be null
     * @param maxLength maximum allowed length
     * @return sanitized string, or null if input was null
     */
    private static String stripControlChars(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        // Remove ASCII control characters (0x00-0x1F) except for space (0x20)
        String cleaned = value.replaceAll("[\\x00-\\x1f]", "");
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    /**
     * Validates an email address has a basic valid structure and limits length.
     * Returns null for clearly invalid values.
     *
     * @param value the raw email input, may be null
     * @return the email if structurally valid, or null
     */
    private static String sanitizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_EMAIL_LENGTH) {
            return null;
        }
        // Basic structural check: must contain @ with text before and after
        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return null;
        }
        return value;
    }

    /**
     * Limits string length without other transformations.
     * Used for fields that may contain legitimate HTML or complex content.
     *
     * @param value the raw input, may be null
     * @param maxLength maximum allowed length
     * @return length-limited string, or null if input was null
     */
    private static String limitLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }
}
