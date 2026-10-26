/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.plugin.IsPropertiesOn;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class EctDisplayDocs2ActionUnitTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "false", "unexpected", "TRUE", "true", " true "})
    void shouldUseViewerGroupingPreference_whenRenderingDocuments(String storedValue) {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        UserPropertyDAO preferences = mock(UserPropertyDAO.class);
        LoggedInInfo viewer = mock(LoggedInInfo.class);
        when(viewer.getLoggedInProviderNo()).thenReturn("viewer");
        when(security.hasPrivilege(viewer, "_edoc", "r", null)).thenReturn(true);
        if (storedValue != null) {
            UserProperty property = new UserProperty();
            property.setValue(storedValue);
            when(preferences.getProp("viewer", UserProperty.ECHART_GROUP_DOCUMENTS_BY_TYPE)).thenReturn(property);
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", viewer);
        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = "1";
        bean.providerNo = "appointment-provider";
        EDoc older = document("1", "Older", "consult", "2020-01-01");
        EDoc newest = document("2", "Newest", "lab", "2026-10-26");
        EDoc undated = document("3", "Undated", " ", null);
        EDoc omitted = document("4", "Omitted", "econsult", "2026-10-27");
        ArrayList<EDoc> documents = new ArrayList<>(List.of(older, undated, omitted, newest));

        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedStatic<IsPropertiesOn> properties = mockStatic(IsPropertiesOn.class);
             MockedStatic<EDocUtil> docs = mockStatic(EDocUtil.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(UserPropertyDAO.class)).thenReturn(preferences);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            docs.when(() -> EDocUtil.listDocs(viewer, "demographic", "1", null,
                    EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE, "active")).thenReturn(documents);
            EctDisplayDocs2Action action = spy(new EctDisplayDocs2Action());
            doAnswer(call -> call.getArgument(0)).when(action).getText(anyString());
            NavBarDisplayDAO dao = new NavBarDisplayDAO();

            assertThat(action.getInfo(bean, request, dao)).isTrue();

            boolean grouped = storedValue != null && "true".equals(storedValue.trim());
            assertThat(dao.isFiledInFolders()).isEqualTo(grouped);
            assertThat(dao.numItems()).isEqualTo(3);
            assertThat(dao.getItem(0).getTitle()).isEqualTo("Newest");
            assertThat(dao.getItem(1).getTitle()).isEqualTo("Older");
            assertThat(dao.getItem(2).getTitle()).isEqualTo("Undated");
            assertThat(dao.getItem(0).getFolder()).isEqualTo(grouped ? "lab" : "");
            assertThat(dao.getItem(1).getFolder()).isEqualTo(grouped ? "consult" : "");
            assertThat(dao.getItem(2).getFolder()).isEqualTo(grouped ? "encounter.LeftNavBar.docs.otherFolder" : "");
            verify(preferences).getProp("viewer", UserProperty.ECHART_GROUP_DOCUMENTS_BY_TYPE);
        }
    }

    @Test
    void shouldKeepDatedDocumentsFirst_whenDatesMissingOrInvalid() {
        EDoc missing = document("1", "Missing", "lab", null);
        EDoc invalid = document("2", "Invalid", "lab", "2026-02-30");
        EDoc older = document("3", "Older", "lab", "2020-01-01");
        EDoc newest = document("4", "Newest", "consult", "2026-10-26");
        EDoc sameDay = document("5", "Same day", "lab", "2026-10-26");
        EDoc blank = document("6", "Blank", "lab", "");
        EDoc trailingText = document("7", "Trailing text", "lab", "2026-10-26invalid");
        ArrayList<EDoc> documents = new ArrayList<>(Arrays.asList(
                missing, older, invalid, newest, sameDay, blank, trailingText));

        EctDisplayDocs2Action.sortByDate(documents);

        assertThat(documents).containsExactly(newest, sameDay, older, missing, invalid, blank, trailingText);
    }

    private static EDoc document(String id, String description, String type, String date) {
        EDoc doc = new EDoc();
        doc.setDocId(id);
        doc.setDescription(description);
        doc.setType(type);
        doc.setObservationDate(date);
        doc.setStatus('A');
        return doc;
    }
}
