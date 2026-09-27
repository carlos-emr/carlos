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

package io.github.carlos_emr.carlos.messenger.config.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.GroupsDao;
import io.github.carlos_emr.carlos.commn.model.Groups;
import io.github.carlos_emr.carlos.managers.MessengerGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.messenger.data.MsgAddressBookMaker;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 Action class for administering messenger groups and their memberships in the CARLOS EMR system.
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
 *   <li>Managing local messenger contacts</li>
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

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INVALID_REASON = "invalid";
    private static final Logger logger = MiscUtils.getLogger();

    private MessengerGroupManager messengerGroupManager = SpringUtils.getBean(MessengerGroupManager.class);
    private GroupsDao groupsDao = SpringUtils.getBean(GroupsDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Main execution method that routes to specific operations based on the method parameter.
     * 
     * <p>This method implements a method-based routing pattern common in Struts2 actions,
     * allowing multiple related operations to be handled by a single action class.</p>
     * 
     * @return Struts navigation result:
     *         {@link #SUCCESS} for successful operations;
     *         {@code "failure"} if an operation fails (e.g. deleting a group with children);
     *         {@link #NONE} when the request is rejected with HTTP 405 (non-POST mutation)
     *         or when a mutating branch writes its response directly
     * @throws java.io.IOException if the 405 error response cannot be written
     * @throws SecurityException if the current user lacks the required
     *         {@code _admin} read (view/fetch) or write (mutating method) privilege
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws java.io.IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String method = request.getParameter("method");

        // Mutating endpoints: require _admin write + POST. Read endpoints
        // (fetchGroups / view): require _admin read. Without these checks an
        // authenticated non-admin could enumerate or alter messenger groups.
        boolean isMutation = "add".equals(method) || "remove".equals(method)
                || "create".equals(method) || "delete".equals(method) || "update".equals(method);

        String providerNo = loggedInInfo == null ? "anon" : loggedInInfo.getLoggedInProviderNo();
        if (isMutation) {
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
                logger.warn("MsgMessengerAdmin denied: provider={} method={} lacks _admin write",
                        providerNo, method);
                throw new SecurityException("missing required sec object (_admin)");
            }
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                logger.warn("MsgMessengerAdmin method not allowed: provider={} method={} httpMethod={}",
                        providerNo, method, request.getMethod());
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
        } else {
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
                logger.warn("MsgMessengerAdmin denied: provider={} lacks _admin read (method={})",
                        providerNo, method);
                throw new SecurityException("missing required sec object (_admin)");
            }
        }

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
        request.setAttribute("groups", groups);
        request.setAttribute("localContacts", localContacts);
        return SUCCESS;
    }

    /**
     * Adds a healthcare provider or contact to a messenger group.
     * 
     * <p>This method handles adding members to groups using composite identifiers.
     * The member ID is expected to be in the composite format produced by
     * {@link ContactIdentifier#getCompositeId()}.</p>
     *
     * <p>Direct-response endpoint. It answers a small JSON body the admin page reads:
     * <ul>
     *   <li>{@code 200 {"success":true}} when the membership was written;</li>
     *   <li>{@code 409 {"success":false,"reason":"duplicate"}} when the contact is already
     *       in that group, so the page can say so instead of silently writing a second row
     *       (a duplicate row delivers every group message twice);</li>
     *   <li>{@code 400 {"success":false,"reason":"invalid"}} when {@code member} is malformed or
     *       {@code group} is invalid or no longer exists.</li>
     * </ul>
     * 
     * Request parameter "member": The composite member ID to add.
     * Request parameter "group": The target group ID, defaults to "0" (root) if not specified.
     *
     * @return NONE; the response body is written directly
     * @throws java.io.IOException if the JSON response cannot be written
     */
    @SuppressWarnings("unused")
    public String add() throws java.io.IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String memberId = request.getParameter("member");
        Integer groupId = parseGroupId(request.getParameter("group"));
        ContactIdentifier contactIdentifier = parseMemberId(memberId);
        if (contactIdentifier == null || groupId == null) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }
        try {
            var result = messengerGroupManager.addMemberIfAbsent(loggedInInfo, contactIdentifier, groupId);
            return result.created() ? writeMutationResult(HttpServletResponse.SC_OK, null)
                    : writeMutationResult(HttpServletResponse.SC_CONFLICT, "duplicate");
        } catch (MessengerGroupManager.UnknownGroupException _) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }
    }

    private static ContactIdentifier parseMemberId(String value) {
        // Provider numbers occupy a six-character column; optional numeric components
        // are facility, clinic location and group. Reject negative-provider ambiguity.
        if (value == null || !value.matches("\\w{1,6}(?:-\\d+){0,3}")) return null;
        String[] parts = value.split("-");
        try {
            ContactIdentifier id = new ContactIdentifier();
            id.setContactId(parts[0]);
            if (parts.length > 1) id.setFacilityId(Integer.parseInt(parts[1]));
            if (parts.length > 2) id.setClinicLocationNo(Integer.parseInt(parts[2]));
            if (parts.length > 3) id.setGroupId(Integer.parseInt(parts[3]));
            return id;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Parse the {@code group} parameter; absent means the general registry (group 0).
     *
     * @return the group id, or {@code null} when the value is not a non-negative integer
     */
    private static Integer parseGroupId(String groupParam) {
        if (groupParam == null || groupParam.isEmpty()) {
            return 0;
        }
        try {
            int groupId = Integer.parseInt(groupParam);
            return groupId >= 0 ? groupId : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private String writeMutationResult(int status, String reason) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", status == HttpServletResponse.SC_OK);
        if (reason != null) result.put("reason", reason);
        JSON.writeValue(response.getWriter(), result);
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
    public String remove() throws java.io.IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String memberId = request.getParameter("member");
        Integer groupId = parseGroupId(request.getParameter("group"));
        if (groupId == null) return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);

        if (memberId != null && !memberId.isEmpty()) {
            // Parse the composite ID for the member to remove
            ContactIdentifier contactIdentifier = parseMemberId(memberId);
            if (contactIdentifier == null) return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);

            if (groupId == 0) {
                // Remove member from all groups
                messengerGroupManager.removeMember(loggedInInfo, contactIdentifier);
            } else {
                // Remove member from specific group
                contactIdentifier.setGroupId(groupId);
                messengerGroupManager.removeGroupMember(loggedInInfo, contactIdentifier);
            }
        } else if (groupId != 0) {
            // No member specified - delete the entire group
            try {
                if (!messengerGroupManager.removeGroup(loggedInInfo, groupId)) {
                    return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
                }
            } catch (MessengerGroupManager.GroupHasChildrenException _) {
                return writeMutationResult(HttpServletResponse.SC_CONFLICT, "children");
            }
        } else {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
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
    public String create() throws java.io.IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String groupName = request.getParameter("groupName");
        Integer parentId = parseGroupId(request.getParameter("parentId"));
        if (parentId == null || groupName == null || groupName.isBlank()) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }

        try {
            messengerGroupManager.addGroup(loggedInInfo, groupName, parentId);
        } catch (IllegalArgumentException _) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }
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
    public String delete() throws java.io.IOException {
        Integer groupId = parseGroupId(grpNo);
        if (groupId == null || groupId == 0) return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        Groups group = groupsDao.find(groupId);
        if (group == null) return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        try {
            if (!messengerGroupManager.removeGroup(LoggedInInfo.getLoggedInInfoFromSession(request), groupId)) {
                return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
            }
        } catch (MessengerGroupManager.GroupHasChildrenException _) {
            return writeMutationResult(HttpServletResponse.SC_CONFLICT, "children");
        }
        new MsgAddressBookMaker().updateAddressBook();
        request.setAttribute("groupNo", String.valueOf(group.getParentId()));
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
    public String update() throws java.io.IOException {
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(info, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
        boolean updating = bundle.getString("messenger.config.MessengerAdmin.btnUpdateGroupMembers").equals(update);
        boolean deleting = bundle.getString("messenger.config.MessengerAdmin.btnDeleteThisGroup").equals(delete);
        if (updating == deleting) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }
        if (deleting) return delete();
        Integer groupId = parseGroupId(grpNo);
        String[] providers = getProviders();
        if (grpNo == null || grpNo.isEmpty() || groupId == null || !validLegacyProviders(providers)) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }
        try {
            messengerGroupManager.replaceGroupMembers(info, groupId, providers);
        } catch (MessengerGroupManager.UnknownGroupException _) {
            return writeMutationResult(HttpServletResponse.SC_BAD_REQUEST, INVALID_REASON);
        }
        new MsgAddressBookMaker().updateAddressBook();
        request.setAttribute("groupNo", grpNo);
        return SUCCESS;
    }

    private static boolean validLegacyProviders(String[] providers) {
        if (providers == null) return true;
        for (String provider : providers) {
            if (parseMemberId(provider) == null || provider.contains("-")) return false;
        }
        return true;
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
    @StrutsParameter
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
    @StrutsParameter
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
    @StrutsParameter
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
    @StrutsParameter
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
    @StrutsParameter
    public void setGrpNo(String grpNo) {
        this.grpNo = grpNo;
    }

}
