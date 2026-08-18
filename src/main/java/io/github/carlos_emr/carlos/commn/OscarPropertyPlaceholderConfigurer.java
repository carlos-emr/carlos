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

package io.github.carlos_emr.carlos.commn;

import java.io.IOException;

import io.github.carlos_emr.CarlosProperties;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

public class OscarPropertyPlaceholderConfigurer extends PropertySourcesPlaceholderConfigurer {
    private static Logger log = MiscUtils.getLogger();

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        log.debug("Initializing OscarPropertyPlaceholderConfigurer with CarlosProperties");

        MutablePropertySources propertySources = new MutablePropertySources();

        // CarlosProperties takes highest priority (matches old resolvePlaceholder behavior)
        propertySources.addLast(new PropertiesPropertySource(
                "carlosProperties", CarlosProperties.getInstance()));

        // Local properties from locations (carlos.properties on classpath)
        try {
            propertySources.addLast(new PropertiesPropertySource(
                    LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME, mergeProperties()));
        } catch (IOException ex) {
            throw new BeanInitializationException("Could not load properties", ex);
        }

        // setPropertySources() replaces the default Environment sources, so JVM -D system
        // properties and OS environment variables are intentionally excluded from ${...}
        // placeholder resolution. All Spring XML placeholders resolve from CarlosProperties
        // or carlos.properties only.
        setPropertySources(propertySources);
        super.postProcessBeanFactory(beanFactory);
    }
}
