/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.EformDataManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Spring transaction and real-DAO integration tests for {@link EFormInstanceService}.
 *
 * <p>Uses {@link Propagation#NOT_SUPPORTED} with Spring's real {@link TransactionInterceptor}
 * reading {@code @Transactional} metadata from {@link EFormInstanceService}, proving actual
 * commit/rollback semantics against the database and real {@link EFormDocsDao} behavior for
 * unrouted or reassigned lab attachments.</p>
 *
 * @since 2026-11-27
 */
@Tag("integration")
@Tag("eform")
@DisplayName("EFormInstanceService transactional integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EFormInstanceServiceIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final String TEMPLATE_HTML =
            "<html><body><form><input type=\"text\" name=\"bp\" value=\"\"></form></body></html>";

    @Autowired
    private EFormDao eFormDao;
    @Autowired
    private EFormDataDao eFormDataDao;
    @Autowired
    private EFormValueDao eFormValueDao;
    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    private EFormDocsDao eFormDocsDao;
    @Autowired
    private ConsultDocsDao consultDocsDao;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private TransactionTemplate tx;
    private EFormInstanceService transactionalService;
    private LoggedInInfo caller;
    private Integer patientId;
    private Integer otherPatientId;
    private Integer templateId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Demographic patient = new Demographic();
            patient.setFirstName("FAKE-EFormTx");
            patient.setLastName("FAKE-PatientA");
            patient.setSex("U");
            patient.setProviderNo(PROVIDER_NO);
            entityManager.persist(patient);

            Demographic other = new Demographic();
            other.setFirstName("FAKE-EFormTx");
            other.setLastName("FAKE-PatientB");
            other.setSex("U");
            other.setProviderNo(PROVIDER_NO);
            entityManager.persist(other);

            EForm template = new EForm();
            template.setFormName("FAKE-TxTemplate-" + System.nanoTime());
            template.setFileName("fake-tx.html");
            template.setSubject("Tx Subject");
            template.setFormHtml(TEMPLATE_HTML);
            template.setCreator(PROVIDER_NO);
            template.setCurrent(true);
            template.setShowLatestFormOnly(false);
            template.setPatientIndependent(false);
            template.setFormDate(new Date());
            template.setFormTime(new Date());
            eFormDao.persist(template);
            entityManager.flush();

            patientId = patient.getDemographicNo();
            otherPatientId = other.getDemographicNo();
            templateId = template.getId();
        });

        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt())).thenReturn(true);

        EformDataManagerImpl eformDataManager = new EformDataManagerImpl(security, mock(EFormBrowserPdfService.class));
        ReflectionTestUtils.setField(eformDataManager, "eFormDataDao", eFormDataDao);

        EctProgramManager ectProgramManager = mock(EctProgramManager.class);
        when(ectProgramManager.getProgramBeans(anyString(), nullable(Integer.class))).thenReturn(List.of());

        EFormInstanceService target = new EFormInstanceService(
                security, eformDataManager, eFormDao, eFormDataDao, eFormValueDao,
                demographicDao, eFormDocsDao, consultDocsDao, ectProgramManager);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        transactionalService = (EFormInstanceService) proxyFactory.getProxy();

        caller = mock(LoggedInInfo.class);
        when(caller.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            if (patientId != null) {
                List<Integer> fdids = entityManager.createQuery(
                                "select d.id from EFormData d where d.demographicId in (:ids)", Integer.class)
                        .setParameter("ids", List.of(patientId, otherPatientId))
                        .getResultList();
                if (!fdids.isEmpty()) {
                    entityManager.createQuery("delete from EFormDocs x where x.fdid in (:fdids)")
                            .setParameter("fdids", fdids).executeUpdate();
                    entityManager.createQuery("delete from EFormValue v where v.formDataId in (:fdids)")
                            .setParameter("fdids", fdids).executeUpdate();
                    entityManager.createQuery("delete from EFormData d where d.id in (:fdids)")
                            .setParameter("fdids", fdids).executeUpdate();
                }
                entityManager.createQuery("delete from PatientLabRouting r where r.demographicNo in (:ids)")
                        .setParameter("ids", List.of(patientId, otherPatientId)).executeUpdate();
                entityManager.createQuery("delete from Demographic d where d.demographicNo in (:ids)")
                        .setParameter("ids", List.of(patientId, otherPatientId)).executeUpdate();
            }
            if (templateId != null) {
                entityManager.createQuery("delete from EForm e where e.id = :id")
                        .setParameter("id", templateId).executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("should roll back persisted eForm data and values when chart writes fail during create")
    void shouldRollBackCreate_whenChartWritesFailAfterPersist() {
        try (var audit = mockStatic(LogAction.class);
             var util = mockStatic(EFormUtil.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            util.when(() -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("Injected chart-write failure"));

            assertThatThrownBy(() -> transactionalService.create(
                    caller, templateId, patientId, "Rollback Subject", Map.of("bp", "120/80"), "/carlos"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Injected chart-write failure");
        }

        // Committed database state must have neither eform_data nor eform_values rows.
        tx.executeWithoutResult(status -> {
            List<EFormData> rows = eFormDataDao.findByDemographicIdAndFormId(patientId, templateId);
            assertThat(rows).isEmpty();
            Long valueCount = entityManager.createQuery(
                            "select count(v) from EFormValue v where v.demographicId = :demo", Long.class)
                    .setParameter("demo", patientId).getSingleResult();
            assertThat(valueCount).isZero();
        });
    }

    @Test
    @DisplayName("should revise and copy lab attachment when lab routing was reassigned to another patient")
    void shouldReviseAndCopyLabAttachment_whenLabNoLongerRoutedToPatient() {
        Integer initialFdid;
        try (var audit = mockStatic(LogAction.class);
             var util = mockStatic(EFormUtil.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            util.when(() -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> null);

            EFormData created = transactionalService.create(
                    caller, templateId, patientId, "Initial Subject", Map.of("bp", "120/80"), "/carlos");
            initialFdid = created.getId();

            // Attach a lab while routed to patientId, then reassign the lab's routing to otherPatientId.
            tx.executeWithoutResult(status -> {
                PatientLabRouting routing = new PatientLabRouting(884201, "HL7", patientId);
                entityManager.persist(routing);
                entityManager.flush();

                EFormDocs labAttachment = new EFormDocs(initialFdid, 884201, EFormDocs.DOCTYPE_LAB, PROVIDER_NO);
                labAttachment.setLabType("HL7");
                eFormDocsDao.persist(labAttachment);
                entityManager.flush();

                routing.setDemographicNo(otherPatientId);
                entityManager.merge(routing);
                entityManager.flush();
            });

            // Revising must succeed despite the reassigned lab and preserve the historical link.
            EFormData revised = transactionalService.revise(
                    caller, initialFdid, "Revised Subject", Map.of("bp", "130/85"), "/carlos");

            assertThat(revised.getId()).isNotNull().isNotEqualTo(initialFdid);

            tx.executeWithoutResult(status -> {
                EFormData oldRow = eFormDataDao.find(initialFdid);
                EFormData newRow = eFormDataDao.find(revised.getId());
                assertThat(oldRow.isCurrent()).isFalse();
                assertThat(newRow.isCurrent()).isTrue();
                assertThat(eFormDocsDao.findByFdid(revised.getId()))
                        .extracting(EFormDocs::getDocType, EFormDocs::getDocumentNo, EFormDocs::getLabType)
                        .containsExactly(tuple(EFormDocs.DOCTYPE_LAB, 884201, "HL7"));
                // Patient-filtered lab lookup still omits the lab reassigned to another patient.
                assertThat(eFormDocsDao.findLabs(revised.getId())).isEmpty();
            });
        }
    }

    @Test
    @DisplayName("should roll back revision and leave original row current when chart writes fail")
    void shouldRollBackRevise_whenChartWritesFail() {
        Integer initialFdid;
        try (var audit = mockStatic(LogAction.class);
             var util = mockStatic(EFormUtil.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            util.when(() -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> null);

            initialFdid = transactionalService.create(
                    caller, templateId, patientId, "Initial Subject", Map.of("bp", "120/80"), "/carlos").getId();

            util.when(() -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("Injected revise chart-write failure"));

            assertThatThrownBy(() -> transactionalService.revise(
                    caller, initialFdid, "Failed Revision", Map.of("bp", "140/90"), "/carlos"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Injected revise chart-write failure");
        }

        tx.executeWithoutResult(status -> {
            List<EFormData> rows = entityManager.createQuery(
                            "select d from EFormData d where d.demographicId = :demo", EFormData.class)
                    .setParameter("demo", patientId).getResultList();
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.getId()).isEqualTo(initialFdid);
                assertThat(row.isCurrent()).isTrue();
                assertThat(row.getSubject()).isEqualTo("Initial Subject");
            });
        });
    }
}
