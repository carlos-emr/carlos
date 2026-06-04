package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity representing the binary content payload of an eReferral attachment.
 * Typically stores the raw byte array of PDFs, images, or clinical text files sent along with a referral request.
 */

@Entity
@IdClass(EReferAttachmentDataCompositeKey.class)
@Table(name = "erefer_attachment_data")
public class EReferAttachmentData extends AbstractModel<EReferAttachmentDataCompositeKey> {
    @Id
    @ManyToOne
    @JoinColumn(name = "erefer_attachment_id", referencedColumnName = "id")
    private EReferAttachment eReferAttachment;

    @Id
    @Column(name = "lab_id")
    private Integer labId;

    @Id
    @Column(name = "lab_type")
    private String labType;

    public EReferAttachmentData() {
    }

    public EReferAttachmentData(EReferAttachment eReferAttachment, Integer labId, String labType) {
        this.eReferAttachment = eReferAttachment;
        this.labId = labId;
        this.labType = labType;
    }

    public EReferAttachmentDataCompositeKey getId() {
        return new EReferAttachmentDataCompositeKey(eReferAttachment, labId, labType);
    }

    public EReferAttachment geteReferAttachment() {
        return eReferAttachment;
    }

    public void seteReferAttachment(EReferAttachment eReferAttachment) {
        this.eReferAttachment = eReferAttachment;
    }

    public Integer getLabId() {
        return labId;
    }

    public void setLabId(Integer labId) {
        this.labId = labId;
    }

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
    }
}