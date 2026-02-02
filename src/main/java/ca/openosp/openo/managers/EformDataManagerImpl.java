//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.openosp.openo.commn.dao.EFormDataDao;
import ca.openosp.openo.commn.model.EFormData;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ca.openosp.openo.documentManager.ConvertToEdoc;
import ca.openosp.openo.documentManager.DocumentAttachmentManager;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.hospitalReportManager.HRMUtil;
import org.springframework.context.annotation.Lazy;

import ca.openosp.openo.eform.EFormUtil;
import ca.openosp.openo.eform.data.EForm;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.commn.model.OscarLog;
import ca.openosp.openo.encounter.data.EctFormData;

@Service
public class EformDataManagerImpl implements EformDataManager {

    @Autowired
    SecurityInfoManager securityInfoManager;

    @Autowired
    EFormDataDao eFormDataDao;

    @Autowired
    DocumentManager documentManager;

    @Autowired
    @Lazy
    private DocumentAttachmentManager documentAttachmentManager;

    @Autowired
    private FormsManager formsManager;

    public EformDataManagerImpl() {
        // Default
    }

    // @Autowired
    // public void setDocumentAttachmentManager(DocumentAttachmentManager documentAttachmentManager) {
    //     this.documentAttachmentManager = documentAttachmentManager;
    // }

    public Integer saveEformData(LoggedInInfo loggedInInfo, EForm eform) {
        Integer formid = null;

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        EFormData eFormData = EFormUtil.toEFormData(eform);
        eFormDataDao.persist(eFormData);
        formid = eFormData.getId();

        OscarLog logEntry = new OscarLog();
        logEntry.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        if (loggedInInfo.getLoggedInSecurity() != null) {
            logEntry.setSecurityId(loggedInInfo.getLoggedInSecurity().getSecurityNo());
        }
        logEntry.setAction(LogConst.ADD);
        logEntry.setContent("eform");
        logEntry.setIp(loggedInInfo.getIp());
        try {
            String demoNo = eform.getDemographicNo();
            if (demoNo != null) {
                logEntry.setDemographicId(Integer.parseInt(demoNo));
            }
        } catch (NumberFormatException e) {
            // demographic not set - continue without it
        }

        if (formid != null) {
            logEntry.setContentId(formid.toString());
            logEntry.setData("Saved eform: " + eform.getFormName());
        } else {
            logEntry.setData("Failed to save eform: " + eform.getFormName());
        }
        LogAction.addLogSynchronous(logEntry);

        return formid;
    }

