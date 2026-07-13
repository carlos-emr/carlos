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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit coverage for BC billing manager price/percentage calculations. */
@DisplayName("BillingBillingManager")
@Tag("unit")
@Tag("billing")
class BillingBillingManagerUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldThrowHelpfulException_whenServicePercentageIsMalformed() {
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        GstSettingsService gstSettingsService = mock(GstSettingsService.class);
        registerMock(BillingServiceDao.class, billingServiceDao);
        registerMock(GstSettingsService.class, gstSettingsService);
        when(gstSettingsService.getCurrentPercent()).thenReturn(BigDecimal.ZERO);

        BillingService service = new BillingService();
        service.setServiceCode("00100");
        service.setDescription("Office visit");
        service.setValue("10.00");
        service.setGstFlag(false);
        service.setPercentage("not-a-percent");
        when(billingServiceDao.findByServiceCode("00100")).thenReturn(List.of(service));

        BillingBillingManager manager = new BillingBillingManager();
        BillingBillingManager.BillingItem item = manager.new BillingItem("00100", "1");

        assertThatThrownBy(() -> item.fill("msp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid percentage")
                .hasMessageContaining("00100")
                .hasMessageContaining("not-a-percent")
                .hasCauseInstanceOf(NumberFormatException.class);
    }
}
