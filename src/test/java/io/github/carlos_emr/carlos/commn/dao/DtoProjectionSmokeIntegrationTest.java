/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Parse-and-execute smoke tests for the 8 DAO DTO projection methods added in
 * the DTO projection layer. Each test invokes the DAO method against an empty
 * H2 database and asserts:
 *
 * <ul>
 *   <li>The JPQL {@code SELECT NEW} query parses successfully — catches HBM
 *       property-name typos (including the PascalCase HBM quirk for Demographic
 *       and Provider), constructor arity mismatches, and type incompatibilities.
 *   <li>Execution against an empty table returns a non-null empty list, not
 *       an exception.
 * </ul>
 *
 * <p><b>What this does NOT catch:</b> field reorders within same-typed argument
 * sequences (e.g., swapping two adjacent {@code String} columns). Those require
 * per-DAO round-trip tests with populated data — the {@code CaseManagementIssueDAOIntegrationTest}
 * FindIssueDTOsByDemographicNo nested class is the prototype for that deeper
 * coverage and can be replicated per DAO as a follow-up.</p>
 *
 * <p>This file is the cheap-but-broad safety net that catches the class of bugs
 * Hibernate would throw at query-execution time rather than at deploy time.</p>
 *
 * @since 2026-04-12
 */
@DisplayName("DTO Projection Smoke Tests — 8 DAOs")
@Tag("integration")
@Tag("dao")
@Tag("dto")
@Transactional
public class DtoProjectionSmokeIntegrationTest extends CarlosTestBase {

    @Autowired
    private AllergyDao allergyDao;

    @Autowired
    private OscarAppointmentDao appointmentDao;

    @Autowired
    private BillingONCHeader1Dao billingONCHeader1Dao;

    @Autowired
    private ConsultationRequestDao consultationRequestDao;

    @Autowired
    private DocumentDao documentDao;

    @Autowired
    private DrugDao drugDao;

    @Autowired
    private PreventionDao preventionDao;

    @Autowired
    @Qualifier("CaseManagementNoteDAO")
    private CaseManagementNoteDAO caseManagementNoteDAO;

    private static final Integer NON_EXISTENT_DEMO = 999_999;
    private static final String NON_EXISTENT_DEMO_STR = "999999";
    private static final String PROVIDER_NO = "999990";

    @Test
    @DisplayName("AllergyDao.findAllergyDTOsByDemographicNo should parse and return empty for unknown demographic")
    void allergyDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(allergyDao.findAllergyDTOsByDemographicNo(NON_EXISTENT_DEMO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OscarAppointmentDao.findDayAppointmentDTOs should parse and return empty for unknown provider/date")
    void appointmentDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(appointmentDao.findDayAppointmentDTOs(new Date(), PROVIDER_NO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BillingONCHeader1Dao.findBillingDTOsByDemographicNo should parse and return empty for unknown demographic")
    void billingDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(billingONCHeader1Dao.findBillingDTOsByDemographicNo(NON_EXISTENT_DEMO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ConsultationRequestDao.findConsultationDTOsByDemographicId should parse and return empty for unknown demographic")
    void consultationDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(consultationRequestDao.findConsultationDTOsByDemographicId(NON_EXISTENT_DEMO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DocumentDao.findDocumentDTOsByDemographicNo should parse and return empty for unknown demographic")
    void documentDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(documentDao.findDocumentDTOsByDemographicNo(NON_EXISTENT_DEMO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DrugDao.findDrugDTOsByDemographicId should parse and return empty for unknown demographic")
    void drugDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(drugDao.findDrugDTOsByDemographicId(NON_EXISTENT_DEMO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PreventionDao.findPreventionDTOsByDemographicId should parse and return empty for unknown demographic")
    void preventionDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(preventionDao.findPreventionDTOsByDemographicId(NON_EXISTENT_DEMO)).isEmpty()
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CaseManagementNoteDAO.findNoteDTOsByDemographicNo should parse and return empty for unknown demographic")
    void noteDao_projectionShouldParseAndExecute() {
        assertThatCode(() ->
                assertThat(caseManagementNoteDAO.findNoteDTOsByDemographicNo(NON_EXISTENT_DEMO_STR)).isEmpty()
        ).doesNotThrowAnyException();
    }
}
