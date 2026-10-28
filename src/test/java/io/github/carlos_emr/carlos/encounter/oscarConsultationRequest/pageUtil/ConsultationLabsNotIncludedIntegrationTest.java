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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Issue #4693 against the real consultation lab listing on H2: a referral with an HL7 lab and an
 * MDS lab attached. The consultation PDF can draw only the HL7 lab; the MDS lab must come out of
 * the listing as a lab to name, with the discipline and date the listing gives it, or, when the
 * install does not list MDS labs, as an attachment whose details are unavailable.
 *
 * @since 2026-10-16
 */
@DisplayName("Consultation labs the PDF cannot include, from the real lab listing (H2)")
@Tag("integration")
@Tag("lab")
class ConsultationLabsNotIncludedIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 469301;
    private static final int HL7_LAB = 4693001;
    private static final int MDS_LAB = 4693002;
    private static final List<String> LAB_SOURCE_PROPERTIES =
            List.of("CML_LABS", "MDS_LABS", "PATHNET_LABS", "HL7TEXT_LABS", "Epsilon_LABS");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ConsultDocsDao consultDocsDao;

    private final Map<String, String> savedProperties = new HashMap<>();
    private Integer requestId;

    @BeforeEach
    void seedReferralWithHl7AndMdsLab() {
        for (String name : LAB_SOURCE_PROPERTIES) {
            savedProperties.put(name, CarlosProperties.getInstance().getProperty(name));
            CarlosProperties.getInstance().remove(name);
        }
        CarlosProperties.getInstance().setProperty("HL7TEXT_LABS", "yes");

        ConsultationRequest referral = new ConsultationRequest();
        referral.setDemographicId(PATIENT);
        entityManager.persist(referral);
        requestId = referral.getId();

        Hl7TextInfo hl7 = new Hl7TextInfo();
        hl7.setLabNumber(HL7_LAB);
        hl7.setDiscipline("CHEM");
        hl7.setObrDate("2026-01-02 08:30:00");
        hl7.setReportStatus("F");
        hl7.setAccessionNumber("FAKE-4693");
        entityManager.persist(hl7);
        entityManager.persist(new PatientLabRouting(HL7_LAB, "HL7", PATIENT));

        // The MDS listing joins the routing row with mdsMSH and mdsZRG by segment id. A report group
        // outside MICRO and DIAGNOSTIC IMAGING lists as Hem/Chem/Other.
        entityManager.createNativeQuery("INSERT INTO mdsMSH (segmentID, sendingApp, dateTime, type, messageConID,"
                + " processingID, versionID, acceptAckType, appAckType, demographic_no)"
                + " VALUES (" + MDS_LAB + ", 'FAKE-LAB', TIMESTAMP '2026-01-02 08:30:00', 'ORU^R01', 'FAKE-4693',"
                + " 'P', '2.3', 'AL', 'NE', " + PATIENT + ")").executeUpdate();
        entityManager.createNativeQuery("INSERT INTO mdsZRG (segmentID, reportSequence, reportGroupID,"
                + " reportGroupDesc, reportGroupHeading) VALUES (" + MDS_LAB + ", '1', 'CHEM', 'FAKE CHEMISTRY', '')")
                .executeUpdate();
        entityManager.persist(new PatientLabRouting(MDS_LAB, "MDS", PATIENT));

        attach(HL7_LAB, "HL7");
        attach(MDS_LAB, "MDS");
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void restoreLabSourceProperties() {
        savedProperties.forEach((name, value) -> {
            if (value == null) {
                CarlosProperties.getInstance().remove(name);
            } else {
                CarlosProperties.getInstance().setProperty(name, value);
            }
        });
    }

    private void attach(int labNo, String source) {
        ConsultDocs row = new ConsultDocs(requestId, labNo, ConsultDocs.DOCTYPE_LAB, "999998");
        row.setLabType(source);
        consultDocsDao.persist(row);
    }

    private List<LabResultData> attachedLabs() {
        return new CommonLabResultData().populateLabResultsData(null, String.valueOf(PATIENT),
                String.valueOf(requestId), CommonLabResultData.ATTACHED);
    }

    @Test
    @DisplayName("should name the listed MDS lab by discipline and date and keep the HL7 lab for the PDF")
    void shouldNameMdsLabAndRenderHl7Lab_whenInstallListsMdsLabs() {
        CarlosProperties.getInstance().setProperty("MDS_LABS", "yes");

        List<LabResultData> labs = attachedLabs();

        assertThat(AttachmentOwnershipService.renderableLabsOnly(labs))
                .extracting(LabResultData::getSegmentID).containsExactly(String.valueOf(HL7_LAB));
        assertThat(ConsultationLabsNotIncluded.describe(AttachmentOwnershipService.labsNotRenderable(labs), Locale.ENGLISH))
                .containsExactly("MDS lab result: Hem/Chem/Other, 2026-01-02 08:30:00");
    }

    @Test
    @DisplayName("should still name the MDS attachment, without details, when the install does not list MDS labs")
    void shouldNameMdsAttachmentWithoutDetails_whenInstallDoesNotListMdsLabs() {
        List<LabResultData> labs = attachedLabs();

        assertThat(ConsultationLabsNotIncluded.describe(AttachmentOwnershipService.labsNotRenderable(labs), Locale.ENGLISH))
                .containsExactly("MDS lab result (details unavailable)");
    }
}
