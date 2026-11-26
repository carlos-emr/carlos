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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoChainLockDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Files one report's measurements as a single transaction that is serialized per lab chain and
 * can be repeated (#6391).
 *
 * <p><b>Atomic.</b> The archive of the replaced rows, every measurement and every extension are
 * written in one transaction: a failure on any result leaves the chart as it was, and a retry
 * files one complete set.</p>
 *
 * <p><b>Serialized.</b> The filing first locks the report rows of its chain through
 * {@link Hl7TextInfoChainLockDao} (accession, filler order number, its own row), and only then
 * reads the chain and the existing measurements. A second filing of the same report, or of its
 * correction, waits for the first and then reads what the first committed, so a late older version
 * finds it is no longer current and files nothing. The locks are database row locks, so a second
 * Tomcat node waits too.</p>
 *
 * <p><b>Repeatable.</b> The measurements of every version of the chain, the report's own included,
 * are archived before the new ones are written, so filing the same report twice leaves one set.</p>
 *
 * <p><b>After the upload commits.</b> Every upload entry runs its handlers inside a
 * {@code READ_COMMITTED} transaction whose report rows are not yet visible to anyone else, and a
 * locking read inside it would wait on a concurrent upload's own uncommitted row: a deadlock that
 * rolls back the whole upload. {@link #fileAfterCommit} therefore files in a NEW transaction
 * ({@code REQUIRES_NEW}, REPEATABLE READ) once the upload has committed. A failure there cannot
 * undo the upload: it is retried, logged at ERROR and audited, and never thrown. There is no repair
 * path other than a user re-matching the lab, so the log and the audit row are how an operator finds
 * a lab left without measurements.</p>
 *
 * <p>Database connections: a filing after commit holds a second connection while the upload's own
 * is still bound to the thread, so N simultaneous uploads can hold 2N of the pool's connections
 * ({@code db_max_active}, 32 by default). The backoff between attempts is milliseconds, filing runs
 * on the uploading thread, and the number of simultaneous uploads is bounded by the servlet and web
 * service thread pools (see the upload connector's {@code maxThreads}), so a connection is never held
 * long on a lock failure.</p>
 */
@Service
public class LabMeasurementFilingService {

    private static final Logger logger = MiscUtils.getLogger();

    /** Whole-transaction attempts on a lock failure (a deadlock or a lock wait timeout). */
    static final int MAX_ATTEMPTS = 3;

    /** The wait before the second attempt; doubled for the third. */
    static final long BACKOFF_MILLIS = 50;

    /** How a filing transaction ended. */
    enum Result {
        /** The report's measurements are filed. */
        FILED,
        /** A newer version of the report exists: nothing was archived or filed. */
        NOT_CURRENT,
        /** The report is gone (its upload was cleaned up before this ran): nothing was written. */
        LAB_REMOVED
    }

    private final PlatformTransactionManager transactions;
    private final Hl7TextInfoChainLockDao locks;
    private final MeasurementDao measurementDao;
    private final MeasurementsExtDao measurementsExtDao;
    private final MeasurementsDeletedDao measurementsDeletedDao;
    private final UnaryOperator<String> matchingLabs;

    // Two constructors: Spring is told which one builds the bean (without this it finds no default
    // constructor and the application does not start).
    @Autowired
    public LabMeasurementFilingService(
            PlatformTransactionManager transactions,
            Hl7TextInfoChainLockDao locks,
            MeasurementDao measurementDao,
            MeasurementsExtDao measurementsExtDao,
            MeasurementsDeletedDao measurementsDeletedDao) {
        this(transactions, locks, measurementDao, measurementsExtDao, measurementsDeletedDao,
                Hl7textResultsData::getMatchingLabs);
    }

    /** For tests: the chain of a report, as a comma-separated list of lab numbers. */
    LabMeasurementFilingService(
            PlatformTransactionManager transactions,
            Hl7TextInfoChainLockDao locks,
            MeasurementDao measurementDao,
            MeasurementsExtDao measurementsExtDao,
            MeasurementsDeletedDao measurementsDeletedDao,
            UnaryOperator<String> matchingLabs) {
        this.transactions = transactions;
        this.locks = locks;
        this.measurementDao = measurementDao;
        this.measurementsExtDao = measurementsExtDao;
        this.measurementsDeletedDao = measurementsDeletedDao;
        this.matchingLabs = matchingLabs;
    }

    /**
     * Files the report now. Joins the caller's transaction when there is one (the caller owns its
     * retry and its failure); otherwise runs its own, retrying the whole transaction on a lock
     * failure. Any other failure is thrown, and nothing of the filing is kept.
     *
     * @param dto the report and its results
     * @return how the filing ended
     */
    Result file(LabMeasurementFilingDto dto) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return fileInTransaction(dto);
        }
        return attempts(dto, TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Files the report once the caller's transaction has committed, in a transaction of its own; or
     * at once when the caller has none. After the commit a failure is never thrown (it would make a
     * committed upload look failed): it is retried, logged and audited. At once, a failure is thrown.
     *
     * @param dto the report and its results
     */
    public void fileAfterCommit(LabMeasurementFilingDto dto) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            file(dto);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                filedAfterCommit(dto);
            }
        });
    }

    /** What runs after the commit: never throws, except for a {@link VirtualMachineError}. */
    void filedAfterCommit(LabMeasurementFilingDto dto) {
        try {
            Result result = attempts(dto, TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            if (result == Result.LAB_REMOVED) {
                logger.info("Lab {} was removed before its measurements were filed; nothing was filed",
                        LogSafe.sanitize(dto.labNo()));
            }
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError error) {
                throw error;
            }
            reportFailure(dto, failure);
        }
    }

    private void reportFailure(LabMeasurementFilingDto dto, Throwable failure) {
        // Neither the message nor the cause's message: a database error can quote the values it was
        // writing, which are a patient's results.
        try {
            Throwable root = failure;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            logger.error("Lab {} was committed but its measurements were not filed ({}, caused by {}); "
                            + "re-match the lab to file them",
                    LogSafe.sanitize(dto.labNo()), failure.getClass().getName(), root.getClass().getName());
        } catch (Throwable ignored) {
            // The failure path must not itself throw into the committing caller.
        }
        try {
            LogAction.addLog(Provider.SYSTEM_PROVIDER_NO, LogConst.UPDATE, LogConst.CON_HL7_LAB, dto.labNo(),
                    null, dto.patient() == null ? null : String.valueOf(dto.patient()),
                    "lab measurement filing failed");
        } catch (Throwable ignored) {
            // The audit row is best effort here; the ERROR line above is the record that remains.
        }
    }

    /** One transaction per attempt; a lock failure is retried with a short backoff. */
    private Result attempts(LabMeasurementFilingDto dto, int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(propagation);
        long backoff = BACKOFF_MILLIS;
        for (int attempt = 1; ; attempt++) {
            try {
                return template.execute(status -> fileInTransaction(dto));
            } catch (RuntimeException lockFailure) {
                if (!isLockFailure(lockFailure) || attempt >= MAX_ATTEMPTS) {
                    throw lockFailure;
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException interrupted) {
                    // Someone wants this thread to stop: keep the flag and give the failure up.
                    Thread.currentThread().interrupt();
                    throw lockFailure;
                }
                backoff *= 2;
            }
        }
    }

    /**
     * Whether a failure is the database refusing a lock: a deadlock victim or a lock wait timeout, which a
     * second attempt of the whole transaction can get past. Nothing in this application translates JPA
     * exceptions to Spring's ({@link PessimisticLockingFailureException}), so a real deadlock arrives as
     * Jakarta's or Hibernate's own exception, or as the driver's {@link SQLException}: the whole cause
     * chain is read, for any of them.
     */
    public static boolean isLockFailure(Throwable failure) {
        Set<Throwable> seen = new HashSet<>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || cause instanceof jakarta.persistence.PessimisticLockException
                    || cause instanceof jakarta.persistence.LockTimeoutException
                    || cause instanceof org.hibernate.PessimisticLockException
                    || cause instanceof org.hibernate.exception.LockAcquisitionException
                    || cause instanceof org.hibernate.exception.LockTimeoutException) {
                return true;
            }
            if (cause instanceof SQLException sql) {
                // 40001: serialization failure / deadlock; MariaDB 1213 deadlock, 1205 lock wait timeout.
                if ("40001".equals(sql.getSQLState()) || sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The filing itself, in the caller's transaction. Locks first, then reads, then writes.
     */
    Result fileInTransaction(LabMeasurementFilingDto dto) {
        // Locks BEFORE any plain read: under REPEATABLE READ a locking read does not open the
        // consistent-read snapshot, so the reads that follow see what the thread that held these
        // rows committed. Always the same order: accession, filler order number, the report's own row.
        locks.lockByAccession(dto.accession());
        locks.lockByFillerOrderNumber(dto.fillerOrderNumber());
        Hl7TextInfo own = locks.lockByLabNumber(Integer.parseInt(dto.labNo().trim()));
        if (own == null) {
            return Result.LAB_REMOVED;
        }

        String[] chain = matchingLabs.apply(dto.labNo()).split(",");
        if (!Arrays.asList(chain).contains(dto.labNo())) {
            throw new IllegalStateException("The incoming lab is absent from its resolved version chain");
        }
        // A late historical delivery must not replace the current version's measurements.
        if (!dto.labNo().equals(Hl7textResultsData.currentVersion(chain, dto.revisionOrdered()))) {
            return Result.NOT_CURRENT;
        }

        archive(dto, new LinkedHashSet<>(Arrays.asList(chain)));
        store(dto.measurements());
        return Result.FILED;
    }

    /**
     * Archives, then removes, the measurements of every version of the chain, the report's own
     * included, so the chart holds exactly one version's values and a repeat does not double them.
     * Only this patient's values: the chain is resolved by accession number alone, so a recycled
     * accession can put another patient's report in it.
     */
    private void archive(LabMeasurementFilingDto dto, Set<String> versions) {
        Integer patient = dto.patient();
        List<MeasurementsDeleted> deleted = new ArrayList<>();
        List<Measurement> removed = new ArrayList<>();
        for (String version : versions) {
            boolean own = version.equals(dto.labNo());
            for (Measurement m : measurementDao.findByValue("lab_no", version)) {
                Integer owner = m.getDemographicId();
                if (patient == null) {
                    // No patient to compare with: only the report's own values are replaced.
                    if (!own) {
                        continue;
                    }
                } else if (owner != null ? !patient.equals(owner) : !own) {
                    // Another patient's values are never touched; nor are values with no patient,
                    // other than the report's own.
                    continue;
                }
                deleted.add(new MeasurementsDeleted(m));
                removed.add(m);
            }
        }
        measurementsDeletedDao.batchPersistAtomically(deleted);
        measurementDao.batchRemoveAtomically(removed);
    }

    private void store(List<FiledMeasurementDto> results) {
        List<MeasurementsExt> extensions = new ArrayList<>();
        for (FiledMeasurementDto result : results) {
            measurementDao.persist(result.measurement());
            int measurementId = result.measurement().getId();
            for (Map.Entry<String, String> extension : result.extensions().entrySet()) {
                MeasurementsExt me = new MeasurementsExt();
                me.setMeasurementId(measurementId);
                me.setKeyVal(extension.getKey());
                me.setVal(extension.getValue());
                extensions.add(me);
            }
        }
        // Every extension in one list, the "blocked" one included: a failure rolls back them all.
        measurementsExtDao.batchPersistAtomically(extensions, 50);
    }
}
