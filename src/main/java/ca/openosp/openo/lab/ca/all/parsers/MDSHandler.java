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
 * MDSHandler.java
 *
 * Created on June 5, 2007, 9:25 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.ca.all.parsers;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.lab.ca.all.util.HL7VersionFixer;
import ca.openosp.openo.util.UtilDateUtilities;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

/**
 * @author wrighd
 */
public class MDSHandler implements MessageHandler {

    Message msg = null;
    Terser terser = null;
    ArrayList obrGroups = null;
    HashMap<String, String> headerMaps = new HashMap<String, String>();
    Logger logger = MiscUtils.getLogger();
    String rawHL7Body = null; // Store raw HL7 for Z-segment access

    /**
     * Creates a new instance of CMLHandler
     */
    public MDSHandler() {
    }

    public void init(String hl7Body) throws HL7Exception {
        hl7Body = StringUtils.trimToNull(hl7Body);
        if (hl7Body == null) {
            logger.error("Some one called MDSHandler.init with null data");
            return;
        }

        try {
            // Fix for MDS lab messages using version 2.3.0 which HAPI doesn't recognize
            // HAPI only supports 2.3 and 2.3.1, not 2.3.0
            hl7Body = HL7VersionFixer.fixHL7Version(hl7Body);

            // Store raw HL7 for direct Z-segment parsing (official HAPI can't access Z-segments reliably)
            this.rawHL7Body = hl7Body;

            PipeParser parser = new PipeParser();
            parser.setValidationContext(new NoValidation());
            msg = parser.parse(hl7Body.replace("\n", "\r\n"));
            terser = new Terser(msg);

            int obrCount = getOBRCount();
            String[] segments = terser.getFinder().getCurrentGroup().getNames();
            obrGroups = new ArrayList();

            /*
             *  Fill the OBX array list for use by future methods
             *  Build groups by iterating through all OBX segments and associating them with their parent OBR
             */

            // Initialize empty lists for each OBR
            for (int i = 0; i < obrCount; i++) {
                obrGroups.add(new ArrayList<String>());
            }

            // Iterate through all OBX segments using HAPI's RESPONSE group structure
            // For ORU_R01 v2.3 messages, the structure is: RESPONSE -> ORDER_OBSERVATION -> OBSERVATION
            try {
                // Access the RESPONSE group which contains all ORDER_OBSERVATION groups
                String responseGroupPath = "/RESPONSE";

                // For each OBR, find its associated OBX segments
                for (int obrIdx = 0; obrIdx < obrCount; obrIdx++) {
                    ArrayList<String> obxList = (ArrayList<String>) obrGroups.get(obrIdx);

                    // Build paths to access OBX segments within the ORDER_OBSERVATION group
                    String orderObsPath = responseGroupPath + "/ORDER_OBSERVATION(" + obrIdx + ")";

                    // Try to find OBX segments in the OBSERVATION group
                    int obxCount = 0;
                    final int MAX_OBX_PER_OBR = 1000; // Safety limit
                    while (obxCount < MAX_OBX_PER_OBR) {
                        try {
                            String obxPath = orderObsPath + "/OBSERVATION(" + obxCount + ")/OBX";
                            String testField = terser.get(obxPath + "-1");

                            if (testField != null) {
                                obxList.add(obxPath);
                                obxCount++;
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            // No more OBX segments for this OBR
                            break;
                        }
                    }
                    if (obxCount >= MAX_OBX_PER_OBR) {
                        logger.warn("Reached maximum OBX count per OBR limit: " + MAX_OBX_PER_OBR);
                    }

                    // If no OBX found using the group structure, try alternative paths
                    if (obxList.isEmpty()) {
                        // Try direct OBX access (for messages that might not follow strict structure)
                        try {
                            String obxPath = orderObsPath + "/OBX";
                            String testField = terser.get(obxPath + "-1");
                            if (testField != null) {
                                obxList.add(obxPath);
                            }
                        } catch (Exception e) {
                            logger.debug("Could not find OBX for OBR index " + obrIdx + " using direct path", e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error grouping OBX segments with OBR segments", e);

                // Fallback: try the old sequential approach if group structure fails
                try {
                    int globalObxIndex = 0;
                    for (int obrIdx = 0; obrIdx < obrCount; obrIdx++) {
                        ArrayList<String> obxList = (ArrayList<String>) obrGroups.get(obrIdx);

                        // Try /.OBX path (flat structure)
                        try {
                            String obxPath = "/.OBX";
                            if (globalObxIndex > 0) {
                                obxPath = "/.OBX" + (globalObxIndex + 1);
                            }
                            String testField = terser.get(obxPath + "-1");
                            if (testField != null) {
                                obxList.add(obxPath);
                                globalObxIndex++;
                            }
                        } catch (Exception ex) {
                            logger.debug("Fallback OBX access failed for OBR " + obrIdx, ex);
                        }
                    }
                } catch (Exception ex2) {
                    logger.error("Fallback OBX grouping also failed", ex2);
                }
            }
        } catch (Exception e) {
            logger.error("Could not parse MDS message", e);
        }
    }

    public String getMsgType() {
        return ("MDS");
    }

    /**
     * Helper method to get OBR field value using correct path syntax.
     * Supports both group structure (official HAPI) and flat structure (legacy).
     *
     * @param obrIndex 0-based OBR index
     * @param field OBR field number (e.g., "7-1" for observation date/time)
     * @return field value or empty string if not found
     */
    private String getOBRField(int obrIndex, String field) {
        try {
            // Try group structure first (official HAPI for ORU_R01 v2.3)
            String groupPath = "/RESPONSE/ORDER_OBSERVATION(" + obrIndex + ")/OBR-" + field;
            String value = terser.get(groupPath);
            if (value != null) {
                return value;
            }

            // Fallback to flat structure (old HAPI)
            String flatPath = (obrIndex == 0) ? "/.OBR-" + field : "/.OBR" + (obrIndex + 1) + "-" + field;
            value = terser.get(flatPath);
            return (value != null) ? value : "";
        } catch (Exception e) {
            logger.debug("Error getting OBR field " + field + " for index " + obrIndex, e);
            return "";
        }
    }

    public String getMsgPriority() {

        int obrCount = getOBRCount();
        String priority = "R";
        try {
            for (int i = 0; i < obrCount; i++) {
                priority = getOBRField(i, "27-1");
                if (priority != null && !priority.equalsIgnoreCase("R")) {
                    break;
                }
            }
        } catch (Exception e) {
            // ignore exceptions
        }

        if (priority != null && priority.startsWith("AL"))
            priority = "L";

        return priority;
    }

    /**
     * Methods to get information about the Observation Request
     */
    public int getOBRCount() {
        if (obrGroups != null) {
            return (obrGroups.size());
        } else {
            int i = 0;
            String test;
            try {
                // For ORU_R01 v2.3 messages, OBR segments are in RESPONSE/ORDER_OBSERVATION groups
                // Try group structure first (official HAPI)
                test = terser.get("/RESPONSE/ORDER_OBSERVATION(0)/OBR-2-1");
                if (test != null) {
                    // Successfully found OBR using group structure, count all ORDER_OBSERVATION groups
                    // Safety limit to prevent infinite loops
                    final int MAX_OBR_COUNT = 1000;
                    while (test != null && i < MAX_OBR_COUNT) {
                        i++;
                        test = terser.get("/RESPONSE/ORDER_OBSERVATION(" + i + ")/OBR-2-1");
                    }
                    if (i >= MAX_OBR_COUNT) {
                        logger.warn("Reached maximum OBR count limit: " + MAX_OBR_COUNT);
                    }
                } else {
                    // Fallback to flat structure (old HAPI compatibility)
                    test = terser.get("/.OBR-2-1");
                    i = 1;
                    final int MAX_OBR_COUNT = 1000;
                    while (test != null && i < MAX_OBR_COUNT) {
                        i++;
                        test = terser.get("/.OBR" + i + "-2-1");
                    }
                    if (i >= MAX_OBR_COUNT) {
                        logger.warn("Reached maximum OBR count limit: " + MAX_OBR_COUNT);
                    }
                    i = i - 1;
                }
            } catch (Exception e) {
                logger.debug("Error counting OBR segments", e);
            }
            return i;
        }
    }

    public String getOBRName(int i) {
        /*String obrCode;
        i++;
        try{
            if (i == 1){
                obrCode = getString(terser.get("/.OBR-4-1"));
            }else{
                obrCode = getString(terser.get("/.OBR"+i+"-4-1"));
            }
            return("TEST "+i+": "+obrCode);
        }catch(Exception e){
            return("TEST "+i+":");
        }*/

        // the MDS local table is not available so the corresponding lab names are unknown
        // NO NAMES FOR MDS OBR SEGMENTS
        return ("");
    }

    @Override
    public String getOBRIdentifier(int i) {
        return null;
    }

    public String getTimeStamp(int i, int j) {
        try {
            String timeStamp = formatDateTime(getString(getOBRField(i, "7-1")));
            return (timeStamp != null && !timeStamp.isEmpty()) ? timeStamp : getMsgDate();
        } catch (Exception e) {
            return getMsgDate();
        }
    }

    public boolean isOBXAbnormal(int i, int j) {
        if (getOBXAbnormalFlag(i, j).equals(""))
            return (false);
        else
            return (true);
    }

    public String getOBXAbnormalFlag(int i, int j) {
        return (getOBXField("8-1", i, j));
    }

    public String getObservationHeader(int i, int j) {

        ArrayList obxSegs = (ArrayList) obrGroups.get(i);
        //logger.info("OBRGROUP("+i+") OBXSEG("+j+"): '"+((String) obxSegs.get(j)));
        return (matchOBXToHeader((String) obxSegs.get(j)));

    }

    public int getOBXCount(int i) {
        ArrayList obxSegs = (ArrayList) obrGroups.get(i);
        return (obxSegs.size());
    }

    public String getOBXIdentifier(int i, int j) {
        return (getOBXField("3-1", i, j));
    }


    public String getOBXValueType(int i, int j) {
        return (getOBXField("2-1", i, j));
    }

    public String getOBXName(int i, int j) {
        return (getOBXField("3-2", i, j));
    }

    @Override
    public String getOBXNameLong(int i, int j) {
        return (getOBXField("3-3", i, j));
    }

    public String getOBXResult(int i, int j) {
        return (getOBXField("5-1", i, j));
    }

    public String getOBXReferenceRange(int i, int j) {
        return (getOBXField("7-1", i, j));
    }

    public String getOBXUnits(int i, int j) {
        return (getOBXField("6-1", i, j));
    }

    public String getOBXResultStatus(int i, int j) {
        String resultStatus = getOBXField("11-1", i, j);

        switch (resultStatus.charAt(0)) {
            case 'C':
                resultStatus = "Edited";
                break;
            case 'D':
                resultStatus = "DNS";
                break;
            case 'F':
                resultStatus = "Final";
                break;
            case 'f':
                resultStatus = "Final";
                break;
            case 'I':
                resultStatus = "Pending";
                break;
            case 'P':
                resultStatus = "Preliminary";
                break;
            case 'p':
                resultStatus = "Preliminary";
                break;
            case 'R':
                resultStatus = "Entered - Not Verified";
                break;
            case 'r':
                resultStatus = "Entered - Not Verified";
                break;
            case 'S':
                resultStatus = "Partial";
                break;
            case 's':
                resultStatus = "Partial";
                break;
            case 'X':
                resultStatus = "DNS";
                break; // do not show
            case 'U':
                resultStatus = "Changed to Final";
                break;
            case 'u':
                resultStatus = "Changed to Final";
                break;
            case 'W':
                resultStatus = "DNS";
                break;
        }

        return (resultStatus);
    }

    public int getOBXFinalResultCount() {
        int obrCount = getOBRCount();
        int obxCount;
        int count = 0;
        for (int i = 0; i < obrCount; i++) {
            obxCount = getOBXCount(i);
            for (int j = 0; j < obxCount; j++) {
                String status = getOBXResultStatus(i, j);
                if (status.equalsIgnoreCase("Final") || status.equalsIgnoreCase("Edited"))
                    count++;
            }
        }
        String orderStatus = getOrderStatus();
        // add extra so final reports are always the ordered as the latest
        if (orderStatus.equalsIgnoreCase("F"))
            count = count + 100;

        return count;
    }


    /**
     * Retrieve the possible segment headers from the OBX fields.
     * Parse ZRG segments directly from raw HL7 since official HAPI can't access Z-segments reliably.
     */
    public ArrayList<String> getHeaders() {
        ArrayList<String> headers = new ArrayList<String>();
        String currentHeader = "";
        String headerNum = null;

        if (rawHL7Body == null) {
            logger.warn("rawHL7Body is null in getHeaders()");
            return headers;
        }

        try {
            // Parse ZRG segments directly from raw HL7 message
            String[] lines = rawHL7Body.split("\r?\n");
            ArrayList<String[]> zrgSegments = new ArrayList<String[]>();

            // Collect all ZRG segments
            for (String line : lines) {
                if (line.startsWith("ZRG|")) {
                    String[] fields = line.split("\\|", -1);
                    zrgSegments.add(fields);
                }
            }

            // Process ZRG segments to build headers
            // ZRG format: ZRG|1.1|4000|||ROUTINE CHEMISTRY I|1|
            // Field 0: ZRG
            // Field 1: sequence (e.g., "1.1")
            // Field 2: group number (e.g., "4000")
            // Field 5: header name (e.g., "ROUTINE CHEMISTRY I")
            // Field 7: display name (if present)

            String prevGroupNum = null;
            for (String[] fields : zrgSegments) {
                if (fields.length > 2) {
                    String groupNum = (fields.length > 2) ? fields[2] : "";
                    String headerName = (fields.length > 5) ? fields[5] : "";
                    String displayName = (fields.length > 7) ? fields[7] : "";

                    // Use display name if available, otherwise use header name
                    String headerText = !displayName.isEmpty() ? displayName : headerName;

                    if (prevGroupNum != null && !groupNum.equals(prevGroupNum)) {
                        // New group - save current header and start new one
                        if (!currentHeader.isEmpty()) {
                            headerMaps.put(prevGroupNum, currentHeader);
                            headers.add(currentHeader);
                        }
                        currentHeader = headerText;
                    } else {
                        // Same group - append to current header
                        if (currentHeader.isEmpty()) {
                            currentHeader = headerText;
                        } else {
                            currentHeader = currentHeader + "<br />" + headerText;
                        }
                    }

                    prevGroupNum = groupNum;
                    headerNum = groupNum;
                }
            }

            // Add final header
            if (!currentHeader.isEmpty() && headerNum != null) {
                headerMaps.put(headerNum, currentHeader);
                headers.add(currentHeader);
            }

        } catch (Exception e) {
            logger.error("Error parsing headers from ZRG segments", e);
        }

        return headers;
    }

    /**
     * Methods to get information from observation notes
     */
    public int getOBRCommentCount(int i) {
        // not needed comments will only follow OBX segments
        return (0);
    }

    public String getOBRComment(int i, int j) {
        // not needed comments will only follow OBX segments
        return ("");
    }

    /**
     * Methods to get information from observation notes
     * Returns 1 if there are any NTE segments (representing one concatenated comment block)
     * Returns 0 if there are no NTE segments
     * This matches the old behavior where all NTE segments are shown in a single table row
     */
    public int getOBXCommentCount(int i, int j) {
        try {
            // jth obx of the ith obr

            ArrayList obxSegs = (ArrayList) obrGroups.get(i);
            if (obxSegs == null || j >= obxSegs.size()) {
                return 0;
            }

            String obxPath = (String) obxSegs.get(j);

            // Try to find NTE segments following this OBX in the OBSERVATION group
            // Path format: /RESPONSE/ORDER_OBSERVATION(i)/OBSERVATION(j)/OBX
            try {
                // Build the path to potential NTE segments
                // They would be at /RESPONSE/ORDER_OBSERVATION(i)/OBSERVATION(j)/NTE(k)
                String basePath = obxPath.substring(0, obxPath.lastIndexOf("/"));

                // Check if there are any NTE segments
                String ntePath = basePath + "/NTE(0)";
                try {
                    String commentType = terser.get(ntePath + "-2-1");
                    if (commentType != null) {
                        // There is at least one NTE segment, return 1
                        return 1;
                    }
                } catch (HL7Exception e) {
                    // No NTE segments
                    return 0;
                }

                return 0;
            } catch (Exception e) {
                // If we can't parse the structure, return 0
                logger.debug("Error counting comments", e);
                return 0;
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getOBXCommentCount", e);
            return 0;
        }
    }

    public String getOBXComment(int i, int j, int k) {
        try {
            // For MDS messages with RESPONSE group structure
            // Since getOBXCommentCount() returns 1, k should always be 0
            // Returns ALL NTE segments concatenated with <br />
            // For MC type: returns all matching ZMC segments
            // For other types: returns all NTE-3 component 2 values

            ArrayList obxSegs = (ArrayList) obrGroups.get(i);
            if (obxSegs == null || j >= obxSegs.size()) {
                return "";
            }

            String obxPath = (String) obxSegs.get(j);

            // Build the path to NTE segments
            // Path format: /RESPONSE/ORDER_OBSERVATION(i)/OBSERVATION(j)/NTE(k)
            String basePath = obxPath.substring(0, obxPath.lastIndexOf("/"));

            // Collect all NTE segments using pattern from hacked_patched_hapi-0.5.1.jar
            // Pattern: comment = (comment==null) ? commentCode : comment+"<br/>"+commentCode
            // This tracks "first segment" explicitly to handle empty first segments correctly
            StringBuilder allComments = new StringBuilder();
            boolean isFirstSegment = true;
            int nteIndex = 0;
            final int MAX_NTE_COUNT = 500; // Safety limit

            while (nteIndex < MAX_NTE_COUNT) {
                String ntePath = basePath + "/NTE(" + nteIndex + ")";

                try {
                    String commentType = terser.get(ntePath + "-2-1");
                    if (commentType == null) {
                        // No more NTE segments
                        break;
                    }

                    String currentComment = "";

                    if ("MC".equals(commentType)) {
                        // This is a matched code - get all ZMC segments for this code
                        // NTE-3 field format: ^CODE means component 1 is empty, component 2 is CODE
                        String commentCode = terser.get(ntePath + "-3-2");

                        if (commentCode != null && rawHL7Body != null) {
                            // Parse all ZMC segments from raw HL7 for this code
                            // Pattern: First ZMC appends directly (even if empty), subsequent ZMCs prepend <br />
                            // This ensures empty ZMC segments produce visible line breaks
                            StringBuilder zmcComments = new StringBuilder();
                            boolean isFirstZmc = true;
                            String[] lines = rawHL7Body.split("\r?\n");
                            for (String line : lines) {
                                if (line.startsWith("ZMC|")) {
                                    String[] fields = line.split("\\|", -1);
                                    if (fields.length > 6) {
                                        // ZMC field 2 may have components (CODE^EXTRA), extract component 1 only
                                        String zmcCode = fields[2];
                                        if (zmcCode.contains("^")) {
                                            zmcCode = zmcCode.split("\\^")[0];
                                        }
                                        if (zmcCode.equals(commentCode)) {
                                            // Add this ZMC comment line from field 6
                                            String commentText = getString(fields[6]);

                                            // Pattern from hacked_patched_hapi-0.5.1.jar for ZMC:
                                            // First ZMC: append commentText directly (even if empty)
                                            // Subsequent ZMCs: prepend "<br />" then append commentText
                                            // This makes empty ZMC segments (e.g., ZMC|1.1|CODE||5|Y|)
                                            // produce a leading <br />, creating a visible blank line
                                            if (isFirstZmc) {
                                                zmcComments.append(commentText);
                                                isFirstZmc = false;
                                            } else {
                                                zmcComments.append("<br />").append(commentText);
                                            }
                                        }
                                    }
                                }
                            }
                            currentComment = zmcComments.toString();
                        }
                    } else {
                        // Direct text comment from NTE
                        // NTE-3 format: CODE^DESCRIPTION, we want component 2 (description)
                        String nteText = terser.get(ntePath + "-3-2");
                        if (nteText != null) {
                            currentComment = getString(nteText);
                        }
                    }

                    // Pattern from hacked_patched_hapi-0.5.1.jar implementation:
                    // First NTE: append currentComment directly (even if empty)
                    // Subsequent NTEs: append "<br />" + currentComment
                    // This ensures leading <br /> when first segment is empty
                    if (isFirstSegment) {
                        allComments.append(currentComment);
                        isFirstSegment = false;
                    } else {
                        allComments.append("<br />").append(currentComment);
                    }

                    nteIndex++;
                } catch (HL7Exception e) {
                    // No more NTE segments
                    break;
                }
            }

            if (nteIndex >= MAX_NTE_COUNT) {
                logger.warn("Reached maximum NTE count limit: " + MAX_NTE_COUNT + ". Some NTE segments may have been omitted due to truncation.");
            }

            return allComments.toString();
        } catch (Exception e) {
            logger.error("Unexpected error in getOBXComment", e);
            return "";
        }
    }


    /**
     * Methods to get information about the patient
     */
    public String getPatientName() {
        return (getFirstName() + " " + getLastName());
    }

    public String getFirstName() {
        try {
            return (getString(terser.get("/.PID-5-2")));
        } catch (Exception ex) {
            return ("");
        }
    }

    public String getLastName() {
        try {
            return (getString(terser.get("/.PID-5-1")));
        } catch (Exception ex) {
            return ("");
        }
    }

    public String getMiddleName() {
        try {
            return (getString(terser.get("/.PID-5-3")));
        } catch (Exception ex) {
            return ("");
        }
    }

    public String getUnescapedName() {
        return getLastName() + "^" + getFirstName() + "^" + getMiddleName();
    }

    public String getDOB() {
        try {
            return (formatDateTime(getString(terser.get("/.PID-7-1"))).substring(0, 10));
        } catch (Exception e) {
            logger.error("Error retrieving date of birth", e);
            return ("");
        }
    }

    public String getAge() {
        String age = "N/A";
        String dob = getDOB();
        try {
            // Some examples
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            java.util.Date date = formatter.parse(dob);
            age = UtilDateUtilities.calcAge(date);
        } catch (ParseException e) {
            logger.error("Could not get age", e);

        }
        return age;
    }

    public String getSex() {
        try {
            return (getString(terser.get("/.PID-8-1")));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getHealthNum() {
        try {
            String healthNum = getString(terser.get("/.PID-19-1"));
            int end = healthNum.indexOf(" ");
            if (end > 0)
                return (healthNum.substring(1, end));
            else
                return (healthNum.substring(1));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getHealthNumVersion() {
        try {
            String healthNum = getString(terser.get("/.PID-19-1"));
            return (healthNum.substring(healthNum.indexOf(" ") + 1));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getHomePhone() {
        try {
            return (getString(terser.get("/.PID-13-1")));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getWorkPhone() {
        return ("N/A");
    }

    public String getPatientLocation() {
        try {
            return (getString(terser.get("/.MSH-3-1")));
            //return(getString(terser.get("/.PV1-3-1-1")));
        } catch (Exception e) {
            return ("");
        }
    }

    //should be from the 1st OBR always.
    public String getServiceDate() {
        try {
            return getRequestDate(0);
        } catch (Exception e) {
            return ("");
        }
    }

    public String getRequestDate(int i) {
        try {
            String requestDate = formatDateTime(getString(getOBRField(i, "14-1")));
            return (requestDate != null && !requestDate.isEmpty()) ? requestDate : getMsgDate();
        } catch (Exception e) {
            return getMsgDate();
        }
    }

    public String getOrderStatus() {

        String ret = "F";
        try {
            String firstZfrStatus = getString(terser.get("/.ZFR-3-1"));
            if (firstZfrStatus != null && firstZfrStatus.equals("0"))
                return ("P");

            String status = "";
            int i = 0;
            final int MAX_ZFR_COUNT = 100; // Safety limit

            // If one of the zfr segments says partial, the lab should be marked
            // as a partial lab
            while (i < MAX_ZFR_COUNT && (status = terser.get("/.ZFR(" + i + ")-3-1")) != null) {
                if (status.equals("0")) {
                    ret = "P";
                    break;
                }
                i++;
            }
            if (i >= MAX_ZFR_COUNT) {
                logger.warn("Reached maximum ZFR count limit: " + MAX_ZFR_COUNT);
            }
        } catch (HL7Exception e) {
            // ZFR segments may not be found if they were moved to end of message
            // This is expected with official HAPI - default to Final status
            logger.debug("Could not retrieve ZFR segments for order status - defaulting to Final", e);
        } catch (Exception e) {
            logger.warn("Unexpected exception retrieving order status - defaulting to Final", e);
        }
        return ret;
    }

    public String getClientRef() {
        try {
            String clientNum = getString(terser.get("/.MSH-10-1"));
            int firstDash = clientNum.indexOf("-");
            return (clientNum.substring(0, firstDash));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getDocNum() {
        try {
            return (terser.get("/.PV1-8-1").replace("-", ""));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getAccessionNum() {
        try {
            String accessionNum = getString(terser.get("/.MSH-10-1"));
            int firstDash = accessionNum.indexOf("-");
            int secondDash = accessionNum.indexOf("-", firstDash + 1);
            return (accessionNum.substring(firstDash + 1, secondDash));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getDocName() {
        try {
            return (getFullDocName("/.PV1-8-"));
        } catch (Exception e) {
            return ("");
        }
    }

    public String getCCDocs() {
        String docs = "";
        try {
            String tmp = "";
            int x = 0;
            final int MAX_CC_DOCS = 100; // Safety limit to prevent infinite loops
            do {
                tmp = getFullDocName("/.PV1-9(" + x + ")-");
                if (tmp.length() > 0) {
                    if (docs.length() > 0) {
                        docs = docs + ", " + tmp;
                    } else {
                        docs = tmp;
                    }
                }
                x++;
            } while (!tmp.equals("") && x < MAX_CC_DOCS);

            if (x >= MAX_CC_DOCS) {
                logger.warn("Reached maximum CC docs limit: " + MAX_CC_DOCS);
            }

            if (docs.equals(""))
                docs = getFullDocName("/.PV1-17-");
            else
                docs = docs + ", " + getFullDocName("/.PV1-17-");
            return (docs);
        } catch (Exception e) {
            logger.debug("Error retrieving CC docs", e);
            return ("");
        }
    }

    public ArrayList<String> getDocNums() {
        ArrayList<String> nums = new ArrayList<String>();
        String docNum;
        try {
            if ((docNum = terser.get("/.PV1-8-1")) != null) {
                nums.add(docNum.replace("-", ""));
            }
            if ((docNum = terser.get("/.PV1-9-1")) != null) {
                nums.add(docNum.replace("-", ""));
            }
            if ((docNum = terser.get("/.PV1-17-1")) != null) {
                nums.add(docNum.replace("-", ""));
            }
        } catch (Exception e) {
            logger.error("Could not retrieve doctor numbers", e);
        }
        return (nums);
    }

    /**
     * Methods specific to the MDSHandler
     */
    public String getFormType() {
        String retVal = "";

        try {
            String typeField = getString(terser.get("/.MSH-10-1"));
            char typeNum = typeField.charAt(typeField.indexOf('-', typeField.indexOf('-') + 1) + 1);

            switch (typeNum) {
                case '1':
                    retVal = "S";
                    break;
                case '4':
                    retVal = "X";
                    break;
                case '5':
                    retVal = "C";
                    break;
                case '6':
                    retVal = "H";
                    break;
                case '7':
                    retVal = "A";
                    break;
                case '9':
                    retVal = "M";
                    break;
            }

        } catch (Exception e) {
            logger.error("Could not retrieve form type", e);

        }

        return (retVal);
    }

    public String getMsgDate() {

        try {
            String dateString = formatDateTime(getString(terser.get("/.MSH-7-1")));
            return (dateString);
        } catch (Exception e) {
            return ("");
        }

    }

    public Date getMsgDateAsDate() {
        Date date = null;
        try {
            date = getDateTime(getString(terser.get("/.MSH-7-1")));
        } catch (Exception e) {
            //Not sure what to do here
            MiscUtils.getLogger().error("Error", e);
        }
        return date;
    }

    private String getOBXField(String field, int i, int j) {
        ArrayList obxSegs = (ArrayList) obrGroups.get(i);
        String obxSeg = (String) obxSegs.get(j);
        String fullPath = obxSeg + "-" + field;

        try {
            String value = getString(terser.get(fullPath));
            return value;
        } catch (Exception e) {
            logger.debug("Error retrieving OBX field " + field + " for OBR=" + i + ", OBX=" + j, e);
            return ("");
        }
    }

    private String matchOBXToHeader(String obxSeg) {
        String headerNum = "null";

        try {
            // Get the OBX observation identifier (field 4)
            String obxNum = terser.get(obxSeg + "-4-1");

            // Extract LOINC code - it's the last section after the last hyphen
            // Example: "43856-1-43856" -> "43856"
            if (obxNum != null && obxNum.contains("-")) {
                obxNum = obxNum.substring(obxNum.lastIndexOf("-") + 1);
            }

            if (rawHL7Body == null) {
                logger.warn("rawHL7Body is null in matchOBXToHeader()");
                return null;
            }

            // Parse ZMN segments directly from raw HL7 message
            // ZMN format: ZMN||NAME||DISPLAY|UNITS|VAL|RANGE|LOINC|FLAG|GROUPNUM
            // Field 8 = LOINC code, Field 10 = group number
            String[] lines = rawHL7Body.split("\r?\n");
            for (String line : lines) {
                if (line.startsWith("ZMN|")) {
                    String[] fields = line.split("\\|", -1);
                    if (fields.length > 10) {
                        String zmnLoinc = fields[8];  // LOINC code
                        String groupNum = fields[10]; // Group number

                        if (zmnLoinc.equals(obxNum)) {
                            headerNum = groupNum;
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error retrieving header", e);
        }

        return headerMaps.get(headerNum);
    }

    private String getFullDocName(String docSeg) {
        //docSeg = "/.PV1-8-"
        String docName = "";
        String temp;

        try {
            // get name prefix ie/ DR.
            temp = terser.get(docSeg + "6");
            if (temp != null)
                docName = temp;

            // get the name
            temp = terser.get(docSeg + "2");
            if (temp != null) {
                if (docName.equals("")) {
                    docName = temp;
                } else {
                    docName = docName + " " + temp;
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected Error.", e);
        }

        return (docName);
    }

    private Date getDateTime(String plain) {
        String dateFormat = "yyyyMMddHHmmss";
        dateFormat = dateFormat.substring(0, plain.length());
        Date date = UtilDateUtilities.StringToDate(plain, dateFormat);
        return date;
    }

    private String formatDateTime(String plain) {
        if (plain == null || plain.trim().equals("")) return "";

        String dateFormat = "yyyyMMddHHmmss";
        dateFormat = dateFormat.substring(0, plain.length());
        String stringFormat = "yyyy-MM-dd HH:mm:ss";
        stringFormat = stringFormat.substring(0, stringFormat.lastIndexOf(dateFormat.charAt(dateFormat.length() - 1)) + 1);

        Date date = UtilDateUtilities.StringToDate(plain, dateFormat);
        return UtilDateUtilities.DateToString(date, stringFormat);
    }

    private String getString(String retrieve) {
        if (retrieve != null) {
            retrieve.replaceAll("^", " ");
            return (retrieve.trim());
        } else {
            return ("");
        }
    }

    String delimiter = "  ";
    char bl = ' ';


    public String getAuditLine(String procDate, String procTime, String logId, String formStatus, String formType, String accession, String hcNum, String hcVerCode, String patientName, String orderingClient, String messageDate, String messageTime) {
        logger.info("Getting Audit Line");


        return getPaddedString(procDate, 11, bl) + delimiter +
                getPaddedString(procTime, 8, bl) + delimiter +
                getPaddedString(logId, 7, bl) + delimiter +
                getPaddedString(formStatus, 1, bl) + delimiter +
                getPaddedString(formType, 1, bl) + delimiter +
                getPaddedString(accession, 9, bl) + delimiter +
                getPaddedString(hcNum, 10, bl) + delimiter +
                getPaddedString(hcVerCode, 2, bl) + delimiter +
                getPaddedString(patientName, 61, bl) + delimiter +
                getPaddedString(orderingClient, 8, bl) + delimiter +
                getPaddedString(messageDate, 11, bl) + delimiter +
                getPaddedString(messageTime, 8, bl) + "\n\r";

    }

    String getPaddedString(String originalString, int length, char paddingChar) {
        StringBuilder str = new StringBuilder(length);
        str.append(originalString);

        for (int i = str.length(); i < length; i++) {
            str.append(paddingChar);
        }

        return str.substring(0, length);
    }

    public String audit() {
        String retVal = "";

        java.util.Date date = new java.util.Date();
        SimpleDateFormat dayFormatter = new SimpleDateFormat("dd-MMM-yyyy");
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");

        String procDate = dayFormatter.format(date);
        String procTime = timeFormatter.format(date);

        String messageDate = "";
        String messageTime = "";
        try {
            messageDate = dayFormatter.format(getMsgDateAsDate());
            messageTime = timeFormatter.format(getMsgDateAsDate());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        retVal = getAuditLine(procDate, procTime, "REC", getOrderStatus(), getFormType(), getAccessionNum(), getHealthNum(), getHealthNumVersion(), getUnescapedName(), getClientRef(), messageDate, messageTime);

        return retVal;
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

    public String getNteForPID() {
        return "";
    }

    /**
     * Checks if the test result is blocked based on the ZPD segment in the HL7 message.
     * The ZPD segment is generated during import when BlockedTestResult is "Y" in the XML.
     * Format: ZPD|||Y| where field 3 contains "Y" if blocked.
     * Uses HAPI segment access pattern consistent with OLISHL7Handler.isOBRBlocked().
     *
     * @param i OBR index (unused, blocked status applies to entire message)
     * @param j OBX index (unused, blocked status applies to entire message)
     * @return true if the test result is blocked, false otherwise
     */
    public boolean isTestResultBlocked(int i, int j) {
        Segment zpd;
        try {
            zpd = terser.getSegment("/.ZPD");
        } catch (HL7Exception e) {
            // "End of message reached" means ZPD segment not present - normal for non-blocked labs
            if (e.getMessage() != null && e.getMessage().contains("End of message")) {
                logger.debug("No ZPD segment found - lab result is not blocked");
            } else {
                // Other HL7 exceptions are unexpected and should be logged
                logger.warn("Unexpected error retrieving ZPD segment", e);
            }
            return false;
        }

        try {
            if (zpd != null) {
                String indicator = Terser.get(zpd, 3, 0, 1, 1);
                if ("Y".equalsIgnoreCase(indicator)) {
                    return true;
                }
            }
        } catch (HL7Exception e) {
            // Error parsing ZPD structure - log as warning since segment exists but is malformed
            logger.warn("Error parsing ZPD segment for blocked status", e);
        }
        return false;
    }

}
