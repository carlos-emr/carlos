//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.utility;

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
import ca.openosp.openo.utility.CxfClientUtils.TrustAllManager;

/**
 * When using the shutdown hook...
 * <br /><br />
 * In the context of a normal JVM, you only need to use
 * the register and deregister methods where ever appropriate.
 * In your code you can periodically checkShutdownSignaled on long
 * running threads.
 * <br /><br />
 * In the conext of a application context such as a servlet
 * container. You should register and deregister in the context
 * startup/shutdown methods. In addition you should manually flag
 * set shutdownSignaled=true upon context shutdown as the jvm
 * itself may not be shutting down and no shutdown hook signal
 * maybe sent. Similarly you should set the shutdownSignaled=false
 * upon startup as it may have been set true by a previous context stop
 * even though the jvm itself has not restarted.
 * <p>
 * -----------------------
 * <p>
 * This file has been renamed to "Old" because this file should no longer be enhanced. A commons version of this class
 * is made available from the Utils package. There maybe some methods left here which don't entirely make sense
 * or don't make sense in the context of a general purpose project agnostic utility class. This class still exists as "Old" so
 * we can slowly refactor the non sensical code to use the new commons utilities. Any remaining methods which do make sense
 * should then me moved to a generic Oscar Utility class or similar. If the method makes sense in a project
 * agnostic fashion, then it should be moved to the util project itself.
 */
public final class MiscUtils {
    public static final String DEFAULT_UTF8_ENCODING = "UTF-8";
    private static boolean shutdownSignaled = false;
    private static Thread shutdownHookThread = null;

    public MiscUtils() {
    }

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

    public static Logger getLogger() {
        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        String caller = ste[2].getClassName();
        return LogManager.getLogger(caller);
    }

    public static String getBuildDateTime() {
        return ConfigXmlUtils.getPropertyString("misc", "build_date_time");
    }

    public static String trimToNullLowerCase(String s) {
        s = StringUtils.trimToNull(s);
        if (s != null) {
            s = s.toLowerCase();
        }

        return s;
    }

    public static String trimToNullUpperCase(String s) {
        s = StringUtils.trimToNull(s);
        if (s != null) {
            s = s.toUpperCase();
        }

        return s;
    }

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

    public static byte[] serialize(Serializable s) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(s);
        return baos.toByteArray();
    }

    public static Serializable deserialize(byte[] b) throws IOException, ClassNotFoundException {
        return (Serializable) (new ObjectInputStream(new ByteArrayInputStream(b))).readObject();
    }

    public static void serializeToFile(Serializable s, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(s);
            oos.flush();
            fos.flush();
        }
    }

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

    public static byte[] readFileAsByteArray(String url) throws IOException {
        try (InputStream is = MiscUtils.class.getResourceAsStream(url)) {
            int size = is.available();
            byte[] b = new byte[size];
            is.read(b);
            return b;
        }
    }

    public static String readFileAsString(String url) throws IOException {
        return new String(readFileAsByteArray(url));
    }

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

    public static boolean soundex(String s1, String s2) throws EncoderException {
        return soundexScore(s1, s2) >= 4;
    }

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
