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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.decisionSupport.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.decisionSupport.model.conditionValue.DSValue;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.ServiceCodeValidationLogic;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.dxresearch.bean.dxResearchBean;
import io.github.carlos_emr.carlos.dxresearch.bean.dxResearchBeanHandler;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription;

/**
 * Provides access to patient demographic, clinical, prescription, billing, and flowsheet data
 * for clinical decision support guideline evaluation.
 * <p>
 * DSDemographicAccess is the primary fact object inserted into the Drools rules engine
 * during guideline evaluation. It exposes methods for evaluating patient data against
 * guideline conditions, including diagnosis codes (ICD-9/ICD-10), prescriptions (ATC codes),
 * patient demographics (age, sex), clinical notes, billing history, and flowsheet status.
 * </p>
 * <p>
 * Each evaluation method follows a naming convention based on the {@link Module} enum's
 * access method name combined with a {@link DSCondition.ListOperator} suffix:
 * </p>
 * <ul>
 *   <li>{@code Any} - returns true if any condition value matches (OR logic)</li>
 *   <li>{@code All} - returns true if all condition values match (AND logic)</li>
 *   <li>{@code Not} - alias for Notany</li>
 *   <li>{@code Notany} - returns true if no condition values match</li>
 *   <li>{@code Notall} - returns true if not all condition values match</li>
 * </ul>
 * <p>
 * The {@code passedGuideline} flag is set to {@code true} by the Drools rule consequence
 * when all conditions are satisfied, signaling to the calling
 * {@link DSGuideline#evaluate(io.github.carlos_emr.carlos.utility.LoggedInInfo, String)}
 * method that the guideline matched.
 * </p>
 *
 * @since 2009-07-06
 * @see DSGuideline for guideline evaluation that uses this class
 * @see DSCondition for condition definitions evaluated by this class
 * @see Module for supported data source types
 */
public class DSDemographicAccess {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Defines the available healthcare data modules (data sources) for condition evaluation.
     * <p>
     * Each module maps to a base access method name that is combined with a
     * {@link DSCondition.ListOperator} suffix to form the complete evaluation method name.
     * To add new modules, add an enum value with the access method, implement the
     * corresponding Any/All/Not/Notall/Notany methods, and add to getDemogrpahicValues.
     * </p>
     *
     * @since 2009-07-06
     */
    public enum Module {
        dxcodes("hasDxCodes"),
        drugs("hasRxCodes"),
        age("isAge"),
        sex("isSex"),
        notes("noteContains"),
        billedFor("billedFor"),
        paid("paid"),
        flowsheet("flowsheetUptoDate"),
        hasATCcode("hasATCcode"),
        hasRxClass("hasRxClass");


        //define more here....

        private final String accessMethod;

        Module(String accessMethod) {
            this.accessMethod = accessMethod;
        }

        public String getAccessMethod() {
            return accessMethod;
        }
    }

    private String demographicNo;
    private String providerNo = null;
    private List<Object> dynamicArgs = null;
    private boolean passedGuideline = false;
    private Demographic demographicData;
    private List<Prescription> prescriptionData;

    private LoggedInInfo loggedInInfo;

    /**
     * Constructs a DSDemographicAccess for the specified patient.
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     */
    public DSDemographicAccess(LoggedInInfo loggedInInfo, String demographicNo) {
        this.loggedInInfo = loggedInInfo;
        this.demographicNo = demographicNo;
    }

    /**
     * Constructs a DSDemographicAccess for the specified patient with provider context.
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @param providerNo String provider identifier for provider-specific evaluation
     */
    public DSDemographicAccess(LoggedInInfo loggedInInfo, String demographicNo, String providerNo) {
        this(loggedInInfo, demographicNo);
        this.providerNo = providerNo;
    }

    /**
     * Constructs a DSDemographicAccess for the specified patient with provider context and dynamic arguments.
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @param providerNo String provider identifier for provider-specific evaluation
     * @param dynamicArgs List of Object runtime parameters (e.g., ATC codes from current prescription context)
     */
    public DSDemographicAccess(LoggedInInfo loggedInInfo, String demographicNo, String providerNo, List<Object> dynamicArgs) {
        this(loggedInInfo, demographicNo, providerNo);
        this.dynamicArgs = dynamicArgs;
        logger.debug("dynamic args size " + this.dynamicArgs.size());
    }

