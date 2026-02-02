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

/**
 * Legacy data class for managing reply message recipients across local and remote healthcare facilities.
 * 
 * <p>This class was originally designed to organize message recipients into local and remote
 * provider lists, allowing the messaging system to differentiate between providers within
 * the same facility and those at connected remote facilities.</p>
 * 
 * <p>The class maintains two separate lists:
 * <ul>
 *   <li>localList: Providers within the current facility/location</li>
 *   <li>remoList: Providers at remote connected facilities</li>
 * </ul>
 * </p>
 * 
 * @deprecated This class is no longer needed in the current messaging implementation.
 *             The functionality has been replaced by improved recipient management
 *             in the MessengerManager and related classes.
 * @version 1.0
 * @since 2002
 */
@Deprecated
public class MsgReplyMessageData {
    /**
     * List of local providers within the current facility.
     */
    public java.util.ArrayList<MsgProviderData> localList = null;
    
    /**
     * List of remote providers at connected facilities.
     */
    public java.util.ArrayList<MsgProviderData> remoList = null;
    
    /**
     * The current location/facility ID for determining local vs remote.
     */
    String localId;


    /**
     * Initializes the local and remote provider lists.
     * 
     * <p>This method creates new ArrayLists for both local and remote providers
     * and retrieves the current location ID from MsgMessageData to determine
     * which providers are local versus remote.</p>
     * 
     * <p>Note: Method name "estLists" appears to be shortened from "establishLists"</p>
     */
    public void estLists() {
        localList = new java.util.ArrayList<MsgProviderData>();
        remoList = new java.util.ArrayList<MsgProviderData>();

        MsgMessageData messageData = new MsgMessageData();
        localId = messageData.getCurrentLocationId() + "";
    }

    /**
     * Adds a provider to either the local or remote list based on location.
     * 
     * <p>This method creates a new MsgProviderData object and adds it to the
     * appropriate list (local or remote) based on whether the provider's location
     * matches the current local facility ID.</p>
     * 
     * <p>Note: There appears to be a bug where setContactId is called twice with
     * different values - the second call with locoId overwrites the first with proId.</p>
     * 
     * @param proId The provider ID to add
     * @param locoId The location/facility ID of the provider
     */
    public void add(String proId, String locoId) {
        if (locoId.equals(localId)) {
            // Provider is at the local facility
            MsgProviderData providerData = new MsgProviderData();
            providerData.getId().setContactId(proId);
            // Bug: This overwrites the provider ID with location ID
            providerData.getId().setContactId(locoId);
            localList.add(providerData);
        } else {
            // Provider is at a remote facility
            MsgProviderData providerData = new MsgProviderData();
            providerData.getId().setContactId(proId);
            // Bug: This overwrites the provider ID with location ID
            providerData.getId().setContactId(locoId);
            remoList.add(providerData);
        }
    }

    /**
     * Checks if a provider exists in the remote list.
     * 
     * <p>This method searches through the remote provider list to determine if a
     * provider with the specified ID and location already exists. The search
     * terminates early once a match is found by setting the loop counter to
     * the list size.</p>
     * 
     * @param proId The provider ID to search for
     * @param locoId The location/facility ID to match
     * @return true if the provider exists in the remote list, false otherwise
     */
    public boolean remoContains(String proId, String locoId) {
        boolean retval = false;
        if (remoList != null) {
            for (int i = 0; i < remoList.size(); i++) {
                MsgProviderData pD = remoList.get(i);
                if ((proId.equals(pD.getId().getContactId())) && (locoId.equals(pD.getId().getClinicLocationNo() + ""))) {
                    retval = true;
                    // Early exit by setting i to list size
                    i = remoList.size();
                }
            }
        }
        return retval;
    }


}
