/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.AdditionalAnswers;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Persisted sources, projections, archives and audit, using real transaction advice and DAO writes.
 * @since 2026-11-09
 */
@Tag("integration") @Tag("lab")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Isolated("Binds legacy HL7 static DAOs to the real test context")
class CmlProjectionIntegrationTest extends CarlosTestBase {
    @Autowired PlatformTransactionManager transactions;
    @Autowired PatientLabRoutingDao patients;
    @Autowired ProviderLabRoutingDao providers;
    @Autowired MeasurementDao measurements;
    @Autowired MeasurementsDeletedDao deleted;
    @Autowired MeasurementsExtDao extensions;
    @Autowired Hl7TextMessageDao messages;
    @Autowired Hl7TextInfoDao infos;
    @Autowired MeasurementMapDao mappings;
    @Autowired OscarLogDao audit;
    @Autowired DemographicDao demographics;
    @PersistenceContext(unitName="entityManagerFactory") EntityManager em;
    private final Map<Field,Object> saved=new LinkedHashMap<>();
    private final List<Integer> reports=new ArrayList<>();
    private TransactionTemplate tx;
    private CmlProjectionService service;
    private ClinicalMeasurementDeletionService manualDelete;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private int patient;
    private String accession, oldFilter;
    private boolean failAudit, failArchiveMarker, lockExisted;

