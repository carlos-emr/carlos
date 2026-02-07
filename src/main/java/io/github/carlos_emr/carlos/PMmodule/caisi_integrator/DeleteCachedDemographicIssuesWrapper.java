/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr.carlos.PMmodule.caisi_integrator;

import io.github.carlos_emr.carlos.caisi_integrator.dao.FacilityIdDemographicIssueCompositePk;
import java.util.List;
import java.io.Serializable;

/**
 * Wrapper class for deletion operations on cached demographic issues in the integrator system.
 * <p>
 * This class encapsulates the data needed to identify which demographic issues should
 * be retained (and implicitly which should be deleted) during synchronization between
 * facilities in the integrator system. It contains the demographic identifier and a
 * list of issue keys that should NOT be deleted.
 * </p>
 * <p>
 * Used during integrator push operations to synchronize issue data across facilities,
 * ensuring that issues deleted at the source facility are also removed at remote facilities.
 * </p>
 *
 * @see FacilityIdDemographicIssueCompositePk
 */
public class DeleteCachedDemographicIssuesWrapper implements Serializable
{
    /** The demographic number identifying the patient whose issues are being managed */
    private Integer demographicNo;

    /** List of composite keys for issues that should be retained (not deleted) */
    private List<FacilityIdDemographicIssueCompositePk> keys;

    /**
     * Default constructor creating an empty wrapper.
     */
    public DeleteCachedDemographicIssuesWrapper() {
    }

    /**
     * Constructs a wrapper with the specified demographic and issue keys.
     *
     * @param demographicNo the demographic number of the patient
     * @param keys the list of issue keys that should be retained
     */
    public DeleteCachedDemographicIssuesWrapper(final Integer demographicNo, final List<FacilityIdDemographicIssueCompositePk> keys) {
        this.demographicNo = demographicNo;
        this.keys = keys;
    }

    /**
     * Gets the demographic number.
     *
     * @return the demographic number of the patient
     */
    public Integer getDemographicNo() {
        return this.demographicNo;
    }

    /**
     * Sets the demographic number.
     *
     * @param demographicNo the demographic number to set
     */
    public void setDemographicNo(final Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Gets the list of issue keys to be retained.
     *
     * @return the list of composite keys for issues that should not be deleted
     */
    public List<FacilityIdDemographicIssueCompositePk> getKeys() {
        return this.keys;
    }

    /**
     * Sets the list of issue keys to be retained.
     *
     * @param keys the list of composite keys for issues that should not be deleted
     */
    public void setKeys(final List<FacilityIdDemographicIssueCompositePk> keys) {
        this.keys = keys;
    }
}
