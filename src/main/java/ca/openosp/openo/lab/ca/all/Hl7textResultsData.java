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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.openosp.openo.lab.ca.all.parsers.PATHL7Handler;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.ConsultDocsDao;
import ca.openosp.openo.commn.dao.ConsultResponseDocDao;
import ca.openosp.openo.commn.dao.EFormDocsDao;
import ca.openosp.openo.commn.dao.Hl7TextInfoDao;
import ca.openosp.openo.commn.dao.Hl7TextMessageDao;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.dao.MeasurementMapDao;
import ca.openosp.openo.commn.dao.MeasurementsDeletedDao;
import ca.openosp.openo.commn.dao.MeasurementsExtDao;
import ca.openosp.openo.commn.dao.PatientLabRoutingDao;
import ca.openosp.openo.commn.model.ConsultDocs;
import ca.openosp.openo.commn.model.ConsultResponseDoc;
import ca.openosp.openo.commn.model.EFormDocs;
import ca.openosp.openo.commn.model.Hl7TextInfo;
import ca.openosp.openo.commn.model.Hl7TextMessage;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.commn.model.MeasurementMap;
import ca.openosp.openo.commn.model.MeasurementType;
import ca.openosp.openo.commn.model.MeasurementsDeleted;
import ca.openosp.openo.commn.model.MeasurementsExt;
import ca.openosp.openo.commn.model.PatientLabRouting;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.ca.all.parsers.Factory;
import ca.openosp.openo.lab.ca.all.parsers.MessageHandler;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.UtilDateUtilities;

public class Hl7textResultsData {

    private static Logger logger = MiscUtils.getLogger();
    private static MeasurementsDeletedDao measurementsDeletedDao = (MeasurementsDeletedDao) SpringUtils.getBean(MeasurementsDeletedDao.class);
    private static MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
    private static MeasurementsExtDao measurementsExtDao = SpringUtils.getBean(MeasurementsExtDao.class);
    private static MeasurementMapDao measurementMapDao = SpringUtils.getBean(MeasurementMapDao.class);
    private static ConsultDocsDao consultDocsDao = SpringUtils.getBean(ConsultDocsDao.class);
    private static ConsultResponseDocDao consultResponseDocDao = SpringUtils.getBean(ConsultResponseDocDao.class);
    private static Hl7TextInfoDao hl7TxtInfoDao = SpringUtils.getBean(Hl7TextInfoDao.class);
    private static Hl7TextMessageDao hl7TxtMsgDao = SpringUtils.getBean(Hl7TextMessageDao.class);
    private static PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    private static EFormDocsDao eformDocsDao = SpringUtils.getBean(EFormDocsDao.class);

    private Hl7textResultsData() {
        // no one should instantiate this
    }

