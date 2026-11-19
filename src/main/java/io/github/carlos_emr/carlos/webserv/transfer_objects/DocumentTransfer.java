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

package io.github.carlos_emr.carlos.webserv.transfer_objects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.DocumentListProfile;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.BeanUtils;

public final class DocumentTransfer {
    private static final Logger logger = MiscUtils.getLogger();

    private Integer documentNo;
    private String doctype;
    private String docClass;
    private String docSubClass;
    private String docdesc;
    private String docxml;
    private String docfilename;
    private String doccreator;
    private String responsible;
    private String source;
    private String sourceFacility;
    private Integer programId;
    private Date updatedatetime;
    private char status;
    private String contenttype;
    private Date contentdatetime;
    private String cdsContentPrecision;
    private int public1;
    private Date observationdate;
    private String reviewer;
    private Date reviewdatetime;
    private Integer numberofpages;
    private Integer appointmentNo;

    private String ctlModule;
    private Integer ctlModuleId;
    private String ctlStatus;

    private byte[] fileContents;

    public Integer getDocumentNo() {
        return (documentNo);
    }

    public void setDocumentNo(Integer documentNo) {
        this.documentNo = documentNo;
    }

    public String getDoctype() {
        return (doctype);
    }

    public void setDoctype(String doctype) {
        this.doctype = doctype;
    }

    public String getDocClass() {
        return (docClass);
    }

    public void setDocClass(String docClass) {
        this.docClass = docClass;
    }

    public String getDocSubClass() {
        return (docSubClass);
    }

    public void setDocSubClass(String docSubClass) {
        this.docSubClass = docSubClass;
    }

    public String getDocdesc() {
        return (docdesc);
    }

    public void setDocdesc(String docdesc) {
        this.docdesc = docdesc;
    }

    public String getDocxml() {
        return (docxml);
    }

    public void setDocxml(String docxml) {
        this.docxml = docxml;
    }

    public String getDocfilename() {
        return (docfilename);
    }

    public void setDocfilename(String docfilename) {
        this.docfilename = docfilename;
    }

    public String getDoccreator() {
        return (doccreator);
    }

    public void setDoccreator(String doccreator) {
        this.doccreator = doccreator;
    }

