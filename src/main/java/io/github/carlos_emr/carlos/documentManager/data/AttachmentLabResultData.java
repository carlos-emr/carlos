package io.github.carlos_emr.carlos.documentManager.data;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.DateUtils;

/**
 * Data model or DTO representing the associated attachment information specifically
 * linked to a laboratory result entry.
 */
public class AttachmentLabResultData {
    private String segmentID;
    private String labName;
    private Date labDate;
    private Map<String, String> labVersionIds = new LinkedHashMap<>();

    public AttachmentLabResultData() {
    }

    public AttachmentLabResultData(String segmentID, String labName, Date labDate) {
        this.segmentID = segmentID;
        this.labName = labName;
        this.labDate = labDate;
    }

    public String getSegmentID() {
        // Process standard operational requirements ensuring context-specific compliance

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
