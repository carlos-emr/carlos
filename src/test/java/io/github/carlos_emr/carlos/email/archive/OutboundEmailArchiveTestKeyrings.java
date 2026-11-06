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

package io.github.carlos_emr.carlos.email.archive;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.TreeMap;

/**
 * Obviously synthetic archive keys for tests (#3448). Each key is the 32 ASCII bytes
 * {@code synthetic-archive-test-key-NNNNN}; none has ever encrypted anything real.
 */
public final class OutboundEmailArchiveTestKeyrings {

    private OutboundEmailArchiveTestKeyrings() {
    }

    /**
     * @param keyId a key id
     * @return the synthetic 32-byte key for that id
     */
    public static byte[] syntheticKey(int keyId) {
        byte[] key = String.format("synthetic-archive-test-key-%05d", keyId).getBytes(StandardCharsets.US_ASCII);
        if (key.length != OutboundEmailArchiveKeyring.KEY_BYTES) {
            throw new IllegalArgumentException("synthetic key ids must have at most five digits");
        }
        return key;
    }

    /** @return the Base64 form of {@link #syntheticKey(int)}, as it appears in a keyring file */
    public static String syntheticKeyBase64(int keyId) {
        return Base64.getEncoder().encodeToString(syntheticKey(keyId));
    }

    /**
     * Raw key material for assertions in other packages; production code outside this package has no
     * way to read it.
     *
     * @param keyring a keyring
     * @param keyId   a key id in it
     * @return a copy of that key's 32 bytes
     */
    public static byte[] material(OutboundEmailArchiveKeyring keyring, int keyId) {
        return keyring.encodedKey(keyId);
    }

    /**
     * @param currentKeyId the key that encrypts new artifacts
     * @param keyIds       every key in the keyring; must include {@code currentKeyId}
     * @return a keyring of synthetic keys
     */
    public static OutboundEmailArchiveKeyring keyring(int currentKeyId, int... keyIds) {
        TreeMap<Integer, byte[]> keys = new TreeMap<>();
        for (int keyId : keyIds) {
            keys.put(keyId, syntheticKey(keyId));
        }
        return new OutboundEmailArchiveKeyring(currentKeyId, keys);
    }
}
