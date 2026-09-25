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
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;
import io.github.carlos_emr.carlos.commn.model.MdsMSH;
import io.github.carlos_emr.carlos.commn.model.MdsZRG;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lab identifier consistency for the legacy CML and MDS consultation attachment listings: an
 * attachment matches a candidate only on the same lab number and the same lab type.
 *
 * <p>consult_docs.document_no holds a lab number ({@code ConsultDocsDao.findLabs} joins it to
 * {@code patient_lab_routing.lab_no}), and the attachment window, Ocean and the ownership check all
 * use the lab number (segmentID). These listings used to compare the stored number with the
 * candidate's routing row id instead, so they reported a different lab, or none, as attached.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("MDSResultsData consultation lab listing")
@Tag("unit")
@Tag("lab")
@Tag("consultation")
class MDSResultsDataConsultLabUnitTest extends CarlosUnitTestBase {

    private static final String DEMOGRAPHIC_NO = "123";
    private static final String CONSULT_ID = "456";

    private ConsultDocsDao consultDocsDao;
    private LabPatientPhysicianInfoDao labPatientPhysicianInfoDao;
    private PatientLabRoutingDao patientLabRoutingDao;

    @BeforeEach
    void setUp() {
        consultDocsDao = mock(ConsultDocsDao.class);
        labPatientPhysicianInfoDao = mock(LabPatientPhysicianInfoDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        registerMock(ConsultDocsDao.class, consultDocsDao);
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(LabPatientPhysicianInfoDao.class, labPatientPhysicianInfoDao);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
        registerMock(OscarLogDao.class, mock(OscarLogDao.class));
    }

    /** Lab {@code labNo} is attached, and findLabs joins it to these routings of {@code labNo}. */
    private void attach(int labNo, PatientLabRouting... routings) {
        List<Object[]> attachedRows = new ArrayList<>();
        for (PatientLabRouting routing : routings) {
            attachedRows.add(new Object[] {new ConsultDocs(456, labNo, "L", "999998"), routing});
        }
        when(consultDocsDao.findLabs(456)).thenReturn(attachedRows);
    }

    private void cmlRoutings() {
        // Lab 30's routing row id is 9001; lab 31's routing row id happens to be 30.
        List<Object[]> routings = new ArrayList<>();
        routings.add(new Object[] {cmlInfo(30), routing(9001, 30)});
        routings.add(new Object[] {cmlInfo(31), routing(30, 31)});
        when(labPatientPhysicianInfoDao.findRoutings(123, "CML")).thenReturn(routings);
    }

    @Test
    @DisplayName("should list the CML lab whose lab number is stored, not the one whose routing id equals it")
    void shouldMatchCmlLabByLabNumber_whenListingAttachedLabs() {
        attach(30, typedRouting(9001, 30, "CML", 123));
        cmlRoutings();

        List<LabResultData> attached = new MDSResultsData().populateCMLResultsData(DEMOGRAPHIC_NO, CONSULT_ID, true);
        List<LabResultData> notAttached = new MDSResultsData().populateCMLResultsData(DEMOGRAPHIC_NO, CONSULT_ID, false);

        assertThat(attached).extracting(LabResultData::getSegmentID).containsExactly("30");
        assertThat(notAttached).extracting(LabResultData::getSegmentID).containsExactly("31");
    }

    @Test
    @DisplayName("should list the MDS lab whose lab number is stored, not the one whose routing id equals it")
    void shouldMatchMdsLabByLabNumber_whenListingAttachedLabs() {
        attach(30, typedRouting(9001, 30, "MDS", 123));
        List<Object[]> routings = new ArrayList<>();
        routings.add(mdsRow(9001, 30));
        routings.add(mdsRow(30, 31));
        when(patientLabRoutingDao.findResultsByDemographicAndLabType(123, "MDS")).thenReturn(routings);

        List<LabResultData> attached = new MDSResultsData().populateMDSResultsData(DEMOGRAPHIC_NO, CONSULT_ID, true);

        assertThat(attached).extracting(LabResultData::getSegmentID).containsExactly("30");
    }

    @Test
    @DisplayName("should not report a CML lab as attached when the attached number is the patient's MDS lab")
    void shouldNotMatchAcrossLabTypes_whenAttachedNumberIsAnotherType() {
        attach(30, typedRouting(7001, 30, "MDS", 123));
        cmlRoutings();

        assertThat(new MDSResultsData().populateCMLResultsData(DEMOGRAPHIC_NO, CONSULT_ID, true)).isEmpty();
    }

    @Test
    @DisplayName("should treat a number routed to the patient under two lab types as ambiguous")
    void shouldTreatAttachmentAsAmbiguous_whenNumberHasTwoLabTypesForPatient() {
        attach(30, typedRouting(9001, 30, "CML", 123), typedRouting(7001, 30, "MDS", 123));
        cmlRoutings();

        assertThat(new MDSResultsData().populateCMLResultsData(DEMOGRAPHIC_NO, CONSULT_ID, true)).isEmpty();
    }

    @Test
    @DisplayName("should ignore other patients' and document routings when recovering the lab type")
    void shouldIgnoreForeignAndDocumentRoutings_whenRecoveringLabType() {
        attach(30, typedRouting(9001, 30, "CML", 123), typedRouting(8001, 30, "MDS", 999),
                typedRouting(6001, 30, "DOC", 123));
        cmlRoutings();

        assertThat(new MDSResultsData().populateCMLResultsData(DEMOGRAPHIC_NO, CONSULT_ID, true))
                .extracting(LabResultData::getSegmentID).containsExactly("30");
    }

    private static PatientLabRouting typedRouting(int routingId, int labNo, String labType, int demographicNo) {
        PatientLabRouting routing = new PatientLabRouting(labNo, labType, demographicNo);
        ReflectionTestUtils.setField(routing, "id", routingId);
        return routing;
    }

    private static LabPatientPhysicianInfo cmlInfo(int labNo) {
        LabPatientPhysicianInfo info = new LabPatientPhysicianInfo();
        info.setId(labNo);
        info.setCollectionDate("01-Jan-26");
        return info;
    }

    private static PatientLabRouting routing(int routingId, int labNo) {
        PatientLabRouting routing = new PatientLabRouting(labNo, "CML", 123);
        ReflectionTestUtils.setField(routing, "id", routingId);
        return routing;
    }

    private static Object[] mdsRow(int routingId, int labNo) {
        PatientLabRouting routing = new PatientLabRouting(labNo, "MDS", 123);
        ReflectionTestUtils.setField(routing, "id", routingId);
        MdsMSH msh = new MdsMSH();
        msh.setId(labNo);
        msh.setDateTime(new Date());
        MdsZRG zrg = new MdsZRG();
        zrg.setId(labNo);
        zrg.setReportGroupsDesc("HEMATOLOGY");
        return new Object[] {routing, msh, zrg};
    }
}
