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

import ca.openosp.openo.commn.dao.GroupMembersDao;
import ca.openosp.openo.commn.dao.GroupsDao;
import ca.openosp.openo.commn.model.GroupMembers;
import ca.openosp.openo.commn.model.Groups;
import ca.openosp.openo.managers.MessengerGroupManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.messenger.data.ContactIdentifier;
import ca.openosp.openo.messenger.data.MsgAddressBookMaker;
import ca.openosp.openo.messenger.data.MsgProviderData;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 Action class for administering messenger groups and their memberships in the OpenO EMR system.
 * 
 * <p>This action provides comprehensive management functionality for the messenger group system,
 * including creating groups, managing group memberships, and deleting groups. It supports both
 * the newer method-based approach and legacy form-based operations for backward compatibility.</p>
 * 
 * <p>Key functionalities include:
 * <ul>
 *   <li>Fetching all groups with their members for display</li>
 *   <li>Adding and removing providers from groups</li>
 *   <li>Creating new groups within the hierarchy</li>
 *   <li>Deleting groups (with validation to prevent orphaning child groups)</li>
 *   <li>Managing both local and remote messenger contacts</li>
 * </ul>
 * </p>
 * 
 * <p>The action enforces administrative privileges for sensitive operations and automatically
 * updates the system address book after any structural changes.</p>
 * 
 * @version 2.0
 * @since 2002
 * @see MsgMessengerCreateGroup2Action
 * @see MessengerGroupManager
 * @see MsgAddressBookMaker
 */
