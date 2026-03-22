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

import java.io.IOException;
import java.nio.file.*;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import io.github.carlos_emr.carlos.documentManager.EDoc;

/**
 * Service interface for managing clinical documents in the CARLOS EMR system.
 *
 * <p>Provides comprehensive document lifecycle management including creation,
 * retrieval, storage, rendering to PDF, and acknowledgment tracking. Documents
 * are associated with patient demographics and can be queried by type, date,
 * program, or provider.</p>
 *
 * @see DocumentManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.Document
 * @since 2026-03-17
 */
public interface DocumentManager {

    /**
     * Retrieves a document by its identifier.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param id Integer the document identifier
     * @return Document the document record, or null if not found
     */
    public Document getDocument(LoggedInInfo loggedInInfo, Integer id);

    /**
     * Retrieves all documents for a patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo Integer the patient demographic number
     * @return List of Document records for the patient
     */
    public List<Document> getDocumentsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo);

    /**
     * Retrieves the control document mapping for a document identifier.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param documentId Integer the document identifier
     * @return CtlDocument the control document mapping
     */
    public CtlDocument getCtlDocumentByDocumentId(LoggedInInfo loggedInInfo, Integer documentId);

    /**
     * Creates a document and saves it to the provided demographic
     *
     * @param loggedInInfo  The logged in info of the current user
     * @param document      Document to create
     * @param demographicNo The demographic number to save the document to
     * @param providerNo    The optional providers number to route the document to
     * @param documentData  The document byte data
     * @return Document record from the database once it has been created
     * @throws IOException If actions related to getting document data fail
     */
    public Document createDocument(LoggedInInfo loggedInInfo, Document document, Integer demographicNo, String providerNo, byte[] documentData) throws IOException;

    public List<Document> getDocumentsUpdateAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Document> getDocumentsByDemographicIdUpdateAfterDate(LoggedInInfo loggedInInfo, Integer demographicId, Date updatedAfterThisDateExclusive);

    public List<Document> getDocumentsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn);

    public Integer saveDocument(LoggedInInfo loggedInInfo, EDoc edoc);


    public Integer saveDocument(LoggedInInfo loggedInInfo, Document document, CtlDocument ctlDocument);

    public void moveDocumentToOscarDocuments(LoggedInInfo loggedInInfo, Document document, String fromPath);

    public void moveDocument(LoggedInInfo loggedInInfo, Document document, String fromPath, String toPath);


    public String getPathToDocument(LoggedInInfo loggedInInfo, int documentId);

    public String getFullPathToDocument(String filename);

    /**
     * Fetch by demographic number and given document type
     * ie: get only LAB documents for the given demographic number.
     */
    public List<Document> getDemographicDocumentsByDocumentType(LoggedInInfo loggedInInfo, int demographicNo, DocumentDao.DocumentType documentType);

    public Document getDocumentByDemographicAndFilename(LoggedInInfo loggedInInfo, int demographicNo, String fileName);

    /**
     * Add a document to Oscar's document library.
     * <p>
     * This method actually saves the Document contents to the file system. The document resource
     * MUST contain valid Base64 encoded document binary data.
     *
     * @param loggedInInfo
     * @param document
     * @return
     * @throws Exception
     */
    public Document addDocument(LoggedInInfo loggedInInfo, Document document, CtlDocument ctlDocument) throws Exception;

    public List<String> getProvidersThatHaveAcknowledgedDocument(LoggedInInfo loggedInInfo, Integer documentId);

    public Path renderDocument(LoggedInInfo loggedInInfo, EDoc eDoc) throws PDFGenerationException;

    public Path renderDocument(LoggedInInfo loggedInInfo, String documentId) throws PDFGenerationException;

     public Integer addDocumentToQueue(LoggedInInfo loggedInInfo, Integer documentId, Integer queueId);
}
