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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDaoBaseIntegrationTest;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The mapped save action drives the real legacy persistence and real H2 link DAO.
 * @since 2026-10-25
 */
class GroupNoteSelectionPersistenceIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void shouldReplaceAndClearOnlyTheSelectedNotesLinksWithoutRevisingEitherClinicalNote_whenUsingNativeGroupSelection() throws Exception {
        runScenario(false);
    }
    @Test void shouldRefuseForeignRecipientWithoutChangingAnyPersistedLinkOrNote_whenUsingNativeGroupSelection() throws Exception {
        runScenario(true);
    }
    private void runScenario(boolean denied) throws Exception {
        CaseManagementNote source = createNote("1", "Owned group selection café 中文");
        CaseManagementNote other = createNote("1", "Other editor note remains exact");
        GroupNoteDaoImpl groups = new GroupNoteDaoImpl();
        ReflectionTestUtils.setField(groups,"entityManager",entityManager);
        for (var pair : List.of(new int[]{source.getId().intValue(),202},new int[]{other.getId().intValue(),303})) {
            GroupNoteLink link = new GroupNoteLink();link.setNoteId(pair[0]);link.setDemographicNo(pair[1]);link.setActive(true);groups.persist(link);
        }
        entityManager.flush();
        List<?> notesBefore = entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id IN (:a,:b) ORDER BY note_id")
                .setParameter("a",source.getId()).setParameter("b",other.getId()).getResultList();
        List<?> linksBefore = entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE 1=1 ORDER BY id").getResultList();
        var request = new MockHttpServletRequest();request.setMethod("POST");request.setParameter("demographicNo","1");
        request.setParameter("noteId",source.getId().toString());request.setParameter("num_anonymous","0");
        if(denied)request.setParameter("group_client_id",new String[]{"203","404"});else request.setParameter("group_client_id","203");
        ChartProgramContext.remember(request,"7");request.setParameter("chartContext",ChartProgramContext.token(request));
        var response = new MockHttpServletResponse();
        var login = mock(LoggedInInfo.class);when(login.getLoggedInProviderNo()).thenReturn("999998");
        Facility facility=new Facility();facility.setEnableGroupNotes(true);when(login.getCurrentFacility()).thenReturn(facility);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),login);
        var form = new CaseManagementEntryFormBean();form.setCaseNote(source);
        request.getSession().setAttribute(ChartProgramContext.formKey(request,"1"),form);
        var security=mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login),anyString(),anyString(),anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login),anyInt())).thenReturn(true);
        var manager=mock(CaseManagementManager.class);
        when(manager.isClientInProgramDomain(eq("999998"),anyString())).thenReturn(true);
        when(manager.getNote(source.getId().toString())).thenReturn(source);
        when(manager.filterNotes(eq(login),eq("999998"),anyCollection(),eq("7"))).thenReturn(List.of(source));
        var admissions=mock(AdmissionManager.class);var admission=mock(Admission.class);when(admission.getClientId()).thenReturn(203);
        when(admissions.getCurrentAdmissionsByProgramId("7")).thenReturn(List.of(admission));
        var demographics=mock(DemographicDao.class);var recipient=new Demographic();recipient.setDemographicNo(203);recipient.setLastName("Owned Recipient");
        when(demographics.getDemographic("203")).thenReturn(recipient);
        try(MockedStatic<SpringUtils> beans=mockStatic(SpringUtils.class);MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
            beans.when(()->SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class)).thenReturn(transactions);
            beans.when(()->SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            beans.when(()->SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);
            beans.when(()->SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
            beans.when(()->SpringUtils.getBean(DemographicDao.class)).thenReturn(demographics);
            beans.when(()->SpringUtils.getBean(GroupNoteDao.class)).thenReturn(groups);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(response);
            // Legacy static dependencies can have been initialized by another test; restore them exactly afterwards.
            Object oldGroups=ReflectionTestUtils.getField(GroupNoteAction.class,"groupNoteDao");
            Object oldAdmissions=ReflectionTestUtils.getField(GroupNoteAction.class,"admissionManager");
            Object oldDemographics=ReflectionTestUtils.getField(GroupNoteAction.class,"demographicDao");
            try {
                ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",groups);
                ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",admissions);
                ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",demographics);
                var action=new GroupNoteSelect2Action();
                assertThat(action.save()).isEqualTo(denied?"none":"success");entityManager.flush();entityManager.clear();
                if(denied) {
                    assertThat(response.getStatus()).isEqualTo(403);
                    assertThat(entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE 1=1 ORDER BY id").getResultList())
                            .usingRecursiveComparison().isEqualTo(linksBefore);
                    assertThat(form.isGroupNote()).isFalse();
                } else {
                    assertThat(groups.findLinksByNoteId(source.getId().intValue())).extracting(GroupNoteLink::getDemographicNo).containsExactly(203);
                    assertThat(groups.findLinksByNoteId(other.getId().intValue())).extracting(GroupNoteLink::getDemographicNo).containsExactly(303);
                    request.removeParameter("group_client_id");
                    assertThat(new GroupNoteSelect2Action().save()).isEqualTo("success");entityManager.flush();entityManager.clear();
                    assertThat(groups.findLinksByNoteId(source.getId().intValue())).isEmpty();
                    assertThat(groups.findLinksByNoteId(other.getId().intValue())).extracting(GroupNoteLink::getDemographicNo).containsExactly(303);
                }
            } finally {
                ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",oldGroups);
                ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",oldAdmissions);
                ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",oldDemographics);
            }
        }
        assertThat(entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id IN (:a,:b) ORDER BY note_id")
                .setParameter("a",source.getId()).setParameter("b",other.getId()).getResultList())
                .usingRecursiveComparison().isEqualTo(notesBefore);
    }
}
