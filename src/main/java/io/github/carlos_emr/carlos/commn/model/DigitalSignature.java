/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.model;

import io.github.carlos_emr.carlos.commn.model.converter.DigitalSignatureModuleTypeConverter;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;

import java.util.Date;

import jakarta.persistence.*;

@Entity
public class DigitalSignature extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id = null;

    /**
     * The facility in which this was captured
     */
    private Integer facilityId = null;

    /**
     * The provider who captured the signature
     */
    private String providerNo = null;

    /**
     * The client for which this signature belongs
     */
    private Integer demographicId = null;

    /**
     * The date the signature was captured
     */
    private Date dateSigned = null;

    /**
     * Encrypted image data of the signature (format varies: PNG for stamp signatures, other formats for drawn signatures)
     */
    private byte[] signatureImage = null;

	/** The module type of the signature */
	@Convert(converter = DigitalSignatureModuleTypeConverter.class)
	private ModuleType moduleType = null;

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(Integer facilityId) {
        this.facilityId = facilityId;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getDemographicId() {
        return demographicId;
    }

    public void setDemographicId(Integer demographicId) {
        this.demographicId = demographicId;
    }

    public Date getDateSigned() {
        return dateSigned;
    }

    public void setDateSigned(Date dateSigned) {
        this.dateSigned = dateSigned;
    }

    public byte[] getSignatureImage() {
        return signatureImage;
    }

    public void setSignatureImage(byte[] signatureImage) {
        this.signatureImage = signatureImage;
    }

	public ModuleType getModuleType() {
		return moduleType;
	}

	public void setModuleType(ModuleType moduleType) {
		this.moduleType = moduleType;
	}
}
