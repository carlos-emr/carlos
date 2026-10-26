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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * An explicit complete public-key inventory for portal #44. Previously published keys remain
 * listed as revoked; choosing, storing or rotating that inventory belongs to its caller.
 */
public record PatientPortalClinicKeysPublishRequest(List<Key> keys) {
    static final int MAX_KEYS = 16;
    private static final String INVALID = "clinic public key inventory is invalid";
    // Canonical DER SubjectPublicKeyInfo for named P-256, with one uncompressed public point.
    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex(
            "3059301306072a8648ce3d020106082a8648ce3d03010703420004");

    public PatientPortalClinicKeysPublishRequest {
        if (keys == null || keys.isEmpty() || keys.size() > MAX_KEYS) {
            throw invalid();
        }
        Set<String> kids = new HashSet<>();
        for (Key key : keys) {
            if (key == null || !kids.add(key.kid())) {
                throw invalid();
            }
        }
        keys = List.copyOf(keys);
    }

    /** Strings keep the validated public material immutable across signing and transport. */
    public record Key(String kid, String spki, String status, String replacesKid, String replacementSignature) {
        public Key {
            byte[] encoded = decode(spki, 512);
            if (encoded.length != 91
                    || !Arrays.equals(SPKI_PREFIX, Arrays.copyOf(encoded, SPKI_PREFIX.length))
                    || !("active".equals(status) || "revoked".equals(status))) {
                throw invalid();
            }
            try {
                var parsed = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
                if (!(parsed instanceof ECPublicKey key)
                        || !Arrays.equals(encoded, key.getEncoded()) || !onCurve(key)
                        || !Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(MessageDigest.getInstance("SHA-256").digest(encoded)).equals(kid)) {
                    throw invalid();
                }
            } catch (GeneralSecurityException exception) {
                // Input/provider exception messages may contain material from the request.
                throw invalid();
            }
            if ((replacesKid == null) != (replacementSignature == null)) {
                throw invalid();
            }
            if (replacesKid != null) {
                if (decode(replacesKid, 32).length != 32 || replacesKid.equals(kid)) {
                    throw invalid();
                }
                decode(replacementSignature, 512);
            }
        }

        @Override
        public String toString() {
            return "ClinicKey[details=REDACTED]";
        }
    }

    private static boolean onCurve(ECPublicKey key) {
        if (!(key.getParams().getCurve().getField() instanceof ECFieldFp field)) {
            return false;
        }
        BigInteger prime = field.getP();
        BigInteger x = key.getW().getAffineX();
        BigInteger y = key.getW().getAffineY();
        if (x == null || y == null || x.signum() < 0 || y.signum() < 0
                || x.compareTo(prime) >= 0 || y.compareTo(prime) >= 0) {
            return false;
        }
        // Validate the supplied point too; parsing an SPKI alone is not proof it is on the curve.
        var curve = key.getParams().getCurve();
        return y.multiply(y).mod(prime).equals(
                x.multiply(x).multiply(x).add(curve.getA().multiply(x)).add(curve.getB()).mod(prime));
    }

    private static byte[] decode(String encoded, int limit) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > (limit * 4 + 2) / 3) {
            throw invalid();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > limit
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded)) {
                throw invalid();
            }
            return bytes;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    ObjectNode toJson(ObjectMapper mapper) {
        ObjectNode body = mapper.createObjectNode();
        var entries = body.putArray("keys");
        for (Key key : keys) {
            ObjectNode entry = entries.addObject();
            entry.put("kid", key.kid());
            entry.put("spki", key.spki());
            entry.put("status", key.status());
            entry.put("replaces_kid", key.replacesKid());
            entry.put("replacement_signature", key.replacementSignature());
        }
        return body;
    }

    private static PortalRequestPreparationException invalid() {
        return new PortalRequestPreparationException(INVALID);
    }

    @Override
    public String toString() {
        return "ClinicKeyInventory[details=REDACTED]";
    }
}
