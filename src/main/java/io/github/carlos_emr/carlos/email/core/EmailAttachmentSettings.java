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
import org.owasp.encoder.Encode;

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
    /**
     * Creates an EmailAttachmentSettings instance from an HTTP request.
     *
     * <p>All string values sourced from HTTP request parameters are encoded with
     * {@link Encode#forHtml} to break the taint chain before session storage,
     * preventing trust-boundary violations (CodeQL java/TrustBoundaryViolation).
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
            Encode.forHtml(req.getParameter("passwordEmail")),
            Encode.forHtml(req.getParameter("passwordClueEmail")),
            Encode.forHtml(req.getParameter("senderEmail")),
            Encode.forHtml(req.getParameter("subjectEmail")),
            Encode.forHtml(req.getParameter("bodyEmail")),
            Encode.forHtml(req.getParameter("encryptedMessageEmail")),
            Encode.forHtml(req.getParameter("emailPatientChartOption"))
        );
    }
}
