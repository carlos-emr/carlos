package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.billings.ca.on.service.GstReportService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GstReportViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GstReportViewModelAssembler")
@Tag("unit")
@Tag("billing")
class GstReportViewModelAssemblerUnitTest {

    @Test
    void shouldTreatNullProviderLookupAsEmptyList() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        GstReportService gstReport = mock(GstReportService.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), any(String.class), eq("r"), isNull()))
                .thenReturn(false);
        when(lookupService.getCurProviderStr()).thenReturn(null);
        when(gstReport.getGST(eq(loggedInInfo), eq(""), eq(""), eq("")))
                .thenReturn(new ArrayList<>());
        GstReportViewModelAssembler assembler =
                new GstReportViewModelAssembler(securityInfoManager, gstReport, lookupService);

        GstReportViewModel model = assembler.assemble(new MockHttpServletRequest(), loggedInInfo);

        assertThat(model.getProviderOptions()).isEmpty();
        assertThat(model.getRows()).isEmpty();
    }
}
