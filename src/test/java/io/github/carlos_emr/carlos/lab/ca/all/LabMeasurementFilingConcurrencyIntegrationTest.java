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

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Two filings of one chain at once (#6391): the second waits for the first's locks, then reads what
 * the first committed. A parked first filing holds the chain's rows; the correction's filing must
 * not finish until it is released, and only the correction's values remain after both.
 */
@DisplayName("LabMeasurementFilingService concurrency")
@Tag("integration")
@Tag("service")
@Tag("lab")
public class LabMeasurementFilingConcurrencyIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 90_201;
    private static final AtomicInteger LAB_NUMBERS = new AtomicInteger(7_300_000);

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

    /** The real lock methods, then, for the filing that is to be parked, a pause until released. */
    private final class ParkingLocks implements Hl7TextInfoChainLockDao {
        final CountDownLatch holding = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final boolean park;

        ParkingLocks(boolean park) {
            this.park = park;
        }

        @Override
        public List<Hl7TextInfo> lockByAccession(String accession) {
            return locks.lockByAccession(accession);
        }

        @Override
        public List<Hl7TextInfo> lockByFillerOrderNumber(String fillerOrderNumber) {
            return locks.lockByFillerOrderNumber(fillerOrderNumber);
        }

        @Override
        public Hl7TextInfo lockByLabNumber(int labNumber) {
            Hl7TextInfo own = locks.lockByLabNumber(labNumber);
            if (park) {
                holding.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("never released");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return own;
        }
    }

    private LabMeasurementFilingService service(Hl7TextInfoChainLockDao chainLocks, String chain) {
        return new LabMeasurementFilingService(transactions, chainLocks, measurementDao, measurementsExtDao,
                measurementsDeletedDao, lab -> chain);
    }

    private int storedReport(String accession) {
        return new TransactionTemplate(transactions).execute(status -> {
            Hl7TextInfo info = new Hl7TextInfo();
            info.setLabNumber(LAB_NUMBERS.incrementAndGet());
            info.setAccessionNumber(accession);
            info.setFirstName("FAKE");
            info.setLastName("FAKE");
            info.setObrDate(new Date().toString());
            info.setReportStatus("F");
            info.setFinalResultCount(0);
            hibernateTemplate.save(info);
            hibernateTemplate.flush();
            return info.getLabNumber();
        });
    }

    private static LabMeasurementFilingDto filing(int lab, String accession, String value) {
        Measurement m = new Measurement();
        m.setType("A1C");
        m.setDemographicId(PATIENT);
        m.setProviderNo("0");
        m.setDataField(value);
        m.setDateObserved(new Date());
        m.setAppointmentNo(0);
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put("lab_no", String.valueOf(lab));
        return new LabMeasurementFilingDto(String.valueOf(lab), PATIENT, false, accession, null,
                List.of(new FiledMeasurementDto(m, extensions)));
    }

    private List<Measurement> filed(int lab) {
        return new TransactionTemplate(transactions)
                .execute(status -> measurementDao.findByValue("lab_no", String.valueOf(lab)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldMakeTheCorrectionWaitForTheFirstFilingAndLeaveOnlyItsValues_whenTheyOverlap() throws Exception {
        String accession = "ACC-CONC-1";
        int original = storedReport(accession);
        int correction = storedReport(accession);
        ParkingLocks parked = new ParkingLocks(true);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            // The original is filed when it is the only version the chain read can see.
            Future<?> first = threads.submit(() ->
                    service(parked, String.valueOf(original)).file(filing(original, accession, "5.1")));
            assertThat(parked.holding.await(30, TimeUnit.SECONDS)).isTrue();

            // The correction needs the rows the first filing holds: it cannot finish while they are held.
            Future<?> second = threads.submit(() ->
                    service(new ParkingLocks(false), original + "," + correction)
                            .file(filing(correction, accession, "6.9")));
            try {
                second.get(750, TimeUnit.MILLISECONDS);
                throw new AssertionError("the correction filed while the first filing held the chain's rows");
            } catch (TimeoutException waiting) {
                // expected: it is waiting for the locks
            }

            parked.release.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            parked.release.countDown();
            threads.shutdownNow();
        }

        // The correction read the chain after the first filing committed, archived its values and filed its own.
        assertThat(filed(original)).isEmpty();
        assertThat(filed(correction)).extracting(Measurement::getDataField).containsExactly("6.9");
    }
}
