/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoChainLockDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

/**
 * One report's measurements filed against the real tables (#6391): all or nothing, once however
 * often it runs, and a correction replaces the version before it. Each test uses lab numbers of its
 * own, so a test that commits (the rollback one) leaves nothing another reads.
 */
@DisplayName("LabMeasurementFilingService Integration Tests")
@Tag("integration")
@Tag("service")
@Tag("lab")
public class LabMeasurementFilingServiceIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 90_001;

    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private Hl7TextInfoChainLockDao locks;
    @Autowired
    private MeasurementDao measurementDao;
    @Autowired
    private MeasurementsExtDao measurementsExtDao;
    @Autowired
    private MeasurementsDeletedDao measurementsDeletedDao;

    private LabMeasurementFilingService service(String chain) {
        return new LabMeasurementFilingService(transactions, locks, measurementDao, measurementsExtDao,
                measurementsDeletedDao, lab -> chain);
    }

    private static final java.util.concurrent.atomic.AtomicInteger LAB_NUMBERS =
            new java.util.concurrent.atomic.AtomicInteger(7_200_000);

    /** Committed in a transaction of its own: these tests run outside the test transaction. */
    private int storedReport(String accession) {
        return new TransactionTemplate(transactions).execute(status -> {
            Hl7TextInfo info = new Hl7TextInfo();
            info.setLabNumber(LAB_NUMBERS.incrementAndGet());
            info.setAccessionNumber(accession);
            info.setFirstName("FAKE");
            info.setLastName("FAKE");
            info.setObrDate("2026-11-27 12:00:00");
            info.setReportStatus("F");
            info.setFinalResultCount(0);
            hibernateTemplate.save(info);
            hibernateTemplate.flush();
            return info.getLabNumber();
        });
    }

    private static FiledMeasurementDto result(int lab, String value, String extensionValue) {
        return result(lab, PATIENT, value, extensionValue);
    }

    private static FiledMeasurementDto result(int lab, int patient, String value, String extensionValue) {
        Measurement m = new Measurement();
        m.setType("A1C");
        m.setDemographicId(patient);
        m.setProviderNo("0");
        m.setDataField(value);
        m.setDateObserved(new Date());
        m.setAppointmentNo(0);
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put("lab_no", String.valueOf(lab));
        extensions.put("identifier", extensionValue);
        return new FiledMeasurementDto(m, extensions);
    }

    private static LabMeasurementFilingDto filing(int lab, String accession, FiledMeasurementDto... results) {
        return new LabMeasurementFilingDto(String.valueOf(lab), PATIENT, false, accession, null, List.of(results));
    }

    private List<Measurement> filed(int lab) {
        return new TransactionTemplate(transactions).execute(status -> measurementDao.findByValue("lab_no", String.valueOf(lab)));
    }

    @Test
    void shouldBuildTheServiceFromTheApplicationContext_withItsDaosAndOneTransactionManager() {
        // Two constructors and no @Autowired would stop the application starting; this builds the bean the
        // way production does, which also shows the chain-lock DAO bean exists and that the transaction
        // manager it needs is unique.
        var bean = io.github.carlos_emr.carlos.utility.SpringUtils.getBean(LabMeasurementFilingService.class);

        assertThat(bean).isNotNull();
    }

    /** What another transaction sees right now: the upload's own is still open when this is called inside it. */
    private List<Measurement> filedElsewhere(int lab) {
        TransactionTemplate other = new TransactionTemplate(transactions);
        other.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return other.execute(status -> measurementDao.findByValue("lab_no", String.valueOf(lab)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldFileAfterTheUploadCommits_notBefore_whenCalledInsideTheUploadsTransaction() {
        int lab = storedReport("ACC-INT-AFTER");
        var service = service(String.valueOf(lab));
        List<Measurement> beforeCommit = new TransactionTemplate(transactions).execute(status -> {
            service.fileAfterCommit(filing(lab, "ACC-INT-AFTER", result(lab, "5.1", "3767")));
            return filedElsewhere(lab);
        });

        // Not while the upload's transaction was open; and, read from another connection, there once it ended:
        // the filing committed in a transaction of its own.
        assertThat(beforeCommit).isEmpty();
        assertThat(filedElsewhere(lab)).extracting(Measurement::getDataField).containsExactly("5.1");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldFileNothing_whenTheUploadRollsBack() {
        int lab = storedReport("ACC-INT-ROLLEDBACK");
        var service = service(String.valueOf(lab));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.fileAfterCommit(filing(lab, "ACC-INT-ROLLEDBACK", result(lab, "5.1", "3767")));
            status.setRollbackOnly();
        });

        assertThat(filedElsewhere(lab)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldLetTheUploadCommitAndFileNothing_whenTheFilingAfterCommitFails() {
        int lab = storedReport("ACC-INT-BROKEN");
        var service = service(String.valueOf(lab));

        // The upload reports success (no exception reaches it) though its filing failed.
        new TransactionTemplate(transactions).executeWithoutResult(status -> service.fileAfterCommit(
                filing(lab, "ACC-INT-BROKEN", result(lab, "5.1", "3767"), result(lab, "5.2", "x".repeat(70_000)))));

        assertThat(filedElsewhere(lab)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldKeepNothingOfTheFiling_whenOneResultsExtensionFails() {
        int lab = storedReport("ACC-INT-ROLLBACK");
        var tooLong = "x".repeat(70_000);
        var filingService = service(String.valueOf(lab));
        var dto = filing(lab, "ACC-INT-ROLLBACK",
                result(lab, "5.1", "3767"), result(lab, "5.2", tooLong), result(lab, "5.3", "3767"));

        assertThatThrownBy(() -> filingService.file(dto))
                .isInstanceOf(RuntimeException.class);

        // Not one of the three results, though the first was fully written before the second failed.
        assertThat(filed(lab)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldLeaveOneCompleteSet_whenAFailedFilingIsRetried() {
        int lab = storedReport("ACC-INT-RETRY");
        var service = service(String.valueOf(lab));
        var badDto = filing(lab, "ACC-INT-RETRY",
                result(lab, "5.1", "3767"), result(lab, "5.2", "x".repeat(70_000)));
        assertThatThrownBy(() -> service.file(badDto))
                .isInstanceOf(RuntimeException.class);

        service.file(filing(lab, "ACC-INT-RETRY", result(lab, "5.1", "3767"), result(lab, "5.2", "3767")));

        assertThat(filed(lab)).extracting(Measurement::getDataField).containsExactlyInAnyOrder("5.1", "5.2");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldFileOneRowPerResultAndArchiveTheFirstSet_whenTheSameReportIsFiledTwice() {
        int lab = storedReport("ACC-INT-TWICE");
        var service = service(String.valueOf(lab));
        // Reusing the exact same DTO instance also proves the DTO is safely repeatable.
        var dto = filing(lab, "ACC-INT-TWICE", result(lab, "5.1", "3767"), result(lab, "5.2", "3767"));

        service.file(dto);
        service.file(dto);

        assertThat(filed(lab)).hasSize(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldCommitOneCompleteSet_whenALockFailureHappensAfterPersistingMeasurements() {
        int lab = storedReport("ACC-INT-LOCK-RETRY");
        var extCalls = new java.util.concurrent.atomic.AtomicInteger();
        MeasurementsExtDao flakyExtDao = org.mockito.Mockito.mock(MeasurementsExtDao.class, invocation -> {
            if ("batchPersistAtomically".equals(invocation.getMethod().getName())
                    && extCalls.incrementAndGet() == 1) {
                throw new jakarta.persistence.LockTimeoutException("synthetic lock wait timeout on extensions");
            }
            return invocation.getMethod().invoke(measurementsExtDao, invocation.getArguments());
        });
        var flakyService = new LabMeasurementFilingService(transactions, locks, measurementDao, flakyExtDao,
                measurementsDeletedDao, id -> String.valueOf(lab));

        var outcome = flakyService.file(filing(lab, "ACC-INT-LOCK-RETRY",
                result(lab, "5.1", "3767"), result(lab, "5.2", "3767")));

        assertThat(outcome).isEqualTo(LabMeasurementFilingService.Result.FILED);
        assertThat(extCalls.get()).isEqualTo(2);
        assertThat(filed(lab)).extracting(Measurement::getDataField).containsExactlyInAnyOrder("5.1", "5.2");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldLeaveOnlyTheCorrectionsValues_whenACorrectionFollowsTheOriginal() {
        int original = storedReport("ACC-INT-CORR");
        int correction = storedReport("ACC-INT-CORR");
        String chain = original + "," + correction;
        service(chain).file(filing(original, "ACC-INT-CORR", result(original, "5.1", "3767")));

        service(chain).file(filing(correction, "ACC-INT-CORR", result(correction, "6.9", "3767")));

        assertThat(filed(original)).isEmpty();
        assertThat(filed(correction)).extracting(Measurement::getDataField).containsExactly("6.9");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldFileNothingForTheOlderVersion_whenItArrivesAfterTheCorrection() {
        int original = storedReport("ACC-INT-LATE");
        int correction = storedReport("ACC-INT-LATE");
        String chain = original + "," + correction;
        service(chain).file(filing(correction, "ACC-INT-LATE", result(correction, "6.9", "3767")));

        service(chain).file(filing(original, "ACC-INT-LATE", result(original, "5.1", "3767")));

        assertThat(filed(original)).isEmpty();
        assertThat(filed(correction)).extracting(Measurement::getDataField).containsExactly("6.9");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldLeaveAnotherPatientsValuesAlone_whenARecycledAccessionPutsTheirReportInTheChain() {
        int theirs = storedReport("ACC-INT-RECYCLED");
        int mine = storedReport("ACC-INT-RECYCLED");
        String chain = theirs + "," + mine;
        FiledMeasurementDto other = result(theirs, PATIENT + 1, "4.4", "3767");
        // Their report was filed when it was the only one of the accession (later, mine joins its chain).
        service(String.valueOf(theirs)).file(new LabMeasurementFilingDto(String.valueOf(theirs), PATIENT + 1, false,
                "ACC-INT-RECYCLED", null, List.of(other)));

        service(chain).file(filing(mine, "ACC-INT-RECYCLED", result(mine, "5.5", "3767")));

        assertThat(filed(theirs)).extracting(Measurement::getDataField).containsExactly("4.4");
        assertThat(filed(mine)).extracting(Measurement::getDataField).containsExactly("5.5");
    }
}
