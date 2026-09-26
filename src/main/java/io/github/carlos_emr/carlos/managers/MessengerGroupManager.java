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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.model.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.GroupMembersDao;
import io.github.carlos_emr.carlos.commn.dao.GroupsDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;

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
     * Get all Messenger members from the local clinic.
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

        return allMembers;
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
     * Get all groups that contain all local members.
     * All members sorted alphabetically into groups of assigned groups.
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

        groupMembers = groupMembersDao.findLocalByGroupId(groupId);

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
            logger.warn("Ignoring non-local facility ID {} for provider {}", facilityId, providerNo);
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
     * All provider contacts (potential Messenger Members) from the local clinic.
     * This list is used in the Messenger Configuration to present potential members that can be enrolled into
     * the Messenger system.
     *
     * @param loggedInInfo
     * @return Map containing local provider data
     */
    public Map<String, List<MsgProviderData>> getAllMessengerContacts(LoggedInInfo loggedInInfo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        Map<String, List<MsgProviderData>> providersMap = new TreeMap<String, List<MsgProviderData>>();
        List<MsgProviderData> localMessengerContactList = getAllLocalMessengerContactList(loggedInInfo);
        providersMap.put("Local Providers", localMessengerContactList);
        return providersMap;
    }

    /**
     * All provider contacts (potential Messenger members) from the local server.
     * This list is used in the Messenger Configuration to present potential members that can be enrolled into
     * the Messenger system.
     *
     * <p>Providers with a negative provider number are excluded: {@code -1} is the system
     * account and other negative numbers mark deactivated providers.</p>
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
            if (isContactableProviderNo(provider.getProviderNo())
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
     * Add a new empty group for adding Messenger members.
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
     * Members will still remain Messenger members.
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
     * Make a provider into a Messenger member. Adding to a group is
     * optional
     *
     * <p>Idempotent: when the contact is already a member of {@code groupId} no row is
     * written and the existing membership id is returned. Callers that need to tell the
     * user "already a member" should ask {@link #isGroupMember} first.</p>
     *
     * @param loggedInInfo
     * @param contactIdentifier
     * @param groupId
     * @return group member ID (the existing one when the contact was already a member)
     */
    public int addMember(LoggedInInfo loggedInInfo, ContactIdentifier contactIdentifier, int groupId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        /*
         * groupMembers_tbl has no unique key on (facilityId, member_id, groupID), so this
         * check is the only thing preventing a second row for the same contact. A duplicate
         * row makes group messages fan out twice to the same recipient. Re-adding an
         * existing member is therefore a no-op that answers the existing row's id.
         */
        GroupMembers existing = findMembership(contactIdentifier, groupId);
        if (existing != null) {
            logger.debug("Messenger member already present in group {}; skipping insert", groupId);
            return existing.getId();
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
        if (groupId > 0 && findMembership(contactIdentifier, 0) == null) {
            GroupMembers registeredMember = new GroupMembers();
            BeanUtils.copyProperties(groupMembers, registeredMember);
            registeredMember.setGroupId(0);
            groupMembersDao.persist(registeredMember);
        }

        groupMembersDao.persist(groupMembers);

        return groupMembers.getId();
    }

    /**
     * Remove a member from Messenger membership.
     * Member is also removed from all groups.
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

        if (oscarCommLocations != null && !oscarCommLocations.isEmpty()) {
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

    /**
     * Report whether a contact is already a member of a group. Group {@code 0} is the general
     * Messenger membership registry, so {@code isGroupMember(info, contact, 0)} answers whether
     * the contact is a Messenger member at all.
     *
     * @param loggedInInfo the current user; requires {@code _admin} read
     * @param contactIdentifier the contact; only its contact id and facility id are compared
     * @param groupId the group to look in
     * @return {@code true} when a membership row already exists for that contact and group
     * @throws SecurityException if the user lacks {@code _admin} read
     */
    public boolean isGroupMember(LoggedInInfo loggedInInfo, ContactIdentifier contactIdentifier, int groupId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        return findMembership(contactIdentifier, groupId) != null;
    }

    /**
     * Whether a provider number may be offered as a Messenger contact. {@code -1} is the
     * system account, and administrators deactivate a provider by renumbering it to another
     * negative value, so no negative number is a real, reachable contact.
     */
    static boolean isContactableProviderNo(String providerNo) {
        return providerNo != null && !providerNo.isEmpty() && !providerNo.startsWith("-");
    }

    /**
     * Find an existing membership row for the contact in the given group. The lookup uses a copy
     * of the identifier so the caller's {@code groupId} is never mutated. When legacy duplicate
     * rows already exist the first one is returned (the DAO caps the query at one row).
     */
    private GroupMembers findMembership(ContactIdentifier contactIdentifier, int groupId) {
        ContactIdentifier lookup = new ContactIdentifier();
        lookup.setContactId(contactIdentifier.getContactId());
        lookup.setFacilityId(contactIdentifier.getFacilityId());
        lookup.setGroupId(groupId);
        GroupMembers groupMember = groupMembersDao.findByIdentity(lookup);
        return groupMember != null && groupMember.getId() != null ? groupMember : null;
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
