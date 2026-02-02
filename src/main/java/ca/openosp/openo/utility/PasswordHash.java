package ca.openosp.openo.utility;/*
 * Password Hashing With PBKDF2 (http://crackstation.net/hashing-security.htm).
 * Copyright (c) 2013, Taylor Hornby
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2 (Password-Based Key Derivation Function 2) salted password hashing utility
 * for secure password storage in healthcare provider authentication systems.
 *
 * <p>This class provides cryptographically secure password hashing and verification
 * using PBKDF2 with HMAC-SHA1 algorithm. It implements industry best practices for
 * password storage by using:</p>
 * <ul>
 * <li>Cryptographic salting with SecureRandom to prevent rainbow table attacks</li>
 * <li>Configurable iteration count (default 64,000) to slow down brute force attacks</li>
 * <li>Constant-time comparison to prevent timing attacks</li>
 * <li>Base64 encoding for safe storage in databases</li>
 * </ul>
 *
 * <p>The hash format is: {@code algorithm:iterations:hashSize:salt:hash}</p>
 *
 * <p><strong>Healthcare Context:</strong> This utility is critical for protecting
 * healthcare provider credentials in compliance with HIPAA/PIPEDA security requirements.
 * All passwords must be securely hashed before storage, and verification must use
 * constant-time comparison to prevent timing-based attacks that could expose
 * sensitive authentication information.</p>
 *
 * <p><strong>Security Note:</strong> Never log, display, or transmit passwords or
 * password hashes in plain text. Always use the provided methods for hash creation
 * and verification.</p>
 *
 * <p>Based on reference implementation from crackstation.net/hashing-security.htm</p>
 *
 * @see ca.openosp.openo.managers.SecurityInfoManager
 * @see ca.openosp.openo.utility.LoggedInInfo
 * @since 2026-01-23
 */
public class PasswordHash
{

	/**
	 * Exception thrown when a password hash is invalid or malformed.
	 *
	 * <p>This exception indicates that the provided hash string does not conform
	 * to the expected format (algorithm:iterations:hashSize:salt:hash) or contains
	 * invalid data such as corrupted Base64 encoding or incorrect field counts.</p>
	 *
	 * @since 2026-01-23
	 */
	static public class InvalidHashException extends Exception {
		/**
		 * Constructs a new InvalidHashException with the specified detail message.
		 *
		 * @param message String the detail message explaining why the hash is invalid
		 */
		public InvalidHashException(String message) {
			super(message);
		}

		/**
		 * Constructs a new InvalidHashException with the specified detail message and cause.
		 *
		 * @param message String the detail message explaining why the hash is invalid
		 * @param source Throwable the cause of this exception (e.g., Base64 decoding exception)
		 */
		public InvalidHashException(String message, Throwable source) {
			super(message, source);
		}
	}

	/**
	 * Exception thrown when a password hashing operation cannot be performed.
	 *
	 * <p>This exception indicates a system-level failure such as an unsupported
	 * cryptographic algorithm, invalid key specification, or other operational
	 * errors that prevent password hashing or verification from completing.</p>
	 *
	 * @since 2026-01-23
	 */
	static public class CannotPerformOperationException extends Exception {
		/**
		 * Constructs a new CannotPerformOperationException with the specified detail message.
		 *
		 * @param message String the detail message explaining why the operation failed
		 */
		public CannotPerformOperationException(String message) {
			super(message);
		}

		/**
		 * Constructs a new CannotPerformOperationException with the specified detail message and cause.
		 *
		 * @param message String the detail message explaining why the operation failed
		 * @param source Throwable the cause of this exception (e.g., NoSuchAlgorithmException)
		 */
		public CannotPerformOperationException(String message, Throwable source) {
			super(message, source);
		}
	}

	/**
	 * The PBKDF2 algorithm identifier used for password hashing.
	 *
	 * <p><strong>Note:</strong> This constant is set to "PBKDF2WithHmacSHA2" which is not a valid
	 * Java algorithm name and will cause a NoSuchAlgorithmException at runtime. The actual
	 * implementation uses PBKDF2 with HMAC-SHA1 (indicated by the "sha1" algorithm identifier
	 * in the hash format at line 248 and verified at line 322). This is a known bug that will
	 * be fixed in a future PR to use a valid algorithm name such as "PBKDF2WithHmacSHA1" or
	 * upgrade to "PBKDF2WithHmacSHA256".</p>
	 *
	 * @see #createHash(char[])
	 * @see #verifyPassword(char[], String)
	 */
	public static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA2";

	/**
	 * Size of the cryptographic salt in bytes (24 bytes = 192 bits).
	 * This constant may be changed without breaking existing hashes.
	 */
	public static final int SALT_BYTE_SIZE = 24;

	/**
	 * Size of the resulting hash in bytes (18 bytes = 144 bits).
	 * This constant may be changed without breaking existing hashes.
	 */
	public static final int HASH_BYTE_SIZE = 18;

	/**
	 * Number of PBKDF2 iterations (64,000).
	 * Higher iteration counts increase security but also increase computation time.
	 * This constant may be changed without breaking existing hashes.
	 */
	public static final int PBKDF2_ITERATIONS = 64000;

