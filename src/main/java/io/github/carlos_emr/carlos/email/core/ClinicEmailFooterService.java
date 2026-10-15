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

/**
 * The clinic email footer: text set by an administrator, independent of each user's own footer.
 *
 * <p>An administrator chooses whether it is {@link Mode#REQUIRED required} (the default: every new
 * email carries it, and none can be sent until it is set) or {@link Mode#OFF off} (emails sent from
 * CARLOS carry no clinic footer and no logo). The choice covers CARLOS's own emails only: the
 * patient portal's emails still need the saved footer ({@link #portalSnapshot()}).</p>
 */
@Service
public class ClinicEmailFooterService {

    /** Whether CARLOS's emails must carry the clinic footer. */
    public enum Mode {
        /** Every new email carries the clinic footer; none can be sent until it is set. */
        REQUIRED("required"),
        /** Emails sent from CARLOS carry no clinic footer and no logo. */
        OFF("off");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        /** @return the stored and posted form, {@code required} or {@code off} */
        public String value() {
            return value;
        }

        /** @return the mode named by {@code value}, or {@code null} when it names none */
        public static Mode parse(String value) {
            for (Mode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return null;
        }
    }

    static final String MODE_PROPERTY = "email_footer_clinic_mode";
    static final String LOGO_SHOWN_PROPERTY = "email_footer_logo_shown";

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

    /**
     * Whether email must wait for an administrator to set the clinic footer. Senders that prepare
     * anything outside CARLOS first (a portal invitation code) ask this before they start, so staff
     * hear why instead of a generic refusal from the send itself.
     *
     * @return {@code true} when the clinic footer is required and none is set
     */
    public boolean requiredFooterMissing() {
        return mode() == Mode.REQUIRED && clinicFooter().isEmpty();
    }

    /**
     * @return whether CARLOS's emails must carry the clinic footer; {@link Mode#REQUIRED} until an
     *     administrator chooses otherwise, and for any stored value it does not recognise
     */
    public Mode mode() {
        UserProperty row = properties.findClinicProperty(MODE_PROPERTY);
        Mode mode = row == null ? null : Mode.parse(row.getValue());
        return mode == null ? Mode.REQUIRED : mode;
    }

    /**
     * Sets whether CARLOS's emails must carry the clinic footer. Serialized with clinic footer saves.
     *
     * @param mode the new mode
     * @return {@code true} when the mode changed; the caller audits it
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean saveMode(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Clinic footer mode is missing");
        }
        properties.lockClinicEmailFooterSettings();
        UserProperty row = properties.findClinicProperty(MODE_PROPERTY);
        Mode current = row == null ? null : Mode.parse(row.getValue());
        // No row, or one it does not recognise, reads as required: choosing required changes nothing.
        if ((current == null ? Mode.REQUIRED : current) == mode) {
            return false;
        }
        if (row == null) {
            row = new UserProperty();
            row.setName(MODE_PROPERTY);
        }
        row.setValue(mode.value());
        properties.saveProp(row);
        return true;
    }

    /**
     * @return whether emails carry a logo with the clinic footer: {@code true} until an administrator
     *     turns it off; the logo is the clinic's own, or the CARLOS logo until it uploads one
     */
    public boolean logoShown() {
        UserProperty row = properties.findClinicProperty(LOGO_SHOWN_PROPERTY);
        return row == null || !"false".equals(row.getValue());
    }

    /**
     * Sets whether emails carry a logo. Serialized with clinic footer saves.
     *
     * @param shown whether they do
     * @return {@code true} when the choice changed; the caller audits it
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean saveLogoShown(boolean shown) {
        properties.lockClinicEmailFooterSettings();
        UserProperty row = properties.findClinicProperty(LOGO_SHOWN_PROPERTY);
        boolean current = row == null || !"false".equals(row.getValue());
        if (current == shown) {
            return false;
        }
        if (row == null) {
            row = new UserProperty();
            row.setName(LOGO_SHOWN_PROPERTY);
        }
        row.setValue(String.valueOf(shown));
        properties.saveProp(row);
        return true;
    }

    /** @return the logo an email carries with the clinic footer, or {@code null} when it carries none */
    public EmailInlineImage emailLogo() {
        return logoShown() ? logos.inlineLogo() : null;
    }

    /** @return what a new email from CARLOS carries from the clinic, as the mode says */
    public ClinicEmailFooterSnapshot snapshot() {
        if (mode() == Mode.OFF) {
            return ClinicEmailFooterSnapshot.OFF;
        }
        return portalSnapshot();
    }

    /**
     * The saved clinic footer and its logo whatever the mode: the patient portal's emails still need
     * them when CARLOS's own emails have the footer turned off.
     *
     * @return the saved footer and logo; the footer is empty when none is saved
     */
    public ClinicEmailFooterSnapshot portalSnapshot() {
        String html = clinicFooter();
        return new ClinicEmailFooterSnapshot(html, html.isEmpty() ? null : emailLogo());
    }

    /** Render the same trusted clinic snapshot carried by the server-side compose token. */
    public static void expose(jakarta.servlet.http.HttpServletRequest request, ClinicEmailFooterSnapshot snapshot) {
        request.setAttribute("clinicFooter", snapshot == null ? "" : snapshot.html());
        request.setAttribute("clinicFooterMissing", snapshot == null || snapshot.missingRequiredFooter());
        request.setAttribute("clinicFooterOff", snapshot != null && snapshot.clinicFooterOff());
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

    /**
     * What a save from Configure Email changed.
     *
     * @param footer what happened to the footer text
     * @param modeChanged whether the required/off choice changed
     * @param logoChanged whether "Add a logo to emails" changed
     */
    public record Saved(SaveResult footer, boolean modeChanged, boolean logoChanged) {
    }

    /**
     * Saves the footer text, the required/off choice and whether emails carry a logo together, in one
     * transaction: Configure Email's Save footer sends all three. A refused or stale text save changes
     * nothing, the settings included. With {@link Mode#OFF}, blank text is not a refusal: it leaves the
     * saved footer, which the patient portal's emails still use.
     *
     * @param html the footer as the page posted it
     * @param shownFingerprint the fingerprint of the footer the page showed
     * @param mode the choice posted with it, or {@code null} to leave it as it is
     * @param logoShown whether emails carry a logo, or {@code null} to leave it as it is
     * @return what changed; the caller audits each change
     * @throws IllegalArgumentException when the text is refused (blank while required, or too long)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Saved save(String html, String shownFingerprint, Mode mode, Boolean logoShown) {
        SaveResult footer = mode == Mode.OFF && html != null && EmailFooterHtml.clean(html).isEmpty()
                ? SaveResult.UNCHANGED : save(html, shownFingerprint);
        boolean saving = footer != SaveResult.STALE;
        return new Saved(footer, saving && mode != null && saveMode(mode),
                saving && logoShown != null && saveLogoShown(logoShown));
    }

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
