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
import java.util.List;
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

/** A committed baseline and real independent JPA transaction prove rollback after link retirement.
 * @since 2026-10-25
 */
class GroupNoteSelectionRollbackIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired private PlatformTransactionManager transactions;

    @ParameterizedTest @ValueSource(strings={"replacement-failure","record-exclusion-interleave"})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void shouldPreserveRowsAndSession_whenReplacementOrInterleavedAuthorizationFails(String mode) throws Exception {
        boolean interleave="record-exclusion-interleave".equals(mode);
        var transaction=new TransactionTemplate(transactions);
        var groups=new GroupNoteDaoImpl();ReflectionTestUtils.setField(groups,"entityManager",entityManager);
        CaseManagementNote source=transaction.execute(status->{
            var note=createNote("1","Synthetic group rollback café 中文");
            var old=new GroupNoteLink();old.setNoteId(note.getId().intValue());old.setDemographicNo(202);old.setActive(true);
            groups.persist(old);entityManager.flush();return note;
        });
        try {
            List<?> before=entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:note ORDER BY id")
                    .setParameter("note",source.getId().intValue()).getResultList();
            List<?> noteBefore=entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id=:note")
                    .setParameter("note",source.getId()).getResultList();
            var proxyFactory=new org.springframework.aop.framework.ProxyFactory(groups);
            proxyFactory.setInterfaces(GroupNoteDao.class);
            proxyFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                    new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            GroupNoteDao transactional=(GroupNoteDao)proxyFactory.getProxy();
            GroupNoteDao observed=mock(GroupNoteDao.class,org.mockito.AdditionalAnswers.delegatesTo(transactional));
            if(!interleave)doAnswer(call->{
                // Force the dirty old-link update into H2 before the simulated replacement failure.
                if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())entityManager.flush();
                Number retired=(Number)entityManager.createNativeQuery("SELECT COUNT(*) FROM GroupNoteLink WHERE noteId=:note AND active=false")
                        .setParameter("note",source.getId().intValue()).getSingleResult();
                assertThat(retired.intValue()).isEqualTo(1);
                throw new IllegalStateException("Owned group replacement rollback probe");
            }).when(observed).persist(any(GroupNoteLink.class));
            var added=new java.util.concurrent.atomic.AtomicReference<List<?>>();
            if(interleave){
                var lookups=new java.util.concurrent.atomic.AtomicInteger();
                doAnswer(call->{
                    if(lookups.incrementAndGet()==2){
                        var concurrent=new TransactionTemplate(transactions);
                        concurrent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        concurrent.executeWithoutResult(status->{
                            var foreign=new GroupNoteLink();foreign.setNoteId(source.getId().intValue());foreign.setDemographicNo(404);foreign.setActive(true);
                            groups.persist(foreign);entityManager.flush();
                        });
                        added.set(entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:note AND demographicNo=404")
                                .setParameter("note",source.getId().intValue()).getResultList());
                    }
                    return transactional.findLinksByNoteId(call.getArgument(0));
                }).when(observed).findLinksByNoteId(anyInt());
            }
            var request=new MockHttpServletRequest();request.setMethod("POST");request.setParameter("demographicNo","1");
            request.setParameter("noteId",source.getId().toString());request.setParameter("num_anonymous","0");request.setParameter("group_client_id","203");
            ChartProgramContext.remember(request,"7");request.setParameter("chartContext",ChartProgramContext.token(request));
            var login=mock(LoggedInInfo.class);when(login.getLoggedInProviderNo()).thenReturn("999998");
            var facility=new Facility();facility.setEnableGroupNotes(true);when(login.getCurrentFacility()).thenReturn(facility);
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),login);
            var form=new CaseManagementEntryFormBean();form.setCaseNote(source);
            String key=ChartProgramContext.formKey(request,"1");request.getSession().setAttribute(key,form);
            var security=mock(SecurityInfoManager.class);
            when(security.hasPrivilege(eq(login),anyString(),anyString(),anyString())).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(eq(login),anyInt())).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(login,404)).thenReturn(false);
            var manager=mock(CaseManagementManager.class);when(manager.isClientInProgramDomain(eq("999998"),anyString())).thenReturn(true);
            when(manager.getNote(source.getId().toString())).thenReturn(source);
            when(manager.filterNotes(eq(login),eq("999998"),anyCollection(),eq("7"))).thenReturn(List.of(source));
            var admissions=mock(AdmissionManager.class);var admitted=mock(Admission.class);when(admitted.getClientId()).thenReturn(203);
            when(admissions.getCurrentAdmissionsByProgramId("7")).thenReturn(List.of(admitted));
            var demographics=mock(DemographicDao.class);var recipient=new Demographic();recipient.setDemographicNo(203);recipient.setLastName("Synthetic replacement");
            when(demographics.getDemographic("203")).thenReturn(recipient);
            var response=new MockHttpServletResponse();
            try(MockedStatic<SpringUtils> beans=mockStatic(SpringUtils.class);MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
                beans.when(()->SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
                beans.when(()->SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
                beans.when(()->SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);
                beans.when(()->SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
                beans.when(()->SpringUtils.getBean(DemographicDao.class)).thenReturn(demographics);
                beans.when(()->SpringUtils.getBean(GroupNoteDao.class)).thenReturn(observed);
                servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(response);
                Object oldGroups=ReflectionTestUtils.getField(GroupNoteAction.class,"groupNoteDao");
                Object oldAdmissions=ReflectionTestUtils.getField(GroupNoteAction.class,"admissionManager");
                Object oldDemographics=ReflectionTestUtils.getField(GroupNoteAction.class,"demographicDao");
                try {
                    ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",observed);
                    ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",admissions);
                    ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",demographics);
                    if(interleave)assertThatThrownBy(()->new GroupNoteSelect2Action().save()).isInstanceOf(SecurityException.class)
                            .hasMessage("Existing group recipient is not writable");
                    else assertThatThrownBy(()->new GroupNoteSelect2Action().save()).isInstanceOf(IllegalStateException.class)
                            .hasMessage("Owned group replacement rollback probe");
                } finally {
                    ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",oldGroups);
                    ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",oldAdmissions);
                    ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",oldDemographics);
                }
            }
            assertThat(request.getSession().getAttribute(key)).isSameAs(form);
            assertThat(form.isGroupNote()).isFalse();assertThat(form.getGroupNoteClientIds()).isNull();
            assertThat(entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:note AND demographicNo<>404 ORDER BY id")
                    .setParameter("note",source.getId().intValue()).getResultList()).usingRecursiveComparison().isEqualTo(before);
            if(interleave){
                assertThat(added.get()).hasSize(1);
                assertThat(entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:note AND demographicNo=404")
                        .setParameter("note",source.getId().intValue()).getResultList()).usingRecursiveComparison().isEqualTo(added.get());
            }
            assertThat(entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id=:note")
                    .setParameter("note",source.getId()).getResultList()).usingRecursiveComparison().isEqualTo(noteBefore);
        } finally {
            transaction.executeWithoutResult(status->{
                entityManager.createNativeQuery("DELETE FROM GroupNoteLink WHERE noteId=:note").setParameter("note",source.getId().intValue()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM casemgmt_note WHERE note_id=:note").setParameter("note",source.getId()).executeUpdate();
            });
        }
    }
}
