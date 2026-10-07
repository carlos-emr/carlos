/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
class EctDeleteMeasurementStyleSheet2ActionUnitTest extends CarlosUnitTestBase {
    private MeasurementGroupStyleDao styles;
    private MeasurementCSSLocationDao locations;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private EctDeleteMeasurementStyleSheet2Action action;
    private MeasurementGroupStyle row;

    @BeforeEach
    void setUp() {
        styles = mock(MeasurementGroupStyleDao.class);
        locations = mock(MeasurementCSSLocationDao.class);
        security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        registerMock(MeasurementGroupStyleDao.class, styles);
        registerMock(MeasurementCSSLocationDao.class, locations);
        registerMock(SecurityInfoManager.class, security);
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        row = new MeasurementGroupStyle();
        row.setId(91);
        row.setCssId(27);
        when(styles.findByCssId(27)).thenReturn(List.of(row));
        action = spy(new EctDeleteMeasurementStyleSheet2Action());
        action.setDeleteCheckbox(new String[]{"27"});
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    @Test
    void shouldDenyTheRequestBeforeReadingRows_whenNeitherAdminPrivilegeIsGranted() {
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(styles, locations);
    }

    @Test
    void shouldAllowTheMeasurementAdministrator_whenGeneralAdminWriteIsNotGranted() throws Exception {
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo("success");
        verify(styles).remove(Integer.valueOf(91));
        verify(styles, never()).remove(row);
        verify(locations, never()).remove(any(Integer.class));
    }

    @Test
    void shouldRefuseDeletionAndEncodeTheErrorParameter_whenTheStylesheetStillExists() throws Exception {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        MeasurementCSSLocation location = new MeasurementCSSLocation();
        location.setLocation("<script>owned</script>");
        when(locations.find(27)).thenReturn(location);
        doReturn("Stylesheet still in use").when(action).getText(
                eq("error.encounter.Measurements.cannotDeleteStyleSheet"), any(String[].class));
        assertThat(action.execute()).isEqualTo("error");
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of("Stylesheet still in use"));
        verify(action).getText("error.encounter.Measurements.cannotDeleteStyleSheet",
                new String[]{"&lt;script&gt;owned&lt;/script&gt;"});
        verify(styles, never()).remove(any(Integer.class));
        verify(styles, never()).remove(row);
        verify(locations, never()).remove(any(Integer.class));
    }

    @Test
    void shouldPropagateTheDatabaseFailure_whenDeletionCannotComplete() {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        when(styles.remove(Integer.valueOf(91))).thenThrow(new IllegalStateException("Owned test failure"));
        assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class)
                .hasMessage("Owned test failure");
    }
}
