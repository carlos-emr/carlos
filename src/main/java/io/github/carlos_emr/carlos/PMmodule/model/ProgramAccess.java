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

 package io.github.carlos_emr.carlos.PMmodule.model;

 import jakarta.persistence.Entity;
 import jakarta.persistence.Table;
 
 import java.util.Set;
 import io.github.carlos_emr.carlos.model.security.Secrole;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
 
 /**
  * This is the object class that relates to the program_access table. Any customizations belong here.
  */
 @Entity
 @Table(name = "program_access")
 @jakarta.persistence.Access(jakarta.persistence.AccessType.PROPERTY)
public class ProgramAccess extends AbstractModel<Long> {

     private int hashCode = Integer.MIN_VALUE; // primary key

     private Long _id;
     private Long _programId;
     private String _accessTypeId;
     private boolean _allRoles;
     private AccessType _accessType;
     private Set<Secrole> _roles;
 
     // constructors
     public ProgramAccess() {
 
     }
 
     /**
      * Constructor for primary key.
      * @param _id the ID
      */
     public ProgramAccess(Long _id) {
         this.setId(_id);
     }  
 
 
     @Override
     @jakarta.persistence.Id
     @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
     @jakarta.persistence.Column(name = "id")
     public Long getId() {
         return _id;
     }

     /**
      * Sets the ID.
      * @param _id the new ID
      */
     public void setId(Long _id) {
         this._id = _id;
         this.hashCode = Integer.MIN_VALUE;
     }
 
     /**
      * Gets the program ID.
      * @return the program ID
      */
     @jakarta.persistence.Column(name = "program_id")
     public Long getProgramId() {
         return _programId;
     }

     /**
      * Sets the program ID.
      * @param _programId the new program ID
      */
     public void setProgramId(Long _programId) {
         this._programId = _programId;
     }
 
     /**
      * Gets the access type ID.
      * @return the access type ID
      */
     @jakarta.persistence.Column(name = "access_type_id")
     public String getAccessTypeId() {
         return _accessTypeId;
     }

     /**
      * Sets the access type ID.
      * @param _accessTypeId the new access type ID
      */
     public void setAccessTypeId(String _accessTypeId) {
         this._accessTypeId = _accessTypeId;
     }
 
     /**
      * Checks if all roles have this access.
      * @return true, if all roles have this access
      */
     @jakarta.persistence.Column(name = "all_roles")
     public boolean isAllRoles() {
         return _allRoles;
     }

     /**
      * Sets whether all roles have this access.
      * @param _allRoles the new all roles flag
      */
     public void setAllRoles(boolean _allRoles) {
         this._allRoles = _allRoles;
     }
 
     /**
      * Gets the access type.
      * @return the access type
      */
     @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.EAGER)
     @jakarta.persistence.JoinColumn(name = "access_type_id", insertable = false, updatable = false)
     public AccessType getAccessType() {
         return this._accessType;
     }

     /**
      * Sets the access type.
      * @param _accessType the new access type
      */
     public void setAccessType(AccessType _accessType) {
         this._accessType = _accessType;
     }
 
     /**
      * Gets the roles that have this access.
      * @return the roles
      */
     @jakarta.persistence.ManyToMany(fetch = jakarta.persistence.FetchType.EAGER)
     @jakarta.persistence.JoinTable(name = "program_access_roles", joinColumns = @jakarta.persistence.JoinColumn(name = "id"), inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "role_id"))
     public java.util.Set<Secrole> getRoles() {
         return this._roles;
     }

     /**
      * Sets the roles that have this access.
      * @param _roles the new roles
      */
     public void setRoles(java.util.Set<Secrole> _roles) {
         this._roles = _roles;
     }
 
 
     @Override
     public boolean equals(Object obj) {
        if (null == obj) return false;
        if (!(obj instanceof ProgramAccess)) return false;
        else {
            ProgramAccess mObj = (ProgramAccess) obj;
            if (null == this.getId() || null == mObj.getId()) return false;
            else return (this.getId().equals(mObj.getId()));
        }
    }

    @Override
    public int hashCode() {
        if (Integer.MIN_VALUE == this.hashCode) {
            if (null == this.getId()) return super.hashCode();
            else {
                String hashStr = this.getClass().getName() + ":" + this.getId().hashCode();
                this.hashCode = hashStr.hashCode();
            }
        }
        return this.hashCode;
    }

 
    public String toString() {
        return super.toString();
    }

}
