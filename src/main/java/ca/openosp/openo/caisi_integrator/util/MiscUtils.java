package ca.openosp.openo.caisi_integrator.util;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import java.util.Timer;

public class MiscUtils
{
    public static final char ID_SEPARATOR = ':';
    public static final Integer NULL_ID_PLACEHOLDER;
    private static final String VALID_USERNAME_CHARACTERS = "abcdefghijklmnopqrstuvwxyz1234567890-_.";
    private static Timer sharedTimer;
    
    public static final Timer getSharedTimer() {
        if (MiscUtils.sharedTimer == null) {
            MiscUtils.sharedTimer = new Timer("Shared Generic Timer", true);
        }
        return MiscUtils.sharedTimer;
    }
    
    protected static void addLoggingOverrideConfiguration(String contextPath) {
        final String configLocation = System.getProperty("log4j.override.configuration");
        if (configLocation != null) {
            if (contextPath != null) {
                if (contextPath.length() > 0 && contextPath.charAt(0) == '/') {
                    contextPath = contextPath.substring(1);
                }
                if (contextPath.length() > 0 && contextPath.charAt(contextPath.length() - 1) == '/') {
                    contextPath = contextPath.substring(0, contextPath.length() - 2);
                }
            }
            final String resolvedLocation = configLocation.replace("${contextName}", contextPath);
            getLogger().info((Object)("loading additional override logging configuration from : " + resolvedLocation));
            DOMConfigurator.configureAndWatch(resolvedLocation);
        }
    }
    
    public static Logger getLogger() {
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        final String caller = ste[2].getClassName();
        return Logger.getLogger(caller);
    }
    
    public static String getAsBracketedList(final Object[] list, final boolean singleQuoteItems) {
        final StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (final Object item : list) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            if (singleQuoteItems) {
                sb.append('\'');
            }
            sb.append(item.toString());
            if (singleQuoteItems) {
                sb.append('\'');
            }
        }
        sb.append(')');
        return sb.toString();
    }
    
    public static String getBuildDateTime() {
        return ConfigXmlUtils.getPropertyString("misc", "build_date_time");
    }
    
    public static String validateAndNormaliseUserName(String userName) {
        userName = StringUtils.trimToNull(userName);
        if (userName == null) {
            throw new IllegalArgumentException("usernames can not be blank");
        }
        userName = userName.toLowerCase();
        if (!StringUtils.containsOnly(userName, "abcdefghijklmnopqrstuvwxyz1234567890-_.")) {
            throw new IllegalArgumentException("username are only allowed to have letters, numbers, dashes, underscores, and periods : " + userName);
        }
        return userName;
    }
    
    public static byte[] propertiesToXmlByteArray(final Properties p) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        p.storeToXML(os, null);
        return os.toByteArray();
    }
    
    public static Properties xmlByteArrayToProperties(final byte[] b) throws IOException {
        final Properties p = new Properties();
        final ByteArrayInputStream is = new ByteArrayInputStream(b);
        p.loadFromXML(is);
        return p;
    }
    
    public static String trimToNullLowerCase(String s) {
        s = StringUtils.trimToNull(s);
        if (s != null) {
            s = s.toLowerCase();
        }
        return s;
    }
    
    public static String alphanumFilter(final String filterMe) {
        return filterMe.replaceAll("[^a-zA-Z0-9]", "");
    }
    
    public static String arbitraryFilter(final String filterMe, final String replaceThese, final String withThis) {
        return filterMe.replaceAll(replaceThese, withThis);
    }
    
    static {
        NULL_ID_PLACEHOLDER = -1;
        MiscUtils.sharedTimer = null;
    }
}
