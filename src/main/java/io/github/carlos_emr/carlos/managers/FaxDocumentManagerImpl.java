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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.log.LogAction;

@Service
public class FaxDocumentManagerImpl implements FaxDocumentManager {

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
    public Path getEformFaxDocument(LoggedInInfo loggedInInfo, int eformId) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }

        LogAction.addLogSynchronous(loggedInInfo, "FaxDocumentManager.getEformFaxDocument", "eformID: " + eformId);

        /*
         * For future code refactoring, the 'getEformFaxDocument' method is unnecessary.
         * Instead, developers should directly use 'EformDataManager.createEformPDF()'.
         */
        Path path = null;
        try {
            eformDataManager.createEformPDF(loggedInInfo, eformId);
        } catch (PDFGenerationException e) {
            MiscUtils.getLogger().error("An error occurred while creating the pdf of the eForm.", e);
        }

        return path;
    }

    public Path getFormFaxDocument(LoggedInInfo loggedInInfo, FormTransportContainer formTransportContainer) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_fax)");
        }
        LogAction.addLogSynchronous(loggedInInfo, "FaxDocumentManager.getFormFaxDocument", "eformID: " + formTransportContainer.getFormName());
        return ConvertToEdoc.saveAsTempPDF(formTransportContainer);

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
