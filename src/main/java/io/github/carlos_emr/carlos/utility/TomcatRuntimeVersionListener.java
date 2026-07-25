/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.apache.tomcat.util.IntrospectionUtils;

/**
 * Refuses deployment on a Tomcat runtime older than the supported security baseline.
 */
public final class TomcatRuntimeVersionListener implements ServletContextListener {

    static final String MINIMUM_VERSION = "11.0.24";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String implementationVersion = IntrospectionUtils.class.getPackage().getImplementationVersion();
        String runtimeVersion = implementationVersion != null
                ? implementationVersion
                : versionFromServerInfo(event.getServletContext().getServerInfo());
        if (!isSupported(runtimeVersion)) {
            throw new IllegalStateException(
                    "CARLOS requires Apache Tomcat " + MINIMUM_VERSION
                    + " or a newer 11.0.x patch release; detected " + runtimeVersion);
        }
    }

    static boolean isSupported(String version) {
        if (version == null) {
            return false;
        }
        String[] parts = version.split("[.-]", 4);
        if (parts.length < 3) {
            return false;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);
            return major == 11 && minor == 0 && patch >= 24;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String versionFromServerInfo(String serverInfo) {
        if (serverInfo == null) {
            return null;
        }
        int separator = serverInfo.lastIndexOf('/');
        return separator >= 0 ? serverInfo.substring(separator + 1) : serverInfo;
    }
}
