/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import java.nio.file.Path;

import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.fax.util.PdfCoverPageCreator;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.log.LogAction;

@Service
public class FaxDocumentManagerImpl implements FaxDocumentManager {

    private static final org.apache.logging.log4j.Logger logger = MiscUtils.getLogger();

//	@Autowired
//	DocumentManager documentManager;

//	@Autowired 
//	private LabManager labManager;

//	@Autowired
//	private FormsManager formsManager;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private EformDataManager eformDataManager;

    /*
     * Returns a temporary path to a PDF version of the given eformId.
     */
    @Override
    public Path getEformFaxDocument(LoggedInInfo loggedInInfo, int eformId) throws PDFGenerationException {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        LogAction.addLogSynchronous(loggedInInfo, "FaxDocumentManager.getEformFaxDocument", "eformID: " + eformId);

        /*
         * For future code refactoring, the 'getEformFaxDocument' method is unnecessary.
         * Instead, developers should directly use 'EformDataManager.createEformPDF()'.
         *
         * PDFGenerationException propagates to the caller: swallowing it here and returning null
         * used to detonate later as a context-free NullPointerException in consumers that opened
         * the returned path, discarding the renderer's diagnosis.
         */
        return eformDataManager.createEformPDF(loggedInInfo, eformId);
    }

    @Override
    public Path getFormFaxDocument(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }
        LogAction.addLogSynchronous(loggedInInfo, "FaxDocumentManager.getFormFaxDocument", "formName: " + formTransportContainer.getFormName());
        Path tempPdf = ConvertToEdoc.saveAsTempPDF(formTransportContainer);
        if (tempPdf == null) {
            // A null path means the form-to-PDF conversion produced nothing; the fax preview/send flow
            // would otherwise treat this silent failure as "no document" with no trace of why.
            logger.warn("Form-to-PDF conversion for fax returned no document (form={})",
                    LogSafe.sanitize(formTransportContainer.getFormName()));
            throw new PDFGenerationException(
                    "Form-to-PDF conversion produced no document for form " + formTransportContainer.getFormName());
        }
        return tempPdf;
    }

    /**
     * Create a new cover page with the clinic heading with the
     * given cover page text.
     *
     * @param loggedInInfo
     * @param note
     * @return
     */
    public byte[] createCoverPage(LoggedInInfo loggedInInfo, String note) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        PdfCoverPageCreator pdfCoverPageCreator = new PdfCoverPageCreator(note);
        return pdfCoverPageCreator.createCoverPage();

    }

    public byte[] createCoverPage(LoggedInInfo loggedInInfo, String note, int numberPages) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }
        PdfCoverPageCreator pdfCoverPageCreator = new PdfCoverPageCreator(note, numberPages);
        return pdfCoverPageCreator.createCoverPage();
    }

    public byte[] createCoverPage(LoggedInInfo loggedInInfo, String note, FaxRecipient recipient, FaxAccount sender, int numberPages) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }
        PdfCoverPageCreator pdfCoverPageCreator = new PdfCoverPageCreator(note, numberPages, recipient, sender);
        return pdfCoverPageCreator.createCoverPage();
    }

}
