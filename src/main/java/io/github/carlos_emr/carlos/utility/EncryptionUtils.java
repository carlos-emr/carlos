//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.OscarProperties;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.password.PasswordHashHelper;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Objects;


public final class EncryptionUtils {
    private static final QueueCacheValueCloner<byte[]> byteArrayCloner = new QueueCacheValueCloner<byte[]>() {
        public byte[] cloneBean(byte[] original) {
            return (byte[]) original.clone();
        }
    };
    private static Logger logger = MiscUtils.getLogger();
    private static final MessageDigest messageDigest = initMessageDigest();
    private static final QueueCache<String, byte[]> sha1Cache;
    private static final int MAX_SHA_KEY_CACHE_SIZE = 2048;
    public static final String SECRET_KEY_ENV_VAR = "encryption.util.secret.key";
    private static SecretKeySpec SECRET_KEY_SPEC;
    private static final String ENCRYPTION_PREFIX = "{ENC}";

    public EncryptionUtils() {
    }

    private static MessageDigest initMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException var1) {
            logger.error("Error", var1);
            return null;
        }
    }

    /**
     * @deprecated
     * weak: do not use for generating password hashes.
     * use the hash(String password) method below.
     */
    @Deprecated
    public static byte[] getSha1(String s) {
        byte[] b = (byte[]) sha1Cache.get(s);
        if (b == null) {
            b = getSha1NoCache(s);
            if (s.length() < 2048) {
                sha1Cache.put(s, b);
            }
        }

        return b;
    }

    /**
     * @deprecated
     * weak: do not use for generating password hashes.
     * use the hash(String password) method below.
     */
    @Deprecated
    private static byte[] getSha1NoCache(String s) {
        if (s == null) {
            return null;
        } else {
            try {
                synchronized (Objects.requireNonNull(messageDigest)) {
                    return messageDigest.digest(s.getBytes("UTF-8"));
                }
            } catch (Exception var4) {
                logger.error("Unexpected error.", var4);
                return null;
            }
        }
    }

    /**
     * uses Javax.crypto utils to generate a random AES key.
     * Considered a weak method.
     */
    public static SecretKey generateEncryptionKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        return keyGenerator.generateKey();
    }

    /**
     * uses Javax.crypto utils to generate a secret key spec
     * Considered a weak method.
     */
    public static SecretKeySpec generateEncryptionKey(String seed) {
        byte[] sha1 = getSha1(seed);
        return new SecretKeySpec(sha1, 0, 16, "AES");
    }

    /**
     * Use only to store encrypted data.
     * Do NOT use for authentication.
     * Considered a weak encryption.
     */
    public static byte[] encrypt(SecretKey secretKey, byte[] plainData) throws IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        if (secretKey == null) {
            return plainData;
        } else {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(1, secretKey);
            return cipher.doFinal(plainData);
        }
    }

    /**
     * Use only to store encrypted data.
     * Do NOT use for authentication.
     * Considered a weak encryption.
     */
    public static byte[] decrypt(SecretKey secretKey, byte[] encryptedData) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        if (secretKey == null) {
            return encryptedData;
        } else {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(2, secretKey);
            return cipher.doFinal(encryptedData);
        }
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
            throw new Exception("Secret key not found in environment variables.");
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
            throw new Exception("Secret key not found in environment variables.");
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
     * This method retrieves the secret key from the OscarProperties, decodes it from Base64,
     * and initializes the SECRET_KEY_SPEC with the decoded key.
     * If the secret key is not found in the properties, an error is logged.
     */
    public static void prepareSecretKeySpec() {
        String key = OscarProperties.getInstance().getProperty(SECRET_KEY_ENV_VAR);
        if (Objects.isNull(key)) {
            logger.error("Secret key not found in environment variables.");
            return;
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
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
        sha1Cache = new QueueCache(4, 2048, byteArrayCloner);
        prepareSecretKeySpec();
    }
}

