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

package io.github.carlos_emr.carlos.billings.ca.bc.data;

import static javax.persistence.GenerationType.IDENTITY;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

/**
 * Represents a user preferences in the Billing Module
 */
@Entity
@Table(name = "billing_preferences")
public class BillingPreference extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    /**
     * indicates the default display value in the Billing Screen
     * May be one of thre values refer to = 1, refer from = 2 or neither = 3
     */
    @Column(name = "referral", nullable = false)
    private int referral = 1;

    @Column(name = "providerNo", nullable = false)
    private String providerNo;

    @Column(name = "defaultPayeeNo", nullable = false)
    private String defaultPayeeNo = "";

    public BillingPreference() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setReferral(int referral) {
        this.referral = referral;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public void setDefaultPayeeNo(String defaultPayeeNo) {
        this.defaultPayeeNo = defaultPayeeNo;
    }

    public int getReferral() {
        return referral;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public String getDefaultPayeeNo() {
        return defaultPayeeNo;
    }
}