    /**
     * Checks that none of the dynamic ATC codes match any active prescription's ATC code.
     *
     * @param atcCodes String comma-separated ATC codes (unused; dynamic args provide the codes)
     * @return boolean true if no active prescriptions match any of the dynamic ATC codes
     */
    public boolean hasRxClassNotany(String atcCodes) {
        List<Prescription> undeletedPrescriptions = this.getPrescriptionData();
        List<Prescription> prescriptions = new ArrayList<Prescription>();

        Date now = new Date();
        Date end_date;
        for (Prescription prescription : undeletedPrescriptions) {
            end_date = prescription.getEndDate();
            if (end_date != null) {
                if (end_date.after(now)) {
                    prescriptions.add(prescription);
                }
            }
        }

        boolean notInClass = true;
        String atcCode;
        for (Object atcCodeObj : this.dynamicArgs) {
            atcCode = (String) atcCodeObj;
            for (Prescription prescription : prescriptions) {
                logger.debug("Comparing " + prescription.getAtcCode() + " to " + atcCode);
                if (prescription.getAtcCode() != null && prescription.getAtcCode().equals(atcCode)) {
                    notInClass = false;
                    break;
                }
            }

            if (!notInClass) {
                break;
            }
        }

        return notInClass;
    }

    /**
     * Checks if any of the dynamic ATC codes match an active prescription's ATC code.
     *
     * @param atcCode String ATC code parameter (delegates to hasRxClassNotany negation)
     * @return boolean true if any active prescription matches the dynamic ATC codes
     */
    public boolean hasRxClassAny(String atcCode) {
        return !hasRxClassNotany(atcCode);
    }

    /**
     * Not implemented. Checks if not all dynamic ATC codes match active prescriptions.
     *
     * @param atcCode String ATC code parameter
     * @return boolean (never returns normally)
     * @throws DecisionSupportException always thrown as this operator is not implemented
     */
    public boolean hasRxClassNotall(String atcCode) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    /**
     * Negation alias for hasRxClassNotany.
     *
     * @param atcCode String ATC code parameter
     * @return boolean true if no active prescriptions match the dynamic ATC codes
     */
    public boolean hasRxClassNot(String atcCode) {
        return hasRxClassNotany(atcCode);
    }

