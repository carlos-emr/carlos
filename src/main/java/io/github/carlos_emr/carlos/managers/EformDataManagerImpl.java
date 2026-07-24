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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import org.springframework.context.annotation.Lazy;

import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;

@Service
public class EformDataManagerImpl implements EformDataManager {

    private static final org.apache.logging.log4j.Logger logger =
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;

    @Autowired
    EFormDataDao eFormDataDao;

    @Autowired
    DocumentManager documentManager;

    @Autowired
    @Lazy
    private DocumentAttachmentManager documentAttachmentManager;

    @Autowired
    private FormsManager formsManager;


    private final EFormBrowserPdfService eFormBrowserPdfService;

    /**
     * @param securityInfoManager authorization gate for {@code _eform} privilege checks
     * @param eFormBrowserPdfService headless-browser renderer used to produce saved-eForm PDFs
     */
    @Autowired
    public EformDataManagerImpl(SecurityInfoManager securityInfoManager, EFormBrowserPdfService eFormBrowserPdfService) {
        this.securityInfoManager = securityInfoManager;
        this.eFormBrowserPdfService = eFormBrowserPdfService;
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
     * Renders a saved eForm as a browser-generated PDF in a managed temporary location.
     *
     * @param loggedInInfo current user; must hold demographic-scoped {@code _eform} READ for the
     *        eForm's patient. Also used for audit logging.
     * @param fdid saved eForm data identifier, looked up via {@code eFormDataDao.find(fdid)}
     * @return readable path to an {@code eform-browser-render-*.pdf} file; never {@code null}
     *         (an unreadable result throws instead). Callers are responsible for cleanup.
     * @throws SecurityException {@code missing required sec object (_eform)} when the demographic-scoped
     *         {@code _eform} READ privilege is absent
     * @throws PDFGenerationException when the eForm is not found, the browser render fails (the
     *         renderer's own message is preserved; other runtime failures are wrapped), or the
     *         renderer produces a null / unreadable output file
     */
    public Path createEformPDF(LoggedInInfo loggedInInfo, int fdid) throws PDFGenerationException {
        return createEformPDF(loggedInInfo, fdid, false);
    }

    @Override
    public Path createEformPDF(LoggedInInfo loggedInInfo, int fdid, boolean allowMissingContent) throws PDFGenerationException {
        EFormData eformData = eFormDataDao.find(fdid);
        if (eformData == null) {
            logger.warn("EForm PDF generation failed: no saved eForm found for fdid={}", fdid);
            throw new PDFGenerationException("EForm PDF generation failed because the eForm was not found.");
        }

        String demographicId = eformData.getDemographicId() == null ? null : String.valueOf(eformData.getDemographicId());
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicId)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        logger.debug("Generating eForm PDF via browser renderer: fdid={}", fdid);
        Path path;
        try {
            // Ownership transfer, not try-with-resources: this method's contract hands the raw
            // Path (and its cleanup responsibility) to OUR caller, so the handle is deliberately
            // unwrapped without close() — closing here would delete the file being returned.
            EFormBrowserPdfService.RenderedEformPdf rendered =
                    eFormBrowserPdfService.renderSavedEformPdf(fdid, loggedInInfo.getLoggedInProviderNo(), allowMissingContent);
            path = rendered == null ? null : rendered.path();
        } catch (PDFGenerationException e) {
            // The renderer already logged a redacted cause. Record which fdid failed and the exception
            // TYPE only for correlation — not e.getMessage(), which can re-emit unredacted renderer
            // text (a page-generated error, URL, or path). The message still propagates to callers/UI.
            logger.warn("EForm PDF generation failed during browser rendering: fdid={} type={}", fdid, e.getClass().getName());
            throw e;
        } catch (RuntimeException e) {
            // Only genuinely-unexpected non-renderer errors (NPE/Spring/etc.) reach here — the renderer
            // de-chains WebDriver exceptions internally, so this carries no PHI; keep the stack for triage.
            logger.error("EForm PDF generation errored during browser rendering: fdid={} type={}", fdid, e.getClass().getName(), e);
            throw new PDFGenerationException("EForm PDF generation failed during browser rendering.", e);
        }

        if (path == null) {
            logger.warn("EForm PDF generation returned no output path: fdid={}", fdid);
            throw new PDFGenerationException("EForm PDF generation failed during browser rendering.");
        }

        if (Files.isReadable(path)) {
            logger.debug("EForm PDF generation succeeded: fdid={}", fdid);
            LogAction.addLogSynchronous(loggedInInfo, "EformDataManager.saveEformDataAsPDF", "Document saved at " + path.toString());
        } else {
            LogAction.addLogSynchronous(loggedInInfo, "EformDataManager.saveEformDataAsPDF", "Document failed to save for eform id " + fdid);
            throw new PDFGenerationException("EForm PDF generation produced an unreadable temporary file.");
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
