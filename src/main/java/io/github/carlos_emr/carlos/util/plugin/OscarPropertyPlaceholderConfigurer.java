/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util.plugin;

import java.util.Properties;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

/**
 * Spring {@link PropertyPlaceholderConfigurer} extension that resolves property placeholders
 * prefixed with {@code oscar.} from {@link CarlosProperties}. Falls back to standard Spring
 * property resolution for non-CARLOS placeholders.
 *
 * @since 2006-01-01
 */
public class OscarPropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer {


    /**
     * Resolves a placeholder, first checking CARLOS properties for {@code oscar.} prefixed keys,
     * then falling back to standard Spring resolution.
     *
     * @param placeholder String the placeholder to resolve
     * @param properties Properties the merged Spring properties
     * @param systemPropertiesMode int the system properties resolution mode
     * @return String the resolved value, or null if not found
     */
    protected String resolvePlaceholder(String placeholder, Properties properties, int systemPropertiesMode) {

        Properties p2 = CarlosProperties.getInstance();
        MiscUtils.getLogger().debug("oscarproperties=" + p2.toString());
        if (p2 != null && placeholder.startsWith("oscar.")) {
            String value = p2.getProperty(placeholder.substring(6));
            MiscUtils.getLogger().debug("resolveplaceholder1:" + placeholder.substring(6) + "=" + value);
            if (value != null) {
                return value;
            }
        }

        return super.resolvePlaceholder(placeholder, properties, systemPropertiesMode);
    }

    /**
     * Resolves a placeholder, first checking CARLOS properties for {@code oscar.} prefixed keys,
     * then falling back to standard Spring resolution.
     *
     * @param placeholder String the placeholder to resolve
     * @param properties Properties the merged Spring properties
     * @return String the resolved value, or null if not found
     */
    protected String resolvePlaceholder(String placeholder, Properties properties) {

        Properties p2 = CarlosProperties.getInstance();
        MiscUtils.getLogger().debug("oscarproperties=" + p2.toString());
        if (p2 != null && placeholder.startsWith("oscar.")) {
            String value = p2.getProperty(placeholder.substring(6));
            MiscUtils.getLogger().debug("resolveplaceholder2:" + placeholder.substring(6) + "=" + value);
            if (value != null) {
                return value;
            }
        }
        return super.resolvePlaceholder(placeholder, properties);
    }


}
