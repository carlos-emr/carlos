/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.documentManager.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Document;

/**
 * Lightweight data transfer object for document inbox and list views,
 * optimized for JPQL constructor expression projection. Eliminates the
 * EAGER-loaded DocumentReview collection and the LOB docxml field that
 * are loaded on every Document entity fetch.
 *
 * <p>Omits: {@code docxml} (LOB), {@code base64Binary} (transient),
 * {@code reviews} (EAGER collection), {@code reportMedia}, {@code sentDateTime},
 * {@code restrictToProgram}, {@code receivedDate}, {@code sourceFacility}.</p>
 *
 * @since 2026-04-11
 */
public class DocumentListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer documentNo;
    private String doctype;
    private String docClass;
    private String docSubClass;
    private String docdesc;
    private String docfilename;
    private String doccreator;
    private String responsible;
    private String source;
    private char status;
    private String contenttype;
    private Date contentdatetime;
    private Date observationdate;
    private String reviewer;
    private Date reviewdatetime;
    private Integer numberofpages;
    private Integer appointmentNo;
    private boolean abnormal;

    public DocumentListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param documentNo Integer the document number (primary key)
     * @param doctype String the document type
     * @param docClass String the document class
     * @param docSubClass String the document sub-class
     * @param docdesc String the document description
     * @param docfilename String the document filename
     * @param doccreator String the creator provider number
     * @param responsible String the responsible provider number
     * @param source String the document source
     * @param status char the document status ('A' active, 'D' deleted)
     * @param contenttype String the MIME content type
     * @param contentdatetime Date the content date/time
     * @param observationdate Date the observation date
     * @param reviewer String the reviewer provider number
     * @param reviewdatetime Date the review date/time
     * @param numberofpages Integer the page count
     * @param appointmentNo Integer the linked appointment number
     * @param abnormal boolean whether the document is flagged abnormal
     */
    public DocumentListItemDTO(Integer documentNo, String doctype, String docClass, String docSubClass,
                               String docdesc, String docfilename, String doccreator, String responsible,
                               String source, char status, String contenttype, Date contentdatetime,
                               Date observationdate, String reviewer, Date reviewdatetime,
                               Integer numberofpages, Integer appointmentNo, boolean abnormal) {
        this.documentNo = documentNo;
        this.doctype = doctype;
        this.docClass = docClass;
        this.docSubClass = docSubClass;
        this.docdesc = docdesc;
        this.docfilename = docfilename;
        this.doccreator = doccreator;
        this.responsible = responsible;
        this.source = source;
        this.status = status;
        this.contenttype = contenttype;
        this.contentdatetime = contentdatetime;
        this.observationdate = observationdate;
        this.reviewer = reviewer;
        this.reviewdatetime = reviewdatetime;
        this.numberofpages = numberofpages;
        this.appointmentNo = appointmentNo;
        this.abnormal = abnormal;
    }

    /**
     * Creates a DocumentListItemDTO from a full Document entity.
     *
     * @param d Document the entity to convert; must not be null
     * @return DocumentListItemDTO a lightweight projection
     */
    public static DocumentListItemDTO fromEntity(Document d) {
        Objects.requireNonNull(d, "Document entity must not be null for DTO conversion");
        return new DocumentListItemDTO(
                d.getDocumentNo(), d.getDoctype(), d.getDocClass(), d.getDocSubClass(),
                d.getDocdesc(), d.getDocfilename(), d.getDoccreator(), d.getResponsible(),
                d.getSource(), d.getStatus(), d.getContenttype(), d.getContentdatetime(),
                d.getObservationdate(), d.getReviewer(), d.getReviewdatetime(),
                d.getNumberofpages(), d.getAppointmentNo(), d.isAbnormal()
        );
    }

    /**
     * Returns whether this document has been reviewed.
     *
     * @return boolean true if a reviewer is assigned
     */
    public boolean isReviewed() {
        return reviewer != null && !reviewer.trim().isEmpty();
    }

    public Integer getDocumentNo() { return documentNo; }
    public void setDocumentNo(Integer documentNo) { this.documentNo = documentNo; }
    public String getDoctype() { return doctype; }
    public void setDoctype(String doctype) { this.doctype = doctype; }
    public String getDocClass() { return docClass; }
    public void setDocClass(String docClass) { this.docClass = docClass; }
    public String getDocSubClass() { return docSubClass; }
    public void setDocSubClass(String docSubClass) { this.docSubClass = docSubClass; }
    public String getDocdesc() { return docdesc; }
    public void setDocdesc(String docdesc) { this.docdesc = docdesc; }
    public String getDocfilename() { return docfilename; }
    public void setDocfilename(String docfilename) { this.docfilename = docfilename; }
    public String getDoccreator() { return doccreator; }
    public void setDoccreator(String doccreator) { this.doccreator = doccreator; }
    public String getResponsible() { return responsible; }
    public void setResponsible(String responsible) { this.responsible = responsible; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public char getStatus() { return status; }
    public void setStatus(char status) { this.status = status; }
    public String getContenttype() { return contenttype; }
    public void setContenttype(String contenttype) { this.contenttype = contenttype; }
    public Date getContentdatetime() { return contentdatetime; }
    public void setContentdatetime(Date contentdatetime) { this.contentdatetime = contentdatetime; }
    public Date getObservationdate() { return observationdate; }
    public void setObservationdate(Date observationdate) { this.observationdate = observationdate; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public Date getReviewdatetime() { return reviewdatetime; }
    public void setReviewdatetime(Date reviewdatetime) { this.reviewdatetime = reviewdatetime; }
    public Integer getNumberofpages() { return numberofpages; }
    public void setNumberofpages(Integer numberofpages) { this.numberofpages = numberofpages; }
    public Integer getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(Integer appointmentNo) { this.appointmentNo = appointmentNo; }
    public boolean isAbnormal() { return abnormal; }
    public void setAbnormal(boolean abnormal) { this.abnormal = abnormal; }
}
