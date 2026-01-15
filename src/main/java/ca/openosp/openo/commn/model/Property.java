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


package ca.openosp.openo.commn.model;

import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "property")
public class Property extends AbstractModel<Integer> implements Serializable {

    public enum PROPERTY_KEY {
        invoice_payee_display_clinic, invoice_payee_info, default_billing_provider, default_billing_form,
        bc_default_service_location, auto_populate_refer, auto_link_to_mrp, hl7_offer_file_for_others, hl7_allow_others_file_for_you
    }

    public enum PROPERTY_VALUE {clinicdefault}

    public Property() {
    }

    public Property(String name) {
        setName(name);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String value;

    @Column(name = "provider_no")
    private String providerNo;

    @Override
    public Integer getId() {
        return id;
    }

    public String getName() {
        return (name);
    }

    public void setName(String name) {
        this.name = StringUtils.trimToNull(name);
    }

    public String getValue() {
        return (value);
    }

    public void setValue(String value) {
        this.value = StringUtils.trimToNull(value);
    }

    public String getProviderNo() {
        return (providerNo);
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = StringUtils.trimToNull(providerNo);
    }

}
