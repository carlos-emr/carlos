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
package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.*;
import io.github.carlos_emr.carlos.billing.CA.BC.model.*;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Parse and persist real synthetic HL7, then follow the same joins as the inbox and chart. */
@Tag("integration")
@Tag("lab")
class PathnetImportIntegrationTest extends CarlosTestBase {
    @Autowired private Hl7PidDao pids;
    @Autowired private Hl7ObrDao obrs;
    @Autowired private Hl7ObxDao obxs;
    @Autowired private Hl7LinkDao links;
    @Autowired private DemographicDao demographics;
    @Autowired private PatientLabRoutingDao routing;
    @PersistenceContext private EntityManager em;

    private Demographic patient(String phn, String status) {
        Demographic demo = new Demographic();
        demo.setFirstName("FAKE-PATHNET");
        demo.setLastName("FAKE-5052");
        demo.setHin(phn);
        demo.setHcType("BC");
        demo.setPatientStatus(status);
        demo.setYearOfBirth("1980");
        demo.setMonthOfBirth("01");
        demo.setDateOfBirth("15");
        demo.setSex("M");
        demographics.save(demo);
        hibernateTemplate.flush();
        return demo;
    }

    private Hl7Pid upload(String phn, String dob) throws Exception {
        Message message = new Message("now");
        message.Parse("MSH|^~\\&|FAKE|PATHNET|CARLOS|TEST|20261023090000||ORU^R01|FAKE5052|P|2.3\n"
                + "PID|1|" + phn + "|||DIFFERENT^NAME||" + dob + "|F\n"
                + "ORC|RE||FAKE5052\n"
                + "OBR|1||FAKE5052|GLU^Glucose||20261023090000|20261023090000\n"
                + "OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.6-6.0|N|||F\n");
        message.ToDatabase();
        em.flush();
        return em.createQuery("FROM Hl7Pid p WHERE p.externalId = :phn ORDER BY p.id DESC", Hl7Pid.class)
                .setParameter("phn", phn).setMaxResults(1).getSingleResult();
    }

