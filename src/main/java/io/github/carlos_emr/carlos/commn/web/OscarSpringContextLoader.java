/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
package io.github.carlos_emr.carlos.commn.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletContext;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextException;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import io.github.carlos_emr.CarlosProperties;
/**
 * Servlet context listener that creates the Spring web application context.
 *
 * <p>This loader owns the list of Spring XML files the root context is built from. It refreshes
 * the context itself, so Spring's {@code ContextLoader} never applies the
 * {@code contextConfigLocation} parameter in {@code web.xml}: that value is not read. The list is
 * {@link #CORE_MODULES} followed by every module named in the {@code ModuleNames} property
 * ({@code applicationContext<Name>.xml}).
 */

public final class OscarSpringContextLoader extends ContextLoaderListener {

    private static final Logger log = MiscUtils.getLogger();
    private static final String CONTEXTNAME = "classpath:applicationContext";
    private static final String PROPERTYNAME = "ModuleNames";

    /**
     * Module names loaded on every deployment, in this order, before anything {@code ModuleNames}
     * lists. The empty name is {@code applicationContext.xml} itself.
     *
     * <p>{@code REST} ({@code applicationContextREST.xml}) publishes the OAuth 1.0a handshake at
     * {@code /ws/oauth} and the OAuth-guarded data API at {@code /ws/services}. It used to load only
     * when an operator listed {@code REST} in {@code ModuleNames}. No shipped configuration does, so
     * both CXF servers were never created and answered 404 (issue #3446). Loading it always is safe
     * because both surfaces fail closed. A handshake needs a client an administrator registered, and
     * every {@code /ws/services} call must carry a valid signed OAuth access token.
     */
    static final List<String> CORE_MODULES = List.of("", "REST");

    @Override
    protected WebApplicationContext createWebApplicationContext(ServletContext servletContext) {
        String contextClassName = servletContext.getInitParameter(CONTEXT_CLASS_PARAM);
        log.info("Creating Spring context");
        Class<?> contextClass;
        if (contextClassName != null) {
            try {
                // nosemgrep: unsafe-reflection -- contextClassName is read from the
                // server-side web.xml <context-param> CONTEXT_CLASS_PARAM, not from
                // user input. It is controlled by the deployment descriptor.
                contextClass = Class.forName(contextClassName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new ApplicationContextException("Failed to load context class [" + contextClassName + "]", ex);
            }

            if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
                throw new ApplicationContextException("Custom context class [" + contextClassName + "] is not of type ConfigurableWebApplicationContext");
            }
        } else {
            contextClass = XmlWebApplicationContext.class;
        }

        ConfigurableWebApplicationContext wac = (ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);
        // wac.setParent(parent);
        wac.setServletContext(servletContext);

        List<String> configLocations =
                resolveConfigLocations((String) CarlosProperties.getInstance().get(PROPERTYNAME));

        for (String s : configLocations) {
            log.info("Preparing {}", s);
        }

        wac.setConfigLocations(configLocations.toArray(new String[0]));
        wac.refresh();

        if (SpringUtils.getBeanFactory() == null) {
            SpringUtils.setBeanFactory(wac);
        }

        return wac;
    }

    /**
     * Resolves the Spring XML locations the root context is built from.
     *
     * <p>Returns {@link #CORE_MODULES} first, then each module named in {@code moduleNames} in the
     * order given. Names are trimmed, blank names are skipped, and a name already present is not
     * added again. Configurations written before issue #3446 may still list {@code REST}, and
     * reading that file a second time would re-register every bean it defines.
     *
     * @param moduleNames the raw comma-separated {@code ModuleNames} property value; may be null
     * @return the {@code classpath:} locations to load, without duplicates
     */
    static List<String> resolveConfigLocations(String moduleNames) {
        Set<String> locations = new LinkedHashSet<>();
        for (String module : CORE_MODULES) {
            locations.add(CONTEXTNAME + module + ".xml");
        }
        if (moduleNames != null) {
            for (String module : moduleNames.split(",")) {
                String name = module.trim();
                if (!name.isEmpty()) {
                    locations.add(CONTEXTNAME + name + ".xml");
                }
            }
        }
        return List.copyOf(locations);
    }
}
