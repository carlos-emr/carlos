/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.demographic.data.DemographicMergeSearch;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class DemographicMergeRecord2ActionUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DemographicDao dao = mock(DemographicDao.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final CarlosProperties properties = mock(CarlosProperties.class);

    @BeforeEach
    void setup() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicDao.class, dao);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(info, "_demographic", "w", null)).thenReturn(true);
        when(properties.getProperty("ModuleNames", "")).thenReturn("Caisi");
        when(properties.getProperty("pmm.client.search.outside.of.domain.enabled", "true")).thenReturn("false");
        when(dao.searchForMerge(any(), anyString(), anyBoolean())).thenReturn(List.of());
    }

    private String execute() throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class);
             var loggedIn = mockStatic(LoggedInInfo.class); var config = mockStatic(CarlosProperties.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            config.when(CarlosProperties::getInstance).thenReturn(properties);
            return new DemographicMergeRecord2Action().execute();
        }
    }

    @Test
    void shouldKeepInitialViewEmpty_withoutSearchParameters() throws Exception {
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(dao);
        assertThat(request.getAttribute("mergeSearchResults")).isEqualTo(List.of());
    }

    @Test
    void shouldRefuseSearch_withoutWritePrivilege() {
        when(security.hasPrivilege(info, "_demographic", "w", null)).thenReturn(false);
        request.setParameter("keyword", "synthetic");
        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(dao);
    }

    @ParameterizedTest
    @CsvSource({"limit1,-1", "limit1,abc", "limit1,2147483647", "limit2,0", "limit2,501",
            "search_mode,unknown", "orderby,last_name desc"})
    void shouldReturnBadRequest_forUnsupportedSearchOptions(String name, String value) throws Exception {
        request.setParameter("keyword", "synthetic");
        request.setParameter(name, value);
        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).doesNotContain("synthetic", value);
        verifyNoInteractions(dao);
    }

    @Test
    void shouldKeepDomainRestriction_withoutOutsideDomainPrivilege() throws Exception {
        request.setParameter("keyword", "synthetic");
        request.setParameter("outofdomain", "true");
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(dao).searchForMerge(new DemographicMergeSearch("search_name", "synthetic", "last_name", 0, 10, false),
                "999998", false);
    }

    @Test
    void shouldAllowExplicitDomainOverride_withOutsideDomainPrivilege() throws Exception {
        when(security.hasPrivilege(info, "_search.outofdomain", "r", null)).thenReturn(true);
        request.setParameter("keyword", "synthetic");
        request.setParameter("outofdomain", "true");
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(dao).searchForMerge(any(), eq("999998"), eq(true));
    }
}