    /**
     * Not implemented. Checks if all dynamic ATC codes match active prescriptions.
     *
     * @param atcCode String ATC code parameter
     * @return boolean (never returns normally)
     * @throws DecisionSupportException always thrown as this operator is not implemented
     */
    public boolean hasRxClassAll(String atcCode) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }



    /**
     * Checks if any of the dynamic ATC code arguments start with the specified code prefix.
     *
     * @param rxCode DSValue containing the ATC code prefix to match
     * @return boolean true if any dynamic argument starts with the specified ATC code
     */
    public boolean hasATCcode(DSValue rxCode) {
        logger.debug("hasATCcode dynamicArgs size " + this.dynamicArgs.size());
        boolean found = false;
        String atcCode = "";

        for (Object obj : this.dynamicArgs) {
            atcCode = (String) obj;
            logger.debug("comparing " + rxCode.getValue() + " with " + atcCode);
            if (atcCode.startsWith(rxCode.getValue())) {
                found = true;
                break;
            }
        }


        return found;
    }

    /**
     * Checks if any of the specified ATC codes match the dynamic arguments (OR logic).
     *
     * @param atcCodes String comma-separated ATC codes to check
     * @return boolean true if any specified ATC code matches a dynamic argument
     */
    public boolean hasATCcodeAny(String atcCodes) {
        logger.debug("HASATCCODEANY CALLED");
        boolean found = false;
        List<DSValue> testATCcodes = DSValue.createDSValues(atcCodes);
        for (DSValue testATCcode : testATCcodes) {
            if (this.hasATCcode(testATCcode)) {
                found = true;
                break;
            }
        }

        return found;
    }

    /**
     * Not implemented. Negation check for ATC code presence.
     *
     * @param atcCode String ATC code to check
     * @return boolean (never returns normally)
     * @throws DecisionSupportException always thrown as this operator is not implemented
     */
    public boolean hasATCcodeNot(String atcCode) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    /**
     * Not implemented. Checks if not all ATC codes are present in dynamic arguments.
     *
     * @param atcCode String ATC code to check
     * @return boolean (never returns normally)
     * @throws DecisionSupportException always thrown as this operator is not implemented
     */
    public boolean hasATCcodeNotall(String atcCode) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    /**
     * Checks that none of the specified ATC codes match the dynamic arguments.
     *
     * @param atcCode String comma-separated ATC codes to check
     * @return boolean true if none of the specified ATC codes match
     */
    public boolean hasATCcodeNotany(String atcCode) {
        return !this.hasATCcodeAny(atcCode);
    }

    /**
     * Not implemented. Checks if all specified ATC codes are present in dynamic arguments.
     *
     * @param atcCode String ATC code to check
     * @return boolean (never returns normally)
     * @throws DecisionSupportException always thrown as this operator is not implemented
     */
    public boolean hasATCcodeAll(String atcCode) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }




    /**
     * Retrieves all diagnosis research codes for this patient.
     *
     * @return List of dxResearchBean objects representing the patient's diagnosis codes
     */
    public List<dxResearchBean> getDxCodes() {
        dxResearchBeanHandler handler = new dxResearchBeanHandler(demographicNo);
        List<dxResearchBean> dxCodes = handler.getDxResearchBeanVector();
        return dxCodes;
    }

    /**
     * Returns a comma-separated string of all diagnosis codes in "type:code" format.
     *
     * @return String formatted diagnosis codes (e.g., "icd9:250,icd10:E11")
     */
    public String getDxCodesStr() {
        List<dxResearchBean> dxCodes = this.getDxCodes();
        String returnStr = "";
        for (dxResearchBean dxCode : dxCodes) {
            returnStr = returnStr + dxCode.getType() + ":" + dxCode.getDxSearchCode() + ",";
        }
        if (returnStr.length() > 1)
            returnStr = returnStr.substring(0, returnStr.length() - 1); //remove the trailing comma
        return returnStr;

    }

    /**
     * Checks if the patient has a specific active diagnosis code.
     *
     * @param codeType String the coding system (e.g., "icd9", "icd10")
     * @param code String the diagnosis code value
     * @return boolean true if the patient has an active diagnosis matching the type and code
     */
    public boolean hasDxCode(String codeType, String code) {
        logger.debug("HAS DX CODES CALLED");
        List<dxResearchBean> dxCodes = this.getDxCodes();
        for (dxResearchBean dxCode : dxCodes) {
            if (dxCode.getDxSearchCode().equals(code) && dxCode.getType().equalsIgnoreCase(codeType) && dxCode.getStatus().equals("A")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the patient has any of the specified diagnosis codes (OR logic).
     *
     * @param dxCodesStr String comma-separated diagnosis codes in "type:code" format
     * @return boolean true if the patient has at least one matching active diagnosis
     */
    public boolean hasDxCodesAny(String dxCodesStr) {
        List<DSValue> testDxCodes = DSValue.createDSValues(dxCodesStr);
        for (DSValue testDxCode : testDxCodes) {
            if (this.hasDxCode(testDxCode.getValueType(), testDxCode.getValue()))
                return true;
        }
        return false;
    }

    /**
     * Checks if the patient has all of the specified diagnosis codes (AND logic).
     *
     * @param dxCodesStr String comma-separated diagnosis codes in "type:code" format
     * @return boolean true if the patient has all specified active diagnoses
     */
    public boolean hasDxCodesAll(String dxCodesStr) {
        List<DSValue> testDxCodes = DSValue.createDSValues(dxCodesStr);
        for (DSValue testDxCode : testDxCodes) {
            if (!this.hasDxCode(testDxCode.getValueType(), testDxCode.getValue()))
                return false;
        }
        return true;
    }

    /**
     * Negation alias for hasDxCodesNotany. Returns true if no specified diagnosis codes match.
     *
     * @param dxCodesStr String comma-separated diagnosis codes in "type:code" format
     * @return boolean true if the patient has none of the specified diagnoses
     */
    public boolean hasDxCodesNot(String dxCodesStr) {
        return hasDxCodesNotany(dxCodesStr);
    }

    /**
     * Checks if not all of the specified diagnosis codes are present (NOT AND logic).
     *
     * @param dxCodesStr String comma-separated diagnosis codes
     * @return boolean true if at least one specified diagnosis is absent
     */
    public boolean hasDxCodesNotall(String dxCodesStr) {
        return !hasDxCodesAll(dxCodesStr);
    }

    /**
     * Checks that none of the specified diagnosis codes are present (NOT OR logic).
     *
     * @param dxCodesStr String comma-separated diagnosis codes
     * @return boolean true if the patient has none of the specified diagnoses
     */
    public boolean hasDxCodesNotany(String dxCodesStr) {
        return !hasDxCodesAny(dxCodesStr);
    }


    /**
     * Retrieves all active prescriptions for this patient.
     *
     * @return List of Prescription objects representing the patient's active medications
     */
    public List<Prescription> getRxCodes() {
        logger.debug("GET RX CODES CALLED");
        try {
            Prescription[] prescriptions = new RxPrescriptionData().getActivePrescriptionsByPatient(Integer.parseInt(this.demographicNo));
            List<Prescription> prescribedDrugs = Arrays.asList(prescriptions);
            return prescribedDrugs;
        } catch (NumberFormatException nfe) {
            logger.error("Decision Support Exception, could not format demographicNo: " + demographicNo);
        }
        return new ArrayList<Prescription>();
    }

    /**
     * Returns a comma-separated string of all active prescription ATC codes. Primarily for testing.
     *
     * @return String formatted ATC codes (e.g., "atc:C09AA,atc:N02BE")
     * @throws DecisionSupportException if prescription data cannot be retrieved
     */
    public String getRxCodesStr() throws DecisionSupportException {
        String returnStr = "";
        try {
            for (Prescription prescription : getPrescriptionData()) {
                returnStr = returnStr + "atc:" + prescription.getAtcCode() + ",";
            }
            if (returnStr.length() > 1)
                returnStr = returnStr.substring(0, returnStr.length() - 1);  //stop the last comma
            return returnStr;
        } catch (Exception e) {
            throw new DecisionSupportException("Cannot get dugs for patient", e);
        }
    }

    /**
     * Checks if the patient has an active prescription matching the specified code.
     *
     * @param rxCode DSValue containing the prescription code to match (defaults to ATC type)
     * @return boolean true if an active prescription matches the code
     * @throws DecisionSupportException if prescription data cannot be retrieved
     */
    public boolean hasRxCode(DSValue rxCode) throws DecisionSupportException {
        String codeType = rxCode.getValueType();
        if (codeType == null) codeType = "atc";
        try {
            for (Prescription prescription : getPrescriptionData()) {
                if (codeType.equalsIgnoreCase("atc") && rxCode.testValue(prescription.getAtcCode())) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new DecisionSupportException("Cannot get prescription data for this patient", e);
        }
        return false;
    }

    /**
     * Checks if the patient has any of the specified prescription codes (OR logic).
     *
     * @param rxCodesStr String comma-separated prescription codes
     * @return boolean true if at least one prescription matches
     * @throws DecisionSupportException if prescription data cannot be retrieved
     */
    public boolean hasRxCodesAny(String rxCodesStr) throws DecisionSupportException {
        List<DSValue> testRxCodes = DSValue.createDSValues(rxCodesStr);
        for (DSValue testRxCode : testRxCodes) {
            if (this.hasRxCode(testRxCode))
                return true;
        }
        return false;
    }

    public boolean hasRxCodesAll(String rxCodesStr) throws DecisionSupportException {
        List<DSValue> testRxCodes = DSValue.createDSValues(rxCodesStr);
        for (DSValue testRxCode : testRxCodes) {
            if (!this.hasRxCode(testRxCode))
                return false;
        }
        return true;
    }

    public boolean hasRxCodesNot(String rxCodesStr) throws DecisionSupportException {
        return hasRxCodesNotany(rxCodesStr);
    }

    public boolean hasRxCodesNotall(String rxCodesStr) throws DecisionSupportException {
        return !hasRxCodesAll(rxCodesStr);
    }

    public boolean hasRxCodesNotany(String rxCodesStr) throws DecisionSupportException {
        return !hasRxCodesAny(rxCodesStr);
    }

    //not used by isAge
    public String getAge() {
        return getDemographicData(loggedInInfo).getAge() + " y";
    }

    public boolean isAge(DSValue statement) throws DecisionSupportException {
        logger.debug("IS AGE CALLED");
        String compareAge = getDemographicData(loggedInInfo).getAgeInYears() + "";
        if (statement.getValueUnit() != null) {
            if (statement.getValueUnit().equals("y")) {/*empty*/} else if (statement.getValueUnit().equals("m"))
                compareAge = DemographicData.getAgeInMonths(getDemographicData(loggedInInfo)) + "";
            else if (statement.getValueUnit().equals("d"))
                compareAge = DemographicData.getAgeInDays(getDemographicData(loggedInInfo)) + "";
            else throw new DecisionSupportException("Cannot recognize unit: " + statement.getValueUnit());
        }
        return statement.testValue(compareAge);
    }

    public long getAgeDays() {
        return DemographicData.getAgeInDays(getDemographicData(loggedInInfo));
    }

    public boolean isAgeAny(String ageStatements) throws DecisionSupportException {
        List<DSValue> statements = DSValue.createDSValues(ageStatements);
        for (DSValue statement : statements) {
            if (isAge(statement)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAgeAll(String ageStatements) throws DecisionSupportException {
        List<DSValue> statements = DSValue.createDSValues(ageStatements);
        for (DSValue statement : statements) {
            if (!isAge(statement)) {
                return false;
            }
        }
        return true;
    }

    //ageStatement: ">2y"
    public boolean isAgeNot(String ageStatement) throws DecisionSupportException {
        return isAgeNotany(ageStatement);
    }

    public boolean isAgeNotall(String ageStatement) throws DecisionSupportException {
        return !isAgeAll(ageStatement);
    }

    public boolean isAgeNotany(String ageStatement) throws DecisionSupportException {
        return !isAgeAny(ageStatement);
    }

    public String getSex() {
        return getDemographicData(loggedInInfo).getSex();
    }

    public boolean isSex(DSValue sexStatement) throws DecisionSupportException {
        logger.debug("IS SEX CALLED");
        if (sexStatement.getValue().equalsIgnoreCase("male")) sexStatement.setValue("M");
        else if (sexStatement.getValue().equalsIgnoreCase("female")) sexStatement.setValue("F");
        return sexStatement.testValue(this.getSex());
    }

    public boolean isSexAny(String sexStatements) throws DecisionSupportException {
        List<DSValue> statements = DSValue.createDSValues(sexStatements);
        for (DSValue statement : statements) {
            if (isSex(statement)) {
                return true;
            }
        }
        return false;
    }


    //makes no sense, but for consistency...
    public boolean isSexAll(String sexStatements) throws DecisionSupportException {
        List<DSValue> statements = DSValue.createDSValues(sexStatements);
        for (DSValue statement : statements) {
            if (isSex(statement)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSexNot(String sexStatement) throws DecisionSupportException {
        return isSexNotany(sexStatement);
    }

    public boolean isSexNotall(String sexStatement) throws DecisionSupportException {
        return !isSexAll(sexStatement);
    }

    public boolean isSexNotany(String sexStatement) throws DecisionSupportException {
        return !isSexAny(sexStatement);
    }


    public boolean noteContains(DSValue searchValue) {
        CaseManagementNoteDAO dao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
        List<CaseManagementNote> notes = dao.searchDemographicNotes(demographicNo, "%" + searchValue.getValue() + "%");
        if (notes != null && notes.size() > 0) return true;
        else return false;
    }

    public boolean noteContainsAny(String searchStrings) {
        List<DSValue> searchValues = DSValue.createDSValues(searchStrings);
        for (DSValue searchValue : searchValues) {
            if (noteContains(searchValue)) return true;
        }
        return false;
    }

    public boolean noteContainsAll(String searchStrings) {
        List<DSValue> searchValues = DSValue.createDSValues(searchStrings);
        for (DSValue searchValue : searchValues) {
            if (!noteContains(searchValue)) return false;
        }
        return true;
    }

    public boolean noteContainsNot(String searchStrings) {
        return noteContainsNotany(searchStrings);
    }

    public boolean noteContainsNotall(String searchStrings) {
        return !noteContainsAll(searchStrings);
    }

    public boolean noteContainsNotany(String searchStrings) {
        return !noteContainsAny(searchStrings);
    }

    @SuppressWarnings("unchecked")
    public boolean flowsheetUptoDateAny(String flowsheetId) {
        boolean retval = false;
        flowsheetId = flowsheetId.replaceAll("'", "");
        FlowSheetCustomizationDao flowSheetCustomizationDao = (FlowSheetCustomizationDao) SpringUtils.getBean(FlowSheetCustomizationDao.class);

        dxResearchBeanHandler dxRes = new dxResearchBeanHandler(demographicNo);
        List<String> dxCodes = dxRes.getActiveCodeListWithCodingSystem();
        MeasurementTemplateFlowSheetConfig templateConfig = MeasurementTemplateFlowSheetConfig.getInstance();
        ArrayList<String> flowsheets = templateConfig.getFlowsheetsFromDxCodes(dxCodes);

        boolean hasFlowSheet = false;
        for (int idx = 0; idx < flowsheets.size(); ++idx) {
            if (flowsheets.get(idx).equals(flowsheetId)) {
                hasFlowSheet = true;
                break;
            }
        }

        if (hasFlowSheet) {

            List<FlowSheetCustomization> custList = flowSheetCustomizationDao.getFlowSheetCustomizations(flowsheetId, providerNo, Integer.parseInt(demographicNo));

            MeasurementFlowSheet mFlowsheet = templateConfig.getFlowSheet(flowsheetId, custList);

            MeasurementInfo mi = new MeasurementInfo(demographicNo);
            List<String> measurementLs = mFlowsheet.getMeasurementList();

            mi.getMeasurements(measurementLs);
            try {
                mFlowsheet.getMessages(mi);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error getting messages for flowsheet ", e);
            }

            ArrayList<String> warnings = mi.getWarnings();
            if (warnings.size() == 0) {
                retval = true;
            }

        }

        return retval;
    }

    public boolean flowsheetUptoDateAll(String flowsheetId) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean flowsheetUptoDateNot(String flowsheetId) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean flowsheetUptoDateNotall(String flowsheetId) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean flowsheetUptoDateNotany(String flowsheetId) {
        return !flowsheetUptoDateAny(flowsheetId);
    }


    public boolean paidAny(String searchStrings, Map<String, String> options) {

        boolean retval = true;  //Set this optimistically that it has not been paid in the said number of days
        if (options.containsKey("payer") && options.get("payer").equals("MSP")) {
            BillingONCHeader1Dao billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
            String[] codes = searchStrings.replaceAll("'", "").split(",");

            if (options.containsKey("notInDays")) {
                int notInDays = getAsInt(options, "notInDays");
                int numDays = -1;
                for (String code : codes) {
                    //This returns how many days since the last time this code was paid and -1 if it never has been settled
                    numDays = billingONCHeader1Dao.getDaysSincePaid(code, Integer.parseInt(demographicNo));

                    //If any of the codes has been paid in the number of days then return false
                    if (numDays < notInDays && numDays != -1) {
                        retval = false;
                        break;
                    } else {
                        //if no paid bills in last number of days check to see if it has been billed within last 2 months and waits to be settled
                        numDays = billingONCHeader1Dao.getDaysSinceBilled(code, Integer.parseInt(demographicNo));

                        if (numDays < 60 && numDays != -1) {
                            retval = false;
                            break;
                        }

                    }

                    logger.debug("PAYER:MSP demo " + demographicNo + " Code:" + code + " numDays" + numDays + " notInDays:" + notInDays + " Answer: " + !(numDays < notInDays && numDays != -1) + " Setting return val to :" + retval);
                }


            }
        }

        return retval;
    }

    public boolean paidAll(String searchStrings, Map options) {

        int countPaid = 0;
        int numCodes = 0;

        if (options.containsKey("payer") && options.get("payer").equals("MSP")) {

            BillingONCHeader1Dao billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
            String[] codes = searchStrings.replaceAll("'", "").split(",");
            numCodes = codes.length;

            if (options.containsKey("inDays")) {
                int inDays = getAsInt(options, "inDays");

                for (String code : codes) {
                    //This returns how many days since the last time this code was paid and -1 if it never has been settled
                    int numDaysSinceSettled = billingONCHeader1Dao.getDaysSincePaid(code, Integer.parseInt(demographicNo));
                    int numDaysSinceBilled = billingONCHeader1Dao.getDaysSinceBilled(code, Integer.parseInt(demographicNo));

                    if (((numDaysSinceSettled <= inDays) && (numDaysSinceSettled != -1))
                            || ((numDaysSinceBilled <= inDays) && (numDaysSinceBilled != -1))) {
                        countPaid++;
                    }
                    logger.debug("PAYER:MSP demo " + demographicNo + " Code:" + code + " numDaysSinceSettled " + numDaysSinceSettled + "  numDaysSinceBilled " + numDaysSinceBilled + " inDays:" + inDays + " Answer: " + ((countPaid > 0) && (countPaid == numCodes)) + " Setting number paid to :" + countPaid);
                }
            }
        }

        return ((countPaid > 0) && (countPaid == numCodes));
    }

    public boolean paidNot(String searchStrings, Map options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean paidNotall(String searchStrings, Map options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean paidNotany(String searchStrings, Map options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    /////New Billing Functionality
    //Days since last billed
//    public boolean billedFor(String searchString,Hashtable options) throws DecisionSupportException {

//        return true;
//    }

    //Look for any of the billing codes that have been billed for this patient
    //Options:  notInDays=999              limit to the number of days to check for this code
    //          notInCalendarYear=true
    //          unitsBilledToday=<4
    //          requiresStartTime=true     not implemented yet.
    public boolean billedForAny(String searchStrings, Hashtable<String, String> options) {
        boolean retval = false;
        if (options.containsKey("payer") && options.get("payer").equals("MSP")) {
            logger.debug("PAYER:MSP ");
            ServiceCodeValidationLogic bcCodeValidation = null;
            BillingONCHeader1Dao billingONCHeader1Dao = null;
            String billregion = CarlosProperties.getInstance().getProperty("billregion", "");
            if (billregion.equalsIgnoreCase("BC")) {
                bcCodeValidation = new ServiceCodeValidationLogic();
            } else if (billregion.equalsIgnoreCase("ON")) {
                billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
            }
            String[] codes = searchStrings.replaceAll("\'", "").split(",");


            /* Has any of these codes been billed for in the past number of days.   ( if any
             *
             * If:
                icd9 code 250 is in the disease registry
                AND
                icd9 code 401 is not in the disease registry
                AND
                Any of the billing codes (13050,14050,14051,14052) Payer MSP have not been billed in the past 365 days.
             */
            if (options.containsKey("notInDays")) {
                int notInDays = getAsInt(options, "notInDays");
                retval = true;  //Set this optimistically that it has not been billed in the said number of days
                int numDays = -1;
                for (String code : codes) {
                    //This returns how many days since the last time this code was billed and -1 if it never has been billed
                    if (billregion.equalsIgnoreCase("BC")) {
                        numDays = bcCodeValidation.daysSinceCodeLastBilled(demographicNo, code);
                    } else if (billregion.equalsIgnoreCase("ON")) {
                        numDays = billingONCHeader1Dao.getDaysSinceBilled(code, Integer.parseInt(demographicNo));
                    }

                    //If any of the codes has been billed in the number of days then return false
                    if (numDays < notInDays && numDays != -1) {
                        retval = false;  // should it just return false here,  why go on once it finds a false?
                    }
                    logger.debug("PAYER:MSP demo " + demographicNo + " Code:" + code + " numDays" + numDays + " notInDays:" + notInDays + " Answer: " + !(numDays < notInDays && numDays != -1) + " Setting return val to :" + retval);

                }

            }
        }

        logger.debug("In Billed For Any  look for " + searchStrings + " returning :" + retval);
        return retval;
    }

    public boolean billedForAny2(String searchStrings, Hashtable<String, String> options) {
        boolean retval = false;
        String[] codes = searchStrings.replaceAll("\'", "").split(",");
        for (String code : codes) {

            if (options.containsKey("payer") && options.get("payer").equals("MSP")) {
                ServiceCodeValidationLogic bcCodeValidation = new ServiceCodeValidationLogic();
                if (options.containsKey("notInDays")) {
                    int notInDays = getAsInt(options, "notInDays");
                    int numDays = bcCodeValidation.daysSinceCodeLastBilled(demographicNo, code);
                    if (numDays > notInDays || numDays == -1) {
                        retval = true;
                    }
                }
//                if (options.containsKey("notInCalendarYear")){
//
//                }
            }


        }

        logger.debug("In Billed For Any  look for " + searchStrings);
        return retval;
    }

    public int getAsInt(Map options, String key) {
        String str = (String) options.get(key);
        int intval = Integer.parseInt(str);
        return intval;
    }

    public boolean billedForAll(String searchStrings, Hashtable options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForNot(String searchStrings, Hashtable options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForNotall(String searchStrings, Hashtable options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForNotany(String searchStrings, Hashtable options) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }


    public boolean billedForAny(String searchStrings) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForAll(String searchStrings) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForNot(String searchStrings) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForNotall(String searchStrings) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    public boolean billedForNotany(String searchStrings) throws DecisionSupportException {
        throw new DecisionSupportException("NOT IMPLEMENTED");
    }

    //number of units billed this year
    //number of units billed this calendar year
    //number of units billed today


    //for testing purposes mostly (used to list patient values in echart
    public String getDemogrpahicValues(Module module) {
        try {
            if (module == Module.dxcodes) return this.getDxCodesStr();
            if (module == Module.drugs) return this.getRxCodesStr();
            if (module == Module.age) return this.getAge();
            if (module == Module.sex) return this.getSex();
            if (module == Module.notes) return "";
        } catch (Exception dse) {
            logger.error("Cannot get demographic data for decision support, module: '" + module + "'", dse);
            return null;
        }
        logger.error("Decision Support Display Error: Cannot get text for module: " + module);
        return null;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    /**
     * @return the passedGuideline
     */
    public boolean isPassedGuideline() {
        return passedGuideline;
    }

    /**
     * @param passedGuideline the passedGuideline to set
     */
    public void setPassedGuideline(boolean passedGuideline) {
        this.passedGuideline = passedGuideline;
    }

    /**
     * @return the demographicData
     */
    public Demographic getDemographicData(LoggedInInfo loggedInInfo) {
        if (this.demographicData == null) {
            this.demographicData = new DemographicData().getDemographic(loggedInInfo, demographicNo);
        }
        return demographicData;
    }

    /**
     * @param demographicData the demographicData to set
     */
    public void setDemographicData(Demographic demographicData) {
        this.demographicData = demographicData;
    }

    /**
     * @return the prescriptionData
     */
    public List<Prescription> getPrescriptionData() {
        if (this.prescriptionData == null)
            setPrescriptionData(this.getRxCodes());
        return prescriptionData;
    }

    /**
     * @param prescriptionData the prescriptionData to set
     */
    public void setPrescriptionData(List<Prescription> prescriptionData) {
        this.prescriptionData = prescriptionData;
    }
}