    private <T> T transactional(T target) {
        var proxy=new ProxyFactory(target);
        var advice=new TransactionInterceptor(); advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); proxy.addAdvice(advice);
        @SuppressWarnings("unchecked") T result=(T)proxy.getProxy(); return result;
    }
    @BeforeEach void setup() throws Exception {
        tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for(Field field:Hl7textResultsData.class.getDeclaredFields()) if(Modifier.isStatic(field.getModifiers())&&field.getName().endsWith("Dao")) {
            field.setAccessible(true);saved.put(field,field.get(null));field.set(null,applicationContext.getBean(field.getType()));
        }
        oldFilter=CarlosProperties.getInstance().getProperty("HL7_LAB_MEASUREMENT_FILTER");
        CarlosProperties.getInstance().setProperty("HL7_LAB_MEASUREMENT_FILTER",".*");
        accession="CML-RECONCILE-"+UUID.randomUUID();
        tx.executeWithoutResult(status->{
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
            lockExisted=!em.createNativeQuery("select lab_no from providerLabRoutingLock where lab_no=?1")
                    .setParameter(1,Hl7ChainLocks.key("CML",accession)).getResultList().isEmpty();
            var demo=new Demographic();demo.setFirstName("Synthetic");demo.setLastName(accession);
            demo.setYearOfBirth("1980");demo.setMonthOfBirth("01");demo.setDateOfBirth("02");demo.setSex("F");
            demo.setPatientStatus("AC");demo.setProviderNo("999998");demographics.save(demo);patient=demo.getDemographicNo();
        });
        actor=mock(LoggedInInfo.class);when(actor.getLoggedInProviderNo()).thenReturn("999998");
        security=mock(SecurityInfoManager.class);
        when(security.isAllowedAccessToPatientRecord(actor,patient)).thenReturn(true);
        when(security.hasPrivilege(eq(actor),anyString(),anyString(),nullable(String.class))).thenReturn(true);
        OscarLogDao auditFault=mock(OscarLogDao.class);
        doAnswer(call->{audit.persist(call.getArgument(0));if(failAudit)throw new IllegalStateException("late audit INSERT fault");return null;})
                .when(auditFault).persist(any());
        MeasurementsExtDao extFault=mock(MeasurementsExtDao.class,AdditionalAnswers.delegatesTo(extensions));
        doAnswer(call->{MeasurementsExt row=call.getArgument(0);extensions.persist(row);
            if(failArchiveMarker&&CmlProjectionService.ARCHIVE.equals(row.getKeyVal()))throw new IllegalStateException("late archive marker fault");return null;})
                .when(extFault).persist(any());
        service=transactional(new CmlProjectionService(patients,providers,measurements,deleted,extFault,messages,mappings,security,auditFault));
        manualDelete=transactional(new ClinicalMeasurementDeletionService(measurements,deleted,extensions,security));
    }
    private int add(String time,String status,String... rows) {
        String body=CmlVersionPolicyUnitTest.body(accession,time,status,rows);
        Hl7TextMessage message=new Hl7TextMessage();message.setType("CML");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        messages.persist(message);int report=message.getId();reports.add(report);
        var handler=Factory.getHandler("CML",body);
        var info=new Hl7TextInfo();info.setLabNumber(report);info.setAccessionNumber(accession);
        info.setFirstName(handler.getFirstName());info.setLastName(handler.getLastName());info.setObrDate(handler.getMsgDate());
        info.setReportStatus(status);info.setSendingFacility(handler.getPatientLocation());
        info.setFinalResultCount("F".equals(status)?rows.length:0);infos.persist(info);return report;
    }
    private int ingest(String time,String status,String... rows) {
        return tx.execute(transaction->{
            Hl7ChainLocks.lock("CML",accession);
            int report=add(time,status,rows);
            service.reconcile(report,patient); // Incoming source has no patient route yet, just like MessageUploader.
            patients.persist(new PatientLabRouting(report,"HL7",patient));
            providers.persist(new ProviderLabRoutingModel("999998",report,"N","synthetic independent review",new Date(),"HL7"));
            return report;
        });
    }
    private List<Measurement> active() {return tx.execute(status->measurements.findByDemographicNo(patient));}
    private long archiveCount(){return tx.execute(status->em.createQuery("select count(d) from MeasurementsDeleted d where d.demographicNo=?1",Long.class).setParameter(1,patient).getSingleResult());}
    private long auditCount(){return tx.execute(status->em.createQuery("select count(l) from OscarLog l where l.demographicId=?1 and l.content='HL7_LAB_PROJECTION'",Long.class).setParameter(1,patient).getSingleResult());}
    private int legacyDuplicates() {
        ingest("20260310080000","F","7010=5.1");
        return tx.execute(status->{int report=add("20260310120000","C","7010=12.7");
            patients.persist(new PatientLabRouting(report,"HL7",patient));
            Hl7textResultsData.projectMeasurements(Integer.toString(report),Integer.toString(patient),(i,j)->true);return report;});
    }
    @AfterEach void cleanup() throws Exception {
        try {tx.executeWithoutResult(status->{
            var ids=em.createQuery("select e.measurementId from MeasurementsExt e where e.keyVal='lab_no' and e.val in ?1",Integer.class)
                    .setParameter(1,reports.stream().map(String::valueOf).toList()).getResultList();
            if(!ids.isEmpty()) {
                em.createQuery("delete from MeasurementsExt e where e.measurementId in ?1").setParameter(1,ids).executeUpdate();
                em.createQuery("delete from Measurement m where m.id in ?1").setParameter(1,ids).executeUpdate();
                em.createQuery("delete from MeasurementsDeleted d where d.originalId in ?1").setParameter(1,ids).executeUpdate();
            }
            if(!reports.isEmpty()) {
                em.createQuery("delete from PatientLabRouting p where p.labType='HL7' and p.labNo in ?1").setParameter(1,reports).executeUpdate();
                em.createQuery("delete from ProviderLabRoutingModel p where p.labType='HL7' and p.labNo in ?1").setParameter(1,reports).executeUpdate();
                em.createQuery("delete from Hl7TextInfo h where h.labNumber in ?1").setParameter(1,reports).executeUpdate();
                em.createQuery("delete from Hl7TextMessage h where h.id in ?1").setParameter(1,reports).executeUpdate();
            }
            em.createQuery("delete from OscarLog l where l.demographicId=?1 and l.content='HL7_LAB_PROJECTION'").setParameter(1,patient).executeUpdate();
            em.createQuery("delete from Demographic d where d.demographicNo=?1").setParameter(1,patient).executeUpdate();
            if(!lockExisted)em.createNativeQuery("delete from providerLabRoutingLock where lab_no=?1")
                    .setParameter(1,Hl7ChainLocks.key("CML",accession)).executeUpdate();
            for(int id:reports)em.createNativeQuery("delete from providerLabRoutingLock where lab_no=?1").setParameter(1,id).executeUpdate();
        });}finally{
            if(oldFilter==null)CarlosProperties.getInstance().remove("HL7_LAB_MEASUREMENT_FILTER");
            else CarlosProperties.getInstance().setProperty("HL7_LAB_MEASUREMENT_FILTER",oldFilter);
            for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());
        }
    }
    @Test void shouldKeepOnlyTheCorrection_whenAnOlderFinalArrivesLast() {
        int original=ingest("20260310080000","F","7010=5.1");
        tx.executeWithoutResult(status->{var row=providers.findAllLabRoutingByIdandType(original,"HL7").getFirst();row.setStatus("A");providers.merge(row);});
        int corrected=ingest("20260310120000","C","7010=12.7");
        ingest("20260310070000","F","7010=4.3");
        assertThat(active()).singleElement().satisfies(row->{assertThat(row.getDataField()).isEqualTo("12.7");
            tx.executeWithoutResult(status->assertThat(extensions.getMeasurementsExtByMeasurementIdAndKeyVal(row.getId(),"lab_no").getVal()).isEqualTo(Integer.toString(corrected)));});
        assertThat(archiveCount()).isEqualTo(1);
        tx.executeWithoutResult(status->assertThat(providers.findAllLabRoutingByIdandType(original,"HL7").getFirst().getStatus()).isEqualTo("A"));
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(original)).split(",")).endsWith(Integer.toString(corrected));
    }
    @Test void shouldNotProjectHistoricalValues_whenTheCorrectionArrivesFirst() {
        ingest("20260310120000","C","7010=12.7");ingest("20260310070000","F","7010=4.3");ingest("20260310080000","F","7010=5.1");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("12.7");assertThat(archiveCount()).isZero();
    }
    @Test void shouldKeepUnmentionedObservations_whenTheCorrectionIsPartial() {
        ingest("20260310080000","F","7010=5.1","7020=9.2");int report=ingest("20260310120000","C","7010=12.7");
        assertThat(active()).extracting(Measurement::getDataField).containsExactlyInAnyOrder("12.7","9.2");
        assertThat(service.preview(report,actor).spansReports()).isTrue();assertThat(service.preview(report,actor).isChangeNeeded()).isFalse();
    }
    @Test void shouldHonorAnExplicitWithdrawal_withoutResurrectingAnOlderDelivery() {
        ingest("20260310080000","F","7010=5.1");ingest("20260310120000","D","7010=");ingest("20260310070000","F","7010=4.3");
        assertThat(active()).isEmpty();assertThat(archiveCount()).isEqualTo(1);
    }
    @Test void shouldPreviewWithoutWrites_andApplyAnIdempotentReviewedRepair() {
        int report=legacyDuplicates();long archives=archiveCount();
        var preview=service.preview(report,actor);assertThat(preview.reason()).isEqualTo(CmlProjectionService.Reason.READY);
        assertThat(preview.archiveCount()).isEqualTo(1);assertThat(preview.createCount()).isZero();
        assertThat(active()).hasSize(2);assertThat(archiveCount()).isEqualTo(archives);assertThat(auditCount()).isZero();
        var result=service.apply(report,preview.fingerprint(),actor,"127.0.0.1");assertThat(result.isChangeNeeded()).isFalse();
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("12.7");assertThat(archiveCount()).isEqualTo(archives+1);assertThat(auditCount()).isEqualTo(1);
        assertThatThrownBy(()->service.apply(report,preview.fingerprint(),actor,"127.0.0.1")).isInstanceOf(CmlProjectionService.StalePlan.class);
        var fresh=service.preview(report,actor);service.apply(report,fresh.fingerprint(),actor,"127.0.0.1");
        assertThat(archiveCount()).isEqualTo(archives+1);assertThat(auditCount()).isEqualTo(1);
    }
    @Test void shouldRefuseAStalePlan_whenAnotherSourceArrivesAfterPreview() {
        int report=legacyDuplicates();var preview=service.preview(report,actor);
        ingest("20260310070000","F","7010=4.3");
        assertThatThrownBy(()->service.apply(report,preview.fingerprint(),actor,"127.0.0.1")).isInstanceOf(CmlProjectionService.StalePlan.class);
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("12.7");assertThat(auditCount()).isZero();
    }
    @Test void shouldRollbackArchivesMarkersClinicalRowsAndAudit_whenAuditFailsLate() {
        int report=legacyDuplicates();var preview=service.preview(report,actor);failAudit=true;
        assertThatThrownBy(()->service.apply(report,preview.fingerprint(),actor,"127.0.0.1")).isInstanceOf(IllegalStateException.class);
        assertThat(active()).hasSize(2);assertThat(archiveCount()).isZero();assertThat(auditCount()).isZero();
        assertThat(service.preview(report,actor).fingerprint()).isEqualTo(preview.fingerprint());
        failAudit=false;service.apply(report,preview.fingerprint(),actor,"127.0.0.1");assertThat(active()).hasSize(1);
    }
    @Test void shouldRollbackAnImport_whenArchivalFailsAfterItsInsert() {
        ingest("20260310080000","F","7010=5.1");failArchiveMarker=true;
        assertThatThrownBy(()->ingest("20260310120000","C","7010=12.7")).isInstanceOf(IllegalStateException.class);
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");assertThat(archiveCount()).isZero();
        tx.executeWithoutResult(status->assertThat(infos.searchByAccessionNumberOrderByObrDate(accession)).hasSize(1));
    }
    @Test void shouldPreserveClinicianEdits_andRequireReviewForTheNewCorrection() {
        ingest("20260310080000","F","7010=5.1");int id=active().getFirst().getId();
        // Fixture for a historical externally edited row. The production entity correctly forbids ORM updates.
        tx.executeWithoutResult(status->assertThat(em.createNativeQuery("update measurements set dataField='5.9' where id=?1 and demographicNo=?2")
                .setParameter(1,id).setParameter(2,patient).executeUpdate()).isEqualTo(1));
        int report=ingest("20260310120000","C","7010=12.7");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.9");
        assertThat(service.status(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.CLINICIAN_CHANGE);assertThat(archiveCount()).isZero();
    }
    @Test void shouldProtectManualDeletion_whenALaterCorrectionArrives() {
        ingest("20260310080000","F","7010=5.1");int id=active().getFirst().getId();
        manualDelete.delete(List.of(id),Integer.toString(patient),actor);
        int report=ingest("20260310120000","C","7010=12.7");
        assertThat(active()).isEmpty();assertThat(archiveCount()).isEqualTo(1);
        assertThat(service.preview(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.CLINICIAN_CHANGE);
        tx.executeWithoutResult(status->assertThat(extensions.getMeasurementsExtByMeasurementIdAndKeyVal(id,CmlProjectionService.ARCHIVE).getVal()).isEqualTo("CLINICIAN"));
    }
    @Test void shouldRefuseAutomaticRestoration_whenLegacyDeletionIntentIsUnknown() {
        int report=ingest("20260310080000","F","7010=5.1");int id=active().getFirst().getId();
        tx.executeWithoutResult(status->{var row=measurements.find(id);deleted.persist(new MeasurementsDeleted(row));measurements.remove(id);});
        assertThat(service.preview(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.LEGACY_DELETION);
        ingest("20260310120000","C","7010=12.7");assertThat(active()).isEmpty();
    }
    @Test void shouldRefuseRepair_whenThePatientRecordIsLocked() {
        int report=legacyDuplicates();var preview=service.preview(report,actor);
        when(security.isAllowedAccessToPatientRecord(actor,patient)).thenReturn(false);
        assertThatThrownBy(()->service.apply(report,preview.fingerprint(),actor,"127.0.0.1")).isInstanceOf(SecurityException.class);
        assertThat(active()).hasSize(2);assertThat(archiveCount()).isZero();assertThat(auditCount()).isZero();
    }
}
