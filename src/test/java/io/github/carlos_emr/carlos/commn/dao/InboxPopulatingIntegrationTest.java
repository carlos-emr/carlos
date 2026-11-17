/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.dao.utils.DataUtils;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the legacy inbox population utilities with one synthetic patient and
 * provider. Database effects are rolled back and every generated file is isolated
 * in a temporary directory. Persisted results are asserted because these helpers
 * otherwise log and swallow some import failures.
 */
@DisplayName("Inbox Populating Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
@Isolated("Temporarily overrides document directory and legacy uploader beans")
public class InboxPopulatingIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicDao demographicDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @TempDir
    Path documentDir;

    private final Map<Field, Object> originalUploaderBeans = new LinkedHashMap<>();

    @BeforeEach
    void bindRealUploaderDaos() throws Exception {
        // Matching a patient also populates measurements through Hl7textResultsData.
        // EDocUtil also caches five lazy collaborators used by document population.
        // All three legacy classes can retain mocks from an earlier unit-test context.
        for (Class<?> owner : new Class<?>[]{MessageUploader.class, Hl7textResultsData.class, EDocUtil.class}) {
            for (Field field : owner.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                        && (field.getName().endsWith("Dao") || field.getName().endsWith("Manager")
                            || field.getName().endsWith("Manager2"))) {
                    field.setAccessible(true);
                    originalUploaderBeans.put(field, field.get(null));
                    field.set(null, applicationContext.getBean(field.getType()));
                }
            }
        }
        entityManager.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)")
                .executeUpdate();
    }

    @AfterEach
    void restoreUploaderDaos() throws Exception {
        for (var entry : originalUploaderBeans.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test
    @DisplayName("should populate a patient inbox with persisted lab and document routing")
    void shouldPopulateInboxData_withPersistedResults() throws Exception {
        String fixture = "<data><provider no=\"902355\" gender=\"male\" title=\"Dr.\" "
                + "firstName=\"Inbox\" lastName=\"Fixture\" type=\"doctor\"/>"
                + "<demographic gender=\"F\" title=\"Ms.\" firstName=\"Inbox\" "
                + "lastName=\"PopulationFixture\"/></data>";
        String previousSystemDir = System.getProperty("DOCUMENT_DIR");
        String previousPropertyDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        try {
            System.setProperty("DOCUMENT_DIR", documentDir.toString());
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
            DataUtils.populateDemographicsAndProviders(new ByteArrayInputStream(fixture.getBytes(StandardCharsets.UTF_8)));
            assertThat(demographicDao.getActiveDemographicIds()).hasSize(1);
            Integer demographicId = demographicDao.getActiveDemographicIds().get(0);
            DataUtils.populateProviders();
            assertThat(demographicDao.getDemographic(demographicId.toString()).getProviderNo()).isEqualTo("902355");

            DataUtils.populateLabs();
            DataUtils.populateDocs();
            entityManager.flush();
            assertThat(count("select count(*) from hl7TextMessage")).isEqualTo(10);
            assertThat(count("select count(*) from document")).isEqualTo(10);
            assertThat(count("select count(*) from providerLabRouting where provider_no='902355' and lab_type='DOC'"))
                    .isEqualTo(10);
            assertThat(count("select count(*) from patientLabRouting where demographic_no=" + demographicId))
                    .isEqualTo(10);
            try (Stream<Path> files = Files.list(documentDir)) {
                assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(20);
            }
        } finally {
            if (previousSystemDir == null) System.clearProperty("DOCUMENT_DIR");
            else System.setProperty("DOCUMENT_DIR", previousSystemDir);
            if (previousPropertyDir == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousPropertyDir);
        }
    }

    private long count(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
