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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException.Reason;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.managers.EformDataManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Database integration tests for {@link EFormInstanceService} exercising the real JPA/Hibernate
 * DAOs ({@link EFormDao}, {@link EFormDataDao}, {@link EFormValueDao}, {@link EFormDocsDao},
 * {@link ConsultDocsDao}, {@link DemographicDao}), {@link EformDataManagerImpl}, and Spring
 * {@link Transactional} proxy boundaries.
 *
 * @since 2026-11-27
 */
@Tag("integration")
@Tag("eform")
@Tag("create")
@Tag("read")
@Tag("update")
@Tag("delete")
@DisplayName("EFormInstanceService database integration")
class EFormInstanceServiceIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final String CONTEXT = "/carlos";
    private static final String TEMPLATE_HTML = "<html><body><form>"
            + "<input type=\"text\" name=\"bp\" value=\"\">"
            + "<textarea name=\"note\">template default</textarea>"
            + "<select name=\"status\">"
            + "<option>Normal</option>"
            + "<option value=\"\">Not assessed</option>"
            + "</select>"
            + "<input type=\"checkbox\" name=\"smoker\" value=\"yes\" checked>"
            + "</form></body></html>";

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

    private EFormInstanceService buildTransactionalService(int... allowedPatients) {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), anyString(), nullable(String.class)))
                .thenReturn(true);
        for (int demographicNo : allowedPatients) {
            when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(demographicNo))).thenReturn(true);
        }

        EformDataManagerImpl dataManager = new EformDataManagerImpl(security, mock(EFormBrowserPdfService.class));
        ReflectionTestUtils.setField(dataManager, "eFormDataDao", eFormDataDao);

        EFormInstanceService target = new EFormInstanceService(security, dataManager, eFormDao, eFormDataDao,
                eFormValueDao, demographicDao, eFormDocsDao, consultDocsDao, mock(EctProgramManager.class));

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (EFormInstanceService) proxyFactory.getProxy();
    }

    private LoggedInInfo buildCaller() {
        LoggedInInfo caller = mock(LoggedInInfo.class);
        when(caller.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(caller.getIp()).thenReturn("127.0.0.1");
        return caller;
    }

    private int persistPatient(String firstName, String lastName) {
        Demographic demographic = new Demographic();
        demographic.setFirstName(firstName);
        demographic.setLastName(lastName);
        demographic.setSex("U");
        demographic.setProviderNo(PROVIDER_NO);
        entityManager.persist(demographic);
        entityManager.flush();
        return demographic.getDemographicNo();
    }

    private int persistTemplate(String name, boolean showLatestFormOnly) {
        EForm template = new EForm();
        template.setFormName(name);
        template.setFileName(name + ".html");
        template.setSubject("Default subject");
        template.setFormHtml(TEMPLATE_HTML);
        template.setCurrent(true);
        template.setShowLatestFormOnly(showLatestFormOnly);
        template.setPatientIndependent(false);
        template.setCreator(PROVIDER_NO);
        template.setFormDate(new Date());
        template.setFormTime(new Date());
        eFormDao.persist(template);
        entityManager.flush();
        hibernateTemplate.flush();
        return template.getId();
    }

    @Nested
    @Transactional
    @DisplayName("lifecycle and attachments")
    class LifecycleAndAttachments {

        private EFormInstanceService service;
        private LoggedInInfo caller;
        private int patient;
        private int otherPatient;
        private int activeTemplateId;

        @BeforeEach
        void setUp() {
            patient = persistPatient("FAKE-EForm", "FAKE-PatientOne");
            otherPatient = persistPatient("FAKE-EForm", "FAKE-PatientTwo");
            activeTemplateId = persistTemplate("FAKE-InstanceTemplate", false);
            caller = buildCaller();
            service = buildTransactionalService(patient, otherPatient);
        }

        @Test
        @DisplayName("should persist, read back, revise with implicit select clear, and retire an instance")
        void shouldRoundTripLifecycle_againstDatabase() {
            EFormData created = service.create(caller, activeTemplateId, patient, "Initial",
                    Map.of("bp", "122/78", "status", "", "smoker", ""), CONTEXT);
            entityManager.flush();
            entityManager.clear();

            EFormInstanceService.Instance readAfterCreate = service.get(caller, created.getId());
            assertThat(readAfterCreate.data().isCurrent()).isTrue();
            assertThat(readAfterCreate.data().getSubject()).isEqualTo("Initial");
            assertThat(readAfterCreate.values())
                    .containsEntry("bp", "122/78")
                    .containsEntry("status", "")
                    .containsEntry("smoker", "")
                    .doesNotContainKey("note");

            // Sending GET's values back keeps both cleared fields cleared and replaces bp.
            Map<String, String> revisionValues = new LinkedHashMap<>(readAfterCreate.values());
            revisionValues.put("bp", "118/74");
            EFormData revised = service.revise(caller, created.getId(), "Revised", revisionValues, CONTEXT);
            entityManager.flush();
            entityManager.clear();

            assertThat(revised.getId()).isNotEqualTo(created.getId());
            assertThat(eFormDataDao.find(created.getId()).isCurrent()).isFalse();
            assertThat(eFormDataDao.find(revised.getId()).isCurrent()).isTrue();
            assertThat(Jsoup.parse(revised.getFormData()).select("select[name=status] option[selected]"))
                    .extracting(option -> option.attr("value")).containsExactly("");
            assertThat(eFormValueDao.findByFormDataId(revised.getId()))
                    .extracting(v -> v.getVarName() + "=" + v.getVarValue())
                    .containsExactly("bp=118/74");

            service.delete(caller, revised.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(eFormDataDao.find(revised.getId()).isCurrent()).isFalse();
            assertThat(service.get(caller, revised.getId()).data().isCurrent()).isFalse();
        }

        @Test
        @DisplayName("should copy routed lab attachments and repoint only current owning eForm and consultation links on revision")
        void shouldCarryAttachmentsAndRepointLinks_whenRevisingInstance() {
            EFormData created = service.create(caller, activeTemplateId, patient, null, Map.of("bp", "120/80"), CONTEXT);
            EFormData currentParentForm = service.create(caller, activeTemplateId, patient, "CurrentParent", Map.of(), CONTEXT);
            EFormData retiredParentForm = service.create(caller, activeTemplateId, patient, "RetiredParent", Map.of(), CONTEXT);

            ConsultationRequest consult = new ConsultationRequest();
            consult.setDemographicId(patient);
            consult.setProviderNo(PROVIDER_NO);
            consult.setReferralDate(new Date());
            consult.setServiceId(1);
            consult.setStatus("1");
            entityManager.persist(consult);

            entityManager.persist(new PatientLabRouting(9101, "HL7", patient));
            entityManager.flush();

            EFormDocs attachedLab = new EFormDocs(created.getId(), 9101, EFormDocs.DOCTYPE_LAB, PROVIDER_NO);
            attachedLab.setLabType("HL7");
            eFormDocsDao.persist(attachedLab);

            ConsultDocs consultLink = new ConsultDocs(consult.getId(), created.getId(), ConsultDocs.DOCTYPE_EFORM, PROVIDER_NO);
            consultDocsDao.persist(consultLink);

            EFormDocs currentParentLink = new EFormDocs(currentParentForm.getId(), created.getId(), EFormDocs.DOCTYPE_EFORM, PROVIDER_NO);
            eFormDocsDao.persist(currentParentLink);

            EFormDocs retiredParentLink = new EFormDocs(retiredParentForm.getId(), created.getId(), EFormDocs.DOCTYPE_EFORM, PROVIDER_NO);
            eFormDocsDao.persist(retiredParentLink);
            service.delete(caller, retiredParentForm.getId());
            entityManager.flush();
            entityManager.clear();

            EFormData revised = service.revise(caller, created.getId(), null, Map.of("bp", "124/82"), CONTEXT);
            entityManager.flush();
            entityManager.clear();

            List<EFormDocs> revisedLabs = eFormDocsDao.findByFdid(revised.getId());
            assertThat(revisedLabs).singleElement().satisfies(lab -> {
                assertThat(lab.getDocumentNo()).isEqualTo(9101);
                assertThat(lab.getDocType()).isEqualTo(EFormDocs.DOCTYPE_LAB);
                assertThat(lab.getLabType()).isEqualTo("HL7");
            });
            assertThat(eFormDocsDao.findByFdid(created.getId())).hasSize(1);
            assertThat(consultDocsDao.findByRequestIdDocType(consult.getId(), ConsultDocs.DOCTYPE_EFORM))
                    .extracting(ConsultDocs::getDocumentNo).containsExactly(revised.getId());
            assertThat(eFormDocsDao.findByFdidIdDocType(currentParentForm.getId(), EFormDocs.DOCTYPE_EFORM))
                    .extracting(EFormDocs::getDocumentNo).containsExactly(revised.getId());
            // Retired parent eForm keeps its historical attachment pointing at the original child row.
            assertThat(eFormDocsDao.findByFdidIdDocType(retiredParentForm.getId(), EFormDocs.DOCTYPE_EFORM))
                    .extracting(EFormDocs::getDocumentNo).containsExactly(created.getId());
        }

        @Test
        @DisplayName("should refuse revising an older current row of a latest-only template while allowing the newest row")
        void shouldRefuseOlderRowAsSuperseded_whenLatestOnlyTemplateHasNewerCurrentRow() {
            int latestOnlyFid = persistTemplate("FAKE-LatestOnlyTemplate", true);
            EFormData older = service.create(caller, latestOnlyFid, patient, "First", Map.of("bp", "110/70"), CONTEXT);
            EFormData newer = service.create(caller, latestOnlyFid, patient, "Second", Map.of("bp", "120/80"), CONTEXT);
            entityManager.flush();
            entityManager.clear();

            Map<String, String> values = Map.of("bp", "115/75");
            assertThatThrownBy(() -> service.revise(caller, older.getId(), null, values, CONTEXT))
                    .isInstanceOf(EFormInstanceException.class)
                    .extracting(e -> ((EFormInstanceException) e).getReason())
                    .isEqualTo(Reason.SUPERSEDED);

            EFormData revisedNewest = service.revise(caller, newer.getId(), null, values, CONTEXT);
            entityManager.flush();
            entityManager.clear();

            assertThat(eFormDataDao.find(older.getId()).isCurrent()).isTrue();
            assertThat(eFormDataDao.find(newer.getId()).isCurrent()).isFalse();
            assertThat(eFormDataDao.find(revisedNewest.getId()).isCurrent()).isTrue();
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Spring transactional rollback")
    class TransactionalRollback {

        @Test
        @DisplayName("should roll back the revision, retirement, copied attachments and audit entry when an attached lab was re-matched")
        void shouldRollBackRevisionAndAudit_whenAttachedLabRematchedToAnotherPatient() {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            LoggedInInfo caller = buildCaller();

            int[] fixtureIds = new int[4]; // [patient, otherPatient, templateId, createdId]
            int labNo = 9202;
            try {
                // Commit prerequisite fixtures in their own transaction.
                tx.executeWithoutResult(status -> {
                    fixtureIds[0] = persistPatient("FAKE-Rollback", "FAKE-PatientOne");
                    fixtureIds[1] = persistPatient("FAKE-Rollback", "FAKE-PatientTwo");
                    fixtureIds[2] = persistTemplate("FAKE-RollbackTemplate", false);
                });

                EFormInstanceService service = buildTransactionalService(fixtureIds[0], fixtureIds[1]);

                // Commit the initial instance via the Spring-proxied service.
                EFormData created = service.create(caller, fixtureIds[2], fixtureIds[0], "Initial",
                        Map.of("bp", "120/80"), CONTEXT);
                fixtureIds[3] = created.getId();

                // Attach a routed lab and then re-match that lab to another patient in committed state.
                tx.executeWithoutResult(status -> {
                    PatientLabRouting routing = new PatientLabRouting(labNo, "HL7", fixtureIds[0]);
                    entityManager.persist(routing);
                    entityManager.flush();

                    EFormDocs attachedLab = new EFormDocs(fixtureIds[3], labNo, EFormDocs.DOCTYPE_LAB, PROVIDER_NO);
                    attachedLab.setLabType("HL7");
                    eFormDocsDao.persist(attachedLab);
                    entityManager.flush();

                    routing.setDemographicNo(fixtureIds[1]);
                    entityManager.flush();
                });

                // Run revise outside any test-managed transaction so EFormInstanceService's @Transactional
                // boundary owns the commit/rollback.
                Map<String, String> values = Map.of("bp", "130/85");
                assertThatThrownBy(() -> service.revise(caller, fixtureIds[3], "RolledBack", values, CONTEXT))
                        .isInstanceOf(EFormInstanceException.class)
                        .extracting(e -> ((EFormInstanceException) e).getReason())
                        .isEqualTo(Reason.ATTACHMENT_UNAVAILABLE);

                // Verify committed database state in a fresh read transaction: nothing from the failed revision survived.
                tx.executeWithoutResult(status -> {
                    EFormData original = eFormDataDao.find(fixtureIds[3]);
                    assertThat(original).isNotNull();
                    assertThat(original.isCurrent()).isTrue();
                    assertThat(original.getSubject()).isEqualTo("Initial");

                    assertThat(eFormDataDao.findByDemographicIdAndFormId(fixtureIds[0], fixtureIds[2]))
                            .extracting(EFormData::getId)
                            .containsExactly(fixtureIds[3]);

                    assertThat(eFormValueDao.findByFormDataId(fixtureIds[3]))
                            .extracting(v -> v.getVarName() + "=" + v.getVarValue())
                            .containsExactly("bp=120/80");

                    Number totalValues = (Number) entityManager
                            .createNativeQuery("SELECT COUNT(*) FROM eform_values WHERE demographic_no = :demo")
                            .setParameter("demo", fixtureIds[0])
                            .getSingleResult();
                    assertThat(totalValues.longValue()).isEqualTo(1L);

                    assertThat(eFormDocsDao.findByFdid(fixtureIds[3])).hasSize(1);

                    Number updateAudits = (Number) entityManager
                            .createNativeQuery("SELECT COUNT(*) FROM log WHERE demographic_no = :demo AND action = 'update' AND content = 'eform'")
                            .setParameter("demo", fixtureIds[0])
                            .getSingleResult();
                    assertThat(updateAudits.longValue()).isZero();
                });
            } finally {
                // Clean up committed rows so no state leaks to other tests sharing the H2 database.
                tx.executeWithoutResult(status -> {
                    if (fixtureIds[0] > 0) {
                        entityManager.createNativeQuery("DELETE FROM EFormDocs WHERE fdid IN (SELECT fdid FROM eform_data WHERE demographic_no = :demo)")
                                .setParameter("demo", fixtureIds[0]).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM eform_values WHERE demographic_no = :demo")
                                .setParameter("demo", fixtureIds[0]).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM eform_data WHERE demographic_no = :demo")
                                .setParameter("demo", fixtureIds[0]).executeUpdate();
                        entityManager.createNativeQuery("DELETE FROM log WHERE demographic_no = :demo")
                                .setParameter("demo", fixtureIds[0]).executeUpdate();
                    }
                    entityManager.createNativeQuery("DELETE FROM patientLabRouting WHERE lab_no = :labNo AND lab_type = 'HL7'")
                            .setParameter("labNo", labNo).executeUpdate();
                    if (fixtureIds[2] > 0) {
                        entityManager.createNativeQuery("DELETE FROM eform WHERE fid = :fid")
                                .setParameter("fid", fixtureIds[2]).executeUpdate();
                    }
                    if (fixtureIds[0] > 0) {
                        entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no = :demo")
                                .setParameter("demo", fixtureIds[0]).executeUpdate();
                    }
                    if (fixtureIds[1] > 0) {
                        entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no = :demo")
                                .setParameter("demo", fixtureIds[1]).executeUpdate();
                    }
                });
            }
        }
    }
}
