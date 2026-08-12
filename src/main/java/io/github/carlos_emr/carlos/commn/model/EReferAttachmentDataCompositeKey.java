package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.io.Serializable;
import java.util.Objects;
/**
 * Domain model representing EReferAttachmentDataCompositeKey data structures within the CARLOS EMR system, including state and relationships.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

public class EReferAttachmentDataCompositeKey implements Serializable {
    @ManyToOne
    @JoinColumn(name = "erefer_attachment_id", referencedColumnName = "id")
    private EReferAttachment eReferAttachment;

    @Column(name = "lab_id")
    private Integer labId;

    @Column(name = "lab_type")
    private String labType;

    public EReferAttachmentDataCompositeKey() {
        // Internal logic boundary for EReferAttachmentDataCompositeKey state management
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