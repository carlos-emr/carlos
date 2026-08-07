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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnRaService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaReportService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaErrorViewModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the LOAD_FAILURE_MARKER consumer contract on
 * {@link OnRaErrorViewModelAssembler}. The producer side
 * ({@link BillingOnRaService#getRAErrorReport}) emits a sentinel Properties
 * row when its outer catch fires; the assembler must filter the sentinel
 * AND raise {@code partial=true} on the viewmodel so the JSP can render
 * a "data may be incomplete" banner.
 *
 * <p>Without this test, a refactor that drops the marker check would
 * silently render a phantom blank reconciliation row as if it were real
 * data.</p>
 *
 * @since 2026-04-30
 */
@DisplayName("OnRaErrorViewModelAssembler LOAD_FAILURE_MARKER consumer")
@Tag("unit")
@Tag("billing")
class OnRaErrorViewModelAssemblerUnitTest {

    private static Properties row(String account, String servicecode) {
        Properties p = new Properties();
        p.setProperty("account", account);
        p.setProperty("servicecode", servicecode);
        p.setProperty("servicedate", "2026-04-25");
        p.setProperty("serviceno", "1");
        p.setProperty("amountsubmit", "100.00");
        p.setProperty("amountpay", "100.00");
        p.setProperty("explain", "");
        p.setProperty("demoLast", "Doe");
        return p;
    }

    private static Properties marker() {
        Properties p = new Properties();
        p.setProperty(BillingOnRaService.LOAD_FAILURE_MARKER, "true");
        return p;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void shouldFilterMarker_andRaisePartialFlag() {
        BillingRaReportService prep = mock(BillingRaReportService.class);
        when(prep.getProviderListFromRAReport(anyString())).thenReturn(new ArrayList());
        // List has one real row + the marker; the marker must be dropped
        // from rendered rows and the partial flag must be true.
        List<Properties> errorList = List.of(row("100", "A007"), marker());
        when(prep.getRAErrorReport(anyString(), anyString(), any(String[].class)))
                .thenReturn(errorList);

        OnRaErrorViewModel vm = new OnRaErrorViewModelAssembler(prep)
                .assemble("99", "012345");

        assertThat(vm.isPartial()).isTrue();
        assertThat(vm.getErrorRows()).hasSize(1);
        assertThat(vm.getErrorRows().get(0).account()).isEqualTo("100");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void shouldNotRaisePartial_whenNoMarkerPresent() {
        BillingRaReportService prep = mock(BillingRaReportService.class);
        when(prep.getProviderListFromRAReport(anyString())).thenReturn(new ArrayList());
        when(prep.getRAErrorReport(anyString(), anyString(), any(String[].class)))
                .thenReturn(List.of(row("100", "A007"), row("101", "A008")));

        OnRaErrorViewModel vm = new OnRaErrorViewModelAssembler(prep)
                .assemble("99", "012345");

        assertThat(vm.isPartial()).isFalse();
        assertThat(vm.getErrorRows()).hasSize(2);
    }
}
