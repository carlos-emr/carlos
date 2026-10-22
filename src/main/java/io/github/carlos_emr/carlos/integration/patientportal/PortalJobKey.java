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

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * One job's signing key, read from the CARLOS properties beside the staff key and protected the
 * same way: the private key is a {@link PortalSecret}, never rendered or logged.
 *
 * <p>Each job has its own key pair. Nothing here reads the staff private key, and nothing falls
 * back to it: a job with no key of its own is off. The staff key id is read only to refuse a job
 * key id equal to it, because the portal selects the verification key by id and a shared id would
 * let one key's holder pass for the other.
 *
 * <p>The {@code .next} variants of both settings, which carlos-ctl stages during a rotation, are
 * never read; CARLOS signs with the active pair only.
 *
 * @param job the job the key signs for
 * @param privateKey unpadded base64url PKCS#8 Ed25519 private key
 * @param keyId the {@code kid} the portal's job keyring lists the public half under
 * @since 2026-10-20
 */
public record PortalJobKey(PortalJob job, PortalSecret privateKey, String keyId) {

    private static final int MAX_KEY_ID_LENGTH = 64;
    private static final String MISSING = "%s is required when %s is set";
    private static final String BAD_KEY_ID =
            "%s must contain 1 to 64 ASCII letters, digits, dots, underscores, or hyphens";
    private static final String SHARED_KEY_ID = "%s must differ from %s";

    /** Validates on every construction path, as {@link PatientPortalSettings} does. */
    public PortalJobKey {
        if (job == null) {
            throw new PatientPortalConfigurationException("portal job is required");
        }
        if (privateKey == null) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING, job.privateKeyProperty(), job.keyIdProperty()));
        }
        PortalStaffAssertionSigner.parsePrivateKey(privateKey, job.privateKeyProperty());
        if (keyId == null || keyId.isBlank()) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, MISSING, job.keyIdProperty(), job.privateKeyProperty()));
        }
        keyId = keyId.strip();
        if (!isKeyId(keyId)) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, BAD_KEY_ID, job.keyIdProperty()));
        }
    }

    /**
     * Reads the job's key from the CARLOS properties.
     *
     * @return the key, or empty when neither of the job's settings is present
     * @throws PatientPortalConfigurationException naming the setting at fault when only one is
     *     present or either is invalid; the message never carries a configured value
     */
    public static Optional<PortalJobKey> fromCarlosProperties(PortalJob job) {
        return fromProperties(job, PatientPortalSettings::rawProperty);
    }

    /**
     * Reads the job's key through an arbitrary lookup, as {@link #fromCarlosProperties} does.
     *
     * @see #fromCarlosProperties(PortalJob)
     */
    public static Optional<PortalJobKey> fromProperties(PortalJob job, Function<String, String> lookup) {
        String encoded = trimmed(lookup.apply(job.privateKeyProperty()));
        String keyId = trimmed(lookup.apply(job.keyIdProperty()));
        if (encoded == null && keyId == null) {
            return Optional.empty();
        }
        PortalJobKey key = new PortalJobKey(job, encoded == null ? null : PortalSecret.of(encoded), keyId);
        String staffKeyId = trimmed(lookup.apply(PatientPortalSettings.STAFF_ASSERTION_KEY_ID));
        if (key.keyId().equals(staffKeyId)) {
            throw new PatientPortalConfigurationException(String.format(Locale.ROOT, SHARED_KEY_ID,
                    job.keyIdProperty(), PatientPortalSettings.STAFF_ASSERTION_KEY_ID));
        }
        for (PortalJob other : PortalJob.values()) {
            if (other != job && key.keyId().equals(trimmed(lookup.apply(other.keyIdProperty())))) {
                throw new PatientPortalConfigurationException(String.format(Locale.ROOT, SHARED_KEY_ID,
                        job.keyIdProperty(), other.keyIdProperty()));
            }
        }
        return Optional.of(key);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean isKeyId(String value) {
        if (value.length() > MAX_KEY_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < 'A' || character > 'Z') && (character < 'a' || character > 'z')
                    && (character < '0' || character > '9')
                    && character != '.' && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    /** A record's generated rendering would print the key; this one names only the job and its id. */
    @Override
    public String toString() {
        return "PortalJobKey[job=" + job.jobName() + ", keyId=" + keyId + ", privateKey=" + privateKey + "]";
    }
}
