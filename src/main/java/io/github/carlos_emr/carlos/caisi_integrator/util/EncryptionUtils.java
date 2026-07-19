package io.github.carlos_emr.carlos.caisi_integrator.util;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

public final class EncryptionUtils
{
	private static final Logger logger=MiscUtils.getLogger();
	
	private static final MessageDigest messageDigest = initMessageDigest();

	private static MessageDigest initMessageDigest()
	{
		try
		{
			return (MessageDigest.getInstance("SHA-1"));
		}
		catch (NoSuchAlgorithmException e)
		{
			logger.error("Error", e);
			return (null);
		}
	}

	/**
	 * We're only really using this to encrypt passwords so it's not too often so syncrhonisation on 1 instance shouldn't be too bad.
	 */
	public static byte[] getSha1(String s)
	{
		// A JCS cache version was attempted,
		// the time savings was about .001ms per call
		// as a result, it was decided that caching was not 
		// that effective here.
		
		if (s==null) return (null);
		
        try
		{        	
    		synchronized (messageDigest)
			{					
    			return (messageDigest.digest(s.getBytes()));
			}
		}
		catch (Exception e)
		{
			logger.error("Unexpected error.", e);
			return (null);
		}
	}
	
	public static byte[] generateEncryptionKey() throws NoSuchAlgorithmException
	{
		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(128);

		SecretKey secretKey = keyGenerator.generateKey();
		return (secretKey.getEncoded());
	}
	
	public static byte[] encrypt(SecretKeySpec secretKeySpec, byte[] plainData) throws IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException
	{
		if (secretKeySpec==null) return (plainData);
		
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
		byte[] results=cipher.doFinal(plainData);
		return (results);
	}
	
	public static byte[] decrypt(SecretKeySpec secretKeySpec, byte[] encryptedData) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException
	{
		if (secretKeySpec==null) return (encryptedData);
		
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
		byte[] results=cipher.doFinal(encryptedData);
		return (results);
	}
	
	public static void main(String... argv)
	{
		String temp="one two three four";
		logger.info(new String(Hex.encodeHex(getSha1(temp))));
	}
}
