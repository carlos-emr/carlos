//CHECKSTYLE:OFF
/**
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
 */
package ca.openosp.openo.lab.ca.all;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.Hl7TextInfoDao;
import ca.openosp.openo.commn.dao.PatientLabRoutingDao;
import ca.openosp.openo.commn.model.Hl7TextInfo;
import ca.openosp.openo.commn.model.PatientLabRouting;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.util.ConversionUtils;

public class SpireResultsData {

    private static Logger logger = MiscUtils.getLogger();

    public SpireResultsData() {
    }

    public static ArrayList<LabResultData> populateSpireResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status) {

        if (providerNo == null) {
            providerNo = "";
        }
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (status == null) {
            status = "";
        }

        patientHealthNumber = StringUtils.trimToNull(patientHealthNumber);

        ArrayList<LabResultData> labResults = new ArrayList<LabResultData>();
        Hl7TextInfoDao dao = SpringUtils.getBean(Hl7TextInfoDao.class);
        List<Object[]> routings = null;

        if (demographicNo == null) {
            // note to self: lab reports not found in the providerLabRouting table will not show up -
            // need to ensure every lab is entered in providerLabRouting, with '0'
            // for the providers number if unable to find correct providers
            routings = dao.findLabsViaMagic(status, providerNo, patientFirstName, patientLastName, patientHealthNumber);
        } else {
            routings = dao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        }

        try {
            for (Object[] o : routings) {
                Hl7TextInfo hl7 = (Hl7TextInfo) o[0];
                PatientLabRouting p = (PatientLabRouting) o[1];

                LabResultData lbData = new LabResultData(LabResultData.Spire);
                lbData.labType = LabResultData.HL7TEXT;
                lbData.segmentID = "" + p.getLabNo();
                //check if any demographic is linked to this lab
                if (lbData.isMatchedToPatient()) {
                    //get matched demographic no
                    PatientLabRoutingDao pDao = SpringUtils.getBean(PatientLabRoutingDao.class);
                    List<PatientLabRouting> lst = pDao.findByLabNoAndLabType(Integer.parseInt(lbData.segmentID), lbData.labType);

                    if (!lst.isEmpty()) {
                        lbData.setLabPatientId("" + lst.get(0).getDemographicNo());
                    } else {
                        lbData.setLabPatientId("-1");
                    }
                } else {
                    lbData.setLabPatientId("-1");
                }

                if (demographicNo == null && !providerNo.equals("0")) {
                    lbData.acknowledgedStatus = hl7.getResultStatus();
                } else {
                    lbData.acknowledgedStatus = "U";
                }

                lbData.accessionNumber = hl7.getAccessionNumber();
                lbData.healthNumber = hl7.getHealthNumber();
                lbData.patientName = hl7.getLastName() + ", " + hl7.getFirstName();
                lbData.sex = hl7.getSex();
                lbData.label = hl7.getLabel();

                lbData.resultStatus = hl7.getResultStatus();
                if (lbData.resultStatus != null && lbData.resultStatus.equals("A")) {
                    lbData.abn = true;
                }

                lbData.dateTime = hl7.getObrDate();

                //priority
                String priority = hl7.getPriority();

                if (priority != null && !priority.equals("")) {
                    switch (priority.charAt(0)) {
                        case 'C':
                            lbData.priority = "Critical";
                            break;
                        case 'S':
                            lbData.priority = "Stat/Urgent";
                            break;
                        case 'U':
                            lbData.priority = "Unclaimed";
                            break;
                        case 'A':
                            lbData.priority = "ASAP";
                            break;
                        case 'L':
                            lbData.priority = "Alert";
                            break;
                        default:
                            lbData.priority = "Routine";
                            break;
                    }
                } else {
                    lbData.priority = "----";
                }

                lbData.requestingClient = hl7.getRequestingProvider();
                lbData.reportStatus = hl7.getReportStatus();

                // the "C" is for corrected excelleris labs
                if (lbData.reportStatus != null && (lbData.reportStatus.equals("F") || lbData.reportStatus.equals("C"))) {
                    lbData.finalRes = true;
                } else {
                    lbData.finalRes = false;
                }

                lbData.discipline = hl7.getDiscipline();
                lbData.finalResultsCount = hl7.getFinalResultCount();
                labResults.add(lbData);
            }
        } catch (Exception e) {
            logger.error("exception in Hl7Populate:", e);
        }
        return labResults;
    }

}
