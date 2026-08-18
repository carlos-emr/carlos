/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.casemgmt.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;

/**
 * Lightweight DTO for case management issue list views. Eliminates the
 * EAGER-loaded Issue entity relationship (lazy=false in HBM). Pre-joins
 * issue code and description via LEFT JOIN.
 *
 * <p>HBM property names (camelCase with underscores): {@code id},
 * {@code demographic_no}, {@code issue_id}, {@code type}, {@code acute},
 * {@code certain}, {@code major}, {@code resolved}, {@code update_date},
 * {@code program_id}.</p>
 *
 * @since 2026-04-11
 */
public class CaseManagementIssueListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer demographicNo;
    private Long issueId;
    private String type;
    private boolean acute;
    private boolean certain;
    private boolean major;
    private boolean resolved;
    private Date updateDate;
    private Integer programId;
    // Pre-joined from Issue entity
    private String issueCode;
    private String issueDescription;

    /** Default constructor for serialization/framework binding. */
    public CaseManagementIssueListDTO() {
    }

    /**
     * Projection constructor for HQL constructor expressions.
     * Parameter order must match the SELECT NEW clause exactly.
     *
     * @param id Long issue row identifier
     * @param demographicNo Integer demographic surrogate key
     * @param issueId Long issue identifier
     * @param type String issue type
     * @param acute boolean acute flag
     * @param certain boolean certainty flag
     * @param major boolean major flag
     * @param resolved boolean resolved flag
     * @param updateDate Date update timestamp
     * @param programId Integer program identifier
     * @param issueCode String joined issue code (from Issue entity)
     * @param issueDescription String joined issue description (from Issue entity)
     * @since 2026-04-11
     */
    public CaseManagementIssueListDTO(Long id, Integer demographicNo, Long issueId, String type,
                                      boolean acute, boolean certain, boolean major, boolean resolved,
                                      Date updateDate, Integer programId,
                                      String issueCode, String issueDescription) {
        this.id = id;
        this.demographicNo = demographicNo;
        this.issueId = issueId;
        this.type = type;
        this.acute = acute;
        this.certain = certain;
        this.major = major;
        this.resolved = resolved;
        this.updateDate = updateDate;
        this.programId = programId;
        this.issueCode = issueCode;
        this.issueDescription = issueDescription;
    }

    /**
     * Creates a DTO from a full {@link CaseManagementIssue} entity. Pulls
     * {@code issue.code}/{@code issue.description} via the already-loaded
     * {@code Issue} relationship when present; otherwise leaves them null.
     *
     * @param cmi CaseManagementIssue the entity to convert; must not be null
     * @return CaseManagementIssueListDTO a lightweight projection
     * @since 2026-04-12
     */
    public static CaseManagementIssueListDTO fromEntity(CaseManagementIssue cmi) {
        Objects.requireNonNull(cmi, "CaseManagementIssue entity must not be null for DTO conversion");
        Issue issue = cmi.getIssue();
        return new CaseManagementIssueListDTO(
                cmi.getId(), cmi.getDemographic_no(), cmi.getIssue_id(), cmi.getType(),
                cmi.isAcute(), cmi.isCertain(), cmi.isMajor(), cmi.isResolved(),
                cmi.getUpdate_date(), cmi.getProgram_id(),
                issue == null ? null : issue.getCode(),
                issue == null ? null : issue.getDescription()
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isAcute() { return acute; }
    public void setAcute(boolean acute) { this.acute = acute; }
    public boolean isCertain() { return certain; }
    public void setCertain(boolean certain) { this.certain = certain; }
    public boolean isMajor() { return major; }
    public void setMajor(boolean major) { this.major = major; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Date getUpdateDate() { return updateDate; }
    public void setUpdateDate(Date updateDate) { this.updateDate = updateDate; }
    public Integer getProgramId() { return programId; }
    public void setProgramId(Integer programId) { this.programId = programId; }
    public String getIssueCode() { return issueCode; }
    public void setIssueCode(String issueCode) { this.issueCode = issueCode; }
    public String getIssueDescription() { return issueDescription; }
    public void setIssueDescription(String issueDescription) { this.issueDescription = issueDescription; }
}