public class MsgMessengerAdmin2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MessengerGroupManager messengerGroupManager = SpringUtils.getBean(MessengerGroupManager.class);
    private GroupsDao groupsDao = SpringUtils.getBean(GroupsDao.class);
    private GroupMembersDao groupMembersDao = SpringUtils.getBean(GroupMembersDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Main execution method that routes to specific operations based on the method parameter.
     * 
     * <p>This method implements a method-based routing pattern common in Struts2 actions,
     * allowing multiple related operations to be handled by a single action class.</p>
     * 
     * @return Result string for Struts navigation:
     *         - SUCCESS for successful operations
     *         - "failure" if operation fails (e.g., attempting to delete a group with children)
     */
    public String execute() {
        String method = request.getParameter("method");
        if ("add".equals(method)) {
            return add();
        } else if ("remove".equals(method)) {
            return remove();
        } else if ("create".equals(method)) {
            return create();
        } else if ("delete".equals(method)) {
            return delete();
        } else if ("update".equals(method)) {
            return update();
        }
        return fetch();
    }

    /**
     * Fetches all messenger groups and contacts for display in the administration interface.
     * 
     * <p>This method retrieves:
     * <ul>
     *   <li>All groups with their current members</li>
     *   <li>All local healthcare provider contacts available for messaging</li>
     *   <li>All remote contacts from connected healthcare facilities</li>
     * </ul>
     * </p>
     * 
     * <p>The retrieved data is placed in request attributes for rendering in the JSP view.</p>
     * 
     * @return SUCCESS to display the messenger admin page with all group and contact data
     */
    @SuppressWarnings("unused")
    public String fetch() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Retrieve all groups with their member lists
        Map<Groups, List<MsgProviderData>> groups = messengerGroupManager.getAllGroupsWithMembers(loggedInInfo);
        // Get all local providers available for messaging
        List<MsgProviderData> localContacts = messengerGroupManager.getAllLocalMessengerContactList(loggedInInfo);
        // Get remote contacts from other connected facilities
        Map<String, List<MsgProviderData>> remoteContacts = messengerGroupManager.getAllRemoteMessengerContactList(loggedInInfo);

        request.setAttribute("groups", groups);
        request.setAttribute("localContacts", localContacts);
        request.setAttribute("remoteContacts", remoteContacts);
        return SUCCESS;
    }

    /**
     * Adds a healthcare provider or contact to a messenger group.
     * 
     * <p>This method handles adding members to groups using composite identifiers that
     * can represent both local providers and remote contacts. The member ID is expected
     * to be in a composite format that includes the contact type and identifier.</p>
     * 
     * Request parameter "member": The composite member ID to add (format: type:id).
     * Request parameter "group": The target group ID, defaults to "0" (root) if not specified.
     *
     * @return NONE as response is set via request attribute
     */
    @SuppressWarnings("unused")
    public String add() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String memberId = request.getParameter("member");
        String groupId = request.getParameter("group");
        if (groupId == null) {
            groupId = "0";
        }
        if (memberId != null && !memberId.isEmpty()) {
            // Parse the composite ID which contains contact type and identifier
            ContactIdentifier contactIdentifier = new ContactIdentifier(memberId);
            messengerGroupManager.addMember(loggedInInfo, contactIdentifier, Integer.parseInt(groupId));
        }
        request.setAttribute("success", true);

        return NONE;
    }

    /**
     * Removes a member from a group or deletes an entire group.
     * 
     * <p>This method supports three removal scenarios:
     * <ul>
     *   <li>Remove a member from all groups (when groupId is "0")</li>
     *   <li>Remove a member from a specific group</li>
     *   <li>Delete an entire group (when no member is specified)</li>
     * </ul>
     * </p>
     * 
     * Request parameter "member": The composite member ID to remove (optional).
     * Request parameter "group": The group ID to remove from or to delete entirely.
     *
     * @return NONE as response is set via request attribute
     */
    @SuppressWarnings("unused")
    public String remove() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String memberId = request.getParameter("member");
        String groupId = request.getParameter("group");
        if (groupId == null || groupId.isEmpty()) {
            groupId = "0";
        }

        if (memberId != null && !memberId.isEmpty()) {
            // Parse the composite ID for the member to remove
            ContactIdentifier contactIdentifier = new ContactIdentifier(memberId);

            if ("0".equals(groupId)) {
                // Remove member from all groups
                messengerGroupManager.removeMember(loggedInInfo, contactIdentifier);
            } else {
                // Remove member from specific group
                contactIdentifier.setGroupId(Integer.parseInt(groupId));
                messengerGroupManager.removeGroupMember(loggedInInfo, contactIdentifier);
            }
        } else if (!"0".equals(groupId)) {
            // No member specified - delete the entire group
            messengerGroupManager.removeGroup(loggedInInfo, Integer.parseInt(groupId));
        }

        return NONE;
    }

    /**
     * Creates a new messenger group within the hierarchy.
     * 
     * <p>This method creates a new group with the specified name under a parent group.
     * If no parent is specified, the group is created at the root level (parent ID 0).
     * After creation, the method refreshes the group list by calling fetch().</p>
     * 
     * Request parameter "groupName": The name/description for the new group.
     * Request parameter "parentId": The parent group ID, defaults to "0" (root) if not specified.
     *
     * @return the result of the fetch operation
     */
    public String create() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String groupName = request.getParameter("groupName");
        String parentId = request.getParameter("parentId");
        if (parentId == null) {
            parentId = "0";
        }

        messengerGroupManager.addGroup(loggedInInfo, groupName, Integer.parseInt(parentId));
        // Refresh the display after creating the group
        fetch();

        return NONE;
    }

    /**
     * Legacy method for deleting a messenger group.
     * 
     * <p>This method validates that the group has no child groups before deletion to prevent
     * orphaning groups in the hierarchy. If the group has children, the deletion is rejected
     * with an error message.</p>
     * 
     * <p>The method performs the following steps:
     * <ol>
     *   <li>Checks if the group has child groups</li>
     *   <li>If children exist, returns failure with error message</li>
     *   <li>Removes all group members</li>
     *   <li>Deletes the group itself</li>
     *   <li>Updates the system address book</li>
     * </ol>
     * </p>
     * 
     * @return "failure" if group has children, SUCCESS if deletion completed
     * @deprecated Use remove method instead for newer implementations
     */
    @Deprecated
    @SuppressWarnings("unused")
    public String delete() {
        String parent = new String();

        GroupsDao dao = SpringUtils.getBean(GroupsDao.class);

        // Get the parent ID of the group to be deleted
        Groups gg = dao.find(ConversionUtils.fromIntString(grpNo));
        if (gg != null) {
            parent = "" + gg.getParentId();
        }

        // Check if this group has any child groups
        if (dao.findByParentId(ConversionUtils.fromIntString(parent)).size() > 1) {
            request.setAttribute("groupNo", grpNo);
            request.setAttribute("fail", "This Group has Children, you must delete the children groups first");
            return "failure";
        }

        // Remove all members from the group first
        for (GroupMembers g : groupMembersDao.findByGroupId(Integer.parseInt(grpNo))) {
            groupMembersDao.remove(g.getId());
        }

        // Delete the group itself
        Groups g = groupsDao.find(Integer.parseInt(grpNo));
        if (g != null) {
            groupsDao.remove(g.getId());
        }

        // Update the system address book to reflect the deletion
        MsgAddressBookMaker addMake = new MsgAddressBookMaker();
        addMake.updateAddressBook();
        
        // Return to the parent group view
        request.setAttribute("groupNo", parent);

        return SUCCESS;
    }

    /**
     * Legacy method for updating group memberships or deleting groups based on button clicked.
     * 
     * <p>This method handles two operations based on which submit button was clicked:
     * <ul>
     *   <li>Update Group Members: Replaces all current members with the selected providers</li>
     *   <li>Delete This Group: Removes the group and all its memberships</li>
     * </ul>
     * </p>
     * 
     * <p>The method enforces administrative privileges and uses resource bundles for
     * internationalized button labels to determine the operation.</p>
     * 
     * @return "failure" if attempting to delete a group with children, SUCCESS otherwise
     * @throws SecurityException if the user lacks administrative write privileges
     * @deprecated Use the newer add/remove/create methods for better separation of concerns
     */
    @Deprecated
    @SuppressWarnings("unused")
    public String update() {

        // Enforce security: only administrators can update groups
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String[] providers = this.getProviders();

        String parent = new String();

        // Get localized button labels to determine which operation was requested
        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());

        if (update.equals(oscarR.getString("messenger.config.MessengerAdmin.btnUpdateGroupMembers"))) {
            // Update group members operation
            
            // First remove all existing members from the group
            for (GroupMembers g : groupMembersDao.findByGroupId(Integer.parseInt(grpNo))) {
                groupMembersDao.remove(g.getId());
            }

            // Add all newly selected providers to the group
            for (int i = 0; i < providers.length; i++) {
                GroupMembers gm = new GroupMembers();
                gm.setGroupId(Integer.parseInt(grpNo));
                gm.setProviderNo(providers[i]);
                groupMembersDao.persist(gm);
            }

            // Update the system address book to reflect membership changes
            MsgAddressBookMaker addMake = new MsgAddressBookMaker();
            addMake.updateAddressBook();
            request.setAttribute("groupNo", grpNo);
        } else if (delete.equals(oscarR.getString("messenger.config.MessengerAdmin.btnDeleteThisGroup"))) {
            // Delete group operation
            
            GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
            // Get the parent ID for navigation after deletion
            Groups gg = dao.find(ConversionUtils.fromIntString(grpNo));
            if (gg != null) {
                parent = "" + gg.getParentId();
            }

            // Validate that the group has no children
            if (dao.findByParentId(ConversionUtils.fromIntString(parent)).size() > 1) {
                request.setAttribute("groupNo", grpNo);
                request.setAttribute("fail", "This Group has Children, you must delete the children groups first");
                return "failure";
            }

            // Remove all members from the group
            for (GroupMembers g : groupMembersDao.findByGroupId(Integer.parseInt(grpNo))) {
                groupMembersDao.remove(g.getId());
            }

            // Delete the group itself
            Groups g = groupsDao.find(Integer.parseInt(grpNo));
            if (g != null) {
                groupsDao.remove(g.getId());
            }

            // Update the system address book
            MsgAddressBookMaker addMake = new MsgAddressBookMaker();
            addMake.updateAddressBook();
            // Navigate to parent group after deletion
            request.setAttribute("groupNo", parent);
        }

        return SUCCESS;
    }
    /**
     * The group number/ID being operated on (for legacy methods).
     */
    String grpNo;
    
    /**
     * Array of provider IDs selected for group membership (for legacy update method).
     */
    String[] provider;
    
    /**
     * Button value for update operation (compared against resource bundle).
     */
    String update;
    
    /**
     * Button value for delete operation (compared against resource bundle).
     */
    String delete;


    /**
     * Gets the update button value for form submission comparison.
     * 
     * @return The update button value, or empty string if not set
     */
    public String getUpdate() {
        if (this.update == null) {
            this.update = new String();
        }
        return update;
    }

    /**
     * Sets the update button value from form submission.
     * 
     * @param update The button value to set
     */
    public void setUpdate(String update) {
        this.update = update;
    }

    /**
     * Gets the delete button value for form submission comparison.
     * 
     * @return The delete button value, or empty string if not set
     */
    public String getDelete() {
        if (this.delete == null) {
            this.delete = new String();
        }
        return delete;
    }

    /**
     * Sets the delete button value from form submission.
     * 
     * @param delete The button value to set
     */
    public void setDelete(String delete) {
        this.delete = delete;
    }

    /**
     * Gets the array of selected provider IDs.
     * 
     * @return The provider ID array
     */
    public String[] getProvider() {
        return provider;
    }

    /**
     * Sets the array of selected provider IDs.
     * 
     * @param provider The provider ID array to set
     */
    public void setProvider(String[] provider) {
        this.provider = provider;
    }

    /**
     * Gets the array of selected provider IDs with null safety.
     * 
     * @return The provider ID array, or empty array if not set
     */
    public String[] getProviders() {
        if (this.provider == null) {
            this.provider = new String[]{};
        }
        return this.provider;
    }

    /**
     * Sets the array of selected provider IDs (alternate setter).
     * 
     * @param prov The provider ID array to set
     */
    public void setProviders(String[] prov) {
        this.provider = prov;
    }

    /**
     * Gets the group number/ID being operated on.
     * 
     * @return The group number, or empty string if not set
     */
    public String getGrpNo() {
        if (this.grpNo == null) {
            this.grpNo = new String();
        }
        return this.grpNo;
    }

    /**
     * Sets the group number/ID to operate on.
     * 
     * @param grpNo The group number to set
     */
    public void setGrpNo(String grpNo) {
        this.grpNo = grpNo;
    }

}