    public static void populateMeasurementsTable(String lab_no, String demographic_no) {
        MessageHandler h = Factory.getHandler(lab_no);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        Calendar calender = Calendar.getInstance();
        String day = Integer.toString(calender.get(Calendar.DAY_OF_MONTH));
        String month = Integer.toString(calender.get(Calendar.MONTH) + 1);
        String year = Integer.toString(calender.get(Calendar.YEAR));
        String hour = Integer.toString(calender.get(Calendar.HOUR));
        String min = Integer.toString(calender.get(Calendar.MINUTE));
        String second = Integer.toString(calender.get(Calendar.SECOND));
        String dateEntered = year + "-" + month + "-" + day + " " + hour + ":" + min + ":" + second + ":";

        //Check for other versions of this lab
        String[] matchingLabs = getMatchingLabs(lab_no).split(",");
        //if this lab is the latest version delete the measurements from the previous version and add the new ones

        int k = 0;
        while (k < matchingLabs.length && !matchingLabs[k].equals(lab_no)) {
            k++;
        }

        if (k != 0) {
            List<MeasurementsDeleted> measurementsDeletedList = new ArrayList<>();
            List<Measurement> measurementsToRemove = new ArrayList<>();
            MeasurementsDeleted measurementsDeleted;
            for (Measurement m : measurementDao.findByValue("lab_no", matchingLabs[k - 1])) {
                measurementsDeleted = new MeasurementsDeleted(m);
                measurementsDeletedList.add(measurementsDeleted);
                measurementsToRemove.add(m);
            }
            measurementsDeletedDao.batchPersist(measurementsDeletedList);
            measurementDao.batchRemove(measurementsToRemove);
        }
        // loop through the measurements for the lab and add them

        /*
         * find a regex filter in properties -or- set default to find everything
         * This OSCAR property determines what is allowed to be written to the measurements
         * table.  No more entire PDF documents written into measurements.
         */
        String patternstring = OscarProperties.getInstance().getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*");
        Pattern pattern = Pattern.compile(patternstring);

        /*
         * Setting in OSCAR properties that toggles if a code
         * should be mapped to a measurement by Name AND identifier.
         * Excelleris uses only LOINC identifiers for the most part.
         */
        boolean isSearchName = Boolean.parseBoolean(OscarProperties.getInstance().getProperty("MAP_BY_IDENTIFIER_AND_NAME", "false"));

        List<MeasurementsExt> measurementsExts = new ArrayList<>();
        for (int i = 0; i < h.getOBRCount(); i++) {
            for (int j = 0; j < h.getOBXCount(i); j++) {

                String result = h.getOBXResult(i, j);

                if (result == null) {
                    continue;
                }

                // only add if there is a result and it is supposed to be viewed
                if ("".equals(result) || "DNR".equals(result) || "".equals(h.getOBXName(i, j)) || "DNS".equals(h.getOBXResultStatus(i, j))) {
                    continue;
                }

                logger.debug("obx(" + j + ") should be added");
                String identifier = h.getOBXIdentifier(i, j);
                String name = h.getOBXName(i, j);
                String unit = h.getOBXUnits(i, j);
                String labname = h.getPatientLocation();
                String accession = h.getAccessionNum();
                String req_datetime = h.getRequestDate(i);
                String observationDate = h.getTimeStamp(i, j);

                // temporary for Excelleris labs. This method be added to the MessageHandler interface if
                // it is useful for other HL7 labs
                if (h instanceof PATHL7Handler) {
                    observationDate = ((PATHL7Handler) h).getObservationDate(i);
                }

                String olis_status = h.getOBXResultStatus(i, j);
                String abnormal = h.getOBXAbnormalFlag(i, j);
                if (abnormal != null && (abnormal.equals("A") || abnormal.startsWith("H"))) {
                    abnormal = "A";
                } else if (abnormal != null && abnormal.startsWith("L")) {
                    abnormal = "L";
                } else {
                    abnormal = "N";
                }
                String[] refRange = splitRefRange(h.getOBXReferenceRange(i, j));
                String blocked = h.isTestResultBlocked(i, j) ? "BLOCKED" : null;
                String comments = "";
                for (int l = 0; l < h.getOBXCommentCount(i, j); l++) {
                    comments += comments.length() > 0 ? "\n" + h.getOBXComment(i, j, l) : h.getOBXComment(i, j, l);
                }

                String measType = "";
                String measInst = "";

                identifier = StringUtils.trimToEmpty(identifier);
                name = StringUtils.trimToEmpty(name);

                if (!identifier.isEmpty()) {

                    String searchName = "%";

                    if (isSearchName) {
                        searchName = name;
                    }

                    List<Object[]> measurements = measurementMapDao.findMeasurements("FLOWSHEET", identifier, searchName);

                    if (measurements.isEmpty()) {
                        logger.debug("CODE:" + identifier + " needs to be mapped");
                    } else {
                        for (Object[] o : measurements) {
                            MeasurementMap mm = (MeasurementMap) o[1];
                            MeasurementType type = (MeasurementType) o[2];

                            measType = mm.getIdentCode();
                            measInst = type.getMeasuringInstruction();
                        }
                    }
                }


                /*
                 * Trim and remove HTML
                 */
                String trimmed = StringUtils.trimToEmpty(result);
                trimmed = trimmed.replaceAll("\\<br\\s?/?\\>", "");
                Matcher matcher = pattern.matcher(trimmed.toLowerCase());
                Measurement m = new Measurement();

                if (matcher.find()) {
                    m.setType(measType);
                    m.setDemographicId(Integer.parseInt(demographic_no));
                    m.setProviderNo("0");
                    m.setDataField(matcher.group());
                    m.setMeasuringInstruction(measInst);
                    try {
                        m.setCreateDate(simpleDateFormat.parse(dateEntered));
                    } catch (Exception e) {
                        m.setCreateDate(new Date());
                    }

                    /*
                     * This should be the OBR date - not the date the lab or measurement was received.
                     */
                    try {
                        m.setDateObserved(simpleDateFormat.parse(observationDate));
                    } catch (Exception e) {
                        m.setDateObserved(new Date());
                    }

                    m.setAppointmentNo(0);

                    /*
                     * Remove HTML
                     */
                    comments = comments.replaceAll("\\<br\\s?/?\\>", "");
                    m.setComments(comments);
                    measurementDao.persist(m);

                    int mId = m.getId();

                    MeasurementsExt me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("lab_no");
                    me.setVal(lab_no);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("abnormal");
                    me.setVal(abnormal);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("identifier");
                    me.setVal(identifier);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("name");
                    me.setVal(name);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("labname");
                    me.setVal(labname);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("accession");
                    me.setVal(accession);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("request_datetime");
                    me.setVal(req_datetime);
                    measurementsExts.add(me);

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("datetime");
                    me.setVal(observationDate);
                    measurementsExts.add(me);

                    if (olis_status != null && olis_status.length() > 0) {
                        me = new MeasurementsExt();
                        me.setMeasurementId(mId);
                        me.setKeyVal("olis_status");
                        me.setVal(olis_status);
                        measurementsExts.add(me);
                    }

                    if (unit != null && unit.length() > 0) {
                        me = new MeasurementsExt();
                        me.setMeasurementId(mId);
                        me.setKeyVal("unit");
                        me.setVal(unit);
                        measurementsExts.add(me);
                    }

                    if (refRange[0].length() > 0) {
                        me = new MeasurementsExt();
                        me.setMeasurementId(mId);
                        me.setKeyVal("range");
                        me.setVal(refRange[0]);
                        measurementsExts.add(me);
                    } else {


                        if (refRange[1].length() > 0) {
                            me = new MeasurementsExt();
                            me.setMeasurementId(mId);
                            me.setKeyVal("minimum");
                            me.setVal(refRange[1]);
                            measurementsExts.add(me);
                        }
                        if (refRange[2].length() > 0) {
                            me = new MeasurementsExt();
                            me.setMeasurementId(mId);
                            me.setKeyVal("maximum");
                            me.setVal(refRange[2]);
                            measurementsExts.add(me);
                        }
                    }

                    if (blocked != null) {
                        me = new MeasurementsExt();
                        me.setMeasurementId(mId);
                        me.setKeyVal("blocked");
                        me.setVal(blocked);
                        measurementsExtDao.persist(me);
                    }

                    me = new MeasurementsExt();
                    me.setMeasurementId(mId);
                    me.setKeyVal("other_id");
                    me.setVal(i + "-" + j);
                    measurementsExts.add(me);

                }
            }
        }
        measurementsExtDao.batchPersist(measurementsExts, 50);
    }

