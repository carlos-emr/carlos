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

import ca.openosp.openo.commn.model.*;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.caisi_integrator.ws.CachedFacility;
import ca.openosp.openo.caisi_integrator.ws.CachedProvider;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdStringCompositePk;
import ca.openosp.openo.commn.dao.GroupMembersDao;
import ca.openosp.openo.commn.dao.GroupsDao;
import ca.openosp.openo.commn.dao.OscarCommLocationsDao;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ca.openosp.openo.messenger.data.ContactIdentifier;
import ca.openosp.openo.messenger.data.MsgProviderData;

import java.util.*;

@Service
public class MessengerGroupManager {

    @Autowired
    private SecurityInfoManager securityInfoManager;
    @Autowired
    private GroupMembersDao groupMembersDao;
    @Autowired
    private GroupsDao groupsDao;
    @Autowired
    private ProviderManager2 providerManager;
    @Autowired
    private FacilityManager facilityManager;
    @Autowired
    private OscarCommLocationsDao oscarCommLocationsDao;
    @Autowired
    private ProviderDao providerDao;

    private static Logger logger = MiscUtils.getLogger();

    /**
     * Get all the member group names and ids
     *
     * @param loggedInInfo
     * @return
     */
    public List<Groups> getGroups(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        int available = groupsDao.getCountAll();
        return groupsDao.findAll(0, available);
    }

    /**
     * Get ALL Oscar Messenger Members from ALL locations.
     * Organize the results in groups of location name.
     *
     * @param loggedInInfo
     * @return
     */
    public Map<String, List<MsgProviderData>> getAllMembers(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        Map<String, List<MsgProviderData>> allMembers = new TreeMap<String, List<MsgProviderData>>();

        List<MsgProviderData> localMembers = getAllLocalMembers(loggedInInfo);
        allMembers.put("Local Members", localMembers);

        Map<String, List<MsgProviderData>> remoteMembers = getAllRemoteMembers(loggedInInfo);
        allMembers.putAll(remoteMembers);

        return allMembers;
    }

    /**
     * All remote (integrated) members enrolled as a Oscar messenger contact.
     * Sorted alphabetically into groups of facility location.
     *
     * @param loggedInInfo
     * @return
     */
    public Map<String, List<MsgProviderData>> getAllRemoteMembers(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        Map<String, List<MsgProviderData>> remoteMembers = new TreeMap<String, List<MsgProviderData>>();

        if (!loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            logger.warn("Cannot retrieve remote providers contact list. Integrator is disabled.");
            return remoteMembers;
        }

        Facility facility = facilityManager.getDefaultFacility(loggedInInfo);

        try {
            List<CachedFacility> remoteFacilities = CaisiIntegratorManager.getRemoteFacilitiesExcludingCurrent(loggedInInfo, facility);
            List<GroupMembers> groupMembers = Collections.emptyList();
            for (CachedFacility remoteFacility : remoteFacilities) {
                groupMembers = groupMembersDao.findByFacilityId(remoteFacility.getIntegratorFacilityId());
                List<MsgProviderData> remoteMemberDataList = getMemberData(loggedInInfo, groupMembers);
                if (remoteMemberDataList.size() > 0) {
                    Collections.sort(remoteMemberDataList, new SortLastName());
                    remoteMembers.put(remoteFacility.getName(), remoteMemberDataList);
                }
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }

        return remoteMembers;
    }

    /**
     * All local members enrolled as a Oscar messenger contact.
     * Sorted alphabetically.
     *
     * @param loggedInInfo
     * @return
     */
    public List<MsgProviderData> getAllLocalMembers(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        // default facility Id for local members is 0
        List<GroupMembers> localMembers = groupMembersDao.findByFacilityId(0);

        // remove any duplicate providers.
        Map<String, GroupMembers> hashMap = null;
        for (GroupMembers localMember : localMembers) {
            if (hashMap == null) {
                hashMap = new HashMap<String, GroupMembers>();
            }

            hashMap.put(localMember.getProviderNo(), localMember);
        }

        if (hashMap != null && hashMap.size() > 0) {
            localMembers.clear();
            localMembers.addAll(hashMap.values());
        }

        List<MsgProviderData> localMemberData = getMemberData(loggedInInfo, localMembers);
        Collections.sort(localMemberData, new SortLastName());
        return localMemberData;
    }

    /**
     * Get all groups that contain all local and remote members.
     * All members from all local and remote locations sorted alphabetically into groups of assigned groups.
     *
     * @param loggedInInfo
     * @return
     */
    public Map<Groups, List<MsgProviderData>> getAllGroupsWithMembers(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        Map<Groups, List<MsgProviderData>> groupsMap = new TreeMap<Groups, List<MsgProviderData>>();
        List<Groups> groups = getGroups(loggedInInfo);
        for (Groups group : groups) {
            List<MsgProviderData> groupMembers = getGroupMembers(loggedInInfo, group.getId());

            groupsMap.put(group, groupMembers);
        }
        return groupsMap;
    }

    /**
     * Get all members contained in the given group id.
     *
     * @param loggedInInfo
     * @param groupId
     * @return
     */
    public List<MsgProviderData> getGroupMembers(LoggedInInfo loggedInInfo, int groupId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        List<GroupMembers> groupMembers = Collections.emptyList();

        /*
         *  get all group members if the Integrator is enabled.
         *  Otherwise just the local groups is good
         */
        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            groupMembers = groupMembersDao.findByGroupId(groupId);
        } else {
            groupMembers = groupMembersDao.findLocalByGroupId(groupId);
        }

        List<MsgProviderData> messengerContactList = getMemberData(loggedInInfo, groupMembers);
        Collections.sort(messengerContactList, new SortLastName());
        return messengerContactList;
    }

