/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.nio.ByteBuffer;
import java.util.Objects;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.utility.ContentDigests;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Database coordination shared by ingestion and patient-match mutations, before report locks.
 * @since 2026-11-09
 */
public final class Hl7ChainLocks {
    private Hl7ChainLocks() { }

    public static void lock(String type, String accession) {
        // CLS may link distinct accessions by filler order (glucose tolerance tests).
        // Its legacy membership lookup therefore needs source-wide coordination.
        if ("CLS".equals(type)) {
            acquire(key(type, "*"));
            return;
        }
        if (accession == null || accession.isBlank()) return; // No accession means no version chain.
        acquire(key(type, accession.trim()));
    }

    private static void acquire(int key) {
        try {
            SpringUtils.getBean(ProviderLabRoutingDao.class).lockRoutingReport(key);
        } catch (RuntimeException failure) {
            // No raw accession, patient data or coordination digest in operational logs.
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                    "HL7 chain coordination failed: {}", failure.getClass().getSimpleName());
            throw failure;
        }
    }

    static int key(String type, String accession) {
        // Negative coordination ids cannot collide with positive report ids. A digest collision
        // only serializes unrelated accessions; it never establishes clinical membership.
        String source = Objects.toString(type, "");
        return ByteBuffer.wrap(ContentDigests.sha256(source.length() + ":" + source + accession)).getInt()
                | Integer.MIN_VALUE;
    }

    /** Acquire a batch's coordination keys in one order, before any clinical row locks. */
    public static void lockReports(java.util.Collection<Integer> reports) {
        java.util.Set<Integer> keys = new java.util.TreeSet<>();
        for (int report : reports) {
            var message = SpringUtils.getBean(Hl7TextMessageDao.class).find(report);
            var info = SpringUtils.getBean(Hl7TextInfoDao.class).findByLabId(report);
            if (message == null && info.isEmpty()) continue; // An orphan cannot be changed by source ingestion.
            if (message == null || info.size() != 1) throw new IllegalArgumentException("Source report is unavailable");
            String accession = info.getFirst().getAccessionNumber();
            if ("CLS".equals(message.getType())) keys.add(key("CLS", "*"));
            else if (accession != null && !accession.isBlank()) keys.add(key(message.getType(), accession.trim()));
        }
        keys.forEach(Hl7ChainLocks::acquire);
    }

    public static void lockReport(int report) {
        var message = SpringUtils.getBean(Hl7TextMessageDao.class).find(report);
        var info = SpringUtils.getBean(Hl7TextInfoDao.class).findByLabId(report);
        if (message == null || info.size() != 1) throw new IllegalArgumentException("Source report is unavailable");
        lock(message.getType(), info.getFirst().getAccessionNumber());
    }
}
