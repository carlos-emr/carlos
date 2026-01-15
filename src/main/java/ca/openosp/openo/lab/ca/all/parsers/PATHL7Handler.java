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


import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.v23.datatype.ED;
import ca.uhn.hl7v2.model.v23.datatype.HD;
import ca.uhn.hl7v2.model.v23.datatype.XCN;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;
import ca.uhn.hl7v2.parser.DefaultXMLParser;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.parser.XMLParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.util.UtilDateUtilities;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * @author wrighd
 */
public class PATHL7Handler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    ORU_R01 msg = null;

    private static List<String> labDocuments = Arrays.asList("BCCACSP", "BCCASMP", "BLOODBANKT",
            "CELLPATH", "CELLPATHR", "DIAG IMAGE", "MICRO3T",
            "MICROGCMT", "MICROGRT", "MICROBCT", "TRANSCRIP", "NOTIF", "CYTO");

    public static final String VIHARTF = "CELLPATHR";

    public static enum OBX_DATA_TYPES {NM, ST, CE, TX, FT} // Numeric, String, Coded Element, Text, String

    /**
     * Creates a new instance of CMLHandler
     */
    public PATHL7Handler() {
    }

    public void init(String hl7Body) throws HL7Exception {
        Parser p = new PipeParser();
        p.setValidationContext(new NoValidation());
        msg = (ORU_R01) p.parse(hl7Body.replaceAll("\n", "\r\n").replace("\\.Zt\\", "\t"));
    }

    public String getMsgType() {
        return ("PATHL7");
    }

    public String getMsgPriority() {
        return ("");
    }

    /*
     *  MSH METHODS
     */
    public String getMsgDate() {
        return (formatDateTime(getString(msg.getMSH().getDateTimeOfMessage().getTimeOfAnEvent().getValue())));
    }

    /*
     *  PID METHODS
     */
    public String getPatientName() {
        return (getFirstName() + " " + getMiddleName() + " " + getLastName());
    }

    public String getFirstName() {
        return (getString(msg.getRESPONSE().getPATIENT().getPID().getPatientName().getGivenName().getValue()));
    }

    public String getMiddleName() {
        return (getString(msg.getRESPONSE().getPATIENT().getPID().getPatientName().getXpn3_MiddleInitialOrName().getValue()));
    }

    public String getLastName() {
        return (getString(msg.getRESPONSE().getPATIENT().getPID().getPatientName().getFamilyName().getValue()));
    }

    public String getDOB() {
        try {
            return (formatDate(getString(msg.getRESPONSE().getPATIENT().getPID().getDateOfBirth().getTimeOfAnEvent().getValue())));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getAge() {
        String age = "N/A";
        String dob = getDOB();
        String service = getServiceDate();
        try {
            // Some examples
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            java.util.Date birthDate = formatter.parse(dob);
            java.util.Date serviceDate = formatter.parse(service);
            age = UtilDateUtilities.calcAgeAtDate(birthDate, serviceDate);
        } catch (ParseException e) {
            logger.error("Could not get age from DOB: " + dob, e);
        }
        return age;
    }

    public String getSex() {
        return (getString(msg.getRESPONSE().getPATIENT().getPID().getSex().getValue()));
    }

    public String getHealthNum() {
        return (getString(msg.getRESPONSE().getPATIENT().getPID().getPatientIDExternalID().getID().getValue()));
    }

    public String getHomePhone() {
        String phone = "";
        int i = 0;
        try {
            while (!getString(msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue()).equals("")) {
                if (i == 0) {
                    phone = getString(msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue());
                } else {
                    phone = phone + ", " + getString(msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue());
                }
                i++;
            }
            return (phone);
        } catch (Exception e) {
            logger.error("Could not return phone number", e);

            return ("");
        }
    }

    public String getWorkPhone() {
        String phone = "";
        int i = 0;
        try {
            while (!getString(msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue()).equals("")) {
                if (i == 0) {
                    phone = getString(msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue());
                } else {
                    phone = phone + ", " + getString(msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue());
                }
                i++;
            }
            return (phone);
        } catch (Exception e) {
            logger.error("Could not return phone number", e);

            return ("");
        }
    }

    public String getPatientLocation() {
        return (getString(msg.getMSH().getSendingFacility().getNamespaceID().getValue()));
    }

    /*
     *  OBC METHODS
     */
    public String getAccessionNum() {
        try {

            String str = msg.getRESPONSE().getORDER_OBSERVATION(0).getORC().getFillerOrderNumber().getEntityIdentifier().getValue();

            String accessionNum = getString(str);

            String[] nums = accessionNum.split("-");
            if (nums.length == 3) {
                return nums[0];
            } else if (nums.length == 5) {
                return nums[0] + "-" + nums[1] + "-" + nums[2];
            } else {


                if (nums.length > 1)
                    return nums[0] + "-" + nums[1];
                else
                    return "";
            }
        } catch (Exception e) {
            logger.error("Could not return accession number", e);

            return ("");
        }
    }

    /*
     *  OBR METHODS
     */

    public int getOBRCount() {
        return (msg.getRESPONSE().getORDER_OBSERVATIONReps());
    }

    public String getOBRName(int i) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getUniversalServiceIdentifier().getText().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBRIdentifier(int i) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getUniversalServiceIdentifier().getCe1_Identifier().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getUniversalServiceIdentifier(int i) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getUniversalServiceIdentifier().getCe2_Text().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getObservationHeader(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getDiagnosticServiceSectionID().getValue()));
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

    public String getOBRComment(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getNTE(j).getComment(0).getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getServiceDate() {
        try {
            return (formatDateTime(getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getObservationDateTime().getTimeOfAnEvent().getValue())));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getRequestDate(int i) {
        try {
            return (formatDateTime(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getRequestedDateTime().getTimeOfAnEvent().getValue())));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getObservationDate(int i) {
        try {
            return formatDateTime(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getObservationDateTime().getTimeOfAnEvent().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOrderStatus() {
        try {
            String orderStatus = getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultStatus().getValue());
            int obrCount = getOBRCount();
            int obxCount;
            int count = 0;
            for (int i = 0; i < obrCount; i++) {
                obxCount = getOBXCount(i);
                for (int j = 0; j < obxCount; j++) {
                    String obxStatus = getOBXResultStatus(i, j);
                    if (obxStatus.equalsIgnoreCase("C")) {
                        count++;
                    }
                }
            }
            if (count >= 1) {//if any of the OBX's have been corrected, mark the entire report as corrected
                return "corrected";
            }

            if ("P".equals(orderStatus)) {
                return "preliminary";
            }
            if ("I".equals(orderStatus)) {
                return "pending";
            }
            if ("A".equals(orderStatus)) {
                return "partial results";
            }
            if ("F".equals(orderStatus)) {
                return "complete";
            }
            if ("R".equals(orderStatus)) {
                return "retransmitted";
            }
            if ("C".equals(orderStatus)) {
                return "corrected";
            }
            if ("X".equals(orderStatus)) {
                return "deleted";
            }
        } catch (Exception e) {
            return ("");
        }
        return "N/A";
    }

    public String getClientRef() {
        String docNum = "";
        int i = 0;
        try {
            while (!getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i).getIDNumber().getValue()).equals("")) {
                if (i == 0) {
                    docNum = getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i).getIDNumber().getValue());
                } else {
                    docNum = docNum + ", " + getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i).getIDNumber().getValue());
                }
                i++;
            }
            return (docNum);
        } catch (Exception e) {
            logger.error("Could not return doctor id numbers", e);

            return ("");
        }
    }

    public String getDocName() {
        String docName = "";
        int i = 0;
        try {
            while (!getFullDocName(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i)).equals("")) {
                if (i == 0) {
                    docName = getFullDocName(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i));
                } else {
                    docName = docName + ", " + getFullDocName(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i));
                }
                i++;
            }
            return (docName);
        } catch (Exception e) {
            logger.error("Could not return doctor names", e);

            return ("");
        }
    }

    public String getCCDocs() {
        String docName = "";
        int i = 0;
        try {
            while (!getFullDocName(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultCopiesTo(i)).equals("")) {
                if (i == 0) {
                    docName = getFullDocName(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultCopiesTo(i));
                } else {
                    docName = docName + ", " + getFullDocName(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultCopiesTo(i));
                }
                i++;
            }
            return (docName);
        } catch (Exception e) {
            logger.error("Could not return cc'ed doctors", e);

            return ("");
        }
    }

    public ArrayList<String> getDocNums() {
        ArrayList<String> docNums = new ArrayList<String>();
        String id;
        int i;

        try {
            String providerId = msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(0).getIDNumber().getValue();
            docNums.add(providerId);

            i = 0;
            while ((id = msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultCopiesTo(i).getIDNumber().getValue()) != null) {
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
            count = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATIONReps();
            // if count is 1 there may only be an nte segment and no obx segments so check
            if (count == 1) {
                String test = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(0).getOBX().getObservationIdentifier().getIdentifier().getValue();
                // logger.info("name: "+test);
                if (test == null)
                    count = 0;
            }
        } catch (Exception e) {
            logger.error("Error retrieving obx count", e);
            count = 0;
        }
        return count;
    }

    public String getOBXIdentifier(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getIdentifier().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBXValueType(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getValueType().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBXName(int i, int j) {
        try {
            //legacy PDF is "special"
            if ("PDF".equals(getOBXIdentifier(i, j))) {
                return getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getUniversalServiceIdentifier().getText().getValue());
            }
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getText().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    @Override
    public String getOBXNameLong(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getComponent(2).toString()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBXResult(int i, int j) {
        try {
            if ("ED".equals(getOBXValueType(i, j))) {
                ED ed = (ED) msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObx5_ObservationValue()[0].getData();

                if (msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getObr24_DiagnosticServiceSectionID() != null &&
                        "CELLPATHR".equals(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getObr24_DiagnosticServiceSectionID().getValue())) {

                    HD sourceApp = ed.getEd1_SourceApplication();
                    if (!StringUtils.isEmpty(sourceApp.getHd1_NamespaceID().getValue())) {
                        return sourceApp.getHd1_NamespaceID().getValue()/*.replaceAll("\\E\\", "\\")*/;
                    }
                }

                if (ed.getData() != null) {
                    return ed.getData().getValue();

                }
            }
            return (getString(Terser.get(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX(), 5, 0, 1, 1)));
        } catch (Exception e) {
            return ("");
        }
    }

    public boolean isLegacy(int i, int j) {
        if ("PDF".equals(getOBXIdentifier(i, j))) {
            try {

                Varies[] v = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationValue();
                if (v != null && v.length > 0) {
                    Varies va = v[0];
                    Type t = va.getData();
                    if (t instanceof ED) {
                        ED ed = (ED) t;
                        if (ed.getEd2_TypeOfData().getValue() == null && ed.getEd3_DataSubtype().getValue() == null && ed.getEd4_Encoding().getValue() == null && ed.getEd5_Data().getValue() == null) {
                            return true;
                        }
                    }
                }
            } catch (HL7Exception e) {
                logger.error("Error", e);
                return false;
            }
        }
        return false;
    }

    public String getLegacyOBXResult(int i, int j) {
        try {
            if ("ED".equals(getOBXValueType(i, j))) {
                ED ed = (ED) msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObx5_ObservationValue()[0].getData();
                if (ed.getSourceApplication() != null) {
                    return ed.getSourceApplication().getNamespaceID().getValue();
                }
            }
            return (getString(Terser.get(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX(), 5, 0, 1, 1)));
        } catch (Exception e) {
            return ("");
        }
    }

    /**
     * Get the sub id for this obx line
     */
    public String getOBXSubId(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObx4_ObservationSubID().getValue()));
        } catch (Exception e) {
            return (null);
        }
    }

    public String getOBXReferenceRange(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getReferencesRange().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBXUnits(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getUnits().getIdentifier().getValue()));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getOBXResultStatus(int i, int j) {
        try {
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservResultStatus().getValue()));
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
                if (status.equalsIgnoreCase("F") || status.equalsIgnoreCase("C")) {
                    count++;
                }
            }
        }


        String orderStatus = getOrderStatus();
        // add extra so final reports are always the ordered as the latest except
        // if the report has been changed in which case that report should be the latest
        if ("complete".equalsIgnoreCase(orderStatus)) {
            count = count + 100;
        } else if ("corrected".equalsIgnoreCase(orderStatus)) {
            count = count + 150;
        }
        return count;
    }

    public String getTimeStamp(int i, int j) {
        try {
            return (formatDateTime(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getDateTimeOfTheObservation().getTimeOfAnEvent().getValue())));
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
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getAbnormalFlags(0).getValue()));
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
            return (getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getNTE(k).getComment(0).getValue()));
        } catch (Exception e) {
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
            for (i = 0; i < msg.getRESPONSE().getORDER_OBSERVATIONReps(); i++) {

                currentHeader = getObservationHeader(i, 0);
                arraySize = headers.size();
                if (arraySize == 0 || !currentHeader.equals(headers.get(arraySize - 1))) {
                    //logger.info("Adding header: '"+currentHeader+"' to list");
                    headers.add(currentHeader);
                }

            }
            return (headers);
        } catch (Exception e) {
            logger.error("Could not create header list", e);

            return (null);
        }

    }

    /**
     * Get the OBR universal service identifier(s) as the label for this lab.
     */
    public String getLabel() {
        Set<String> labels = new HashSet<>();
        StringBuilder stringBuilder = new StringBuilder(" ");

        for (int i = 0; i < msg.getRESPONSE().getORDER_OBSERVATIONReps(); i++) {
            String usi = getUniversalServiceIdentifier(i);
            labels.add(usi);
        }

        String comma = "";
        for (String label : labels) {
            stringBuilder.append(comma);
            comma = " | ";
            stringBuilder.append(label);
        }

        return stringBuilder.toString().trim();
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
        if (docSeg.getFamilyName().getValue() != null)
            docName = docName + " " + docSeg.getFamilyName().getValue();
        if (docSeg.getSuffixEgJRorIII().getValue() != null)
            docName = docName + " " + docSeg.getSuffixEgJRorIII().getValue();
        if (docSeg.getDegreeEgMD().getValue() != null)
            docName = docName + " " + docSeg.getDegreeEgMD().getValue();

        return (docName);
    }

    /**
     * Format HL7 datetime into ISO standard date.
     *
     * @param plain date string
     * @return ISO standard
     */
    protected static String formatDateTime(String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return "";
        }

        plain = plain.trim();

        try {
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parsedDate = null;

            if (plain.matches("\\d{4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{2}:\\d{2}")) {
                // Format: yyyy-MM-dd HH:mm:ss or yyyy-M-d H:mm:ss
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-M-d H:mm:ss");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{2}")) {
                // Format: yyyy-MM-dd HH:mm or yyyy-M-d H:mm
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-M-d H:mm");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                // Format: yyyy-MM-dd or yyyy-M-d
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-M-d");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{2}:\\d{2}")) {
                // Format: yyyy/MM/dd HH:mm:ss or yyyy/M/d H:mm:ss
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/M/d H:mm:ss");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{2}")) {
                // Format: yyyy/MM/dd HH:mm or yyyy/M/d H:mm
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/M/d H:mm");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:")) {
                // Format: yyyy/MM/dd HH: (incomplete time - missing minutes)
                String fixedPlain = plain + "00:00"; // Add missing minutes and seconds
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/M/d H:mm:ss");
                parsedDate = inputFormat.parse(fixedPlain);

            } else if (plain.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
                // Format: yyyy/MM/dd or yyyy/M/d
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/M/d");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{1,2}/\\d{1,2}/\\d{4} \\d{1,2}:\\d{2}:\\d{2}")) {
                // Format: MM/dd/yyyy HH:mm:ss or M/d/yyyy H:mm:ss
                SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy H:mm:ss");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{1,2}/\\d{1,2}/\\d{4} \\d{1,2}:\\d{2}")) {
                // Format: MM/dd/yyyy HH:mm or M/d/yyyy H:mm
                SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy H:mm");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{1,2}/\\d{1,2}/\\d{4} \\d{1,2}:")) {
                // Format: MM/dd/yyyy HH: (incomplete time - missing minutes)
                String fixedPlain = plain + "00:00"; // Add missing minutes and seconds
                SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy H:mm:ss");
                parsedDate = inputFormat.parse(fixedPlain);

            } else if (plain.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                // Format: MM/dd/yyyy or M/d/yyyy
                SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{1,2}-\\d{1,2}-\\d{4} \\d{1,2}:\\d{2}:\\d{2}")) {
                // Format: dd-MM-yyyy HH:mm:ss or d-M-yyyy H:mm:ss
                SimpleDateFormat inputFormat = new SimpleDateFormat("d-M-yyyy H:mm:ss");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{1,2}-\\d{1,2}-\\d{4} \\d{1,2}:\\d{2}")) {
                // Format: dd-MM-yyyy HH:mm or d-M-yyyy H:mm
                SimpleDateFormat inputFormat = new SimpleDateFormat("d-M-yyyy H:mm");
                parsedDate = inputFormat.parse(plain);

            } else if (plain.matches("\\d{1,2}-\\d{1,2}-\\d{4} \\d{1,2}:")) {
                // Format: dd-MM-yyyy HH: (incomplete time - missing minutes)
                String fixedPlain = plain + "00:00"; // Add missing minutes and seconds
                SimpleDateFormat inputFormat = new SimpleDateFormat("d-M-yyyy H:mm:ss");
                parsedDate = inputFormat.parse(fixedPlain);

            } else if (plain.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
                // Format: dd-MM-yyyy or d-M-yyyy
                SimpleDateFormat inputFormat = new SimpleDateFormat("d-M-yyyy");
                parsedDate = inputFormat.parse(plain);

            } else {
                // Try to handle partial yyyyMMdd formats by padding
                if (plain.matches("\\d+")) {
                    String paddedPlain = plain;

                    // Pad to minimum 8 digits for date
                    while (paddedPlain.length() < 8) {
                        paddedPlain += "0";
                    }

                    // If less than 14 digits, pad to include time
                    if (paddedPlain.length() < 14) {
                        paddedPlain += "000100"; // Add default time 00:01:00
                        paddedPlain = paddedPlain.substring(0, 14); // Ensure exactly 14 digits
                    }

                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                    parsedDate = inputFormat.parse(paddedPlain);
                }
            }

            if (parsedDate != null) {
                return outputFormat.format(parsedDate);
            }

        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error while parsing date time: " + plain, e);

            // Try one more fallback: if it looks like an incomplete time format, try to fix it
            try {
                if (plain.matches(".*\\d{1,2}:$")) {
                    String fixedPlain = plain + "00:00";
                    return formatDateTime(fixedPlain); // Recursive call with fixed string
                }
            } catch (Exception ignored) {
                // If recursive call also fails, continue to return empty string
            }
        }

        // If all parsing attempts fail, return empty string for consistency
        return "";
    }


    protected static String formatDate(String plain) {
        if (plain == null) {
            plain = "";
        }
        SimpleDateFormat stringToDate = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat dateToString = new SimpleDateFormat("yyyy-MM-dd");

        try {
            Date date = stringToDate.parse(plain);
            plain = dateToString.format(date);
        } catch (ParseException e) {
            MiscUtils.getLogger().error("error while parsing date: " + plain, e);
        }
        return plain;
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
        boolean result = true;
        for (int x = 0; x < getOBRCount(); x++) {
            for (int y = 0; y < getOBXCount(x); y++) {
                if (!OBX_DATA_TYPES.TX.name().equals(getOBXValueType(x, y))) {
                    result = false;
                }
            }
        }
        return result;
    }


    //for OMD validation
    public boolean isTestResultBlocked(int i, int j) {
        return false;
    }


    public String getXML() {

        XMLParser xmlParser = new DefaultXMLParser();
        String messageInXML = "";
        try {
            messageInXML = xmlParser.encode(msg);
        } catch (HL7Exception e) {
            messageInXML = "";
        }

        return messageInXML;

    }

}
