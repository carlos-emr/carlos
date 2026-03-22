/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;

/**
 * DAO interface for patient operations.
 *
 * @since 2001
 */

public interface PatientLabRoutingDao extends AbstractDao<PatientLabRouting> {

    public static final Integer UNMATCHED = 0;
    public static final String HL7 = "HL7";

    /**
     * Find Demographic By Lab Id.
     *
     * @param labId Integer the labId
     * @return PatientLabRouting
     */
    public PatientLabRouting findDemographicByLabId(Integer labId);

    /**
     * Find Demographics.
     *
     * @param labType String the labType
     * @param labNo Integer the labNo
     * @return PatientLabRouting
     */
    public PatientLabRouting findDemographics(String labType, Integer labNo);

    /**
     * Find Doc By Demographic.
     *
     * @param docNum Integer the docNum
     * @return List<PatientLabRouting>
     */
    public List<PatientLabRouting> findDocByDemographic(Integer docNum);

    /**
     * Find By Lab No.
     *
     * @param labNo int the labNo
     * @return PatientLabRouting
     */
    public PatientLabRouting findByLabNo(int labNo);

    /**
     * Find By Lab No And Lab Type.
     *
     * @param labNo int the labNo
     * @param labType String the labType
     * @return List<PatientLabRouting>
     */
    public List<PatientLabRouting> findByLabNoAndLabType(int labNo, String labType);

    /**
     * Find Unique Test Names.
     *
     * @param demoId Integer the demoId
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findUniqueTestNames(Integer demoId, String labType);

    /**
     * Find Tests.
     *
     * @param demoId Integer the demoId
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findTests(Integer demoId, String labType);

    /**
     * Find Unique Test Names For Patient Excelleris.
     *
     * @param demoNo Integer the demoNo
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findUniqueTestNamesForPatientExcelleris(Integer demoNo, String labType);

    /**
     * Find By Demographic And Lab Type.
     *
     * @param demoNo Integer the demoNo
     * @param labType String the labType
     * @return List<PatientLabRouting>
     */
    public List<PatientLabRouting> findByDemographicAndLabType(Integer demoNo, String labType);

    /**
     * Find Routings And Tests.
     *
     * @param demoNo Integer the demoNo
     * @param labType String the labType
     * @param testName String the testName
     * @return List<Object[]>
     */
    public List<Object[]> findRoutingsAndTests(Integer demoNo, String labType, String testName);

    /**
     * Find Routings And Tests.
     *
     * @param demoNo Integer the demoNo
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findRoutingsAndTests(Integer demoNo, String labType);

    /**
     * Find Mds Routings.
     *
     * @param demoNo Integer the demoNo
     * @param testName String the testName
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findMdsRoutings(Integer demoNo, String testName, String labType);

    /**
     * Find Hl7 Info For Routings And Tests.
     *
     * @param demoNo Integer the demoNo
     * @param labType String the labType
     * @param testName String the testName
     * @return List<Object[]>
     */
    public List<Object[]> findHl7InfoForRoutingsAndTests(Integer demoNo, String labType, String testName);

    /**
     * Find Routings And Consult Docs By Request Id.
     *
     * @param reqId Integer the reqId
     * @param docType String the docType
     * @return List<Object[]>
     */
    public List<Object[]> findRoutingsAndConsultDocsByRequestId(Integer reqId, String docType);

    /**
     * Find Results By Demographic And Lab Type.
     *
     * @param demographicNo Integer the demographicNo
     * @param labType String the labType
     * @return List<Object[]>
     */
    public List<Object[]> findResultsByDemographicAndLabType(Integer demographicNo, String labType);

    /**
     * Find Routing And Physician Info By Type And Demo No.
     *
     * @param labType String the labType
     * @param demographicNo Integer the demographicNo
     * @return List<Object[]>
     */
    public List<Object[]> findRoutingAndPhysicianInfoByTypeAndDemoNo(String labType, Integer demographicNo);

    /**
     * Find Routings And Mds Msh By Demo No.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Object[]>
     */
    public List<Object[]> findRoutingsAndMdsMshByDemoNo(Integer demographicNo);

    /**
     * Find Lab Nos By Demographic.
     *
     * @param demographicNo Integer the demographicNo
     * @param labTypes String[] the labTypes
     * @return List<PatientLabRouting>
     */
    public List<PatientLabRouting> findLabNosByDemographic(Integer demographicNo, String[] labTypes);

    /**
     * Find Demographic Ids Since.
     *
     * @param date Date the date
     * @return List<Integer>
     */
    public List<Integer> findDemographicIdsSince(Date date);

}
