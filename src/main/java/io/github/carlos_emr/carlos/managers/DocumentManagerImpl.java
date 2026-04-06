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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import org.openpdf.text.DocumentException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;

import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ImagePDFCreator;

/**
 * Spring-managed implementation of the {@link DocumentManager} interface for managing
 * clinical documents in the CARLOS EMR document library.
 *
 * <p>Handles the complete document lifecycle including creation, retrieval, update,
 * file system storage, queue assignment, and rendering (converting images to PDF using
 * OpenPDF's {@link ImagePDFCreator} or resolving PDF document paths).
 *
 * <p>All document operations are protected by {@link SecurityInfoManager} privilege checks
 * on the {@code _edoc} and {@code _newCasemgmt.documents} security objects. Patient consent
 * is verified for provider-specific document access.
 *
 * <p>Documents are stored on the file system in the directory configured by the
 * {@code DOCUMENT_DIR} property, with metadata persisted in the {@code document} and
 * {@code ctl_document} database tables. PDF page counts are determined using Apache PDFBox.
 *
 * @see DocumentManager
 * @see EDocUtil
 * @since 2012 (McMaster University)
 */
@Service
public class DocumentManagerImpl implements DocumentManager {

    private static final String PARENT_DIR = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
    private final Logger logger = MiscUtils.getLogger();

    @Autowired
    private DocumentDao documentDao;

    @Autowired
    private CtlDocumentDao ctlDocumentDao;

    @Autowired
    private NioFileManager nioFileManager;

    @Autowired
    protected SecurityInfoManager securityInfoManager;

    @Autowired
    private ProviderInboxRoutingDao providerInboxRoutingDao;

    @Autowired
    private PatientConsentManager patientConsentManager;

    @Autowired
    private ProviderLabRoutingDao providerLabRoutingDao;

    @Autowired
    private PatientLabRoutingDao patientLabRoutingDao;

	@Autowired
	private QueueDocumentLinkDao queueDocumentLinkDAO;

    public Document getDocument(LoggedInInfo loggedInInfo, Integer id) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", "")) {
            throw new RuntimeException("Read Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        Document result = documentDao.find(id);

        //--- log action ---
        if (result != null) {
            LogAction.addLog(loggedInInfo, "DocumentManager.getDocument", "id=" + id, "", "", "");
        }

        return (result);
    }

