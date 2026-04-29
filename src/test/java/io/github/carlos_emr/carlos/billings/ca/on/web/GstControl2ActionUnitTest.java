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
