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

    private int storedReport(String accession) {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setAccessionNumber(accession);
        info.setFirstName("FAKE");
        info.setLastName("FAKE");
        info.setObrDate(new Date().toString());
        info.setReportStatus("F");
        info.setFinalResultCount(0);
        hibernateTemplate.save(info);
        hibernateTemplate.flush();
        return info.getLabNumber();
    }

    private static FiledMeasurementDto result(int lab, String value, String extensionValue) {
        Measurement m = new Measurement();
        m.setType("A1C");
        m.setDemographicId(PATIENT);
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldKeepNothingOfTheFiling_whenOneResultsExtensionFails() {
        int lab = storedReport("ACC-INT-ROLLBACK");
        var tooLong = "x".repeat(70_000);

        assertThatThrownBy(() -> service(String.valueOf(lab)).file(filing(lab, "ACC-INT-ROLLBACK",
                result(lab, "5.1", "3767"), result(lab, "5.2", tooLong), result(lab, "5.3", "3767"))))
                .isInstanceOf(RuntimeException.class);

        // Not one of the three results, though the first was fully written before the second failed.
        assertThat(filed(lab)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldLeaveOneCompleteSet_whenAFailedFilingIsRetried() {
        int lab = storedReport("ACC-INT-RETRY");
        var service = service(String.valueOf(lab));
        assertThatThrownBy(() -> service.file(filing(lab, "ACC-INT-RETRY",
                result(lab, "5.1", "3767"), result(lab, "5.2", "x".repeat(70_000)))))
                .isInstanceOf(RuntimeException.class);

        service.file(filing(lab, "ACC-INT-RETRY", result(lab, "5.1", "3767"), result(lab, "5.2", "3767")));

        assertThat(filed(lab)).extracting(Measurement::getDataField).containsExactlyInAnyOrder("5.1", "5.2");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldFileOneRowPerResultAndArchiveTheFirstSet_whenTheSameReportIsFiledTwice() {
        int lab = storedReport("ACC-INT-TWICE");
        var service = service(String.valueOf(lab));

        service.file(filing(lab, "ACC-INT-TWICE", result(lab, "5.1", "3767"), result(lab, "5.2", "3767")));
        service.file(filing(lab, "ACC-INT-TWICE", result(lab, "5.1", "3767"), result(lab, "5.2", "3767")));

        assertThat(filed(lab)).hasSize(2);
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
        FiledMeasurementDto other = result(theirs, "4.4", "3767");
        other.measurement().setDemographicId(PATIENT + 1);
        service(chain).file(new LabMeasurementFilingDto(String.valueOf(theirs), PATIENT + 1, false,
                "ACC-INT-RECYCLED", null, List.of(other)));

        service(chain).file(filing(mine, "ACC-INT-RECYCLED", result(mine, "5.5", "3767")));

        assertThat(filed(theirs)).extracting(Measurement::getDataField).containsExactly("4.4");
        assertThat(filed(mine)).extracting(Measurement::getDataField).containsExactly("5.5");
    }
}
