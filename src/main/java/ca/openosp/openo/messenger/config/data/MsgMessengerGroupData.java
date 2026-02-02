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

package ca.openosp.openo.messenger.config.data;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.servlet.jsp.JspWriter;

import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.GroupMembersDao;
import ca.openosp.openo.commn.dao.GroupsDao;
import ca.openosp.openo.commn.model.GroupMembers;
import ca.openosp.openo.commn.model.Groups;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.util.ConversionUtils;

/**
 * Data access and management class for messenger group configurations within the OpenO EMR system.
 * 
 * <p>This class provides functionality to manage and display healthcare provider groups used in the 
 * internal messaging system. It handles hierarchical group structures where groups can have parent-child
 * relationships, allowing for organizational structuring of healthcare providers.</p>
 * 
 * <p>Key features include:
 * <ul>
 *   <li>Retrieval of group hierarchies and navigation between parent/child groups</li>
 *   <li>Management of group membership for healthcare providers</li>
 *   <li>HTML generation for group display in the messenger admin interface</li>
 *   <li>Provider listing with group membership status</li>
 * </ul>
 * </p>
 * 
 * <p>The class supports a tree-like structure where group ID 0 represents the root node,
 * and all other groups can have parent groups, creating an organizational hierarchy.</p>
 *
 * @version 1.0
 * @deprecated Use MessagingManager - This class is part of the legacy messenger system and is being 
 *             phased out in favor of the newer MessagingManager implementation which provides 
 *             better separation of concerns and improved security features
 * @since 2003
 */
@Deprecated
public class MsgMessengerGroupData {

    /**
     * Vector containing provider numbers (IDs) of members belonging to a specific group.
     * Populated by the {@link #membersInGroups(String)} method when querying group membership.
     */
    public java.util.Vector<String> groupMemberVector;
    
    /**
     * Counter tracking the number of child groups under a parent group.
     * Updated by the {@link #printGroups(String)} method during group hierarchy traversal.
     */
    public int numGroups;

    /**
     * Retrieves the display name/description for a specific group.
     * 
     * <p>This method returns the descriptive name of a group based on its ID. The root group 
     * (ID = 0) is always named "Root", while all other groups have their names retrieved 
     * from the database.</p>
     *
     * @param grpNo The group number/ID as a String. "0" represents the root group.
     * @return The group description/name if found, or "Root" for group ID 0 or if the group 
     *         doesn't exist in the database
     */
    public String getMyName(String grpNo) {
        // Default to "Root" for the top-level group
        String retval = new String("Root");
        if (Integer.parseInt(grpNo) > 0) {
            GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
            Groups groups = dao.find(ConversionUtils.fromIntString(grpNo));
            if (groups != null) {
                retval = groups.getGroupDesc();
            }
        }
        return retval;
    }

    /**
     * Retrieves the parent group ID for a given group.
     * 
     * <p>This method navigates the group hierarchy by finding the parent of the specified group.
     * Used for upward navigation in the group tree structure.</p>
     *
     * @param grpNo The group number/ID as a String to find the parent for
     * @return The parent group ID as a String, or empty string if the group doesn't exist
     *         or has no parent (is the root)
     */
    public String parentDirectory(String grpNo) {
        String retval = "";
        GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
        Groups groups = dao.find(ConversionUtils.fromIntString(grpNo));
        if (groups != null) {
            retval = "" + groups.getParentId();
        }
        return retval;
    }

    /**
     * Generates HTML links for all child groups under a specified parent group.
     * 
     * <p>This method creates clickable HTML links for navigation to child groups in the 
     * messenger admin interface. It also counts the total number of child groups found.</p>
     * 
     * <p>The generated HTML creates a link for each child group that navigates to the
     * MessengerAdmin.jsp page with the appropriate group number parameter.</p>
     *
     * @param groupNo The parent group number/ID as a String to list children for
     * @return HTML string containing linked child group names separated by line breaks,
     *         or empty string if no child groups exist
     * @side-effect Updates the {@link #numGroups} field with the count of child groups
     */
    public String printGroups(String groupNo) {
        StringBuilder stringBuffer = new StringBuilder();
        // Reset the group counter
        numGroups = 0;

        GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
        // Iterate through all child groups of the specified parent
        for (Groups g : dao.findByParentId(ConversionUtils.fromIntString(groupNo))) {
            stringBuffer.append("<a href=\"MessengerAdmin.jsp?groupNo=" + g.getId() + "\">" + g.getGroupDesc() + "</a><br>");
            numGroups++;
        }
        return stringBuffer.toString();
    }

