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
package io.github.carlos_emr.carlos.login.jaas;

import java.security.Principal;

import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * JAAS Principal adapter that bridges CARLOS EMR providers to the JAAS security framework.
 *
 * <p>Extends {@link Provider} to inherit provider data while implementing {@link Principal}
 * for integration with JAAS Subject authentication. Used by {@link BaseLoginModule} and
 * {@link LdapLoginModule} to represent authenticated providers.
 *
 * @see OscarRole
 * @see OscarGroup
 * @since 2026-03-17
 */
public class OscarPrincipal extends Provider implements Principal {

    private String name;

    /** Default constructor. */
    public OscarPrincipal() {
        super();
    }

    /**
     * Constructs an OscarPrincipal by copying fields from an existing Provider.
     *
     * @param provider Provider the provider to copy from
     */
    public OscarPrincipal(Provider provider) {
        super(provider);
    }

    /**
     * Constructs an OscarPrincipal with the specified provider fields.
     *
     * @param providerNo String the provider number
     * @param lastName String the provider's last name
     * @param providerType String the provider type
     * @param sex String the provider's sex
     * @param specialty String the provider's specialty
     * @param firstName String the provider's first name
     */
    public OscarPrincipal(String providerNo, String lastName, String providerType, String sex, String specialty, String firstName) {
        super(providerNo, lastName, providerType, sex, specialty, firstName);
    }

    /**
     * Constructs an OscarPrincipal with just the provider number.
     *
     * @param providerNo String the provider number
     */
    public OscarPrincipal(String providerNo) {
        super(providerNo);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the principal name (typically the login username).
     *
     * @param name String the principal name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Creates a new instance deep copy of this instance.
     *
     * @return Returns a new copy of the providers with all matching fields populated.
     */
    public Provider asProvider() {
        return new Provider(this);
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = prime + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OscarPrincipal other = (OscarPrincipal) obj;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        return true;
    }


}
