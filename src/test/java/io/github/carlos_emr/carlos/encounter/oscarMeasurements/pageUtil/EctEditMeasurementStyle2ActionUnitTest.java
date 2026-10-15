/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class EctEditMeasurementStyle2ActionUnitTest extends CarlosUnitTestBase {
    private MeasurementGroupStyleDao dao;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loginContext;
    private EctEditMeasurementStyle2Action action;
    private MeasurementGroupStyle row;

    @BeforeEach
    void setUp() {
        dao = mock(MeasurementGroupStyleDao.class);
        security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        registerMock(MeasurementGroupStyleDao.class, dao);
        registerMock(SecurityInfoManager.class, security);
        request = new MockHttpServletRequest();
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        loginContext = mockStatic(LoggedInInfo.class);
        loginContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        row = row(91, 3);
        when(dao.findByGroupName("Synthetic group A")).thenReturn(List.of(row));
        action = new EctEditMeasurementStyle2Action();
        action.setGroupName("Synthetic group A");
        action.setStyleSheet("27");
    }

    @AfterEach
    void tearDown() {
        loginContext.close();
        servletContext.close();
    }

    private static MeasurementGroupStyle row(int id, int cssId) {
        MeasurementGroupStyle row = new MeasurementGroupStyle();
        row.setId(id);
        row.setGroupName("Synthetic group A");
        row.setCssId(cssId);
        return row;
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "27", "2147483647"})
    void shouldChangeOnlyTheStylesheetReference_andPreserveTheRowIdentity(String css) throws Exception {
        action.setStyleSheet(css);
        assertThat(action.execute()).isEqualTo("continue");
        assertThat(row.getId()).isEqualTo(91);
        assertThat(row.getGroupName()).isEqualTo("Synthetic group A");
        assertThat(row.getCssId()).isEqualTo(Integer.parseInt(css));
        verify(dao).merge(row);
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo("Synthetic group A");
    }

    @Test
    void shouldUpdateAllExistingRowsForTheGroup_withoutChangingTheirKeys() throws Exception {
        MeasurementGroupStyle second = row(92, 4);
        when(dao.findByGroupName("Synthetic group A")).thenReturn(List.of(row, second));
        action.execute();
        assertThat(row.getId()).isEqualTo(91);
        assertThat(second.getId()).isEqualTo(92);
        assertThat(row.getCssId()).isEqualTo(27);
        assertThat(second.getCssId()).isEqualTo(27);
        verify(dao).merge(row);
        verify(dao).merge(second);
    }

    @Test
    void shouldAllowTheMeasurementAdministrator_withoutGeneralAdminWrite() throws Exception {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(false);
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo("continue");
        assertThat(row.getId()).isEqualTo(91);
        assertThat(row.getCssId()).isEqualTo(27);
        verify(dao).merge(row);
    }

    @Test
    void shouldDenyAnUnauthorizedChange_beforeReadingOrWritingRows() {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(dao);
        assertThat(row.getCssId()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "2147483648"})
    void shouldRejectANonIntegerStylesheet_withoutMutatingTheRow(String css) {
        action.setStyleSheet(css);
        assertThatThrownBy(action::execute).isInstanceOf(NumberFormatException.class);
        assertThat(row.getId()).isEqualTo(91);
        assertThat(row.getCssId()).isEqualTo(3);
        verify(dao, never()).merge(any());
    }

    @Test
    void shouldNotCreateAStyleRow_whenTheGroupHasNone() throws Exception {
        when(dao.findByGroupName("Synthetic group A")).thenReturn(List.of());
        assertThat(action.execute()).isEqualTo("continue");
        verify(dao, never()).merge(any());
    }
}
