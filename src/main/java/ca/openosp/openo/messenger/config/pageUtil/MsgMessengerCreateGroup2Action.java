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

package ca.openosp.openo.messenger.config.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.GroupsDao;
import ca.openosp.openo.commn.model.Groups;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.messenger.data.MsgAddressBookMaker;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 Action class for creating and renaming messenger groups in the OpenO EMR system.
 * 
 * <p>This action handles the creation of new messenger groups and the renaming of existing groups
 * within the healthcare provider organization structure. It enforces administrative privileges
 * and maintains the hierarchical group structure used for organizing healthcare providers
 * in the internal messaging system.</p>
 * 
 * <p>The action supports two operations:
 * <ul>
 *   <li>Type 1: Create a new group under a specified parent group</li>
 *   <li>Type 2: Rename an existing group</li>
 * </ul>
 * </p>
 * 
 * <p>After any group modification, the system's address book is automatically updated
 * to reflect the changes for all users.</p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgMessengerAdmin2Action
 * @see MsgAddressBookMaker
 */
public class MsgMessengerCreateGroup2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Main execution method that handles group creation or renaming operations.
     * 
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Validates that the current user has administrative write privileges</li>
     *   <li>Retrieves and validates the group name and operation type</li>
     *   <li>For type 1: Creates a new group with the specified parent</li>
     *   <li>For type 2: Renames an existing group</li>
     *   <li>Updates the system address book to reflect changes</li>
     * </ol>
     * </p>
     * 
     * @return SUCCESS constant indicating successful completion, redirects to the parent group view
     * @throws IOException if there's an I/O error during processing
     * @throws ServletException if there's a servlet processing error
     * @throws SecurityException if the user lacks administrative privileges
     */
    public String execute() throws IOException, ServletException {

        // Enforce security: only administrators can create or modify groups
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String grpName = this.getGroupName();
        String parentID = this.getParentID();
        String type = this.getType2();

        // Remove any leading/trailing whitespace from the group name
        grpName = grpName.trim();

        // Only process if a valid group name was provided
        if (!grpName.equals("")) {
            if (type.equals("1")) {
                // Type 1: Create a new group
                GroupsDao gd = SpringUtils.getBean(GroupsDao.class);
                Groups g = new Groups();
                g.setParentId(Integer.parseInt(parentID));
                g.setGroupDesc(grpName);
                gd.persist(g);

                // Update the system-wide address book to include the new group
                MsgAddressBookMaker addMake = new MsgAddressBookMaker();
                addMake.updateAddressBook();
            } else if (type.equals("2")) {
                // Type 2: Rename an existing group (parentID actually contains the group ID to rename)
                Groups g = dao.find(Integer.parseInt(parentID));
                if (g != null) {
                    g.setGroupDesc(grpName);
                    dao.merge(g);
                }
                // Update the system-wide address book with the renamed group
                MsgAddressBookMaker addMake = new MsgAddressBookMaker();
                addMake.updateAddressBook();
            }
        }
        // Set the group number for the view to display the correct parent group
        request.setAttribute("groupNo", parentID);
        return SUCCESS;
    }

    /**
     * The name/description for the new group or the new name for an existing group.
     */
    String groupName;
    
    /**
     * For type 1: The parent group ID under which to create the new group.
     * For type 2: The ID of the group to rename.
     */
    String parentID;
    
    /**
     * Operation type indicator:
     * "1" = Create new group
     * "2" = Rename existing group
     */
    String type;

    /**
     * Sets the operation type for this action.
     * 
     * @param type "1" for create new group, "2" for rename existing group
     */
    public void setType2(String type) {
        this.type = type;
    }

    /**
     * Gets the operation type for this action.
     * 
     * @return "1" for create new group, "2" for rename existing group, or empty string if not set
     */
    public String getType2() {
        if (this.type == null) {
            this.type = new String();
        }
        return this.type;
    }

    /**
     * Sets the parent group ID for new groups or the target group ID for renaming.
     * 
     * @param parentID The group ID to use based on operation type
     */
    public void setParentID(String parentID) {
        this.parentID = parentID;
    }

    /**
     * Gets the parent group ID for new groups or the target group ID for renaming.
     * 
     * @return The group ID to use based on operation type, or empty string if not set
     */
    public String getParentID() {
        if (this.parentID == null) {
            this.parentID = new String();
        }
        return this.parentID;
    }


    /**
     * Sets the group name for creation or the new name for renaming.
     * 
     * @param groupName The name to use for the group
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /**
     * Gets the group name for creation or the new name for renaming.
     * 
     * @return The group name, or empty string if not set
     */
    public String getGroupName() {
        if (this.groupName == null) {
            this.groupName = new String();
        }
        return this.groupName;
    }
}
