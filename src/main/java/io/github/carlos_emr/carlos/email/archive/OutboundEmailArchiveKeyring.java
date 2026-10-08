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

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The outbound email archive key domain (#3448): numbered AES-256 keys plus the id of the one that
 * encrypts new artifacts.
 *
 * <p>Every key ever made current is kept. An archived artifact records the id of the key that
 * sealed it in its envelope header, so an artifact written under key 1 is still opened with key 1
 * after key 2 becomes current. Removing a key orphans every artifact sealed with it, which is why
 * this class can add keys and change the current one but has no way to drop one.</p>
 *
 * <p>Immutable. {@link #toString()} lists ids only; no method exposes key material except
 * {@link #encodedKey(int)}, which exists solely so the keyring file can be written.</p>
 *
 * @since 2026-09-30
 */
public final class OutboundEmailArchiveKeyring {

    /** AES-256. */
    public static final int KEY_BYTES = 32;

    /** Key ids are stored as a signed 32-bit field in the envelope header; only positive ids are valid. */
    public static final int MAX_KEY_ID = Integer.MAX_VALUE;

    private static final String KEY_ALGORITHM = "AES";

    private final int currentKeyId;
    private final SortedMap<Integer, SecretKey> keys;

    /**
     * @param currentKeyId id of the key that encrypts new artifacts; must be one of {@code keyMaterial}
     * @param keyMaterial  raw 32-byte keys by positive id; copied, so the caller may clear its arrays
     * @throws IllegalArgumentException when the keyring is empty, an id is not positive, a key is not
     *         32 bytes, or the current id names no key. Messages carry ids only.
     */
    public OutboundEmailArchiveKeyring(int currentKeyId, Map<Integer, byte[]> keyMaterial) {
        if (keyMaterial == null || keyMaterial.isEmpty()) {
            throw new IllegalArgumentException("The archive keyring holds no keys");
        }
        TreeMap<Integer, SecretKey> copy = new TreeMap<>();
        for (Map.Entry<Integer, byte[]> entry : keyMaterial.entrySet()) {
            Integer keyId = entry.getKey();
            if (keyId == null || keyId <= 0) {
                throw new IllegalArgumentException("Archive key ids must be positive whole numbers");
            }
            byte[] material = entry.getValue();
            if (material == null || material.length != KEY_BYTES) {
                throw new IllegalArgumentException("Archive key " + keyId + " is not a " + KEY_BYTES + "-byte AES key");
            }
            copy.put(keyId, new SecretKeySpec(material, KEY_ALGORITHM));
        }
        if (!copy.containsKey(currentKeyId)) {
            throw new IllegalArgumentException("The current archive key " + currentKeyId + " is not in the keyring");
        }
        this.currentKeyId = currentKeyId;
        this.keys = Collections.unmodifiableSortedMap(copy);
    }

    /**
     * Creates a new keyring holding one freshly generated key, which is current.
     *
     * @param keyId  id of the new key: 1 on a fresh install; after an acknowledged keyring loss, an
     *               id that cannot be a lost key's (above every id found, or a random high id when
     *               the ids in use could not all be read)
     * @param random source of key material; a {@link SecureRandom}
     * @return a keyring holding one new key
     */
    public static OutboundEmailArchiveKeyring generate(int keyId, SecureRandom random) {
        byte[] material = newKeyMaterial(random);
        try {
            return new OutboundEmailArchiveKeyring(keyId, Map.of(keyId, material));
        } finally {
            Arrays.fill(material, (byte) 0);
        }
    }

    /**
     * Makes {@code keyId} current, generating it first when the keyring does not already hold it.
     * Every existing key is kept, so artifacts sealed with them stay readable.
     *
     * @param keyId  the key to make current
     * @param random source for a new key, used only when {@code keyId} is not yet in the keyring
     * @return a new keyring; this one is unchanged
     */
    public OutboundEmailArchiveKeyring withCurrentKey(int keyId, SecureRandom random) {
        TreeMap<Integer, byte[]> material = new TreeMap<>();
        for (Integer id : keys.keySet()) {
            material.put(id, encodedKey(id));
        }
        material.computeIfAbsent(keyId, id -> newKeyMaterial(random));
        try {
            return new OutboundEmailArchiveKeyring(keyId, material);
        } finally {
            // The new keyring holds its own copies.
            material.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        }
    }

    /** @return id of the key that encrypts new artifacts */
    public int currentKeyId() {
        return currentKeyId;
    }

    /** @return every key id, ascending */
    public SortedSet<Integer> keyIds() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(keys.keySet()));
    }

    /** @return true when the current key is also the newest (highest-numbered) key */
    public boolean isCurrentKeyNewest() {
        return keys.lastKey() == currentKeyId;
    }

    /**
     * Raw key material, for writing the keyring file and nothing else. Package-private, so code outside
     * this package cannot take single keys from the keyring bean; the file format itself
     * ({@link OutboundEmailArchiveKeyringParser#format}) is the one other way out, for writing the file.
     *
     * @param keyId a key id in this keyring
     * @return a copy of the 32 key bytes
     * @throws IllegalArgumentException when the id is not in the keyring
     */
    byte[] encodedKey(int keyId) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("Archive key " + keyId + " is not in the keyring");
        }
        return key.getEncoded();
    }

    SecretKey currentKey() {
        return keys.get(currentKeyId);
    }

    Optional<SecretKey> key(int keyId) {
        return Optional.ofNullable(keys.get(keyId));
    }

    /** Ids only: key material must never reach a log line or an exception message. */
    @Override
    public String toString() {
        return "OutboundEmailArchiveKeyring[currentKeyId=" + currentKeyId + ", keyIds=" + keys.keySet() + "]";
    }

    private static byte[] newKeyMaterial(SecureRandom random) {
        byte[] material = new byte[KEY_BYTES];
        random.nextBytes(material);
        return material;
    }
}
