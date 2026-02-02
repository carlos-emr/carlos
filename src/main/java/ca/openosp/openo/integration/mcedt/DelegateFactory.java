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
 */
package ca.openosp.openo.integration.mcedt;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.integration.ebs.client.ng.EdtClientBuilder;
import ca.openosp.openo.integration.ebs.client.ng.EdtClientBuilderConfig;
import ca.openosp.openo.integration.mcedt.mailbox.ActionUtils;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.ontario.health.edt.EDTDelegate;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DelegateFactory {

    private static Logger logger = MiscUtils.getLogger();
    private static UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    private static Map<String, EDTDelegate> edtDelegates = new HashMap<>();

    public static EDTDelegate getEDTDelegateInstance(String serviceId) {
        if (edtDelegates.get(serviceId) == null) {
            edtDelegates.put(serviceId, newDelegate(serviceId));
        }
        return edtDelegates.get(serviceId);
    }

    public static EDTDelegate getEDTDelegateInstance() {
        OscarProperties props = OscarProperties.getInstance();
        return getEDTDelegateInstance(props.getProperty("mcedt.service.id"));
    }

    private static EDTDelegate newDelegate(String serviceId) {
        OscarProperties props = OscarProperties.getInstance();
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setLoggingRequired(!Boolean.valueOf(props.getProperty("mcedt.logging.skip")));
        config.setKeystoreUser(props.getProperty("mcedt.keystore.user"));
        config.setKeystorePassword(props.getProperty("mcedt.keystore.pass"));
        config.setUserNameTokenUser(props.getProperty("mcedt.service.user"));
        //config.setUserNameTokenPassword(props.getProperty("mcedt.service.pass"));
        UserProperty prop = userPropertyDAO.getProp(UserProperty.MCEDT_ACCOUNT_PASSWORD);
        config.setUserNameTokenPassword((prop == null || prop.getValue() == null || prop.getValue().trim().equals("")) ? props.getProperty("mcedt.service.pass") : prop.getValue());
        config.setServiceUrl(props.getProperty("mcedt.service.url"));
        config.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config.setServiceId((serviceId == null || serviceId.trim().equals("")) ? props.getProperty("mcedt.service.id") : serviceId);
        config.setMtomEnabled(true);
        EdtClientBuilder builder = new EdtClientBuilder(config);
        setExternalClientKeystoreFilename(builder, props.getProperty("mcedt.service.clientKeystore.properties"));
        EDTDelegate edtDelegate = builder.build(EDTDelegate.class);
        if (logger.isInfoEnabled()) {
            logger.info("Created new EDT delegate " + edtDelegate);
        }
        return edtDelegate;
    }

    /**
     * Refreshes created delegates in the event that information has been updated
     */
    public static void refreshDelegates() {
        // For each service id, create a new updated delegate
        for (String serviceId : ActionUtils.getServiceIds()) {
            edtDelegates.put(serviceId, newDelegate(serviceId));
        }
        // Suggests for the Garbage Collector to run in an effort to clean up any lingering connections
        System.gc();
    }

    public static UserPropertyDAO getUserPropertyDAO() {
        return userPropertyDAO;
    }

    public static void setUserPropertyDAO(UserPropertyDAO userPropertyDAO) {
        DelegateFactory.userPropertyDAO = userPropertyDAO;
    }

    /**
     * Set an external client keystore properties file for the EDT client builder.
     * This method configures a custom keystore properties file path for MCEDT service
     * client certificate authentication. If the provided path is null or the file does
     * not exist, the default keystore at src/main/resources/clientKeystore.properties
     * will be used.
     *
     * @param builder EdtClientBuilder the EDT client builder instance to configure
     * @param clientKeystorePropertiesPath String the absolute path to the client keystore properties file, or null to use default
     * @since 2026-01-29
     */
    private static void setExternalClientKeystoreFilename(EdtClientBuilder builder, String clientKeystorePropertiesPath) {
        if (clientKeystorePropertiesPath == null || clientKeystorePropertiesPath.trim().isEmpty()) {
            return;
        }
        Path signaturePropFile = Paths.get(clientKeystorePropertiesPath);
        if (Files.exists(signaturePropFile)) {
            File file = new File(clientKeystorePropertiesPath);
            try {
                builder.setClientKeystoreFilename(file.toURI().toURL().toString());
            } catch (MalformedURLException e) {
                logger.error("Malformed URL: " + clientKeystorePropertiesPath, e);
            }
        }
    }

}