/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.UUID;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Each real DAO call completes its transaction, just as it does in the web action. */
@Tag("integration")
@Tag("measurement")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EctDeleteMeasurementStyleSheet2ActionIntegrationTest extends CarlosTestBase {
    @Autowired private MeasurementGroupStyleDao styles;
    @Autowired private MeasurementCSSLocationDao locations;

    @Test
    void shouldDeleteOnlyTheSelectedOrphan_whenDaoTransactionsAreSeparateAndRequestReplayed() throws Exception {
        MeasurementCSSLocation selected = location();
        MeasurementCSSLocation peer = location();
        MeasurementGroupStyle orphan = style(selected.getId());
        MeasurementGroupStyle other = style(peer.getId());
        try {
            locations.remove(selected.getId());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // No test-wide transaction may keep the action's queried rows managed.
            assertThat(styles.findByCssId(selected.getId())).extracting(MeasurementGroupStyle::getId)
                    .containsExactly(orphan.getId());
            assertThat(execute("POST", selected.getId(), new MockHttpServletResponse())).isEqualTo("success");
            assertThat(styles.find(orphan.getId())).isNull();
            assertThat(styles.find(other.getId()).getCssId()).isEqualTo(peer.getId());
            assertThat(execute("POST", selected.getId(), new MockHttpServletResponse())).isEqualTo("success");
            assertThat(styles.find(other.getId()).getCssId()).isEqualTo(peer.getId());
            assertThat(locations.find(peer.getId())).isNotNull();
        } finally {
            styles.remove(orphan.getId());
            styles.remove(other.getId());
            locations.remove(selected.getId());
            locations.remove(peer.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "post", "PoSt", "PO\u017fT"})
    void shouldPreserveTheOrphan_whenTheRequestMethodIsUnsafe(String method) throws Exception {
        MeasurementCSSLocation selected = location();
        MeasurementGroupStyle orphan = style(selected.getId());
        try {
            locations.remove(selected.getId());
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(execute(method, selected.getId(), response)).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(styles.find(orphan.getId()).getCssId()).isEqualTo(selected.getId());
        } finally {
            styles.remove(orphan.getId());
            locations.remove(selected.getId());
        }
    }

    private MeasurementCSSLocation location() {
        MeasurementCSSLocation row = new MeasurementCSSLocation();
        row.setLocation("test-" + UUID.randomUUID() + ".css");
        locations.persist(row);
        return row;
    }

    private MeasurementGroupStyle style(int cssId) {
        MeasurementGroupStyle row = new MeasurementGroupStyle();
        row.setGroupName("test-" + UUID.randomUUID());
        row.setCssId(cssId);
        styles.persist(row);
        return row;
    }

    private String execute(String method, int cssId, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/encounter/oscarMeasurements/DeleteMeasurementStyleSheet");
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        try (var spring = mockStatic(SpringUtils.class);
             var servlet = mockStatic(ServletActionContext.class);
             var loggedIn = mockStatic(LoggedInInfo.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(MeasurementGroupStyleDao.class)).thenReturn(styles);
            spring.when(() -> SpringUtils.getBean(MeasurementCSSLocationDao.class)).thenReturn(locations);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
            EctDeleteMeasurementStyleSheet2Action action = new EctDeleteMeasurementStyleSheet2Action();
            action.setDeleteCheckbox(new String[]{String.valueOf(cssId)});
            return action.execute();
        }
    }
}
