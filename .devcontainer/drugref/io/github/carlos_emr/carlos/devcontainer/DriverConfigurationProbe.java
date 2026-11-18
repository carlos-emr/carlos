/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.devcontainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/**
 * Checks mounted settings against the datasource and drivers in the actual image WAR.
 * This offline CI probe neither opens a database connection nor prints credentials.
 *
 * @since 2026-11-17
 */
class DriverConfigurationProbe {
    /**
     * Loads the WAR's datasource, binds its configured driver and URL, and checks driver compatibility.
     * The shell caller extracts the image WAR and supplies {@code WEB-INF/classes:WEB-INF/lib/*}
     * as the Java classpath; datasource implementations and JDBC drivers must come from that WAR.
     *
     * @param args {@code String[]} with the mounted DrugRef properties-file path at position 0
     *             and the extracted WAR's Spring datasource XML path at position 1
     * @throws Exception if input reading, secure XML configuration/parsing, reflective datasource
     *                   or driver loading/binding, driver URL checking, or datasource closing fails
     * @throws IllegalStateException if the XML has missing/duplicate datasource settings,
     *                               a placeholder is unresolved, or the driver refuses the URL
     * @throws IndexOutOfBoundsException if either required argument is absent
     */
    public static void main(String[] args) throws Exception {
        Properties settings = new Properties();
        try (var stream = Files.newInputStream(Path.of(args[0]))) {
            settings.load(stream);
        }
        var xml = DocumentBuilderFactory.newInstance();
        xml.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        xml.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        xml.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var beans = xml.newDocumentBuilder().parse(args[1]).getElementsByTagName("bean");
        Element dataSourceBean = null;
        for (int index = 0; index < beans.getLength(); index++) {
            Element bean = (Element) beans.item(index);
            if ("dataSource".equals(bean.getAttribute("id"))) {
                if (dataSourceBean != null) throw new IllegalStateException("Duplicate datasource bean");
                dataSourceBean = bean;
            }
        }
        if (dataSourceBean == null) throw new IllegalStateException("No datasource bean in the image WAR");
        Class<?> type = Class.forName(dataSourceBean.getAttribute("class"));
        Object dataSource = type.getConstructor().newInstance();
        try {
            type.getMethod("setDriverClassName", String.class)
                    .invoke(dataSource, configuredValue(dataSourceBean, "driverClassName", settings));
            type.getMethod("setJdbcUrl", String.class)
                    .invoke(dataSource, configuredValue(dataSourceBean, "jdbcUrl", settings));
            String driverName = (String) type.getMethod("getDriverClassName").invoke(dataSource);
            String url = (String) type.getMethod("getJdbcUrl").invoke(dataSource);
            Driver driver = (Driver) Class.forName(driverName).getConstructor().newInstance();
            if (!driver.acceptsURL(url)) throw new IllegalStateException("The image driver refuses the configured JDBC URL");
            // Neither the connection URL nor credentials belong in CI output.
            System.out.println("PASS mounted DrugRef settings load an image driver that accepts the effective URL");
        } finally {
            if (dataSource instanceof AutoCloseable closeable) closeable.close();
        }
    }

    private static String configuredValue(Element bean, String name, Properties settings) {
        var properties = bean.getElementsByTagName("property");
        String value = null;
        for (int index = 0; index < properties.getLength(); index++) {
            Element property = (Element) properties.item(index);
            if (name.equals(property.getAttribute("name"))) {
                if (value != null) throw new IllegalStateException("Duplicate datasource property: " + name);
                value = property.getAttribute("value");
            }
        }
        if (value == null) throw new IllegalStateException("Missing datasource property: " + name);
        for (String key : settings.stringPropertyNames()) {
            value = value.replace("${" + key + "}", settings.getProperty(key));
        }
        if (value.isBlank() || value.contains("${")) {
            throw new IllegalStateException("Unresolved datasource property: " + name);
        }
        return value;
    }
}
