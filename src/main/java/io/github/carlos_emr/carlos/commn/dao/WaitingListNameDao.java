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

import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/**
 * DAO interface for waiting list operations.
 *
 * @since 2001
 */

public interface WaitingListNameDao extends AbstractDao<WaitingListName> {
    /**
     * Count Active Wating List Names.
     * @return long
     */
    long countActiveWatingListNames();

    /**
     * Find Current By Name And Group.
     *
     * @param name String the name
     * @param group String the group
     * @return List<WaitingListName>
     */
    List<WaitingListName> findCurrentByNameAndGroup(String name, String group);

    /**
     * Find By My Groups.
     *
     * @param providerNo String the providerNo
     * @param myGroups List<MyGroup> the myGroups
     * @return List<WaitingListName>
     */
    List<WaitingListName> findByMyGroups(String providerNo, List<MyGroup> myGroups);

    /**
     * Find Current By Group.
     *
     * @param group String the group
     * @return List<WaitingListName>
     */
    List<WaitingListName> findCurrentByGroup(String group);
}
