/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerComment;
import io.github.carlos_emr.carlos.demographic.data.DemographicListSearch;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.support.IntegrationTestSeedService;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** The caller derives locks; every backing query must exclude them before counts and pages. */
@Tag("integration") @Tag("database") @Tag("security") @Transactional
class PatientReadPaginationIntegrationTest extends CarlosTestBase {
    @Autowired private DemographicDao demographics;
    @Autowired private OscarLogDao logs;
    @Autowired private TicklerDao ticklers;
    @PersistenceContext(unitName="entityManagerFactory") private EntityManager em;
    private String marker;
    private Demographic locked,firstOpen,lastOpen;

    @BeforeEach void fixtures()throws Exception{
        marker="LOCKREAD"+UUID.randomUUID().toString().replace("-","").substring(0,10);
        IntegrationTestSeedService.ensureProviderExists(em,"999998");
        locked=patient("A");firstOpen=patient("B");lastOpen=patient("C");em.flush();
    }
    private Demographic patient(String first)throws Exception{
        Demographic patient=new Demographic();EntityDataGenerator.generateTestDataForModelClass(patient);
        patient.setLastName(marker+first);patient.setFirstName("FAKE");patient.setPatientStatus("AC");
        em.persist(patient);return patient;
    }
    @Test void demographicSearchRemovesLocksBeforeLimitOffsetAndLookaheadWithoutDeletingPatients(){
        var restricted=new DemographicListSearch("search_name",marker,"last_name",null,false,0,1,Set.of(locked.getDemographicNo()));
        assertThat(demographics.searchForPatientList(restricted,null,true)).extracting(Demographic::getDemographicNo)
                .containsExactly(firstOpen.getDemographicNo(),lastOpen.getDemographicNo());
        var second=new DemographicListSearch("search_name",marker,"last_name",null,false,1,1,restricted.deniedPatients());
        assertThat(demographics.searchForPatientList(second,null,true)).extracting(Demographic::getDemographicNo)
                .containsExactly(lastOpen.getDemographicNo());
        var allDenied=new DemographicListSearch("search_name",marker,"last_name",null,false,0,1,
                Set.of(locked.getDemographicNo(),firstOpen.getDemographicNo(),lastOpen.getDemographicNo()));
        assertThat(demographics.searchForPatientList(allDenied,null,true)).isEmpty();
        var legacy=new DemographicListSearch("search_name",marker,"last_name",null,false,0,1);
        assertThat(demographics.searchForPatientList(legacy,null,true)).extracting(Demographic::getDemographicNo)
                .containsExactly(locked.getDemographicNo(),firstOpen.getDemographicNo());
        assertThat(em.find(Demographic.class,locked.getDemographicNo())).isNotNull();
    }
    private OscarLog access(Demographic patient,String provider,long time)throws Exception{
        OscarLog log=new OscarLog();EntityDataGenerator.generateTestDataForModelClass(log);
        log.setDemographicId(patient.getDemographicNo());log.setProviderNo(provider);log.setAction("read");
        log.setContent("demographic");log.setContentId(marker);logs.persist(log);em.flush();
        em.createNativeQuery("update log set dateTime=?1 where id=?2").setParameter(1,new Timestamp(time)).setParameter(2,log.getId()).executeUpdate();
        em.flush();return log;
    }
    @Test void recentPatientsSkipDeniedNewestRowsBeforePagingAndKeepOriginalAuditRows()throws Exception{
        access(lastOpen,marker,1000);access(firstOpen,marker,2000);OscarLog hidden=access(locked,marker,3000);
        access(lastOpen,marker+"other",4000);em.clear();Set<Integer> denied=Set.of(locked.getDemographicNo());
        assertThat(logs.getRecentDemographicsAccessedByProvider(marker,0,1,denied)).containsExactly(firstOpen.getDemographicNo());
        assertThat(logs.getRecentDemographicsAccessedByProvider(marker,1,1,denied)).containsExactly(lastOpen.getDemographicNo());
        assertThat(logs.getRecentDemographicsAccessedByProvider(marker,2,1,denied)).isEmpty();
        assertThat(logs.getRecentDemographicsAccessedByProvider(marker,0,1)).containsExactly(locked.getDemographicNo());
        assertThat(logs.find(hidden.getId()).getDemographicId()).isEqualTo(locked.getDemographicNo());
    }
    private Tickler tickler(Demographic patient,long time){
        Tickler row=new Tickler();row.setDemographicNo(patient.getDemographicNo());row.setMessage(marker);
        row.setStatus(Tickler.STATUS.A);row.setPriority(Tickler.PRIORITY.Normal);row.setCreator("999998");
        row.setTaskAssignedTo("999998");row.setServiceDate(new Date(time));em.persist(row);return row;
    }
    @Test void ticklerCountsPagesAndBatchedCommentsUseTheSamePatientExclusion(){
        Tickler hidden=tickler(locked,1000),open=tickler(firstOpen,2000),last=tickler(lastOpen,3000);
        em.flush();TicklerComment comment=new TicklerComment();comment.setTicklerNo(hidden.getId());
        comment.setProviderNo("999998");comment.setMessage("FAKE-LOCKED-COMMENT");em.persist(comment);em.flush();
        CustomFilter filter=new CustomFilter();filter.setStartDate(null);filter.setEndDate(null);filter.setStatus("A");
        filter.setMessage(marker);filter.setSortColumn("serviceDate");filter.setSort_order("asc");
        filter.setExcludedDemographicNos(Set.of(locked.getDemographicNo()));
        assertThat(ticklers.getNumTicklers(filter)).isEqualTo(2);
        assertThat(ticklers.getNumFilteredTicklerDTOs(filter)).isEqualTo(2);
        assertThat(ticklers.getTicklers(filter,0,1)).extracting(Tickler::getId).containsExactly(open.getId());
        List<TicklerListDTO> first=ticklers.getTicklerDTOs(filter,0,1);
        assertThat(first).extracting(TicklerListDTO::getId).containsExactly(open.getId());
        assertThat(first.getFirst().getComments()).isEmpty();
        assertThat(ticklers.getTicklerDTOs(filter,1,1)).extracting(TicklerListDTO::getId).containsExactly(last.getId());
        assertThat(ticklers.getTicklerDTOs(filter,2,1)).isEmpty();
        filter.setSearchTerm(marker);assertThat(ticklers.getNumFilteredTicklerDTOs(filter)).isEqualTo(2);
        filter.setExcludedDemographicNos(Set.of());assertThat(ticklers.getNumTicklers(filter)).isEqualTo(3);
        assertThat(em.find(TicklerComment.class,comment.getId()).getMessage()).isEqualTo("FAKE-LOCKED-COMMENT");
    }
}
