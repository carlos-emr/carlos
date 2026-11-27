/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.business;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.utility.DoseText;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.util.CaseNoteParser;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.util.JsonUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.digest.DigestUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/*
 * Author: Dennis Warren
 * Company: Colcamex Resources
 * Date: November 2014
 * For: UBC Pharmacy Clinic and McMaster Department of Family Medicine
 *
 *
 * This handler pulls specific data from Oscar's database related to a Demographic's Medication history.
 * Only certian data can be modified from a POST:
 *
 * - Family Physician Fax and Phone numbers
 * - BpmhDrug Bean data: Why, When, How, Instruction
 *   (Why, When and How have not been tested)
 * - Notes - summary notes at the bottom of a form
 *
 * The following Order of use demonstrates a typical use for building a form into a
 * request scope.
 * RETRIEVE
 * 1. setFormHistory(formBPMH.id) to retrieve form history or setDemographic(Demographic.id) to create a new form
 * 2. populateFormBean() - requests required data from database.
 * 3. redirect bean to form
 *
 * SAVE
 * 1. setDemographicNo(Demographic.id) - from request
 * 2. populateFormBean() - merges database data with edited request data
 * 3. saveFormHistory() - saves contents of form bean to the formBPMH data table.
 *
 * Additional Notes:
 *  - data persists to the formBPMH table only.
 *  - Lazy collections of BpmhDrug and Allergy beans are persisted as JSON.
 *  - form history data is retrieved from the formBPMH table as JSON and then converted to beans.
 *  - if a NULL is sent to any of the set form bean signatures - parameters will be populated with
 *  	new data automatically.
 *
 */
public class BpmhForm2Handler {

    private static final Logger logger = MiscUtils.getLogger();
    private static final int MEDICATION_HISTORY_BYTES = 16_777_215;
    private static final String[] IGNORE_METHODS = new String[]{
            "handler",
            "hibernateLazyInitializer",
            "hours",
            "minutes",
            "seconds",
            "reviewedVersion",
            "reviewChanged"};
    private static final String PRIMARY_DR_TITLE = "family physician";

    private BpmhForm2Bean bpmhFormBean;
    private FormBPMH formBPMH;
    private int demographicNo;
    private AllergyDao allergyDao;
    private DemographicCustDao demographicCustDao;
    private DemographicDao demographicDao;
    private DrugDao drugDao;
    private DrugReasonDao drugReasonDao;
    private ProviderDao providerDao;
    private OscarAppointmentDao appointmentDao;
    private Icd9Dao icd9Dao;
    private Icd9SynonymDao dxCodeTranslationsDao;
    private FormBPMHDao formBPMHDao;
    private ProfessionalSpecialistDao professionalSpecialistDao;
    private ContactSpecialtyDao specialtyDao;
    private ProfessionalContactDao proContactDao;
    private DemographicContactDao demographicContactDao;

    /**
     * Depends on:
     * (Set these after instantiation or auto-init with BpmhForm2Handler(BpmhForm2Bean bpmhForm2Bean)):
     * AllergyDao allergyDao;
     * DemographicCustDao demographicCustDao;
     * DemographicDao demographicDao;
     * DrugDao drugDao;
     * DrugReasonDao drugReasonDao;
     * ProviderDao providerDao;
     * OscarAppointmentDao appointmentDao;
     * Icd9Dao icd9Dao;
     * DxCodeTranslationsDao dxCodeTranslationsDao;
     * FormBPMHDao formBPMHDao;
     * ProfessionalSpecialistDao professionalSpecialistDao;
     * SpecialtyDao specialtyDao;
     * ProfessionalContactDao proContactDao;
     * DemographicContactDao demographicContactDao;
     */
    public BpmhForm2Handler() {
        // default constructor
    }

    public BpmhForm2Handler(BpmhForm2Bean bpmhFormBean) {
        setBpmhFormBean(bpmhFormBean);
        _init();
    }

