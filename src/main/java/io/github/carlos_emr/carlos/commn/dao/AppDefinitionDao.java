/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.AppDefinition;

/**
 * DAO interface for managing OAuth application definitions.
 * <p>
 * Provides operations to retrieve application definitions by name or consumer key,
 * supporting the CARLOS EMR OAuth 1.0a authentication framework.
 *
 * @since 2001
 */
public interface AppDefinitionDao extends AbstractDao<AppDefinition> {

    /**
     * Retrieves all application definitions.
     *
     * @return List of all {@link AppDefinition} records
     */
    public List<AppDefinition> findAll();

    /**
     * Finds an application definition by its unique name.
     *
     * @param name String the application name
     * @return the matching {@link AppDefinition}, or {@code null} if not found
     */
    public AppDefinition findByName(String name);

    /**
     * Finds an application definition by its OAuth consumer key.
     * Searches within the XML configuration stored in the entity.
     *
     * @param consumerKey String the OAuth consumer key
     * @return the matching {@link AppDefinition}, or {@code null} if not found
     */
    public AppDefinition findByConsumerKey(String consumerKey);
}
