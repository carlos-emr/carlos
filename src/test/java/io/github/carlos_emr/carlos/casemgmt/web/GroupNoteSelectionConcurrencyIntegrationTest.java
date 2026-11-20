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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Committed fixtures and independent worker transactions expose empty-group replacement/clear races.
 * @since 2026-10-25
 */
class GroupNoteSelectionConcurrencyIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired private PlatformTransactionManager transactions;

    record Result(MockHttpServletRequest request, String key, CaseManagementEntryFormBean form) { }
    static void await(CountDownLatch latch, String label) {
        try { assertThat(latch.await(20,TimeUnit.SECONDS)).as(label).isTrue(); }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(label,interrupted);}
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void shouldRequireAnExistingTransaction_whenTakingTheParentLock() {
        var dao=new GroupNoteDaoImpl();ReflectionTestUtils.setField(dao,"entityManager",entityManager);
        var proxyFactory=new org.springframework.aop.framework.ProxyFactory(dao);proxyFactory.setInterfaces(GroupNoteDao.class);
        proxyFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy=(GroupNoteDao)proxyFactory.getProxy();
        assertThatThrownBy(()->proxy.lockNoteForGroupUpdate(Integer.MAX_VALUE))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void shouldReturnAbsentWithoutMutation_whenSourceNoteDisappeared() {
        var dao=new GroupNoteDaoImpl();ReflectionTestUtils.setField(dao,"entityManager",entityManager);
        Boolean found=new TransactionTemplate(transactions).execute(status->dao.lockNoteForGroupUpdate(Integer.MAX_VALUE));
        assertThat(found).isFalse();
    }

    @ParameterizedTest @ValueSource(booleans={false,true}) @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void shouldApplyWholeSerialReplacementsAndPreserveBystanders_whenTwoEmptyPlansOverlap(boolean clearSecond) throws Exception {
        var transaction=new TransactionTemplate(transactions);
        var groups=new GroupNoteDaoImpl();ReflectionTestUtils.setField(groups,"entityManager",entityManager);
        var seeded=transaction.execute(status->{
            var first=createNote("1","Concurrent source café 中文");var other=createNote("1","Bystander source remains exact");
            var link=new GroupNoteLink();link.setNoteId(other.getId().intValue());link.setDemographicNo(404);link.setActive(true);groups.persist(link);entityManager.flush();
            return List.of(first,other);
        });
        CaseManagementNote source=seeded.get(0),bystander=seeded.get(1);
        var xPlan=new CountDownLatch(1);var yPlan=new CountDownLatch(1);var yProgress=new CountDownLatch(1);
        var xRelease=new CountDownLatch(1);var yRelease=new CountDownLatch(1);
        var label=new ThreadLocal<String>();var events=new CopyOnWriteArrayList<String>();
        var sessions=new ConcurrentHashMap<String,String>();var yAttemptedLock=new AtomicBoolean();
        GroupNoteDao observed=mock(GroupNoteDao.class,org.mockito.AdditionalAnswers.delegatesTo(groups));
        doAnswer(call->{
            String who=label.get();if(who!=null){events.add(who+":lock-attempt");if(who.equals("Y")){yAttemptedLock.set(true);yProgress.countDown();}}
            boolean found=groups.lockNoteForGroupUpdate(call.getArgument(0));
            if(who!=null)events.add(who+":locked");return found;
        }).when(observed).lockNoteForGroupUpdate(source.getId().intValue());
        doAnswer(call->{
            var result=groups.findLinksByNoteId(call.getArgument(0));String who=label.get();
            if(who!=null&&TransactionSynchronizationManager.isActualTransactionActive()){
                events.add(who+":plan:"+result.size());sessions.put(who,entityManager.unwrap(org.hibernate.engine.spi.SharedSessionContractImplementor.class).getSessionIdentifier().toString());
                if(who.equals("X")){xPlan.countDown();await(xRelease,"release first planned request");}
                else{yPlan.countDown();yProgress.countDown();await(yRelease,"release second planned request");}
            }
            return result;
        }).when(observed).findLinksByNoteId(source.getId().intValue());
        var login=mock(LoggedInInfo.class);when(login.getLoggedInProviderNo()).thenReturn("999998");
        var facility=new Facility();facility.setEnableGroupNotes(true);when(login.getCurrentFacility()).thenReturn(facility);
        var security=mock(SecurityInfoManager.class);when(security.hasPrivilege(eq(login),anyString(),anyString(),anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login),anyInt())).thenReturn(true);
        var manager=mock(CaseManagementManager.class);when(manager.isClientInProgramDomain(eq("999998"),anyString())).thenReturn(true);
        when(manager.getNote(source.getId().toString())).thenReturn(source);
        when(manager.filterNotes(eq(login),eq("999998"),anyCollection(),eq("7"))).thenReturn(List.of(source));
        var admissions=mock(AdmissionManager.class);var admitted=new ArrayList<Admission>();
        for(int patient:List.of(202,303)){var row=new Admission();row.setClientId(patient);row.setProgramId(7);admitted.add(row);}
        when(admissions.getCurrentAdmissionsByProgramId("7")).thenAnswer(call->{
            if(label.get()!=null&&TransactionSynchronizationManager.isActualTransactionActive())events.add(label.get()+":enrolled");
            return admitted;
        });
        var demographics=mock(DemographicDao.class);when(demographics.getDemographic(anyString())).thenAnswer(call->{
            var value=new Demographic();value.setDemographicNo(Integer.parseInt(call.getArgument(0)));value.setLastName("Synthetic recipient");return value;
        });
        var executor=Executors.newFixedThreadPool(2);
        List<Future<Result>> futures=new ArrayList<>();
        Object oldGroups,oldAdmissions,oldDemographics;
        try(MockedStatic<SpringUtils> beans=mockStatic(SpringUtils.class)){
            beans.when(()->SpringUtils.getBean(GroupNoteDao.class)).thenReturn(observed);
            beans.when(()->SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
            beans.when(()->SpringUtils.getBean(DemographicDao.class)).thenReturn(demographics);
            oldGroups=ReflectionTestUtils.getField(GroupNoteAction.class,"groupNoteDao");oldAdmissions=ReflectionTestUtils.getField(GroupNoteAction.class,"admissionManager");oldDemographics=ReflectionTestUtils.getField(GroupNoteAction.class,"demographicDao");
        }
        ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",observed);ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",admissions);ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",demographics);
        try {
            List<?> notesBefore=entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id IN (:a,:b) ORDER BY note_id")
                    .setParameter("a",source.getId()).setParameter("b",bystander.getId()).getResultList();
            List<?> bystanderBefore=entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:id ORDER BY id").setParameter("id",bystander.getId().intValue()).getResultList();
            java.util.function.BiFunction<String,String,Callable<Result>> operation=(who,recipient)->()->{
                label.set(who);
                var request=new MockHttpServletRequest();request.setMethod("POST");request.setParameter("demographicNo","1");request.setParameter("noteId",source.getId().toString());request.setParameter("num_anonymous","0");
                if(recipient!=null)request.setParameter("group_client_id",recipient);
                ChartProgramContext.remember(request,"7");request.setParameter("chartContext",ChartProgramContext.token(request));
                LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),login);
                var form=new CaseManagementEntryFormBean();form.setCaseNote(source);
                String key=ChartProgramContext.formKey(request,"1");request.getSession().setAttribute(key,form);
                try(MockedStatic<SpringUtils> beans=mockStatic(SpringUtils.class);MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)){
                    beans.when(()->SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);beans.when(()->SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
                    beans.when(()->SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);beans.when(()->SpringUtils.getBean(AdmissionManager.class)).thenReturn(admissions);
                    beans.when(()->SpringUtils.getBean(DemographicDao.class)).thenReturn(demographics);beans.when(()->SpringUtils.getBean(GroupNoteDao.class)).thenReturn(observed);
                    servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
                    assertThat(new GroupNoteSelect2Action().save()).isEqualTo("success");return new Result(request,key,form);
                } finally {label.remove();}
            };
            futures.add(executor.submit(operation.apply("X","202")));await(xPlan,"first real transaction reads its initially empty plan");
            // A different note remains lockable while X holds this note; no global mutex/table lock.
            Boolean otherLock=transaction.execute(status->groups.lockNoteForGroupUpdate(bystander.getId().intValue()));assertThat(otherLock).isTrue();
            futures.add(executor.submit(operation.apply("Y",clearSecond?null:"303")));await(yProgress,"second request reaches parent lock or plan");
            boolean secondReadWhileFirstHeld=yPlan.getCount()==0||events.contains("Y:enrolled");
            xRelease.countDown();Result x=futures.get(0).get(25,TimeUnit.SECONDS);
            yRelease.countDown();Result y=futures.get(1).get(25,TimeUnit.SECONDS);
            List<Integer> active=groups.findLinksByNoteId(source.getId().intValue()).stream().map(GroupNoteLink::getDemographicNo).sorted().toList();
            assertThat(active).as("The second request must replace the first complete set, never union it or miss clearing it")
                    .containsExactlyElementsOf(clearSecond?List.of():List.of(303));
            var allRows=entityManager.createQuery("from GroupNoteLink where noteId=:note order by id",GroupNoteLink.class)
                    .setParameter("note",source.getId().intValue()).getResultList();
            assertThat(allRows).hasSize(clearSecond?1:2).allMatch(row->!row.isAnonymous());
            assertThat(allRows.getFirst().getDemographicNo()).isEqualTo(202);assertThat(allRows.getFirst().isActive()).isFalse();
            if(!clearSecond){assertThat(allRows.getLast().getDemographicNo()).isEqualTo(303);assertThat(allRows.getLast().isActive()).isTrue();}
            assertThat(yAttemptedLock).isTrue();assertThat(secondReadWhileFirstHeld).as("Second transaction cannot query its plan/admissions before the first commits").isFalse();
            assertThat(events.indexOf("X:locked")).isLessThan(events.indexOf("X:enrolled"));assertThat(events.indexOf("Y:locked")).isLessThan(events.indexOf("Y:enrolled"));
            assertThat(sessions).hasSize(2);assertThat(sessions.get("X")).isNotEqualTo(sessions.get("Y"));
            assertThat(entityManager.createNativeQuery("SELECT * FROM GroupNoteLink WHERE noteId=:id ORDER BY id").setParameter("id",bystander.getId().intValue()).getResultList()).usingRecursiveComparison().isEqualTo(bystanderBefore);
            assertThat(entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE note_id IN (:a,:b) ORDER BY note_id")
                    .setParameter("a",source.getId()).setParameter("b",bystander.getId()).getResultList()).usingRecursiveComparison().isEqualTo(notesBefore);
            assertThat(x.request().getSession().getAttribute(x.key())).isSameAs(x.form());assertThat(y.request().getSession().getAttribute(y.key())).isSameAs(y.form());
            assertThat(x.form().getNoteId()).isNull();assertThat(y.form().getNoteId()).isNull();
            assertThat(x.form().getCaseNote()).isSameAs(source);assertThat(y.form().getCaseNote()).isSameAs(source);
            assertThat(x.form().getGroupNoteClientIds()).containsExactly("202");assertThat(y.form().getGroupNoteClientIds()).containsExactly(clearSecond?new String[0]:new String[]{"303"});
            System.out.println("GROUP_CONCURRENCY_PROOF clearSecond="+clearSecond+" active="+active+" events="+events);
        } finally {
            xRelease.countDown();yRelease.countDown();executor.shutdown();
            if(!executor.awaitTermination(30,TimeUnit.SECONDS)){executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).as("All writers terminate before fixture cleanup").isTrue();}
            ReflectionTestUtils.setField(GroupNoteAction.class,"groupNoteDao",oldGroups);ReflectionTestUtils.setField(GroupNoteAction.class,"admissionManager",oldAdmissions);ReflectionTestUtils.setField(GroupNoteAction.class,"demographicDao",oldDemographics);
            transaction.executeWithoutResult(status->{
                entityManager.createNativeQuery("DELETE FROM GroupNoteLink WHERE noteId IN (:a,:b)").setParameter("a",source.getId().intValue()).setParameter("b",bystander.getId().intValue()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM casemgmt_note WHERE note_id IN (:a,:b)").setParameter("a",source.getId()).setParameter("b",bystander.getId()).executeUpdate();
            });
        }
    }
}
