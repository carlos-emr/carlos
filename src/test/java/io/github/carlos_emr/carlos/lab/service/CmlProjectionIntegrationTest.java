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
    private Runnable auditHook = () -> { };
    private Runnable archiveHook = () -> { };
    private final List<Integer> controls = new ArrayList<>();

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
        doAnswer(call->{audit.persist(call.getArgument(0));auditHook.run();if(failAudit)throw new IllegalStateException("late audit INSERT fault");return null;})
                .when(auditFault).persist(any());
        MeasurementsExtDao extFault=mock(MeasurementsExtDao.class,AdditionalAnswers.delegatesTo(extensions));
        doAnswer(call->{MeasurementsExt row=call.getArgument(0);extensions.persist(row);
            if(CmlProjectionService.ARCHIVE.equals(row.getKeyVal())) {
                archiveHook.run();
                if(failArchiveMarker)throw new IllegalStateException("late archive marker fault");
            }
            return null;})
                .when(extFault).persist(any());
        service=transactional(new CmlProjectionService(patients,providers,measurements,deleted,extFault,messages,mappings,security,auditFault));
        manualDelete=transactional(new ClinicalMeasurementDeletionService(measurements,deleted,extensions,security));
    }
    private int add(String time,String status,String... rows) {
        String body=CmlVersionPolicyUnitTest.body(accession,time,status,rows);
        return addBody(body, status, rows.length);
    }
    private int addBody(String body, String status, int rowCount) {
        Hl7TextMessage message=new Hl7TextMessage();message.setType("CML");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        messages.persist(message);int report=message.getId();reports.add(report);
        var handler=Factory.getHandler("CML",body);
        var info=new Hl7TextInfo();info.setLabNumber(report);info.setAccessionNumber(accession);
        info.setFirstName(handler.getFirstName());info.setLastName(handler.getLastName());info.setObrDate(handler.getServiceDate().isEmpty()?handler.getMsgDate():handler.getServiceDate());
        info.setReportStatus(status);info.setSendingFacility(handler.getPatientLocation());
        info.setFinalResultCount("F".equals(status)?rowCount:0);infos.persist(info);return report;
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
            em.createQuery("delete from OscarLog l where l.demographicId=?1").setParameter(1,patient).executeUpdate();
            em.createQuery("delete from Demographic d where d.demographicNo=?1").setParameter(1,patient).executeUpdate();
            for(int control:controls)em.createQuery("delete from Demographic d where d.demographicNo=?1").setParameter(1,control).executeUpdate();
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
    @Test void shouldUpdateUnprotectedObservations_whenAnotherObservationHasClinicianChanges() {
        ingest("20260310080000","F","7010=5.1","7020=9.2");
        int edited=active().stream().filter(row->"5.1".equals(row.getDataField())).findFirst().orElseThrow().getId();
        tx.executeWithoutResult(status->assertThat(em.createNativeQuery("update measurements set dataField='5.9' where id=?1 and demographicNo=?2")
                .setParameter(1,edited).setParameter(2,patient).executeUpdate()).isEqualTo(1));
        int report=ingest("20260310120000","C","7010=12.7","7020=9.9");
        assertThat(active()).extracting(Measurement::getDataField).containsExactlyInAnyOrder("5.9","9.9");
        assertThat(archiveCount()).isEqualTo(1);
        assertThat(service.preview(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.CLINICIAN_CHANGE);
    }
    @Test void shouldProtectLegacyUnitEdits_evenWithoutAnOriginMarker() {
        ingest("20260310080000","F","7010=5.1");int id=active().getFirst().getId();
        tx.executeWithoutResult(status->{
            em.createQuery("delete from MeasurementsExt e where e.measurementId=?1 and e.keyVal='lab_proj_origin'").setParameter(1,id).executeUpdate();
            em.createQuery("update MeasurementsExt e set e.val='mg/dL' where e.measurementId=?1 and e.keyVal='unit'").setParameter(1,id).executeUpdate();
        });
        int report=ingest("20260310120000","C","7010=12.7");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");assertThat(archiveCount()).isZero();
        assertThat(service.preview(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.CLINICIAN_CHANGE);
    }
    @Test void shouldBlockWrongOwnership_evenWhenTheSameRowAlsoHasClinicianChanges() {
        ingest("20260310080000","F","7010=5.1");int id=active().getFirst().getId();
        int foreign=tx.execute(status->{
            var demo=new Demographic();demo.setFirstName("Synthetic");demo.setLastName("CONTROL-"+UUID.randomUUID());
            demo.setYearOfBirth("1980");demo.setMonthOfBirth("01");demo.setDateOfBirth("02");demo.setSex("F");
            demo.setPatientStatus("AC");demo.setProviderNo("999998");demographics.save(demo);controls.add(demo.getDemographicNo());
            em.createNativeQuery("update measurements set dataField='5.9',demographicNo=?1 where id=?2 and demographicNo=?3")
                    .setParameter(1,demo.getDemographicNo()).setParameter(2,id).setParameter(3,patient).executeUpdate();
            return demo.getDemographicNo();
        });
        int report=ingest("20260310120000","C","7010=12.7");
        assertThat(service.preview(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.WRONG_OWNER);
        assertThat(active()).isEmpty();assertThat(archiveCount()).isZero();
        tx.executeWithoutResult(status->{var row=measurements.find(id);assertThat(row.getDemographicId()).isEqualTo(foreign);assertThat(row.getDataField()).isEqualTo("5.9");});
    }
    @Test void shouldPreserveRawConflictingObservations_withoutGuessingWhichOneToProject() {
        ingest("20260310080000","F","7010=5.1");
        int report=ingest("20260310120000","C","7010=12.7","7010=14.1");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");assertThat(archiveCount()).isZero();
        assertThat(service.status(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.AMBIGUOUS_SOURCE);
        tx.executeWithoutResult(status->assertThat(infos.searchByAccessionNumberOrderByObrDate(accession)).hasSize(2));
    }
    @Test void shouldRequireReviewForDuplicateSourceExtensions_withoutRejectingTheRawCorrection() {
        ingest("20260310080000","F","7010=5.1");int id=active().getFirst().getId();
        tx.executeWithoutResult(status->{var duplicate=new MeasurementsExt();duplicate.setMeasurementId(id);duplicate.setKeyVal("identifier");duplicate.setVal("7010");extensions.persist(duplicate);});
        int report=ingest("20260310120000","C","7010=12.7");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");assertThat(archiveCount()).isZero();
        assertThat(service.status(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.AMBIGUOUS_SOURCE);
    }
    @Test void shouldNotTreatAnEmptyFinalValueAsAnImplicitDeletion() {
        ingest("20260310080000","F","7010=5.1");int report=ingest("20260310120000","F","7010=");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");assertThat(archiveCount()).isZero();
        assertThat(service.status(report,actor).spansReports()).isTrue();
    }
    @Test void shouldRepairAnUnambiguousMissingProjection_withoutChangingSourceOrReviewRows() {
        int report=tx.execute(status->{int id=add("20260310080000","F","7010=5.1");patients.persist(new PatientLabRouting(id,"HL7",patient));return id;});
        var preview=service.preview(report,actor);assertThat(preview.createCount()).isEqualTo(1);assertThat(preview.archiveCount()).isZero();
        service.apply(report,preview.fingerprint(),actor,"127.0.0.1");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");assertThat(auditCount()).isEqualTo(1);
        tx.executeWithoutResult(status->{
            int id=active().getFirst().getId();
            assertThat(extensions.getMeasurementsExtByMeasurementIdAndKeyVal(id,"lab_proj_origin").getVal()).hasSize(64);
            assertThat(patients.findByLabNoAndLabType(report,"HL7")).singleElement().satisfies(row->assertThat(row.getDemographicNo()).isEqualTo(patient));
        });
    }
    @Test void shouldRecognizeLegacyTruncatedMarkers_withoutLosingNewProvenance() {
        ingest("20260310080000","F","7010=5.1");ingest("20260310120000","C","7010=12.7");
        tx.executeWithoutResult(status->{
            em.createQuery("update MeasurementsExt e set e.keyVal='lab_projection_archi' where e.keyVal='lab_proj_archive' and e.measurementId in (select d.originalId from MeasurementsDeleted d where d.demographicNo=?1)")
                    .setParameter(1,patient).executeUpdate();
            em.createQuery("update MeasurementsExt e set e.keyVal='lab_projection_origi' where e.keyVal='lab_proj_origin' and e.measurementId in (select d.originalId from MeasurementsDeleted d where d.demographicNo=?1)")
                    .setParameter(1,patient).executeUpdate();
        });
        int report=ingest("20260310140000","C","7010=14.2");
        assertThat(active()).extracting(Measurement::getDataField).containsExactly("14.2");
        assertThat(service.preview(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.READY);
        assertThat(CmlProjectionService.ARCHIVE.length()).isLessThanOrEqualTo(20);
    }
    private static void await(java.util.concurrent.CountDownLatch latch) {
        try { if(!latch.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("concurrency test latch expired"); }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
    }
    @Test void shouldSerializeRepairAndNewIngestion_withoutExposingPartialClinicalWrites() throws Exception {
        int report=legacyDuplicates();var preview=service.preview(report,actor);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        auditHook=()->{entered.countDown();await(release);};
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var repair=pool.submit(()->service.apply(report,preview.fingerprint(),actor,"127.0.0.1"));
            await(entered);
            var started=new java.util.concurrent.CountDownLatch(1);
            var incoming=pool.submit(()->{started.countDown();return ingest("20260310140000","C","7010=14.2");});
            await(started);
            assertThat(active()).hasSize(2); // READ_COMMITTED sees the prior complete state while repair is uncommitted.
            assertThat(incoming.isDone()).isFalse();
            release.countDown();repair.get(30,java.util.concurrent.TimeUnit.SECONDS);incoming.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(active()).extracting(Measurement::getDataField).containsExactly("14.2");assertThat(auditCount()).isEqualTo(1);
        } finally {release.countDown();}
    }
    @Test void shouldRefuseAStaleManualDelete_whenIngestionReplacesItsSelectedRow() throws Exception {
        ingest("20260310080000","F","7010=5.1");int old=active().getFirst().getId();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        archiveHook=()->{entered.countDown();await(release);};
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var incoming=pool.submit(()->ingest("20260310120000","C","7010=12.7"));await(entered);
            var deletion=pool.submit(()->manualDelete.delete(List.of(old),Integer.toString(patient),actor));
            assertThat(active()).extracting(Measurement::getDataField).containsExactly("5.1");
            release.countDown();incoming.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertThatThrownBy(()->deletion.get(30,java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(ClinicalMeasurementDeletionService.StaleMeasurement.class);
            assertThat(active()).extracting(Measurement::getDataField).containsExactly("12.7");
            assertThat(archiveCount()).isEqualTo(1);
        } finally {release.countDown();}
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "20260230120000"})
    void shouldKeepRawHistoryAndUnlinkAvailable_whenClinicalRevisionTimeIsUnusable(String clinicalTime) {
        int original=ingest("20260310080000","F","7010=5.1");
        int report=tx.execute(status->{
            String body=CmlVersionPolicyUnitTest.body(accession,"20260310120000","C","7010=12.7");
            String[] lines=body.split("\r");
            for(int i=0;i<lines.length;i++) {
                String[] fields=lines[i].split("\\|",-1);
                if("OBR".equals(fields[0])) {fields[7]=clinicalTime;fields[22]=clinicalTime;}
                if("OBX".equals(fields[0])) fields[14]=clinicalTime;
                lines[i]=String.join("|",fields);
            }
            int id=addBody(String.join("\r",lines)+"\r","C",1);
            service.reconcile(id,patient);patients.persist(new PatientLabRouting(id,"HL7",patient));return id;
        });
        assertThat(active()).singleElement().satisfies(row->assertThat(row.getDataField()).isEqualTo("5.1"));
        assertThat(service.status(report,actor).reason()).isEqualTo(CmlProjectionService.Reason.AMBIGUOUS_SOURCE);
        assertThat(service.preview(report,actor).isReady()).isFalse();
        assertThatThrownBy(()->Hl7textResultsData.getMatchingLabs(Integer.toString(report)))
                .isInstanceOf(CmlVersionPolicy.AmbiguousRevision.class);
        assertThat(Hl7textResultsData.getMatchingLabsForHistory(Integer.toString(report)).split(","))
                .containsExactlyInAnyOrder(Integer.toString(original),Integer.toString(report));
        var source=Factory.getHandler(Integer.toString(report));assertThat(source.getOBXResult(0,0)).isEqualTo("12.7");
        var inbox = new io.github.carlos_emr.carlos.lab.ca.on.LabResultData("HL7");
        inbox.setSegmentID(Integer.toString(report));assertThat(inbox.getMultipleAckCount()).isZero();
        assertThatCode(() -> new io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator(
                new java.io.ByteArrayOutputStream(), Integer.toString(report), "999998")).doesNotThrowAnyException();
        var unlink=transactional(new Hl7PatientUnlinkService(patients,providers,measurements,deleted,extensions,messages,security,audit));
        unlink.unlink(report,unlink.version(report),"synthetic wrong-patient correction",actor,"127.0.0.1");
        assertThat(active()).isEmpty();
        tx.executeWithoutResult(status->{for(int id:List.of(original,report))assertThat(patients.findByLabNoAndLabType(id,"HL7"))
                .allSatisfy(row->assertThat(row.getDemographicNo()).isEqualTo(PatientLabRoutingDao.UNMATCHED));});
    }

}