    private void _init() {
        setDemographicDao((DemographicDao) SpringUtils.getBean(DemographicDao.class));
        setAllergyDao((AllergyDao) SpringUtils.getBean(AllergyDao.class));
        setDemographicCustDao((DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class));
        setDrugDao((DrugDao) SpringUtils.getBean(DrugDao.class));
        setDrugReasonDao((DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class));
        setProviderDao((ProviderDao) SpringUtils.getBean(ProviderDao.class));
        setAppointmentDao((OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class));
        setIcd9Dao((Icd9Dao) SpringUtils.getBean(Icd9Dao.class));
        setDxCodeTranslationsDao((Icd9SynonymDao) SpringUtils.getBean(Icd9SynonymDao.class));
        setFormBPMHDao((FormBPMHDao) SpringUtils.getBean(FormBPMHDao.class));
        setProfessionalSpecialistDao((ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class));
        setSpecialtyDao((ContactSpecialtyDao) SpringUtils.getBean(ContactSpecialtyDao.class));
        setProContactDao((ProfessionalContactDao) SpringUtils.getBean(ProfessionalContactDao.class));
        setDemographicContactDao((DemographicContactDao) SpringUtils.getBean(DemographicContactDao.class));
    }

    public void populateFormBean() {
        populateFormBean(null);
    }

    /**
     * Populates and merges all parameters of a form bean based on directive(s):
     * - save form
     * - retrieve new form
     * - retrieve form history
     */
    @SuppressWarnings("unchecked") // json conversion is unchecked.
    public void populateFormBean(BpmhForm2Bean bpmhFormBean) {
        if (bpmhFormBean != null) {
            setBpmhFormBean(bpmhFormBean);
        }

        Demographic formBeanDemographic = null;
        Provider formBeanProvider = null;
        String familyDrName = "";
        String familyDrPhone = "";
        String familyDrFax = "";
        List<BpmhDrug> formBeanDrugList = null;
        List<Allergy> formBeanAllergyList = null;

        // if form history is set, it has priority.
        if (getFormHistory() != null) {

            logger.debug("Retrieving form history number " + getFormHistory().getId());

            setDemographicNo(getFormHistory().getDemographicNo());
            getBpmhFormBean().setFormDate(getFormHistory().getFormCreated());
            getBpmhFormBean().setFormId(getFormHistory().getId() + "");
            getBpmhFormBean().setEditDate(getFormHistory().getFormEdited());
            // provider is implicitly set from form table.
            formBeanProvider = getProviderDao().getProvider(getFormHistory().getProviderNo() + "");
            familyDrName = getFormHistory().getFamilyDrName();
            familyDrPhone = getFormHistory().getFamilyDrPhone();
            familyDrFax = getFormHistory().getFamilyDrFax();
            // note is only set and saved from the form table.
            getBpmhFormBean().setNote(getFormHistory().getNote());
            getBpmhFormBean().setConfirm(Boolean.TRUE.equals(getFormHistory().getOtcReviewed()));

            // allergies and drugs are stored as serialized JSON
            formBeanAllergyList = (List<Allergy>) JsonUtil.jsonToPojoList(getFormHistory().getAllergies(), Allergy.class);
            formBeanDrugList = (List<BpmhDrug>) JsonUtil.jsonToPojoList(getFormHistory().getDrugs(), BpmhDrug.class);
        }

        logger.debug("Initializing BPMH form bean for demographic no " + getDemographicNo());
        getBpmhFormBean().setDemographicNo(getDemographicNo() + "");

        logger.debug("Populating form Bean... ");

        setFormBeanDemographic(formBeanDemographic);
        logger.debug("Setting Demographic");

        setFormBeanProvider(formBeanProvider);
        logger.debug("Setting Provider");

        setFormBeanFamilyDoctor(familyDrName, familyDrPhone, familyDrFax);
        logger.debug("Setting Family Doctor");

        setFormBeanDrugList(formBeanDrugList);
        logger.debug("Setting Drug List");

        setFormBeanAllergyList(formBeanAllergyList);
        logger.debug("Setting Allergy List");

    }

