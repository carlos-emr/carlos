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
package io.github.carlos_emr.carlos.integration.fhir.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises stored Done Externally data through the real FHIR JSON serializer. */
@Tag("integration")
@Transactional
@DisplayName("Stored immunization report origin contract")
class ImmunizationReportOriginIntegrationTest extends CarlosTestBase {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    @Autowired
    private PreventionDao preventionDao;
    @Autowired
    private PreventionExtDao preventionExtDao;

    @ParameterizedTest
    @CsvSource({"1, provider, External clinician", "30, record, other"})
    void shouldSerializeExpectedOrigin_afterReloadingExternalDose(int daysAgo, String code, String display) throws Exception {
        Prevention prevention = new Prevention();
        prevention.setDemographicId(20001);
        prevention.setPreventionType("Flu");
        prevention.setPreventionDate(Date.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)));
        prevention.setCreatorProviderNo("999998");
        prevention.setLastUpdateDate(new Date());
        prevention.setDeleted(false);
        prevention.setCompletedExternally(true);
        preventionDao.persist(prevention);
        prevention.setProviderName("External clinician");
        for (PreventionExt ext : prevention.getPreventionExts()) {
            ext.setPreventionId(prevention.getId());
            preventionExtDao.persist(ext);
        }
        entityManager.flush();
        entityManager.clear();

        Prevention reloaded = preventionDao.find(prevention.getId());
        assertThat(reloaded).isNotSameAs(prevention);
        assertThat(reloaded.isCompletedExternally()).isTrue();
        Immunization<Prevention> resource = new Immunization<>(reloaded);
        JsonNode json = new ObjectMapper().readTree(resource.getFhirJSON());

        assertThat(json.path("primarySource").asBoolean()).isFalse();
        JsonNode coding = json.path("reportOrigin").path("coding").get(0);
        assertThat(coding.path("system").asText()).isEqualTo("http://hl7.org/fhir/immunization-origin");
        assertThat(coding.path("code").asText()).isEqualTo(code);
        assertThat(coding.path("display").asText()).isEqualTo(display);
    }
}
