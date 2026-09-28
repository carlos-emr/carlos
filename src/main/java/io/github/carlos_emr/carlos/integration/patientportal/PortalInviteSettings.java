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
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.CarlosProperties;
import java.util.function.Function;

/**
 * Deployment settings used only by the invitation workflow.
 *
 * <p>Kept apart from {@link PatientPortalSettings} because they describe what a patient sees, not how
 * CARLOS reaches the portal. The public URL is the address in the invitation email; it is not the
 * pinned internal API origin, and in a real deployment the two usually differ. Both values are
 * optional here, so an unconfigured invitation workflow never stops the rest of the portal
 * integration; the workflow refuses to send until they are set.
 *
 * @param publicBaseUrl the patient-facing portal URL: an origin, optionally followed by the path the portal's
 *     patient pages are served under; {@code null} when unset
 * @param senderEmail the sender address of an active CARLOS email account, or {@code null} when unset
 * @since 2026-09-22
 */
public record PortalInviteSettings(String publicBaseUrl, String senderEmail) {

    public static final String PUBLIC_BASE_URL_KEY = "patient_portal.public_base_url";
    public static final String SENDER_EMAIL_KEY = "patient_portal.invite.sender_email";

    /** The page a patient opens to activate an account; it takes no parameters. */
    static final String ACTIVATION_PATH = "/auth/activate";

    public PortalInviteSettings {
        publicBaseUrl = blankToNull(publicBaseUrl);
        if (publicBaseUrl != null) {
            publicBaseUrl = PatientPortalSettings.validatedHttpsUrl(publicBaseUrl, PUBLIC_BASE_URL_KEY);
        }
        senderEmail = blankToNull(senderEmail);
    }

    /** Reads the settings from {@code carlos.properties}. */
    public static PortalInviteSettings fromCarlosProperties() {
        return fromProperties(PortalInviteSettings::rawProperty);
    }

    /**
     * Reads a value as written, as {@code PatientPortalSettings.rawProperty} does. {@code getProperty}
     * discards a value beginning with the deprecated {@code oscar.} namespace, as a sender address such as
     * {@code oscar.clinic@example.org} does, so invitations would be refused as unconfigured; it also warns
     * for every unset key, and both settings are optional here.
     */
    private static String rawProperty(String key) {
        return (String) CarlosProperties.getInstance().get(key);
    }

    static PortalInviteSettings fromProperties(Function<String, String> lookup) {
        return new PortalInviteSettings(lookup.apply(PUBLIC_BASE_URL_KEY), lookup.apply(SENDER_EMAIL_KEY));
    }

    /** @return the activation page URL, or {@code null} when the public URL is unset */
    public String activationUrl() {
        return publicBaseUrl == null ? null : publicBaseUrl + ACTIVATION_PATH;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
