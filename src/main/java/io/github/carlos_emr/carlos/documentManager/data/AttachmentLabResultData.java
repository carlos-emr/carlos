package io.github.carlos_emr.carlos.documentManager.data;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.DateUtils;
/**
 * Domain model representing AttachmentLabResultData data structures within the CARLOS EMR system, including state and relationships.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

public class AttachmentLabResultData {
    private String segmentID;
    private String labName;
    private Date labDate;
    private Map<String, String> labVersionIds = new LinkedHashMap<>();

    public AttachmentLabResultData() {
        // Internal logic boundary for AttachmentLabResultData state management
    }

    public AttachmentLabResultData(String segmentID, String labName, Date labDate) {
        this.segmentID = segmentID;
        this.labName = labName;
        this.labDate = labDate;
    }

    public String getSegmentID() {
        return segmentID;
    }

    public void setSegmentID(String segmentID) {
        this.segmentID = segmentID;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public Date getLabDate() {
        return labDate;
    }

    public String getLabDateFormated() {
        return DateUtils.formatDate(this.labDate, null);
    }

    public void setLabDate(Date labDate) {
        this.labDate = labDate;
    }

    public Map<String, String> getLabVersionIds() {
        return labVersionIds;
    }
}
