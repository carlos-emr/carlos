//CHECKSTYLE:OFF
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


package ca.openosp.openo.managers;

import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import ca.openosp.openo.commn.dao.RBTGroupDao;
import ca.openosp.openo.commn.model.RBTGroup;
import ca.openosp.openo.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class RBTGroupManager {

    @Autowired
    private RBTGroupDao rbtGroupDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    /**
     * Removes all templates in a given template group and deletes the group from the database.
     *
     * @param loggedInInfo User current login information and credentials
     * @param name         Group name to be deleted
     */
    public void delTemplateGroup(LoggedInInfo loggedInInfo, String name) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("Unauthorised Access. Object[_admin]");
        }
        rbtGroupDao.deleteByName(name);
    }

    /**
     * Adds a template to an existing template group by linking the template id to a group name and persists the relationship to the database.
     *
     * @param loggedInInfo User current login information and credentials
     * @param groupName    Name of the template group to add the template to
     * @param tid          Template id of the group to be added
     */
    public void addTemplateToGroup(LoggedInInfo loggedInInfo, String groupName, int tid) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("Unauthorised Access. Object[_admin]");
        }

        groupName = groupName.trim();
        groupName = StringEscapeUtils.escapeJava(groupName);

        RBTGroup rbtGroup = new RBTGroup();
        rbtGroup.setTemplateId(tid);
        rbtGroup.setGroupName(groupName);
        rbtGroupDao.persist(rbtGroup);
    }

    /**
     * Removes a template from an existing template group relationship
     *
     * @param loggedInInfo User current login information and credentials
     * @param groupName    Name of the template group from which to delete the template
     * @param tid          Template id of the template to delete from the group
     */
    public void remTemplateFromGroup(LoggedInInfo loggedInInfo, String groupName, int tid) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new RuntimeException("Unauthorised Access. Object[_admin]");
        }

        rbtGroupDao.deleteByNameAndTemplateId(groupName, tid);
    }

    /**
     * Returns a list of template groups from the database
     *
     * @param loggedInInfo User current login information and credentials
     */
    public List<String> getTemplateGroups(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("Unauthorised Access. Object[_admin]");
        }

        return rbtGroupDao.getGroupNames();
    }

    /**
     * Returns a list of templates in a given template group
     *
     * @param loggedInInfo User current login information and credentials
     * @param groupName    Name of the group to retrieve
     */
    public List<RBTGroup> getGroup(LoggedInInfo loggedInInfo, String groupName) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("Unauthorised Access. Object[_admin]");
        }

        return rbtGroupDao.getByGroupName(groupName);
    }

}