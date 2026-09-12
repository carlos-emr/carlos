/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.fax.provider;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@DisplayName("Provider-aware fax destination validation")
class FaxDestinationUnitTest {
    @org.junit.jupiter.api.Test
    @DisplayName("should widen fax destinations without changing existing numbers or null values")
    void shouldPreserveStoredDestinations_whenApplyingInternationalMigration() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:h2:mem:fax-destination-" + java.util.UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE faxes(id INT PRIMARY KEY, destination VARCHAR(11) DEFAULT NULL)");
            statement.execute("INSERT INTO faxes VALUES(1,'14165550100'),(2,NULL)");
            statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of(
                    "database/mysql/migration/common/V1.0.19__widen_fax_destination_for_international_numbers.sql")));
            statement.execute("INSERT INTO faxes VALUES(3,'+123456789012345')");
            try (var rows = statement.executeQuery("SELECT destination FROM faxes ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("14165550100");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("+123456789012345");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678", "123456789", "1234567890123456", "!!!!!!!", "+1234567"})
    @DisplayName("should reject undialable SRFax destinations before queuing")
    void shouldRejectDestination_whenSrfaxCannotDial(String raw) {
        assertThatThrownBy(() -> FaxDestination.forQueue(raw, FaxConfig.ProviderType.SRFAX))
                .isInstanceOf(FaxProviderException.class);
    }

    @ParameterizedTest
    @CsvSource({"416-555-0100,14165550100,14165550100", "+1 (416) 555-0100,14165550100,14165550100",
            "+44 20 7946 0100,+442079460100,011442079460100",
            "011442079460100,+442079460100,011442079460100",
            "+123456789012345,+123456789012345,011123456789012345",
            "+44207946,+44207946,01144207946"})
    @DisplayName("should preserve international intent until a single provider normalization")
    void shouldPreserveDialingIntent_whenQueuingSrfax(String raw, String queued, String transmitted) throws Exception {
        assertThat(FaxDestination.forQueue(raw, FaxConfig.ProviderType.SRFAX)).isEqualTo(queued);
        assertThat(SRFaxProviderClient.normalizeDestinationNumber(queued)).isEqualTo(transmitted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"555-0100", "+5550100"})
    @DisplayName("should retain seven-digit destinations for legacy middleware")
    void shouldKeepLegacyLocalNumbers_whenMiddlewareSelected(String raw) throws Exception {
        assertThat(FaxDestination.forQueue(raw, FaxConfig.ProviderType.MIDDLEWARE)).isEqualTo("5550100");
    }
}
