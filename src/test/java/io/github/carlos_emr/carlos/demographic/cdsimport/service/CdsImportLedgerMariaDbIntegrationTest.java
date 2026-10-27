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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Statement;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;


/** Required real-database tier: actual JPA/JDBC atomicity, checkpoints, fencing, audit rollback and holds. */
@Tag("integration") @Tag("mariadb")
@Isolated("Dedicated CDS ledger database admission")
@EnabledIfSystemProperty(named="cds.ledger.test.properties",matches=".+")
class CdsImportLedgerMariaDbIntegrationTest {
 @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Config { }
 public static class CommitFaultManager implements PlatformTransactionManager {
  private final PlatformTransactionManager delegate; private boolean fail; private boolean failBegin;
  CommitFaultManager(PlatformTransactionManager delegate) {this.delegate=delegate;}
  void failNextCommitAcknowledgement() {fail=true;}
  void failNextBegin() {failBegin=true;}
  public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {if(failBegin){failBegin=false;throw new org.springframework.transaction.TransactionSystemException("synthetic failed transaction begin");}return delegate.getTransaction(definition);}
  public void commit(org.springframework.transaction.TransactionStatus status) {boolean owning=status.isNewTransaction();delegate.commit(status);if(owning && fail){fail=false;throw new org.springframework.transaction.TransactionSystemException("synthetic lost acknowledgement");}}
  public void rollback(org.springframework.transaction.TransactionStatus status) {delegate.rollback(status);}
 }
 public static class Audit extends CdsImportEventDao {
  private boolean fail;
  public void failNext() { fail=true; }
  @Override public void persist(AbstractModel<?> value) { if(fail) {fail=false;throw new IllegalStateException("synthetic audit fault");} super.persist(value); }
 }
 static final String A="a".repeat(64),B="b".repeat(64),C="c".repeat(64);
 static JdbcTemplate sql; static CdsImportLedgerService ledger; static CdsImportUnitService units;
 static CdsImportFileService files; static CdsImportReportService reports; static CdsImportFieldPersister fieldValues; static Audit audit;
 static int target=100_000_000+new java.security.SecureRandom().nextInt(100_000_000);
 static CommitFaultManager commitFault;
 record Job(String id,long epoch,String patient,String first,String second,int target) {}
 @Test void physicalTransactionsAndRecoveryRetainExactAccounting() throws Exception {
  Properties jdbc=new Properties();
  try(InputStream input=Files.newInputStream(Path.of(System.getProperty("cds.ledger.test.properties")))) { jdbc.load(input); }
  String url=jdbc.getProperty("jdbcUrl","").replace("jdbc:mariadb:","jdbc:mysql:");
  if(!url.matches("jdbc:mysql://[^/]+/cds_ledger_test(?:\\?.*)?")) throw new IllegalArgumentException("A dedicated disposable cds_ledger_test schema is required");
  AnnotationConfigApplicationContext ctx=new AnnotationConfigApplicationContext();
  ctx.register(Config.class);
  ctx.registerBean("dataSource",DriverManagerDataSource.class,()->new DriverManagerDataSource(url,jdbc.getProperty("jdbcUser"),jdbc.getProperty("jdbcPassword")));
  ctx.registerBean("entityManagerFactory",LocalContainerEntityManagerFactoryBean.class,()->{
   var emf=new LocalContainerEntityManagerFactoryBean();emf.setPersistenceUnitName("entityManagerFactory");emf.setDataSource(ctx.getBean(DataSource.class));
   emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
   String[] names={"Run","Patient","Unit","Dependency","Lineage","Checkpoint","Field","Diagnostic","File","FileReference","Event","Admission","Identity"};
   emf.setManagedTypes(PersistenceManagedTypes.of(Arrays.stream(names).map(n->"io.github.carlos_emr.carlos.commn.model.CdsImport"+n).toArray(String[]::new)));
   emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.dialect","org.hibernate.dialect.MariaDBDialect","hibernate.show_sql","false","hibernate.jdbc.time_zone","UTC"));return emf;
  });
  ctx.registerBean("transactionManager",PlatformTransactionManager.class,()->new CommitFaultManager(new JpaTransactionManager(ctx.getBean(EntityManagerFactory.class))));
  ctx.registerBean("persistenceAnnotationBeanPostProcessor",org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor.class);
  ctx.register(CdsImportIdentityDao.class,CdsImportRunDao.class,CdsImportPatientDao.class,CdsImportUnitDao.class,CdsImportDependencyDao.class,CdsImportLineageDao.class,CdsImportCheckpointDao.class,CdsImportDiagnosticDao.class,CdsImportAdmissionDao.class,CdsImportFieldDao.class,CdsImportFileDao.class,CdsImportFileReferenceDao.class);
  ctx.registerBean(CdsImportEventDao.class,Audit::new);
  ctx.register(CdsImportLedgerService.class,CdsImportUnitService.class,CdsImportFieldPersister.class,CdsImportFileService.class,CdsImportReportService.class);
  ctx.refresh();
  try {
   sql=new JdbcTemplate(ctx.getBean(DataSource.class));ledger=ctx.getBean(CdsImportLedgerService.class);units=ctx.getBean(CdsImportUnitService.class);files=ctx.getBean(CdsImportFileService.class);reports=ctx.getBean(CdsImportReportService.class);audit=ctx.getBean(Audit.class);commitFault=(CommitFaultManager)ctx.getBean(PlatformTransactionManager.class);fieldValues=ctx.getBean(CdsImportFieldPersister.class);
   sql.update("update cds_import_admission set owner_run_id=null where id='global'");
   check(sql.queryForObject("select @@innodb_snapshot_isolation",Integer.class)==1,"snapshot isolation enabled");
   Job job=job("p"+UUID.randomUUID().toString().substring(0,8),false);
   expect("MANIFEST_ALREADY_SEALED",()->ledger.planUnit(job.id,job.epoch,job.patient,CdsImportKeys.hash("unexpected"),"ClinicalNotes",3,A,"/opaque/unexpected"));
   var published=files.published(job.id,job.epoch,"/opaque/immutable-"+UUID.randomUUID(),A,7);
   long beforeBegin=countNative(); commitFault.failNextBegin();
   expect("UNIT_TRANSACTION_START_FAILED",()->units.execute(job.id,job.epoch,job.first,()->{throw new AssertionError("Callback after transaction begin failure");},CdsImportLedgerMariaDbIntegrationTest::verify));
   check(countNative()==beforeBegin && sql.queryForObject("select count(*) from cds_import_checkpoint where id=?",Long.class,job.first)==0,"failed transaction begin is known no-write, never unknown commit");
   CdsImportReceiptDto first=units.execute(job.id,job.epoch,job.first,()->{
    var receipt=write(job,"first"); files.reference(job.id,job.epoch,job.first,published.id(),A);
    fieldValues.preserve(job.id,job.epoch,job.first,"/@same",1,"","xs:string",null,"original-a","normalized-a",null,null,0,0);
    fieldValues.preserve(job.id,job.epoch,job.first,"/@same",1,"urn:fixture","xs:string",null,"original-b","normalized-b",null,null,0,0);
    return receipt;
   },CdsImportLedgerMariaDbIntegrationTest::verify);
   check(fieldValues.fields(job.target,"Demographics","",10).size()==2,"same-local fields in distinct namespaces remain individually addressable");
   check(!first.alreadyComplete(),"first source occurrence imported");
   var ownership=files.ownedFiles(job.id,"",10);
   check(ownership.size()==1 && ownership.get(0).referenceCount()==1,"bounded owned-file catalogue counts all durable references");
   long before=countNative();
   expect("UNIT_WRITE_FAILED",()->units.execute(job.id,job.epoch,job.second,()->{write(job,"rolled-back");throw new Exception("synthetic fault");},CdsImportLedgerMariaDbIntegrationTest::verify));
   check(countNative()==before,"native JDBC write rolled back with checked callback failure");
   check(sql.queryForObject("select count(*) from cds_import_checkpoint where id=?",Long.class,job.second)==0,"no checkpoint after callback rollback");
   audit.failNext();
   expect("UNIT_WRITE_FAILED",()->units.execute(job.id,job.epoch,job.second,()->write(job,"audit-rollback"),CdsImportLedgerMariaDbIntegrationTest::verify));
   check(countNative()==before,"strict audit failure rolls back native JDBC and provenance");
   commitFault.failNextCommitAcknowledgement();
   expect("UNIT_COMMIT_OUTCOME_UNKNOWN",()->units.execute(job.id,job.epoch,job.second,()->write(job,"second"),CdsImportLedgerMariaDbIntegrationTest::verify));
   check(countNative()==before+1,"lost commit acknowledgement retains exactly one committed native row");
   units.execute(job.id,job.epoch,job.second,()->{throw new AssertionError("unknown commit replay");},CdsImportLedgerMariaDbIntegrationTest::verify);
   CdsImportReceiptDto repeated=units.execute(job.id,job.epoch,job.first,()->{throw new AssertionError("blind replay");},CdsImportLedgerMariaDbIntegrationTest::verify);
   check(repeated.alreadyComplete(),"same-run verification is no new write");
   check(((Number)ledger.summary(job.id).get("committedUnits")).longValue()==2,"same-run resumed committed occurrences retain import count");
   check(ledger.isHeld(job.target),"partial chart held");
   ledger.finalizePatient(job.id,job.epoch,job.patient,CdsImportLedgerMariaDbIntegrationTest::verify);
   check(!ledger.isHeld(job.target),"completion releases reconciled hold");
   check("COMPLETE".equals(ledger.finish(job.id,job.epoch).state()),"run completion derived from patient outcome");
   check("RELEASE_PENDING".equals(ledger.summary(job.id).get("maintenanceReleaseState")),"clinical completion atomically persists release-pending crash protection");
   reports.select(job.id,job.epoch,0,C,"/opaque/report-v1",A);
   expect("REPORT_GENERATION_CONFLICT",()->reports.select(job.id,job.epoch,0,C,"/opaque/stale-report",B));
   Job repeat=repeat(job);
   units.execute(repeat.id,repeat.epoch,repeat.first,()->{throw new AssertionError("cross-run replay");},CdsImportLedgerMariaDbIntegrationTest::verify);
   units.execute(repeat.id,repeat.epoch,repeat.second,()->{throw new AssertionError("cross-run replay");},CdsImportLedgerMariaDbIntegrationTest::verify);
   check(files.referencedFiles(repeat.id,"",10).stream().anyMatch(file->file.id().equals(published.id())),"cross-run no-op retains physical file reconciliation dependencies");
   ledger.finalizePatient(repeat.id,repeat.epoch,repeat.patient,CdsImportLedgerMariaDbIntegrationTest::verify);
   check("ALREADY_COMPLETE".equals(ledger.patients(repeat.id,"",10).get(0).state()),"prior completion distinct from new imports");
   check(((Number)ledger.summary(repeat.id).get("committedUnits")).longValue()==0,"cross-run no-op not counted imported");
   ledger.finish(repeat.id,repeat.epoch);
   Job pending=job("p"+UUID.randomUUID().toString().substring(0,8),false);
   units.execute(pending.id,pending.epoch,pending.first,()->write(pending,"pending-first"),CdsImportLedgerMariaDbIntegrationTest::verify);
   expect("PATIENT_INCOMPLETE",()->ledger.finalizePatient(pending.id,pending.epoch,pending.patient,CdsImportLedgerMariaDbIntegrationTest::verify));
   check(ledger.isHeld(pending.target),"never-started unit keeps hold");
   long successor=ledger.acquire(pending.id);
   expect("OWNER_FENCED",()->units.execute(pending.id,pending.epoch,pending.second,()->{throw new AssertionError("stale write");},CdsImportLedgerMariaDbIntegrationTest::verify));
   var orphan=files.published(pending.id,successor,"/opaque/immutable-"+UUID.randomUUID(),A,7);
   files.beginRetirement(pending.id,successor,orphan.id());
   expect("FILE_NOT_AVAILABLE",()->units.execute(pending.id,successor,pending.second,()->{ledger.bindTarget(pending.id,successor,pending.patient,pending.target);files.reference(pending.id,successor,pending.second,orphan.id(),A);return first;},CdsImportLedgerMariaDbIntegrationTest::verify));
   files.retired(pending.id,successor,orphan.id(),orphan.generation());
   check("DELETED".equals(files.file(orphan.id()).state()),"retirement tombstone prevents adoption");
   ledger.markPatient(pending.id,successor,pending.patient,"PARTIAL_COMMITTED");ledger.finish(pending.id,successor);
   check(ledger.isHeld(pending.target),"partial completion never releases hold");
   ledger.requestResume(pending.id);ledger.cancel(pending.id);
   expect("IMPORT_CANCELLED",()->ledger.resume(pending.id));
   check(ledger.status(pending.id).ownerEpoch()==successor,"new cancellation is not erased or advanced by stale queued resume");
   check(ledger.summary(pending.id).get("clinicalComplete").equals(false),"incomplete clinical work is not complete");
   check(ledger.summary(pending.id).get("reportsDelivered").equals(false),"clinical bookkeeping does not fake report delivery");
   assertPartialReplayRequiresOriginal(pending);
   verifyOwnerRowLock();
   verifyPlanFencing();
   verifyDryRun();
   verifyReviewedResetAndReplan();
   verifyAuthorizationStopAndResume();
   verifyCurrentInitiatorControlAudit();
   verifyUnwrittenRevisionApproval();
   verifyMaintenanceReleaseOutcome(job);
  } finally {ctx.close();}
 }
 static void verifyOwnerRowLock() throws Exception {
  Job job=job("lock-"+UUID.randomUUID(),false);
  var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var writer=executor.submit(()->units.execute(job.id,job.epoch,job.first,()->{entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Synthetic writer timed out");return write(job,"lock");},CdsImportLedgerMariaDbIntegrationTest::verify));
   check(entered.await(5,java.util.concurrent.TimeUnit.SECONDS),"writer entered actual unit");
   var attempted=new java.util.concurrent.CountDownLatch(1);
   var successor=executor.submit(()->{attempted.countDown();return ledger.acquire(job.id);});
   check(attempted.await(5,java.util.concurrent.TimeUnit.SECONDS),"successor attempted owner acquisition");
   try {successor.get(200,java.util.concurrent.TimeUnit.MILLISECONDS);throw new AssertionError("Owner row lock released before unit commit");}
   catch(java.util.concurrent.TimeoutException expected) { /* The physical row lock is retained until transaction end. */ }
   release.countDown();writer.get(5,java.util.concurrent.TimeUnit.SECONDS);long epoch=successor.get(5,java.util.concurrent.TimeUnit.SECONDS);
   check(epoch>job.epoch,"successor acquires next epoch after unit commit");
   ledger.markPatient(job.id,epoch,job.patient,"PARTIAL_COMMITTED");ledger.finish(job.id,epoch);
  } finally {release.countDown();executor.shutdownNow();}
 }
 static void verifyPlanFencing() {
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/planning")).id();
  long first=ledger.claimPlanning(id); long second=ledger.claimPlanning(id);
  expect("OWNER_FENCED",()->ledger.planPatient(id,first,"patient","fenced-source","primary",A));
  ledger.planPatient(id,second,"patient","fenced-source","primary",A);
  String unit=CdsImportKeys.hash(id,"unit"); ledger.planUnit(id,second,"patient",unit,"Demographics",1,A,"/opaque/unit");
  expect("OWNER_FENCED",()->ledger.seal(id,first,C,1,1));
  ledger.seal(id,second,C,1,1);
  expect("MANIFEST_ALREADY_SEALED",()->ledger.planUnit(id,second,"patient",CdsImportKeys.hash(id,"extra"),"ClinicalNotes",2,A,"/opaque/extra"));
  ledger.release(id,second);
 }
 static void verifyDryRun() {
  String source="preview-"+UUID.randomUUID();
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/preview",true)).id();
  ledger.planPatient(id,"patient",source,"primary",A);
  String unit=CdsImportKeys.hash("fixture",source,"primary","Demographics","1"); ledger.planUnit(id,"patient",unit,"Demographics",1,A,"/opaque/preview-unit"); ledger.seal(id,C,1,1);
  long epoch=ledger.acquire(id);
  expect("DRY_RUN_CLINICAL_WRITE_REFUSED",()->units.execute(id,epoch,unit,()->{throw new AssertionError("dry-run write");},CdsImportLedgerMariaDbIntegrationTest::verify));
  check("DRY_RUN_COMPLETE".equals(ledger.completeDryRun(id,epoch).state()),"dry-run separate terminal state");
  check(ledger.maintenanceReleasePending(id),"successful preview must verify maintenance release before clean completion");
  check(!Boolean.TRUE.equals(ledger.summary(id).get("clinicalComplete")),"dry-run not imported");
  String actual=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,"d".repeat(64),C,"/opaque/actual",false)).id();
  ledger.planPatient(actual,"patient",source,"primary",A); ledger.planUnit(actual,"patient",unit,"Demographics",1,A,"/opaque/actual-unit"); ledger.seal(actual,C,1,1);
  long actualEpoch=ledger.acquire(actual); Job actualJob=new Job(actual,actualEpoch,"patient",unit,"unused",++target);
  units.execute(actual,actualEpoch,unit,()->write(actualJob,"actual-after-preview"),CdsImportLedgerMariaDbIntegrationTest::verify);
  ledger.finalizePatient(actual,actualEpoch,"patient",CdsImportLedgerMariaDbIntegrationTest::verify); ledger.finish(actual,actualEpoch);
  String conflict=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,"e".repeat(64),C,"/opaque/conflicting",false)).id();
  expect("SOURCE_REVISION_REVIEW_REQUIRED",()->ledger.planPatient(conflict,"patient",source,"primary",A));
 }
 static void verifyReviewedResetAndReplan() {
  String source="review-"+UUID.randomUUID();
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/review")).id();
  long epoch=ledger.claimPlanning(id); ledger.planPatient(id,epoch,"patient",source,"primary",A);
  String first=CdsImportKeys.hash("fixture",source,"primary","Demographics","1"),second=CdsImportKeys.hash(first,"fields");
  ledger.planUnit(id,epoch,"patient",first,"Demographics",1,A,"/opaque/old-first"); ledger.planUnit(id,epoch,"patient",second,"Demographics_FIELDS",1,A,"/opaque/old-fields"); ledger.addDependency(id,epoch,second,first);
  sql.update("insert into cds_import_identity(id,run_id,patient_key,copy_key,first_name,last_name,birth_date,hin) values (?,?,?,'primary','Synthetic','Fixture','2000-01-01',null)",CdsImportKeys.hash(id,"identity"),id,"patient");
  // The independent review service supplies the archived/XSD-validated proof; this core test installs its published CAS version.
  sql.update("update cds_import_run set review_version=1 where id=?",id);
  ledger.resetUnsealedPlan(id,epoch,1);
  check(ledger.units(id,null,"",10).isEmpty(),"review reset removes only expected administrative units");
  check(ledger.dependencyEvidence(id,"",10).isEmpty(),"review reset clears old expected dependencies");
  check(sql.queryForObject("select count(*) from cds_import_identity where run_id=?",Long.class,id)==0,"review reset clears old protected identity register");
  check(A.equals(ledger.patientStatus(id,"patient").sourceDigest()),"old source facts retained until explicit revision");
  ledger.cancel(id);check(!ledger.requestResumeAfterReview(id),"finished administrative review cannot revive a later cancellation");
  check(ledger.status(id).cancelRequested(),"review continuation preserves newer cancel flag");ledger.requestResume(id);
  CdsImportLedgerService.template(commitFault).executeWithoutResult(status -> ledger.reviseUncommittedLineage(id,epoch,"patient",1,B));
  check(B.equals(ledger.patientStatus(id,"patient").sourceDigest()),"reviewed source revision becomes exact new patient provenance");
  ledger.planUnit(id,epoch,"patient",first,"Demographics",1,C,"/opaque/reviewed-first"); ledger.discovery(id,epoch,1,1,1); ledger.seal(id,epoch,C,1,1);
  long execution=ledger.acquire(id); Job job=new Job(id,execution,"patient",first,"unused",++target);
  units.execute(id,execution,first,()->write(job,"reviewed"),CdsImportLedgerMariaDbIntegrationTest::verify);
  ledger.finalizePatient(id,execution,"patient",CdsImportLedgerMariaDbIntegrationTest::verify); ledger.finish(id,execution);
 }
 static void verifyAuthorizationStopAndResume() {
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/auth")).id();
  long epoch=ledger.claimPlanning(id); ledger.planPatient(id,epoch,"patient","auth-"+UUID.randomUUID(),"primary",A);
  ledger.authorizationStopped(id,epoch);
  check("FAILED_BEFORE_WRITE".equals(ledger.status(id).state()),"authorization loss durable before writes");
  check("NOT_STARTED".equals(ledger.patientStatus(id,"patient").state()),"authorization stop accounts untouched patient");
  ledger.requestResume(id);ledger.cancel(id);
  expect("IMPORT_CANCELLED",()->ledger.claimPlanning(id,true));
  check(ledger.status(id).ownerEpoch()==epoch,"queued preflight resume cannot erase later cancellation");
  ledger.requestResume(id);
  long resumed=ledger.claimPlanning(id,true);ledger.resolveReauthorizedAccess(id,resumed);ledger.preparePreflightResume(id,resumed);
  check("PLANNED".equals(ledger.patientStatus(id,"patient").state()),"fresh authorization reopens only administrative preflight");
  ledger.endPreflight(id,resumed,"FAILED_BEFORE_WRITE","SYNTHETIC_IO_STOP");ledger.release(id,resumed);
  long retry=ledger.claimPlanning(id,true);ledger.preparePreflightResume(id,retry);
  expect("PREFLIGHT_REVALIDATION_REQUIRED",()->ledger.resolveRevalidatedPreflightStops(id,retry,C));
  ledger.discovery(id,retry,1,1,1);ledger.resolveRevalidatedPreflightStops(id,retry,C);
  check(((Number)ledger.summary(id).get("unresolvedReviewItems")).longValue()==0,"only live-auth and complete revalidation proofs retire supervision stops");ledger.release(id,retry);
 }
 static void verifyCurrentInitiatorControlAudit() {
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/controls")).id();
  var current=new CdsImportActorDto("web-777","999997","999997",777,null,null);
  ledger.control(id,current,"RESUME_REQUESTED",()->ledger.requestResumeParticipating(id));
  check("fixture".equals(ledger.status(id).operator()),"current web controller never replaces original job actor");
  check(sql.queryForObject("select count(*) from cds_import_event where run_id=? and event_type='RESUME_REQUESTED' and operator='web-777' and provider_no='999997' and observation_context like '%777%'",Long.class,id)==1,"strict control audit identifies current security/operator/provider");
  audit.failNext();expect("CONTROL_WRITE_FAILED",()->ledger.control(id,current,"CANCEL_REQUESTED",()->ledger.cancelParticipating(id)));
  check(!ledger.status(id).cancelRequested(),"failed strict control audit rolls back cancellation transition");
  check(sql.queryForObject("select count(*) from cds_import_event where run_id=? and event_type='CANCEL_REQUESTED'",Long.class,id)==0,"no applied control audit after failed atomic transition");
  ledger.control(id,current,"CANCEL_REQUESTED",()->ledger.cancelParticipating(id));
  check(ledger.status(id).cancelRequested(),"successful current-initiator cancellation commits");
 }
 static void verifyMaintenanceReleaseOutcome(Job complete) {
  ledger.maintenanceReleaseDeferred(complete.id,complete.epoch,"GLOBAL_HOLDS_REMAIN");
  check("RELEASE_DEFERRED".equals(ledger.summary(complete.id).get("maintenanceReleaseState")),"another run held chart is visible release deferral, not a clinical failure");
  check("COMPLETE".equals(ledger.status(complete.id).state()),"other global holds never reclassify this completed clinical run");
  ledger.requestResume(complete.id);
  check(ledger.maintenanceReleaseRetryRequested(complete.id),"completed-run maintenance recovery intent survives loss of the in-memory queue");
  ledger.maintenanceReleaseFailed(complete.id,complete.epoch,null);
  check("COMPLETE".equals(ledger.status(complete.id).state()),"release failure preserves already published clinical outcome");
  check(Boolean.TRUE.equals(ledger.summary(complete.id).get("maintenanceReleasePending")),"own failed maintenance close remains durable and unresolved");
  ledger.operational(complete.id,complete.epoch,null,"COMPLETE",null);
  check(!Boolean.TRUE.equals(ledger.summary(complete.id).get("cleanComplete")),"successful filesystem cleanup cannot hide a failed maintenance release");
  check("FAILED".equals(ledger.summary(complete.id).get("cleanupState")),"operator sees composite operational failure after retained maintenance");
  var diagnostic=ledger.diagnostics(complete.id,0,200).stream().filter(d->CdsImportLedgerService.MAINTENANCE_RELEASE_CODE.equals(d.code())).findFirst().orElseThrow();
  expect("MAINTENANCE_RELEASE_PROOF_REQUIRED",()->ledger.resolveDiagnostic(complete.id,complete.epoch,diagnostic.id()));
  // The separate maintenance real-DB suite proves actual owner-specific release/readback; this verifies only its ledger participant.
  ledger.maintenanceReleaseReconciled(complete.id,complete.epoch);
  check(!ledger.maintenanceReleasePending(complete.id),"verified release retires only the operational latch");
  check(!ledger.maintenanceReleaseRetryRequested(complete.id),"verified release consumes its durable recovery request");
  check("COMPLETE".equals(ledger.status(complete.id).state()),"verified operational recovery never changes clinical completion");
 }
 static void verifyUnwrittenRevisionApproval() {
  String source="revision-"+UUID.randomUUID(),copy="primary",patient=CdsImportKeys.hash("fixture",source,copy);
  String old=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/revision-old")).id();
  long oldEpoch=ledger.claimPlanning(old);ledger.planPatient(old,oldEpoch,patient,source,copy,A);ledger.release(old,oldEpoch);
  String next=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",B,C,A,"/opaque/revision-new")).id();
  long epoch=ledger.claimPlanning(next);
  expect("SOURCE_REVISION_REVIEW_REQUIRED",()->ledger.planPatient(next,epoch,patient,source,copy,B));
  ledger.diagnostic(next,epoch,patient,null,"ERROR","SOURCE_REVISION_REVIEW_REQUIRED","PREFLIGHT",null,"Explicit unwritten revision approval required","/opaque/revision-context",true);
  check(ledger.diagnostics(next,0,10).stream().anyMatch(d->patient.equals(d.patientKey())),"canonical conflict keeps the patient key even before the new patient row exists");
  ledger.recordRevisionConflictPatient(next,epoch,source,copy,B);
  check(((Number)ledger.summary(next).get("sourcePatientsIdentified")).longValue()==1,"canonical conflict preserves already identified source patient inventory");
  check("NOT_STARTED".equals(ledger.patientStatus(next,patient).state()) && ledger.patientStatus(next,patient).targetDemographicNo()==null,"identified conflict is neither an eligible plan nor a bound chart");
  expect("SOURCE_REVISION_REVIEW_REQUIRED",()->ledger.planPatient(next,epoch,patient,source,copy,B));
  var projection=ledger.unwrittenRevision(next,epoch,source,copy,B);
  check(projection.eligible() && projection.priorRunIds().equals(List.of(old)) && projection.priorRunCount()==1,"new accepted source exposes exact bounded zero-write prior provenance");
  check(ledger.unwrittenRevisionPriorRuns(next,epoch,source,copy,"",1).equals(List.of(old)),"revision provenance is pageable without retaining patient graphs");
  expect("REVIEW_REQUIRES_TRANSACTION",()->ledger.approveUnwrittenRevision(next,epoch,1,source,copy,A,B,C,B));
  sql.update("update cds_import_run set review_version=1 where id=?",next);
  expect("CANONICAL_REVISION_CONFLICT",()->CdsImportLedgerService.template(commitFault).executeWithoutResult(status->ledger.approveUnwrittenRevision(next,epoch,1,source,copy,C,B,C,B)));
  audit.failNext();
  try { CdsImportLedgerService.template(commitFault).executeWithoutResult(status->ledger.approveUnwrittenRevision(next,epoch,1,source,copy,A,B,C,B));throw new AssertionError("Missing strict revision audit failure"); }
  catch(IllegalStateException expected) { check(A.equals(ledger.unwrittenRevision(next,epoch,source,copy,B).oldSourceDigest()),"strict audit failure rolls back canonical revision"); }
  CdsImportLedgerService.template(commitFault).executeWithoutResult(status->ledger.approveUnwrittenRevision(next,epoch,1,source,copy,A,B,C,B));
  check("PLANNED".equals(ledger.planPatient(next,epoch,patient,source,copy,B).state()),"approved conflict row becomes an eligible administrative plan only after canonical CAS");ledger.release(next,epoch);
  String unit=CdsImportKeys.hash(source,"old-unit");long obsolete=ledger.claimPlanning(old,true);ledger.planUnit(old,obsolete,patient,unit,"Demographics",1,A,"/opaque/old-unit");ledger.seal(old,obsolete,C,1,1);
  long execution=ledger.acquire(old);
  expect("SOURCE_REVISION_REVIEW_REQUIRED",()->units.execute(old,execution,unit,()->{throw new AssertionError("Superseded source clinical write");},CdsImportLedgerMariaDbIntegrationTest::verify));
  check(sql.queryForObject("select count(*) from cds_import_checkpoint where lineage_key=?",Long.class,patient)==0,"superseded zero-write plan cannot create clinical checkpoint");ledger.release(old,execution);
 }
 static Job job(String source,boolean unused) {
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/snapshot")).id();
  String p="patient";ledger.planPatient(id,p,source,"primary",A);
  String first=CdsImportKeys.hash("fixture",source,"primary","Demographics","1"),second=CdsImportKeys.hash("fixture",source,"primary","ClinicalNotes","1");
  ledger.planUnit(id,p,first,"Demographics",1,A,"/opaque/first");ledger.planUnit(id,p,second,"ClinicalNotes",1,B,"/opaque/second");ledger.addDependency(id,second,first);
  ledger.seal(id,C,1,2);return new Job(id,ledger.acquire(id),p,first,second,++target);
 }
 static Job repeat(Job previous) {
  String source=ledger.patients(previous.id,"",10).get(0).sourcePatientId();
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/snapshot-repeat")).id();
  long epoch=ledger.claimPlanning(id);
  ledger.planPatient(id,epoch,previous.patient,source,"primary",A);ledger.planUnit(id,epoch,previous.patient,previous.first,"Demographics",1,A,"/opaque/first");ledger.planUnit(id,epoch,previous.patient,previous.second,"ClinicalNotes",1,B,"/opaque/second");ledger.addDependency(id,epoch,previous.second,previous.first);
  ledger.requireCompleteCanonicalReplay(id,epoch,previous.patient); ledger.seal(id,epoch,C,1,2);
  return new Job(id,ledger.acquire(id),previous.patient,previous.first,previous.second,previous.target);
 }
 static void assertPartialReplayRequiresOriginal(Job previous) {
  String source=ledger.patients(previous.id,"",10).get(0).sourcePatientId();
  String id=ledger.accept(new CdsImportJobCommand(UUID.randomUUID().toString(),"fixture","999998","fixture","omd-5.2",A,B,C,"/opaque/partial-replay")).id();
  long epoch=ledger.claimPlanning(id);ledger.planPatient(id,epoch,previous.patient,source,"primary",A);
  ledger.planUnit(id,epoch,previous.patient,previous.first,"Demographics",1,A,"/opaque/first");ledger.planUnit(id,epoch,previous.patient,previous.second,"ClinicalNotes",1,B,"/opaque/second");
  expect("PRIOR_IMPORT_MUST_RESUME",()->ledger.requireCompleteCanonicalReplay(id,epoch,previous.patient));
  ledger.endPreflight(id,epoch,"REVIEW_REQUIRED","PRIOR_IMPORT_MUST_RESUME");ledger.release(id,epoch);
 }
 static CdsImportReceiptDto write(Job job,String suffix) {
  check("READ-COMMITTED".equals(sql.queryForObject("select @@tx_isolation",String.class)),"actual unit connection READ_COMMITTED");
  ledger.bindTarget(job.id,job.epoch,job.patient,job.target);
  String value="ledger-"+suffix+"-"+UUID.randomUUID();var key=new GeneratedKeyHolder();
  sql.update(connection->{var statement=connection.prepareStatement("insert into drugs(provider_no,demographic_no,position,lastUpdateDate,dispenseInternal,customName,rx_date,end_date) values ('999998',?,0,NOW(),0,?,NULL,NULL)",Statement.RETURN_GENERATED_KEYS);statement.setInt(1,job.target);statement.setString(2,value);return statement;},key);
  int id=key.getKey().intValue();return new CdsImportReceiptDto("{\"id\":"+id+"}",CdsImportKeys.hash(Integer.toString(id),value));
 }
 static boolean verify(CdsImportReceiptDto receipt) {
  int id=Integer.parseInt(receipt.targetReferences().replaceAll("[^0-9]",""));
  List<String> values=sql.queryForList("select customName from drugs where drugid=?",String.class,id);
  return values.size()==1 && receipt.targetDigest().equals(CdsImportKeys.hash(Integer.toString(id),values.get(0)));
 }
 static long countNative() {return sql.queryForObject("select count(*) from drugs where provider_no='999998'",Long.class);}
 static void check(boolean value,String description) {if(!value)throw new AssertionError(description);}
 interface Checked {void run()throws Exception;}
 static void expect(String code,Checked action) {try {action.run();throw new AssertionError("Missing "+code);}catch(CdsImportException e){check(code.equals(e.getCode()),"expected "+code+" got "+e.getCode());}catch(Exception e){throw new RuntimeException(e);}}
}