    /**
     * Retrieves all provider IDs that are members of a specified group.
     * 
     * <p>This method queries the database for all providers belonging to a group and returns
     * their provider numbers in a Vector. The result is also stored in the instance variable
     * {@link #groupMemberVector} for potential reuse.</p>
     *
     * @param grpNo The group number/ID as a String to retrieve members for
     * @return A Vector containing provider numbers (as Strings) of all members in the group,
     *         or an empty Vector if the group has no members
     * @side-effect Populates the {@link #groupMemberVector} field with the same data
     */
    public java.util.Vector<String> membersInGroups(String grpNo) {
        // Initialize a new vector to hold member provider numbers
        groupMemberVector = new java.util.Vector<String>();
        GroupMembersDao dao = SpringUtils.getBean(GroupMembersDao.class);
        // Query all group members and extract their provider numbers
        for (GroupMembers g : dao.findByGroupId(ConversionUtils.fromIntString(grpNo))) {
            groupMemberVector.add(g.getProviderNo());
        }
        return groupMemberVector;
    }

    /**
     * Generates HTML table rows displaying all providers with checkboxes indicating group membership.
     * 
     * <p>This method creates an HTML representation of all healthcare providers in the system,
     * showing each provider's details (name, type) along with a checkbox that is pre-checked
     * if the provider is a member of the specified group. This is typically used in the
     * group administration interface to allow adding/removing providers from groups.</p>
     * 
     * <p>The providers are displayed sorted alphabetically by last name for easier navigation.</p>
     * 
     * <p>Generated HTML structure for each provider:
     * <ul>
     *   <li>Checkbox input with provider number as value</li>
     *   <li>Last name column</li>
     *   <li>First name column</li>
     *   <li>Provider type column</li>
     * </ul>
     * </p>
     *
     * @param locale The locale for internationalization (currently unused but kept for compatibility)
     * @param grpNo The group number/ID as a String to check membership against
     * @param out The JspWriter to output the generated HTML directly to the response
     * @throws RuntimeException if there's an error writing to the JspWriter (caught internally)
     */
    @SuppressWarnings("unchecked")
    public void printAllProvidersWithMembers(Locale locale, String grpNo, JspWriter out) {
        // First get all current members of the group
        java.util.Vector<String> vector = membersInGroups(grpNo);
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> ps = dao.getProviders();
        // Sort providers by last name for display
        Collections.sort(ps, Comparator.comparing(Provider::getLastName));
        try {
            // Generate HTML table row for each provider
            for (Provider p : ps) {
                out.print("   <tr>");
                out.print("      <td>");
                // Check the checkbox if provider is already a member
                if (vector.contains(p.getProviderNo())) {
                    out.print("<input type=\"checkbox\" name=providers value=" + p.getProviderNo() + " checked >");
                } else {
                    out.print("<input type=\"checkbox\" name=providers value=" + p.getProviderNo() + ">");
                }
                out.print("      </td>");
                out.print("      <td>");
                out.print(p.getLastName());
                out.print("      </td>");
                out.print("      <td>");
                out.print(p.getFirstName());
                out.print("      </td>");
                out.print("      <td>");
                String strProviderType = p.getProviderType();
                out.print(strProviderType);
                out.print("      </td>");
                out.print("   </tr>");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }

    /**
     * Generates a breadcrumb navigation trail showing the full hierarchy path from root to the current group.
     * 
     * <p>This method creates an HTML breadcrumb trail that shows the complete path from the root
     * group down to the specified group. Each group in the path is rendered as a clickable link
     * allowing navigation to any level in the hierarchy. The trail uses ">" symbols as separators
     * between levels.</p>
     * 
     * <p>Example output: "Root > Department A > Team B > Subteam C"</p>
     * 
     * <p>The method traverses upward from the given group to the root, building the path in reverse,
     * then prepends the root link to complete the navigation trail.</p>
     *
     * @param grpNo The group number/ID as a String to generate the breadcrumb trail for
     * @return HTML string containing the linked breadcrumb trail from root to the specified group.
     *         Returns just the root link if the group doesn't exist or if grpNo is "0"
     */
    public String printAllBelowGroups(String grpNo) {
        StringBuilder stringBuffer = new StringBuilder();
        int untilZero = Integer.parseInt(grpNo);

        try {
            GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
            // Traverse upward through the hierarchy until reaching the root (parent ID = 0)
            while (untilZero != 0) {
                Groups g = dao.find(untilZero);
                if (g != null) {
                    // Move up to the parent group
                    untilZero = g.getParentId();
                    // Insert at beginning to build path in correct order (root to child)
                    stringBuffer.insert(0, " <a href=\"MessengerAdmin.jsp?groupNo=" + g.getId() + "\"> > "
                            + g.getGroupDesc() + "</a>");
                } else {
                    // Group not found, stop traversal
                    untilZero = 0;
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        // Always prepend the root link as the starting point
        stringBuffer.insert(0, "<a href=\"MessengerAdmin.jsp?groupNo=0\">Root</a>");
        return stringBuffer.toString();
    }

}
