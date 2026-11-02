/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.commn.model.ProviderArchive;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Native archive audit timestamps retain the live record's time of day. */
@Tag("integration")
@Tag("dao")
@Transactional
class ArchiveAuditTimestampIntegrationTest extends CarlosTestBase {
    @Autowired
    private DemographicArchiveDao demographicArchiveDao;
    @Autowired
    private ProviderArchiveDao providerArchiveDao;

    @Test
    void shouldPreserveDemographicArchiveTimeOfDay() throws Exception {
        DemographicArchive archive = new DemographicArchive();
        EntityDataGenerator.generateTestDataForModelClass(archive);
        Date changed = Date.from(Instant.parse("2026-11-01T14:32:17Z"));
        archive.setLastUpdateDate(changed);
        demographicArchiveDao.persist(archive);
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        assertThat(demographicArchiveDao.find(archive.getId()).getLastUpdateDate().getTime())
                .isEqualTo(changed.getTime());
    }

    @Test
    void shouldPreserveProviderArchiveTimeOfDay() throws Exception {
        ProviderArchive archive = new ProviderArchive();
        EntityDataGenerator.generateTestDataForModelClass(archive);
        Date changed = Date.from(Instant.parse("2026-11-01T14:32:17Z"));
        archive.setLastUpdateDate(changed);
        providerArchiveDao.persist(archive);
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        assertThat(providerArchiveDao.find(archive.getId()).getLastUpdateDate().getTime())
                .isEqualTo(changed.getTime());
    }
}
