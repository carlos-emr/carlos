/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.ConsultationRequestArchive;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExtArchive;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

/** Real ORM round trips against the migrated ON and BC archive tables. */
@Tag("integration")
@Tag("dao")
@Tag("flyway-schema")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
class ConsultationArchiveDaoIntegrationTest extends CarlosTestBase {
    @Autowired
    private ConsultationRequestArchiveDao archiveDao;
    @Autowired
    private ConsultationRequestExtArchiveDao extensionDao;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    void shouldRoundTripLongImportedTextThroughArchiveDaos() {
        assertThat(System.getenv("CARLOS_TEST_MARIADB_DAO")).isEqualTo("true");
        // 80,000 UTF-8 bytes exceeds both VARCHAR(255) and the TEXT byte limit.
        String text = "é".repeat(40_000);
        ConsultationRequestArchive archive = new ConsultationRequestArchive();
        archive.setRequestId(5661);
        archive.setAllergies(text);
        archive.setClinicalInfo(text);
        archive.setConcurrentProblems(text);
        archive.setCurrentMeds(text);
        archive.setLetterheadAddress(text);
        archive.setReasonForReferral(text);
        archive.setStatusText(text);
        archiveDao.persist(archive);
        archiveDao.flush();

        ConsultationRequestExtArchive extension = new ConsultationRequestExtArchive();
        extension.setOriginalId(1);
        extension.setRequestId(5661);
        extension.setConsultationRequestArchiveId(archive.getId());
        extension.setKey("legacyText");
        extension.setValue(text);
        extension.setDateCreated(java.sql.Date.valueOf("2026-11-01"));
        extensionDao.persist(extension);
        extensionDao.flush();
        Integer archiveId = archive.getId();
        Integer extensionId = extension.getId();
        entityManager.clear();

        ConsultationRequestArchive reloaded = archiveDao.find(archiveId);
        assertThat(reloaded).isNotSameAs(archive);
        assertThat(reloaded)
                .extracting(ConsultationRequestArchive::getAllergies,
                        ConsultationRequestArchive::getClinicalInfo,
                        ConsultationRequestArchive::getConcurrentProblems,
                        ConsultationRequestArchive::getCurrentMeds,
                        ConsultationRequestArchive::getLetterheadAddress,
                        ConsultationRequestArchive::getReasonForReferral,
                        ConsultationRequestArchive::getStatusText)
                .containsExactly(text, text, text, text, text, text, text);
        ConsultationRequestExtArchive reloadedExtension = extensionDao.find(extensionId);
        assertThat(reloadedExtension).isNotSameAs(extension);
        assertThat(reloadedExtension.getConsultationRequestArchiveId()).isEqualTo(archiveId);
        assertThat(reloadedExtension.getValue()).isEqualTo(text);
    }
}
