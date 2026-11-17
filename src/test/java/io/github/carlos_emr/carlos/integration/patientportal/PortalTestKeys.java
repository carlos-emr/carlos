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

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Synthetic RFC 8032 key material shared by portal contract tests. Public so the booking package's
 * tests can build a job signer too.
 */
public final class PortalTestKeys {

    /** RFC 8032 section 7.1 TEST 1: the staff key here, and the key the shared job vectors use. */
    public static final String PRIVATE_KEY =
            "MC4CAQAwBQYDK2VwBCIEIJ1hsZ3v_VpguoRK9JLsLMREScVpezJpGXA7rAMcrn9g";
    public static final String PUBLIC_KEY =
            "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo";

    /** RFC 8032 section 7.1 TEST 2: a job key that is not the staff key. */
    public static final String JOB_PRIVATE_KEY =
            "MC4CAQAwBQYDK2VwBCIEIEzNCJso_5banbbDRuwRTg9bijGfNaumJNqM9u1PuKb7";
    public static final String JOB_PUBLIC_KEY =
            "PUAXw-hDiVqStwqnTRt-vJyYLM8uxJaMwM1V8Sr0Zgw";

    /** Format-valid placeholder for stub transports and HTTP loopback tests; never a real TLS key. */
    public static final String UNUSED_TLS_PIN = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private PortalTestKeys() {}

    /** The X.509 form of a raw 32-byte Ed25519 public key in unpadded base64url. */
    public static PublicKey publicKey(String raw) throws GeneralSecurityException {
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        byte[] key = Base64.getUrlDecoder().decode(raw);
        byte[] encoded = new byte[prefix.length + key.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(key, 0, encoded, prefix.length, key.length);
        return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(encoded));
    }

    /** Whether {@code assertion}'s signature verifies under {@code rawPublicKey}. */
    public static boolean verifies(String assertion, String rawPublicKey) throws GeneralSecurityException {
        String[] parts = assertion.split("\\.", -1);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey(rawPublicKey));
        verifier.update(Base64.getUrlDecoder().decode(parts[0]));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[1]));
    }
}
