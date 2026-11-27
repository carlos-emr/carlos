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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

/**
 * The row locks of a lab chain (#6391): joined to the caller's transaction, ordered by lab number,
 * and exact in what they select. The locking itself is shown on a real InnoDB in
 * {@code Hl7TextInfoChainLockMariaDbIntegrationTest}: H2 accepts the statements but the
 * snapshot ordering the filing depends on is MariaDB's.
 */
@DisplayName("Hl7TextInfoChainLockDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class Hl7TextInfoChainLockDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    protected Hl7TextInfoChainLockDao locks;

    private static final java.util.concurrent.atomic.AtomicInteger LAB_NUMBERS =
            new java.util.concurrent.atomic.AtomicInteger(7_100_000);

    /**
     * Stored through Hibernate directly: the id is an identity the JPA persist does not report back in H2.
     * The lab number is the report's own (the message id), so it is given here.
     */
    protected Hl7TextInfo stored(String accession, String filler) {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setLabNumber(LAB_NUMBERS.incrementAndGet());
        info.setAccessionNumber(accession);
        info.setFillerOrderNum(filler);
        info.setFirstName("FAKE");
        info.setLastName("FAKE");
        info.setObrDate("2026-11-27 12:00:00");
        info.setReportStatus("F");
        info.setFinalResultCount(0);
        hibernateTemplate.save(info);
        hibernateTemplate.flush();
        return info;
    }

    @Test
    void shouldReturnTheAccessionsRowsLowestLabNumberFirst_whenLockingByAccession() {
        Hl7TextInfo first = stored("ACC-LOCK-1", null);
        Hl7TextInfo second = stored("ACC-LOCK-1", null);
        stored("ACC-LOCK-OTHER", null);

        List<Hl7TextInfo> rows = locks.lockByAccession("ACC-LOCK-1");

        assertThat(rows).extracting(Hl7TextInfo::getLabNumber)
                .containsExactly(first.getLabNumber(), second.getLabNumber())
                .isSorted();
    }

    @Test
    void shouldIncludeRowsOfOtherAccessions_whenLockingByFillerOrderNumber() {
        Hl7TextInfo one = stored("ACC-GTT-1", "FILL-GTT");
        Hl7TextInfo two = stored("ACC-GTT-2", "FILL-GTT");
        stored("ACC-GTT-3", "FILL-OTHER");

        List<Hl7TextInfo> rows = locks.lockByFillerOrderNumber("FILL-GTT");

        assertThat(rows).extracting(Hl7TextInfo::getLabNumber)
                .containsExactly(one.getLabNumber(), two.getLabNumber());
    }

    @Test
    void shouldLockNothing_whenTheAccessionOrFillerNumberIsBlank() {
        stored(null, null);
        stored("", "");

        assertThat(locks.lockByAccession(null)).isEmpty();
        assertThat(locks.lockByAccession("  ")).isEmpty();
        assertThat(locks.lockByFillerOrderNumber(null)).isEmpty();
        assertThat(locks.lockByFillerOrderNumber("")).isEmpty();
    }

    @Test
    void shouldMatchNoRow_whenTheReportHasNoAccession() {
        Hl7TextInfo noAccession = stored(null, null);

        assertThat(locks.lockByAccession("ACC-NOBODY")).isEmpty();
        // Its own row is still lockable, which is why the own-row lock exists.
        assertThat(locks.lockByLabNumber(noAccession.getLabNumber()).getLabNumber())
                .isEqualTo(noAccession.getLabNumber());
    }

    @Test
    void shouldReturnNull_whenTheReportIsNotStored() {
        assertThat(locks.lockByLabNumber(2_000_000_000)).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRefuseEveryLock_whenThereIsNoTransaction() {
        assertThatThrownBy(() -> locks.lockByAccession("ACC-1")).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> locks.lockByFillerOrderNumber("FILL-1"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> locks.lockByLabNumber(1)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
