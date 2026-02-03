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
 */

package io.github.carlos_emr.carlos.services;

import java.util.List;

import io.github.carlos_emr.carlos.daos.ProviderDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * It doesn't look like anyone implements this, but meh, some one references it so it'll stay for now.
 */
public interface ProviderManagerTickler {

    public void setProviderDAO(ProviderDAO dao);

    /**
     * Get all providers in the system
     *
     * @return a List of Provider objects
     */
    public List getProviders();

    /**
     * Find a specific providers
     *
     * @param provider_no
     * @return The providers
     */
    public Provider getProvider(String provider_no);


    public List getProgramDomain(String providerNo);
}
