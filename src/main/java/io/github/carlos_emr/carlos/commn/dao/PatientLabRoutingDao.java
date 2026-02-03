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
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;

public interface PatientLabRoutingDao extends AbstractDao<PatientLabRouting> {

    public static final Integer UNMATCHED = 0;
    public static final String HL7 = "HL7";

    public PatientLabRouting findDemographicByLabId(Integer labId);

    public PatientLabRouting findDemographics(String labType, Integer labNo);

    public List<PatientLabRouting> findDocByDemographic(Integer docNum);

    public PatientLabRouting findByLabNo(int labNo);

    public List<PatientLabRouting> findByLabNoAndLabType(int labNo, String labType);

    public List<Object[]> findUniqueTestNames(Integer demoId, String labType);

    public List<Object[]> findTests(Integer demoId, String labType);

    public List<Object[]> findUniqueTestNamesForPatientExcelleris(Integer demoNo, String labType);

    public List<PatientLabRouting> findByDemographicAndLabType(Integer demoNo, String labType);

    public List<Object[]> findRoutingsAndTests(Integer demoNo, String labType, String testName);

    public List<Object[]> findRoutingsAndTests(Integer demoNo, String labType);

    public List<Object[]> findMdsRoutings(Integer demoNo, String testName, String labType);

    public List<Object[]> findHl7InfoForRoutingsAndTests(Integer demoNo, String labType, String testName);

    public List<Object[]> findRoutingsAndConsultDocsByRequestId(Integer reqId, String docType);

    public List<Object[]> findResultsByDemographicAndLabType(Integer demographicNo, String labType);

    public List<Object[]> findRoutingAndPhysicianInfoByTypeAndDemoNo(String labType, Integer demographicNo);

    public List<Object[]> findRoutingsAndMdsMshByDemoNo(Integer demographicNo);

    public List<PatientLabRouting> findLabNosByDemographic(Integer demographicNo, String[] labTypes);

    public List<Integer> findDemographicIdsSince(Date date);

}
