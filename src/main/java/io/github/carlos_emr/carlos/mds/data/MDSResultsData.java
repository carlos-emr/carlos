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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.util.NativeQueryValues;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.LabTestResultsDao;
import io.github.carlos_emr.carlos.commn.dao.MdsMSHDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;
import io.github.carlos_emr.carlos.commn.model.MdsMSH;
import io.github.carlos_emr.carlos.commn.model.MdsZRG;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;


public class MDSResultsData {
    private ConsultDocsDao consultDocsDao = SpringUtils.getBean(ConsultDocsDao.class);
    private ConsultResponseDocDao consultResponseDocDao = SpringUtils.getBean(ConsultResponseDocDao.class);
    private LabPatientPhysicianInfoDao labPPIDao = SpringUtils.getBean(LabPatientPhysicianInfoDao.class);
    private PatientLabRoutingDao PLRDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    private EFormDocsDao eformDocsDao = SpringUtils.getBean(EFormDocsDao.class);

    static Logger logger = MiscUtils.getLogger();

    /**
     * Months a sibling CML report's service date may lie from the selected report's and still be one of
     * its versions. Vendors recycle accession numbers, so a matching accession alone does not make two
     * reports versions of each other.
     */
    private static final int CML_VERSION_WINDOW_MONTHS = 4;

    /**
     * The vendor's {@code YYYYMMDD}. Strict, unlike {@code SimpleDateFormat}: {@code 20260230} is not a
     * date and trailing text is not ignored, so a damaged value is refused rather than silently placed.
     */
    private static final DateTimeFormatter CML_SERVICE_DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    public ArrayList<String> segmentID;
    public ArrayList<String> acknowledgedStatus;
    public ArrayList<String> healthNumber;
    public ArrayList<String> patientName;
    public ArrayList<String> sex;
    public ArrayList<String> resultStatus;
    public ArrayList<String> dateTime;
    public ArrayList<String> priority;
    public ArrayList<String> requestingClient;
    public ArrayList<String> discipline;
    public ArrayList<String> reportStatus;
    public ArrayList<LabResultData> labResults;

    /**
     * Lists CML labs predicated on relationship to patient's consultation
     */
    //Consult Request list labs
    public ArrayList<LabResultData> populateCMLResultsData(String demographicNo, String consultationId, boolean attached) {
        java.util.Set<String> attachedLabs = LabResultData.attachedLabKeys(consultDocsDao.findLabs(ConversionUtils.fromIntString(consultationId)), demographicNo);
        return populateCMLResultsData(demographicNo, attached, attachedLabs);
    }

    public ArrayList<LabResultData> populateCMLResultsDataEForm(String demographicNo, String fdid, boolean attached) {
        java.util.Set<String> attachedLabs = LabResultData.attachedLabKeys(eformDocsDao.findLabs(ConversionUtils.fromIntString(fdid)), demographicNo);
        return populateCMLResultsData(demographicNo, attached, attachedLabs);
    }

    //Consult Response list labs
    public ArrayList<LabResultData> populateCMLResultsDataConsultResponse(String demographicNo, String consultationId, boolean attached) {
        java.util.Set<String> attachedLabs = LabResultData.attachedLabKeys(consultResponseDocDao.findLabs(ConversionUtils.fromIntString(consultationId)), demographicNo);
        return populateCMLResultsData(demographicNo, attached, attachedLabs);
    }


    //Consult List Labs private shared method
    private ArrayList<LabResultData> populateCMLResultsData(String demographicNo, boolean attached, java.util.Set<String> attachedLabs) {
        labResults = new ArrayList<LabResultData>();
        try {
            LabResultData lbData = new LabResultData(LabResultData.CML);
            for (Object[] o : labPPIDao.findRoutings(ConversionUtils.fromIntString(demographicNo), "CML")) {
                LabPatientPhysicianInfo lpp = (LabPatientPhysicianInfo) o[0];
                PatientLabRouting r = (PatientLabRouting) o[1];

                lbData.labType = LabResultData.CML;
                lbData.labPatientId = "" + r.getId();
                lbData.segmentID = "" + lpp.getId();
                lbData.dateTime = lpp.getCollectionDate();
                lbData.setDateObj(UtilDateUtilities.getDateFromString(lbData.dateTime, "dd-MMM-yy"));

                if (attached && attachedLabs.contains(LabResultData.labKey(lbData.labType, lbData.segmentID)))
                    labResults.add(lbData);
                else if (!attached && !attachedLabs.contains(LabResultData.labKey(lbData.labType, lbData.segmentID)))
                    labResults.add(lbData);

                lbData = new LabResultData(LabResultData.CML);
            }
        } catch (Exception e) {
            logger.error("exception in CMLPopulate ({})", e.getClass().getSimpleName());
        }
        return labResults;
    }

