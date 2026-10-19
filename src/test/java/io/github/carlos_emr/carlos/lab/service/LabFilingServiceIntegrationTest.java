/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.util.List;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real Hibernate route updates and independent commits; strict-audit failures must roll back. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LabFilingServiceIntegrationTest extends CarlosTestBase {
    @Autowired private ProviderLabRoutingDao routes;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private LabFilingService service;
    private TransactionTemplate tx;
    private MockedStatic<LogAction> audit;
    private static final int REVIEWED = 9835030;
    private static final int OLDER = 9835090;
    private static final int NEWER = 9835010;

    @BeforeEach void fixture() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
            for (int id : List.of(REVIEWED, OLDER, NEWER)) {
                for (String state : List.of("N", "A")) {
                    var row = new ProviderLabRoutingModel(); row.setLabNo(id); row.setLabType("HL7");
                    row.setProviderNo("100002"); row.setStatus(state); row.setComment("Existing " + state + ("N".equals(state) ? "x".repeat(250) : ""));
                    routes.persist(row);
                }
            }
        });
        var providers = mock(ProviderDao.class); var properties = mock(PropertyDao.class);
        var security = mock(SecurityInfoManager.class); var provider = new Provider();
        provider.setFirstName("Synthetic"); provider.setLastName("Reviewer");
        when(info.getLoggedInProvider()).thenReturn(provider); when(info.getLoggedInProviderNo()).thenReturn("100001");
        when(providers.getProvider("100002")).thenReturn(provider);
        when(properties.isActiveBooleanProperty(any(io.github.carlos_emr.carlos.commn.model.Property.PROPERTY_KEY.class), eq("100002"))).thenReturn(true);
        when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(true);
        service = spy(new LabFilingService(routes, properties, providers, security, transactions));
        doReturn(List.of(OLDER)).when(service).olderVersions(REVIEWED);
        audit = mockStatic(LogAction.class);
    }
    @AfterEach void cleanup() {
        if (audit != null) audit.close();
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from ProviderLabRoutingModel where labNo in :ids")
                    .setParameter("ids", List.of(REVIEWED, OLDER, NEWER)).executeUpdate();
            em.createNativeQuery("delete from providerLabRoutingLock where lab_no in (?1, ?2, ?3)")
                    .setParameter(1, REVIEWED).setParameter(2, OLDER).setParameter(3, NEWER).executeUpdate();
        });
    }
    private List<String> states(int id) {
        return tx.execute(status -> routes.findByLabNoAndLabTypeAndProviderNo(id, "HL7", "100002").stream()
                .map(ProviderLabRoutingModel::getStatus).sorted().toList());
    }
    private int file() { return service.fileForProviders(info, REVIEWED, "HL7", List.of("100002"), null, true); }

    @Test void shouldCommitOnlyNewRowsAndRetainComments_whenFilingVersionChain() {
        assertThat(file()).isEqualTo(2);
        assertThat(states(REVIEWED)).containsExactly("A", "F");
        assertThat(states(OLDER)).containsExactly("A", "F");
        assertThat(states(NEWER)).containsExactly("A", "N");
        tx.executeWithoutResult(status -> {
            var rows = routes.findByLabNoAndLabTypeAndProviderNo(REVIEWED, "HL7", "100002");
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.getStatus()).isEqualTo("F");
                assertThat(row.getComment()).startsWith("Existing N" + "x".repeat(250) + "\nFiled by ").contains("on behalf of ");
            });
            assertThat(rows).anySatisfy(row -> {
                assertThat(row.getStatus()).isEqualTo("A"); assertThat(row.getComment()).isEqualTo("Existing A");
            });
        });
        assertThat(file()).isZero();
    }
    @Test void shouldRollBackAllVersions_whenAuditFailsAfterFirstMutation() {
        audit.when(() -> LogAction.addLogStrict(any(), anyString(), anyString(), isNull(), isNull(), anyString()))
                .thenThrow(new IllegalStateException("synthetic audit failure"));
        assertThatThrownBy(this::file).isInstanceOf(IllegalStateException.class);
        assertThat(states(REVIEWED)).containsExactly("A", "N");
        assertThat(states(OLDER)).containsExactly("A", "N");
    }
    @Test void shouldLeaveAllTargetsUnchanged_whenOneTargetIsUnrouted() {
        assertThatThrownBy(() -> service.fileForProviders(info, REVIEWED, "HL7", List.of("100002", "100003"), null, true))
                .isInstanceOf(SecurityException.class);
        assertThat(states(REVIEWED)).containsExactly("A", "N");
        audit.verifyNoInteractions();
    }
}
