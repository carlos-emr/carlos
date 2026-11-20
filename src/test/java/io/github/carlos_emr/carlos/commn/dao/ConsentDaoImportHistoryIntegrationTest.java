/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Imported consent histories retain retired rows alongside the deciding record. */
@Tag("integration")
@Tag("dao")
@Transactional
class ConsentDaoImportHistoryIntegrationTest extends CarlosTestBase {
    @Autowired
    private ConsentDao consentDao;
    @Autowired
    private ConsentTypeDao consentTypeDao;

    @Test
    void shouldIgnoreDeletedHistoryInBothActiveLookups() {
        ConsentType type = type("o19_deleted_history");
        Consent live = consent(type, false, false, 1000L);
        consent(type, true, true, 2000L);
        assertThat(consentDao.findByDemographicAndConsentTypeId(550301, type.getId()).getId())
                .isEqualTo(live.getId());
        assertThat(consentDao.findByDemographicAndConsentType(550301, type.getType()).getId())
                .isEqualTo(live.getId());
    }

    @Test
    void shouldKeepLiveOptOutDistinctFromNewerRetiredHistory() {
        ConsentType type = type("o19_duplicate_history");
        Consent optout = consent(type, true, false, 1000L);
        consent(type, false, true, 2000L);
        assertThat(consentDao.findByDemographicAndConsentTypeId(550301, type.getId()).getId())
                .isEqualTo(optout.getId());
        assertThat(consentDao.findByDemographicAndConsentType(550301, type.getType()).getId())
                .isEqualTo(optout.getId());
    }

    private ConsentType type(String code) {
        ConsentType type = new ConsentType();
        type.setName(code);
        type.setType(code);
        type.setActive(true);
        consentTypeDao.persist(type);
        return type;
    }

    private Consent consent(ConsentType type, boolean optout, boolean deleted, long edited) {
        Consent record = new Consent();
        record.setDemographicNo(550301);
        record.setConsentTypeId(type.getId());
        record.setExplicit(true);
        record.setOptout(optout);
        record.setDeleted(deleted);
        record.setEditDate(new Date(edited));
        consentDao.persist(record);
        hibernateTemplate.flush();
        return record;
    }
}
