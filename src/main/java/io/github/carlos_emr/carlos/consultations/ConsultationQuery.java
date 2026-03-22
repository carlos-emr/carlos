/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.consultations;

import io.github.carlos_emr.carlos.commn.PaginationQuery;

/**
 * Query parameter object for searching and filtering consultation requests.
 *
 * <p>Extends {@link PaginationQuery} to add consultation-specific filter criteria
 * such as demographic ID, provider number, team, status, and completion state.
 * Used by the consultation DAO layer to construct paginated consultation queries.</p>
 *
 * @since 2026-03-17
 */
public class ConsultationQuery extends PaginationQuery {
    private static final long serialVersionUID = 5994830654027801723L;
    private Integer demographicId;
    private String dateType;
    private String complete;
    private String providerNo;
    private String withOption;
    private String team;
    private String status;

    /**
     * Returns the consultation status filter value.
     *
     * @return String the consultation status, or {@code null} if not set
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the consultation status filter value.
     *
     * @param status String the consultation status to filter by
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the team filter value.
     *
     * @return String the team name, or {@code null} if not set
     */
    public String getTeam() {
        return team;
    }

    /**
     * Sets the team filter value.
     *
     * @param team String the team name to filter by
     */
    public void setTeam(String team) {
        this.team = team;
    }

    /**
     * Returns the additional filtering option.
     *
     * @return String the additional option value, or {@code null} if not set
     */
    public String getWithOption() {
        return withOption;
    }

    /**
     * Sets the additional filtering option.
     *
     * @param withOption String the additional option value to apply
     */
    public void setWithOption(String withOption) {
        this.withOption = withOption;
    }

    /**
     * Returns the patient demographic ID filter.
     *
     * @return Integer the demographic ID, or {@code null} if not set
     */
    public Integer getDemographicId() {
        return demographicId;
    }

    /**
     * Sets the patient demographic ID filter.
     *
     * @param demographicId Integer the patient demographic ID to filter by
     */
    public void setDemographicId(Integer demographicId) {
        this.demographicId = demographicId;
    }

    /**
     * Returns the date type used for date-range filtering (e.g., referral date, appointment date).
     *
     * @return String the date type identifier, or {@code null} if not set
     */
    public String getDateType() {
        return dateType;
    }

    /**
     * Sets the date type used for date-range filtering.
     *
     * @param dateType String the date type identifier
     */
    public void setDateType(String dateType) {
        this.dateType = dateType;
    }

    /**
     * Returns the provider number filter.
     *
     * @return String the provider number, or {@code null} if not set
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number filter.
     *
     * @param providerNo String the provider number to filter by
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Returns the completion status filter.
     *
     * @return String the completion status, or {@code null} if not set
     */
    public String getComplete() {
        return complete;
    }

    /**
     * Sets the completion status filter.
     *
     * @param complete String the completion status to filter by
     */
    public void setComplete(String complete) {
        this.complete = complete;
    }

}