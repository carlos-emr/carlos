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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleApplyResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportRequest;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleImportResult;
import io.github.carlos_emr.carlos.billings.ca.on.dto.FeeScheduleSelectedChange;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code FeeScheduleImportService} preview/apply parsing and validation rules. */
@DisplayName("Fee schedule import service")
@Tag("unit")
@Tag("billing")
class FeeScheduleImportServiceUnitTest {

    @Test
    void shouldPreviewNewCodeWithTypedChangeAndExactMoneyParsing() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        when(billingServiceDao.findMostRecentByServiceCode("A001A")).thenReturn(Collections.emptyList());
        when(billingServiceDao.searchDescBillingCode("A001", "ON")).thenReturn("Minor assessment");

        FeeScheduleImportService service = newService(billingServiceDao);

        FeeScheduleImportResult result = service.preview(stream(line("A001", "00000010050")),
                new FeeScheduleImportRequest(true, false, false, null, null));

        assertThat(result.validationErrors()).isEmpty();
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.feeCode()).isEqualTo("A001A");
            assertThat(change.oldPrice()).isNull();
            assertThat(change.newPrice()).isEqualByComparingTo("1.01");
            assertThat(change.description()).isEqualTo("Minor assessment");
        });
        assertThat(result.warningMaps()).singleElement().satisfies(warning -> {
            assertThat(warning).containsEntry("feeCode", "A001A");
            assertThat(warning).containsEntry("oldprice", "--");
            assertThat(warning).containsEntry("newprice", new BigDecimal("1.01"));
        });
    }

    @Test
    void shouldPreviewChangedCodeWithStoredDecimalHalfUpRounding() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        BillingService existing = new BillingService();
        existing.setServiceCode("A001A");
        existing.setValue("1.005");
        existing.setDescription("Existing description");
        existing.setBillingserviceDate(Date.from(Instant.parse("2026-04-27T00:00:00Z")));
        when(billingServiceDao.findMostRecentByServiceCode("A001A")).thenReturn(List.of(existing));

        FeeScheduleImportService service = newService(billingServiceDao);

        FeeScheduleImportResult result = service.preview(stream(line("A001", "00000020000")),
                new FeeScheduleImportRequest(false, true, false, null, null));

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.oldPrice()).isEqualByComparingTo("1.01");
            assertThat(change.newPrice()).isEqualByComparingTo("2.00");
            assertThat(change.diff()).isEqualByComparingTo("0.99");
            assertThat(change.numCodes()).isEqualTo(1);
        });
    }

    @Test
    void shouldReportMalformedExistingValueInsteadOfPreviewingFictionalDelta() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        BillingService existing = new BillingService();
        existing.setServiceCode("A001A");
        existing.setValue("not-money");
        existing.setDescription("Existing description");
        existing.setBillingserviceDate(Date.from(Instant.parse("2026-04-27T00:00:00Z")));
        when(billingServiceDao.findMostRecentByServiceCode("A001A")).thenReturn(List.of(existing));

        FeeScheduleImportService service = newService(billingServiceDao);

        FeeScheduleImportResult result = service.preview(stream(line("A001", "00000020000")),
                new FeeScheduleImportRequest(false, true, false, null, null));

        assertThat(result.changes()).isEmpty();
        assertThat(result.validationErrors()).singleElement().satisfies(error -> {
            assertThat(error.lineNumber()).isEqualTo(1);
            assertThat(error.field()).isEqualTo("existingValue");
            assertThat(error.message()).contains("A001A");
        });
    }

    @Test
    void shouldPreviewExistingCodeWithMissingStoredServiceDate() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        BillingService existing = new BillingService();
        existing.setServiceCode("A001A");
        existing.setValue("1.00");
        existing.setDescription("Existing description");
        when(billingServiceDao.findMostRecentByServiceCode("A001A")).thenReturn(List.of(existing));

        FeeScheduleImportService service = newService(billingServiceDao);

        FeeScheduleImportResult result = service.preview(stream(line("A001", "00000020000")),
                new FeeScheduleImportRequest(false, true, false, null, null));

        assertThat(result.validationErrors()).isEmpty();
        assertThat(result.changes()).singleElement()
                .satisfies(change -> assertThat(change.newPrice()).isEqualByComparingTo("2.00"));
    }

    @Test
    void shouldReportMalformedLinesAsValidationErrors() {
        FeeScheduleImportService service = newService(mock(BillingServiceDao.class));

        FeeScheduleImportResult result = service.preview(stream("bad-line\n"),
                new FeeScheduleImportRequest(true, true, false, null, null));

        assertThat(result.changes()).isEmpty();
        assertThat(result.validationErrors()).singleElement().satisfies(error -> {
            assertThat(error.lineNumber()).isEqualTo(1);
            assertThat(error.field()).isEqualTo("line");
        });
    }

    @Test
    void shouldPersistSelectedChangesWithNormalizedDates() throws Exception {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        FeeScheduleImportService service = newService(billingServiceDao);

        FeeScheduleSelectedChange selectedChange =
                FeeScheduleSelectedChange.fromSubmittedValue("A001A|33.70|20260428|99999999|Minor assessment");
        FeeScheduleApplyResult result = service.applySelected(List.of(selectedChange));

        ArgumentCaptor<BillingService> captor = ArgumentCaptor.forClass(BillingService.class);
        verify(billingServiceDao).persist(captor.capture());

        BillingService persisted = captor.getValue();
        assertThat(persisted.getServiceCode()).isEqualTo("A001A");
        assertThat(persisted.getValue()).isEqualTo("33.70");
        assertThat(persisted.getDescription()).isEqualTo("Minor assessment");
        assertThat(persisted.getRegion()).isEqualTo("ON");
        assertThat(result.changes()).singleElement()
                .satisfies(change -> assertThat(change.toViewMap()).containsEntry("code", "A001A"));

        Method method = FeeScheduleImportService.class.getMethod("applySelected", List.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    private FeeScheduleImportService newService(BillingServiceDao billingServiceDao) {
        return new FeeScheduleImportService(billingServiceDao,
                Clock.fixed(Instant.parse("2026-04-28T00:00:00Z"), ZoneOffset.UTC));
    }

    private ByteArrayInputStream stream(String contents) {
        return new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
    }

    private String line(String code, String gpFee) {
        return code + "20260428" + "99999999"
                + gpFee + "00000000000" + "00000000000"
                + "00000000000" + "00000000000" + "\n";
    }
}
