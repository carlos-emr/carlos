/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes provider-group ("My Group") memberships selected on a group list page.
 *
 * <p>Both group lists, Administration &#9656; Schedule Management &#9656; Search/Edit/Delete
 * Groups and Preferences &#9656; View groups, render one checkbox per {@code mygroup} row, named
 * {@code <mygroup_no><provider_no>} with the value {@code <mygroup_no>}. The provider number is
 * therefore the part of the name that follows the value. The same form also posts control fields:
 * the CSRFGuard token, the submit button and, on older pages, {@code displaymode} and
 * {@code submit_form}. The JSP scriptlets this replaces cut every parameter name at the length of
 * its value, so the token answered {@code StringIndexOutOfBoundsException} and the whole delete
 * failed with HTTP 500 (issue #4515).</p>
 *
 * <p>A parameter is read as a membership only when one of its values is a non-empty, proper prefix
 * of its name. Every control field fails that test, because none of their values starts their own
 * name, so no list of reserved names has to be kept in step with the pages or with CSRFGuard's
 * configured token name. The selection only names rows to remove: a pair that matches no stored
 * row deletes nothing.</p>
 *
 * @since 2026-10-15
 */
@Service
public class MyGroupMembershipService {

    private final MyGroupDao myGroupDao;

    public MyGroupMembershipService(MyGroupDao myGroupDao) {
        this.myGroupDao = myGroupDao;
    }

    /**
     * Deletes the memberships selected in a group list form, in one transaction.
     *
     * @param parameters the posted request parameters, as {@code ServletRequest#getParameterMap()}
     *                   returns them; control fields are ignored
     * @return the number of {@code mygroup} rows removed; selections that match no stored row are
     *         not counted
     */
    @Transactional
    public int deleteSelectedMembers(Map<String, String[]> parameters) {
        int deleted = 0;
        for (SelectedMember member : selectedMembers(parameters)) {
            MyGroupPrimaryKey key = new MyGroupPrimaryKey();
            key.setMyGroupNo(member.groupNo());
            key.setProviderNo(member.providerNo());
            if (myGroupDao.remove(key)) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Reads the selected memberships out of a group list form's parameters, in posting order and
     * without duplicates.
     *
     * @param parameters the posted request parameters; {@code null} selects nothing
     * @return the selected group and provider pairs
     */
    static List<SelectedMember> selectedMembers(Map<String, String[]> parameters) {
        Set<SelectedMember> selected = new LinkedHashSet<>();
        if (parameters == null) {
            return new ArrayList<>(selected);
        }
        for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
            String name = parameter.getKey();
            String[] values = parameter.getValue();
            if (name == null || values == null) {
                continue;
            }
            // More than one value under one name is legitimate: group "AB" holding provider "C1"
            // and group "ABC" holding provider "1" both render a checkbox named "ABC1".
            for (String groupNo : values) {
                if (groupNo != null && !groupNo.isEmpty()
                        && name.length() > groupNo.length() && name.startsWith(groupNo)) {
                    selected.add(new SelectedMember(groupNo, name.substring(groupNo.length())));
                }
            }
        }
        return new ArrayList<>(selected);
    }

    /** One selected {@code mygroup} row, by its composite key. */
    record SelectedMember(String groupNo, String providerNo) {
    }
}
