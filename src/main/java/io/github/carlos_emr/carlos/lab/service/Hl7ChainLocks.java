/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.nio.ByteBuffer;
import java.util.Objects;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.utility.ContentDigests;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Database coordination shared by ingestion and patient-match mutations, before report locks. */
public final class Hl7ChainLocks {
    private Hl7ChainLocks() { }

    public static void lock(String type, String accession) {
        // CLS may link distinct accessions by filler order (glucose tolerance tests).
        // Its legacy membership lookup therefore needs source-wide coordination.
        if ("CLS".equals(type)) {
            SpringUtils.getBean(ProviderLabRoutingDao.class).lockRoutingReport(key(type, "*"));
            return;
        }
        if (accession == null || accession.isBlank()) return; // No accession means no version chain.
        SpringUtils.getBean(ProviderLabRoutingDao.class).lockRoutingReport(key(type, accession.trim()));
    }

    static int key(String type, String accession) {
        // Negative coordination ids cannot collide with positive report ids. A digest collision
        // only serializes unrelated accessions; it never establishes clinical membership.
        String source = Objects.toString(type, "");
        return ByteBuffer.wrap(ContentDigests.sha256(source.length() + ":" + source + accession)).getInt()
                | Integer.MIN_VALUE;
    }

    public static void lockReport(int report) {
        var message = SpringUtils.getBean(Hl7TextMessageDao.class).find(report);
        var info = SpringUtils.getBean(Hl7TextInfoDao.class).findByLabId(report);
        if (message == null || info.size() != 1) throw new IllegalArgumentException("Source report is unavailable");
        lock(message.getType(), info.getFirst().getAccessionNumber());
    }
}
