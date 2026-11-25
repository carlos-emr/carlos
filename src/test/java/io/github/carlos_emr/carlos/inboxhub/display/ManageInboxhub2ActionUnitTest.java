// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.inboxhub.display;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.inboxhub.inboxdata.LabDataController;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.mds.data.CategoryData;
import java.util.ArrayList;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ManageInboxhub2ActionUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest @NullSource
    @ValueSource(strings = {"displayInboxForm", "displayInboxList", "displayInboxView", "unexpected"})
    void shouldDenyEveryDispatch_beforeConstructingDataController(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (method != null) request.setParameter("method", method);
        createAndRegisterMock(SecurityInfoManager.class);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedConstruction<LabDataController> controllers = mockConstruction(LabDataController.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            assertThat(new ManageInboxhub2Action().execute()).isEqualTo("unauthorized");
            assertThat(controllers.constructed()).isEmpty();
        }
    }
    @ParameterizedTest @CsvSource({"displayInboxList,displayList,false,false", "displayInboxView,displayView,false,false",
            "displayInboxList,displayList,true,false", "displayInboxView,displayView,true,false",
            "displayInboxList,displayList,false,true", "displayInboxView,displayView,true,true"})
    void shouldContinueWhenThePageHasRenderableOrOmittedResults(String method, String result, boolean omitted,
            boolean hasVisibleItem) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setParameter("method", method);
        request.setParameter("page", "2"); request.setParameter("pageSize", "10");
        request.setParameter("demographicFilter", "770001"); request.setParameter("typeFilter", "DOC");
        if (omitted) request.setAttribute("inboxDocumentsIncomplete", true);
        var items = new ArrayList<LabResultData>();
        if (hasVisibleItem) items.add(mock(LabResultData.class));
        LoggedInInfo info = mock(LoggedInInfo.class); LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_appointment.doctorLink", SecurityInfoManager.READ, null)).thenReturn(true);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedConstruction<LabDataController> controllers = mockConstruction(LabDataController.class,
                (mock, context) -> when(mock.getLabData(eq(info), any())).thenReturn(items))) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            ManageInboxhub2Action action = new ManageInboxhub2Action();
            assertThat(action.execute()).isEqualTo(result);
            assertThat(action.getQuery().getPage()).isEqualTo(2); assertThat(action.getQuery().getPageSize()).isEqualTo(10);
            assertThat(request.getAttribute("page")).isEqualTo("2");
            assertThat(request.getAttribute("pageSize")).isEqualTo("10");
            assertThat(request.getAttribute("hasMoreData")).isEqualTo(omitted || hasVisibleItem);
            assertThat(request.getAttribute("labDocs")).isSameAs(items);
            verify(controllers.constructed().get(0)).sanitizeInboxFormQuery(info, action.getQuery(), "770001", "DOC");
        }
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"displayInboxForm", "unexpected"})
    void shouldRenderSummary_whenDispatchDefaultsToForm(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (method != null) request.setParameter("method", method);
        request.setParameter("unclaimed", "true");
        LoggedInInfo info = mock(LoggedInInfo.class); LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_appointment.doctorLink", SecurityInfoManager.READ, null)).thenReturn(true);
        CategoryData categories = mock(CategoryData.class);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedConstruction<LabDataController> controllers = mockConstruction(LabDataController.class, (mock, context) -> {
                 when(mock.getCategoryData(any(), any())).thenReturn(categories);
                 when(mock.getTotalResultsCountBasedOnQuery(any(), eq(categories))).thenReturn(new int[] {2, 3, 4, 9});
             })) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            ManageInboxhub2Action action = new ManageInboxhub2Action(); assertThat(action.execute()).isEqualTo("success");
            assertThat(request.getAttribute("totalDocsCount")).isEqualTo(2);
            assertThat(request.getAttribute("totalLabsCount")).isEqualTo(3);
            assertThat(request.getAttribute("totalHRMCount")).isEqualTo(4);
            assertThat(request.getAttribute("totalResultsCount")).isEqualTo(9);
            assertThat(request.getAttribute("categoryData")).isSameAs(categories);
            verify(controllers.constructed().get(0)).setInboxFormQueryUnclaimed(action.getQuery(), "true");
        }
    }
}
