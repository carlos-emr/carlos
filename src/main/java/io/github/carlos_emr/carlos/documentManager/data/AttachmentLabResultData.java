package io.github.carlos_emr.carlos.documentManager.data;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.DateUtils;

public class AttachmentLabResultData {
    private String segmentID;
    private String labName;
    private Date labDate;
    /** Lab source ({@code HL7}, {@code MDS}, {@code CML}, {@code BCP}); segment ids are only unique within a source. */
    private String labType;
    private Map<String, String> labVersionIds = new LinkedHashMap<>();

    public AttachmentLabResultData() {
    }

    public AttachmentLabResultData(String segmentID, String labName, Date labDate) {
        this.segmentID = segmentID;
        this.labName = labName;
        this.labDate = labDate;
    }

    public AttachmentLabResultData(String segmentID, String labName, Date labDate, String labType) {
        this(segmentID, labName, labDate);
        this.labType = labType;
    }

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
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
