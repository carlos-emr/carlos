//CHECKSTYLE:OFF
package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

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