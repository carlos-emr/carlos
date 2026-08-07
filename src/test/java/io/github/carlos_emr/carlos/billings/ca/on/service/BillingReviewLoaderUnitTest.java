/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingReviewServiceParam;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReviewCodeItem;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReviewPercentageItem;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BillingReviewLoader} review-screen data assembly and fallback behavior. */
@DisplayName("Ontario billing review loader")
@Tag("unit")
@Tag("billing")
class BillingReviewLoaderUnitTest {

    private final BillingOnClaimLoader claimLoader = mock(BillingOnClaimLoader.class);
    private final BillingReviewLoader loader = new BillingReviewLoader(
            claimLoader,
            mock(BillingOnDiskLoader.class),
            mock(BillingOnLookupService.class),
            mock(DiagnosticCodeDao.class));

    @Test
    void shouldCalculateServiceCodeTotals_withHalfUpRounding() {
        when(claimLoader.getCodeFeeResult("A001A", "2026-05-01"))
                .thenReturn(BillingOnClaimLoader.FeeLookupResult.found("10.005"));
        when(claimLoader.getCodeDescription("A001A", "2026-05-01"))
                .thenReturn("Minor assessment");

        List<BillingReviewCodeItem> items = loader.getServiceCodeReviewItems(
                List.of(new BillingReviewServiceParam("A001A", "2", "1.5")),
                "2026-05-01");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCodeFee()).isEqualTo("10.005");
        assertThat(items.get(0).getCodeTotal()).isEqualTo("30.02");
        assertThat(items.get(0).getCodeDescription()).isEqualTo("Minor assessment");
    }

    @Test
    void shouldSurfacePartialServiceFeeLookup_withoutThrowing() {
        when(claimLoader.getCodeFeeResult("A001A", "2026-05-01"))
                .thenReturn(BillingOnClaimLoader.FeeLookupResult.partial("lookup failed"));

        List<BillingReviewCodeItem> items = loader.getServiceCodeReviewItems(
                List.of(new BillingReviewServiceParam("A001A", "2", "1")),
                "2026-05-01");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCodeFee()).isEqualTo("0");
        assertThat(items.get(0).getCodeTotal()).isEqualTo("0");
        assertThat(items.get(0).getMsg()).contains("Fee lookup failed");
    }

    @Test
    void shouldCalculatePercentageRows_andKeepMinMaxWarningContract() {
        when(claimLoader.getPercFeeResult("P001A", "2026-05-01"))
                .thenReturn(BillingOnClaimLoader.FeeLookupResult.found("0.1000"));
        when(claimLoader.getPercMinMaxFeeResult("P001A", "2026-05-01"))
                .thenReturn(BillingOnClaimLoader.FeeRangeLookupResult.found("1.00", "5.00"));

        List<BillingReviewPercentageItem> items = loader.getPercentageCodeReviewItems(
                List.of(
                        new BillingReviewServiceParam("A001A", "1", "1"),
                        new BillingReviewServiceParam("P001A", "1", "1")),
                List.of(new BillingReviewCodeItem("A001A", "1", "10.00", "10.00", "1", "", "")),
                "2026-05-01");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getCodeFee()).isEqualTo("0.1000");
        assertThat(items.get(0).getCodeMinFee()).isEqualTo("1.00");
        assertThat(items.get(0).getCodeMaxFee()).isEqualTo("5.00");
        assertThat(items.get(0).getCodeTotals()).containsExactly("1.0000");
        assertThat(items.get(0).getMsg()).isEmpty();
    }

    @Test
    void shouldSkipNullAndBlankRequestCodes_andDefaultMissingUnitAndAt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("code1", "A001A");
        request.setParameter("code2", "");

        List<BillingReviewServiceParam> rows = loader.getRequestCodes(
                request, "code", "unit", "at", 3);

        assertThat(rows).containsExactly(new BillingReviewServiceParam("A001A", "1", "1"));
    }
}
