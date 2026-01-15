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


package ca.openosp.openo.messenger.data;


import java.util.List;

import ca.openosp.openo.commn.dao.GroupMembersDao;
import ca.openosp.openo.commn.dao.GroupsDao;
import ca.openosp.openo.commn.dao.OscarCommLocationsDao;
import ca.openosp.openo.commn.model.Groups;
import ca.openosp.openo.commn.model.OscarCommLocations;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.SpringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ca.openosp.openo.messenger.docxfer.util.MsgCommxml;

/**
 * Utility class for generating and updating the hierarchical address book XML for the messaging system.
 * 
 * <p>This class creates an XML representation of the provider group hierarchy, including all
 * groups, subgroups, and their member providers. The address book is stored as XML in the
 * database and is used by the messaging interface to display organized provider lists.</p>
 * 
 * <p>The generated XML structure represents:
 * <ul>
 *   <li>Hierarchical group structure with parent-child relationships</li>
 *   <li>Provider memberships within groups</li>
 *   <li>Provider details (ID, formatted name) for display</li>
 * </ul>
 * </p>
 * 
 * <p>This is a modified version of ca.openosp.openo.comm.client.AddressBook adapted for the
 * OpenO messaging system.</p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgAddressBook
 * @see Groups
 * @see GroupMembersDao
 */
public class MsgAddressBookMaker {
    private OscarCommLocationsDao oscarCommLocationsDao = SpringUtils.getBean(OscarCommLocationsDao.class);


    /**
     * Updates the local address book XML in the database.
     * 
     * <p>This method generates a complete XML representation of the current group
     * hierarchy and provider memberships, then updates all active communication
     * locations with the new address book data. The method is typically called
     * after any changes to groups or group memberships.</p>
     * 
     * <p>The generated XML starts from the root group (ID 0) and recursively
     * includes all subgroups and their members.</p>
     * 
     * @return true if the address book was successfully generated, false otherwise
     */
    public boolean updateAddressBook() {
        Document doc = MsgCommxml.newDocument();

        Element addressBook = doc.createElement("addressBook");

        // Start recursive build from root group (ID 0)
        addressBook.appendChild(this.getChildren(doc, 0, ""));

        // Update all active communication locations with new address book
        if (!oscarCommLocationsDao.findByCurrent1(1).isEmpty()) {
            String newAddressBook = MsgCommxml.toXML(addressBook);

            List<OscarCommLocations> ls = oscarCommLocationsDao.findByCurrent1(1);
            for (OscarCommLocations l : ls) {
                l.setAddressBook(newAddressBook);
                oscarCommLocationsDao.merge(l);
            }

        }

        return (addressBook != null);
    }

    /**
     * Recursively builds the XML structure for a group and all its children.
     * 
     * <p>This method creates an XML element for the specified group, then recursively
     * adds all child groups and finally adds all provider members. Empty groups
     * (those with no subgroups or members) are not included in the final structure.</p>
     * 
     * <p>The generated XML structure for each group includes:
     * <ul>
     *   <li>Group element with id and description attributes</li>
     *   <li>Nested group elements for all subgroups</li>
     *   <li>Address elements for all member providers</li>
     * </ul>
     * </p>
     * 
     * @param doc The XML document being built
     * @param groupId The ID of the group to process
     * @param desc The description/name of the group (empty for root)
     * @return The XML Element representing this group and all its children
     */
    private Element getChildren(Document doc, int groupId, String desc) {
        Element group = doc.createElement("group");
        // Only add attributes for non-root groups
        if (desc.length() > 0) {
            group.setAttribute("id", String.valueOf(groupId));
            group.setAttribute("desc", desc);
        }

        // Recursively add all child groups
        GroupsDao dao = SpringUtils.getBean(GroupsDao.class);
        for (Groups g : dao.findByParentId(groupId)) {
            Element subGrp = getChildren(doc, g.getId(), g.getGroupDesc());

            // Only add non-empty subgroups
            if (subGrp.hasChildNodes()) {
                group.appendChild(subGrp);
            }
        }

        // Add all provider members of this group
        GroupMembersDao gDao = SpringUtils.getBean(GroupMembersDao.class);
        for (Object[] g : gDao.findMembersByGroupId(groupId)) {
            Provider p = (Provider) g[1];

            Element address = MsgCommxml.addNode(group, "address");
            address.setAttribute("id", p.getProviderNo());
            address.setAttribute("desc", p.getFormattedName());
        }
        return group;
    }

}
