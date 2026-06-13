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
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.RefinedSoundex;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

    /** Maximum deserialization object graph depth to prevent stack-overflow bombs. */
    private static final int MAX_DESER_DEPTH = 20;
    /** Maximum object reference count to prevent reference-expansion bombs. */
    private static final long MAX_DESER_REFS = 100_000L;
    /** Maximum stream byte count (10 MB) to prevent oversized payload bombs. */
    private static final long MAX_DESER_BYTES = 10_000_000L;
    /** Maximum array length to prevent large-array allocation bombs. */
    private static final int MAX_DESER_ARRAY = 10_000;

    /**
     * Restricts ObjectInputStream deserialization to safe classes only.
     * Allows standard Java types and CARLOS domain objects; rejects everything else
     * to prevent remote code execution via untrusted deserialized data.
     *
     * <p>Also enforces resource bounds (depth, references, stream bytes, array length)
     * to mitigate deserialization bomb (DoS) attacks.
     *
     * <p>Array types are restricted to primitive arrays and object arrays whose
     * component package is already on the allowlist. Multi-dimensional arrays
     * ({@code name.startsWith("[[")}) are permitted at the descriptor level because
     * the filter is also invoked for each element's class, providing defence-in-depth.
     */
    private static final ObjectInputFilter DESERIALIZATION_FILTER = filterInfo -> {
        if (filterInfo.depth() > MAX_DESER_DEPTH ||
            filterInfo.references() > MAX_DESER_REFS ||
            filterInfo.streamBytes() > MAX_DESER_BYTES ||
            (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > MAX_DESER_ARRAY)) {
            return ObjectInputFilter.Status.REJECTED;
        }
        if (filterInfo.serialClass() != null) {
            String name = filterInfo.serialClass().getName();
            if (name.startsWith("java.lang.") ||
                name.startsWith("java.util.") ||
                name.startsWith("java.io.") ||
                name.startsWith("java.math.") ||
                // Primitive array types: [B=byte[], [I=int[], [J=long[], [F=float[],
                // [D=double[], [Z=boolean[], [C=char[], [S=short[]
                name.equals("[B") || name.equals("[I") || name.equals("[J") || name.equals("[F") ||
                name.equals("[D") || name.equals("[Z") || name.equals("[C") || name.equals("[S") ||
                // Object arrays restricted to the same allowed package prefixes
                name.startsWith("[Ljava.lang.") || name.startsWith("[Ljava.util.") ||
                name.startsWith("[Ljava.io.") || name.startsWith("[Ljava.math.") ||
                name.startsWith("[Lio.github.carlos_emr.carlos.") ||
                // Multi-dimensional arrays; element classes are still checked individually
                name.startsWith("[[") ||
                name.startsWith("io.github.carlos_emr.carlos.")) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            return ObjectInputFilter.Status.REJECTED;
        }
        // Non-class invocations (metrics-only updates for depth/refs/bytes) —
        // resource bounds already checked above, so allow them to proceed.
        return ObjectInputFilter.Status.ALLOWED;
    };

    public MiscUtils() {
    }

    // FindSecBugs CRLF_INJECTION_LOGS: resolvedLocation derives from the log4j.override.configuration JVM system property and the servlet container's contextPath (getServletContext().getContextPath()); both are trusted server/deployment config, not request input.
    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS", justification = "resolvedLocation is built from the log4j.override.configuration system property and the servlet context path, both trusted deployment config; no request-controlled CR/LF.")
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

            File configFile;
            try {
                configFile = PathValidationUtils.resolveConfiguredFile(resolvedLocation, "log4j override configuration");
            } catch (SecurityException e) {
                getLogger().warn("log4j.override.configuration points to a missing or unreadable file: " + resolvedLocation, e);
                return;
            }
            if (!configFile.isFile() || !configFile.canRead()) {
                getLogger().warn("log4j.override.configuration points to a missing or unreadable file: " + resolvedLocation);
                return;
            }

            getLogger().info("loading additional override logging configuration from : " + resolvedLocation);
            // Auto-reload on file change requires monitorInterval="N" on the
            // <Configuration> root element of the override XML.
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.setConfigLocation(configFile.toURI());
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
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(s);
        }
        return baos.toByteArray();
    }

    // FP for deserialization scanners (CodeQL java/UnsafeDeserialization, Semgrep
    // object-deserialization): DESERIALIZATION_FILTER blocks JDK gadget packages
    // (java.net.*, com.sun.*, javax.naming.*, Spring, CommonsCollections) and restricts
    // to java.lang/util/io/math + the project's own namespace. No project class defines
    // a side-effecting readObject/readResolve (verified via grep). Filter set BEFORE
    // readObject is called. Callers pass internally-serialized Integrator payloads.
    @SuppressFBWarnings(value = "OBJECT_DESERIALIZATION", justification = "ObjectInputFilter is installed before readObject and limits classes/resources; callers pass internally serialized payloads")
    public static Serializable deserialize(byte[] b) throws IOException, ClassNotFoundException { // lgtm[java/unsafe-deserialization]
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b))) {
            ois.setObjectInputFilter(DESERIALIZATION_FILTER);
            return (Serializable) ois.readObject(); // nosemgrep: java.lang.security.audit.object-deserialization.object-deserialization
        }
    }

    public static void serializeToFile(Serializable s, String filename) throws IOException {
        File outputFile;
        try {
            outputFile = PathValidationUtils.resolveConfiguredFile(filename, "serialized output file");
        } catch (SecurityException e) {
            // Preserve the declared IOException contract (mirrors deserializeFromFile): a blank or
            // un-canonicalizable path surfaces as a checked IOException, not an unchecked SecurityException.
            throw new IOException("Cannot write serialized output file", e);
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(s);
            oos.flush();
            fos.flush();
        }
    }

    // FP for deserialization scanners: same DESERIALIZATION_FILTER as deserialize(byte[])
    // above; callers pass filenames that resolve to internal classpath resources or
    // validated configured files written by the application itself (not user-uploaded bytes).
    @SuppressFBWarnings(value = "OBJECT_DESERIALIZATION", justification = "ObjectInputFilter is installed before readObject; inputs are internal classpath resources or validated configured files")
    public static Serializable deserializeFromFile(String filename) throws IOException, ClassNotFoundException { // lgtm[java/unsafe-deserialization]
        InputStream rawIs = MiscUtils.class.getResourceAsStream(filename);
        if (rawIs == null) {
            File inputFile;
            try {
                inputFile = PathValidationUtils.validateConfiguredFile(filename, "serialized input file");
            } catch (SecurityException e) {
                // Preserve the declared IOException contract: a missing/invalid file surfaced as
                // FileNotFoundException before this validation was added, so callers catching
                // IOException for "not found" keep working.
                throw new IOException("Cannot read serialized input file", e);
            }
            rawIs = new FileInputStream(inputFile);
        }
        // Include rawIs in try-with-resources so it is closed even if
        // ObjectInputStream construction throws (e.g. StreamCorruptedException).
        try (InputStream is = rawIs;
             ObjectInputStream ois = new ObjectInputStream(is)) {
            ois.setObjectInputFilter(DESERIALIZATION_FILTER);
            return (Serializable) ois.readObject(); // nosemgrep: java.lang.security.audit.object-deserialization.object-deserialization
        }
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
	 * Sanitizes a file name to ensure it is safe for use and complies with naming conventions.
	 *		<p>- Replace spaces with underscores.
	 * 		<p>- Remove characters outside {@code [a-zA-Z0-9._]}, including hyphens.
	 * 		<p>- Collapse repeated dots.
	 *
	 * @param fileName The original file name to be sanitized. It must not be {@code null}.
	 * @return A sanitized value of the input file name.
	 * @throws NullPointerException if the input fileName is {@code null}.
	 * @deprecated Use {@link PathValidationUtils#validateFileName(String)} for filename-only
	 * validation or {@link PathValidationUtils#validateUserFilePath(String, File)} when
	 * constructing file paths from user-provided filenames.
	 */
	@Deprecated(since = "2026-05-21", forRemoval = true)
	public static String sanitizeFileName(String fileName) {
        return PathValidationUtils.normalizeFileNameCharacters(fileName);
	}

}
