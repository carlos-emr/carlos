package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "erefer_attachment")
public class EReferAttachment extends AbstractModel<Integer> {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

    @Column(name = "demographic_no")
    private Integer demographicNo;

    @Column(name = "created")
    private Date created;

    @Column(name = "archived")
    private boolean archived = false;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "erefer_attachment_id", referencedColumnName = "id")
    private List<EReferAttachmentData> attachments;

    public EReferAttachment() {
    }

    public EReferAttachment(Integer demographicNo) {
        this.demographicNo = demographicNo;
        this.created = new Date();
    }

    public Integer getId() {
        return id;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public List<EReferAttachmentData> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<EReferAttachmentData> attachments) {
        this.attachments = attachments;
    }
}