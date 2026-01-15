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


/*
 * PATHL7Handler.java
 *
 * Created on June 4, 2007, 1:17 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.ca.all.parsers;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v231.datatype.CX;
import ca.uhn.hl7v2.model.v231.datatype.ST;
import ca.uhn.hl7v2.model.v231.datatype.XCN;
import ca.uhn.hl7v2.model.v231.datatype.XPN;
import ca.uhn.hl7v2.model.v231.group.ORU_R01_PIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI;
import ca.uhn.hl7v2.model.v231.message.ORU_R01;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import ca.openosp.openo.util.UtilDateUtilities;


public class ExcellerisOntarioHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    ORU_R01 msg = null;

    private String hl7Body;

    private static List<String> labDocuments = Arrays.asList("BCCACSP", "BCCASMP", "BLOODBANKT",
            "CELLPATH", "CELLPATHR", "DIAG IMAGE", "MICRO3T",
            "MICROGCMT", "MICROGRT", "MICROBCT", "TRANSCRIP", "NOTIF");

    public static final String VIHARTF = "CELLPATHR";

    public static enum OBX_DATA_TYPES {NM, ST, CE, TX, FT} // Numeric, String, Coded Element, Text, String

    // OBR-25
    /*
     * the value "C" supersedes all others and the mimimum requirement is that the overall report status be displayed as "Corrected."
     * the value "A" or "I" supersedes "F" or Completed and the requirement is that the overall report status be displayed as "Pending" or "Partial."
     */
    public enum OrderStatus {
        CORRECTED("C", "Corrected"),
        PENDING("I", "Pending"),
        PARTIAL_RESULTS("A", "Partial"),
        PRELIMINARY("P", "Preliminary"),
        COMPLETED("F", "Completed"),
        RETRANSMITTED("R", "Retransmitted"),
        DELETED("X", "Cancelled");

        private final String code;
        private final String description;

        OrderStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }
    }

    /** Creates a new instance */
    public ExcellerisOntarioHandler() {
    }

    public void init(String hl7Body) throws HL7Exception {
        this.hl7Body = hl7Body;
        Parser p = new PipeParser();
        p.setValidationContext(new NoValidation());
        msg = (ORU_R01) p.parse(hl7Body.replaceAll("\n", "\r\n").replace("\\.Zt\\", "\t"));
    }

    public String getHl7Body() {
        return hl7Body;
    }

    public void setHl7Body(String hl7Body) {
        this.hl7Body = hl7Body;
    }

    public String getMsgType() {
        return ("ExcellerisON");
    }

    public String getMsgPriority() {
        return ("");
    }


    //MSH-7 (ex 20180213045636-0800)
    public String getMsgDate() {
        return (formatDateTime(getString(msg.getMSH().getDateTimeOfMessage().getTimeOfAnEvent().getValue())));
    }


    public String getAlternativePatientIdentifier() {
        CX[] alternateList = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPid4_AlternatePatientIDPID();
        if (alternateList != null && alternateList.length > 0) {
            CX item = alternateList[0];
            return getString(item.getCx1_ID().getValue());
        }
        return "";
    }

    public String getPatientName() {
        return (getFirstName() + " " + getMiddleName() + " " + getLastName());
    }

    //PID-5-1
    public String getFirstName() {
        XPN[] names = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPatientName();
        if (names.length > 0) {
            return (getString(names[0].getGivenName().getValue()));
        }
        return "";
    }

    //PID-5-3
    public String getMiddleName() {
        XPN[] names = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPatientName();
        if (names.length > 0) {
            return (getString(names[0].getMiddleInitialOrName().getValue()));
        }
        return "";
    }

    //PID-5-0
    public String getLastName() {
        XPN[] names = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPatientName();
        if (names.length > 0) {
            return (getString(names[0].getFamilyLastName().getFamilyName().getValue()));
        }
        return "";
    }

    //PID-7
    public String getDOB(){
        try{
            return(formatDateTime(getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPid7_DateTimeOfBirth().getTimeOfAnEvent().getValue())).substring(0, 10));
        }catch(Exception e){
            return("UNKNOWN");
        }
    }

    public String getAge(){
        String age = "UNKNOWN";
        String dob = getDOB();
        String service = getServiceDate();
        try {
            // Some examples
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            java.util.Date birthDate = formatter.parse(dob);
            java.util.Date serviceDate = formatter.parse(service);
            age = UtilDateUtilities.calcAgeAtDate(birthDate, serviceDate);
        } catch (ParseException e) {
            logger.error("Could not get age", e);

        }
        return age;
    }

    //PID-8
    public String getSex() {
        return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getSex().getValue()));
    }

    //(PID-3-11) 1111111111^^^^JHN^^^^ON&Ontario&HL70347^^AB
    public String getHealthNum() {
        CX[] data = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPid3_PatientIdentifierList();
        if (data.length > 0) {
            CX cx = data[0];
            String hin = cx.getCx1_ID().getValue();
            String type = cx.getCx5_IdentifierTypeCode().getValue();
            String ver = "";
            if (cx.getExtraComponents() != null && cx.getExtraComponents().numComponents() == 5) {
                Varies v = cx.getExtraComponents().getComponent(4);
                ver = v.getData().toString();
            }

            return (getString(hin + ver));
        }
        return "";

    }


    //PID-13, comma separated list
    public String getHomePhone() {
        String phone = "";
        int i = 0;
        try {
            while (!getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue()).equals("")) {
                if (i == 0) {
                    phone = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue());
                } else {
                    phone = phone + ", " + getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue());
                }
                i++;
            }
            return (phone);
        } catch (Exception e) {
            logger.error("Could not return phone number", e);

            return ("");
        }
    }

    //PID-14
    public String getWorkPhone() {
        String phone = "";
        int i = 0;
        try {
            while (!getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue()).equals("")) {
                if (i == 0) {
                    phone = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue());
                } else {
                    phone = phone + ", " + getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getPIDPD1NK1NTEPV1PV2().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue());
                }
                i++;
            }
            return (phone);
        } catch (Exception e) {
            logger.error("Could not return phone number", e);

            return ("");
        }
    }

    //MSH-4 ???
    public String getPatientLocation() {
        return (getString(msg.getMSH().getSendingFacility().getHd2_UniversalID().getValue()));
    }


    //ORC-3
    //Order ID of lab performing tests (accession number-test code-tiebreaker)
    public String getAccessionNum() {
        try {

            String str = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getORC().getFillerOrderNumber().getEntityIdentifier().getValue();

            String accessionNum = getString(str);

            // String[] nums = accessionNum.split("-");
            // if (nums.length == 3){
            //     return nums[0];
            // }else if (nums.length == 5){
            //     return nums[0]+"-"+nums[1]+"-"+nums[2];
            // }else{


            //     if(nums.length>1)
            //         return nums[0]+"-"+nums[1];
            //     else
            //         return "";
            // }

            return accessionNum;
        }catch(Exception e){
            logger.error("Could not return accession number", e);

            return ("");
        }
    }


    public int getOBRCount() {


        return (msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTIReps());
    }

    //OBR-4 TestCode^TestName
    public String getOBRName(int i) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBR().getObr4_UniversalServiceID().getText().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBRIdentifier(int i) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBR().getObr4_UniversalServiceID().getCe1_Identifier().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBR-24
    //Laboratory Section Codes; expanded names available
    public String getObservationHeader(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBR().getObr24_DiagnosticServSectID().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }


    public int getOBRCommentCount(int i) {
        try {
            if (!getOBRComment(i, 0).equals("")) {
                return (1);
            } else {
                return (0);
            }
        } catch (Exception e) {
            return (0);
        }
    }

    //NTE-3 for OBR group
    public String getOBRComment(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getNTE(j).getComment(0).getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBR-7
    public String getServiceDate(){
        int obrCount = getOBRCount();
        String earliestServiceDate = "";
        List<String> serviceDates = new ArrayList<>();
        for (int i = 0; i < obrCount; i++) {
            try {
                String date = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBR().getObservationDateTime().getTimeOfAnEvent().getValue());
                serviceDates.add(date);
            } catch(Exception e){
                serviceDates.add("");
            }
        }

        for (String reportStatusChangeDate : serviceDates) {
            if (earliestServiceDate.isEmpty() || reportStatusChangeDate.compareTo(earliestServiceDate) < 0) { earliestServiceDate = reportStatusChangeDate; }
        }
        return earliestServiceDate.isEmpty() ? earliestServiceDate : formatDateTime(earliestServiceDate);
    }

    //OBR-6
    public String getRequestDate(int i) {
        try {
            return (formatDateTime(getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBR().getRequestedDateTime().getTimeOfAnEvent().getValue())));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBR-22
    public String getReportStatusChangeDate() {
        int obrCount = getOBRCount();
        String latestReportStatusChangeDate = "";
        List<String> reportStatusChangeDates = new ArrayList<>();
        for (int i = 0; i < obrCount; i++) {
            try {
                String date = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBR().getResultsRptStatusChngDateTime().getTimeOfAnEvent().getValue());
                reportStatusChangeDates.add(date);
            } catch(Exception e){
                reportStatusChangeDates.add("");
            }
        }

        for (String reportStatusChangeDate : reportStatusChangeDates) {
            if (latestReportStatusChangeDate.isEmpty() || reportStatusChangeDate.compareTo(latestReportStatusChangeDate) > 0) { latestReportStatusChangeDate = reportStatusChangeDate; }
        }
        return latestReportStatusChangeDate.isEmpty() ? latestReportStatusChangeDate : formatDateTime(latestReportStatusChangeDate);
    }

    //OBR-25
    /*
    * I = pending
    * P = preliminary
    * A = partial results
    * F = complete
    * R = retransmitted
    * C = corrected
    * X = deleted (available on request; not always preceded by non-X OBRs in an earlier transmission)
    *
    * @see ca.openosp.openo.lab.ca.all.parsers.MessageHandler#getOrderStatus()
    */
    public String getOrderStatus(){
    	Set<String> orderStatusSet = new HashSet<>();
        String orderStatus = "";
        try{
        	for(int x=0;x<msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTIReps();x++) {
        		ORU_R01_PIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI items =  msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI(x);
        		for(int y=0;y<items.getORCOBRNTEOBXNTECTIReps();y++) {
        			String status = items.getORCOBRNTEOBXNTECTI(y).getOBR().getResultStatus().getValue();
        			if(status == null) { continue; }
                    orderStatusSet.add(status);
        		}

        	}

            /*
             * the value "C" supersedes all others and the mimimum requirement is that the overall report status be displayed as "Corrected."
             * the value "A" or "I" supersedes "F" or Completed and the requirement is that the overall report status be displayed as "Pending" or "Partial."
             */
            for (OrderStatus status : OrderStatus.values()) {
                if (!orderStatusSet.contains(status.getCode())) { continue; }

                orderStatus = status.getDescription();
                if (status.equals(OrderStatus.CORRECTED)) {
                    orderStatus = checkForDualStatus(orderStatusSet);
                }
                return orderStatus;
            }
        }catch(Exception e){
            return orderStatus;
        }

        return "N/A";
    }

    /**
     * Checks for dual statuses where one of the statuses is 'Corrected'.
     *
     * @param orderStatusSet A set of status codes to check.
     * @return A string combining another status with 'Corrected', or just 'Corrected' if no match is found.
     */
    public String checkForDualStatus(Set<String> orderStatusSet) {
        String correctedStatus = OrderStatus.CORRECTED.getDescription();

        for (OrderStatus status : OrderStatus.values()) {
            if (status.equals(OrderStatus.CORRECTED)) { continue; }
            if (orderStatusSet.contains(status.getCode())) {
                return status.getDescription() + "/" + correctedStatus;
            }
        }

        return correctedStatus;
    }

    /*
     * All Reports/Tests that contain an OBR.25 value of "I", "P", and "C" need to be individually identified on the report display.
     */
    public String getOrderStatus(int y) {
        String statusDescription = "";
        try {
            String status = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(y).getOBR().getResultStatus().getValue());

            for (OrderStatus orderStatus : OrderStatus.values()) {
                if (status.equals(orderStatus.getCode())) {
                    switch (orderStatus) {
                        case PENDING:
                            statusDescription = "Results are pending...";
                            break;
                        case PRELIMINARY:
                        case CORRECTED:
                        case DELETED:
                            statusDescription = orderStatus.getDescription();
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (Exception e) {
            //
        }
        return statusDescription;
    }

    public Optional<OrderStatus> getOrderStatusEnum(int y) {
        try {
            String status = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(y).getOBR().getResultStatus().getValue());
            for (OrderStatus orderStatus : OrderStatus.values()) {
                if (!status.equals(orderStatus.getCode())) { continue; }
                return Optional.of(orderStatus);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    //OBR-16
    public String getClientRef() {
        String docNum = "";
        int i = 0;
        try {
            while (!getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(i).getIDNumber().getValue()).equals("")) {
                if (i == 0) {
                    docNum = getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(i).getIDNumber().getValue());
                } else {
                    docNum = docNum + ", " + getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(i).getIDNumber().getValue());
                }
                i++;
            }
            return (docNum);
        } catch (Exception e) {
            logger.error("Could not return doctor id numbers", e);

            return ("");
        }
    }

    //OBR-16
    public String getDocName() {
        String docName = "";
        int i = 0;
        try {
            while (!getFullDocName(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(i)).equals("")) {
                if (i == 0) {
                    docName = getFullDocName(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(i));
                } else {
                    docName = docName + ", " + getFullDocName(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(i));
                }
                i++;
            }
            return (docName);
        } catch (Exception e) {
            logger.error("Could not return doctor names", e);

            return ("");
        }
    }

    //OBR-28
    public String getCCDocs() {
        String docName = "";
        int i = 0;
        try {
            while (!getFullDocName(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getResultCopiesTo(i)).equals("")) {
                if (i == 0) {
                    docName = getFullDocName(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getResultCopiesTo(i));
                } else {
                    docName = docName + ", " + getFullDocName(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getResultCopiesTo(i));
                }
                i++;
            }
            return (docName);
        } catch (Exception e) {
            logger.error("Could not return cc'ed doctors", e);

            return ("");
        }
    }

    //OBR-16
    public ArrayList<String> getDocNums() {
        ArrayList<String> docNums = new ArrayList<String>();
        String id;
        int i;

        try {
            String providerId = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getOrderingProvider(0).getIDNumber().getValue();
            docNums.add(providerId);

            i = 0;
            while ((id = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(0).getOBR().getResultCopiesTo(i).getIDNumber().getValue()) != null) {
                if (!id.equals(providerId))
                    docNums.add(id);
                i++;
            }
        } catch (Exception e) {
            logger.error("Could not return doctor nums", e);

        }

        return (docNums);
    }


    /*
     *  OBX METHODS
     */
    public int getOBXCount(int i) {
        int count = 0;
        try {
            count = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTEReps();
            // if count is 1 there may only be an nte segment and no obx segments so check
            if (count == 1) {
                String test = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(0).getOBX().getObservationIdentifier().getText().getValue();
                if (test == null)
                    count = 0;
            }
        } catch (Exception e) {
            logger.error("Error retrieving obx count", e);
            count = 0;
        }
        return count;
    }

    //OBX-3
    public String getOBXIdentifier(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getObservationIdentifier().getIdentifier().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBX-2
    public String getOBXValueType(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getValueType().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBX-3
    public String getOBXName(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getObservationIdentifier().getText().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }


    public String getOBXNameLong(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getObservationIdentifier().getText().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBXResult(int i, int j) {
        try {
            return (getString(Terser.get(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX(), 5, 0, 1, 1)));
        } catch (Exception e) {
            return ("");
        }
    }

    /**
     * Get the sub id for this obx line
     */
    //OBX-4
    public String getOBXSubId( int i, int j ) {
        try{
            return(getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getObx4_ObservationSubID().getValue() ) );
        }catch(Exception e){
            return "";
        }
    }

    //OBX-4 + OBX-5
    public String getOBXSubIdWithObservationValue(int i, int j) {
        try{
            String subId = getOBXSubId(i, j);
            String observationResult = getOBXResult(i, j);
            return subId + ") " + observationResult;
        }catch(Exception e){
            return "";
        }
    }

    //OBX-7
    public String getOBXReferenceRange(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getReferencesRange().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBX-6
    public String getOBXUnits(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getUnits().getIdentifier().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //OBX-11
    public String getOBXResultStatus(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getObx11_ObservationResultStatus().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public int getOBXFinalResultCount() {
        int obrCount = getOBRCount();
        int obxCount;
        int count = 0;
        for (int i = 0; i < obrCount; i++) {
            obxCount = getOBXCount(i);
            for (int j = 0; j < obxCount; j++) {
                String status = getOBXResultStatus(i, j);
                if (status.equalsIgnoreCase("F") || status.equalsIgnoreCase("C"))
                    count++;
            }
        }


        String orderStatus = getOrderStatus();
        // add extra so final reports are always the ordered as the latest except
        // if the report has been changed in which case that report should be the latest
        if (orderStatus.equalsIgnoreCase("F"))
            count = count + 100;
        else if (orderStatus.equalsIgnoreCase("C"))
            count = count + 150;

        return count;
    }

    //OBX-14
    public String getTimeStamp(int i, int j) {
        try {
            return (formatDateTime(getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getDateTimeOfTheObservation().getTimeOfAnEvent().getValue())));
        } catch (Exception e) {
            return ("");
        }
    }

    public boolean isOBXAbnormal(int i, int j) {
        try {
            String abnormalFlag = getOBXAbnormalFlag(i, j);
            if (!abnormalFlag.equals("") && !abnormalFlag.equalsIgnoreCase("N")) {
                return (true);
            } else {
                return (false);
            }

        } catch (Exception e) {
            return (false);
        }
    }

    public String getOBXAbnormalFlag(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getAbnormalFlags(0).getValue()));
        } catch (Exception e) {
            logger.error("Error retrieving obx abnormal flag", e);
            return ("");
        }
    }

    public int getOBXCommentCount(int i, int j) {
        try {
            if (!getOBXComment(i, j, 0).equals("")) {
                return (1);
            } else {
                return (0);
            }
        } catch (Exception e) {
            return (0);
        }
    }

    public String getOBXComment(int i, int j, int k) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getNTE(k).getComment(0).getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    //5687^LifeLabs&100 International Blvd.&&Toronto&Ontario&M9W 6J6&Canada&B
    public String getLabLicenseNo(int i, int j) {
        try {
            return (getString(msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getProducerSID().getCe1_Identifier().getValue()));
        } catch (Exception e) {
            logger.error("Error retrieving obx abnormal flag", e);
            return ("");
        }
    }


    public String getLabLicenseName(int i, int j) {
        try {
            String licenseNo = getLabLicenseNo(i, j);

            ST field = msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTI(i).getOBXNTE(j).getOBX().getProducerSID().getCe2_Text();

            StringBuilder s = new StringBuilder();
            s.append(licenseNo);
            s.append(" - ");
            s.append(field.getValue());
            for (int x = 0; x < field.getExtraComponents().numComponents(); x++) {
                if (field.getExtraComponents().getComponent(x).getData() != null && field.getExtraComponents().getComponent(x).getData().toString() != null &&
                        field.getExtraComponents().getComponent(x).getData().toString().length() > 0 && !"null".equals(field.getExtraComponents().getComponent(x).getData().toString())) {
                    s.append(" " + field.getExtraComponents().getComponent(x).getData());
                }
            }
            return s.toString();
        } catch (Exception e) {
            logger.error("Error retrieving obx abnormal flag", e);
            return ("");
        }
    }


    /**
     * Retrieve the possible segment headers from the OBX fields
     */
    public ArrayList<String> getHeaders() {
        int i;
        int arraySize;
        int k = 0;

        ArrayList<String> headers = new ArrayList<String>();
        String currentHeader;

        try {
            for (i = 0; i < msg.getPIDPD1NK1NTEPV1PV2ORCOBRNTEOBXNTECTI().getORCOBRNTEOBXNTECTIReps(); i++) {

                currentHeader = getObservationHeader(i, 0);
                arraySize = headers.size();
                if (arraySize == 0 || !currentHeader.equals(headers.get(arraySize - 1))) {
                    logger.debug("Adding header: '" + currentHeader + "' to list");
                    headers.add(currentHeader);
                }

            }
            return (headers);
        } catch (Exception e) {
            logger.error("Could not create header list", e);

            return (null);
        }

    }

    public String audit() {
        return "";
    }

    /*
     *  END OF PUBLIC METHODS
     */


    private String getFullDocName(XCN docSeg) {
        String docName = "";

        if (docSeg.getPrefixEgDR().getValue() != null)
            docName = docSeg.getPrefixEgDR().getValue();

        if (docSeg.getGivenName().getValue() != null) {
            if (docName.equals("")) {
                docName = docSeg.getGivenName().getValue();
            } else {
                docName = docName + " " + docSeg.getGivenName().getValue();
            }
        }
        if (docSeg.getMiddleInitialOrName().getValue() != null)
            docName = docName + " " + docSeg.getMiddleInitialOrName().getValue();
        if (docSeg.getFamilyLastName().getFamilyName().getValue() != null)
            docName = docName + " " + docSeg.getFamilyLastName().getFamilyName().getValue();
        if (docSeg.getSuffixEgJRorIII().getValue() != null)
            docName = docName + " " + docSeg.getSuffixEgJRorIII().getValue();
        if (docSeg.getDegreeEgMD().getValue() != null)
            docName = docName + " " + docSeg.getDegreeEgMD().getValue();

        return (docName);
    }


    private String formatDateTime(String plain) {
        String stringFormat = "yyyy-MM-dd HH:mm:ss";

        if (plain == null || plain.trim().equals("")) return "";

        if (plain.length() == 19) {
            Date date = UtilDateUtilities.StringToDate(plain, "yyyyMMddHHmmssZ");
            return UtilDateUtilities.DateToString(date, stringFormat);
        }

        String dateFormat = "yyyyMMddHHmmss";
        dateFormat = dateFormat.substring(0, plain.length());
        stringFormat = stringFormat.substring(0, stringFormat.lastIndexOf(dateFormat.charAt(dateFormat.length() - 1)) + 1);

        Date date = UtilDateUtilities.StringToDate(plain, dateFormat);
        return UtilDateUtilities.DateToString(date, stringFormat);
    }

    private String getString(String retrieve) {
        if (retrieve != null) {
            return (retrieve.trim().replaceAll("\\\\\\.br\\\\", "<br />"));
        } else {
            return ("");
        }
    }

    public String getFillerOrderNumber() {
        return "";
    }

    public String getEncounterId() {
        return "";
    }

    public String getRadiologistInfo() {
        return "";
    }

    public String getNteForOBX(int i, int j) {

        return "";
    }

    /*
     * Checks to see if the PATHL7 lab is an unstructured document or a VIHA RTF pathology report
     * labs that fall into any of these categories have certain requirements per Excelleris
     */
    public boolean unstructuredDocCheck(String header) {
        return (labDocuments.contains(header));
    }

    public boolean vihaRtfCheck(String header) {
        return (header.equals(VIHARTF));
    }

    public String getNteForPID() {

        return "";
    }

    /**
     * If the first OBX segment is presenting a textual report and the lab type is
     * not in the unstructured (PATH or ITS) lab types.
     */
    public boolean isReportData() {
        return (OBX_DATA_TYPES.TX.name().equals(getOBXValueType(0, 0))
                || OBX_DATA_TYPES.FT.name().equals(getOBXValueType(0, 0)));
    }

    //for OMD validation
    public boolean isTestResultBlocked(int i, int j) {
        return false;
    }

}
