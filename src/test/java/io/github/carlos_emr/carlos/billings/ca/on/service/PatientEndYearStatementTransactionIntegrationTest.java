/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("mariadb-native")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ResourceLock("LogAction.oscarLogDao")
class PatientEndYearStatementTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private OscarLogDao audit;
    @Autowired private SecurityInfoManager security;
    @Autowired @Qualifier("dataSource") private DataSource source;

    private PatientEndYearStatementService proxy(PatientEndYearStatementService target) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (PatientEndYearStatementService) factory.getProxy();
    }

    @Test
    void shouldCommitLookupAudit_beforeAnyReportRendering() throws Exception {
        String search = "Jasper5692,Audit";
        var demographic = new Demographic();
        demographic.setDemographicNo(569200);
        var dao = mock(DemographicDao.class);
        when(dao.searchDemographic(search)).thenReturn(java.util.List.of(demographic));
        var manager = new DemographicManagerImpl();
        ReflectionTestUtils.setField(manager, "demographicDao", dao);
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        var actor = mock(LoggedInInfo.class);
        var previous = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        try {
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", audit);
            assertThat(proxy(new PatientEndYearStatementService(mock(FixedJasperReportDao.class), manager))
                    .findUniquePatient(actor, null, "Audit", "Jasper5692")).isEqualTo(demographic);
            try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                    "select count(*) from log where action = ? and data = ?")) {
                statement.setString(1, "DemographicManager.searchDemographic");
                statement.setString(2, "searchStr=" + search);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previous);
            try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                    "delete from log where action = ? and data = ?")) {
                statement.setString(1, "DemographicManager.searchDemographic");
                statement.setString(2, "searchStr=" + search);
                statement.executeUpdate();
            }
        }
    }

    @Test
    void shouldSuspendAmbientTransaction_andPropagateRenderingFailure() {
        var dao = mock(FixedJasperReportDao.class);
        var target = spy(new PatientEndYearStatementService(dao, mock(DemographicManager.class)));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new PatientEndYearStatementService.Failure(PatientEndYearStatementService.Reason.PDF_ERROR);
        }).when(target).writePdfTo(any(), any(), any(), any());
        var service = proxy(target);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThatThrownBy(() -> service.writePdfResponse(response, "synthetic", null, null, null))
                    .isInstanceOf(PatientEndYearStatementService.Failure.class);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
        verifyNoInteractions(dao);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }
}