	/**
	 * Number of fields in the hash string format (5 sections).
	 * This constant defines the encoding format and must not be changed.
	 */
	public static final int HASH_SECTIONS = 5;

	/**
	 * Index of the algorithm field in the hash string (position 0).
	 * This constant defines the encoding format and must not be changed.
	 */
	public static final int HASH_ALGORITHM_INDEX = 0;

	/**
	 * Index of the iteration count field in the hash string (position 1).
	 * This constant defines the encoding format and must not be changed.
	 */
	public static final int ITERATION_INDEX = 1;

	/**
	 * Index of the hash size field in the hash string (position 2).
	 * This constant defines the encoding format and must not be changed.
	 */
	public static final int HASH_SIZE_INDEX = 2;

	/**
	 * Index of the salt field in the hash string (position 3).
	 * This constant defines the encoding format and must not be changed.
	 */
	public static final int SALT_INDEX = 3;

	/**
	 * Index of the PBKDF2 hash field in the hash string (position 4).
	 * This constant defines the encoding format and must not be changed.
	 */
	public static final int PBKDF2_INDEX = 4;

	/**
	 * Creates a salted PBKDF2 hash of the password for secure storage.
	 *
	 * <p>This method converts the password string to a character array and delegates
	 * to {@link #createHash(char[])} for hash generation. The returned hash includes
	 * all necessary parameters (algorithm, iterations, salt) for later verification.</p>
	 *
	 * <p><strong>Healthcare Security:</strong> Use this method when storing healthcare
	 * provider passwords. Never store passwords in plain text. The generated hash is
	 * safe to store in the database.</p>
	 *
	 * @param password String the password to hash (will be converted to char array internally)
	 * @return String a salted PBKDF2 hash in the format "algorithm:iterations:hashSize:salt:hash"
	 * @throws CannotPerformOperationException if the PBKDF2 algorithm is not supported or key generation fails
	 * @see #createHash(char[])
	 * @see #verifyPassword(String, String)
	 */
	public static String createHash(String password)
			throws CannotPerformOperationException
	{
		return createHash(password.toCharArray());
	}

	/**
	 * Creates a salted PBKDF2 hash of the password using a character array.
	 *
	 * <p>This is the core hash creation method. It:</p>
	 * <ol>
	 * <li>Generates a cryptographically secure random salt using SecureRandom</li>
	 * <li>Applies PBKDF2 with HMAC-SHA1 using the configured iteration count</li>
	 * <li>Encodes the salt and hash using Base64 for safe database storage</li>
	 * <li>Returns a formatted string containing all parameters needed for verification</li>
	 * </ol>
	 *
	 * <p>The character array parameter is preferred over String for security-sensitive
	 * applications as it allows the password to be cleared from memory after use.</p>
	 *
	 * @param password char[] a character array containing the password to hash
	 * @return String a salted PBKDF2 hash in the format "algorithm:iterations:hashSize:salt:hash"
	 *         (currently algorithm is "sha1" as seen at line 248)
	 * @throws CannotPerformOperationException if the PBKDF2 algorithm is not supported or key generation fails
	 * @see #createHash(String)
	 * @see #verifyPassword(char[], String)
	 */
	public static String createHash(char[] password)
			throws CannotPerformOperationException
	{
		// Generate a random salt
		SecureRandom random = new SecureRandom();
		byte[] salt = new byte[SALT_BYTE_SIZE];
		random.nextBytes(salt);

		// Hash the password
		byte[] hash = pbkdf2(password, salt, PBKDF2_ITERATIONS, HASH_BYTE_SIZE);
		int hashSize = hash.length;

		// format: algorithm:iterations:hashSize:salt:hash
		String parts = "sha1:" +
				PBKDF2_ITERATIONS +
				":" + hashSize +
				":" +
				toBase64(salt) +
				":" +
				toBase64(hash);
		return parts;
	}

	/**
	 * Verifies a password against a stored hash for authentication purposes.
	 *
	 * <p>This method converts the password string to a character array and delegates
	 * to {@link #verifyPassword(char[], String)} for verification. The verification
	 * uses constant-time comparison to prevent timing attacks.</p>
	 *
	 * <p><strong>Healthcare Security:</strong> Use this method when authenticating
	 * healthcare providers. The constant-time comparison prevents attackers from
	 * using timing differences to extract password information.</p>
	 *
	 * @param password String the password to verify
	 * @param correctHash String the stored hash to verify against (in format "algorithm:iterations:hashSize:salt:hash")
	 * @return boolean true if the password matches the hash, false otherwise
	 * @throws CannotPerformOperationException if the hash uses an unsupported algorithm or key generation fails
	 * @throws InvalidHashException if the hash format is invalid or contains corrupted data
	 * @see #verifyPassword(char[], String)
	 * @see #createHash(String)
	 */
	public static boolean verifyPassword(String password, String correctHash)
			throws CannotPerformOperationException, InvalidHashException
	{
		return verifyPassword(password.toCharArray(), correctHash);
	}

