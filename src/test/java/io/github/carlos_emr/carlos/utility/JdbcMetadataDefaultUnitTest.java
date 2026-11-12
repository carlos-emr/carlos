/*
 * Copyright (C) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.utility;

import org.mariadb.jdbc.Configuration;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests legacy configuration retention and MariaDB option precedence without opening a connection. */
@Tag("unit")
class JdbcMetadataDefaultUnitTest extends CarlosUnitTestBase {
    private static final String URL = "jdbc:mysql://127.0.0.1/fixture";

    @Test
    void shouldRetainOrdinaryWarBehavior_withoutMetadataDefault() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setUrl(URL);
            assertThat(source.getUrl()).startsWith("jdbc:mariadb://127.0.0.1/fixture?");
            assertThat(options(source)).containsKey("connectiontimezone").doesNotContainKey("useinformationschema");
        }
    }

    @Test
    void shouldApplyTheSameMetadataDefault_regardlessOfSetterOrder() throws Exception {
        try (OscarTrackingBasicDataSource before = new OscarTrackingBasicDataSource();
             OscarTrackingBasicDataSource after = new OscarTrackingBasicDataSource()) {
            before.setDefaultUseInformationSchema(false);before.setUrl(URL);
            after.setUrl(URL);after.setDefaultUseInformationSchema(false);
            assertThat(before.getUrl()).isEqualTo(after.getUrl());
            assertThat(options(before)).containsEntry("useinformationschema", "false");
        }
    }

    @Test
    void shouldRetainExplicitMetadataOption_overOperatorDefault() throws Exception {
        for (String query : new String[]{"useInformationSchema=true", "useInformationSchema=false",
                "%75seInformationSchema=%74rue", "USEINFORMATIONSCHEMA=false"}) {
            for (boolean defaultValue : new boolean[]{false, true}) {
                try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
                    source.setUrl(URL + "?" + query);source.setDefaultUseInformationSchema(defaultValue);
                    String expected = query.contains("true") || query.contains("%74rue") ? "true" : "false";
                    assertThat(options(source)).containsEntry("useinformationschema", expected)
                            .containsKey("connectiontimezone");
                }
            }
        }
    }

    @Test
    void shouldRecomputeFromOriginalUrl_whenMetadataDefaultChanges() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setUrl(URL + "?serverTimezone=America/Toronto&connectTimeout=1234");
            String original = source.getUrl();
            source.setDefaultUseInformationSchema(false);
            assertThat(options(source)).containsEntry("useinformationschema", "false")
                    .containsEntry("connectiontimezone", "America/Toronto").containsEntry("connecttimeout", "1234");
            source.setDefaultUseInformationSchema(true);
            assertThat(options(source)).containsEntry("useinformationschema", "true");
            source.setDefaultUseInformationSchema(null);
            assertThat(source.getUrl()).isEqualTo(original);
        }
    }

    @Test
    void shouldApplyDefaultsOnce_whenUrlIsRepeatedOrReplaced() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setDefaultUseInformationSchema(false);source.setUrl(URL);
            String first = source.getUrl();source.setUrl(URL);
            assertThat(source.getUrl()).isEqualTo(first);
            source.setUrl(URL + "?useInformationSchema=true");
            assertThat(source.getUrl()).contains("useInformationSchema=true").doesNotContain("useInformationSchema=false");
            source.setUrl(URL);
            assertThat(source.getUrl()).isEqualTo(first);
        }
    }

    @Test
    void shouldPreferExplicitDriverProperties_overUrlDefaults() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setConnectionProperties("useInformationSchema=true;connectTimeout=1234");
            source.setDefaultUseInformationSchema(false);source.setUrl(URL);
            Properties properties = (Properties) ReflectionTestUtils.getField(source, "connectionProperties");
            var settings = MariaDbJdbcCompatibility.settings(source.getUrl(), properties);
            var effective = Configuration.parse(settings.url(), settings.properties());
            assertThat(source.getUrl()).contains("useInformationSchema=false");
            // MariaDB always uses information_schema; retain this legacy option without
            // pretending it changes the connector's metadata implementation.
            assertThat(effective.nonMappedOptions()).containsEntry("useInformationSchema", "true");
            assertThat(effective.connectTimeout()).isEqualTo(1234);
        }
    }

    @Test
    void shouldIgnoreOptionText_insideAnotherValue() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setDefaultUseInformationSchema(false);
            source.setUrl(URL + "?label=useInformationSchema%3Dtrue");
            assertThat(options(source)).containsEntry("useinformationschema", "false")
                    .containsEntry("label", "useInformationSchema=true");
        }
    }

    @Test
    void shouldRejectMalformedEncoding_beforeApplyingDefaults() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setDefaultUseInformationSchema(false);
            assertThatThrownBy(() -> source.setUrl(URL + "?%not-encoded=true")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldSelectCompatibilityDriver_forLegacyAndNativeClassNames() throws Exception {
        for (String configured : new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "org.mariadb.jdbc.Driver"}) {
            try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
                source.setDriverClassName(configured);
                assertThat(source.getDriverClassName()).isEqualTo(MariaDbCompatibilityDriver.class.getName());
            }
        }
    }

    @Test
    void shouldPreferExplicitCredentials_overEncodedUrlCredentials() throws Exception {
        Properties credentials = new Properties();
        credentials.setProperty("user", "configured-user");
        credentials.setProperty("password", "configured-password");
        var settings = MariaDbJdbcCompatibility.settings(URL
                + "?user=url-user&password=O%27Brien%26tail&connectTimeout=300", credentials);
        var effective = Configuration.parse(settings.url(), settings.properties());
        assertThat(effective.user()).isEqualTo("configured-user");
        assertThat(effective.password()).isEqualTo("configured-password");
        assertThat(effective.connectTimeout()).isEqualTo(300);
    }

    private static java.util.Map<String, String> options(OscarTrackingBasicDataSource source) {
        var result = new java.util.HashMap<String, String>();
        for (String option : source.getUrl().substring(source.getUrl().indexOf('?') + 1).split("&")) {
            String[] pair = option.split("=", 2);
            String key = java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(java.util.Locale.ROOT);
            String value = java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
            assertThat(result.put(key, value)).as("duplicate URL option %s", key).isNull();
        }
        return result;
    }

}
