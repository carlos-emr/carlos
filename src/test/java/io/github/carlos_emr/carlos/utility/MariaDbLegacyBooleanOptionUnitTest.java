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

import java.lang.reflect.Field;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.util.options.OptionAliases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue #5969: MySQL Connector/J accepted {@code yes}/{@code no} (any case) for every Boolean
 * property, while MariaDB Connector/J accepts only {@code true}, {@code false}, {@code 1},
 * {@code 0} or an empty value. Legacy {@code jdbc:mysql:} configuration must keep connecting;
 * native {@code jdbc:mariadb:} configuration keeps MariaDB's own tokens.
 *
 * <p>MariaDB's own {@link Configuration#parse} is the oracle: it is the parser that refused these
 * values and that the pool, Flyway runner, Query by Example and CDS connections all reach.
 */
@Tag("unit")
@DisplayName("Legacy yes/no values for MariaDB Boolean options (#5969)")
class MariaDbLegacyBooleanOptionUnitTest {
    private static final String LEGACY = "jdbc:mysql://db/test";
    private static final String NATIVE = "jdbc:mariadb://db/test";

    /** Every option name MariaDB maps onto a Boolean field, including its alias spellings. */
    private static Set<String> nativeBooleanOptions() {
        Set<String> names = new TreeSet<>();
        for (Field field : Configuration.Builder.class.getDeclaredFields()) {
            if (field.getType() == Boolean.class) names.add(field.getName());
        }
        OptionAliases.OPTIONS_ALIASES.forEach((alias, target) -> {
            if (names.contains(target)) names.add(alias);
        });
        return names;
    }

    /** Three places a legacy option can come from: URL query, connection properties, host descriptor. */
    private static Stream<Arguments> everyBooleanOptionFromEverySource() {
        return nativeBooleanOptions().stream().flatMap(option -> Stream.of(
                Arguments.of(option, "query"), Arguments.of(option, "property"), Arguments.of(option, "descriptor")));
    }

    private static MariaDbJdbcCompatibility.Settings legacySettings(String option, String value, String source)
            throws Exception {
        Properties supplied = new Properties();
        String url = switch (source) {
            case "query" -> LEGACY + "?" + option + "=" + value;
            case "property" -> {
                supplied.setProperty(option, value);
                yield LEGACY;
            }
            case "descriptor" -> "jdbc:mysql://address=(host=db)(" + option + "=" + value + ")/test";
            default -> throw new IllegalArgumentException(source);
        };
        return MariaDbJdbcCompatibility.settings(url, supplied);
    }

    private static boolean nativeValue(Configuration configuration, String accessor) throws Exception {
        return (Boolean) Configuration.class.getMethod(accessor).invoke(configuration);
    }

