//CHECKSTYLE:OFF
/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2025.
 */

/*
 * CMLHandler.java
 *
 * Created on June 4, 2007, 11:19 AM
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.Logger;

import ca.openosp.openo.lab.ca.all.util.Hl7Utils;
import ca.openosp.openo.util.StringUtils;
import ca.openosp.openo.util.UtilDateUtilities;
import ca.openosp.openo.utility.MiscUtils;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.v23.datatype.XCN;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;
import ca.uhn.hl7v2.model.v23.segment.OBR;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;


/**
 *
 * @author wrighd
 */
public class CMLHandler implements MessageHandler {

    ORU_R01 msg = null;
    Logger logger = MiscUtils.getLogger();

    /** Creates a new instance of CMLHandler */
    public CMLHandler(){
    }

    @Override
    public void init(String hl7Body) throws HL7Exception {
        Parser p = new PipeParser();
        p.setValidationContext(new NoValidation());
        msg = (ORU_R01) p.parse(hl7Body.replaceAll( "\n", "\r\n" ));
    }

    @Override
    public String getMsgType(){
        return("CML");
    }

    @Override
    public String getMsgDate(){
        try {
            //return(formatDateTime(msg.getMSH().getDateTimeOfMessage().getTimeOfAnEvent().getValue()));
            return(formatDateTime(msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getObservationDateTime().getTimeOfAnEvent().getValue()));
        } catch (Exception e) {
            logger.error("Could not retrieve message date", e);
            return("");
        }
    }

    @Override
    public String getMsgPriority(){
        return("");
    }

    /**
     *  Methods to get information about the Observation Request
     */
    @Override
    public int getOBRCount(){
        return(msg.getRESPONSE().getORDER_OBSERVATIONReps());
    }

    @Override
    public int getOBXCount(int i){
        try{
            return(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATIONReps());
        }catch(Exception e){
            return(0);
        }
    }

    @Override
    public String getOBRName(int i){
        return getObrField(i, obr -> obr.getUniversalServiceIdentifier().getText().getValue());
    }

    @Override
    public String getOBRIdentifier(int i){
        return getObrField(i, obr -> obr.getUniversalServiceIdentifier().getCe1_Identifier().getValue());
    }

    @Override
    public String getTimeStamp(int i, int j){
        return getObrField(i, obr -> formatDateTime(getString(obr.getObservationDateTime().getTimeOfAnEvent().getValue())));
    }

    @Override
    public boolean isOBXAbnormal(int i, int j){
        try{
            if(getOBXAbnormalFlag(i, j).equals("A")){
                return(true);
            }else{
                return(false);
            }

        }catch(Exception e){
            return(false);
        }
    }

