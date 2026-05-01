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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.math.BigDecimal;

import io.github.carlos_emr.carlos.billings.ca.on.service.GstSettingsService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GstControl2Action")
@Tag("unit")
@Tag("billing")
class GstControl2ActionUnitTest {

    @Test
    void shouldReturnValidationErrorInsteadOfThrowingForInvalidGstPercent() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        GstSettingsService gstSettingsService = mock(GstSettingsService.class);
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(gstSettingsService.getCurrentPercent()).thenReturn(new BigDecimal("13.00"));
        GstControl2Action action = new GstControl2Action(securityInfoManager, gstSettingsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        action.withServletRequest(request);
        action.setGstPercent("not-a-number");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).contains("Invalid GST percent.");
        assertThat(request.getAttribute("gstControlModel")).isNotNull();
        verify(gstSettingsService, never()).setCurrentPercent(any(BigDecimal.class));
    }
}
