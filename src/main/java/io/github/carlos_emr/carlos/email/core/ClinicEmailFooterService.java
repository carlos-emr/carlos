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
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Clinic text is independent of optional personal text and is included in every new email. */
@Service
public class ClinicEmailFooterService {
    private final UserPropertyDAO properties;
    private final EmailFooterLogoService logos;

    public ClinicEmailFooterService(UserPropertyDAO properties, EmailFooterLogoService logos) {
        this.properties = properties;
        this.logos = logos;
    }

    public String clinicFooter() {
        var rows = properties.findClinicEmailFooter();
        return rows.isEmpty() ? "" : EmailFooterHtml.clean(rows.get(0).getValue());
    }

    public ClinicEmailFooterSnapshot snapshot() {
        String html = clinicFooter();
        return new ClinicEmailFooterSnapshot(html, html.isEmpty() ? null : logos.inlineLogo());
    }

    /** Render the same trusted clinic snapshot carried by the server-side compose token. */
    public static void expose(jakarta.servlet.http.HttpServletRequest request, ClinicEmailFooterSnapshot snapshot) {
        request.setAttribute("clinicFooter", snapshot == null ? "" : snapshot.html());
        request.setAttribute("clinicFooterMissing", snapshot == null || snapshot.html().isEmpty());
        request.setAttribute("clinicFooterLogoPreview", null);
        if (snapshot != null && snapshot.logo() != null) {
            EmailInlineImage logo = snapshot.logo();
            request.setAttribute("clinicFooterLogoPreview", "data:" + logo.contentType() + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(logo.bytes()));
        }
    }

    public static String fingerprint(String html) {
        return DigestUtils.sha256Hex(EmailFooterHtml.clean(html));
    }

    public enum SaveResult { SAVED, UNCHANGED, STALE }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SaveResult save(String html, String shownFingerprint) {
        if (shownFingerprint == null || !shownFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Clinic footer fingerprint is missing");
        }
        if (html == null || html.length() > 4 * EmailFooterHtml.MAX_HTML_LENGTH) {
            throw new IllegalArgumentException("Clinic footer is too long");
        }
        String cleaned = EmailFooterHtml.clean(html);
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Clinic footer is required");
        }
        if (cleaned.length() > EmailFooterHtml.MAX_HTML_LENGTH
                || EmailFooterHtml.visibleLength(cleaned) > EmailData.FOOTER_MAX_LENGTH) {
            throw new IllegalArgumentException("Clinic footer is too long");
        }
        properties.lockClinicEmailFooterSettings();
        var rows = properties.findClinicEmailFooterForUpdate();
        String current = rows.isEmpty() ? "" : EmailFooterHtml.clean(rows.get(0).getValue());
        if (fingerprint(cleaned).equals(shownFingerprint) || cleaned.equals(current)) {
            return SaveResult.UNCHANGED;
        }
        if (!fingerprint(current).equals(shownFingerprint)) {
            return SaveResult.STALE;
        }
        if (rows.isEmpty()) {
            UserProperty row = new UserProperty();
            row.setName("email_footer_clinic_default");
            row.setValue(cleaned);
            properties.saveProp(row);
        } else {
            properties.updateClinicEmailFooter(rows.get(0), cleaned);
        }
        rows.stream().skip(1).forEach(row -> properties.deleteClinicEmailFooter(row.getId()));
        return SaveResult.SAVED;
    }
}
