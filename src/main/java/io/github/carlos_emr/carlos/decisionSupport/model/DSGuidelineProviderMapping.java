//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.decisionSupport.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

/**
 * Entity representing the association between healthcare providers and clinical decision support guidelines.
 * <p>
 * DSGuidelineProviderMapping maintains the many-to-many relationship between providers and guidelines,
 * allowing the system to determine which clinical decision support rules should be active for
 * specific healthcare providers. This enables personalized clinical decision support based on
 * provider preferences, specialty, or organizational policies.
 * </p>
 * <p>
 * Each mapping associates a provider (identified by provider number) with a specific guideline
 * (identified by UUID), enabling fine-grained control over which decision support rules are
 * presented to different healthcare providers during patient encounters.
 * </p>
 *
 * @author apavel
 * @since 2009-07-06
 * @see DSGuideline for guideline definitions
 * @see io.github.carlos_emr.carlos.commn.model.Provider for provider information
 */
@Entity
@Table(name = "dsGuidelineProviderMap")
public class DSGuidelineProviderMapping extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapid")
    private Integer id;

    @Column(name = "provider_no", nullable = false)
    private String providerNo;

    @Column(name = "guideline_uuid", length = 60, nullable = false)
    private String guidelineUUID;

    /**
     * Default constructor for DSGuidelineProviderMapping.
     */
    public DSGuidelineProviderMapping() {
    }

    /**
     * Constructs a new provider-guideline mapping with specified identifiers.
     *
     * @param guidelineUUID String UUID identifying the clinical guideline
     * @param providerNo String provider number identifying the healthcare provider
     */
    public DSGuidelineProviderMapping(String guidelineUUID, String providerNo) {
        this.guidelineUUID = guidelineUUID;
        this.providerNo = providerNo;
    }

    /**
     * Determines equality based on provider number and guideline UUID.
     * Two mappings are considered equal if they associate the same provider
     * with the same guideline.
     *
     * @param object2 Object to compare against this mapping
     * @return boolean true if mappings have the same provider and guideline identifiers
     */
    @Override
    public boolean equals(Object object2) {
        DSGuidelineProviderMapping mapping2 = (DSGuidelineProviderMapping) object2;
        if (mapping2.getProviderNo().equals(this.getProviderNo()) && mapping2.getGuidelineUUID().equals(this.getGuidelineUUID())) {
            return true;
        }
        return false;
    }

    /**
     * Gets the unique identifier for this mapping record.
     *
     * @return Integer database ID for this provider-guideline mapping
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this mapping record.
     *
     * @param id Integer database ID for this mapping
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the provider number identifying the healthcare provider.
     *
     * @return String provider number from the provider system
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number identifying the healthcare provider.
     *
     * @param providerNo String provider number from the provider system
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Gets the UUID identifying the clinical decision support guideline.
     *
     * @return String UUID of the associated guideline
     */
    public String getGuidelineUUID() {
        return guidelineUUID;
    }

    /**
     * Sets the UUID identifying the clinical decision support guideline.
     *
     * @param guidelineUUID String UUID of the guideline to associate
     */
    public void setGuidelineUUID(String guidelineUUID) {
        this.guidelineUUID = guidelineUUID;
    }

}
