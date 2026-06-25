/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.password.PasswordHashHelper;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;


public final class EncryptionUtils {
    private static final int AES_128_KEY_BYTES = 16;
    private static final int AES_192_KEY_BYTES = 24;
    private static final int AES_256_KEY_BYTES = 32;
    private static Logger logger = MiscUtils.getLogger();
    public static final String SECRET_KEY_ENV_VAR = "encryption.util.secret.key";
    private static volatile SecretKeySpec SECRET_KEY_SPEC;
    private static final String ENCRYPTION_PREFIX = "{ENC}";

    public EncryptionUtils() {
    }

    /**
     * Encrypts the given byte array using AES/GCM/NoPadding.
     * <p>
     * This method encrypts the provided `input` byte array using AES encryption in GCM mode with no padding.
     * It generates a random 12-byte initialization vector (IV) and uses a 128-bit GCM parameter specification.
     * The encrypted bytes are then combined with the IV and returned as a single byte array.
     *
     * @param input The byte array to encrypt.
     * @return The encrypted byte array, which includes the IV prepended to the ciphertext.
     * @throws Exception If the secret key is not initialized, or if there is an error during encryption. Specifically:
     *                   - If the SECRET_KEY_SPEC is null, indicating the secret key has not been initialized.
     *                   - If the "AES/GCM/NoPadding" cipher is not available.
     *                   - If there is an issue with the encryption process itself, such as invalid key or data.
     **/
    public static byte[] encrypt(byte[] input) throws Exception {
        if (Objects.isNull(SECRET_KEY_SPEC)) {
            throw new IllegalStateException("Secret key not found in CarlosProperties.");
        }

        byte[] iv = new byte[12];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY_SPEC, gcmSpec);

