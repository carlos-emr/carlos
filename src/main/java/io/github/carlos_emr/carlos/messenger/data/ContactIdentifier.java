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
package io.github.carlos_emr.carlos.messenger.data;

import java.io.Serializable;

/**
 * Composite identifier class for messaging contacts across healthcare facilities in the OpenO EMR system.
 * 
 * <p>This class manages the complex identification requirements for messaging contacts that can exist
 * across multiple facilities and clinic locations. It provides a unified way to identify healthcare
 * providers and other messaging contacts throughout the distributed healthcare network.</p>
 * 
 * <p>The composite ID format supports:
 * <ul>
 *   <li>Local providers within the same facility</li>
 *   <li>Remote providers at other connected healthcare facilities</li>
 *   <li>Group membership tracking for organizational structures</li>
 *   <li>Clinic location differentiation within facilities</li>
 * </ul>
 * </p>
 * 
 * <p>The composite ID string format is: "contactId-facilityId-clinicLocationNo-groupId"
 * where each component defaults to "0" if not specified.</p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgProviderData
 * @see MsgAddressBook
 */
public class ContactIdentifier implements Serializable {

    /**
     * The primary contact identifier (typically a provider number or user ID).
     */
    private String contactId;
    
    /**
     * The healthcare facility ID where this contact is located.
     */
    private int facilityId;
    
    /**
     * The specific clinic location number within the facility.
     */
    private int clinicLocationNo;
    
    /**
     * A globally unique identifier for the contact (optional).
     */
    private String contactUniqueId;
    
    /**
     * The composite ID string combining all identifier components.
     */
    private String compositeId;
    
    /**
     * The messenger group ID this contact belongs to (0 if not in a group).
     */
    private int groupId;

    /**
     * Default constructor initializing all IDs to their default values.
     * 
     * <p>Sets contactId to "0" and all numeric IDs to 0, representing
     * an uninitialized or root-level contact identifier.</p>
     */
    public ContactIdentifier() {
        this.contactId = "0";
        this.facilityId = 0;
        this.clinicLocationNo = 0;
        this.groupId = 0;
    }

    /**
     * Constructor that parses a composite ID string into its components.
     * 
     * @param compositeId The composite ID string in format "contactId-facilityId-clinicLocationNo-groupId"
     */
    public ContactIdentifier(String compositeId) {
        this();
        setCompositeId(compositeId);
    }

    /**
     * Gets the primary contact identifier.
     * 
     * @return The contact ID string (provider number or user ID)
     */
    public String getContactId() {
        return contactId;
    }

    /**
     * Sets the contact ID and updates the composite ID.
     * 
     * @param contactId The primary contact identifier to set
     */
    public void setContactId(String contactId) {
        setCompositeId(contactId, this.facilityId, this.clinicLocationNo);
    }

    /**
     * Gets the healthcare facility ID.
     * 
     * @return The facility ID where this contact is located
     */
    public int getFacilityId() {
        return facilityId;
    }

    /**
     * Sets the facility ID and updates the composite ID.
     * 
     * @param facilityId The healthcare facility ID to set
     */
    public void setFacilityId(int facilityId) {
        setCompositeId(this.contactId, facilityId, this.clinicLocationNo);
    }

    /**
     * Gets the clinic location number within the facility.
     * 
     * @return The clinic location number
     */
    public int getClinicLocationNo() {
        return clinicLocationNo;
    }

    /**
     * Sets the clinic location number and updates the composite ID.
     * 
     * @param clinicLocationNo The clinic location number to set
     */
    public void setClinicLocationNo(int clinicLocationNo) {
        setCompositeId(this.contactId, this.facilityId, clinicLocationNo);
    }

    /**
     * Gets the globally unique identifier for this contact.
     * 
     * @return The unique contact ID, or null if not set
     */
    public String getContactUniqueId() {
        return contactUniqueId;
    }

    /**
     * Sets the globally unique identifier for this contact.
     * 
     * @param contactUniqueId The unique identifier to set
     */
    public void setContactUniqueId(String contactUniqueId) {
        this.contactUniqueId = contactUniqueId;
    }

    /**
     * Gets the composite ID string combining all identifier components.
     * 
     * <p>If not already set, constructs the composite ID from the individual
     * components in the format "contactId-facilityId-clinicLocationNo".</p>
     * 
     * @return The composite ID string
     */
    public String getCompositeId() {
        if (compositeId == null) {
            compositeId = this.contactId + "-" + this.facilityId + "-" + this.clinicLocationNo;
        }
        return compositeId;
    }

    /**
     * Sets the composite ID using individual components.
     * 
     * @param contactId The primary contact identifier
     * @param facilityId The healthcare facility ID
     * @param clinicLocationNo The clinic location number
     */
    public void setCompositeId(String contactId, int facilityId, int clinicLocationNo) {
        setCompositeId(contactId.trim() + "-" + facilityId + "-" + clinicLocationNo);
    }

    /**
     * Parses and sets the composite ID string into individual components.
     * 
     * <p>A composite ID is the IDs in this object separated by dashes in the format:
     * "contactId-facilityId-clinicLocationNo-groupId"</p>
     * 
     * <p>The method handles various formats:
     * <ul>
     *   <li>Simple ID with no dashes: treated as contactId only</li>
     *   <li>Two components: contactId-facilityId</li>
     *   <li>Three components: contactId-facilityId-clinicLocationNo</li>
     *   <li>Four components: contactId-facilityId-clinicLocationNo-groupId</li>
     * </ul>
     * </p>
     * 
     * <p>Defaults for missing components: facilityId=0, clinicLocationNo=0, groupId=0</p>
     *
     * @param compositeId The composite ID string to parse
     */
    public void setCompositeId(String compositeId) {

        if (compositeId.contains("-")) {
            String[] theSplit = compositeId.split("-");
            // Always has at least contactId and facilityId
            this.contactId = theSplit[0].trim();
            this.facilityId = Integer.parseInt(theSplit[1].trim());
            // Optional clinic location number
            if (theSplit.length == 3) {
                this.clinicLocationNo = Integer.parseInt(theSplit[2].trim());
            }
            // Optional group ID
            if (theSplit.length == 4) {
                this.groupId = Integer.parseInt(theSplit[3].trim());
            }
        } else {
            // Simple ID with no facility or location info
            this.contactId = compositeId;
        }

        this.compositeId = compositeId;
    }

    /**
     * Gets the messenger group ID this contact belongs to.
     * 
     * @return The group ID, or 0 if not in a group
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * Sets the messenger group ID for this contact.
     * 
     * @param groupId The group ID to set (0 for no group)
     */
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }
}
