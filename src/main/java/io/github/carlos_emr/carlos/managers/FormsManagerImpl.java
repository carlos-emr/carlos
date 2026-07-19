/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao.EFormSortOrder;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormGroupDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This class will change soon to incorporate dealing with forms
 */
@Service
public class FormsManagerImpl implements FormsManager {
    private final Logger logger = MiscUtils.getLogger();

    @Autowired
    private EFormDao eformDao;

    @Autowired
    private EFormGroupDao eFormGroupDao;

    @Autowired
    private EFormDataDao eFormDataDao;

    @Autowired
    private EncounterFormDao encounterFormDao;

    @Autowired
    DocumentManager documentManager;

    @Autowired
    private SecurityInfoManager securityInfoManager;


    /**
     * Finds all eforms based on the status.
     *
     * @param status    Status to be used when looking up forms.
     * @param sortOrder Order how records should be sorted. Providing no sort order
     *                  delegates to the default sorting order of the persistence
     *                  providers
     * @return Returns the list of all forms with the specified status.
     */
    @Override
    public List<EForm> findByStatus(LoggedInInfo loggedInInfo, boolean status, EFormSortOrder sortOrder) {
        List<EForm> results = eformDao.findByStatus(status, sortOrder);

        if (results.size() > 0) {
            String resultIds = EForm.getIdsAsStringList(results);
            LogAction.addLogSynchronous(loggedInInfo, "FormsManager.findByStatus", "ids returned=" + resultIds);
        }

        return (results);
    }

    /**
     * get eform in group by group name
     *
     * @param groupName
     * @return list of EForms
     */
    @Override
    public List<EForm> getEfromInGroupByGroupName(LoggedInInfo loggedInInfo, String groupName) {
        List<EForm> results = eformDao.getEfromInGroupByGroupName(groupName);
        if (results.size() > 0) {
            String resultIds = EForm.getIdsAsStringList(results);
            LogAction.addLogSynchronous(loggedInInfo, "FormsManager.getEfromInGroupByGroupName",
                    "ids returned=" + resultIds);
        }

        return (results);
    }

    @Override
    public List<String> getGroupNames() {
        return eFormGroupDao.getGroupNames();
    }

    @Override
    public List<EFormData> findByDemographicId(LoggedInInfo loggedInInfo, Integer demographicId) {
        List<EFormData> results = eFormDataDao.findByDemographicId(demographicId);
        if (results.size() > 0) {
            String resultIds = EForm.getIdsAsStringList(results);
            LogAction.addLogSynchronous(loggedInInfo, "FormsManager.findByDemographicId", "ids returned=" + resultIds);
        }

        return (results);

    }

    @Override
    public List<EncounterForm> getAllEncounterForms() {
        List<EncounterForm> results = encounterFormDao.findAll();
        Collections.sort(results, EncounterForm.FORM_NAME_COMPARATOR);
        return (results);
    }

    @Override
    public List<EncounterForm> getSelectedEncounterForms() {
        List<EncounterForm> results = encounterFormDao.findAllNotHidden();
        Collections.sort(results, EncounterForm.FORM_NAME_COMPARATOR);
        return (results);
    }