    @Test
    @DisplayName("should discover the reported options from the shipped driver")
    void shouldDiscoverReportedOptions_fromShippedDriver() {
        // Guards the reflective discovery: a driver whose layout hides these names would silently
        // bring the #5969 failure back, so the build fails instead.
        assertThat(nativeBooleanOptions()).hasSizeGreaterThan(40).contains("allowPublicKeyRetrieval",
                "useServerPrepStmts", "cachePrepStmts", "useCompression", "allowMultiQueries", "useAffectedRows",
                "tcpKeepAlive", "useLocalSessionState", "yearIsDateType", "createDatabaseIfNotExist",
                "nullcatalogmeanscurrent");
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"allowPublicKeyRetrieval,yes,true", "useServerPrepStmts,YES,true", "cachePrepStmts,No,false",
            "useCompression,yes,true", "allowMultiQueries,Yes,true", "useAffectedRows,NO,false",
            "tcpKeepAlive,yES,true", "useLocalSessionState,no,false", "yearIsDateType,no,false",
            "createDatabaseIfNotExist,yes,true"})
    @DisplayName("should connect with the reported options spelled yes or no")
    void shouldApplyConnectorJYesNo_forReportedOptions(String option, String value, String expected) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(LEGACY + "?" + option + "=" + value, new Properties());

        assertThat(settings.properties()).containsEntry(option, expected);
        var configuration = Configuration.parse(settings.url(), settings.properties());
        assertThat(nativeValue(configuration, option)).isEqualTo(Boolean.parseBoolean(expected));
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("everyBooleanOptionFromEverySource")
    @DisplayName("should treat yes and no as true and false for every MariaDB Boolean option")
    void shouldMatchTrueAndFalse_forEveryNativeBooleanOption(String option, String source) throws Exception {
        for (String[] pair : new String[][]{{"yes", "true"}, {"YES", "true"}, {"no", "false"}, {"No", "false"}}) {
            var spelled = legacySettings(option, pair[0], source);
            var canonical = legacySettings(option, pair[1], source);

            assertThat(spelled.url()).isEqualTo(canonical.url());
            assertThat(spelled.properties()).isEqualTo(canonical.properties());
            // MariaDB refused the whole connection here before #5969.
            Configuration.parse(spelled.url(), (Properties) spelled.properties().clone());
        }
    }

    @Test
    @DisplayName("should match option names without regard to case, as MariaDB does")
    void shouldTranslateValues_forCaseVariantOptionNames() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                LEGACY + "?ALLOWMULTIQUERIES=Yes&allowpublickeyretrieval=YES&TcpKeepAlive=no", new Properties());

        assertThat(settings.properties()).containsEntry("ALLOWMULTIQUERIES", "true")
                .containsEntry("allowpublickeyretrieval", "true").containsEntry("TcpKeepAlive", "false");
        var configuration = Configuration.parse(settings.url(), settings.properties());
        assertThat(configuration.allowMultiQueries()).isTrue();
        assertThat(configuration.allowPublicKeyRetrieval()).isTrue();
        assertThat(configuration.tcpKeepAlive()).isFalse();
    }

    @Test
    @DisplayName("should translate Connector/J names that MariaDB reads through an alias")
    void shouldTranslateValues_forMariaDbAliasNames() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(LEGACY + "?nullCatalogMeansCurrent=yes", new Properties());

        assertThat(settings.properties()).containsEntry("nullCatalogMeansCurrent", "true");
        assertThat(Configuration.parse(settings.url(), settings.properties()).nullDatabaseMeansCurrent()).isTrue();
    }

    @Test
    @DisplayName("should store yes and no as true and false when a legacy URL is normalized")
    void shouldPersistTranslatedValues_whenLegacyUrlIsNormalized() throws Exception {
        String normalized = MariaDbJdbcCompatibility.normalizeUrl(LEGACY + "?allowMultiQueries=yes&tcpKeepAlive=NO");

        assertThat(normalized).contains("allowMultiQueries=true").contains("tcpKeepAlive=false")
                .contains("carlosLegacyMysqlScheme=true").doesNotContain("=yes").doesNotContain("=NO");
        var settings = MariaDbJdbcCompatibility.settings(normalized, new Properties());
        var configuration = Configuration.parse(settings.url(), settings.properties());
        assertThat(configuration.allowMultiQueries()).isTrue();
        assertThat(configuration.tcpKeepAlive()).isFalse();
        assertThat(MariaDbJdbcCompatibility.normalizeUrl(normalized)).isEqualTo(normalized);
    }

    @Test
    @DisplayName("should accept yes through the compatibility driver entry point")
    void shouldAcceptYesNo_throughCompatibilityDriverPropertyInfo() throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty("useServerPrepStmts", "No");

        DriverPropertyInfo[] info = new MariaDbCompatibilityDriver()
                .getPropertyInfo(LEGACY + "?allowPublicKeyRetrieval=yes", supplied);

        assertThat(Arrays.stream(info).filter(property -> property.name.equals("allowPublicKeyRetrieval")))
                .singleElement().extracting(property -> property.value).isEqualTo("true");
        assertThat(Arrays.stream(info).filter(property -> property.name.equals("useServerPrepStmts")))
                .singleElement().extracting(property -> property.value).isEqualTo("false");
    }

    @Test
    @DisplayName("should keep native MariaDB URLs on MariaDB's own Boolean tokens")
    void shouldLeaveNativeUrlValuesUnchanged_forYesNo() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(NATIVE + "?allowPublicKeyRetrieval=yes", new Properties());

        assertThat(settings.properties()).containsEntry("allowPublicKeyRetrieval", "yes");
        assertThatThrownBy(() -> Configuration.parse(settings.url(), settings.properties()))
                .isInstanceOf(SQLException.class).hasMessageContaining("allowPublicKeyRetrieval must be boolean");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "", "TRUE", "False"})
    @DisplayName("should pass MariaDB's own Boolean tokens through unchanged")
    void shouldLeaveNativeBooleanTokensUnchanged_forLegacyUrls(String value) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(LEGACY + "?useCompression=" + value, new Properties());

        assertThat(settings.properties()).containsEntry("useCompression", value);
        Configuration.parse(settings.url(), settings.properties());
    }

    @Test
    @DisplayName("should leave other invalid values for MariaDB to refuse by option name")
    void shouldLeaveUnrecognizedValuesToMariaDb_forLegacyBooleanOptions() throws Exception {
        // Connector/J refused these too; MariaDB's refusal names the option, unlike a silent change.
        var settings = MariaDbJdbcCompatibility.settings(LEGACY + "?useCompression=maybe", new Properties());

        assertThat(settings.properties()).containsEntry("useCompression", "maybe");
        assertThatThrownBy(() -> Configuration.parse(settings.url(), settings.properties()))
                .isInstanceOf(SQLException.class).hasMessageContaining("useCompression must be boolean");
    }

    @Test
    @DisplayName("should not rewrite yes or no in options that are not Boolean")
    void shouldKeepLiteralYesNo_forNonBooleanAndUnknownOptions() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                LEGACY + "?servicePrincipalName=no&connectionAttributes=yes&autoReconnect=yes", new Properties());

        assertThat(settings.properties()).containsEntry("servicePrincipalName", "no")
                .containsEntry("connectionAttributes", "yes").containsEntry("autoReconnect", "yes");
        var configuration = Configuration.parse(settings.url(), settings.properties());
        assertThat(configuration.servicePrincipalName()).isEqualTo("no");
    }

    @Test
    @DisplayName("should never rewrite credentials that read yes or no")
    void shouldKeepCredentialsVerbatim_whenTheyReadYesOrNo() throws Exception {
        Properties supplied = new Properties();
        supplied.setProperty("password", "No"); // ggignore - synthetic parsing fixture, never a deployed credential

        var fromProperties = MariaDbJdbcCompatibility.settings(LEGACY + "?user=yes", supplied);
        var fromHost = MariaDbJdbcCompatibility.settings(
                "jdbc:mysql://no:YES@db/test", new Properties()); // ggignore - synthetic parsing fixture, never a deployed credential

        assertThat(fromProperties.properties()).containsEntry("user", "yes")
                .containsEntry("password", "No"); // ggignore - synthetic parsing fixture, never a deployed credential
        assertThat(fromHost.properties()).containsEntry("user", "no")
                .containsEntry("password", "YES"); // ggignore - synthetic parsing fixture, never a deployed credential
    }
}
