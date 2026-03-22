
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

package io.github.carlos_emr.carlos.admin.traceability;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class that reads build metadata from the {@code buildNumber.properties} resource file.
 *
 * <p>Loads the properties file once at class initialization time and provides static
 * access to build identifiers such as the Git SHA-1 commit hash. Used by the
 * traceability system to embed build information in trace data.
 *
 * @see TraceDataProcessor
 * @since 2026-03-17
 */
public class BuildNumberPropertiesFileReader {

    private static final Properties properties;

    static {
        InputStream inputStream = BuildNumberPropertiesFileReader.class.getResourceAsStream("/buildNumber.properties");
        properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read properties file: buildNumber.properties", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to close: buildNumber.properties", e);
                }
            }
        }
    }

    private BuildNumberPropertiesFileReader() {
    }

    /**
     * Returns the Git SHA-1 commit hash from the build properties.
     *
     * @return String the Git SHA-1 hash of the build commit
     */
    public static String getGitSha1() {
        return properties.getProperty("git-sha-1");
    }
}
