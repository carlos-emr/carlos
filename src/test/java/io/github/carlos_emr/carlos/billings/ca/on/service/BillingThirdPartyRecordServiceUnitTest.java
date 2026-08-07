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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for the third-party record compatibility facade. */
@DisplayName("BillingThirdPartyRecordService")
@Tag("unit")
@Tag("billing")
class BillingThirdPartyRecordServiceUnitTest {

    private final BillingThirdPartyService thirdPartyService = mock(BillingThirdPartyService.class);
    private final BillingThirdPartyRecordService service =
            new BillingThirdPartyRecordService(thirdPartyService);

    @Test
    void shouldDelegateActiveThirdPartyProperties_toThirdPartyService() {
        Properties expected = new Properties();
        expected.setProperty("payment", "10.00");
        when(thirdPartyService.get3rdPartBillProp("42")).thenReturn(expected);

        assertThat(service.get3rdPartBillProp("42")).isSameAs(expected);
        verify(thirdPartyService).get3rdPartBillProp("42");
    }

    @Test
    void shouldDelegateInactiveThirdPartyProperties_toThirdPartyService() {
        Properties expected = new Properties();
        expected.setProperty("status", "D");
        when(thirdPartyService.get3rdPartBillPropInactive("42")).thenReturn(expected);

        assertThat(service.get3rdPartBillPropInactive("42")).isSameAs(expected);
        verify(thirdPartyService).get3rdPartBillPropInactive("42");
    }

    @Test
    void shouldDelegateLocalClinicAddress_toThirdPartyService() {
        Properties expected = new Properties();
        expected.setProperty("clinic", "main");
        when(thirdPartyService.getLocalClinicAddr()).thenReturn(expected);

        assertThat(service.getLocalClinicAddr()).isSameAs(expected);
        verify(thirdPartyService).getLocalClinicAddr();
    }

    @Test
    void shouldDelegatePaymentMethods_toThirdPartyService() {
        Properties expected = new Properties();
        expected.setProperty("1", "Cash");
        when(thirdPartyService.get3rdPayMethod()).thenReturn(expected);

        assertThat(service.get3rdPayMethod()).isSameAs(expected);
        verify(thirdPartyService).get3rdPayMethod();
    }

    @Test
    void shouldDelegateGstLookup_toThirdPartyService() {
        Properties expected = new Properties();
        expected.setProperty("gst", "13.00");
        when(thirdPartyService.getGstTotal("42")).thenReturn(expected);

        Properties actual = service.getGst("42");

        assertThat(actual).isSameAs(expected);
        verify(thirdPartyService).getGstTotal("42");
    }

    @Test
    void shouldDelegateKeyUpdateIgnoringLegacyDemoNoParameter_toCollaborator() {
        when(thirdPartyService.updateKeyValue("42", "payment", "10.00")).thenReturn(true);

        boolean updated = service.updateKeyValue("42", "123", "payment", "10.00");

        assertThat(updated).isTrue();
        verify(thirdPartyService).updateKeyValue("42", "payment", "10.00");
    }
}
