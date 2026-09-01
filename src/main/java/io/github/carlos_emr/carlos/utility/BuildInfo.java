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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Build identity of the deployed CARLOS artifact: Maven project version, build date and the
 * optional CI job / build number stamp.
 *
 * <p>The values come from {@code carlos-build.properties} on the classpath, which Maven writes at
 * build time (resource filtering for the version, the antrun step for the date and stamp). They are
 * a property of the WAR, not of the deployment, and are therefore deliberately <em>not</em> read
 * through {@link io.github.carlos_emr.CarlosProperties}: that file is re-loaded from an operator
 * override copy ({@code /etc/carlos-emr/carlos.properties}, the devcontainer volume, ...) on top of
 * the in-WAR copy, so a build stamp carried there was frozen at first install and shadowed every
 * later WAR upgrade. Nothing in this class can be overridden by configuration.</p>
 *
 * <p>Values that still contain an unsubstituted {@code ${...}} placeholder are treated as absent so
 * a build made outside the normal toolchain can never render raw placeholder text on the login
 * page, which is visible to unauthenticated visitors.</p>
 *
 * @since 2026-09-01
 */
public final class BuildInfo {

    /** Classpath location of the build stamp written by the Maven build. */
    public static final String RESOURCE = "/carlos-build.properties";

    static final String VERSION_KEY = "build.version";
    static final String DATE_KEY = "build.date";
    static final String JOB_KEY = "build.job";
    static final String NUMBER_KEY = "build.number";

    /** Rendered when no version could be determined at all. */
    static final String UNKNOWN = "unknown";

    private static final Logger LOGGER = LogManager.getLogger(BuildInfo.class);
    private static final BuildInfo INSTANCE = fromClasspath();

    private final String version;
    private final String buildDate;
    private final String jobName;
    private final String buildNumber;

    BuildInfo(Properties properties) {
        this.version = clean(properties.getProperty(VERSION_KEY));
        this.buildDate = clean(properties.getProperty(DATE_KEY));
        this.jobName = clean(properties.getProperty(JOB_KEY));
        this.buildNumber = clean(properties.getProperty(NUMBER_KEY));
    }

    /**
     * @return the build identity of the running artifact; never {@code null}
     */
    public static BuildInfo getInstance() {
        return INSTANCE;
    }

    static BuildInfo fromClasspath() {
        Properties properties = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warn("Build stamp {} not found on the classpath; build identity is unknown", RESOURCE);
            } else {
                properties.load(in);
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to read build stamp {}; build identity is unknown", RESOURCE, e);
        }
        return new BuildInfo(properties);
    }

    /** @return the Maven project version the artifact was built from, or empty */
    public String getVersion() {
        return version;
    }

    /** @return the build date as formatted by the build, or empty */
    public String getBuildDate() {
        return buildDate;
    }

    /** @return the CI job name ({@code JOB_NAME}) the artifact was built under, or empty */
    public String getJobName() {
        return jobName;
    }

    /** @return the CI build number or image stamp ({@code BUILD_NUMBER}), or empty */
    public String getBuildNumber() {
        return buildNumber;
    }

    /**
     * Human-readable build tag shown on the login page, the About page and in REST response
     * headers: the project version, followed by the CI job / build number in parentheses when the
     * build carried one. Examples: {@code 2026.08.0-alpha11},
     * {@code 2026.08.0-alpha11-SNAPSHOT (carlos-emr-deb 2026.08.0~alpha11)}.
     *
     * @return the build tag; {@value #UNKNOWN} when nothing is known
     */
    public String getBuildTag() {
        String stamp = (jobName + " " + buildNumber).trim();
        if (version.isEmpty()) {
            return stamp.isEmpty() ? UNKNOWN : stamp;
        }
        return stamp.isEmpty() ? version : version + " (" + stamp + ")";
    }

    /**
     * Normalizes a raw stamp value: {@code null}, blank and unsubstituted {@code ${...}}
     * placeholders all become the empty string.
     */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.contains("${") ? "" : trimmed;
    }
}
