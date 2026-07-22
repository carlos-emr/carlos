/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.model.security;

import java.util.Calendar;


@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "secRole")
@jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class Secrole implements java.io.Serializable {

    // Fields

    private Long id;
    private String roleName;
    private String description;
    private boolean active;
    private String lastUpdateUser;
    private Calendar lastUpdateDate;

    private int orderByIndex;

    // Constructors

    public void setId(Long id) {
        this.id = id;
    }
    @jakarta.persistence.Id

    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)

    @jakarta.persistence.Column(name = "role_no")

    public Long getId() {
        return id;
    }
    @jakarta.persistence.Transient

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
    @jakarta.persistence.Transient

    public Calendar getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Calendar lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
    @jakarta.persistence.Transient

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public void setLastUpdateUser(String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }
    @jakarta.persistence.Transient

    public int getOrderByIndex() {
        return orderByIndex;
    }

    public void setOrderByIndex(int orderByIndex) {
        this.orderByIndex = orderByIndex;
    }

    /**
     * default constructor
     */
    public Secrole() {
    }

    /**
     * full constructor
     */
    public Secrole(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    // Property accessors

    @jakarta.persistence.Column(name = "role_name")


    public String getRoleName() {
        return this.roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
    @jakarta.persistence.Column(name = "description", length = 60)

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    @jakarta.persistence.Transient

    public String getName() {
        return roleName;
    }

    public void setName(String name) {
        this.roleName = name;
    }

    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof Secrole)) return false;

        Secrole r = (Secrole) obj;
        if (r.getId() == null || this.getId() == null) return false;
        if (this.getId().longValue() == r.getId().longValue()) return true;

        return false;
    }

    public int hashCode() {
        return getId() == null ? 0 : Long.hashCode(getId());
    }
}