    @Override
    public List<PatientForm> getEncounterFormsbyDemographicNumber(LoggedInInfo loggedInInfo, Integer demographicId,
                                                                  boolean getAllVersions, boolean getOnlyPDFReadyForms) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        return processEncounterForms(loggedInInfo, demographicId, getAllVersions, getOnlyPDFReadyForms);
    }

    private List<PatientForm> processEncounterForms(LoggedInInfo loggedInInfo, Integer demographicId,
                                                    boolean getAllVersions, boolean getOnlyPDFReadyForms) {
        List<PatientForm> patientFormList = new ArrayList<PatientForm>();
        List<EncounterForm> encounterFormList = getAllEncounterForms();
        String[] pdfReadyFormNames = {"Annual"};

        for (EncounterForm encounterForm : encounterFormList) {
            String formName = encounterForm.getFormName();
            if (getOnlyPDFReadyForms && !Arrays.asList(pdfReadyFormNames).contains(formName)) {
                continue;
            }

            String table = encounterForm.getFormTable();
            PatientForm[] patientFormArray = EctFormData.getPatientForms(demographicId + "", table);
            int maxFormsToProcess = getAllVersions ? patientFormArray.length : Math.min(1, patientFormArray.length);
            for (int i = 0; i < maxFormsToProcess; i++) {
                PatientForm patientForm = patientFormArray[i];
                patientForm.setTable(table);
                patientForm.setFormName(formName);
                patientFormList.add(patientForm);
            }
        }

        return patientFormList;
    }


    private List<String> getPDFReadyFormNames() {
        List<String> pdfReadyFormList = new ArrayList<>();
        pdfReadyFormList.add("Annual");
        return pdfReadyFormList;
    }

    /**
     * Saves a form as PDF EDoc.
     * Returns the id of the converted document.
     */
    @Override
    public Integer saveFormDataAsEDoc(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        EDoc edoc = ConvertToEdoc.from(formTransportContainer);
        documentManager.moveDocument(loggedInInfo, edoc.getDocument(), edoc.getFilePath(), null);
        edoc.setFilePath(null);
        Integer documentId = documentManager.saveDocument(loggedInInfo, edoc);

        if (documentId != null) {
            LogAction.addLogSynchronous(loggedInInfo, "FormsManager.saveFormDataAsEDoc",
                    "Document ID saved: " + documentId);
        } else {
            LogAction.addLogSynchronous(loggedInInfo, "FormsManager.saveFormDataAsEDoc",
                    "Document conversion for Form " + edoc.getFileName() + " failed.");
        }

        return documentId;
    }


    /**
     * Please refrain from using this method unless your form ID is sourced from PDF-ready forms, as the form ID alone is not guaranteed to be unique.
     * To generate a PDF of a specific form, provide both the form ID and name, as they collectively ensure accurate identification.
     */
    @Override
    public Path renderForm(HttpServletRequest request, HttpServletResponse response, Integer formId, Integer demographicNo) throws PDFGenerationException {
        EctFormData.PatientForm patientForm = null;
        List<EncounterForm> encounterFormList = getAllEncounterForms();
        List<String> pdfReadyFormList = getPDFReadyFormNames();

        for (EncounterForm encounterForm : encounterFormList) {
            String formName = encounterForm.getFormName();
            String table = encounterForm.getFormTable();
            if (!pdfReadyFormList.contains(formName)) {
                continue;
            }
            patientForm = new PatientForm(table, formName, formId, demographicNo);
        }

        if (patientForm == null) {
            throw new PDFGenerationException("Error Details: Form with id: " + formId + " is not a PDF-ready form");
        }

        return renderForm(request, response, patientForm);
    }

    @Override
    public Path renderForm(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        LogAction.addLogSynchronous(loggedInInfo, "FormsManager.saveFormAsTempPdf", "");

        return ConvertToEdoc.saveAsTempPDF(formTransportContainer);
    }

    /**
     * This method processes a PatientForm, which can be null, and retrieves data using the 'formId', 'formName',
     * and 'demographicNo' parameters from the HttpServletRequest request.
     *
     * @param form    The PatientForm to process (can be null).
     * @param request The HttpServletRequest containing the parameters.
     */
    @Override
    public Path renderForm(HttpServletRequest request, HttpServletResponse response, EctFormData.PatientForm form) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() == null) {
            loggedInInfo = LoggedInInfo.getLoggedInInfoFromRequest(request);
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        FormTransportContainer formTransportContainer = getFormTransportContainer(request, response, form);
        Path path = null;
        try {
            path = ConvertToEdoc.saveAsTempPDF(formTransportContainer);
        } catch (Exception e) {
            throw new PDFGenerationException("Error Details: Form [" + formTransportContainer.getFormName() + "] could not be converted into a PDF", e);
        }
        return path;
    }

    private FormTransportContainer getFormTransportContainer(HttpServletRequest request, HttpServletResponse response,
                                                             EctFormData.PatientForm form) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String formId = request.getParameter("formId") != null ? request.getParameter("formId") : form.getFormId();
        String formName = request.getParameter("formName") != null ? request.getParameter("formName")
                : form.getFormName();
        String demographicNo = request.getParameter("demographicNo") != null ? request.getParameter("demographicNo")
                : form.getDemoNo();
        String formPath = "/form/forwardshortcutname.jsp?method=fetch&formname=" + formName + "&demographic_no="
                + demographicNo + "&formId=" + formId;
        FormTransportContainer formTransportContainer = null;
        try {
            formTransportContainer = new FormTransportContainer(response, request, formPath);
            formTransportContainer.setDemographicNo(demographicNo);
            formTransportContainer.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            formTransportContainer.setSubject(formName + " Form ID " + formId);
            formTransportContainer.setFormName(formName);
            formTransportContainer.setRealPath(request.getServletContext().getRealPath(File.separator));
        } catch (ServletException | IOException e) {
            throw new PDFGenerationException("An error occurred while processing the form. " + "Form name: " + formName,
                    e);
        }
        return formTransportContainer;
    }

    /**
     * Please refrain from using this method unless your form ID is sourced from PDF-ready forms, as the form ID alone is not guaranteed to be unique.
     * Fetch a specific form by providing both the form ID and name, as they collectively ensure accurate identification.
     */
    public PatientForm getFormById(LoggedInInfo loggedInInfo, Integer formId, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_form)");
        }

        PatientForm patientForm = null;
        List<EncounterForm> encounterFormList = getAllEncounterForms();
        List<String> pdfReadyFormList = getPDFReadyFormNames();

        for (EncounterForm encounterForm : encounterFormList) {
            String formName = encounterForm.getFormName();
            String table = encounterForm.getFormTable();
            if (!pdfReadyFormList.contains(formName)) {
                continue;
            }
            patientForm = new PatientForm(table, formName, formId, demographicNo);
            if (patientForm != null) {
                break;
            }
        }

        return patientForm;
    }

}
