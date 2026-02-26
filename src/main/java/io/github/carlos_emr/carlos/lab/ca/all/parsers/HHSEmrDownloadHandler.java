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


package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.integration.hl7.model.PatientId;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

/**
 * @author wrighd
 */
public class HHSEmrDownloadHandler extends DefaultGenericHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    ArrayList<String> headerList = null;

    /**
     * Creates a new instance of CMLHandler
     */
    public HHSEmrDownloadHandler() {
        super();


    }

    @Override
    public String getMsgType() {
        return ("HHSEMR");
    }

    @Override
    public ArrayList<String> getHeaders() {
        headerList = new ArrayList<String>();

        String[] noms = msg.getNames();
        for (String s : noms) {
            logger.debug(s);
        }


        for (int i = 0; i < getOBRCount(); i++) {
            headerList.add(getOBRName(i));
            logger.debug("ADDING to header " + getOBRName(i));
        }
        logger.debug("AFTER");
        try {

            logger.debug("Current Group " + terser.getFinder().getCurrentGroup().getName());

            Structure[] strs = terser.getFinder().getCurrentChildReps(); //((Group) terser.getFinder().getCurrentGroup().get("RESPONSE")).getAll("ORDER_OBSERVATION");
            for (Structure str : strs) {
                logger.debug(str.getClass().getName() + "  " + str.getName());
                Group obrseg = (Group) str;

                Structure[] structChilds = obrseg.getAll("OBX");
                for (Structure ss : structChilds) {
                    logger.debug(ss.getClass().getName() + " " + str.getName());
                }
            }
        } catch (Exception e) {
            logger.debug("debug", e);

        }
        return headerList;
    }

    public String getObservationHeader(int i, int j) {
        return headerList.get(i);
    }

    private void findOBX(Group group, ArrayList<Structure> list) throws Exception {
        String[] noms = group.getNames();
        for (String nom : noms) {
            Structure[] obxS = group.getAll(nom);
            for (Structure ss : obxS) {
                if (ss instanceof Segment) {
                    if ("OBX".equals(ss.getName())) {
                        list.add(ss);
                    }
                } else {
                    findOBX((Group) ss, list);
                }
            }
        }
    }


    public void init(String hl7Body) throws HL7Exception {

        Parser p = new PipeParser();
        p.setValidationContext(new NoValidation());

        // force parsing as a generic message by changing the message structure

        msg = p.parse(hl7Body.replaceAll("\n", "\r\n"));

        terser = new Terser(msg);

        int obrCount = getOBRCount();

        obrGroups = new ArrayList();


        logger.debug("Current Group " + terser.getFinder().getCurrentGroup().getName());


        try {
            Structure[] strs = ((Group) terser.getFinder().getRoot().get("RESPONSE")).getAll("ORDER_OBSERVATION");
            for (Structure str : strs) {
                logger.debug("NEW OBX");
                ArrayList obrGroup = new ArrayList();
                findOBX((Group) str, obrGroup);
                obrGroups.add(obrGroup);
            }
        } catch (Exception e) {
            logger.error("Error in adding OBX elements to obrGroup ", e);
        }


        logger.debug("END GROUP PRINT");


    }

    //
//
//
//        try{
//
//
//
//    /**
//     *  Methods to get information about the Observation Request
//     */
//
//        if (obrGroups != null){
//            try{
//
//
//                //ignore exceptions
//
//
    public String getOBRName(int i) {
        String addToEnd = "";
        try {
            addToEnd = getString(terser.get("/.OBR-19-2"));
        } catch (Exception e) {
            //empty
        }

        return super.getOBRName(i) + addToEnd;
    }

    public String getOBRIdentifier(int i) {
        return super.getOBRIdentifier(i);
    }

    //
//        try{
//            if (i == 1){
//
//                if (obrName.equals(""))
//
//                if (obrName.equals(""))
//
//
//
//
//        try{
//            if (i == 1){
//
//        if (abnormalFlag.equals("") || abnormalFlag.equals("N"))
//        else
//
//
//        //stored in different places for different messages
//
//
//
//
    public String getOBXName(int i, int j) {
        if (getOBXField(i, j, 2, 0, 1).equals("FT")) {
            return "";
        } else
            return (getOBXField(i, j, 3, 0, 2));
    }

    //
//
    public String getOBXReferenceRange(int i, int j) {
        String tmp = getOBXField(i, j, 7, 0, 3);
        if (tmp.equals("")) {
            //try the first component
            tmp = getOBXField(i, j, 7, 0, 1);
        }
        return tmp;
    }

    //