    public void setFormHistory(Integer formId) {
        this.formBPMH = getFormBPMHDao().find(formId);
    }

    public FormBPMH getFormHistory() {
        return this.formBPMH;
    }

    public Integer saveFormHistory() {
        return saveFormHistory(getBpmhFormBean());
    }

    public Integer saveFormHistory(BpmhForm2Bean bpmhFormBean) {

        FormBPMH formBpmh = new FormBPMH(); // BPMH form JPA model bean

        // Init JSON strings
        String jsonDrugs = "";
        String jsonAllergies = "";

        // Posted drug and allergy lists
        List<BpmhDrug> drugList = bpmhFormBean.getDrugs();
        List<Allergy> allergyList = bpmhFormBean.getAllergies();

        formBpmh.setFormCreated(bpmhFormBean.getFormDate());
        formBpmh.setFormEdited(bpmhFormBean.getEditDate());

        formBpmh.setDemographicNo(Integer.parseInt(bpmhFormBean.getDemographicNo()));
        formBpmh.setProviderNo(Integer.parseInt(bpmhFormBean.getProvider().getProviderNo()));
        formBpmh.setNote(bpmhFormBean.getNote());
        formBpmh.setOtcReviewed(bpmhFormBean.isConfirm());
        formBpmh.setFamilyDrName(bpmhFormBean.getFamilyDrName());
        formBpmh.setFamilyDrPhone(bpmhFormBean.getFamilyDrPhone());
        formBpmh.setFamilyDrFax(bpmhFormBean.getFamilyDrFax());

        if (drugList != null) {
            jsonDrugs = JsonUtil.pojoCollectionToJson(drugList, IGNORE_METHODS);
            if (jsonDrugs.getBytes(StandardCharsets.UTF_8).length > MEDICATION_HISTORY_BYTES) {
                throw new IllegalArgumentException("Medication history exceeds the database capacity");
            }
        }

        if (allergyList != null) {
            jsonAllergies = JsonUtil.pojoCollectionToJson(allergyList, IGNORE_METHODS);
            if (jsonAllergies.getBytes(StandardCharsets.UTF_8).length > 65_535) {
                throw new IllegalArgumentException("Allergy history exceeds the database capacity");
            }
        }

        formBpmh.setAllergies(jsonAllergies);
        formBpmh.setDrugs(jsonDrugs);

        getFormBPMHDao().persist(formBpmh);

        return formBpmh.getId();

    }

    public void setFormBeanDemographic(Demographic demographic) {

        if (demographic == null) {
            demographic = getDemographicDao().getDemographicById(demographicNo);
        }

        getBpmhFormBean().setDemographic(demographic);
    }

    public void setFormBeanProvider(Provider provider) {

        List<Appointment> appointmentList = null;
        List<Provider> providerList = null;
        Appointment lastAppointment = null;

        if (provider == null) {

            appointmentList = getAppointmentDao().getAppointmentHistory(demographicNo, 0, 3);
            providerList = getProviderDao().getProviderByPatientId(demographicNo);

            logger.debug("Found " + appointmentList.size() + " appointments for this patient.");

            if ((appointmentList != null) && (appointmentList.size() > 0)) {
                lastAppointment = appointmentList.get(0);
            }

            if (providerList != null && providerList.size() > 0) {
                provider = providerList.get(0);
            }

            // check other sources.
            if ((provider == null) && (lastAppointment != null)) {
                provider = getProviderDao().getProvider(lastAppointment.getProviderNo());
            }

            // the prepared-on date is the same as the last appointment
            if (lastAppointment != null) {

                logger.debug("Setting Form Prepared Date");
                getBpmhFormBean().setFormDate(lastAppointment.getAppointmentDate());
            }

        }

        getBpmhFormBean().setProvider(provider);

    }

