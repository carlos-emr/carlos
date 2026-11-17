// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Fingerprint of the fields replaced by the professional-contact edit form. */
public final class SpecialistContactEditVersion {
    private SpecialistContactEditVersion() { }

    public static String of(ProfessionalSpecialist specialist) {
        Object[] values = {specialist.getId(), specialist.getStreetAddress(), specialist.getFirstName(),
                specialist.getLastName(), specialist.getEmailAddress(), specialist.getPhoneNumber(),
                specialist.getFaxNumber(), specialist.getReferralNo(), specialist.getSpecialtyType(),
                specialist.getPrivatePhoneNumber(), specialist.getCellPhoneNumber(), specialist.isDeleted()};
        StringBuilder snapshot = new StringBuilder();
        for (Object value : values) {
            String text = Objects.toString(value, "");
            snapshot.append(text.length()).append(':').append(text);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