//
//
//        for (int i=0; i < obrCount; i++){
//            for (int j=0; j < obxCount; j++){
//                if (status.startsWith("F") || status.startsWith("f"))
//
//    /**
//     *  Retrieve the possible segment headers from the OBX fields
//     */
//        //  stored in different places for different messages,
//        //  a list must still be returned though
//
//    /**
//     *  Methods to get information from observation notes
//     */
//
//        try{
//
//            // make sure to count all the nte segments in the group
//            if (k < segments.length && segments[k].substring(0, 3).equals("NTE")){
//                for (int l=0; l < nteSegs.length; l++){
//
//
//
//
//
//        try{
//
//
//
//
//    /**
//     *  Methods to get information from observation notes
//     */
//        // jth obx of the ith obr
//
//        try{
//
//
//            if (k < segments.length && segments[k].substring(0, 3).equals("NTE")){
//                for (int l=0; l < nteSegs.length; l++){
//
//
//
//
//
//
//        try{
//
//
//
//
//
//
//
//    /**
//     *  Methods to get information about the patient
//     */
//
//        try {
//
//        try {
//
    public String getDOB() {
        String dob = "", year = "", mon = "", day = "";

        try {
            dob = getString(terser.get("/.PID-7-1"));
            Date date = new Date(dob);
            year = UtilDateUtilities.justYear(date);
            mon = UtilDateUtilities.justMonth(date);
            day = UtilDateUtilities.justDay(date);

            return (formatDateTime(year + mon + day));
        } catch (Exception e) {
            return ("");
        }
    }

    //
//        try {
//            // Some examples
//
//
//        try{
//
    public String getHealthNum() {
        String healthNum;
        try {
            healthNum = getString(terser.get("/.PID-3-1"));
            return (healthNum);
        } catch (Exception e) {
            //ignore exceptions
        }

        return ("");
    }

//        try{
//
//
//        try{
//
    public String getServiceDate() {
        String serdate = "", year = "", mon = "", day = "", time = "";
        //usually a message type specific location
        try {
            serdate = getString(terser.get("/.OBR-7-1"));
            Date date = new Date(serdate);
            year = UtilDateUtilities.justYear(date);
            mon = UtilDateUtilities.justMonth(date);
            day = UtilDateUtilities.justDay(date);
            time = justTime(date);
            serdate = (year + "-" + mon + "-" + day + " " + time);
            logger.info("serdate = " + serdate);
            return serdate.toString();
        } catch (Exception e) {
            return ("");
        }
    }

    //
//        //usually a message type specific location
//
    public String getOrderStatus() {
        String status = "F";

        for (int x = 0; x < this.getOBRCount(); x++) {
            for (int y = 0; y < this.getOBXCount(x); y++) {
                String val = this.getOBXResultStatus(x, y);
                if (!val.equals("F")) {
                    return val;
                }
            }
        }

        return (status);
    }
//
//        try{


    public String getAccessionNum() {
        String useOrderNumber = OscarProperties.getInstance().getProperty("hhs.emr.handler.accession.use_order_number", "false");
        if (useOrderNumber.equals("true")) {
            try {
                String accessionNum = getString(terser.get("/.OBR-3-1"));
                return accessionNum;
            } catch (Exception e) {
                return ("");
            }
        }

        try {
            String accessionNum = getString(terser.get("/.MSH-10-1"));
            return accessionNum;
        } catch (Exception e) {
            return ("");
        }
    }


//        //usually a message type specific location
//
//        try{
//
//
//        try {
//
//
//

    /*
     * Custom segment added by medseek because the HL7 messages did not contain "who" that we can route to the right providers in io.github.carlos_emr.carlos.
     *
     */
    @Override
    public ArrayList<String> getDocNums() {
        ArrayList<String> nums = new ArrayList<String>();
        String docNum;
        try {
            if ((docNum = terser.get("/.Z01-1-1")) != null) {
                MiscUtils.getLogger().debug("Adding doc Num" + Misc.forwardZero(docNum, 6));
                nums.add(Misc.forwardZero(docNum, 6));
            } else {
                return super.getDocNums();
            }

        } catch (Exception e) {
            return super.getDocNums();
        }

        return (nums);
    }
//
//
//
//
//        try{
//
//
//            if (segments[k].substring(0, 3).equals("OBR"))
//
//
//
//            if (segments[k].substring(0, 3).equals(("OBX"))){
//
//
//
//
//        // get name prefix ie/ DR.
//
//        // get the name
//            if (docName.equals("")){
//
//
//
//
//
//
//
//        if (retrieve != null){

    public String getPatientIdByType(String type) throws HL7Exception {
        PatientId id = extractInternalPatientIds().get(type);
        if (id != null) {
            return id.getId();
        }
        return null;
    }

    protected Map<String, PatientId> extractInternalPatientIds() throws HL7Exception {
        Map<String, PatientId> ids = new LinkedHashMap<String, PatientId>();
        int x = 0;
        while (true) {
            String identifier = terser.get("PID-3(" + x + ")-1");
            String authority = terser.get("PID-3(" + x + ")-4");
            String typeId = terser.get("PID-3(" + x + ")-5");

            if (identifier != null) {
                PatientId tmp = new PatientId(identifier, authority, typeId);
                ids.put(typeId, tmp);
            }

            if (identifier == null && terser.get("PID-3(" + (x + 1) + ")-1") == null) {
                break;
            }
            x++;
        }
        return ids;
    }

    private String justTime(Date date) {
        SimpleDateFormat simpledateformat = new SimpleDateFormat("HH:mm");
        return simpledateformat.format(date);
    }
}
