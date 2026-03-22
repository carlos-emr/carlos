/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.RefinedSoundex;
import org.apache.commons.lang3.StringUtils;

import org.apache.log4j.xml.DOMConfigurator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.CxfClientUtils.TrustAllManager;

/**
 * General-purpose utility class providing miscellaneous helper methods for logging,
 * string manipulation, serialization, file I/O, SSL configuration, and JVM shutdown management.
 *
 * <p>Includes a shutdown hook mechanism for graceful JVM/container shutdown. In servlet
 * container environments, register via {@link #registerShutdownHook()} on context startup
 * and set {@code shutdownSignaled=true} on context shutdown. Long-running threads should
 * periodically call {@link #checkShutdownSignaled()} to detect shutdown requests.
 *
 * @since 2026-03-17
 */
public final class MiscUtils {
    public static final String DEFAULT_UTF8_ENCODING = "UTF-8";
    private static boolean shutdownSignaled = false;
    private static Thread shutdownHookThread = null;

    public MiscUtils() {
    }

    /**
     * Loads an additional Log4j override configuration file if specified via the
     * {@code log4j.override.configuration} system property.
     *
     * @param contextPath String the servlet context path for variable substitution
     */
    public static void addLoggingOverrideConfiguration(String contextPath) {
        String configLocation = System.getProperty("log4j.override.configuration");
        if (configLocation != null) {
            if (contextPath != null) {
                if (contextPath.length() > 0 && contextPath.charAt(0) == '/') {
                    contextPath = contextPath.substring(1);
                }

                if (contextPath.length() > 0 && contextPath.charAt(contextPath.length() - 1) == '/') {
                    contextPath = contextPath.substring(0, contextPath.length() - 2);
                }
            }

            String resolvedLocation = configLocation.replace("${contextName}", contextPath);
            getLogger().info("loading additional override logging configuration from : " + resolvedLocation);
            DOMConfigurator.configureAndWatch(resolvedLocation);
        }

    }

    /**
     * Returns a Log4j logger named after the calling class. Inspects the call stack
     * to automatically determine the caller's class name.
     *
     * @return Logger a logger for the calling class
     */
    public static Logger getLogger() {
        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        String caller = ste[2].getClassName();
        return LogManager.getLogger(caller);
    }

    /**
     * Returns the application build date/time from configuration.
     *
     * @return String the build datetime string, or {@code null} if not configured
     */
    public static String getBuildDateTime() {
        return ConfigXmlUtils.getPropertyString("misc", "build_date_time");
    }

    /**
     * Trims and lowercases a string, returning {@code null} for blank inputs.
     *
     * @param s String the input string
     * @return String the trimmed lowercase string, or {@code null} if blank
     */
    public static String trimToNullLowerCase(String s) {
        s = StringUtils.trimToNull(s);
        if (s != null) {
            s = s.toLowerCase();
        }

        return s;
    }

    /**
     * Trims and uppercases a string, returning {@code null} for blank inputs.
     *
     * @param s String the input string
     * @return String the trimmed uppercase string, or {@code null} if blank
     */
    public static String trimToNullUpperCase(String s) {
        s = StringUtils.trimToNull(s);
        if (s != null) {
            s = s.toUpperCase();
        }

        return s;
    }

    /**
     * Returns the local part of an email address (everything before the '@' symbol).
     *
     * @param s String the email address
     * @return String the local part, or {@code null} if the input is {@code null}
     */
    public static String getEmailAddressNoDomain(String s) {
        if (s == null) {
            return null;
        } else {
            int indexOfAt = s.indexOf(64);
            if (indexOfAt != -1) {
                s = s.substring(0, indexOfAt);
            }

            return s;
        }
    }