    public static String getMatchingLabs_CLS(String lab_no) {
        String ret = "";
        Hl7TextInfo self = null;
        List<Integer> idList = new ArrayList<Integer>();

        for (Object[] o : hl7TxtInfoDao.findByLabIdViaMagic(ConversionUtils.fromIntString(lab_no))) {
            Hl7TextInfo a = (Hl7TextInfo) o[0];
            int labNo = a.getLabNumber();
            if (lab_no.equals(String.valueOf(labNo))) {
                self = a;

            }
            ret = ret + "," + labNo;
            idList.add(labNo);
        }

        //nothing but itself was found, but we have a special case for glucose tolerance tests
        //they come in with different accessions but same filler order no.
        if (self != null && ret.length() > 0 && ret.substring(1).indexOf(",") == -1) {
            ret = "";
            for (Hl7TextInfo info : hl7TxtInfoDao.findByFillerOrderNumber(self.getFillerOrderNum())) {
                ret = ret + "," + info.getLabNumber();
                idList.add(info.getLabNumber());
            }
        }

        if (idList.isEmpty()) {
            idList.add(Integer.parseInt(lab_no));
        }

        Collections.sort(idList);

        StringBuilder sb = new StringBuilder();
        for (Integer id : idList) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(String.valueOf(id));
        }