    /**
     * Get all the member data (name, location, id etc...) for each of the members in the given collection.
     *
     * @param loggedInInfo
     * @param groupMemberList
     * @return List<MsgProviderData>
     */
    private List<MsgProviderData> getMemberData(LoggedInInfo loggedInInfo, List<GroupMembers> groupMemberList) {
        List<MsgProviderData> memberDataList = new ArrayList<MsgProviderData>();
        for (GroupMembers groupMember : groupMemberList) {
            MsgProviderData messengerContact = getMemberData(loggedInInfo, groupMember);
            if (messengerContact != null) {
                memberDataList.add(messengerContact);
            }
        }
        return memberDataList;
    }

    /**
     * Get the member details (name, location, id etc...) for each of the given member.
     * Details are returned in a MsgProviderData object.
     *
     * @param loggedInInfo
     * @param groupMember
     * @return MsgProviderData
     */
    public MsgProviderData getMemberData(LoggedInInfo loggedInInfo, GroupMembers groupMember) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        return getMemberData(loggedInInfo, groupMember.getFacilityId(), groupMember.getProviderNo());
    }

    public MsgProviderData getMemberData(LoggedInInfo loggedInInfo, ContactIdentifier contactIdentifier) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        return getMemberData(loggedInInfo, contactIdentifier.getFacilityId(), contactIdentifier.getContactId());
    }

    private MsgProviderData getMemberData(LoggedInInfo loggedInInfo, int facilityId, String providerNo) {
        MsgProviderData messengerContact = null;
        if (facilityId == 0 || facilityId == loggedInInfo.getCurrentFacility().getId()) {
            messengerContact = getLocalMember(loggedInInfo, providerNo);
        } else {
            messengerContact = getRemoteMember(loggedInInfo, providerNo, facilityId);
        }
        return messengerContact;
    }

    /**
     * Get the local member details(name, location, id etc...) for the given Oscar Provider Number
     *
     * @param loggedInInfo
     * @param providerNo
     * @return MsgProviderData
     */
    public MsgProviderData getLocalMember(LoggedInInfo loggedInInfo, String providerNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        MsgProviderData msgProviderData = null;
        Provider provider = providerManager.getProviderIfActive(loggedInInfo, providerNo);
        if (provider != null) {
            msgProviderData = new MsgProviderData(provider);
            msgProviderData.getId().setClinicLocationNo(getCurrentLocationId());
        }
        return msgProviderData;
    }

    /**
     * Get a remote contact member details(name, location, id etc...) based on the remote Provider Number and Facility Id
     * Returns data for the providers located at the given Facility data.
     *
     * @param loggedInInfo
     * @param providerNo
     * @param facilityId
     * @return MsgProviderData; NULL if retrieval fails
     */
    public MsgProviderData getRemoteMember(LoggedInInfo loggedInInfo, String providerNo, int facilityId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        MsgProviderData messengerContact = null;
        CachedProvider cachedProvider = null;
        Facility facility = facilityManager.getDefaultFacility(loggedInInfo);
        FacilityIdStringCompositePk facilityCompositePk = new FacilityIdStringCompositePk();
        facilityCompositePk.setCaisiItemId(providerNo);
        facilityCompositePk.setIntegratorFacilityId(facilityId);

        try {
            cachedProvider = CaisiIntegratorManager.getProvider(loggedInInfo, facility, facilityCompositePk);
        } catch (Exception e) {
            logger.error("Error while getting remote providers list from Integrator. Could be offline. ", e);
        }

        if (cachedProvider != null) {
            messengerContact = new MsgProviderData(cachedProvider);
        }

        return messengerContact;
    }

    /**
     * Helper Method:
     * Check a list of contacts for membership status against the members and groups table
     * If the user is a member the MsgProviderData.isMember parameter will be set to true.
     */
    private void checkMembership(List<MsgProviderData> msgProviderDataList) {
        List<GroupMembers> groupMembers = groupMembersDao.findAll(0, groupMembersDao.getCountAll());

        if (groupMembers == null) {
            return;
        }

        for (MsgProviderData msgProviderData : msgProviderDataList) {
            inner:
            for (GroupMembers groupMember : groupMembers) {
                if (msgProviderData.getId().getContactId().equals(groupMember.getProviderNo())
                        && msgProviderData.getId().getFacilityId() == groupMember.getFacilityId()) {
                    msgProviderData.setMember(Boolean.TRUE);
                    msgProviderData.getId().setGroupId(groupMember.getGroupId());
                    continue inner;
                }
            }
        }
    }

    /**
     * All providers contacts (potential Oscar Messenger Members) from the local Oscar
     * AND all the Integrated clinics that are connected.
     * This list is used in the Messenger Configuration to present potential members that can be enrolled into
     * the Oscar Messenger system.
     *
     * @param loggedInInfo
     * @return Map of provider data grouped by facility
     */
    public Map<String, List<MsgProviderData>> getAllMessengerContacts(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        Map<String, List<MsgProviderData>> providersMap = new TreeMap<String, List<MsgProviderData>>();
        List<MsgProviderData> localMessengerContactList = getAllLocalMessengerContactList(loggedInInfo);
        Map<String, List<MsgProviderData>> remoteProviders = getAllRemoteMessengerContactList(loggedInInfo);
        providersMap.put("Local Providers", localMessengerContactList);
        providersMap.putAll(remoteProviders);
        return providersMap;
    }

    /**
     * All providers contacts (potential Oscar Messenger Members) from the local Oscar server.
     * This list is used in the Messenger Configuration to present potential members that can be enrolled into
     * the Oscar Messenger system.
     *
     * @param loggedInInfo
     * @return List<MsgProviderData>
     */
    public List<MsgProviderData> getAllLocalMessengerContactList(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        List<MsgProviderData> messengerContactList = new ArrayList<MsgProviderData>();
        List<Provider> localProviders = providerManager.getProviders(loggedInInfo, Boolean.TRUE);

        for (Provider provider : localProviders) {
            if (!provider.getProviderNo().equals("-1")
                    && provider.getLastName() != null
                    && !provider.getLastName().isEmpty()) {
                MsgProviderData messengerContact = new MsgProviderData(provider);
                messengerContactList.add(messengerContact);
            }
        }
        checkMembership(messengerContactList);
        /*
         * LocationNo: not sure why. It may be related to "multisites", but then how
         * is each providers identified??  Adding it anyway.
         */
        setLocalLocationId(messengerContactList);
        Collections.sort(messengerContactList, new SortLastName());
        return messengerContactList;
    }

    /**
     * All providers contacts (potential Oscar Messenger Members) from ALL remote Integrated Facilities (remote clinics).
     * This list is used in the Messenger Configuration to present potential members that can be enrolled into
     * the Oscar Messenger system.
     * Sorted by facility name
     *
     * @param loggedInInfo
     * @return
     */
    public Map<String, List<MsgProviderData>> getAllRemoteMessengerContactList(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        Map<String, List<MsgProviderData>> providersMap = new TreeMap<String, List<MsgProviderData>>();

        if (!loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            logger.debug("Cannot retrieve remote providers contact list. Integrator is disabled.");
            return providersMap;
        }

        Facility facility = facilityManager.getDefaultFacility(loggedInInfo);
        List<CachedFacility> remoteFacilities = Collections.emptyList();
        List<CachedProvider> remoteProviders = Collections.emptyList();

        try {
            remoteFacilities = CaisiIntegratorManager.getRemoteFacilitiesExcludingCurrent(loggedInInfo, facility);
            remoteProviders = CaisiIntegratorManager.getAllProviders(loggedInInfo, facility);
        } catch (Exception e) {
            logger.error("Error while getting remote providers list from Integrator ", e);
        }

        // re-sort lists by facility.
        for (CachedFacility remoteFacility : remoteFacilities) {
            Integer facilityId = remoteFacility.getIntegratorFacilityId();
            String facilityName = remoteFacility.getName();
            List<MsgProviderData> messengerContactList = new ArrayList<MsgProviderData>();

            for (CachedProvider remoteProvider : remoteProviders) {
                if (facilityId == remoteProvider.getFacilityIdStringCompositePk().getIntegratorFacilityId()
                        && !remoteProvider.getFacilityIdStringCompositePk().getCaisiItemId().equals("-1")
                        && remoteProvider.getLastName() != null
                        && !remoteProvider.getLastName().isEmpty()) {
                    MsgProviderData messengerContact = new MsgProviderData(remoteProvider);
                    messengerContactList.add(messengerContact);
                }
            }
            checkMembership(messengerContactList);
            Collections.sort(messengerContactList, new SortLastName());
            providersMap.put(facilityName, messengerContactList);
        }

        return providersMap;
    }

    /**
     * Add a new empty Group for adding Oscar messenger members.
     *
     * @param loggedInInfo
     * @param groupName
     * @param parentId
     * @return the new Group ID
     */
    public int addGroup(LoggedInInfo loggedInInfo, String groupName, int parentId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        Groups group = new Groups();
        group.setGroupDesc(groupName);
        group.setParentId(parentId);
        groupsDao.persist(group);
        return group.getId();
    }

    /**
     * Remove all members from the given group and delete it from the database.
     * Members will still remain Oscar messenger members.
     *
     * @param loggedInInfo
     * @param groupId
     * @return
     */
    public boolean removeGroup(LoggedInInfo loggedInInfo, int groupId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        boolean removed = false;
        if (groupsDao.remove(groupId)) {
            // remove all members from this group id
            List<GroupMembers> groupMembers = groupMembersDao.findByGroupId(groupId);
            for (GroupMembers groupMember : groupMembers) {
                groupMember.setGroupId(0);
                groupMembersDao.merge(groupMember);
            }

            removed = Boolean.TRUE;
        }
        return removed;
    }

    /**
     * Make an Oscar Provider from any location into an Oscar Messenger Member.  Adding to a group is
     * optional
     *
     * @param loggedInInfo
     * @param contactIdentifier
     * @param groupId
     * @return group member ID
     */
    public int addMember(LoggedInInfo loggedInInfo, ContactIdentifier contactIdentifier, int groupId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        GroupMembers groupMembers = new GroupMembers();
        groupMembers.setFacilityId(contactIdentifier.getFacilityId());
        groupMembers.setGroupId(groupId);
        groupMembers.setProviderNo(contactIdentifier.getContactId());
        groupMembers.setClinicLocationNo(contactIdentifier.getClinicLocationNo());

        /*
         * A general membership registry with group id=0
         * needs to be added if this member was added directly into a group.
         * Indicated by a groupId greater than 0.
         * But first check if the general membership exists before adding.
         */
        if (groupId > 0 && !isRegistered(contactIdentifier)) {
            GroupMembers registeredMember = new GroupMembers();
            BeanUtils.copyProperties(groupMembers, registeredMember);
            registeredMember.setGroupId(0);
            groupMembersDao.persist(registeredMember);
        }

        groupMembersDao.persist(groupMembers);

        return groupMembers.getId();
    }

    /**
     * Remove a Member - from any location - from Oscar Messenger membership.
     * Member is also removed from all Groups.
     *
     * @param loggedInInfo
     * @param contactIdentifier
     * @return
     */
    public boolean removeMember(LoggedInInfo loggedInInfo, ContactIdentifier contactIdentifier) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        List<GroupMembers> groupMembers = groupMembersDao.findByProviderNumberAndFacilityId(contactIdentifier.getContactId(), contactIdentifier.getFacilityId());
        boolean removed = false;
        for (GroupMembers groupMember : groupMembers) {
            removed = groupMembersDao.remove(groupMember.getId());
        }
        return removed;
    }

    /**
     * Remove a messenger member from any given group.
     * Does not remove member from other groups or from the main messenger membership registry.
     */
    public boolean removeGroupMember(LoggedInInfo loggedInInfo, ContactIdentifier contactIdentifier) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        List<GroupMembers> groupMembers = groupMembersDao.findGroupMember(contactIdentifier.getContactId(), contactIdentifier.getGroupId());
        boolean removed = false;
        for (GroupMembers groupMember : groupMembers) {
            removed = groupMembersDao.remove(groupMember.getId());
        }
        return removed;
    }

    public int getCurrentLocationId() {
        List<OscarCommLocations> oscarCommLocations = oscarCommLocationsDao.findByCurrent1(1);
        Integer oscarCommLocationsID = null;

        if (oscarCommLocations != null) {
            oscarCommLocationsID = oscarCommLocations.get(0).getId();
        }

        if (oscarCommLocationsID == null) {
            oscarCommLocationsID = 0;
        }

        return oscarCommLocationsID;
    }

    private void setLocalLocationId(List<MsgProviderData> msgProviderDataList) {
        int currentLocationId = getCurrentLocationId();
        for (MsgProviderData msgProviderData : msgProviderDataList) {
            msgProviderData.getId().setClinicLocationNo(currentLocationId);
        }
    }

    private boolean isRegistered(ContactIdentifier contactIdentifier) {
        //override the group id with 0 to ensure registered status
        int groupId = contactIdentifier.getGroupId();
        contactIdentifier.setGroupId(0);
        // pass to the database for validation
        GroupMembers groupMember = groupMembersDao.findByIdentity(contactIdentifier);
        // set the group id back to the original.
        contactIdentifier.setGroupId(groupId);
        return groupMember != null && groupMember.getId() != null;
    }

    public boolean checkProviderStatus(String providerNo) {
        boolean status = Boolean.FALSE;
        Provider provider = providerDao.getProvider(providerNo);
        if ("1".equals(provider.getStatus())) {
            status = Boolean.TRUE;
        }
        return status;
    }

    /**
     * Helper class.
     * Sort MsgProviderData by last name.
     */
    private class SortLastName implements Comparator<MsgProviderData> {
        @Override
        public int compare(MsgProviderData o1, MsgProviderData o2) {
            if (o1 == null || o2 == null) {
                return -1;
            }
            return o1.getLastName().compareTo(o2.getLastName());
        }
    }

}
