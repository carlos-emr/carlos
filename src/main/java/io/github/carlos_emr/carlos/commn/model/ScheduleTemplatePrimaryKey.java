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


package io.github.carlos_emr.carlos.commn.model;

import java.io.Serializable;

import jakarta.persistence.Column;

/**
 * Composite primary key for {@link ScheduleTemplate}, consisting of provider number and template name.
 *
 * <p>This embeddable key allows schedule templates to be scoped per provider.
 * Public templates use a special sentinel value
 * ({@link #DODGY_FAKE_PROVIDER_NO_USED_TO_HOLD_PUBLIC_TEMPLATES}) as the provider number,
 * allowing templates to be shared across all providers.</p>
 *
 * @since 2026-03-17
 */
public class ScheduleTemplatePrimaryKey implements Serializable {

    /**
     * Don't blame me, I wasn't the one to start doing this, I'm just making the constant for something already in use. Someday we should refactor it to null.
     */
    public static final String DODGY_FAKE_PROVIDER_NO_USED_TO_HOLD_PUBLIC_TEMPLATES = "Public";

    @Column(name = "provider_no")
    private String providerNo;
    private String name;

    /**
     * Default constructor required by JPA.
     */
    public ScheduleTemplatePrimaryKey() {
        //required by JPA
    }

    /**
     * Constructs a composite key with the specified provider number and template name.
     *
     * @param providerNo String the provider number, or "Public" for shared templates
     * @param name String the template name
     */
    public ScheduleTemplatePrimaryKey(String providerNo, String name) {
        this.providerNo = providerNo;
        this.name = name;
    }

    /**
     * Gets the provider number component of this key.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number component of this key.
     *
     * @param providerNo String the provider number to set
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Gets the template name component of this key.
     *
     * @return String the template name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the template name component of this key.
     *
     * @param name String the template name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return ("name=" + name + ", providerNo=" + providerNo);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (toString().hashCode());
    }

    /**
     * Checks equality based on both name and provider number.
     *
     * @param o Object the object to compare
     * @return boolean true if both name and providerNo match
     */
    @Override
    public boolean equals(Object o) {
        try {
            ScheduleTemplatePrimaryKey o1 = (ScheduleTemplatePrimaryKey) o;
            return ((name.equals(o1.name)) && (providerNo.equals(o1.providerNo)));
        } catch (RuntimeException e) {
            return (false);
        }
    }
}
