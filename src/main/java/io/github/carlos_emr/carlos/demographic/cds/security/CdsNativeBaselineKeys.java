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
package io.github.carlos_emr.carlos.demographic.cds.security;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Per-installation keys that seal the CDS native-baseline witness ({@code cds_import_unit} and
 * {@code cds_import_checkpoint.native_baseline}).
 *
 * <p>The witness holds, for each column of each receipted native row, an unsalted SHA-256 and
 * byte length. For low-entropy PHI (sex, birth date parts, health card and SIN numbers, postal
 * code, phone) such a digest can be reversed by enumeration, so it is stored only sealed under a
 * secret held outside the database. The key file follows the CDS export master-key pattern:
 * {@code <cds.nativeBaseline.keyRoot>/<keyId>.key} holds exactly 32 random bytes, owned by root
 * and readable by the service group (for example root:carlos 0640 below a root-owned 0750
 * directory). {@code cds.nativeBaseline.activeKey} names the key that seals new witnesses; a
 * sealed witness records its key ID, so a retired key must stay in the root while any witness
 * sealed with it is still needed. Destroying every key makes old witnesses unreadable (the
 * exporter then treats them as absent evidence and asks for review).
 *
 * <p>A missing or unreadable key is a clear refusal ({@code CDS_NATIVE_BASELINE_KEY_UNAVAILABLE}),
 * never a fallback to an unsealed witness. Key bytes and paths never enter a message or log.
 */
public final class CdsNativeBaselineKeys {
    public static final String KEY_ROOT = "cds.nativeBaseline.keyRoot";
    public static final String ACTIVE_KEY = "cds.nativeBaseline.activeKey";
    private static final String KEY_ID = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";

    @FunctionalInterface
    interface Loader {
        byte[] load(String keyId) throws IOException;
    }

    private final String active;
    private final Loader loader;
    private final Map<String, SecretKey> loaded = new ConcurrentHashMap<>();

    private CdsNativeBaselineKeys(String active, Loader loader) {
        this.active = active;
        this.loader = loader;
    }

    /** Keys named by trusted server configuration; nothing is read until a key is needed. */
    public static CdsNativeBaselineKeys fromProperties(Properties properties) {
        Objects.requireNonNull(properties);
        String root = trimmed(properties.getProperty(KEY_ROOT));
        String active = trimmed(properties.getProperty(ACTIVE_KEY));
        return new CdsNativeBaselineKeys(
                active,
                keyId -> {
                    if (root == null) throw unavailable();
                    Path directory = CdsRootOwnedKeyFile.absolute(root);
                    return CdsRootOwnedKeyFile.read(directory.resolve(keyId + ".key"), 32);
                });
    }

    /** Explicit in-memory keys, for qualification fixtures and tests; the values are copied. */
    public static CdsNativeBaselineKeys of(String active, Map<String, byte[]> keys) {
        var copy = new java.util.HashMap<String, byte[]>();
        keys.forEach((id, bytes) -> copy.put(id, bytes.clone()));
        return new CdsNativeBaselineKeys(
                active,
                keyId -> {
                    byte[] bytes = copy.get(keyId);
                    if (bytes == null) throw unavailable();
                    return bytes.clone();
                });
    }

    /** The key ID that seals new witnesses; refused when it is not configured. */
    public String activeKeyId() throws IOException {
        if (active == null || !active.matches(KEY_ID)) throw unavailable();
        return active;
    }

    /** Proves the active key can be read now, so an import refuses before any clinical write. */
    public void requireActiveKey() throws IOException {
        key(activeKeyId());
    }

    /** The AES-256 key recorded by a sealed witness; refused when absent or malformed. */
    public SecretKey key(String keyId) throws IOException {
        if (keyId == null || !keyId.matches(KEY_ID)) throw unavailable();
        SecretKey cached = loaded.get(keyId);
        if (cached != null) return cached;
        byte[] bytes;
        try {
            bytes = loader.load(keyId);
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
        try {
            if (bytes == null || bytes.length != 32) throw unavailable();
            SecretKey key = new SecretKeySpec(bytes, "AES");
            loaded.putIfAbsent(keyId, key);
            return loaded.get(keyId);
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String trimmed(String value) {
        if (value == null) return null;
        value = value.strip();
        return value.isEmpty() ? null : value;
    }

    private static IOException unavailable() {
        return new IOException("CDS_NATIVE_BASELINE_KEY_UNAVAILABLE");
    }

    @Override
    public String toString() {
        return "CdsNativeBaselineKeys[protected]";
    }
}