    @ParameterizedTest
    @ValueSource(strings = {"19800115", "1980011512", "198001151230", "19800115123000", "19800115123000.1234-0800"})
    void shouldStoreConnectedSegmentsAndBothPatientLinks_whenPhnAndDateMatch(String birthDate) throws Exception {
        Demographic demo = patient("5052000001", "AC");
        Hl7Pid pid = upload("5052000001", birthDate);
        assertThat(pid.getMessageId()).isPositive();
        assertThat(pid.getDateOfBirth()).hasYear(1980).hasMonth(1).hasDayOfMonth(15);
        Hl7Obr obr = obrs.findByPid(pid.getId()).get(0);
        assertThat(obxs.findByObrId(obr.getId())).singleElement()
                .satisfies(obx -> assertThat(obx.getObservationResults()).isEqualTo("5.2"));
        assertThat(em.createQuery("FROM Hl7Orc o WHERE o.pidId = :pid", Hl7Orc.class)
                .setParameter("pid", pid.getId()).getResultList()).hasSize(1);
        assertThat(links.find(pid.getId()).getDemographicNo()).isEqualTo(demo.getDemographicNo());
        assertThat(routing.findByLabNoAndLabType(pid.getMessageId(), "BCP")).singleElement()
                .satisfies(route -> assertThat(route.getDemographicNo()).isEqualTo(demo.getDemographicNo()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"19810115", "19800230", "1980", "garbage", "19800115junk", "19800115999999"})
    void shouldStoreVisibleUnmatchedLab_whenSuppliedBirthDateDoesNotMatch(String dob) throws Exception {
        patient("5052000002", "AC");
        Hl7Pid pid = upload("5052000002", dob);
        assertThat(links.find(pid.getId()).getDemographicNo()).isZero();
        assertThat(links.findLabs()).anySatisfy(row -> assertThat(((Hl7Pid) row[0]).getId()).isEqualTo(pid.getId()));
    }

    @Test
    void shouldMatchUniquePhn_whenBirthDateIsAbsent() throws Exception {
        Demographic demo = patient("5052000003", "AC");
        Hl7Pid pid = upload("5052000003", "");
        assertThat(links.find(pid.getId()).getDemographicNo()).isEqualTo(demo.getDemographicNo());
    }

    @Test
    void shouldLeaveUnmatched_whenPhnIsAmbiguous() throws Exception {
        patient("5052000004", "AC");
        patient("5052000004", "AC");
        Hl7Pid pid = upload("5052000004", "19800115");
        assertThat(links.find(pid.getId()).getDemographicNo()).isZero();
    }

    @Test
    void shouldLeaveUnmatched_whenPhnMissingOrOnlyInactivePatientExists() throws Exception {
        patient("5052000005", "IN");
        assertThat(links.find(upload("5052000005", "19800115").getId()).getDemographicNo()).isZero();
        assertThat(links.find(upload("", "19800115").getId()).getDemographicNo()).isZero();
    }

    @Test
    void shouldListLabWithoutLinkOrOrder_whenLegacyRowsHaveNoLink() {
        Hl7Pid pid = new Hl7Pid();
        pid.setPatientName("FAKE-5052^UNLINKED");
        pids.persist(pid);
        em.flush();
        assertThat(links.findLabs()).anySatisfy(row -> {
            assertThat(((Hl7Pid) row[0]).getId()).isEqualTo(pid.getId());
            assertThat(row[1]).isNull();
            assertThat(row[2]).isNull();
            assertThat(row[3]).isNull();
        });
    }

    @Test
    void shouldTreatWildcardPhnAsLiteral_whenLookingUpPatients() {
        patient("5052000006", "AC");
        assertThat(demographics.findActiveByExactHin("5052%")).isEmpty();
        assertThat(demographics.findActiveByExactHin(" ")).isEmpty();
    }
    @Test
    void shouldSynchronizeLegacyLink_whenInboxMatchesPatient() throws Exception {
        Demographic demo = patient("5052000007", "AC");
        Hl7Pid pid = upload("5052999999", "19800115");
        assertThat(links.find(pid.getId()).getDemographicNo()).isZero();
        assertThat(io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData.updatePatientLabRouting(
                java.util.List.of(pid.getMessageId()), demo.getDemographicNo().toString(), "BCP")).isTrue();
        em.flush();
        assertThat(links.find(pid.getId()).getDemographicNo()).isEqualTo(demo.getDemographicNo());
        assertThat(links.find(pid.getId()).getStatus()).isEqualTo("N");
    }

    @Test
    void shouldSearchPickerByName_whenLegacyDisplayColumnsAreUsed() {
        Demographic demo = patient("5052000008", "AC");
        assertThat(demographics.findByField("LastName", "FAKE-5052%", "LastName", 0))
                .extracting(Demographic::getDemographicNo).contains(demo.getDemographicNo());
    }

    @Test
    void shouldSearchPickerByPatientNumber_whenNumberIsSelected() {
        Demographic demo = patient("5052000009", "AC");
        assertThat(demographics.findByField("DemographicNo", demo.getDemographicNo(), "DemographicNo", 0))
                .extracting(Demographic::getDemographicNo).containsExactly(demo.getDemographicNo());
    }

    @Test
    void shouldRenderInitialPicker_whenSearchIsEmpty() {
        patient("5052000010", "AC");
        assertThat(demographics.findByField(null, "", "LastName", 0)).isNotEmpty();
    }

    @Test
    void shouldUseSafeDefaultPickerColumns_whenColumnInputIsInvalid() {
        Demographic demo = patient("5052000011", "AC");
        assertThat(demographics.findByField("invalid", "FAKE-5052%", "invalid", 0))
                .extracting(Demographic::getDemographicNo).contains(demo.getDemographicNo());
    }

}