    /**
     * Serializes an object to a byte array.
     *
     * @param s Serializable the object to serialize
     * @return byte[] the serialized byte representation
     * @throws IOException if serialization fails
     */
    public static byte[] serialize(Serializable s) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(s);
        return baos.toByteArray();
    }

    /**
     * Deserializes an object from a byte array.
     *
     * @param b byte[] the serialized byte data
     * @return Serializable the deserialized object
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if the object's class cannot be found
     */
    public static Serializable deserialize(byte[] b) throws IOException, ClassNotFoundException {
        return (Serializable) (new ObjectInputStream(new ByteArrayInputStream(b))).readObject();
    }

    /**
     * Serializes an object to a file.
     *
     * @param s        Serializable the object to serialize
     * @param filename String the file path to write to
     * @throws IOException if file writing fails
     */
    public static void serializeToFile(Serializable s, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(s);
            oos.flush();
            fos.flush();
        }
    }

    /**
     * Deserializes an object from a file, trying the classpath first then the filesystem.
     *
     * @param filename String the file path or classpath resource
     * @return Serializable the deserialized object
     * @throws IOException            if the file cannot be read
     * @throws ClassNotFoundException if the object's class cannot be found
     */
    public static Serializable deserializeFromFile(String filename) throws IOException, ClassNotFoundException {
        InputStream is = MiscUtils.class.getResourceAsStream(filename);
        if (is == null) {
            is = new FileInputStream(filename);
        }

        Serializable var2;
        try {
            var2 = (Serializable) (new ObjectInputStream((InputStream) is)).readObject();
        } finally {
            ((InputStream) is).close();
        }

        return var2;
    }

    /**
     * Reads a classpath resource into a byte array.
     *
     * @param url String the classpath resource path
     * @return byte[] the file contents
     * @throws IOException if the file cannot be read
     */
    public static byte[] readFileAsByteArray(String url) throws IOException {
        try (InputStream is = MiscUtils.class.getResourceAsStream(url)) {
            int size = is.available();
            byte[] b = new byte[size];
            is.read(b);
            return b;
        }
    }

    /**
     * Reads a classpath resource as a string using the default charset.
     *
     * @param url String the classpath resource path
     * @return String the file contents
     * @throws IOException if the file cannot be read
     */
    public static String readFileAsString(String url) throws IOException {
        return new String(readFileAsByteArray(url));
    }

    /**
     * Generates a random string of printable ASCII characters, excluding visually
     * ambiguous characters (e.g., 0/O, 1/l/I) and special characters that may
     * cause issues in certain contexts.
     *
     * @param length int the desired string length
     * @return String a random string of the specified length
     */
    public static String getRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        while (sb.length() < length) {
            int ch = random.nextInt(89);
            ch += 33;
            if (ch != 39 && ch != 96 && ch != 34 && ch != 49 && ch != 73 && ch != 108 && ch != 48 && ch != 111 && ch != 79 && ch != 44 && ch != 61) {
                sb.append((char) ch);
            }
        }

        return sb.toString();
    }

    /**
     * Escapes a string for safe inclusion in a CSV field by quoting and escaping
     * double quotes, commas, and newlines.
     *
     * @param s String the value to escape
     * @return String the CSV-safe value, or {@code null} if the input is {@code null}
     */
    public static String escapeCsv(String s) {
        if (s == null) {
            return null;
        } else {
            boolean requiresQuoting = false;
            if (s.contains("\"")) {
                s = s.replaceAll("\"", "\"\"");
                requiresQuoting = true;
            }

            if (s.contains(",") || s.contains("\n")) {
                requiresQuoting = true;
            }

            if (requiresQuoting) {
                s = '"' + s + '"';
            }

            return s;
        }
    }

    /**
     * Configures the JVM's default SSL socket factory to accept all certificates
     * and hostnames. Used for inter-system communication in development environments.
     *
     * @throws NoSuchAlgorithmException if the TLS algorithm is not available
     * @throws KeyManagementException   if the SSL context cannot be initialized
     */
    public static void setJvmDefaultSSLSocketFactoryAllowAllCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        TrustAllManager[] tam = new TrustAllManager[]{new TrustAllManager()};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init((KeyManager[]) null, tam, new SecureRandom());
        SSLSocketFactory sslSocketFactory = ctx.getSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
        HostnameVerifier hostNameVerifier = new HostnameVerifier() {
            public boolean verify(String host, SSLSession sslSession) {
                return true;
            }
        };
        HttpsURLConnection.setDefaultHostnameVerifier(hostNameVerifier);
    }

    /**
     * Returns whether two strings are phonetically similar using the Refined Soundex algorithm
     * (score of 4 or higher).
     *
     * @param s1 String the first string
     * @param s2 String the second string
     * @return boolean {@code true} if the strings are phonetically similar
     * @throws EncoderException if encoding fails
     */
    public static boolean soundex(String s1, String s2) throws EncoderException {
        return soundexScore(s1, s2) >= 4;
    }

    /**
     * Returns the Refined Soundex similarity score between two strings.
     *
     * @param s1 String the first string
     * @param s2 String the second string
     * @return int the similarity score (higher is more similar), or -1 if either is blank
     * @throws EncoderException if encoding fails
     */
    public static int soundexScore(String s1, String s2) throws EncoderException {
        s1 = StringUtils.trimToNull(s1);
        s2 = StringUtils.trimToNull(s2);
        if (s1 != null && s2 != null) {
            s1 = s1.toLowerCase();
            s2 = s2.toLowerCase();
            RefinedSoundex soundex = new RefinedSoundex();
            int difference = soundex.difference(s1, s2);
            return difference;
        } else {
            return -1;
        }
    }

    private static class ShutdownHookThread extends Thread {
        // can't have override until everyone uses jdk1.6
        // @Override
        public void run() {
            shutdownSignaled = true;
        }
    }

    /**
     * Checks if a JVM shutdown has been signaled and throws if so.
     *
     * @throws ShutdownException if shutdown has been signaled
     */
    public static void checkShutdownSignaled() throws ShutdownException {
        if (shutdownSignaled) throw (new ShutdownException());
    }

    /**
     * This method should in most cases only be called by the context startup listener.
     */
    public static synchronized void registerShutdownHook() {
        if (shutdownHookThread == null) {
            shutdownHookThread = new MiscUtils.ShutdownHookThread();
            Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        }
    }

    /**
     * Deregisters the JVM shutdown hook. Should be called during context shutdown.
     */
    public static synchronized void deregisterShutdownHook() {
        if (shutdownHookThread != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            shutdownHookThread = null;
        }
    }

    /**
     * This menthod should only ever be called by a context startup listener. Other than that, the shutdown signal should be set by the shutdown hook.
     */
    protected static void setShutdownSignaled(boolean shutdownSignaled) {
        MiscUtils.shutdownSignaled = shutdownSignaled;
    }


	/**
	 * Sanitizes a file name to ensure it is safe for use and complies with naming conventions .
	 *		<p>- Replace spaces with underscores
	 * 		<p>- Remove invalid characters, excluding valid delimiter like {@code -}.
	 * 		<p>- Remove repeated dots
	 *
	 * @param fileName The original file name to be sanitized. It must not be {@code null}.
	 * @return A sanitized value of the input file name.
	 * @throws NullPointerException if the input fileName is {@code null}.
	 */
	public static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9._]", "")
                .replaceAll("\\.+", ".");
	}

}
