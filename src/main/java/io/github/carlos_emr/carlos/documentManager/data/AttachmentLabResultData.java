package io.github.carlos_emr.carlos.documentManager.data;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.DateUtils;
/**
 * Model representing data that links attachments directly to laboratory results.
 * Contains metadata about a document that supports or constitutes a specific lab test outcome.
 *
 * @since 2026-07-09
 */

public class AttachmentLabResultData {
    private String segmentID;
    private String labName;
    private Date labDate;
    private Map<String, String> labVersionIds = new LinkedHashMap<>();

    public AttachmentLabResultData() {
    }

    public AttachmentLabResultData(String segmentID, String labName, Date labDate) {
        // Link this attachment metadata to the corresponding lab result ID
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
