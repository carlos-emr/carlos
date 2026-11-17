/*
 * Copyright (C) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.config;

import com.mysql.cj.conf.ConnectionUrl;
import com.mysql.cj.conf.DefaultPropertySet;
import com.mysql.cj.conf.PropertyKey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Resolve the exact package-owned Spring factories and real Connector/J URL parser, without connecting. */
@Tag("unit")
class DrugrefJdbcDefaultsWiringUnitTest {
    private static final String BASE = "jdbc:mysql://127.0.0.1/drugref2";
    private static final String DEFAULT_KEY = "db_use_information_schema";

    @Test
    void absentOperatorSettingSelectsFalse() {
        assertThat(resolve(BASE, Map.of())).containsOnlyKeys("useInformationSchema")
                .containsEntry("useInformationSchema", "false");
    }

    @Test
    void explicitOperatorSettingOverridesThePackageDefault() {
        assertThat(resolve(BASE, Map.of(DEFAULT_KEY, "true")))
                .containsOnlyKeys("useInformationSchema").containsEntry("useInformationSchema", "true");
    }

    @Test
    void validExistingUrlOptionWinsOverBothOperatorDefaults() {
        for (boolean explicit : new boolean[]{false, true}) {
            String configured = BASE + "?useInformationSchema=" + explicit + "&unused=tail";
            assertValidExistingUrl(configured, explicit);
            for (boolean fallback : new boolean[]{false, true}) {
                assertThat(resolve(configured, Map.of(DEFAULT_KEY, Boolean.toString(fallback))))
                        .containsOnlyKeys("useInformationSchema")
                        .containsEntry("useInformationSchema", Boolean.toString(explicit));
            }
        }
    }

    @Test
    void encodedExplicitUrlKeyAndValueAreParsedBeforeSelectingTheFallback() {
        String configured = BASE + "?%75seInformationSchema=%74rue&unused=tail";
        assertValidExistingUrl(configured, true);
        assertThat(resolve(configured, Map.of())).containsEntry("useInformationSchema", "true");
    }

    @Test
    void noOtherUrlOrCredentialPropertiesAreCopiedOrReprioritized() {
        String configured = BASE + "?user=url-user&password=url-password&connectTimeout=1234&unused=tail";
        Properties selected = resolve(configured, Map.of());
        assertThat(selected).containsOnlyKeys("useInformationSchema");
        Properties credentials = new Properties();
        credentials.setProperty("user", "configured-user");credentials.setProperty("password", "configured-password");
        Properties before = parsed(configured, credentials);
        Properties merged = new Properties();merged.putAll(credentials);merged.putAll(selected);
        Properties after = parsed(configured, merged);
        after.remove("useInformationSchema");before.remove("useInformationSchema");
        assertThat(after).isEqualTo(before);
    }

    @Test
    void quotedUrlValuesRemainDataAndAreNotInterpolatedIntoExpressionCode() {
        String configured = BASE + "?unused=O%27Brien&useInformationSchema=false&tail=ignored";
        assertValidExistingUrl(configured, false);
        assertThat(resolve(configured, Map.of(DEFAULT_KEY, "true")))
                .containsOnlyKeys("useInformationSchema").containsEntry("useInformationSchema", "false");
    }

    private static Properties parsed(String configured, Properties properties) {
        return ConnectionUrl.getConnectionUrlInstance(configured + "?serverTimezone=UTC", properties)
                .getConnectionArgumentsAsProperties();
    }

    private static void assertValidExistingUrl(String configured, boolean expected) {
        DefaultPropertySet settings = new DefaultPropertySet();
        settings.initializeProperties(parsed(configured, new Properties()));
        assertThat(settings.getBooleanProperty(PropertyKey.useInformationSchema).getValue()).isEqualTo(expected);
    }

    private Properties resolve(String configured, Map<String, String> operator) {
        Properties properties = new Properties();properties.setProperty("db_url", configured);properties.putAll(operator);
        // The packaged DrugRef configurer is the same stock Spring configurer
        // with setProperties(DrugrefProperties). Isolate the test host's environment.
        PropertySourcesPlaceholderConfigurer placeholders = new PropertySourcesPlaceholderConfigurer();
        placeholders.setProperties(properties);placeholders.setEnvironment(new MockEnvironment());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.addBeanFactoryPostProcessor(placeholders);
            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
            reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
            reader.setNamespaceAware(true);
            reader.loadBeanDefinitions(new org.springframework.core.io.FileSystemResource(
                    Path.of("debian/assets/drugref-jdbc-defaults.xml")));
            context.refresh();
            Properties result = context.getBean("carlosDebianMetadataProperties", Properties.class);
            Properties copy = new Properties();copy.putAll(result);
            return copy;
        }
    }
}
