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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit coverage for legacy Ontario DTO field visibility and money invariants. */
@DisplayName("Ontario legacy billing DTO invariants")
@Tag("unit")
@Tag("billing")
class BillingLegacyDtoMoneyInvariantUnitTest {

    @Test
    void shouldKeepLegacyDtoFieldsPrivate_whenReviewedWithReflection() {
        List<Class<?>> dtoTypes = List.of(
                BillingProviderDto.class,
                BillingBatchHeaderDto.class,
                BillingDiskNameDto.class,
                BillingErrorReportDto.class,
                BillingRaDetailDto.class,
                BillingClaimBatchAcknowledgementReportRecordDto.class,
                BillingEdtObecOutputSpecificationRecordDto.class,
                BillingClaimsErrorReportRecordDto.class);

        for (Class<?> dtoType : dtoTypes) {
            assertThat(List.of(dtoType.getDeclaredFields()))
                    .as(dtoType.getSimpleName())
                    .allSatisfy(field -> assertThat(Modifier.isPrivate(field.getModifiers()))
                            .as(dtoType.getSimpleName() + "." + field.getName())
                            .isTrue());
        }
    }

    @Test
    void shouldNormalizeFixedWidthMoney_whenErrorReportAmountsSet() {
        BillingErrorReportDto report = new BillingErrorReportDto();
        BillingClaimsErrorReportRecordDto record = new BillingClaimsErrorReportRecordDto();

        report.setFeeStoredCents("003500");
        record.setAmountsubmitStoredCents("000123");

        assertThat(report.getFee()).isEqualTo("35.00");
        assertThat(report.getFeeMoney().format()).isEqualTo("35.00");
        assertThat(record.getAmountsubmit()).isEqualTo("1.23");
        assertThat(record.getAmountsubmitMoney().format()).isEqualTo("1.23");
    }

    @Test
    void shouldTreatDigitOnlyMoneyAsDecimal_whenLegacyDaoValueSet() {
        BillingErrorReportDto report = new BillingErrorReportDto();

        report.setFee("123456");

        assertThat(report.getFee()).isEqualTo("123456.00");
        assertThat(report.getFeeMoney().format()).isEqualTo("123456.00");
    }

    @Test
    void shouldNormalizeSignedDecimalMoney_whenRaAmountsSet() {
        BillingRaDetailDto detail = new BillingRaDetailDto();

        detail.setAmountclaim("33.7");
        detail.setAmountpay("-1.235");

        assertThat(detail.getAmountclaim()).isEqualTo("33.70");
        assertThat(detail.getAmountclaimAmount()).isEqualByComparingTo("33.70");
        assertThat(detail.getAmountpay()).isEqualTo("-1.24");
        assertThat(detail.getAmountpayAmount()).isEqualByComparingTo("-1.24");
    }

    @Test
    void shouldExposeTypedServiceCodeMoney_whenValuesPresent() {
        BillingCodeAttribute attr = new BillingCodeAttribute(
                "A001A", "Minor assessment", "33.7", "0.15", "2026-05-01", "false");

        assertThat(attr.value()).isEqualTo("33.7");
        assertThat(attr.valueMoney().format()).isEqualTo("33.70");
        assertThat(attr.percentageAmount()).isEqualByComparingTo(new BigDecimal("0.15"));
    }

    @Test
    void shouldRejectMalformedMoney_whenDtoMoneyFieldsSet() {
        BillingErrorReportDto report = new BillingErrorReportDto();
        BillingClaimsErrorReportRecordDto record = new BillingClaimsErrorReportRecordDto();
        BillingRaDetailDto detail = new BillingRaDetailDto();

        assertThatThrownBy(() -> report.setFee("not-money"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("fee");
        assertThatThrownBy(() -> record.setAmountsubmit("12A456"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("amountsubmit");
        assertThatThrownBy(() -> detail.setAmountclaim("bad"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("amountclaim");
    }
}
