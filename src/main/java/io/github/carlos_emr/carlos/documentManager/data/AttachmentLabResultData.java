package io.github.carlos_emr.carlos.documentManager.data;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.DateUtils;

/**
 * Data transfer object representing a laboratory result with version tracking for the
 * document attachment workflow in the CARLOS EMR system.
 *
 * <p>Each instance represents the latest version of a lab result and contains a map of
 * its historical version IDs with their corresponding dates. This structure supports the
 * attachment window (attachDocument.jsp) where lab results are displayed grouped by version
 * to prevent duplicate selection and provide clear version history.
 *
 * @see DocumentAttachmentManager#getAllLabsSortedByVersions
 * @since 2026-01-24
 */
public class AttachmentLabResultData {
    private String segmentID;
    private String labName;
    private Date labDate;
    private Map<String, String> labVersionIds = new LinkedHashMap<>();

    /**
     * Default constructor.
     */
    public AttachmentLabResultData() {
    }

    /**
     * Constructs an AttachmentLabResultData with the specified lab identifiers.
     *
     * @param segmentID String the unique segment identifier of the lab result
     * @param labName String the display name of the lab result (truncated label or discipline)
     * @param labDate Date the date of the lab result
     */
    public AttachmentLabResultData(String segmentID, String labName, Date labDate) {
        this.segmentID = segmentID;
        this.labName = labName;
        this.labDate = labDate;
    }

    /**
     * Returns the unique segment identifier of the lab result.
     *
     * @return String the segment ID
     */
    public String getSegmentID() {
        return segmentID;
    }

    public void setSegmentID(String segmentID) {
        this.segmentID = segmentID;
    }

    /**
     * Returns the display name of the lab result (truncated label or discipline, max 40 chars).
     *
     * @return String the lab display name
     */
    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    /**
     * Returns the date of the lab result.
     *
     * @return Date the lab result date
     */
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