    /**
     * Saves an form as PDF EDoc.
     * Returns the Eform id that was saved.
     */
    public Integer saveEformDataAsEDoc(LoggedInInfo loggedInInfo, String fdid) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }
        Integer documentId = null;
        Integer formid = null;

        if (fdid != null) {
            formid = Integer.parseInt(fdid);
            EFormData eformData = eFormDataDao.find(formid);
            EDoc edoc = ConvertToEdoc.from(eformData);
            documentManager.moveDocumentToOscarDocuments(loggedInInfo, edoc.getDocument(), edoc.getFilePath());
            edoc.setFilePath(null);
            documentId = documentManager.saveDocument(loggedInInfo, edoc);
        }

        if (documentId != null) {
            LogAction.addLogSynchronous(loggedInInfo, "EformDataManager.saveEformDataAsEDoc", "Document ID saved: " + documentId);
        } else {
            LogAction.addLogSynchronous(loggedInInfo, "EformDataManager.saveEformDataAsEDoc", "Document conversion for Eform id: " + formid + " failed.");
        }

        return documentId;
    }

    public Integer saveEFormWithAttachmentsAsEDoc(LoggedInInfo loggedInInfo, String fdid, String demographicId, Path eFormPDFPath) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, demographicId)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        EFormData eForm = eFormDataDao.find(Integer.parseInt(fdid));
        EDoc eDoc = ConvertToEdoc.from(eForm, eFormPDFPath);
        documentManager.moveDocumentToOscarDocuments(loggedInInfo, eDoc.getDocument(), eDoc.getFilePath());
        eDoc.setFilePath(null);
        return documentManager.saveDocument(loggedInInfo, eDoc);
    }

    public EFormData findByFdid(LoggedInInfo loggedInInfo, Integer fdid) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }
        return eFormDataDao.find(fdid);
    }

    /**
     * Saves an form as PDF in a temp directory.
     * <p>
     * Path to a temp file is returned. Remember to change the .tmp filetype and to delete the tmp file when finished.
     */
    public Path createEformPDF(LoggedInInfo loggedInInfo, int fdid) throws PDFGenerationException {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.UPDATE, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        EFormData eformData = eFormDataDao.find(fdid);
        Path path = null;
        try {
            path = ConvertToEdoc.saveAsTempPDF(eformData);
        } catch (Exception e) {
            throw new PDFGenerationException("Error Details: EForm [" + eformData.getFormName() + "] could not be converted into a PDF", e);
        }

        if (Files.isReadable(path)) {
            LogAction.addLogSynchronous(loggedInInfo, "EformDataManager.saveEformDataAsPDF", "Document saved at " + path.toString());
        } else {
            LogAction.addLogSynchronous(loggedInInfo, "EformDataManager.saveEformDataAsPDF", "Document failed to save for eform id " + fdid);
        }

        return path;
    }


    /**
     * Get all current eForms by demographic number but do not include the HTML data.
     * This is a good method for getting just the list and status of eForms. It's a little lighter on the database.
     * <p>
     * Returns a map - not an entity
     */
    public List<Map<String, Object>> findCurrentByDemographicIdNoData(LoggedInInfo loggedInInfo, Integer demographicId) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<Map<String, Object>> results = eFormDataDao.findByDemographicIdCurrentNoData(demographicId, Boolean.TRUE);

        if (results != null && results.size() > 0) {
            LogAction.addLogSynchronous(loggedInInfo, "FormsManager.findCurrentByDemographicIdNoData", "demo" + demographicId);
        }

        return results;
    }

    public ArrayList<HashMap<String, ? extends Object>> getHRMDocumentsAttachedToEForm(LoggedInInfo loggedInInfo, String fdid, String demographicId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicId)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<String> attachedHRMDocumentIds = documentAttachmentManager.getEFormAttachments(loggedInInfo, Integer.parseInt(fdid), DocumentType.HRM, Integer.parseInt(demographicId));
        ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, demographicId, false);
        ArrayList<HashMap<String, ? extends Object>> filteredHRMDocuments = new ArrayList<>(attachedHRMDocumentIds.size());
        for (String hrmId : attachedHRMDocumentIds) {
            for (HashMap<String, ? extends Object> hrmDocument : allHRMDocuments) {
                if (Integer.parseInt(hrmId) == (Integer) hrmDocument.get("id")) {
                    filteredHRMDocuments.add(hrmDocument);
                }
            }
        }
        //return the subset of listHRMDocuments that is attached
        return filteredHRMDocuments;
    }

    public List<EctFormData.PatientForm> getFormsAttachedToEForm(LoggedInInfo loggedInInfo, String fdid, String demographicId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicId)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<String> attachedForms = documentAttachmentManager.getEFormAttachments(loggedInInfo, Integer.parseInt(fdid), DocumentType.FORM, Integer.parseInt(demographicId));
        List<EctFormData.PatientForm> filteredForms = new ArrayList<>(attachedForms.size());
        List<EctFormData.PatientForm> allForms = formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, Integer.parseInt(demographicId), true, true);
        for (String formId : attachedForms) {
            for (EctFormData.PatientForm form : allForms) {
                if ((form.getFormId()).equals(formId)) {
                    filteredForms.add(form);
                    break;
                }
            }
        }

        return filteredForms;
    }

    public void removeEFormData(LoggedInInfo loggedInInfo, String fdid) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.DELETE, null)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }
        EFormData eFormData = eFormDataDao.find(Integer.parseInt(fdid));
        if (eFormData == null) {
            return;
        }
        eFormData.setCurrent(false);
        eFormDataDao.merge(eFormData);
    }
}
