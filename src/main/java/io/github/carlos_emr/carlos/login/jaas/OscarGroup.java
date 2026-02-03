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
package io.github.carlos_emr.carlos.login.jaas;

import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class OscarGroup implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private List<Principal> principals = new ArrayList<Principal>();

    public OscarGroup() {
    }

    public OscarGroup(String roleName) {
        setName(roleName);
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Adds a member to the group.
     *
     * @param user the Principal to be added
     * @return true whether the member was added
     */
    public boolean addMember(Principal user) {
        if (!principals.contains(user))
            principals.add(user);
        return true;
    }

    /**
     * Removes a member from the group.
     *
     * @param user the Principal to be removed
     * @return true whether the member was removed
     */
    public boolean removeMember(Principal user) {
        principals.remove(user);
        return true;
    }

    /**
     * Checks if a Principal is a member of the group.
     *
     * @param member the Principal to be checked
     * @return true if the member exists in the group
     */
    public boolean isMember(Principal member) {
        return principals.contains(member);
    }

    /**
     * Returns an enumeration of the group's members.
     *
     * @return an Enumeration of the members
     */
    public Enumeration<? extends Principal> members() {
        return Collections.enumeration(principals);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OscarGroup other = (OscarGroup) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

}