    public List<Document> getDocumentsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo) {
        List<Document> result = documentDao.findByDemographicId(demographicNo + "");

        //--- log action ---
        if (result != null) {
            LogAction.addLog(loggedInInfo, "DocumentManager.getDocumentsByDemographicNo", "demographicNo=" + demographicNo, "", "", "");
        }

        return result;
    }

    public CtlDocument getCtlDocumentByDocumentId(LoggedInInfo loggedInInfo, Integer documentId) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", "")) {
            throw new RuntimeException("Read Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        CtlDocument result = ctlDocumentDao.getCtrlDocument(documentId);

        //--- log action ---
        if (result != null) {
            LogAction.addLog(loggedInInfo, "DocumentManager.getCtlDocumentByDocumentNoAndModule", "id=" + documentId, "", "", "");
        }

        return (result);
    }

    /**
     * Creates a document and saves it to the provided demographic
     *
     * @param loggedInInfo  The logged in info of the current user
     * @param document      Document to create
     * @param demographicNo The demographic number to save the document to
     * @param providerNo    The optional provider number to route the document to
     * @param documentData  The document byte data
     * @return Document record from the database once it has been created
     * @throws IOException If actions related to getting document data fail
     */
    public Document createDocument(LoggedInInfo loggedInInfo, Document document, Integer demographicNo, String providerNo, byte[] documentData) throws IOException {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", "")) {
            throw new RuntimeException("Write Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date today = new Date();
        // Generates filename and path data and saves the document data to the file system
        String documentPath = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        String fileName = dateTimeFormat.format(today) + "_" + document.getDocfilename();
		fileName = MiscUtils.sanitizeFileName(fileName);
        File file = PathValidationUtils.validatePath(fileName, new File(documentPath));
        FileUtils.writeByteArrayToFile(file, documentData);

        // Gets the number of pages for the document
        int numberOfPages = 1;
        if (fileName.toLowerCase().endsWith("pdf")) {
			try (PDDocument pdDocument = Loader.loadPDF(file)) {
            numberOfPages = pdDocument.getNumberOfPages();
			} catch (IOException e) {
				numberOfPages = 0;
			}
        } else if (fileName.toLowerCase().endsWith("html")) {
            numberOfPages = 0;
        }
        document.setNumberofpages(numberOfPages);
        document.setDoccreator(loggedInInfo.getLoggedInProviderNo());
        document.setDocfilename(fileName);
		if (document.getDocdesc() == null || document.getDocdesc().isEmpty()) { document.setDocdesc(fileName); }

        // Creates and saves the document
        saveDocument(document, demographicNo, providerNo);

		LogAction.addLogSynchronous(loggedInInfo, "DocumentManager.createDocument()", "Document ID: " + document.getId().toString() + " Demographic: " + (demographicNo != null ? demographicNo.toString() : "N/A") + " FileName: " + document.getDocfilename());

        return document;
    }

    public List<Document> getDocumentsUpdateAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive, int itemsToReturn) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", "")) {
            throw new RuntimeException("Read Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        List<Document> results = documentDao.findByUpdateDate(updatedAfterThisDateExclusive, itemsToReturn);

        LogAction.addLog(loggedInInfo, "DocumentManager.getUpdateAfterDate", "updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive, "", "", "Number items " + itemsToReturn);

        return (results);
    }

    public List<Document> getDocumentsByDemographicIdUpdateAfterDate(LoggedInInfo loggedInInfo, Integer demographicId, Date updatedAfterThisDateExclusive) {
        List<Document> results = new ArrayList<Document>();
        //If the consent type does not exist in the table assume this consent type is not being managed by the clinic, otherwise ensure patient has consented
        boolean hasConsent = patientConsentManager.hasProviderSpecificConsent(loggedInInfo) || patientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER) == null;
        if (hasConsent) {
            results = documentDao.findByDemographicUpdateAfterDate(demographicId, updatedAfterThisDateExclusive);
            LogAction.addLogSynchronous(loggedInInfo, "DocumentManager.getDocumentsByDemographicIdUpdateAfterDate", "demographicId=" + demographicId + " updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive);
        }
        return (results);
    }

    public List<Document> getDocumentsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", "")) {
            throw new RuntimeException("Read Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        List<Document> results = documentDao.findByProgramProviderDemographicUpdateDate(programId, providerNo, demographicId, updatedAfterThisDateExclusive.getTime(), itemsToReturn);

        LogAction.addLog(loggedInInfo, "DocumentManager.getDocumentsByProgramProviderDemographicDate", "programId=" + programId, "providerNo=" + providerNo, demographicId + "", "updatedAfterThisDateExclusive=" + updatedAfterThisDateExclusive.getTime());

        return (results);
    }

    public Integer saveDocument(LoggedInInfo loggedInInfo, EDoc edoc) {
        return this.saveDocument(loggedInInfo, edoc.getDocument(), edoc.getCtlDocument());
    }


    public Integer saveDocument(LoggedInInfo loggedInInfo, Document document, CtlDocument ctlDocument) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", "")) {
            throw new RuntimeException("Write Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        Integer savedId = null;

        if (document.getId() == null) {
            savedId = addDocument(loggedInInfo, document);
        } else if (document.getId() > 0) {
            savedId = updateDocument(loggedInInfo, document);
        }

        ctlDocument.getId().setDocumentNo(savedId);

        if (savedId != null) {
            ctlDocumentDao.persist(ctlDocument);
        }

        return savedId;
    }

    /**
     * Persists a document and creates associated routing records (CtlDocument,
     * PatientLabRouting, ProviderLabRouting). Uses demographicNo of -1 for
     * unattached documents.
     *
     * @param document Document the document entity to persist
     * @param demographicNo Integer the patient demographic number, or null/-1 for unattached
     * @param providerNo String the provider number for inbox routing
     */
    private void saveDocument(Document document, Integer demographicNo, String providerNo) {

        // Saves the document
        documentDao.persist(document);

        // Check that the demographic number is a valid number for a demographic, sets it to -1 if it isn't
        if (demographicNo == null || demographicNo < 1) {
            demographicNo = -1;
        }
        // Creates and saves the CtlDocument record
        CtlDocumentPK ctlDocumentPK = new CtlDocumentPK("demographic", demographicNo, document.getId());
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(ctlDocumentPK);
        ctlDocument.setStatus(String.valueOf(document.getStatus()));
        ctlDocumentDao.persist(ctlDocument);

        // Saves the patient and provider lab routings if the provided numbers are valid
        if (demographicNo > 0) {
            PatientLabRouting patientLabRouting = new PatientLabRouting(document.getId(), "DOC", demographicNo);
            patientLabRoutingDao.persist(patientLabRouting);
        }

        if (StringUtils.isNotEmpty(providerNo)) {
            ProviderLabRoutingModel providerLabRouting = new ProviderLabRoutingModel(providerNo, document.getId(), "N", "", new Date(), "DOC");
            providerLabRoutingDao.persist(providerLabRouting);
        }
    }

    /**
     * Persists a new document and logs the action.
     *
     * @param loggedInInfo LoggedInInfo the current user session for audit logging
     * @param document Document the document entity to persist
     * @return Integer the generated document ID
     */
    private Integer addDocument(LoggedInInfo loggedInInfo, Document document) {

        documentDao.persist(document);
        LogAction.addLog(loggedInInfo, "DocumentManager.saveDocument", "Document saved ", "Document No." + document.getDocumentNo(), "", "");
        return document.getId();
    }

    /**
     * Merges an existing document and logs the action.
     *
     * @param loggedInInfo LoggedInInfo the current user session for audit logging
     * @param document Document the document entity to merge
     * @return Integer the document ID
     */
    private Integer updateDocument(LoggedInInfo loggedInInfo, Document document) {
        documentDao.merge(document);
        LogAction.addLog(loggedInInfo, "DocumentManager.saveDocument", "Document updated ", "Document No." + document.getDocumentNo(), "", "");
        return document.getId();
    }

    public void moveDocumentToOscarDocuments(LoggedInInfo loggedInInfo, Document document, String fromPath) {
        moveDocument(loggedInInfo, document, fromPath, null);
    }

    public void moveDocument(LoggedInInfo loggedInInfo, Document document, String fromPath, String toPath) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "x", "")) {
            throw new RuntimeException("Read and Write Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
        }

        // move the PDF from the temp location to CARLOS document directory.
        try {
            if (toPath == null) {
                toPath = getParentDirectory();
            }
            Path from = FileSystems.getDefault().getPath(fromPath, document.getDocfilename());
            Path to = FileSystems.getDefault().getPath(toPath, document.getDocfilename());
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);

            LogAction.addLog(loggedInInfo, "EformDataManager.moveDocument", "Document was moved", "Document No." + document.getDocumentNo(), "", fromPath + " to " + toPath);

        } catch (IOException e) {
            MiscUtils.getLogger().error("Document failed move. Id: " + document.getDocumentNo() + " From: " + fromPath + " To: " + toPath, e);
            LogAction.addLog(loggedInInfo, "EformDataManager.moveDocument", "Document failed move ", "Document No." + document.getDocumentNo(), "", fromPath + " to " + toPath);
        }
    }

    /** @return String the configured parent document directory path */
    public static final String getParentDirectory() {
        return PARENT_DIR;
    }

    /**
     * Returns the full filesystem path to a document by ID, or null if not found.
     *
     * @param loggedInInfo LoggedInInfo the current user session
     * @param documentId int the document ID
     * @return String the absolute path, or null if the document doesn't exist
     */
    public String getPathToDocument(LoggedInInfo loggedInInfo, int documentId) {
        Document document = this.getDocument(loggedInInfo, documentId);
        String path = null;

        if (document != null) {
            path = getFullPathToDocument(document.getDocfilename());
        }

        return path;
    }

    /**
     * Resolves a document filename to its full filesystem path under DOCUMENT_DIR.
     * Returns null if the file does not exist on disk.
     *
     * @param filename String the document filename
     * @return String the absolute path, or null if the file doesn't exist
     */
    public String getFullPathToDocument(String filename) {

        if (filename == null || filename.isEmpty()) {
            return null;
        }

        // Reject filenames containing path separators. Stored filenames are plain basenames;
        // silently stripping a subdirectory component could resolve to a different file.
        if (filename.contains("/") || filename.contains("\\")) {
            logger.error("Document filename contains path separator, rejected: {}", Encode.forJava(filename));
            return null;
        }

        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        File validatedFile;
        try {
            validatedFile = PathValidationUtils.validatePath(filename, new File(documentDir));
        } catch (SecurityException e) {
            logger.error("Invalid document filename rejected: {}", Encode.forJava(filename));
            return null;
        }

        if (!validatedFile.exists()) {
            return null;
        }

        return validatedFile.getAbsolutePath();
    }

    /**
     * Fetch by demographic number and given document type
     * ie: get only LAB documents for the given demographic number.
     */
    public List<Document> getDemographicDocumentsByDocumentType(LoggedInInfo loggedInInfo, int demographicNo, DocumentDao.DocumentType documentType) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("Access Denied");
        }

        LogAction.addLogSynchronous(loggedInInfo, "DocumentManager.getDemographicDocumentsByDocumentType", "fetching documents of type " + documentType.getName() + " for demographic " + demographicNo);

        return documentDao.findByDemographicAndDoctype(demographicNo, documentType);
    }

    public Document getDocumentByDemographicAndFilename(LoggedInInfo loggedInInfo, int demographicNo, String fileName) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("Access Denied");
        }

        LogAction.addLogSynchronous(loggedInInfo, "DocumentManager.getDocumentByDemographicAndFilename", "fetching document with filename " + fileName + " for demographic " + demographicNo);

        return documentDao.findByDemographicAndFilename(demographicNo, fileName);
    }

    /**
     * Add a document to the CARLOS document library.
     * <p>
     * This method actually saves the Document contents to the file system. The document resource
     * MUST contain valid Base64 encoded document binary data.
     *
     * @param loggedInInfo
     * @param document
     * @return
     * @throws Exception
     */
    public Document addDocument(LoggedInInfo loggedInInfo, Document document, CtlDocument ctlDocument) throws Exception {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("Access Denied");
        }

        try {

            // is this new file or an update?
            Document existingDocument = getDocumentByDemographicAndFilename(loggedInInfo, ctlDocument.getId().getModuleId(), document.getDocfilename());

            if (existingDocument != null && existingDocument.getId() != null && existingDocument.getId() > 0) {
                document.setDocumentNo(existingDocument.getId());
            }

            // Always write to file system, updates will overwrite.
            EDocUtil.writeDocContent(document.getDocfilename(), document.getBase64Binary());

            /*
             *  This ensures that all incoming documents contain the highly required default of 0.
             *  A null here will break other parts of CARLOS functionality.
             */
            if (document.getNumberofpages() == null) {
                document.setNumberofpages(0);
            }

            /*
             *  Get the page count if the document is PDF and the page count is not already given.
             *  The page count is usually missing in documents that are imported from external sources.
             *  This method is a catch-all to ensure that the page count is not missed in all PDFs.
             */
            if ("application/pdf".equalsIgnoreCase(document.getContenttype()) && document.getNumberofpages() == 0) {
                int pagecount = EDocUtil.getPDFPageCount(document.getDocfilename());
                document.setNumberofpages(pagecount);
            }

            // save document method handles both new saves and updates
            saveDocument(loggedInInfo, document, ctlDocument);

            // confirm success if the file saved correctly.
            if (document.getId() != null && document.getId() > 0) {
                LogAction.addLogSynchronous(loggedInInfo, "DocumentManager.addDocument", "Document Id: " + document.getId());
                return document;
            }

        } catch (Exception e) {
            // catch exception, document, and then throw.
            LogAction.addLogSynchronous(loggedInInfo, "DocumentManager.addDocument", "Exception thrown during document save: " + e.getMessage());
            throw e;
        }

        return null;
    }

    public List<String> getProvidersThatHaveAcknowledgedDocument(LoggedInInfo loggedInInfo, Integer documentId) {
        List<ProviderInboxItem> inboxList = providerInboxRoutingDao.getProvidersWithRoutingForDocument("DOC", documentId);
        List<String> providerList = new ArrayList<String>();
        for (ProviderInboxItem item : inboxList) {
            if (ProviderInboxItem.ACK.equals(item.getStatus())) {
                //If this has been acknowledge add the provider_no to the list.
                providerList.add(item.getProviderNo());
            }
        }
        return providerList;
    }

    public Path renderDocument(LoggedInInfo loggedInInfo, EDoc eDoc) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("Access Denied");
        }

        return renderDocument(eDoc);
    }

    public Path renderDocument(LoggedInInfo loggedInInfo, String documentId) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("Access Denied");
        }

        EDoc eDoc = EDocUtil.getEDocFromDocId(String.valueOf(documentId));
        return renderDocument(eDoc);
    }

    private Path renderDocument(EDoc eDoc) throws PDFGenerationException {
        Path eDocPDFPath = null;
        String eDocPath = getFullPathToDocument(eDoc.getFileName());
        if (eDoc.isImage() && eDocPath != null) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ImagePDFCreator imagePDFCreator = new ImagePDFCreator(eDocPath, eDoc.getDescription(), outputStream);
                imagePDFCreator.printPdf();
                eDocPDFPath = nioFileManager.saveTempFile("temporaryPDF" + new Date().getTime(), outputStream);
            } catch (DocumentException | IOException e) {
                throw new PDFGenerationException("Error Details: Document [" + eDoc.getDescription() + "] could not be converted into a PDF", e);
            }
        } else if (eDoc.isPDF() && eDocPath != null) {
            try {
                eDocPDFPath = Paths.get(eDocPath);
            } catch (InvalidPathException e) {
                throw new PDFGenerationException("Error Details: Document [" + eDoc.getDescription() + "] could not be converted into a PDF", e);
            }
        } else {
            throw new PDFGenerationException("Error Details: Document [" + eDoc.getDescription() + "] could not be converted into a PDF");
        }
        return eDocPDFPath;
    }

	public Integer addDocumentToQueue(LoggedInInfo loggedInInfo, Integer documentId, Integer queueId) {
		if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", "")) {
			throw new RuntimeException("Write Access Denied _edoc for provider " + loggedInInfo.getLoggedInProviderNo());
		}

		if (queueId != null && queueId > 0) {
			queueDocumentLinkDAO.addActiveQueueDocumentLink(queueId, documentId);
			return queueId;
		}
		return null;
	}
}