    public String getResponsible() {
        return (responsible);
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public String getSource() {
        return (source);
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceFacility() {
        return (sourceFacility);
    }

    public void setSourceFacility(String sourceFacility) {
        this.sourceFacility = sourceFacility;
    }

    public Integer getProgramId() {
        return (programId);
    }

    public void setProgramId(Integer programId) {
        this.programId = programId;
    }

    @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(io.github.carlos_emr.carlos.utility.xml.OptionalDateXmlAdapter.class)
    @jakarta.xml.bind.annotation.XmlSchemaType(name = "dateTime")
    public Date getUpdatedatetime() {
        return (updatedatetime);
    }

    public void setUpdatedatetime(Date updatedatetime) {
        this.updatedatetime = updatedatetime;
    }

    public char getStatus() {
        return (status);
    }

    public void setStatus(char status) {
        this.status = status;
    }

    public String getContenttype() {
        return (contenttype);
    }

    public void setContenttype(String contenttype) {
        this.contenttype = contenttype;
    }

    @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(io.github.carlos_emr.carlos.utility.xml.OptionalDateXmlAdapter.class)
    @jakarta.xml.bind.annotation.XmlSchemaType(name = "dateTime")
    public Date getContentdatetime() { return contentdatetime; }
    public void setContentdatetime(Date value) { contentdatetime=value; }
    /** Imported source precision; no timestamp is inferred for an absent source clock. */
    public String getCdsContentPrecision() { return cdsContentPrecision; }
    public void setCdsContentPrecision(String value) { cdsContentPrecision=value; }

    public int getPublic1() {
        return (public1);
    }

    public void setPublic1(int public1) {
        this.public1 = public1;
    }

    @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(io.github.carlos_emr.carlos.utility.xml.OptionalDateXmlAdapter.class)
    @jakarta.xml.bind.annotation.XmlSchemaType(name = "dateTime")
    public Date getObservationdate() {
        return (observationdate);
    }

    public void setObservationdate(Date observationdate) {
        this.observationdate = observationdate;
    }

    public String getReviewer() {
        return (reviewer);
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(io.github.carlos_emr.carlos.utility.xml.OptionalDateXmlAdapter.class)
    @jakarta.xml.bind.annotation.XmlSchemaType(name = "dateTime")
    public Date getReviewdatetime() {
        return (reviewdatetime);
    }

    public void setReviewdatetime(Date reviewdatetime) {
        this.reviewdatetime = reviewdatetime;
    }

    public Integer getNumberofpages() {
        return (numberofpages);
    }

    public void setNumberofpages(Integer numberofpages) {
        this.numberofpages = numberofpages;
    }

    public Integer getAppointmentNo() {
        return (appointmentNo);
    }

    public void setAppointmentNo(Integer appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public String getCtlModule() {
        return (ctlModule);
    }

    public void setCtlModule(String ctlModule) {
        this.ctlModule = ctlModule;
    }

    public Integer getCtlModuleId() {
        return (ctlModuleId);
    }

    public void setCtlModuleId(Integer ctlModuleId) {
        this.ctlModuleId = ctlModuleId;
    }

    public String getCtlStatus() {
        return (ctlStatus);
    }

    public void setCtlStatus(String ctlStatus) {
        this.ctlStatus = ctlStatus;
    }

    public byte[] getFileContents() {
        return (fileContents);
    }

    public void setFileContents(byte[] fileContents) {
        this.fileContents = fileContents;
    }

    /**
     * ctlDocument can be null
     */
    // FindSecBugs BEAN_PROPERTY_INJECTION: Spring BeanUtils.copyProperties copies fixed JavaBean
    // descriptors between known CARLOS types; no user-controlled property name reaches the sink.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Spring BeanUtils.copyProperties copies fixed JavaBean descriptors between " +
                    "known CARLOS types; no user-controlled property name reaches the sink")
    public static DocumentTransfer toTransfer(Document document, CtlDocument ctlDocument) throws IOException {
        return toTransfer(document, ctlDocument, true);
    }

    private static DocumentTransfer toTransfer(Document document, CtlDocument ctlDocument, boolean inline) throws IOException {
        if (document == null) return (null);
        requireLegacyProjection(document);

        DocumentTransfer documentTransfer = new DocumentTransfer();

        BeanUtils.copyProperties(document, documentTransfer);

        if (ctlDocument != null) {
            documentTransfer.setCtlModule(ctlDocument.getId().getModule());
            documentTransfer.setCtlModuleId(ctlDocument.getId().getModuleId());
            documentTransfer.setCtlStatus(ctlDocument.getStatus());
        }

        if (inline) documentTransfer.setFileContents(document.getDocumentFileContentsAsBytes());

        return (documentTransfer);
    }

    /**
     * Builds one page of a bulk {@code DocumentWs} operation under the caller's
     * {@link DocumentListProfile}.
     *
     * <p>A request without the profile header is an existing client. It receives what the
     * published operation has always returned: file content inline, with a document whose stored
     * file cannot be read left out of the page (and reported in the server log). Refusing the whole
     * page instead would stop such a client at the same page on every retry, so that stricter
     * outcome is reserved for callers that ask for it with {@code legacy-inline}.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated caller, used for the document-link lookup
     * @param documents List&lt;Document&gt; the selected page, already authorized by the manager
     * @return DocumentTransfer[] the page in the selected profile
     * @throws org.apache.cxf.binding.soap.SoapFault when the explicit {@code legacy-inline}
     *         profile meets an unreadable stored file, or a document carries a CDS source precision
     */
    public static DocumentTransfer[] getTransfers(LoggedInInfo loggedInInfo, List<Document> documents) {
        DocumentListProfile profile = DocumentListProfile.current();
        // Refuse the whole selected page before allocating any file byte arrays.
        for (Document document:documents) requireLegacyProjection(document);
        DocumentManager documentManager = SpringUtils.getBean(DocumentManager.class);

        ArrayList<DocumentTransfer> results = new ArrayList<DocumentTransfer>();

        for (Document document : documents) {
            try {
                CtlDocument ctlDocument = documentManager.getCtlDocumentByDocumentId(loggedInInfo, document.getId());
                DocumentTransfer transfer = DocumentTransfer.toTransfer(document, ctlDocument, profile.inline());
                results.add(transfer);
            } catch (IOException e) {
                if (profile.faultsOnUnreadableContent()) {
                    throw new org.apache.cxf.binding.soap.SoapFault("Document content could not be read",
                            org.apache.cxf.binding.soap.SoapFault.FAULT_CODE_SERVER);
                }
                // Published header-less behaviour: the page is still answered, without this document.
                // The failure names a stored file, so only its type and location are logged; the
                // document number is what an operator needs to find and repair the record.
                logger.error("Document content could not be read; left out of the bulk response: documentNo={}{}",
                        LogSafe.sanitizeObject(document.getId()), LogSafe.exceptionTrace(e));
            }
        }

        return (results.toArray(new DocumentTransfer[0]));
    }

    private static void requireLegacyProjection(Document document) {
        if (document != null && document.getCdsContentPrecision()!=null) {
            throw new org.apache.cxf.binding.soap.SoapFault(
                    new io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException().getMessage(),
                    org.apache.cxf.binding.soap.SoapFault.FAULT_CODE_CLIENT);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[protected]";
    }
}
