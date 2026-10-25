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

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDaoBaseIntegrationTest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual anonymous helper and real H2 demographics, admissions and links; external service calls are controlled.
 * @since 2026-10-25
 */
class GroupNoteSelectionAnonymousIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired private PlatformTransactionManager transactions;

    @ParameterizedTest @ValueSource(booleans={false,true}) @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void shouldKeepAnonymousAdmissionAndSharingConsistent_whenCreationSucceedsOrTheSecondAdmissionFails(boolean fail) throws Exception {
        var transaction=new TransactionTemplate(transactions);
        var groups=new GroupNoteDaoImpl();ReflectionTestUtils.setField(groups,"entityManager",entityManager);
        var program=transaction.execute(status->{var value=new Program();value.setName("Synthetic anonymous group");value.setType("service");
            value.setProgramStatus("active");value.setMaxAllowed(10);entityManager.persist(value);entityManager.flush();return value;});
        var source=transaction.execute(status->{var value=createNote("1","Synthetic anonymous group source café 中文");
            var old=new GroupNoteLink();old.setNoteId(value.getId().intValue());old.setDemographicNo(202);old.setActive(true);groups.persist(old);entityManager.flush();return value;});
        var created=new ArrayList<Integer>();
        try {
            List<?> demographicsBefore=entityManager.createNativeQuery("SELECT * FROM demographic WHERE 1=1 ORDER BY demographic_no").getResultList();
            List<?> admissionsBefore=entityManager.createNativeQuery("SELECT * FROM admission WHERE 1=1 ORDER BY am_id").getResultList();
            List<?> linksBefore=entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:note ORDER BY id").setParameter("note",source.getId().intValue()).getResultList();
            List<?> sourceBefore=entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id=:note").setParameter("note",source.getId()).getResultList();
            var request=new MockHttpServletRequest();request.setMethod("POST");request.setParameter("demographicNo","1");
            request.setParameter("noteId",source.getId().toString());request.setParameter("num_anonymous","2");
            ChartProgramContext.remember(request,program.getId().toString());request.setParameter("chartContext",ChartProgramContext.token(request));
            var login=mock(LoggedInInfo.class);when(login.getLoggedInProviderNo()).thenReturn("999998");
            var facility=new Facility();facility.setEnableGroupNotes(true);when(login.getCurrentFacility()).thenReturn(facility);
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),login);
            var form=new CaseManagementEntryFormBean();form.setNoteId(source.getId().toString());form.setCaseNote(source);
            String key=ChartProgramContext.formKey(request,"1");request.getSession().setAttribute(key,form);
            var security=mock(SecurityInfoManager.class);when(security.hasPrivilege(eq(login),anyString(),anyString(),anyString())).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(eq(login),anyInt())).thenReturn(true);
            var manager=mock(CaseManagementManager.class);when(manager.isClientInProgramDomain(eq("999998"),anyString())).thenReturn(true);
            when(manager.getNote(source.getId().toString())).thenReturn(source);
            when(manager.filterNotes(eq(login),eq("999998"),anyCollection(),eq(program.getId().toString()))).thenReturn(List.of(source));
            var clients=mock(ClientManager.class);
            doAnswer(call->{var client=(Demographic)call.getArgument(0);entityManager.persist(client);entityManager.flush();created.add(client.getDemographicNo());return null;})
                    .when(clients).saveClient(any(Demographic.class));
            var programs=mock(ProgramManager.class);when(programs.getProgram(program.getId())).thenReturn(program);
            var admissions=mock(AdmissionManager.class);when(admissions.getCurrentAdmissionsByProgramId(program.getId().toString())).thenReturn(List.of());
            var attempts=new AtomicInteger();
            doAnswer(call->{
                if(fail&&attempts.incrementAndGet()==2)throw new IllegalStateException("Owned second admission failure probe");
                var admission=new Admission();admission.setClientId(call.getArgument(0));admission.setProviderNo(call.getArgument(1));
                admission.setProgramId(program.getId());admission.setAdmissionDate(new java.util.Date());admission.setAdmissionStatus(Admission.STATUS_CURRENT);
                admission.setAdmissionNotes(call.getArgument(4));entityManager.persist(admission);entityManager.flush();return null;
            }).when(admissions).processAdmission(anyInt(),eq("999998"),eq(program),anyString(),anyString());
            when(admissions.getAdmissions(anyInt())).thenAnswer(call->entityManager.createQuery("from Admission where clientId=:patient",Admission.class)
                    .setParameter("patient",call.getArgument(0)).getResultList());
            doAnswer(call->{entityManager.merge((Admission)call.getArgument(0));return null;}).when(admissions).saveAdmission(any(Admission.class));
            var demographics=mock(DemographicDao.class);when(demographics.getDemographic(anyString())).thenAnswer(call->entityManager.find(Demographic.class,Integer.valueOf((String)call.getArgument(0))));
            var response=new MockHttpServletResponse();
            try(MockedStatic<SpringUtils> beans=mockStatic(SpringUtils.class);MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
                beans.when(()->SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
                beans.when(()->SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
                beans.when(()->SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);
                beans.when(()->SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
                beans.when(()->SpringUtils.getBean(DemographicDao.class)).thenReturn(demographics);
                beans.when(()->SpringUtils.getBean(GroupNoteDao.class)).thenReturn(groups);
                beans.when(()->SpringUtils.getBean(ClientManager.class)).thenReturn(clients);
                beans.when(()->SpringUtils.getBean(ProgramManager.class)).thenReturn(programs);
                servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(response);
                Object oldGroups=ReflectionTestUtils.getField(GroupNoteAction.class,"groupNoteDao"),oldAdmissions=ReflectionTestUtils.getField(GroupNoteAction.class,"admissionManager"),oldDemographics=ReflectionTestUtils.getField(GroupNoteAction.class,"demographicDao");
                try {
                    ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",groups);ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",admissions);ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",demographics);
                    if(fail) {
                        assertThatThrownBy(()->new GroupNoteSelect2Action().save()).isInstanceOf(IllegalStateException.class)
                                .hasMessage("Anonymous group client admission failed").hasRootCauseMessage("Owned second admission failure probe");
                        assertThat(created).hasSize(2);
                        assertThat(entityManager.createNativeQuery("SELECT * FROM demographic WHERE 1=1 ORDER BY demographic_no").getResultList()).usingRecursiveComparison().isEqualTo(demographicsBefore);
                        assertThat(entityManager.createNativeQuery("SELECT * FROM admission WHERE 1=1 ORDER BY am_id").getResultList()).usingRecursiveComparison().isEqualTo(admissionsBefore);
                        assertThat(entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:note ORDER BY id").setParameter("note",source.getId().intValue()).getResultList()).usingRecursiveComparison().isEqualTo(linksBefore);
                        assertThat(form.isGroupNote()).isFalse();
                    } else {
                        assertThat(new GroupNoteSelect2Action().save()).isEqualTo("success");assertThat(created).hasSize(2);
                        assertThat(groups.findLinksByNoteId(source.getId().intValue())).hasSize(2).allMatch(GroupNoteLink::isAnonymous);
                        for(int id:created) {
                            var client=entityManager.find(Demographic.class,id);assertThat(client.getAnonymous()).isEqualTo("one-time-anonymous");
                            assertThat(client.getFirstName()).isEqualTo("Anonymous");assertThat(client.getYearOfBirth()).isEqualTo("1800");
                            assertThat(admissions.getAdmissions(id)).hasSize(1).allMatch(value->value.getProgramId().equals(program.getId())&&Admission.STATUS_CURRENT.equals(value.getAdmissionStatus()));
                        }
                        request.setParameter("num_anonymous","0");assertThat(new GroupNoteSelect2Action().save()).isEqualTo("success");
                        assertThat(groups.findLinksByNoteId(source.getId().intValue())).isEmpty();
                        for(int id:created) {assertThat(entityManager.find(Demographic.class,id).getPatientStatus()).isEqualTo("DL");
                            assertThat(admissions.getAdmissions(id)).hasSize(1).allMatch(value->Admission.STATUS_DISCHARGED.equals(value.getAdmissionStatus()));}
                    }
                } finally {ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",oldGroups);ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",oldAdmissions);ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",oldDemographics);}
            }
            assertThat(entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id=:note").setParameter("note",source.getId()).getResultList()).usingRecursiveComparison().isEqualTo(sourceBefore);
        } finally {
            transaction.executeWithoutResult(status->{
                entityManager.createNativeQuery("DELETE FROM GroupNoteLink WHERE noteId=:note").setParameter("note",source.getId().intValue()).executeUpdate();
                for(int id:created){entityManager.createNativeQuery("DELETE FROM admission WHERE client_id=:patient").setParameter("patient",id).executeUpdate();entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no=:patient").setParameter("patient",id).executeUpdate();}
                entityManager.createNativeQuery("DELETE FROM casemgmt_note WHERE note_id=:note").setParameter("note",source.getId()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM program WHERE id=:program").setParameter("program",program.getId()).executeUpdate();
            });
        }
    }
}
