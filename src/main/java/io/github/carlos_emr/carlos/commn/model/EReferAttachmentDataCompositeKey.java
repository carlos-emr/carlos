//CHECKSTYLE:OFF
package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.Column;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.io.Serializable;
import java.util.Objects;

public class EReferAttachmentDataCompositeKey implements Serializable {
    @ManyToOne
    @JoinColumn(name = "erefer_attachment_id", referencedColumnName = "id")
    private EReferAttachment eReferAttachment;

    @Column(name = "lab_id")
    private Integer labId;

    @Column(name = "lab_type")
    private String labType;

    public EReferAttachmentDataCompositeKey() {
    }

    public EReferAttachmentDataCompositeKey(EReferAttachment eReferAttachment, Integer labId, String labType) {
        this.eReferAttachment = eReferAttachment;
        this.labId = labId;
        this.labType = labType;
    }

    public EReferAttachment getEReferAttachment() {
        return eReferAttachment;
    }

    public void setEReferAttachment(EReferAttachment eReferAttachment) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EReferAttachmentDataCompositeKey that = (EReferAttachmentDataCompositeKey) o;
        return eReferAttachment.equals(that.eReferAttachment) &&
                labId.equals(that.labId) &&
                labType.equals(that.labType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eReferAttachment, labId, labType);
    }
}