    /**
     * End Lists CML labs related to consultation
     */


    public ArrayList<LabResultData> populateEpsilonResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, Integer labNo) {
        return populateLabResultsData("Epsilon", providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, labNo);
    }

    private ArrayList<LabResultData> populateLabResultsData(String labName, String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, Integer labNo) {
        //logger.info("populateCMLResultsData getting called now");
        if (providerNo == null) {
            providerNo = "";
        }
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (patientHealthNumber == null) {
            patientHealthNumber = "";
        }
        if (status == null) {
            status = "";
        }


        labResults = new ArrayList<LabResultData>();
        try {
            List<Object[]> infos = new ArrayList<Object[]>();
            if (labNo != null && labNo.intValue() > 0) {
                LabPatientPhysicianInfo lppi = labPPIDao.find(labNo);
                infos.add(new Object[]{lppi});
            } else {
                if (demographicNo == null) {
                    infos = labPPIDao.findByPatientName(status, labName, providerNo, patientLastName, patientFirstName, patientHealthNumber);
                } else {
                    infos = labPPIDao.findByDemographic(ConversionUtils.fromIntString(demographicNo), labName);
                }
            }

            for (Object[] o : infos) {
                LabPatientPhysicianInfo lpp = (LabPatientPhysicianInfo) o[0];

                LabResultData lbData = new LabResultData(LabResultData.CML);
                lbData.labType = LabResultData.CML;
                lbData.segmentID = "" + lpp.getId();

                if (demographicNo == null && !providerNo.equals("0")) {
                    ProviderLabRoutingModel m = (ProviderLabRoutingModel) o[1];
                    lbData.acknowledgedStatus = m.getStatus();
                } else {
                    lbData.acknowledgedStatus = "U";
                }

                lbData.healthNumber = lpp.getPatientHin();
                lbData.patientName = lpp.getPatientFullName();
                lbData.sex = lpp.getPatientSex();
                lbData.resultStatus = "0"; //TODO
                // solve lbData.resultStatus.add(db.getString(rs,"abnormalFlag"));

                lbData.dateTime = lpp.getCollectionDate();
                lbData.setDateObj(UtilDateUtilities.getDateFromString(lbData.dateTime, "dd-MMM-yy"));

                //priority
                lbData.priority = "----";

                lbData.requestingClient = lpp.getDocName();
                lbData.reportStatus = lpp.getLabStatus();
                lbData.accessionNumber = lpp.getAccessionNum();

                if (lbData.reportStatus != null && lbData.reportStatus.equals("F")) {
                    lbData.finalRes = true;
                } else {
                    lbData.finalRes = false;
                }
                lbData.discipline = "Hem/Chem/Other";
                labResults.add(lbData);
            }
        } catch (Exception e) {
            logger.error("exception in CMLPopulate ({})", e.getClass().getSimpleName());
        }

        return labResults;
    }

    public ArrayList<LabResultData> populateCMLResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, Integer labNo) {
        return populateLabResultsData("CML", providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, labNo);
    }

    public int findCMLAdnormalResults(String labId) {
        LabTestResultsDao dao = SpringUtils.getBean(LabTestResultsDao.class);
        return dao.findByAbnAndPhysicianId("A", ConversionUtils.fromIntString(labId)).size();
    }

    public void populateMDSResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status) {
        if (providerNo == null) {
            providerNo = "";
        }
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (patientHealthNumber == null) {
            patientHealthNumber = "";
        }
        if (status == null) {
            status = "";
        }

        segmentID = new ArrayList<String>();
        acknowledgedStatus = new ArrayList<String>();

        healthNumber = new ArrayList<String>();
        patientName = new ArrayList<String>();
        sex = new ArrayList<String>();
        resultStatus = new ArrayList<String>();
        dateTime = new ArrayList<String>();
        priority = new ArrayList<String>();
        requestingClient = new ArrayList<String>();
        discipline = new ArrayList<String>();
        reportStatus = new ArrayList<String>();

        try {

            ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
            List<Object[]> searchResult;
            if (demographicNo == null) {
                searchResult = dao.findMdsResultResultDataByManyThings(status, providerNo, patientLastName, patientFirstName, patientHealthNumber);
            } else {
                searchResult = dao.findMdsResultResultDataByDemoId(demographicNo);
            }

            for (Object[] o : searchResult) {
                // The patient-only query omits providerLabRouting.status.
                int offset = demographicNo == null ? 1 : 0;
                Integer mshSegmentId = ((Number) o[0]).intValue();
                String providerLabRoutingStatus = offset == 1 ? NativeQueryValues.asString(o[2]) : null;
                String mdsPIDpatientName = NativeQueryValues.asString(o[2 + offset]);
                String mdsPIDhealthNumber = NativeQueryValues.asString(o[3 + offset]);
                String mdsPIDsex = NativeQueryValues.asString(o[4 + offset]);
                String abnormalFlag = NativeQueryValues.asString(o[5 + offset]);
                Date mdsMSHdateTime = NativeQueryValues.asDate(o[6 + offset]);
                String mdsOBRquantityTiming = NativeQueryValues.asString(o[7 + offset]);
                String mdsPV1refDoctor = NativeQueryValues.asString(o[8 + offset]);
                String reportFormStatus = NativeQueryValues.asString(o[9 + offset]);
                String mdsZRGreportGroupDesc = NativeQueryValues.asString(o[10 + offset]);

                segmentID.add(Integer.toString(mshSegmentId));
                if (demographicNo == null && !providerNo.equals("0")) {
                    acknowledgedStatus.add(providerLabRoutingStatus);
                } else {
                    acknowledgedStatus.add("U");
                }

                healthNumber.add(mdsPIDhealthNumber);
                patientName.add(beautifyName(mdsPIDpatientName));
                sex.add(mdsPIDsex);
                resultStatus.add(abnormalFlag);
                dateTime.add(ConversionUtils.toDateString(mdsMSHdateTime));

                switch (mdsOBRquantityTiming.charAt(0)) {
                    case 'C':
                        priority.add("Critical");
                        break;
                    case 'S':
                        priority.add("Stat\\Urgent");
                        break;
                    case 'U':
                        priority.add("Unclaimed");
                        break;
                    case 'A':
                        if (mdsOBRquantityTiming.startsWith("AL")) {
                            priority.add("Alert");
                        } else {
                            priority.add("ASAP");
                        }
                        break;
                    default:
                        priority.add("Routine");
                        break;
                }

                requestingClient.add(ProviderData.beautifyProviderName(mdsPV1refDoctor));
                reportStatus.add(reportFormStatus);

                if (mdsZRGreportGroupDesc.startsWith("MICRO")) {
                    discipline.add("Microbiology");
                } else if (mdsZRGreportGroupDesc.startsWith("DIAGNOSTIC IMAGING")) {
                    discipline.add("Diagnostic Imaging");
                } else {
                    discipline.add("Hem/Chem/Other");
                }
            }
        } catch (Exception e) {
            logger.error("exception in MDSResultsData ({})", e.getClass().getSimpleName());
        }
    }

    /**
     * Lists MDS labs predicated on relationship to patient's consultation
     */
    //Consult Request list labs
    public ArrayList<LabResultData> populateMDSResultsData(String demographicNo, String consultationId, boolean attached) {
        java.util.Set<String> attachedLabs = LabResultData.attachedLabKeys(consultDocsDao.findLabs(ConversionUtils.fromIntString(consultationId)), demographicNo);
        List<Object[]> labsMDS = PLRDao.findResultsByDemographicAndLabType(ConversionUtils.fromIntString(demographicNo), "MDS");
        return populateMDSResultsData(attachedLabs, labsMDS, attached);
    }

    public ArrayList<LabResultData> populateMDSResultsDataEForm(String demographicNo, String fdid, boolean attached) {
        java.util.Set<String> attachedLabs = LabResultData.attachedLabKeys(eformDocsDao.findLabs(ConversionUtils.fromIntString(fdid)), demographicNo);
        List<Object[]> labsMDS = PLRDao.findResultsByDemographicAndLabType(ConversionUtils.fromIntString(demographicNo), "MDS");
        return populateMDSResultsData(attachedLabs, labsMDS, attached);
    }


    //Consult Response list labs
    public ArrayList<LabResultData> populateMDSResultsDataConsultResponse(String demographicNo, String consultationId, boolean attached) {
        java.util.Set<String> attachedLabs = LabResultData.attachedLabKeys(consultResponseDocDao.findLabs(ConversionUtils.fromIntString(consultationId)), demographicNo);
        List<Object[]> labsMDS = PLRDao.findResultsByDemographicAndLabType(ConversionUtils.fromIntString(demographicNo), "MDS");
        return populateMDSResultsData(attachedLabs, labsMDS, attached);
    }

    //Consult list labs private shared method
    private ArrayList<LabResultData> populateMDSResultsData(java.util.Set<String> attachedLabs, List<Object[]> labsMDS, boolean attached) {
        labResults = new ArrayList<LabResultData>();
        try {
            LabResultData lData = new LabResultData(LabResultData.MDS);

            for (Object[] o : labsMDS) {
                PatientLabRouting p = (PatientLabRouting) o[0];
                MdsMSH mdsMSH = (MdsMSH) o[1];
                MdsZRG mdsZRG = (MdsZRG) o[2];

                lData = new LabResultData(LabResultData.MDS);
                lData.segmentID = "" + mdsMSH.getId();
                lData.labPatientId = "" + p.getId();
                lData.dateTime = ConversionUtils.toTimestampString(mdsMSH.getDateTime());
                lData.setDateObj(mdsMSH.getDateTime());

                String reportGroupDesc = mdsZRG.getReportGroupsDesc();
                if (reportGroupDesc != null && reportGroupDesc.startsWith("MICRO")) {
                    lData.discipline = "Microbiology";
                } else if (reportGroupDesc != null && reportGroupDesc.startsWith("DIAGNOSTIC IMAGING")) {
                    lData.discipline = "Diagnostic Imaging";
                } else {
                    lData.discipline = "Hem/Chem/Other";
                }

                if (attached && attachedLabs.contains(LabResultData.labKey(lData.labType, lData.segmentID)))
                    labResults.add(lData);
                else if (!attached && !attachedLabs.contains(LabResultData.labKey(lData.labType, lData.segmentID)))
                    labResults.add(lData);

                lData = new LabResultData(LabResultData.MDS);
            }
        } catch (Exception e) {
            logger.error("exception in MDSResultsData ({})", e.getClass().getSimpleName());
        }
        return labResults;
    }

    /**
     * End Lists CML labs related to consultation
     */


    public ArrayList<LabResultData> populateMDSResultsData2(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, Integer labNo) {

        if (providerNo == null) {
            providerNo = "";
        }
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (patientHealthNumber == null) {
            patientHealthNumber = "";
        }
        if (status == null) {
            status = "";
        }

        labResults = new ArrayList<LabResultData>();
        String seqId = null;  //for debugging purposes

        try {
            ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
            List<Object[]> searchResult = null;

            if (labNo != null && labNo.intValue() > 0 && demographicNo != null) {
                searchResult = dao.findMdsResultResultDataByDemographicNoAndLabNo(Integer.parseInt(demographicNo), labNo);
            } else {
                if (demographicNo == null) {
                    searchResult = dao.findMdsResultResultDataByManyThings(status, providerNo, patientLastName, patientFirstName, patientHealthNumber);
                } else {
                    searchResult = dao.findMdsResultResultDataByDemoId(demographicNo);
                }
            }

            // Both provider searches and selected-patient-lab queries include routing status.
            boolean hasRoutingStatus = demographicNo == null || (labNo != null && labNo > 0);
            int offset = hasRoutingStatus ? 1 : 0;
            for (Object[] o : searchResult) {
                Integer mshSegmentId = ((Number) o[0]).intValue();
                String accessionNum = String.valueOf(o[1]);
                String providerLabRoutingStatus = hasRoutingStatus ? NativeQueryValues.asString(o[2]) : null;
                String mdsPIDpatientName = NativeQueryValues.asString(o[2 + offset]);
                String mdsPIDhealthNumber = NativeQueryValues.asString(o[3 + offset]);
                String mdsPIDsex = NativeQueryValues.asString(o[4 + offset]);
                String abnormalFlag = NativeQueryValues.asString(o[5 + offset]);
                Date mdsMSHdateTime = NativeQueryValues.asDate(o[6 + offset]);
                String mdsOBRquantityTiming = NativeQueryValues.asString(o[7 + offset]);
                String mdsPV1refDoctor = NativeQueryValues.asString(o[8 + offset]);
                String reportFormStatus = NativeQueryValues.asString(o[9 + offset]);
                String mdsZRGreportGroupDesc = NativeQueryValues.asString(o[10 + offset]);

                LabResultData lData = new LabResultData(LabResultData.MDS);
                lData.segmentID = "" + mshSegmentId;
                seqId = lData.segmentID;

                if (demographicNo == null && !providerNo.equals("0")) {
                    lData.acknowledgedStatus = providerLabRoutingStatus;
                } else {
                    lData.acknowledgedStatus = "U";
                }

                lData.healthNumber = mdsPIDhealthNumber;
                lData.patientName = beautifyName(mdsPIDpatientName);
                lData.sex = mdsPIDsex;
                lData.resultStatus = abnormalFlag;
                if (lData.resultStatus == null) {
                    lData.resultStatus = "0";
                }
                lData.dateTime = ConversionUtils.toDateString(mdsMSHdateTime, ConversionUtils.DEFAULT_TS_PATTERN);


                lData.setDateObj(UtilDateUtilities.getDateFromString(lData.dateTime, "yyyy-MM-dd HH:mm:ss"));

                if (lData.getDateObj() == null) {
                    lData.setDateObj(UtilDateUtilities.getDateFromString(lData.dateTime, "yyyy-MM-dd"));
                }

                String quantityTimimg = mdsOBRquantityTiming;
                if (quantityTimimg != null) {
                    switch (quantityTimimg.charAt(0)) {
                        case 'C':
                            lData.priority = "Critical";
                            break;
                        case 'S':
                            lData.priority = "Stat/Urgent";
                            break;
                        case 'U':
                            lData.priority = "Unclaimed";
                            break;
                        case 'A':
                            if (quantityTimimg.startsWith("AL")) {
                                lData.priority = "Alert";
                            } else {
                                lData.priority = "ASAP";
                            }
                            break;
                        default:
                            lData.priority = "Routine";
                            break;
                    }
                } else {
                    lData.priority = "Routine";
                }

                lData.requestingClient = ProviderData.beautifyProviderName(mdsPV1refDoctor);
                lData.reportStatus = reportFormStatus;

                if (lData.reportStatus != null && lData.reportStatus.equals("0")) {
                    lData.finalRes = false;
                } else {
                    lData.finalRes = true;
                }


                if (!lData.resultStatus.equals("0")) {
                    lData.abn = true;
                }
                String reportGroupDesc = mdsZRGreportGroupDesc;

                if (reportGroupDesc != null && reportGroupDesc.startsWith("MICRO")) {
                    lData.discipline = "Microbiology";
                } else if (reportGroupDesc != null && reportGroupDesc.startsWith("DIAGNOSTIC IMAGING")) {
                    lData.discipline = "Diagnostic Imaging";
                } else {
                    lData.discipline = "Hem/Chem/Other";
                }

                lData.accessionNumber = justGetAccessionNumber(accessionNum);

                // must reverse the order of the labs based on the final result count when
                // being sorted so subtract from a large integer
                lData.finalResultsCount = 100 - Integer.parseInt(accessionNum.substring(accessionNum.lastIndexOf("-")));
                //lData.multiLabId = getMatchingLabs(lData.segmentID);
                labResults.add(lData);
            }
        } catch (Exception e) {
            logger.error("Error processing MDS lab, segment # " + seqId);
            logger.error("exception in MDSResultsData ({})", e.getClass().getSimpleName());

        }
        return labResults;
    }
    //////

    public String justGetAccessionNumber(String s) {
        String[] ss = s.split("-");
        if (ss.length > 1)
            return ss[1];
        else
            return ss[0];
    }

    public String findMDSAccessionNumber(String labId) {
        MdsMSHDao dao = SpringUtils.getBean(MdsMSHDao.class);
        MdsMSH m = dao.find(ConversionUtils.fromIntString(labId));
        if (m != null) {
            return justGetAccessionNumber(m.getControlId());
        }
        return "";
    }

    public String findCMLAccessionNumber(String labId) {
        LabPatientPhysicianInfo info = labPPIDao.find(ConversionUtils.fromIntString(labId));
        if (info != null) {
            return info.getAccessionNum();
        }
        return "";
    }

    /**
     * Finds the versions of a CML report.
     *
     * @param labId reviewed CML report identifier
     * @return comma-separated report identifiers in print order, oldest first; labId if the
     *         version lookup fails
     * @see #getMatchingCMLLabs(String, boolean)
     */
    public String getMatchingCMLLabs(String labId) {
        return getMatchingCMLLabs(labId, false);
    }

    /**
     * Resolves CML versions for display or mutation. Mutations require a complete, unambiguous
     * chain: accession and date alone do not establish that reports belong to the same patient.
     * Current chart routing is deliberately not used, so wrong-chart assignments can be corrected.
     */
    public String getMatchingCMLLabs(String labId, boolean failOnLookupError) {
        CmlVersionChain chain = getCmlVersionChain(labId);
        if (failOnLookupError && !chain.reliable()) {
            throw new IllegalStateException("Lab version chain could not be determined");
        }
        return chain.ids();
    }

    /** Display result; an incomplete chain must be accompanied by a visible warning. */
    public record CmlVersionChain(String ids, boolean reliable) { }

    /**
     * Returns only the selected report when source metadata cannot establish a safe chain.
     * Neither logs nor exceptions include patient identifiers or raw vendor data.
     */
    public CmlVersionChain getCmlVersionChain(String labId) {
        try {
            CmlVersionChain chain = resolveCmlChain(ConversionUtils.fromIntString(labId));
            if (!chain.reliable()) {
                logger.warn("CML version chain is ambiguous or incomplete; mutations are unavailable");
            }
            return chain;
        } catch (Exception e) {
            logger.error("exception in getMatchingCMLLabs ({})", e.getClass().getSimpleName());
            return new CmlVersionChain(labId, false);
        }
    }

    private CmlVersionChain resolveCmlChain(Integer selectedId) {
        List<Object[]> rows = labPPIDao.findLabServiceDatesByLabId(selectedId);
        if (rows.isEmpty()) return new CmlVersionChain(selectedId.toString(), true);
        StringJoiner ids = new StringJoiner(",");
        boolean selectedPlaced = false;
        boolean reliable = true;
        boolean selectedHasPrintHeader = false;
        for (Object[] row : rows) {
            if (selectedId.equals(row[0])) selectedHasPrintHeader = hasCmlPrintHeader(row);
        }
        for (Object[] row : rows) {
            Integer id = (Integer) row[0];
            if (selectedId.equals(id)) {
                selectedPlaced = true;
                ids.add(id.toString());
                continue;
            }
            LocalDate siblingDate = parseCmlServiceDate((String) row[1]);
            LocalDate selectedDate = parseCmlServiceDate((String) row[2]);
            if (siblingDate == null || selectedDate == null) {
                reliable = false;
            } else if (withinCmlVersionWindow(siblingDate, selectedDate)) {
                if (!selectedHasPrintHeader || !hasCmlPrintHeader(row)
                        || !sameCmlPatient((LabPatientPhysicianInfo) row[3], (LabPatientPhysicianInfo) row[4])) {
                    reliable = false;
                } else {
                    ids.add(id.toString());
                }
            }
        }
        reliable &= selectedPlaced;
        return new CmlVersionChain(reliable ? ids.toString() : selectedId.toString(), reliable);
    }

    private static boolean hasCmlPrintHeader(Object[] row) {
        // The query orders these vendor strings lexically. Only canonical, real dates/times
        // establish chronological order; accepting malformed or mixed formats could file a newer report.
        if (!(row[5] instanceof String date) || !date.matches("[0-9]{8}") || parseCmlServiceDate(date) == null
                || !(row[6] instanceof String time) || !time.matches("[0-9]{2}:[0-9]{2}")) return false;
        try {
            java.time.LocalTime.parse(time);
            return true;
        } catch (DateTimeParseException _) {
            return false;
        }
    }

    private static boolean sameCmlPatient(LabPatientPhysicianInfo candidate, LabPatientPhysicianInfo selected) {
        String candidateHin = normalizeCmlIdentity(candidate.getPatientHin());
        String selectedHin = normalizeCmlIdentity(selected.getPatientHin());
        LocalDate candidateDob = parseCmlServiceDate(candidate.getPatientDob());
        LocalDate selectedDob = parseCmlServiceDate(selected.getPatientDob());
        boolean sameDob = candidateDob != null && candidateDob.equals(selectedDob);
        boolean sameName = sameNonblankCmlName(candidate.getPatientFirstName(), selected.getPatientFirstName())
                && sameNonblankCmlName(candidate.getPatientLastName(), selected.getPatientLastName());
        // Corroborate even a shared HIN: vendor placeholders must not join different patients.
        // Retaining either DOB or name allows a correction to the other field. Conflicting HINs
        // require review even when names match; dropping a corrected version would also be unsafe.
        if (!candidateHin.isEmpty() && !selectedHin.isEmpty()) {
            return candidateHin.equals(selectedHin) && (sameDob || sameName);
        }
        return sameDob && sameName;
    }

    private static boolean sameNonblankCmlName(String first, String second) {
        String normalized = normalizeCmlIdentity(first);
        return !normalized.isEmpty() && normalized.equals(normalizeCmlIdentity(second));
    }

    private static String normalizeCmlIdentity(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean withinCmlVersionWindow(LocalDate siblingDate, LocalDate selectedDate) {
        LocalDate earlier = siblingDate.isBefore(selectedDate) ? siblingDate : selectedDate;
        LocalDate later = siblingDate.isBefore(selectedDate) ? selectedDate : siblingDate;
        // Preserve the legacy Calendar loop's month-end clamping, without midnight/DST conversion.
        LocalDate cutoff = earlier;
        for (int month = 0; month < CML_VERSION_WINDOW_MONTHS; month++) cutoff = cutoff.plusMonths(1);
        return later.isBefore(cutoff);
    }

    /** Reads the vendor YYYYMMDD or ISO yyyy-MM-dd date without a server-timezone dependency. */
    private static LocalDate parseCmlServiceDate(String serviceDate) {
        if (serviceDate == null) return null;
        String value = serviceDate.trim();
        try {
            return value.indexOf('-') < 0 ? LocalDate.parse(value, CML_SERVICE_DATE) : LocalDate.parse(value);
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    /**
     * Finds MDS versions sharing an accession within the legacy date window.
     * @param labId reviewed MDS lab identifier
     * @return comma-separated matching identifiers; labId if the accession is absent or
     *         version lookup fails, never an unbounded query for a missing accession
     */
    public String getMatchingLabs(String labId) {
        return getMatchingLabs(labId, false);
    }

    /**
     * Resolves a source version chain, optionally propagating lookup failures for atomic mutations.
     * @param labId selected report identifier
     * @param failOnLookupError true when fallback to only the selected version would hide a partial operation
     * @return versions in clinical order
     */
    public String getMatchingLabs(String labId, boolean failOnLookupError) {
        String ret = "";
        int monthsBetween = 0;
        try {
            String accessionNum = findMDSAccessionNumber(labId);
            // A missing accession must not turn into a LIKE '%%' query over unrelated labs.
            if (accessionNum == null || accessionNum.isBlank()) {
                return labId;
            }
            MdsMSHDao dao = SpringUtils.getBean(MdsMSHDao.class);
            for (Object[] o : dao.findLabsByAccessionNumAndId(ConversionUtils.fromIntString(labId), "%" + accessionNum + "%")) {
                MdsMSH a = (MdsMSH) o[0];
                MdsMSH b = (MdsMSH) o[1];

                //MDS labs recycle accessoin numbers every two years, accession
                //numbers for a lab should have lab dates within a year of eachother
                //even this is a large timespan
                Date dateA = a.getDateTime();
                Date dateB = b.getDateTime();
                if (dateA.before(dateB)) {
                    monthsBetween = UtilDateUtilities.getNumMonths(dateA, dateB);
                } else {
                    monthsBetween = UtilDateUtilities.getNumMonths(dateB, dateA);
                }

                if (monthsBetween < 4) {
                    if (ret.equals(""))
                        ret = "" + a.getId();
                    else
                        ret = ret + "," + a.getId();
                }
            }
        } catch (Exception e) {
            if (failOnLookupError) throw new IllegalStateException("Lab version lookup failed", e);
            logger.error("exception in MDSResultsData ({})", e.getClass().getSimpleName());
            return labId;
        }
        return ret;
    }

    private String beautifyName(String name) {
        try {
            return name.substring(0, name.indexOf("^")) + ", "
                    + name.substring(name.indexOf("^") + 1).replace('^', ' ');
        } catch (IndexOutOfBoundsException e) {
            return name.replace('^', ' ');
        }
    }
}
