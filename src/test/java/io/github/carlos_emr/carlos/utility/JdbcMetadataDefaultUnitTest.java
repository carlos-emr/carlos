/*
 * Copyright (C) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.utility;

import com.mysql.cj.conf.ConnectionUrl;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests URL defaults and the real Connector/J property parser without opening a connection. */
@Tag("unit")
class JdbcMetadataDefaultUnitTest extends CarlosUnitTestBase {
    private static final String URL = "jdbc:mysql://127.0.0.1/fixture";

    @Test
    void absentDefaultRetainsOrdinaryWarBehavior() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setUrl(URL);
            assertThat(source.getUrl()).startsWith(URL + "?serverTimezone=").doesNotContain("useInformationSchema");
        }
    }

    @Test
    void eitherSetterOrderAppliesTheSameDefault() throws Exception {
        try (OscarTrackingBasicDataSource before = new OscarTrackingBasicDataSource();
             OscarTrackingBasicDataSource after = new OscarTrackingBasicDataSource()) {
            before.setDefaultUseInformationSchema(false);before.setUrl(URL);
            after.setUrl(URL);after.setDefaultUseInformationSchema(false);
            assertThat(before.getUrl()).isEqualTo(after.getUrl()).contains("?useInformationSchema=false&serverTimezone=");
        }
    }

    @Test
    void existingDecodedUrlOptionAlwaysWins() throws Exception {
        for (String query : new String[]{"useInformationSchema=true", "useInformationSchema=false",
                "%75seInformationSchema=%74rue", "USEINFORMATIONSCHEMA=false"}) {
            for (boolean defaultValue : new boolean[]{false, true}) {
                try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
                    source.setUrl(URL + "?" + query);source.setDefaultUseInformationSchema(defaultValue);
                    assertThat(source.getUrl()).startsWith(URL + "?" + query + "&serverTimezone=");
                    assertThat(source.getUrl().split("&")).hasSize(2);
                }
            }
        }
    }

    @Test
    void clearingOrReplacingDefaultRecomputesFromOriginalUrl() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setUrl(URL + "?serverTimezone=America/Toronto&connectTimeout=1234");
            String original = source.getUrl();
            source.setDefaultUseInformationSchema(false);
            assertThat(source.getUrl()).isEqualTo(original + "&useInformationSchema=false");
            source.setDefaultUseInformationSchema(true);
            assertThat(source.getUrl()).isEqualTo(original + "&useInformationSchema=true");
            source.setDefaultUseInformationSchema(null);
            assertThat(source.getUrl()).isEqualTo(original);
        }
    }

    @Test
    void repeatedUrlDoesNotAccumulateDefaultsAndNewExplicitUrlWins() throws Exception {
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
    void explicitDriverPropertiesRetainNormalConnectorPrecedence() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setConnectionProperties("useInformationSchema=true;connectTimeout=1234");
            source.setDefaultUseInformationSchema(false);source.setUrl(URL);
            Properties properties = (Properties) ReflectionTestUtils.getField(source, "connectionProperties");
            Properties effective = ConnectionUrl.getConnectionUrlInstance(source.getUrl(), properties).getConnectionArgumentsAsProperties();
            assertThat(source.getUrl()).contains("useInformationSchema=false");
            assertThat(effective).containsEntry("useInformationSchema", "true").containsEntry("connectTimeout", "1234");
        }
    }

    @Test
    void optionTextInsideAnotherValueIsNotAnExplicitSetting() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setDefaultUseInformationSchema(false);
            source.setUrl(URL + "?label=useInformationSchema%3Dtrue");
            assertThat(source.getUrl()).contains("&useInformationSchema=false&serverTimezone=");
        }
    }

    @Test
    void malformedEncodedOptionFailsInsteadOfSilentlyOverridingIt() throws Exception {
        try (OscarTrackingBasicDataSource source = new OscarTrackingBasicDataSource()) {
            source.setDefaultUseInformationSchema(false);
            assertThatThrownBy(() -> source.setUrl(URL + "?%not-encoded=true")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
