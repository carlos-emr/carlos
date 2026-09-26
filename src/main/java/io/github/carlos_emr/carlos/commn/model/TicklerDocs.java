/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * Ported from the openo-beta/Open-O tickler attachment component
 * (PR #2491, Sebastian Ibanez) and adapted for CARLOS.
 */
package io.github.carlos_emr.carlos.commn.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Links a document, lab, eForm, encounter form or HRM report to a tickler.
 *
 * <p>Tickler counterpart of {@link ConsultDocs} and {@link EFormDocs}; it backs the shared
 * attachment picker ({@code documentManager/attachDocument.jsp}) and supersedes the
 * single-link {@link TicklerLink} store, which is kept read-only for one release.
 * Detached attachments are soft-deleted by setting {@link #deleted} to {@link #DELETED_FLAG}.</p>
 *
 * <p>Unlike the consult/eForm stores, a lab attachment also records its originating lab
 * source ({@link #labType}: HL7, MDS, CML or BCP). Lab identifiers are only unique within
 * one source, so the type is needed both to open the right viewer and to answer
 * "which ticklers reference this lab" without a second routing lookup.</p>
 *
 * @since 2026-09-26
 */
@Entity
@Table(name = "ticklerdocs")
public class TicklerDocs extends AbstractModel<Integer> {
    public static final String DOCTYPE_DOC = "D";
    public static final String DOCTYPE_EFORM = "E";
    public static final String DOCTYPE_LAB = "L";
    public static final String DOCTYPE_FORM = "F";
    public static final String DOCTYPE_HRM = "H";
    public static final String DELETED_FLAG = "Y";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tickler_id")
    private int ticklerId;

    @Column(name = "document_no")
    private int documentNo;

    @Column(name = "doctype")
    private String docType;

    /** Lab source code for {@link #DOCTYPE_LAB} rows; {@code null} for every other type. */
    @Column(name = "lab_type")
    private String labType;

    private String deleted;

    @Column(name = "attach_date")
    @Temporal(TemporalType.DATE)
    private Date attachDate;

    @Column(name = "provider_no")
    private String providerNo;

    /** Repository audit pair: who last wrote the row (attach, detach, revive) and when. */
    private String lastUpdateUser;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdateDate;

    public TicklerDocs() {
    }

    public TicklerDocs(int ticklerId, int documentNo, String docType, String providerNo) {
        setTicklerId(ticklerId);
        setDocumentNo(documentNo);
        setDocType(docType);
        setProviderNo(providerNo);
        setAttachDate(new Date());
        stampUpdate(providerNo);
    }

    /**
     * Records the writer and time of the current change; every write of the row goes through
     * here so the audit pair is never left at its insert value after a detach or a revival.
     *
     * @param providerNo String the session provider making the change
     */
    public void stampUpdate(String providerNo) {
        setLastUpdateUser(providerNo == null || providerNo.isEmpty() ? "system" : providerNo);
        setLastUpdateDate(new Date());
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getTicklerId() {
        return ticklerId;
    }

    public void setTicklerId(int ticklerId) {
        this.ticklerId = ticklerId;
    }

    public int getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(int documentNo) {
        this.documentNo = documentNo;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
    }

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted(String deleted) {
        this.deleted = deleted;
    }

    public Date getAttachDate() {
        return attachDate;
    }

    public void setAttachDate(Date attachDate) {
        this.attachDate = attachDate;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
}
