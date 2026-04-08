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

import java.util.Set;
import java.util.regex.Pattern;

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

    /** Simple email format validation pattern. */
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    /** Valid values for the patient chart option (from {@code ChartDisplayOption} enum). */
    private static final Set<String> VALID_CHART_OPTIONS = Set.of("doNotAddAsNote", "addFullNote");

    private static final int MAX_SUBJECT_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 10000;
    private static final int MAX_PASSWORD_LENGTH = 100;

    /**
     * Creates an EmailAttachmentSettings instance from an HTTP request.
     * Validates and sanitizes raw user input parameters before storage.
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
            sanitizePassword(req.getParameter("passwordEmail")),
            sanitizePassword(req.getParameter("passwordClueEmail")),
            validateEmail(req.getParameter("senderEmail")),
            sanitizeSubject(req.getParameter("subjectEmail")),
            truncate(req.getParameter("bodyEmail"), MAX_BODY_LENGTH),
            truncate(req.getParameter("encryptedMessageEmail"), MAX_BODY_LENGTH),
            validateChartOption(req.getParameter("emailPatientChartOption"))
        );
    }

    /**
     * Validates an email address against a simple format pattern.
     * Returns null (fall back to default sender) if the address is invalid.
     *
     * @param email the raw email address from user input
     * @return the email address if valid, or null if invalid/null
     */
    static String validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return null;
        }
        return email;
    }

    /**
     * Sanitizes an email subject line by stripping all Unicode line break sequences
     * (CR, LF, CRLF, NEL, LS, PS — via {@code \R}) to prevent SMTP header injection,
     * and truncating to maximum length.
     *
     * @param subject the raw subject from user input
     * @return the sanitized subject, or null if input was null
     */
    static String sanitizeSubject(String subject) {
        if (subject == null) {
            return null;
        }
        subject = subject.replaceAll("\\R", "");
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            subject = subject.substring(0, MAX_SUBJECT_LENGTH);
        }
        return subject;
    }

    /**
     * Sanitizes a password or password clue by stripping control characters
     * and truncating to maximum length.
     *
     * @param password the raw password/clue from user input
     * @return the sanitized value, or null if input was null
     */
    static String sanitizePassword(String password) {
        if (password == null) {
            return null;
        }
        password = password.replaceAll("[\\p{Cntrl}]", "");
        if (password.length() > MAX_PASSWORD_LENGTH) {
            password = password.substring(0, MAX_PASSWORD_LENGTH);
        }
        return password;
    }

    /**
     * Truncates a string to the specified maximum length.
     *
     * @param value the raw value from user input
     * @param maxLength the maximum allowed length
     * @return the truncated value, or null if input was null
     */
    static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }

    /**
     * Validates the patient chart option against the known set of valid values.
     *
     * @param option the raw chart option from user input
     * @return the option if valid, or null if invalid/null
     */
    static String validateChartOption(String option) {
        if (option == null || !VALID_CHART_OPTIONS.contains(option)) {
            return null;
        }
        return option;
    }
}