    @Override
    public String getOBXAbnormalFlag(int i, int j){
        try{
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getAbnormalFlags(0).getValue()));
        }catch(Exception e){
            return("");
        }
    }

    public String getOBXBlocked(int i, int j){
        try{
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getUserDefinedAccessChecks().getValue()));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getObservationHeader(int i, int j){
        try{
            return (getString(Terser.get(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX(),4,0,1,1))+" "+
                    getString(Terser.get(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX(),4,0,2,1))+" "+
                    getString(Terser.get(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX(),4,0,3,1))).trim();
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getOBXIdentifier(int i, int j){
        try{
    		Segment obxSeg = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX();
    		String subIdent = Terser.get(obxSeg, 3, 0, 1, 2) ;
    		if(subIdent != null){ //HACK: for gdml labs generated with SubmitLabByFormAction
    			return getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getIdentifier().getValue())+"&"+subIdent;
    		}
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getIdentifier().getValue()));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getOBXValueType(int i, int j){
        try{
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getValueType().getValue()));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getOBXName(int i, int j){
        try{
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getText().getValue()));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getOBXNameLong(int i, int j){
        try{
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservationIdentifier().getComponent(2).toString()));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getOBXResult(int i, int j){
        return getObxField(i, j, 5, 1); // OBX-5: Observation Value
    }

    @Override
    public String getOBXReferenceRange(int i, int j){
        return getObxField(i, j, 7, 1); // OBX-7: References Range
    }

    @Override
    public String getOBXUnits(int i, int j){
        return getObxField(i, j, 6, 1); // OBX-6: Units
    }

    @Override
    public String getOBXResultStatus(int i, int j){
        String status = "";
        try{
            status = getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getOBX().getObservResultStatus().getValue());
            if (status.equalsIgnoreCase("I"))
                status = "Pending";
            else if (status.equalsIgnoreCase("F"))
                status = "Final";
        }catch(Exception e){
            logger.error("Error retrieving obx result status", e);
            return status;
        }
        return status;
    }

    @Override
    public int getOBXFinalResultCount(){
        int obrCount = getOBRCount();
        int obxCount;
        int count = 0;
        for (int i=0; i < obrCount; i++){
            obxCount = getOBXCount(i);
            for (int j=0; j < obxCount; j++){
                if (getOBXResultStatus(i, j).equals("Final"))
                    count++;
            }
        }
        return count;
    }

    /**
     *  Retrieve the possible segment headers from the OBX fields
     */
    @Override
    public ArrayList<String> getHeaders(){
        int i;
        int j;

        ArrayList<String> headers = new ArrayList<String>();
        String currentHeader;
        try{
            for (i=0; i < msg.getRESPONSE().getORDER_OBSERVATIONReps(); i++){

                for (j=0; j < msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATIONReps(); j++){
                    // only check the obx segment for a header if it is one that will be displayed
                    if (getOBXName(i, j) != null && getOBXName(i, j).length() != 0) {
                        currentHeader = getObservationHeader(i, j);

                        if (!headers.contains(currentHeader)){
                            logger.info("Adding header: '"+currentHeader+"' to list");
                            headers.add(currentHeader);
                        }
                    }

                }

            }
            return(headers);
        }catch(Exception e){
            logger.error("Could not create header list", e);

            return(null);
        }
    }

    /**
     *  Methods to get information from observation notes
     */
    @Override
    public int getOBRCommentCount(int i){
        /*try {
            int lastOBX = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATIONReps() - 1;
            return(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(lastOBX).getNTEReps());
        } catch (Exception e) {*/
        return(0);
        // }
    }

    @Override
    public String getOBRComment(int i, int j){
       /* try {
            int lastOBX = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATIONReps() - 1;
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(lastOBX).getNTE(j).getComment(0).getValue()));
        } catch (Exception e) {*/
        return("");
        //}
    }

    /**
     *  Methods to get information from observation notes
     */
    @Override
    public int getOBXCommentCount(int i, int j){
        int count = 0;
        try {
            count = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getNTEReps();

            // a bug in getNTEReps() causes it to return 1 instead of 0 so we check to make
            // sure there actually is a comment there
            if (count == 1){
                String comment = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getNTE().getComment(0).getValue();
                if (comment == null)
                    count = 0;
            }

        } catch (Exception e) {
            logger.error("Error retrieving obx comment count", e);
        }
        return count;
    }

    @Override
    public String getOBXComment(int i, int j, int k){
        try {
            //int lastOBX = msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATIONReps() - 1;
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getNTE(k).getComment(0).getValue()));
        } catch (Exception e) {
            return("");
        }
    }


    /**
     *  Methods to get information about the patient
     */
    @Override
    public String getPatientName(){
        return(getFirstName()+" "+getLastName());
    }

    @Override
    public String getFirstName(){
        return(getString(msg.getRESPONSE().getPATIENT().getPID().getPatientName().getGivenName().getValue()));
    }

    @Override
    public String getLastName(){
        return(getString(msg.getRESPONSE().getPATIENT().getPID().getPatientName().getFamilyName().getValue()));
    }

    @Override
    public String getDOB(){
        try{
            return(formatDateTime(getString(msg.getRESPONSE().getPATIENT().getPID().getDateOfBirth().getTimeOfAnEvent().getValue())));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getAge(){
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

    @Override
    public String getSex(){
        return(getString(msg.getRESPONSE().getPATIENT().getPID().getSex().getValue()));
    }

    @Override
    public String getHealthNum(){
    	//International Student HIN - https://sourceforge.net/p/oscarmcmaster/feature-requests/1003/
    	String value = getString(msg.getRESPONSE().getPATIENT().getPID().getAlternatePatientID().getID().getValue());
    	if(StringUtils.isNullOrEmpty(value) && msg.getRESPONSE().getPATIENT().getPID().getPatientIDExternalID() != null) {
    		if(msg.getRESPONSE().getPATIENT().getPID().getPatientIDExternalID().getID() != null) {
    			String tmp = getString(msg.getRESPONSE().getPATIENT().getPID().getPatientIDExternalID().getID().getValue());
    			if(tmp != null && tmp.startsWith("MU")) {
    				value = tmp.substring(2);
    			}
    		}
    	}
    	
        return(value);
    }

    @Override
    public String getHomePhone(){
        return joinRepeatingPhoneFields(10, i ->
            msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberHome(i).get9999999X99999CAnyText().getValue()
        );
    }

    @Override
    public String getWorkPhone(){
        return joinRepeatingPhoneFields(10, i ->
            msg.getRESPONSE().getPATIENT().getPID().getPhoneNumberBusiness(i).get9999999X99999CAnyText().getValue()
        );
    }

    @Override
    public String getPatientLocation(){
        return(getString(msg.getMSH().getSendingFacility().getNamespaceID().getValue()));
    }

    @Override
    public String getServiceDate(){
        try{
            return(formatDateTime(getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getORC().getOrderEffectiveDateTime().getTimeOfAnEvent().getValue())));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getRequestDate(int i){
        try{
            return(formatDateTime(getString(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR().getRequestedDateTime().getTimeOfAnEvent().getValue())));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getOrderStatus(){
        try{
            return(getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getORC().getOrderStatus().getValue()));
        }catch(Exception e){
            return("");
        }
    }

    @Override
    public String getClientRef(){
        return joinRepeatingXcnFields(10,
            i -> msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i),
            xcn -> xcn.getIDNumber().getValue()
        );
    }

    @Override
    public String getAccessionNum(){
        String accessionNum = "";
        try{
            accessionNum = getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getORC().getPlacerOrderNumber(0).getEntityIdentifier().getValue());
            if(msg.getRESPONSE().getORDER_OBSERVATION(0).getORC().getFillerOrderNumber().getEntityIdentifier().getValue() != null){
                accessionNum = accessionNum+", "+getString(msg.getRESPONSE().getORDER_OBSERVATION(0).getORC().getFillerOrderNumber().getEntityIdentifier().getValue());
            }
            return(accessionNum);
        }catch(Exception e){
            logger.error("Could not return accession number", e);
            return("");
        }
    }

    @Override
    public String getDocName(){
        return joinRepeatingXcnFields(10,
            i -> msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(i),
            this::getFullDocName
        );
    }

    @Override
    public String getCCDocs(){
        return joinRepeatingXcnFields(10,
            i -> msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultCopiesTo(i),
            this::getFullDocName
        );
    }

    @Override
    public ArrayList<String> getDocNums(){
        ArrayList<String> docNums = new ArrayList<String>();
        String id;
        int i;

        try{
            String providerId = msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getOrderingProvider(0).getIDNumber().getValue();
            docNums.add(providerId);

            i=0;
            while((id = msg.getRESPONSE().getORDER_OBSERVATION(0).getOBR().getResultCopiesTo(i).getIDNumber().getValue()) != null){
                if (!id.equals(providerId))
                    docNums.add(id);
                i++;
            }
        }catch(Exception e){
            logger.error("Could not return doctor nums", e);

        }

        return(docNums);
    }

    @Override
    public String audit(){
        return "";
    }


    private String getFullDocName(XCN docSeg){
        String docName = "";

        if(docSeg.getPrefixEgDR().getValue() != null)
            docName = docSeg.getPrefixEgDR().getValue();

        if(docSeg.getGivenName().getValue() != null){
            if (docName.isEmpty())
                docName = docSeg.getGivenName().getValue();
            else
                docName = docName +" "+ docSeg.getGivenName().getValue();
        }
        if(docSeg.getMiddleInitialOrName().getValue() != null){
            if (docName.isEmpty())
                docName = docSeg.getMiddleInitialOrName().getValue();
            else
                docName = docName +" "+ docSeg.getMiddleInitialOrName().getValue();
        }
        if(docSeg.getFamilyName().getValue() != null){
            if (docName.isEmpty())
                docName = docSeg.getFamilyName().getValue();
            else
                docName = docName +" "+ docSeg.getFamilyName().getValue();
        }
        if(docSeg.getSuffixEgJRorIII().getValue() != null){
            if (docName.isEmpty())
                docName = docSeg.getSuffixEgJRorIII().getValue();
            else
                docName = docName +" "+ docSeg.getSuffixEgJRorIII().getValue();
        }
        if(docSeg.getDegreeEgMD().getValue() != null){
            if (docName.isEmpty())
                docName = docSeg.getDegreeEgMD().getValue();
            else
                docName = docName +" "+ docSeg.getDegreeEgMD().getValue();
        }

        return (docName);
    }


    protected String formatDateTime(String plain){
        if (plain == null || plain.trim().isEmpty()) return "";
        String trimmed = plain.trim();
        // Acceptable lengths: 8 (yyyyMMdd), 12 (yyyyMMddHHmm), 14 (yyyyMMddHHmmss)
        if (!(trimmed.matches("\\d{8}") || trimmed.matches("\\d{12}") || trimmed.matches("\\d{14}"))) {
            // Unexpected format, return empty string or handle as needed
            return "";
        }
        String dateFormat;
        String stringFormat;
        switch (trimmed.length()) {
            case 8:
                dateFormat = "yyyyMMdd";
                stringFormat = "yyyy-MM-dd";
                break;
            case 12:
                dateFormat = "yyyyMMddHHmm";
                stringFormat = "yyyy-MM-dd HH:mm";
                break;
            case 14:
                dateFormat = "yyyyMMddHHmmss";
                stringFormat = "yyyy-MM-dd HH:mm:ss";
                break;
            default:
                // Should not reach here due to regex check above
                return "";
        }
        Date date = UtilDateUtilities.StringToDate(trimmed, dateFormat);
        return UtilDateUtilities.DateToString(date, stringFormat);
    }

    protected String getString(String retrieve){
        if (retrieve != null){
            return retrieve.replaceAll("^", " ").trim();
        }else{
            return("");
        }
    }

    /**
     * Generic helper to get an OBX field value using Terser notation.
     * Eliminates repetitive try-catch and null handling.
     *
     * @param obrIndex the ORDER_OBSERVATION index
     * @param obxIndex the OBSERVATION index within the ORDER_OBSERVATION
     * @param field the HL7 field number
     * @param component the component index (usually 1)
     * @return the field value as a trimmed string, or empty string if not found
     */
    private String getObxField(int obrIndex, int obxIndex, int field, int component) {
        return Hl7Utils.safeHl7String(() ->
            getString(Terser.get(
                msg.getRESPONSE().getORDER_OBSERVATION(obrIndex).getOBSERVATION(obxIndex).getOBX(),
                field, 0, component, 1))
        );
    }

    /**
     * Generic helper to get an OBR field value.
     *
     * @param obrIndex the ORDER_OBSERVATION index
     * @param fieldExtractor lambda to extract the specific field from OBR
     * @return the field value as a trimmed string, or empty string if not found
     */
    private String getObrField(int obrIndex, Function<OBR, String> fieldExtractor) {
        return Hl7Utils.safeHl7String(() ->
            getString(fieldExtractor.apply(msg.getRESPONSE().getORDER_OBSERVATION(obrIndex).getOBR()))
        );
    }

    /**
     * Functional interface for extracting XCN fields that may throw HL7Exception.
     */
    @FunctionalInterface
    private interface XcnExtractor {
        XCN apply(int index) throws HL7Exception;
    }

    /**
     * Join repeating XCN (extended composite name) fields like providers.
     *
     * @param maxReps maximum number of repetitions to check
     * @param fieldExtractor lambda to extract XCN field at index i (may throw HL7Exception)
     * @param nameExtractor lambda to convert XCN to display name
     * @return comma-separated string of names
     */
    private String joinRepeatingXcnFields(int maxReps,
            XcnExtractor fieldExtractor,
            Function<XCN, String> nameExtractor) {
        return IntStream.range(0, maxReps)
            .mapToObj(i -> Hl7Utils.safeHl7String(() -> {
                XCN xcn = fieldExtractor.apply(i);
                return nameExtractor.apply(xcn);
            }))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));
    }

    /**
     * Functional interface for extracting phone fields that may throw HL7Exception.
     */
    @FunctionalInterface
    private interface PhoneExtractor {
        String apply(int index) throws HL7Exception;
    }

    /**
     * Join repeating phone/XTN fields by checking each repetition until empty.
     * Uses a stream approach to replace manual while loops.
     *
     * @param maxReps maximum number of repetitions to attempt (usually 10 is safe)
     * @param fieldExtractor lambda to extract phone field value at index i (may throw HL7Exception)
     * @return comma-separated string of phone numbers
     */
    private String joinRepeatingPhoneFields(int maxReps, PhoneExtractor fieldExtractor) {
        return IntStream.range(0, maxReps)
            .mapToObj(i -> Hl7Utils.safeHl7String(() -> getString(fieldExtractor.apply(i))))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));
    }

    @Override
    public String getFillerOrderNumber(){
		return "";
	}

    @Override
    public String getEncounterId(){
    	return "";
    }

    @Override
    public String getRadiologistInfo(){
		return "";
	}

    @Override
    public String getNteForOBX(int i, int j){

    	return "";
    }

    @Override
    public String getNteForPID() {
    	return "";
    }
    
    
    
    /*
     * for OMD validation (imported files)
     */
    @Override
    public boolean isTestResultBlocked(int i, int j){
        try{
            String status = getOBXBlocked(i, j);
            return "BLOCKED".equals(status);
            
        }catch(Exception e){
            logger.error("Error checking if test result is blocked", e);
            return false;
        }
    }
}
