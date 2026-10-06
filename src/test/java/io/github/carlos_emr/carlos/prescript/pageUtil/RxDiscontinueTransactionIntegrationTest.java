/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real archive, note and link writes exercise the action's transaction boundary and recovery. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RxDiscontinueTransactionIntegrationTest extends CarlosTestBase {
    private static final AtomicInteger PATIENTS = new AtomicInteger(893000);
    @Autowired private DrugDao drugs;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entityManager;

    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }

    @ParameterizedTest
    @ValueSource(strings = {"note", "link"})
    void shouldRollBackAllWritesAndPermitOneRetry_whenDiscontinuationPersistenceFails(String failure) throws Exception {
        int patient = PATIENTS.incrementAndGet();
        int id = transaction().execute(status -> {
            Drug drug = new Drug();
            drug.setDemographicId(patient);
            drug.setProviderNo("999998");
            drug.setBrandName("Owned rollback fixture");
            drug.setArchived(false);
            drug.setRxDate(new Date());
            drug.setWrittenDate(new Date());
            drug.setCreateDate(new Date());
            drug.setSpecial("1 tab daily");
            entityManager.persist(drug);
            return drug.getId();
        });
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(patient));
        request.setParameter("drugId", String.valueOf(id));
        request.setParameter("reason", "adverse reaction");
        request.setParameter("drugSpecial", "Owned rollback fixture");
        request.getSession().setAttribute("user", "999998");
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(patient);
        bean.setProviderNo("999998");
        RxSessionBeanResolver.register(request.getSession(), bean);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(login), eq("_rx"), eq("u"), isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(login), eq("_rx"), eq("u"), eq(patient))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, patient)).thenReturn(true);
        SecRoleDao roles = mock(SecRoleDao.class);
        SecRole role = mock(SecRole.class);
        when(role.getId()).thenReturn(1);
        when(roles.findByName("doctor")).thenReturn(role);
        CaseManagementManager notes = mock(CaseManagementManager.class);
        AtomicBoolean fail = new AtomicBoolean(true);
        when(notes.saveNoteSimpleReturnID(any())).thenAnswer(invocation -> {
            CaseManagementNote note = invocation.getArgument(0);
            entityManager.persist(note);
            entityManager.flush();
            if (fail.get() && "note".equals(failure)) throw new IllegalStateException("Injected note failure");
            return note.getId();
        });
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean(CaseManagementManager.class)).thenReturn(notes);
        try (var spring = mockStatic(SpringUtils.class);
             var servlet = mockStatic(ServletActionContext.class);
             var loggedIn = mockStatic(LoggedInInfo.class);
             var contexts = mockStatic(WebApplicationContextUtils.class);
             var programs = mockConstruction(EctProgram.class, (program, construction) -> when(program.getProgram("999998")).thenReturn("1"));
             var documents = mockStatic(EDocUtil.class);
             var audit = mockStatic(LogAction.class)) {
            spring.when(() -> SpringUtils.getBean(DrugDao.class)).thenReturn(drugs);
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
            spring.when(() -> SpringUtils.getBean(SecRoleDao.class)).thenReturn(roles);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
            contexts.when(() -> WebApplicationContextUtils.getRequiredWebApplicationContext(any())).thenReturn(context);
            documents.when(EDocUtil::getDmsDateTimeAsDate).thenAnswer(invocation -> new Date());
            documents.when(() -> EDocUtil.addCaseMgmtNoteLink(any())).thenAnswer(invocation -> {
                entityManager.persist(invocation.<CaseManagementNoteLink>getArgument(0));
                entityManager.flush();
                if (fail.get()) throw new IllegalStateException("Injected link failure");
                return null;
            });

            new RxDeleteRx2Action().Discontinue();
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(drugs.find(id).isArchived()).isFalse();
            assertThat(noteCount(patient)).isZero();
            assertThat(linkCount(id)).isZero();
            audit.verifyNoInteractions();

            fail.set(false);
            response.reset();
            new RxDeleteRx2Action().Discontinue();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(drugs.find(id).isArchived()).isTrue();
            assertThat(noteCount(patient)).isEqualTo(1);
            assertThat(linkCount(id)).isEqualTo(1);

            response.reset();
            new RxDeleteRx2Action().Discontinue();
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(noteCount(patient)).isEqualTo(1);
            assertThat(linkCount(id)).isEqualTo(1);
        } finally {
            transaction().executeWithoutResult(status -> {
                entityManager.createQuery("delete from CaseManagementNoteLink where tableName=:table and tableId=:id")
                        .setParameter("table", CaseManagementNoteLink.DRUGS).setParameter("id", (long) id).executeUpdate();
                entityManager.createQuery("delete from CaseManagementNote where demographic_no=:patient")
                        .setParameter("patient", String.valueOf(patient)).executeUpdate();
                entityManager.remove(entityManager.find(Drug.class, id));
            });
        }
    }

    private long noteCount(int patient) {
        return transaction().execute(status -> entityManager.createQuery(
                "select count(n) from CaseManagementNote n where n.demographic_no=:patient", Long.class)
                .setParameter("patient", String.valueOf(patient)).getSingleResult());
    }

    private long linkCount(int id) {
        return transaction().execute(status -> entityManager.createQuery(
                "select count(l) from CaseManagementNoteLink l where l.tableName=:table and l.tableId=:id", Long.class)
                .setParameter("table", CaseManagementNoteLink.DRUGS).setParameter("id", (long) id).getSingleResult());
    }
}