        return sb.toString();
    }

    public static String getMatchingLabs(String lab_no) {
        String ret = "";
        int monthsBetween = 0;

        Hl7TextMessage hl7Msg = hl7TxtMsgDao.find(Integer.parseInt(lab_no));
        if (hl7Msg != null && "CLS".equals(hl7Msg.getType())) {
            return getMatchingLabs_CLS(lab_no);
        }

        for (Object[] o : hl7TxtInfoDao.findByLabIdViaMagic(ConversionUtils.fromIntString(lab_no))) {
            Hl7TextInfo a = (Hl7TextInfo) o[0];
            Hl7TextInfo b = (Hl7TextInfo) o[1];

            int labNo = a.getLabNumber();


            //Accession numbers may be recycled, accession
            //numbers for a lab should have lab dates within less than 4
            //months of each other even this is a large time span
            Date dateA = ConversionUtils.fromTimestampString(a.getObrDate());
            Date dateB = ConversionUtils.fromTimestampString(b.getObrDate());
            if (dateA == null || dateB == null) continue;

            if (dateA.before(dateB)) {
                monthsBetween = UtilDateUtilities.getNumMonths(dateA, dateB);
            } else {
                monthsBetween = UtilDateUtilities.getNumMonths(dateB, dateA);
            }

            logger.debug("monthsBetween: " + monthsBetween);
            logger.debug("lab_no: " + labNo + " lab: " + lab_no);

            if (monthsBetween < 4) {
                if (ret.equals("")) ret = "" + labNo;
                else ret = ret + "," + labNo;
            }
        }

        if (ret.equals("")) return (lab_no);
        else return (ret);
    }

    /**
     * Populates ArrayList with labs attached to a consultation request
     */
    // Populates labs to consult request
    public static ArrayList<LabResultData> populateHL7ResultsData(String demographicNo, String consultationId, boolean attached) {
        List<LabResultData> attachedLabs = new ArrayList<LabResultData>();
        for (Object[] o : consultDocsDao.findLabs(ConversionUtils.fromIntString(consultationId))) {
            ConsultDocs c = (ConsultDocs) o[0];
            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labPatientId = ConversionUtils.toIntString(c.getDocumentNo());
            attachedLabs.add(lbData);
        }
        List<Object[]> labsHl7 = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        return populateHL7ResultsData(attachedLabs, labsHl7, attached);
    }

    public static ArrayList<LabResultData> populateHL7ResultsDataEForm(String demographicNo, String fdid, boolean attached) {
        List<LabResultData> attachedLabs = new ArrayList<LabResultData>();
        for (Object[] o : eformDocsDao.findLabs(ConversionUtils.fromIntString(fdid))) {
            EFormDocs c = (EFormDocs) o[0];
            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labPatientId = ConversionUtils.toIntString(c.getDocumentNo());
            attachedLabs.add(lbData);
        }
        List<Object[]> labsHl7 = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        return populateHL7ResultsData(attachedLabs, labsHl7, attached);
    }

    // Populates labs to consult response
    public static ArrayList<LabResultData> populateHL7ResultsDataConsultResponse(String demographicNo, String consultationId, boolean attached) {
        List<LabResultData> attachedLabs = new ArrayList<LabResultData>();
        for (Object[] o : consultResponseDocDao.findLabs(ConversionUtils.fromIntString(consultationId))) {
            ConsultResponseDoc c = (ConsultResponseDoc) o[0];
            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labPatientId = ConversionUtils.toIntString(c.getDocumentNo());
            attachedLabs.add(lbData);
        }
        List<Object[]> labsHl7 = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        return populateHL7ResultsData(attachedLabs, labsHl7, attached);
    }

    // Populates labs private shared method
    private static ArrayList<LabResultData> populateHL7ResultsData(List<LabResultData> attachedLabs, List<Object[]> labsHl7, boolean attached) {
        ArrayList<LabResultData> labResults = new ArrayList<LabResultData>();

        LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
        LabResultData.CompareId c = lbData.getComparatorId();
        for (Object[] o : labsHl7) {
            Hl7TextInfo i = (Hl7TextInfo) o[0];
            PatientLabRouting p = (PatientLabRouting) o[1];

            lbData.segmentID = ConversionUtils.toIntString(i.getLabNumber());
            lbData.labPatientId = ConversionUtils.toIntString(p.getLabNo());
            lbData.dateTime = i.getObrDate();
            lbData.discipline = i.getDiscipline();
            lbData.accessionNumber = i.getAccessionNumber();
            lbData.finalResultsCount = i.getFinalResultCount();
            lbData.label = i.getLabel();

            if (attached && Collections.binarySearch(attachedLabs, lbData, c) >= 0) labResults.add(lbData);
            else if (!attached && Collections.binarySearch(attachedLabs, lbData, c) < 0) labResults.add(lbData);

            lbData = new LabResultData(LabResultData.HL7TEXT);
        }

        return labResults;
    }

    /**
     * End Populates labs attached to consultation
     */


    public static ArrayList<LabResultData> getNotAckLabsFromLabNos(List<String> labNos) {
        ArrayList<LabResultData> ret = new ArrayList<LabResultData>();
        LabResultData lrd = new LabResultData();
        for (String labNo : labNos) {
            lrd = getNotAckLabResultDataFromLabNo(labNo);
            ret.add(lrd);
        }
        return ret;
    }

    public static LabResultData getNotAckLabResultDataFromLabNo(String labNo) {
        LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
        // note to self: lab reports not found in the providerLabRouting table will not show up - need to ensure every lab is entered in providerLabRouting, with '0'
        // for the providers number if unable to find correct providers

        List<Hl7TextInfo> infos = hl7TxtInfoDao.findByLabId(ConversionUtils.fromIntString(labNo));
        if (infos.isEmpty()) return lbData;

        Hl7TextInfo info = infos.get(0);

        lbData.labType = LabResultData.HL7TEXT;
        lbData.segmentID = "" + info.getLabNumber();
        //check if any demographic is linked to this lab
        if (lbData.isMatchedToPatient()) {
            //get matched demographic no
            List<PatientLabRouting> rs = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(lbData.segmentID), lbData.labType);
            if (!rs.isEmpty()) {
                lbData.setLabPatientId("" + rs.get(0).getDemographicNo());
            } else {
                lbData.setLabPatientId("-1");
            }
        } else {
            lbData.setLabPatientId("-1");
        }
        lbData.acknowledgedStatus = "U";
        lbData.accessionNumber = info.getAccessionNumber();
        lbData.healthNumber = info.getHealthNumber();
        lbData.patientName = info.getLastName() + ", " + info.getFirstName();
        lbData.sex = info.getSex();

        lbData.resultStatus = info.getResultStatus();
        if (lbData.resultStatus != null && lbData.resultStatus.equals("A")) {
            lbData.abn = true;
        }

        lbData.dateTime = info.getObrDate();

        //priority
        String priority = info.getPriority();

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

        lbData.requestingClient = info.getRequestingProvider();
        lbData.reportStatus = info.getReportStatus();

        // the "C" is for corrected excelleris labs
        if (lbData.reportStatus != null && (lbData.reportStatus.equals("F") || lbData.reportStatus.equals("C"))) {
            lbData.finalRes = true;
        } else {
            lbData.finalRes = false;
        }

        lbData.discipline = info.getDiscipline();
        lbData.finalResultsCount = info.getFinalResultCount();

        return lbData;
    }

    public static ArrayList<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, Integer labNo) {

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

        List<Object[]> routings = null;

        if (labNo != null && labNo.intValue() > 0) {
            routings = new ArrayList<Object[]>();
            for (Hl7TextInfo info : hl7TxtInfoDao.findByLabId(labNo)) {
                routings.add(new Object[]{info});
            }
        } else if (demographicNo == null) {
            // note to self: lab reports not found in the providerLabRouting table will not show up -
            // need to ensure every lab is entered in providerLabRouting, with '0'
            // for the providers number if unable to find correct providers
            routings = hl7TxtInfoDao.findLabsViaMagic(status, providerNo, patientFirstName, patientLastName, patientHealthNumber);
        } else {
            routings = hl7TxtInfoDao.findByDemographicId(ConversionUtils.fromIntString(demographicNo));
        }

        for (Object[] o : routings) {
            Hl7TextInfo hl7 = (Hl7TextInfo) o[0];
            //PatientLabRouting p = (PatientLabRouting) o[1];

            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labType = LabResultData.HL7TEXT;
            lbData.segmentID = "" + hl7.getLabNumber();

            //if the demographic number is null find a matching demo
            if (demographicNo == null) {
                //check if any demographic is linked to this lab
                if (lbData.isMatchedToPatient()) {
                    //get matched demographic no
                    List<PatientLabRouting> lst = patientLabRoutingDao.findByLabNoAndLabType(Integer.parseInt(lbData.segmentID), lbData.labType);

                    if (!lst.isEmpty()) {
                        lbData.setLabPatientId("" + lst.get(0).getDemographicNo());
                    } else {
                        lbData.setLabPatientId("-1");
                    }
                } else {
                    lbData.setLabPatientId("-1");
                }
            } else {
                lbData.setLabPatientId(demographicNo);
            }

            if (o.length == 1) {
                lbData.acknowledgedStatus = "U";
            } else {
                if (demographicNo == null && !providerNo.equals("0")) {
                    lbData.acknowledgedStatus = hl7.getResultStatus();
                } else {
                    lbData.acknowledgedStatus = "U";
                }
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

        return labResults;
    }

    public static ArrayList<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber,
                                                                  String status, boolean isPaged, Integer page, Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal) {
        return populateHl7ResultsData(providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, isPaged, page, pageSize, mixLabsAndDocs, isAbnormal, null, null);
    }

    public static ArrayList<LabResultData> populateHl7ResultsData(String providerNo, String demographicNo, String patientFirstName, String patientLastName, String patientHealthNumber, String status, boolean isPaged, Integer page, Integer pageSize, boolean mixLabsAndDocs, Boolean isAbnormal, Date startDate, Date endDate) {

        if (providerNo == null) {
            providerNo = "";
        }
        boolean searchProvider = !"-1".equals(providerNo) && !"".equals(providerNo);
        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (patientHealthNumber == null) {
            patientHealthNumber = "";
        }
        if (status == null || "U".equals(status)) {
            status = "";
        }

        boolean patientSearch = !"".equals(patientFirstName) || !"".equals(patientLastName) || !"".equals(patientHealthNumber);

        ArrayList<LabResultData> labResults = new ArrayList<LabResultData>();
        // note to self: lab reports not found in the providerLabRouting table will not show up - need to ensure every lab is entered in providerLabRouting, with '0'
        // for the providers number if unable to find correct providers

        for (Object[] i : hl7TxtInfoDao.findLabAndDocsViaMagic(providerNo, demographicNo, patientFirstName, patientLastName, patientHealthNumber, status, isPaged, page, pageSize, mixLabsAndDocs, isAbnormal, searchProvider, patientSearch, startDate, endDate)) {

            String label = String.valueOf(i[0]);
            String lab_no = String.valueOf(i[1]);
            String sex = String.valueOf(i[2]);
            String health_no = String.valueOf(i[3]);
            String result_status = String.valueOf(i[4]);
            String obr_date = String.valueOf(i[5]);
            String priority = String.valueOf(i[6]);
            String requesting_client = String.valueOf(i[7]);
            String discipline = String.valueOf(i[8]);
            String last_name = String.valueOf(i[9]);
            String first_name = String.valueOf(i[10]);
            String report_status = String.valueOf(i[11]);
            String accessionNum = String.valueOf(i[12]);
            String final_result_count = String.valueOf(i[13]);
            String routingStatus = String.valueOf(i[14]);

            LabResultData lbData = new LabResultData(LabResultData.HL7TEXT);
            lbData.labType = LabResultData.HL7TEXT;
            lbData.segmentID = lab_no;

            if (demographicNo == null && !providerNo.equals("0")) {
                lbData.acknowledgedStatus = routingStatus;
            } else {
                lbData.acknowledgedStatus = "U";
            }

            lbData.accessionNumber = accessionNum;
            if (health_no != null && health_no.equals("null")) {
                health_no = "";
            }
            lbData.healthNumber = StringUtils.trimToEmpty(health_no);
            lbData.patientName = last_name + ", " + first_name;
            lbData.sex = sex;
            lbData.label = label;

            lbData.resultStatus = result_status;
            if (lbData.resultStatus != null && lbData.resultStatus.equals("A")) {
                lbData.abn = true;
            }

            lbData.dateTime = obr_date;

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

            lbData.requestingClient = requesting_client;
            lbData.reportStatus = report_status;

            // the "C" is for corrected excelleris labs
            if (lbData.reportStatus != null && (lbData.reportStatus.equals("F") || lbData.reportStatus.equals("C"))) {
                lbData.finalRes = true;
            } else if (lbData.reportStatus != null && lbData.reportStatus.equals("X")) {
                lbData.cancelledReport = true;
            } else {
                lbData.finalRes = false;
            }

            lbData.discipline = discipline;
            lbData.finalResultsCount = ConversionUtils.fromIntString(final_result_count);
            labResults.add(lbData);
        }

        return labResults;
    }

    private static String[] splitRefRange(String refRangeTxt) {
        refRangeTxt = refRangeTxt.trim();
        String[] refRange = {"", "", ""};
        String numeric = "-. 0123456789";
        boolean textual = false;
        if (refRangeTxt == null || refRangeTxt.length() == 0) return refRange;

        for (int i = 0; i < refRangeTxt.length(); i++) {
            if (!numeric.contains(refRangeTxt.subSequence(i, i + 1))) {
                if (i > 0 || (refRangeTxt.charAt(i) != '>' && refRangeTxt.charAt(i) != '<')) {
                    textual = true;
                    break;
                }
            }
        }
        if (textual) {
            refRange[0] = refRangeTxt;
        } else {
            if (refRangeTxt.charAt(0) == '>') {
                refRange[1] = refRangeTxt.substring(1).trim();
            } else if (refRangeTxt.charAt(0) == '<') {
                refRange[2] = refRangeTxt.substring(1).trim();
            } else {
                String[] tmp = refRangeTxt.split("-");
                if (tmp.length == 2) {
                    refRange[1] = tmp[0].trim();
                    refRange[2] = tmp[1].trim();
                } else {
                    refRange[0] = refRangeTxt;
                }
            }
        }
        return refRange;
    }
}
