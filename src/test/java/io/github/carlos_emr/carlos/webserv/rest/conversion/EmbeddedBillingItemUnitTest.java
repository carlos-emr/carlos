/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.webserv.rest.to.model.BillingDetailTo1;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
class EmbeddedBillingItemUnitTest {
    static Stream<ObjectMapper> mappers() { return Stream.of(ProductionRestObjectMappers.sessionSurface(),ProductionRestObjectMappers.oauthSurface()); }
    @ParameterizedTest @MethodSource("mappers")
    void shouldSerializeExactInvoiceValuesAndIdentity_withoutTheUnsupportedOptionalEntityGetter(ObjectMapper mapper) throws Exception {
        var source = new BillingONItem(7); source.setFee("12.00"); source.setServiceCode("A001");
        source.setServiceCount("1"); source.setServiceDate(new Date(123456789)); source.setLastEditDT(new Date(987654321));
        source.setDx("250"); source.setStatus(BillingONItem.BILLED);
        assertThatThrownBy(() -> mapper.writeValueAsBytes(source))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class).hasMessageContaining("feeAsBigDecimal");
        var billing = new BillingDetailTo1(); billing.setBillingItems(List.of(source));
        source.setDx("changed");
        var wire = mapper.readTree(mapper.writeValueAsBytes(billing));
        assertThat(wire.at("/billingItems/0/id").intValue()).isEqualTo(7);
        assertThat(wire.at("/billingItems/0/fee").asText()).isEqualTo("12.00");
        assertThat(wire.at("/billingItems/0/feeAsBigDecimal").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(wire.at("/billingItems/0/dx").asText()).isEqualTo("250");
        assertThat(wire.at("/billingItems/0/lastEditDT").longValue()).isEqualTo(987654321);
        assertThat(wire.has("billingItemSnapshots")).isFalse();
        assertThat(billing.getBillingItems().getFirst().getId()).isEqualTo(7);
        assertThat(billing.getBillingItems().getFirst().getFee()).isEqualTo("12.00");
    }
    @Test void shouldRetainTheDefunctFeeSentinel_withAnAbsentNumericAmount() {
        var source = new BillingONItem(); source.setFee(BillingONItem.DEFUNCT_FEE);
        var snapshot = EmbeddedRecordSnapshots.billingItem(source);
        assertThat(snapshot.getFee()).isEqualTo(BillingONItem.DEFUNCT_FEE);
        assertThat(snapshot.getFeeAsBigDecimal()).isNull(); assertThat(snapshot.isDefunct()).isTrue();
    }
    @Test void shouldRejectMalformedLegacyAmounts_insteadOfSilentlyReplacingThemWithNull() {
        var source = new BillingONItem(); org.springframework.test.util.ReflectionTestUtils.setField(source,"fee","invalid stored amount");
        assertThatThrownBy(() -> EmbeddedRecordSnapshots.billingItem(source)).isInstanceOf(ConversionException.class)
                .hasMessage("Invalid stored billing amount");
    }
    @Test void shouldRejectUninitializedBillingLines_insteadOfReturningAnEmptyInvoice() {
        var source = new org.hibernate.collection.spi.PersistentBag<BillingONItem>();
        assertThatThrownBy(() -> new BillingDetailTo1().setBillingItems(source)).isInstanceOf(ConversionException.class);
    }
}
