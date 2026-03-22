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

import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;

/**
 * DAO interface for incoming lab rules operations.
 *
 * @since 2001
 */

public interface IncomingLabRulesDao extends AbstractDao<IncomingLabRules> {
    /**
     * Find Current By Provider No And Frwd Provider.
     *
     * @param providerNo String the providerNo
     * @param frwdProvider String the frwdProvider
     * @return List<IncomingLabRules>
     */
    List<IncomingLabRules> findCurrentByProviderNoAndFrwdProvider(String providerNo, String frwdProvider);

    /**
     * Find By Provider No And Frwd Provider.
     *
     * @param providerNo String the providerNo
     * @param frwdProvider String the frwdProvider
     * @return List<IncomingLabRules>
     */
    List<IncomingLabRules> findByProviderNoAndFrwdProvider(String providerNo, String frwdProvider);

    /**
     * Find Current By Provider No.
     *
     * @param providerNo String the providerNo
     * @return List<IncomingLabRules>
     */
    List<IncomingLabRules> findCurrentByProviderNo(String providerNo);

    /**
     * Find By Provider No.
     *
     * @param providerNo String the providerNo
     * @return List<IncomingLabRules>
     */
    List<IncomingLabRules> findByProviderNo(String providerNo);

    /**
     * Find Rules.
     *
     * @param providerNo String the providerNo
     * @return List<Object[]>
     */
    List<Object[]> findRules(String providerNo);
}