        byte[] encryptedBytes = cipher.doFinal(input);

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length)
                .put(iv)
                .put(encryptedBytes);

        return byteBuffer.array();
    }

    /**
     * Decrypts the given byte array using AES/GCM/NoPadding.
     * <p>
     * This method decrypts the provided `cipherBytes` byte array, which is assumed to be
     * in the format produced by the `encrypt` method.  It extracts the 12-byte initialization
     * vector (IV) from the beginning of the array, and then decrypts the remaining bytes
     * using AES in GCM mode with no padding.
     *
     * @param cipherBytes The encrypted byte array, including the prepended IV.
     * @return The decrypted byte array.
     * @throws Exception If the secret key is not initialized, or if there is an error during decryption. Specifically:
     *                   - If the SECRET_KEY_SPEC is null, indicating the secret key has not been initialized.
     *                   - If the "AES/GCM/NoPadding" cipher is not available.
     *                   - If the input byte array is not in the expected format (e.g., IV is missing).
     *                   - If there is an issue with the decryption process itself, such as an authentication failure (indicating data corruption).
     **/
    public static byte[] decrypt(byte[] cipherBytes) throws Exception {
        if (Objects.isNull(SECRET_KEY_SPEC)) {
            throw new IllegalStateException("Secret key not found in CarlosProperties.");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(cipherBytes);
        byte[] iv = new byte[12];
        byteBuffer.get(iv);

        byte[] encryptedBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(encryptedBytes);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY_SPEC, gcmSpec);

        return cipher.doFinal(encryptedBytes);
    }

    /**
     * Encrypts a plain text string.
     *
     * @param plainText The plain text string to encrypt.
     * @return The encrypted string, prefixed with an encryption marker and encoded in Base64.
     * @throws Exception If the secret key is not initialized, or if there is an error during encryption.
     */
    public static String encrypt(String plainText) throws Exception {

        /*
         * null will fail and empty string will be encrypted as a valid password.
         * Exit this method in these cases.
         */
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        byte[] cipherBytes = EncryptionUtils.encrypt(plainText.getBytes(StandardCharsets.UTF_8));

        // Return the encrypted string with a prefix, encoded in Base64
        return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(cipherBytes);
    }

    /**
     * Decrypts an encrypted string.
     *
     * @param encryptedText The encrypted string to decrypt.
     * @return The decrypted string, or the original string if it was not encrypted.
     * @throws Exception If the secret key is not initialized, or if there is an error during decryption.
     */
    public static String decrypt(String encryptedText) throws Exception {
        /*
         * avoid nulls and empty strings.
         */
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        if (isEncrypted(encryptedText)) {
            String base64Encoded = encryptedText.substring(ENCRYPTION_PREFIX.length());

            byte[] cipherBytes = Base64.getDecoder().decode(base64Encoded);

            byte[] decryptedBytes = EncryptionUtils.decrypt(cipherBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        }

        // If the text is not encrypted, return it as-is
        return encryptedText;
    }

    /**
     * Checks if a provided string is encrypted.
     * <p>
     * This method determines if the input string starts with the encryption prefix,
     * indicating that it has been encrypted.
     *
     * NULL and empty strings are returned as encrypted = true
     *
     * @param input The string to check for encryption.
     * @return True if the string is encrypted (starts with the encryption prefix), False otherwise.
     */
    public static boolean isEncrypted(String input) {
        return input == null || input.isEmpty() || input.startsWith(ENCRYPTION_PREFIX);
    }

    /**
     * Generates a new secret key for encryption/decryption.
     * <p>
     * This method uses the AES algorithm to generate a 256-bit secret key.
     * The generated key is then Base64 encoded for storage and retrieval.
     *
     * @return The Base64 encoded string representation of the generated secret key.
     * @throws NoSuchAlgorithmException If the AES algorithm is not available.
     */
    public static String generateSecretKey() throws NoSuchAlgorithmException {
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256, new SecureRandom());
        SecretKey key = keygen.generateKey();

        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Prepares the SecretKeySpec object used for encryption and decryption.
     * <p>
     * This method retrieves the secret key from the CarlosProperties, decodes it from Base64,
     * and initializes the SECRET_KEY_SPEC with the decoded key.
     * <p>
     * The cached SECRET_KEY_SPEC is unconditionally reset to {@code null} before re-deriving it,
     * so a missing or invalid key always clears any previously cached spec rather than leaving a
     * stale value in place.
     * <p>
     * Failure modes:
     * <ul>
     *   <li>A missing or blank key is treated as "not configured": a warning is logged and the
     *       method returns with SECRET_KEY_SPEC left {@code null} (non-fatal, no exception). This
     *       is the expected early-class-load state; Startup re-prepares the key once properties
     *       are available.</li>
     *   <li>An invalid key (not valid Base64, or not a 16/24/32-byte AES key) is fatal and throws.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the configured key is not valid Base64 or does not
     *                                  decode to a 16/24/32-byte (AES-128/192/256) key
     */
    public static void prepareSecretKeySpec() {
        String key = CarlosProperties.getInstance().getProperty(SECRET_KEY_ENV_VAR);
        if (key != null) {
            // Base64.getDecoder() rejects any whitespace, and CarlosProperties does not trim
            // values. Normalize incidental surrounding whitespace (e.g. a manual properties edit)
            // so a usable key is not rejected at startup. Base64 never legitimately contains
            // surrounding whitespace, so trimming cannot mask or corrupt a real key.
            key = key.trim();
        }
        SECRET_KEY_SPEC = null;
        if (Objects.isNull(key) || key.isBlank()) {
            // Expected during early class-load before properties are read (the static initializer
            // documents this); Startup re-prepares once a key exists. Warn, not error, so this
            // normal boot path does not raise false-positive monitoring alerts.
            logger.warn("Secret key not found in CarlosProperties.");
            return;
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            // Use a sanitized message (the JDK detail message can name the offending character) but
            // keep the original exception as the cause so operators retain the stack trace for
            // diagnosis. The cause surfaces only in boot-time server logs, never to the browser.
            throw new IllegalArgumentException("encryption key is not valid Base64", e);
        }
        if (keyBytes.length != AES_128_KEY_BYTES
                && keyBytes.length != AES_192_KEY_BYTES
                && keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException("Invalid AES key length: " + keyBytes.length + " bytes");
        }
        SECRET_KEY_SPEC = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Generates a secure hash of the given password using the PasswordHashHelper.
     *
     * @see PasswordHashHelper#encodePassword(CharSequence) 
     * @param password string
     * @return hashed password
     * @throws IllegalArgumentException If the password is null or empty.
     */
    public static String hash(CharSequence password) throws IllegalArgumentException {
        return PasswordHashHelper.encodePassword(password);
    }

    /**
     * Validate a given password phrase against the stored hash password.
     * This is a boolean validation. No password values are returned
     * @param password  plain password string
     * @see PasswordHashHelper#matches(CharSequence, String)
     * @param hashedPassword hashed password string usually stored in the database.
     * @return True if the raw password matches the encoded password, false otherwise.
     * @throws IllegalArgumentException failure while matching
     */
    public static boolean verify(CharSequence password, String hashedPassword) throws IllegalArgumentException {
        return PasswordHashHelper.matches(password, hashedPassword);
    }

    /**
     * Check if a given hashed password needs to be upgraded to a more secure
     * algorithm.
     * @param hashedPassword hashed password string usually stored in the database.
     * @see PasswordHashHelper#upgradeEncoding(String)
     * @return true if upgrade is needed.
     */
    public static boolean isPasswordHashUpgradeNeeded(String hashedPassword) {
        return PasswordHashHelper.upgradeEncoding(hashedPassword);
    }

    static {
        /*
         * EncryptionUtils is frequently class-loaded before application properties are read, so
         * the key may be absent (or, after a bad edit, invalid) at this point. Never let key
         * preparation abort class loading - an exception here would surface as
         * ExceptionInInitializerError and leave the class permanently unusable. Startup is
         * authoritative: it re-prepares and validates the key once properties are available, and
         * fails fast there if the key is invalid. Leaving the spec null mirrors the missing-key state.
         * This deferral assumes the servlet Startup listener runs; non-servlet entry points (CLI,
         * batch jobs, tests) do not re-validate the key and will see a null spec until they prepare it.
         */
        try {
            prepareSecretKeySpec();
        } catch (RuntimeException e) {
            SECRET_KEY_SPEC = null;
            // A missing/blank key does NOT reach here - prepareSecretKeySpec returns after logging.
            // This only fires when an actually-invalid key is present at class-load (bad Base64 /
            // wrong AES length). Warn rather than error: Startup re-prepares and fails fast there if
            // the key is still invalid, so this class-load attempt is not the authoritative check.
            logger.warn("Deferred encryption key initialization; it will be prepared at startup.", e);
        }
    }
}
