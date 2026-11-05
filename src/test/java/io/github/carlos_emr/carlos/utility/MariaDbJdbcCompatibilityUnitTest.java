/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.utility;

import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class MariaDbJdbcCompatibilityUnitTest {
    @ParameterizedTest
    @CsvSource({"DISABLED,disable", "PREFERRED,allow", "REQUIRED,trust", "VERIFY_CA,verify-ca", "VERIFY_IDENTITY,verify-full"})
    void shouldPreserveTlsPolicy_withExplicitLegacyMode(String legacy, String expected) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?sslMode=" + legacy, new Properties());
        assertThat(settings.properties().getProperty("sslMode")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"useSSL=false,disable", "useSSL=true,allow", "requireSSL=true,trust",
            "verifyServerCertificate=true,verify-ca", "useSSL=true&requireSSL=true,trust",
            "useSSL=false&requireSSL=true,disable", "useSSL=true&verifyServerCertificate=true,verify-ca",
            "sslMode=VERIFY_IDENTITY&useSSL=false,verify-full"})
    void shouldPreserveTlsRequirements_withLegacyFlags(String options, String expected) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?" + options, new Properties());
        assertThat(settings.properties()).containsEntry("sslMode", expected)
                .doesNotContainKeys("useSsl", "requireSSL", "verifyServerCertificate");
    }

    @Test
    void shouldPreserveDecodedPropertyPrecedence_withEncodedUrlOptions() throws Exception {
        String url = "jdbc:mysql:///test?user=query&password=a%26b%2Bc%2525&server%54imezone=America%2FToronto&CONNECTtimeout=99"; // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
        Properties properties = new Properties();
        properties.setProperty("user", "configured");
        properties.setProperty("connectTimeout", "101");
        var parsed = MariaDbJdbcCompatibility.settings(MariaDbJdbcCompatibility.normalizeUrl(url), properties);
        assertThat(parsed.url()).isEqualTo("jdbc:mariadb://localhost/test");
        assertThat(parsed.properties()).containsEntry("user", "configured")
                .containsEntry("password", "a&b+c%25").containsEntry("connectTimeout", "101") // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
                .containsEntry("connectionTimeZone", "America/Toronto");
        assertThat(properties).hasSize(2);
        assertThat(MariaDbJdbcCompatibility.normalizeUrl(MariaDbJdbcCompatibility.normalizeUrl(url)))
                .isEqualTo(MariaDbJdbcCompatibility.normalizeUrl(url));
    }

    @Test
    void shouldKeepEncodedTimezone_whenDataSourceAddsDefaults() {
        try (var source = new OscarTrackingBasicDataSource()) {
            source.setUrl("jdbc:mysql:///test?server%54imezone=Pacific%2FAuckland");
            assertThat(source.getUrl()).contains("connectionTimeZone=Pacific%2FAuckland");
        } catch (SQLException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void shouldRejectUnsupportedConfiguration_withoutEchoingCredentials() {
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(
                "jdbc:mysql:///test?characterEncoding=latin1&password=do-not-log", new Properties())) // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration").hasNoCause();
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(
                "jdbc:mysql:///test?password=%invalid", new Properties())) // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration").hasNoCause();
    }

    @Test
    void shouldPreserveFailoverAndFilePolicy_withLegacyOptions() throws Exception {
        var parsed = MariaDbJdbcCompatibility.settings("jdbc:mysql://first:3306,second:3307/test", new Properties());
        assertThat(parsed.url()).isEqualTo("jdbc:mariadb:sequential://first:3306,second:3307/test");
        var nativeConfig = org.mariadb.jdbc.Configuration.parse(parsed.url(), parsed.properties());
        assertThat(nativeConfig.haMode()).isEqualTo(org.mariadb.jdbc.export.HaMode.SEQUENTIAL);
        assertThat(nativeConfig.allowLocalInfile()).isFalse();
        assertThat(nativeConfig.permitRedirect()).isFalse();
        var enabled = MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?allowLoadLocalInfile=1", new Properties());
        assertThat(org.mariadb.jdbc.Configuration.parse(enabled.url(), enabled.properties()).allowLocalInfile()).isTrue();
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://db/test?allowLoadLocalInfileInPath=/restricted", new Properties())).isInstanceOf(SQLException.class);
    }

    @Test
    void shouldAcceptLegacySpellings_forBooleansAndZeroDates() throws Exception {
        var parsed = MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?useOldAliasMetadataBehavior=1&zeroDateTimeBehavior=convertToNull", new Properties());
        assertThat(parsed.properties()).containsEntry("useOldAliasMetadataBehavior", "true")
                .containsEntry("zeroDateTimeBehavior", "CONVERT_TO_NULL");
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://db/test?allowLoadLocalInfile=invalid", new Properties())).isInstanceOf(SQLException.class);
    }

    @Test
    void shouldApplySuppliedTlsFlags_afterUrlNormalization() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("requireSSL", "true");
        var parsed = MariaDbJdbcCompatibility.settings(
                MariaDbJdbcCompatibility.normalizeUrl("jdbc:mysql://db/test?useSSL=true"), properties);
        assertThat(parsed.properties()).containsEntry("sslMode", "trust");
    }

    @Test
    void shouldPreserveHostCredentialPrecedence_withoutIgnoringUnsupportedPolicies() throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty("user", "global");
        var parsed = MariaDbJdbcCompatibility.settings(MariaDbJdbcCompatibility.normalizeUrl(
                "jdbc:mysql://address=(host=db)(port=3306)(user=host-user)(password=a%26b)/test?user=query"), supplied); // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
        assertThat(parsed.url()).isEqualTo("jdbc:mariadb://address=(host=db)(port=3306)/test");
        assertThat(parsed.properties()).containsEntry("user", "host-user").containsEntry("password", "a&b"); // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://address=(host=first)(user=one),address=(host=second)(user=two)/test", new Properties()))
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration");
    }

    @Test
    void shouldDistinguishLegacyPreferredTls_fromExplicitVerifiedModes() throws Exception {
        var preferred = MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?sslMode=PREFERRED", new Properties());
        assertThat(preferred.properties()).containsEntry("sslMode", "allow")
                .containsEntry("tlsSocketType", "carlos-preferred").containsEntry("carlosLegacyPreferredTls", "true");
        for (String mode : new String[]{"ALLOW", "VERIFY_CA", "VERIFY_IDENTITY"}) {
            var verified = MariaDbJdbcCompatibility.settings("jdbc:mariadb://db/test?sslMode=" + mode, new Properties());
            assertThat(verified.properties()).containsEntry("tlsSocketType", "carlos-preferred")
                    .doesNotContainKey("carlosLegacyPreferredTls");
        }
        var hostPreferred = MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://address=(host=db)(sslMode=PREFERRED)/test?sslMode=VERIFY_IDENTITY", new Properties());
        assertThat(hostPreferred.properties()).containsEntry("sslMode", "allow")
                .containsEntry("carlosLegacyPreferredTls", "true");
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?dnsSrv=true", new Properties()))
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration");
    }

    @ParameterizedTest
    @CsvSource({"DISABLED,DISABLE", "REQUIRED,TRUST", "VERIFY_CA,VERIFY_CA", "VERIFY_IDENTITY,VERIFY_FULL", "ALLOW,ALLOW"})
    void shouldPreserveIndependentTlsPolicies_withMultipleHostDescriptors(String legacy, String expected) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://address=(host=first)(sslMode=" + legacy + "),address=(host=second)(sslMode=VERIFY_CA)/test",
                new Properties());
        var nativeConfig = org.mariadb.jdbc.Configuration.parse(settings.url(), settings.properties());
        assertThat(nativeConfig.addresses()).hasSize(2);
        assertThat(nativeConfig.addresses().get(0).sslMode.name()).isEqualTo(expected);
        assertThat(nativeConfig.addresses().get(1).sslMode).isEqualTo(org.mariadb.jdbc.export.SslMode.VERIFY_CA);
        assertThat(nativeConfig.sslMode()).isEqualTo(org.mariadb.jdbc.export.SslMode.ALLOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "address=(host=first)junk(port=3306),address=(host=second)",
            "address=(host=first),address=(host=second)junk",
            "address=(host=first),junkaddress=(host=second)",
            "first,address=(host=second)(user=ignored)",
            "address=(host=first),address=(host=second",
            "address=(host=first)),address=(host=second)",
            "address=(host=first),,address=(host=second)",
            "address=(host=first),address=(host=second)(sslMode=PREFERRED)"})
    void shouldRejectMalformedOrUnrepresentablePolicies_withMultipleHosts(String authority) {
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings("jdbc:mysql://" + authority + "/test", new Properties()))
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration");
    }

    @Test
    void shouldRetainMixedHostsAndIpv6_withHostSpecificTlsPolicy() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://[::1]:3306,address=(host=[::2])(protocol=tcp)(sslMode=VERIFY_IDENTITY)/test", new Properties());
        var configuration = org.mariadb.jdbc.Configuration.parse(settings.url(), settings.properties());
        assertThat(configuration.addresses()).hasSize(2);
        assertThat(configuration.addresses().get(0).host).isEqualTo("::1");
        assertThat(configuration.addresses().get(1).host).isEqualTo("::2");
        assertThat(configuration.addresses().get(1).sslMode).isEqualTo(org.mariadb.jdbc.export.SslMode.VERIFY_FULL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"latin1_swedish_ci", "utf8_general_ci", "utf16_general_ci", "utf8mb4_general_ci;secret"})
    void shouldRejectUnsupportedCollation_withoutEchoingOptionValues(String collation) {
        for (String key : new String[]{"connectionCollation", "CONNECTIONCOLLATION"}) {
            Properties properties = new Properties();
            properties.setProperty(key, collation);
            assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test", properties))
                    .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration");
            assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?" + key + "=" + collation, new Properties()))
                    .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration");
        }
    }

    @Test
    void shouldPreserveUnicodeEncoding_withLegacyNoOpFlagAndSupportedCollation() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://db/test?useUnicode=false&CONNECTIONCOLLATION=UTF8MB4_GENERAL_CI", new Properties());
        var configuration = org.mariadb.jdbc.Configuration.parse(settings.url(), settings.properties());
        assertThat(configuration.connectionCollation()).isEqualTo("utf8mb4_general_ci");
        Properties clear = new Properties();
        clear.setProperty("connectionCollation", "");
        assertThat(MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?connectionCollation=utf8mb4_bin", clear).properties())
                .doesNotContainKey("connectionCollation");
    }

    @Test
    void shouldMatchAsciiProtocolTokens_withoutChangingUnicodeCredentials() throws Exception {
        assertThat(MariaDbJdbcCompatibility.asciiEquals("VERIFY_IDENTITY", "verify_identity")).isTrue();
        assertThat(MariaDbJdbcCompatibility.asciiEquals("falſe", "false")).isFalse();
        assertThat(MariaDbJdbcCompatibility.asciiEquals("DİSABLED", "disabled")).isFalse();
        assertThat(MariaDbJdbcCompatibility.asciiEquals(null, "disabled")).isFalse();
        assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test?sslMode=DİSABLED", new Properties()))
                .isInstanceOf(SQLException.class).hasMessage("Invalid database connection configuration");
        Properties supplied = new Properties();
        supplied.setProperty("user", "usér");
        supplied.setProperty("password", "pāss🔒"); // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
        assertThat(MariaDbJdbcCompatibility.settings("jdbc:mysql://db/test", supplied).properties())
                .containsEntry("user", "usér").containsEntry("password", "pāss🔒"); // ggignore - synthetic JDBC parsing/trust-store fixture, never a deployed credential
    }

}
