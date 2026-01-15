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

package ca.openosp.openo.webserv;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.WebServiceException;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.cxf.annotations.GZIP;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.commn.model.CtlDocument;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.managers.DocumentManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.webserv.transfer_objects.DocumentTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class DocumentWs extends AbstractWs {
    private Logger logger = MiscUtils.getLogger();

    @Autowired
    private DocumentManager documentManager;

    @Autowired
    private ProgramManager programManager;

    public DocumentTransfer getDocument(Integer documentId) {
        try {
            LoggedInInfo loggedInInfo = getLoggedInInfo();
            Document document = documentManager.getDocument(loggedInInfo, documentId);
            CtlDocument ctlDocument = documentManager.getCtlDocumentByDocumentId(loggedInInfo, documentId);
            return (DocumentTransfer.toTransfer(document, ctlDocument));
        } catch (IOException e) {
            logger.error("Unexpected error", e);
            throw (new WebServiceException(e));
        }
    }

    public DocumentTransfer[] getDocumentsUpdateAfterDate(Date updatedAfterThisDateExclusive, int itemsToReturn) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Document> documents = documentManager.getDocumentsUpdateAfterDate(loggedInInfo, updatedAfterThisDateExclusive, itemsToReturn);
        return (DocumentTransfer.getTransfers(loggedInInfo, documents));
    }

    public DocumentTransfer[] getDocumentsByProgramProviderDemographicDate(Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Document> documents = documentManager.getDocumentsByProgramProviderDemographicDate(loggedInInfo, programId, providerNo, demographicId, updatedAfterThisDateExclusive, itemsToReturn);
        logger.debug("programId=" + programId + ", providerNo=" + providerNo + ", demographicId=" + demographicId + ", updatedAfterThisDateExclusive=" + DateFormatUtils.ISO_DATETIME_FORMAT.format(updatedAfterThisDateExclusive) + ", itemsToReturn=" + itemsToReturn + ", results=" + documents.size());
        return (DocumentTransfer.getTransfers(loggedInInfo, documents));
    }

    public DocumentTransfer[] getDocumentsByDemographicIdAfter(@WebParam(name = "lastUpdate") Calendar lastUpdate, @WebParam(name = "demographicId") Integer demographicId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Document> documents = documentManager.getDocumentsByDemographicIdUpdateAfterDate(loggedInInfo, demographicId, lastUpdate.getTime());
        return (DocumentTransfer.getTransfers(loggedInInfo, documents));
    }
}
