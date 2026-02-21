/**
 * Copyright (c) 2024-2026. CARLOS EMR. All rights reserved.
 *
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.utils.ConfigUtils;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilderConfig;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit tests for EdtClientBuilder configuration.
 * Tests keystore configuration isolation and the setExternalClientKeystoreFilename helper method.
 *
 * These tests verify the builder's behavior in isolation and do not require
 * actual EDT service connectivity.
 *
 * @since 2026-01-29
 */
public class EdtClientBuilderTest {

    private static final Logger logger = MiscUtils.getLogger();

    @BeforeClass
    public static void setUpBeforeClass() {
        // Check if the Spring context (bean factory) has been initialized yet
        // Set up the context if it's null
        if (SpringUtils.getBeanFactory() == null) {
            CarlosProperties p = CarlosProperties.getInstance();
            // Set the properties
            p.setProperty("db_name", ConfigUtils.getProperty("db_schema") + ConfigUtils.getProperty("db_schema_properties"));
            p.setProperty("db_username", ConfigUtils.getProperty("db_user"));
            p.setProperty("db_password", ConfigUtils.getProperty("db_password"));
            p.setProperty("db_uri", ConfigUtils.getProperty("db_url_prefix"));
            p.setProperty("db_driver", ConfigUtils.getProperty("db_driver"));
            // Load the Spring application context to initialize beans and other components
            // It loads the configurations defined in the specified XML files
            ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext();
            // Set the configuration files for the application context
            context.setConfigLocations(new String[]{"/applicationContext.xml"});
            context.refresh();
            // Set the application context in the Spring utility class, so it can be accessed throughout the application
            SpringUtils.setBeanFactory(context);
        }
    }

    /**
     * Set an external client keystore properties file for the EDT client builder in test context.
     * This method configures a custom keystore properties file path for MCEDT service
     * client certificate authentication during testing. If the provided path is null or the
     * file does not exist, the default keystore at src/main/resources/clientKeystore.properties
     * will be used.
     *
     * @param builder EdtClientBuilder the EDT client builder instance to configure
     * @param clientKeystorePropertiesPath String the absolute path to the client keystore properties file, or null to use default
     */
    protected static void setExternalClientKeystoreFilename(EdtClientBuilder builder, String clientKeystorePropertiesPath) {
        if (clientKeystorePropertiesPath == null) {
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

    /**
     * Regression test to verify keystore configuration is isolated per EdtClientBuilder instance.
     * This test ensures that the race condition fix (making clientKeystore instance-based rather
     * than static) is maintained. If clientKeystore were ever made static again, this test would fail.
     */
    @Test
    public void clientKeystoreConfigurationIsIsolatedPerBuilderInstance() throws Exception {
        // Arrange: create two independent EDT configurations and builders
        CarlosProperties props = CarlosProperties.getInstance();
        EdtClientBuilderConfig config1 = new EdtClientBuilderConfig();
        config1.setMtomEnabled(true);
        config1.setServiceUrl(props.getProperty("mcedt.service.url"));
        config1.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config1.setServiceId(props.getProperty("mcedt.service.id"));

        EdtClientBuilderConfig config2 = new EdtClientBuilderConfig();
        config2.setMtomEnabled(true);
        config2.setServiceUrl(props.getProperty("mcedt.service.url"));
        config2.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config2.setServiceId(props.getProperty("mcedt.service.id"));

        EdtClientBuilder builder1 = new EdtClientBuilder(config1);
        EdtClientBuilder builder2 = new EdtClientBuilder(config2);

        // Act: configure different keystore paths on each builder
        // Using the default keystore location as a test value
        String keystorePath1 = "clientKeystore.properties";
        String keystorePath2 = "alternateKeystore.properties";

        builder1.setClientKeystoreFilename(keystorePath1);
        builder2.setClientKeystoreFilename(keystorePath2);

        // Assert: client keystore configuration is not shared between builders
        // Using reflection to access the private clientKeystore field
        java.lang.reflect.Field clientKeystoreField = EdtClientBuilder.class.getDeclaredField("clientKeystore");
        clientKeystoreField.setAccessible(true);

        Object clientKeystore1 = clientKeystoreField.get(builder1);
        Object clientKeystore2 = clientKeystoreField.get(builder2);

        assertNotNull("First builder should have client keystore configured", clientKeystore1);
        assertNotNull("Second builder should have client keystore configured", clientKeystore2);
        assertNotSame("Client keystore must be isolated per builder instance", clientKeystore1, clientKeystore2);
        assertEquals("First builder keystore should match configured value", keystorePath1, clientKeystore1);
        assertEquals("Second builder keystore should match configured value", keystorePath2, clientKeystore2);
    }

    /**
     * Test that setExternalClientKeystoreFilename does not modify the builder when path is null.
     * Verifies that null paths are treated as "use default keystore" per the method contract.
     */
    @Test
    public void setExternalClientKeystoreFilename_nullPath_usesDefaultKeystore() throws Exception {
        // Arrange
        CarlosProperties props = CarlosProperties.getInstance();
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setMtomEnabled(true);
        config.setServiceUrl(props.getProperty("mcedt.service.url"));
        config.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config.setServiceId(props.getProperty("mcedt.service.id"));

        EdtClientBuilder builder = new EdtClientBuilder(config);

        // Get the default keystore value before calling the method
        java.lang.reflect.Field clientKeystoreField = EdtClientBuilder.class.getDeclaredField("clientKeystore");
        clientKeystoreField.setAccessible(true);
        Object defaultKeystore = clientKeystoreField.get(builder);

        // Act: call with null path
        setExternalClientKeystoreFilename(builder, null);

        // Assert: keystore should remain unchanged (still the default)
        Object keystoreAfter = clientKeystoreField.get(builder);
        assertEquals("Keystore should remain at default when null path is provided", defaultKeystore, keystoreAfter);
    }

    /**
     * Test that setExternalClientKeystoreFilename does not modify the builder when path does not exist.
     * Verifies that non-existent paths are treated as "use default keystore" per the method contract.
     */
    @Test
    public void setExternalClientKeystoreFilename_nonExistentPath_usesDefaultKeystore() throws Exception {
        // Arrange
        CarlosProperties props = CarlosProperties.getInstance();
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setMtomEnabled(true);
        config.setServiceUrl(props.getProperty("mcedt.service.url"));
        config.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config.setServiceId(props.getProperty("mcedt.service.id"));

        EdtClientBuilder builder = new EdtClientBuilder(config);

        // Get the default keystore value before calling the method
        java.lang.reflect.Field clientKeystoreField = EdtClientBuilder.class.getDeclaredField("clientKeystore");
        clientKeystoreField.setAccessible(true);
        Object defaultKeystore = clientKeystoreField.get(builder);

        // Use a path that does not exist
        Path nonExistentPath = Paths.get("target", "does-not-exist", "clientKeystore.properties");
        assertFalse("Test precondition failed: non-existent path unexpectedly exists", Files.exists(nonExistentPath));

        // Act: call with non-existent path
        setExternalClientKeystoreFilename(builder, nonExistentPath.toAbsolutePath().toString());

        // Assert: keystore should remain unchanged (still the default)
        Object keystoreAfter = clientKeystoreField.get(builder);
        assertEquals("Keystore should remain at default when non-existent path is provided", defaultKeystore, keystoreAfter);
    }
}