    /**
     * Sets the family Dr. Field of the BPMH form.
     * First checks the Demographic if FP is set, then checks the
     * note fields.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public void setFormBeanFamilyDoctor(String familyDrName, String familyDrPhone, String familyDrFax) {

        List<DemographicCust> demographicCustList = null;
        String parseMe = "";
        List<DemographicContact> demographicContacts = null;
        Integer demographicContactId = null;
        String familyDrRole = "";
        String familyDrContactId = "";
        Integer familyDrId = null;
        Integer contactType = null;
        ProfessionalSpecialist professionalSpecialist = null;
        Contact professionalContact = null;

        if (familyDrName.isEmpty() && familyDrPhone.isEmpty() &&
                familyDrFax.isEmpty() && getFormHistory() == null) {

            // look for the Dr. in the demographic health care team.
            demographicContacts = getDemographicContactDao().findByDemographicNoAndCategory(demographicNo, DemographicContact.CATEGORY_PROFESSIONAL);

            for (DemographicContact demographicContact : demographicContacts) {

                if (StringUtils.isNumeric(demographicContact.getRole())) {
                    familyDrRole = getSpecialtyDao().find(Integer.parseInt(demographicContact.getRole())).getSpecialty();
                } else {
                    familyDrRole = demographicContact.getRole();
                }

                if (PRIMARY_DR_TITLE.equalsIgnoreCase(familyDrRole)) {
                    demographicContactId = demographicContact.getId();
                    familyDrContactId = demographicContact.getContactId().trim();

                    if (StringUtils.isNumeric(familyDrContactId)) {
                        familyDrId = Integer.parseInt(familyDrContactId);
                    }

                    contactType = demographicContact.getType();
                }
            }

            logger.debug("Found Family Dr. in Health Care Contacts. Provider Type: " + contactType + " Provider ID: " + familyDrId);

            // populate the Dr. found in the Health Care Team
            if (familyDrId != null && contactType != null) {

                if (contactType == DemographicContact.TYPE_PROFESSIONALSPECIALIST) {

                    professionalSpecialist = getProfessionalSpecialistDao().find(familyDrId);
                    familyDrName = professionalSpecialist.getFormattedName().trim();
                    familyDrPhone = professionalSpecialist.getPhoneNumber().trim();
                    familyDrFax = professionalSpecialist.getFaxNumber().trim();

                } else {

                    professionalContact = getProContactDao().find(familyDrId);
                    familyDrName = professionalContact.getFormattedName().trim();
                    familyDrPhone = professionalContact.getWorkPhone().trim();
                    familyDrFax = professionalContact.getFax().trim();

                }

                // otherwise check the Demogrpahic notes - just in case (soon to be deprecated.)
            } else {

                demographicCustList = getDemographicCustDao().findAllByDemographicNumber(demographicNo);

                if ((demographicCustList != null) && (demographicCustList.size() > 0)) {
                    parseMe = (demographicCustList.get(0)).getNotes();
                }

                // Do NOT log parseMe: it is the free-text demographic notes field (PHI).
                logger.debug("Scanning demographic note field for a family physician reference");

                if (!parseMe.isEmpty()) {
                    familyDrName = CaseNoteParser.getFamilyDr(parseMe);
                    familyDrPhone = CaseNoteParser.getPhoneNumber(parseMe);
                    familyDrFax = CaseNoteParser.getFaxNumber(parseMe);
                }

            }
        }

        getBpmhFormBean().setFamilyDrContactId(demographicContactId + "");
        getBpmhFormBean().setFamilyDrName(familyDrName);
        getBpmhFormBean().setFamilyDrPhone(familyDrPhone);
        getBpmhFormBean().setFamilyDrFax(familyDrFax);

    }

    /**
     * Set the BpmhForm2Bean with a list of BpmhDrug beans.
     * <p>
     * If sent a null parameter a new list will be built.
     * <p>
     * BpmhDrug beans contain data from the database Drugs JPA along
     * with additional transient data.
     * <p>
     * Depends on DrugDao, BpmhForm2Bean, BpmhDrug
     *
     * @param bpmhDrugBeans
     */
    // FindSecBugs BEAN_PROPERTY_INJECTION: Spring BeanUtils.copyProperties copies fixed JavaBean
    // descriptors between known CARLOS types; no user-controlled property name reaches the sink.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Spring BeanUtils.copyProperties copies fixed JavaBean descriptors between " +
                    "known CARLOS types; no user-controlled property name reaches the sink")
    public void setFormBeanDrugList(List<BpmhDrug> bpmhDrugBeans) {

        if (bpmhDrugBeans != null) {

            getBpmhFormBean().setDrugs(bpmhDrugBeans);

        } else {

            List<Drug> drugList = getDrugDao().findByDemographicId(this.demographicNo, false);

            // Every row is built from the patient's current medications. A posted row supplies only its
            // instruction, matched by drug id; any other posted field, and any id that is not a current
            // medication, is dropped (#4731).
            List<BpmhDrug> postedRows = getBpmhFormBean().getDrugs();
            Map<String, BpmhDrug> editedChartRows = new LinkedHashMap<>();
            List<BpmhDrug> reportedRows = new ArrayList<>();
            if (postedRows != null) {
                for (BpmhDrug posted : postedRows) {
                    if (posted == null) continue;
                    if (StringUtils.isNotBlank(posted.getId())) {
                        editedChartRows.putIfAbsent(posted.getId(), posted);
                    } else if (StringUtils.isNotBlank(posted.getReportedMedication())) {
                        reportedRows.add(posted);
                    }
                }
            }
            bpmhDrugBeans = new ArrayList<>();
            getBpmhFormBean().setDrugs(bpmhDrugBeans);
            StringBuilder versions = new StringBuilder().append(demographicNo).append(':');

            if (drugList != null) {
                // Use the numeric source position, retaining DAO order among equal positions.
                List<Drug> ordered = new ArrayList<>(drugList);
                ordered.sort(Comparator.comparing(Drug::getPosition, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Drug::getId));
                for (Drug drug : ordered) {
                    String drugId = drug.getId().toString();
                    BpmhDrug bpmhDrug = new BpmhDrug();
                    BeanUtils.copyProperties(drug, bpmhDrug);
                    bpmhDrugBeans.add(bpmhDrug);
                    // Spring's copyProperties skips Integer -> String, so the id is set here: the form posts it back
                    // to match each typed instruction to its drug (#4731).
                    bpmhDrug.setId(drugId);
                    // The structured dose is a float on the drug and text on the form, so it is not
                    // copied either. Without it "How I take it" read "Take TAB BID" with no amount
                    // whenever the instruction text had no direction line (#6185).
                    // Only a positive number is a dose: 0 is the column default for "no structured
                    // dose" and the legacy editor stores -1 for a dose it was not given.
                    float min = drug.getTakeMin();
                    float max = drug.getTakeMax();
                    if (Float.isFinite(min) && Float.isFinite(max) && min > 0 && max >= min) {
                        bpmhDrug.setTakeMin(DoseText.of(min));
                        bpmhDrug.setTakeMax(DoseText.of(max));
                    }
                    // A selected product's unit can be its strength unit. Its form alone
                    // cannot establish an administered dose. A CDS import records the dose unit.
                    String doseUnit = RxUtil.recordedDoseUnit(drug.getUnit(), drug.getDrugForm(),
                            drug.getDosage(), drug.getCdsDoseText());
                    bpmhDrug.usePrescriptionDirections(drug.getBrandName(), drug.getSpecialInstruction(),
                            StringUtils.isBlank(drug.getCdsDoseText()) ? doseUnit : null);
                    if (StringUtils.isBlank(bpmhDrug.getHow()) && StringUtils.isNotBlank(drug.getCdsDoseText())) {
                        bpmhDrug.useRecordedDoseText(drug.getCdsDoseText(), doseUnit);
                    }
                    bpmhDrug.setWhy(getDrugReason(drug.getId()));
                    String version = reviewVersion(drugId, drug.getRowVersion(), bpmhDrug);
                    bpmhDrug.setReviewedVersion(version);
                    versions.append(version);
                    BpmhDrug edited = editedChartRows.remove(drugId);
                    if (edited != null) {
                        bpmhDrug.setInstruction(edited.getInstruction());
                        bpmhDrug.setPatientReportedUse(edited.getPatientReportedUse());
                    }
                    bpmhDrug.setReviewChanged(postedRows != null
                            && (edited == null || !version.equals(edited.getReviewedVersion())));
                }
            }
            getBpmhFormBean().setReviewToken(DigestUtils.sha256Hex(versions.toString()));
            getBpmhFormBean().setRemovedReviewRows(new ArrayList<>(editedChartRows.values()));
            bpmhDrugBeans.addAll(reportedRows);
        }

    }

    private static String reviewVersion(String id, Long rowVersion, BpmhDrug drug) {
        StringBuilder fields = new StringBuilder();
        for (String value : List.of(id, String.valueOf(rowVersion), drug.getWhat(), drug.getHow(), drug.getWhy())) {
            fields.append(value.length()).append(':').append(value);
        }
        return DigestUtils.sha256Hex(fields.toString());
    }

    public String getDrugReason(Integer drugId) {

        List<DrugReason> reasons = getDrugReasonDao().getReasonsForDrugID(drugId, true);
        if (reasons == null) return "";
        List<String> descriptions = new ArrayList<>();
        for (DrugReason reason : reasons) {
            String description = describeReason(reason);
            String comment = reason.getComments();
            if (StringUtils.isNotBlank(comment)) {
                descriptions.add(description.isEmpty() ? comment : description + " " + comment);
            } else if (!description.isEmpty()) {
                descriptions.add(description);
            }
        }
        return String.join("\n", descriptions);
    }

    private String describeReason(DrugReason reason) {
        String code = reason.getCode();
        if (StringUtils.isBlank(code)) return "";
        String system = reason.getCodingSystem();
        if (org.apache.commons.lang3.Strings.CI.equals("icd9", system)) {
            Icd9Synonym translation = getDxCodeTranslationsDao().findPatientFriendlyTranslationFor(code);
            if (translation != null && StringUtils.isNotBlank(translation.getPatientFriendly())) {
                return translation.getPatientFriendly();
            }
            Icd9 entry = getIcd9Dao().findByCode(code);
            if (entry != null && StringUtils.isNotBlank(entry.getDescription())) return entry.getDescription();
        }
        return StringUtils.isBlank(system) ? code : system + " " + code;
    }

    public void setFormBeanAllergyList(List<Allergy> allergyList) {

        if (allergyList == null && getFormHistory() == null) {
            allergyList = getAllergyDao().findActiveAllergies(demographicNo);
        }

        if ((allergyList != null) && (allergyList.size() > 0)) {
            getBpmhFormBean().setAllergies(allergyList);
        }

    }

    public BpmhForm2Bean getBpmhFormBean() {
        if (bpmhFormBean == null) {
            throw new RuntimeException("BPMH Bean Not Set");
        }
        return bpmhFormBean;
    }

    public void setBpmhFormBean(BpmhForm2Bean bpmhFormBean) {
        this.bpmhFormBean = new BpmhForm2Bean();
        this.bpmhFormBean.setFormDate(bpmhFormBean.getFormDate());
        this.bpmhFormBean.setEditDate(bpmhFormBean.getEditDate());
        this.bpmhFormBean.setFormId(bpmhFormBean.getFormId());
        this.bpmhFormBean.setConfirm(bpmhFormBean.isConfirm());
        this.bpmhFormBean.setNote(bpmhFormBean.getNote());
        this.bpmhFormBean.setDemographicNo(bpmhFormBean.getDemographicNo());
        this.bpmhFormBean.setFamilyDrContactId(bpmhFormBean.getFamilyDrContactId());
        this.bpmhFormBean.setFamilyDrName(bpmhFormBean.getFamilyDrName());
        this.bpmhFormBean.setFamilyDrPhone(bpmhFormBean.getFamilyDrPhone());
        this.bpmhFormBean.setFamilyDrFax(bpmhFormBean.getFamilyDrFax());
        this.bpmhFormBean.setAllergiesString(bpmhFormBean.getAllergiesString());
        this.bpmhFormBean.setDemographic(bpmhFormBean.getDemographic());
        this.bpmhFormBean.setDemographicCust(bpmhFormBean.getDemographicCust());
        this.bpmhFormBean.setProvider(bpmhFormBean.getProvider());
        this.bpmhFormBean.setAllergies(bpmhFormBean.getAllergies());
        this.bpmhFormBean.setDrugs(bpmhFormBean.getDrugs());
    }

    public int getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(int demographicNo) {
        this.demographicNo = demographicNo;
    }

    public AllergyDao getAllergyDao() {
        return allergyDao;
    }

    public void setAllergyDao(AllergyDao allergyDao) {
        this.allergyDao = allergyDao;
    }

    public DemographicCustDao getDemographicCustDao() {
        return demographicCustDao;
    }

    public void setDemographicCustDao(DemographicCustDao demographicCustDao) {
        this.demographicCustDao = demographicCustDao;
    }

    public DemographicDao getDemographicDao() {
        return demographicDao;
    }

    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    public DrugDao getDrugDao() {
        return drugDao;
    }

    public void setDrugDao(DrugDao drugDao) {
        this.drugDao = drugDao;
    }

    public DrugReasonDao getDrugReasonDao() {
        return drugReasonDao;
    }

    public void setDrugReasonDao(DrugReasonDao drugReasonDao) {
        this.drugReasonDao = drugReasonDao;
    }

    public ProviderDao getProviderDao() {
        return providerDao;
    }

    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

    public OscarAppointmentDao getAppointmentDao() {
        return appointmentDao;
    }

    public void setAppointmentDao(OscarAppointmentDao appointmentDao) {
        this.appointmentDao = appointmentDao;
    }

    public Icd9Dao getIcd9Dao() {
        return icd9Dao;
    }

    public void setIcd9Dao(Icd9Dao icd9Dao) {
        this.icd9Dao = icd9Dao;
    }

    public Icd9SynonymDao getDxCodeTranslationsDao() {
        return dxCodeTranslationsDao;
    }

    public void setDxCodeTranslationsDao(Icd9SynonymDao dxCodeTranslationsDao) {
        this.dxCodeTranslationsDao = dxCodeTranslationsDao;
    }

    public FormBPMHDao getFormBPMHDao() {
        return formBPMHDao;
    }

    public void setFormBPMHDao(FormBPMHDao formBPMHDao) {
        this.formBPMHDao = formBPMHDao;
    }

    public ProfessionalSpecialistDao getProfessionalSpecialistDao() {
        return professionalSpecialistDao;
    }

    public void setProfessionalSpecialistDao(ProfessionalSpecialistDao professionalSpecialistDao) {
        this.professionalSpecialistDao = professionalSpecialistDao;
    }

    public ContactSpecialtyDao getSpecialtyDao() {
        return specialtyDao;
    }

    public void setSpecialtyDao(ContactSpecialtyDao specialtyDao) {
        this.specialtyDao = specialtyDao;
    }

    public ProfessionalContactDao getProContactDao() {
        return proContactDao;
    }

    public void setProContactDao(ProfessionalContactDao proContactDao) {
        this.proContactDao = proContactDao;
    }

    public DemographicContactDao getDemographicContactDao() {
        return demographicContactDao;
    }

    public void setDemographicContactDao(DemographicContactDao demographicContactDao) {
        this.demographicContactDao = demographicContactDao;
    }

}