	/**
	 * Verifies a password character array against a stored hash using constant-time comparison.
	 *
	 * <p>This is the core verification method. It:</p>
	 * <ol>
	 * <li>Parses the hash string to extract algorithm, iterations, salt, and expected hash</li>
	 * <li>Validates the hash format and parameters</li>
	 * <li>Recomputes the hash using the same salt and iterations</li>
	 * <li>Compares the hashes using constant-time comparison to prevent timing attacks</li>
	 * </ol>
	 *
	 * <p>The character array parameter is preferred over String for security-sensitive
	 * applications as it allows the password to be cleared from memory after use.</p>
	 *
	 * <p><strong>Security Note:</strong> The constant-time comparison (via {@link #slowEquals(byte[], byte[])})
	 * is critical for preventing timing attacks that could be used to extract password information
	 * in online authentication systems.</p>
	 *
	 * @param password char[] the password to verify as a character array
	 * @param correctHash String the stored hash to verify against (in format "algorithm:iterations:hashSize:salt:hash")
	 * @return boolean true if the password matches the hash, false otherwise
	 * @throws CannotPerformOperationException if the hash uses an unsupported algorithm (currently only SHA1 is supported)
	 * @throws InvalidHashException if the hash format is invalid, missing fields, has corrupted Base64 encoding, or invalid parameters
	 * @see #verifyPassword(String, String)
	 * @see #createHash(char[])
	 * @see #slowEquals(byte[], byte[])
	 */
	public static boolean verifyPassword(char[] password, String correctHash)
			throws CannotPerformOperationException, InvalidHashException
	{
		// Decode the hash into its parameters
		String[] params = correctHash.split(":");
		if (params.length != HASH_SECTIONS) {
			throw new InvalidHashException(
					"Fields are missing from the password hash."
			);
		}

		// Currently, Java only supports SHA1.
		if (!params[HASH_ALGORITHM_INDEX].equals("sha1")) {
			throw new CannotPerformOperationException(
					"Unsupported hash type."
			);
		}

		int iterations = 0;
		try {
			iterations = Integer.parseInt(params[ITERATION_INDEX]);
		} catch (NumberFormatException ex) {
			throw new InvalidHashException(
					"Could not parse the iteration count as an integer.", ex
			);
		}

		if (iterations < 1) {
			throw new InvalidHashException(
					"Invalid number of iterations. Must be >= 1."
			);
		}

		byte[] salt;
		try {
			salt = fromBase64(params[SALT_INDEX]);
		} catch (IllegalArgumentException ex) {
			throw new InvalidHashException(
					"Base64 decoding of salt failed.", ex
			);
		}

		byte[] hash;
		try {
			hash = fromBase64(params[PBKDF2_INDEX]);
		} catch (IllegalArgumentException ex) {
			throw new InvalidHashException(
					"Base64 decoding of pbkdf2 output failed.", ex
			);
		}


		int storedHashSize;
		try {
			storedHashSize = Integer.parseInt(params[HASH_SIZE_INDEX]);
		} catch (NumberFormatException ex) {
			throw new InvalidHashException(
					"Could not parse the hash size as an integer.", ex
			);
		}

		if (storedHashSize != hash.length) {
			throw new InvalidHashException(
					"Hash length doesn't match stored hash length."
			);
		}

		// Compute the hash of the provided password, using the same salt,
		// iteration count, and hash length
		byte[] testHash = pbkdf2(password, salt, iterations, hash.length);
		// Compare the hashes in constant time. The password is correct if
		// both hashes match.
		return slowEquals(hash, testHash);
	}

	/**
	 * Compares two byte arrays in length-constant time. This comparison method
	 * is used so that password hashes cannot be extracted from an on-line
	 * system using a timing attack and then attacked off-line.
	 *
	 * @param   a       the first byte array
	 * @param   b       the second byte array
	 * @return          true if both byte arrays are the same, false if not
	 */
	private static boolean slowEquals(byte[] a, byte[] b)
	{
		int diff = a.length ^ b.length;
		for(int i = 0; i < a.length && i < b.length; i++)
			diff |= a[i] ^ b[i];
		return diff == 0;
	}

	/**
	 *  Computes the PBKDF2 hash of a password.
	 *
	 * @param   password    the password to hash.
	 * @param   salt        the salt
	 * @param   iterations  the iteration count (slowness factor)
	 * @param   bytes       the length of the hash to compute in bytes
	 * @return              the PBDKF2 hash of the password
	 */
	private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes)
			throws CannotPerformOperationException
	{
		try {
			PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
			SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
			return skf.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException ex) {
			throw new CannotPerformOperationException(
					"Hash algorithm not supported.",
					ex
			);
		} catch (InvalidKeySpecException ex) {
			throw new CannotPerformOperationException(
					"Invalid key spec.",ex
			);
		}
	}

	private static byte[] fromBase64(String hex)
			throws IllegalArgumentException
	{
		return Base64.getDecoder().decode(hex);
	}

	private static String toBase64(byte[] array)
	{
		return Base64.getEncoder().encodeToString(array);
	}

}
