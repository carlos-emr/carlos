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
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

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
 * {@link ConsultDocsDao}, {@link DemographicDao}) and {@link EformDataManagerImpl}.
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
@Transactional
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
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

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

        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(patient))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(otherPatient))).thenReturn(true);

        EformDataManagerImpl dataManager = new EformDataManagerImpl(security, mock(EFormBrowserPdfService.class));
        ReflectionTestUtils.setField(dataManager, "eFormDataDao", eFormDataDao);

        EctProgramManager programManager = mock(EctProgramManager.class);

        caller = mock(LoggedInInfo.class);
        when(caller.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(caller.getIp()).thenReturn("127.0.0.1");

        service = new EFormInstanceService(security, dataManager, eFormDao, eFormDataDao, eFormValueDao,
                demographicDao, eFormDocsDao, consultDocsDao, programManager);
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
        Map<String, String> revisionValues = new java.util.LinkedHashMap<>(readAfterCreate.values());
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
    @DisplayName("should copy routed lab attachments and re-point consultation and eForm links on revision")
    void shouldCarryAttachmentsAndRepointLinks_whenRevisingInstance() {
        EFormData created = service.create(caller, activeTemplateId, patient, null, Map.of("bp", "120/80"), CONTEXT);
        EFormData parentForm = service.create(caller, activeTemplateId, patient, "Parent", Map.of(), CONTEXT);

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

        EFormDocs parentLink = new EFormDocs(parentForm.getId(), created.getId(), EFormDocs.DOCTYPE_EFORM, PROVIDER_NO);
        eFormDocsDao.persist(parentLink);
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
        assertThat(eFormDocsDao.findByFdidIdDocType(parentForm.getId(), EFormDocs.DOCTYPE_EFORM))
                .extracting(EFormDocs::getDocumentNo).containsExactly(revised.getId());
    }

    @Test
    @DisplayName("should refuse with attachment unavailable when a previously attached lab was re-matched to another patient")
    void shouldRefuseWithAttachmentUnavailable_whenAttachedLabRematchedToAnotherPatient() {
        EFormData created = service.create(caller, activeTemplateId, patient, null, Map.of("bp", "120/80"), CONTEXT);

        PatientLabRouting routing = new PatientLabRouting(9202, "HL7", patient);
        entityManager.persist(routing);
        entityManager.flush();

        EFormDocs attachedLab = new EFormDocs(created.getId(), 9202, EFormDocs.DOCTYPE_LAB, PROVIDER_NO);
        attachedLab.setLabType("HL7");
        eFormDocsDao.persist(attachedLab);
        entityManager.flush();

        // Simulate lab re-matched to another patient after the form was saved.
        routing.setDemographicNo(otherPatient);
        entityManager.flush();
        entityManager.clear();

        Map<String, String> values = Map.of("bp", "130/85");
        assertThatThrownBy(() -> service.revise(caller, created.getId(), null, values, CONTEXT))
                .isInstanceOf(EFormInstanceException.class)
                .extracting(e -> ((EFormInstanceException) e).getReason())
                .isEqualTo(Reason.ATTACHMENT_UNAVAILABLE);
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
