/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appt.location;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.config.CacheConfig;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Manages the appointment Location List from Administration &gt; Schedule &gt; Appointment Location
 * Setting: add (one or a pasted list), rename, reorder, style, deactivate and reactivate.
 *
 * <p><b>Privileges.</b> Viewing needs read, every change write, and deactivating delete, on
 * {@code _admin.schedule} (the same object as the status settings) or {@code _admin}. Privileges
 * do not inherit, so either object will do. Each method checks for itself, so the service is safe
 * to call from anywhere, not only from {@code AppointmentLocationSettings2Action}.</p>
 *
 * <p><b>Names.</b> A location's name is at most {@link AppointmentLocationResolver#LABEL_MAX_LENGTH}
 * characters, because a booking saves a copy of it in {@code appointment.location}, and no two
 * active locations share a name (ignoring case and surrounding spaces), because the dropdown and a
 * patient's appointment history could not tell them apart. Renaming does not rewrite appointments
 * already booked: they keep the name they were booked with.</p>
 *
 * <p><b>Items.</b> Locations are never deleted, only deactivated, so the code stored on past
 * appointments always names an item. Every change resolves the posted id against this list, so an
 * id from another lookup list is refused.</p>
 *
 * <p>Each change runs in one transaction, evicts the lookup-list cache after commit, and writes an
 * audit row naming the item id and the field changed; location names are clinic configuration,
 * not patient data, and are not logged either.</p>
 *
 * @since 2026-11-04
 */
@Service
public class AppointmentLocationService {

    /** The most lines one paste may add, so a mistaken paste cannot flood the list. */
    public static final int PASTE_MAX_LINES = 100;

    private static final String SCHEDULE_ADMIN = "_admin.schedule";
    private static final String ADMIN = "_admin";
    /** Width of {@code LookupListItem.value}; locations store a readable copy of their name there. */
    private static final int VALUE_MAX_LENGTH = 50;
    /** Width of {@code LookupListItem.createdBy}. */
    private static final int CREATED_BY_MAX_LENGTH = 8;

    private final LookupListDao lookupListDao;
    private final LookupListItemDao lookupListItemDao;
    private final SecurityInfoManager securityInfoManager;
    private final AppointmentLocationResolver resolver;

    public AppointmentLocationService(LookupListDao lookupListDao, LookupListItemDao lookupListItemDao,
                                      SecurityInfoManager securityInfoManager, AppointmentLocationResolver resolver) {
        this.lookupListDao = lookupListDao;
        this.lookupListItemDao = lookupListItemDao;
        this.securityInfoManager = securityInfoManager;
        this.resolver = resolver;
    }

    /**
     * The outcome of adding pasted locations.
     *
     * @param added int how many locations were added
     * @param skipped int how many lines named a location that is already active, or repeated an
     *                earlier line
     */
    public record AddResult(int added, int skipped) {
    }

    /**
     * Loads the Location List for the settings page.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs read
     * @return AppointmentLocationList the list
     * @throws SecurityException if the user lacks read
     */
    public AppointmentLocationList load(LoggedInInfo loggedInInfo) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.READ);
        return resolver.load();
    }

    /**
     * Adds one location at the end of the booking order.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param name String the name, which is trimmed
     * @return LookupListItem the new location
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if the name is blank, too long, or already
     *         an active location's
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public LookupListItem addLocation(LoggedInInfo loggedInInfo, String name) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        String label = requireValidName(name);
        LookupList list = findOrCreateList(loggedInInfo);
        AppointmentLocationList locations = freshList(list);
        requireNameFree(locations, label, null);
        LookupListItem item = persistItem(loggedInInfo, list, label, nextDisplayOrder(locations));
        audit(loggedInInfo, "addLocation", "lookupListItem id=" + item.getId() + " added");
        return item;
    }

    /**
     * Adds every location in a pasted list, one name per line, in order. Blank lines are ignored;
     * a name that an active location already has, or that an earlier line repeats, is skipped and
     * counted. Nothing is added unless every line is acceptable.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param pasted String the pasted names, one per line
     * @return AddResult how many were added and skipped
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if the paste holds no name, more than
     *         {@link #PASTE_MAX_LINES} names, or a name that is too long
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public AddResult addLocations(LoggedInInfo loggedInInfo, String pasted) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        List<String> names = new ArrayList<>();
        for (String line : StringUtils.defaultString(pasted).split("\\R")) {
            String label = line.trim();
            if (!label.isEmpty()) {
                if (label.length() > AppointmentLocationResolver.LABEL_MAX_LENGTH) {
                    throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.nameTooLong",
                            String.valueOf(AppointmentLocationResolver.LABEL_MAX_LENGTH));
                }
                names.add(label);
            }
        }
        if (names.isEmpty()) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.nameRequired", null);
        }
        if (names.size() > PASTE_MAX_LINES) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.pasteTooLong",
                    String.valueOf(PASTE_MAX_LINES));
        }

        LookupList list = findOrCreateList(loggedInInfo);
        AppointmentLocationList locations = freshList(list);
        int order = nextDisplayOrder(locations);
        Set<String> seen = new LinkedHashSet<>();
        int added = 0;
        for (String label : names) {
            if (!seen.add(AppointmentLocationList.normaliseName(label)) || locations.findActiveByName(label, null) != null) {
                continue;
            }
            persistItem(loggedInInfo, list, label, order++);
            added++;
        }
        int skipped = names.size() - added;
        audit(loggedInInfo, "addLocations", added + " locations added, " + skipped + " skipped");
        return new AddResult(added, skipped);
    }

    /**
     * Renames a location. Appointments already booked keep the name they were booked with.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param id int the location's LookupListItem id
     * @param name String the new name, which is trimmed
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if there is no such location, or the name
     *         is blank, too long, or another active location's
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public void rename(LoggedInInfo loggedInInfo, int id, String name) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        String label = requireValidName(name);
        LookupList list = requireList();
        LookupListItem item = requireItem(list, id);
        if (item.isActive()) {
            requireNameFree(freshList(list), label, item.getId());
        }
        item.setLabel(label);
        lookupListItemDao.merge(item);
        audit(loggedInInfo, "rename", "lookupListItem id=" + id + " name updated");
    }

    /**
     * Sets or clears a location's chip colour.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param id int the location's LookupListItem id
     * @param colour String a {@code #rrggbb} colour, or blank to use the default chip colour
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if there is no such location or the colour
     *         is neither blank nor {@code #rrggbb}
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public void updateColour(LoggedInInfo loggedInInfo, int id, String colour) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        String value;
        try {
            value = AppointmentLocationStyle.normaliseColour(colour);
        } catch (IllegalArgumentException e) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.invalidColour", null);
        }
        LookupListItem item = requireItem(requireList(), id);
        item.setColour(value);
        lookupListItemDao.merge(item);
        audit(loggedInInfo, "updateColour", "lookupListItem id=" + id + " colour " + (value == null ? "cleared" : "updated"));
    }

    /**
     * Sets or clears a location's chip icon.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param id int the location's LookupListItem id
     * @param icon String one of {@link AppointmentLocationStyle#ICONS}, or blank to use the default
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if there is no such location or the icon is
     *         neither blank nor whitelisted
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public void updateIcon(LoggedInInfo loggedInInfo, int id, String icon) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        String value;
        try {
            value = AppointmentLocationStyle.normaliseIcon(icon);
        } catch (IllegalArgumentException e) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.invalidIcon", null);
        }
        LookupListItem item = requireItem(requireList(), id);
        item.setIcon(value);
        lookupListItemDao.merge(item);
        audit(loggedInInfo, "updateIcon", "lookupListItem id=" + id + " icon " + (value == null ? "cleared" : "updated"));
    }

    /**
     * Moves an active location one place along the booking order, renumbering the active
     * locations 1..n so repeated display orders cannot stop it moving.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param id int the location's LookupListItem id
     * @param up boolean true to move it towards the top of the dropdown
     * @return boolean true if it moved, false if it is already at that end
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if there is no such active location
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public boolean move(LoggedInInfo loggedInInfo, int id, boolean up) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        LookupList list = requireList();
        LookupListItem item = requireItem(list, id);
        if (!item.isActive()) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.notFound", null);
        }
        List<LookupListItem> ordered = new ArrayList<>(freshList(list).getActiveItems());
        int from = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId() == id) {
                from = i;
                break;
            }
        }
        int to = up ? from - 1 : from + 1;
        if (from < 0 || to < 0 || to >= ordered.size()) {
            return false;
        }
        ordered.add(to, ordered.remove(from));
        for (int i = 0; i < ordered.size(); i++) {
            LookupListItem each = ordered.get(i);
            if (each.getDisplayOrder() != i + 1) {
                each.setDisplayOrder(i + 1);
                lookupListItemDao.merge(each);
            }
        }
        audit(loggedInInfo, "move", "lookupListItem id=" + id + " moved " + (up ? "up" : "down"));
        return true;
    }

    /**
     * Deactivates a location: new bookings no longer offer it, and appointments booked with it
     * keep it. Deactivating the last active location turns the booking dropdown off again.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs delete
     * @param id int the location's LookupListItem id
     * @throws SecurityException if the user lacks delete
     * @throws AppointmentLocationChangeRefusedException if there is no such location
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public void deactivate(LoggedInInfo loggedInInfo, int id) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.DELETE);
        LookupListItem item = requireItem(requireList(), id);
        if (item.isActive()) {
            item.setActive(false);
            lookupListItemDao.merge(item);
            audit(loggedInInfo, "deactivate", "lookupListItem id=" + id + " deactivated");
        }
    }

    /**
     * Makes a deactivated location bookable again, at the end of the booking order.
     *
     * @param loggedInInfo LoggedInInfo the current user, who needs write
     * @param id int the location's LookupListItem id
     * @throws SecurityException if the user lacks write
     * @throws AppointmentLocationChangeRefusedException if there is no such location or an active
     *         location already has its name
     */
    @Transactional
    @CacheEvict(value = CacheConfig.LOOKUP_LISTS, allEntries = true)
    public void restore(LoggedInInfo loggedInInfo, int id) {
        requirePrivilege(loggedInInfo, SecurityInfoManager.WRITE);
        LookupList list = requireList();
        LookupListItem item = requireItem(list, id);
        if (item.isActive()) {
            return;
        }
        AppointmentLocationList locations = freshList(list);
        requireNameFree(locations, item.getLabel(), item.getId());
        // Worked out before the item turns active: the snapshot shares the managed entity, so
        // afterwards the item would count its own old order.
        int displayOrder = nextDisplayOrder(locations);
        item.setActive(true);
        item.setDisplayOrder(displayOrder);
        lookupListItemDao.merge(item);
        audit(loggedInInfo, "restore", "lookupListItem id=" + id + " reactivated");
    }

    private boolean hasEither(LoggedInInfo loggedInInfo, String privilege) {
        return hasLocationPrivilege(securityInfoManager, loggedInInfo, privilege);
    }

    /**
     * The Location List privilege rule, shared with the settings action so both gate the same way:
     * the privilege on {@code _admin.schedule} or on {@code _admin}. Privileges do not inherit, so
     * either object will do.
     *
     * @param securityInfoManager SecurityInfoManager the privilege checker
     * @param loggedInInfo LoggedInInfo the current user
     * @param privilege String a {@link SecurityInfoManager} privilege such as {@code WRITE}
     * @return boolean true if the user holds the privilege on either object
     */
    public static boolean hasLocationPrivilege(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo,
                                               String privilege) {
        return securityInfoManager.hasPrivilege(loggedInInfo, SCHEDULE_ADMIN, privilege, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, ADMIN, privilege, null);
    }

    private void requirePrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!hasEither(loggedInInfo, privilege)) {
            throw new SecurityException("missing required sec object (_admin.schedule)");
        }
    }

    private static String requireValidName(String name) {
        String label = StringUtils.trimToEmpty(name);
        if (label.isEmpty()) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.nameRequired", null);
        }
        if (label.length() > AppointmentLocationResolver.LABEL_MAX_LENGTH) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.nameTooLong",
                    String.valueOf(AppointmentLocationResolver.LABEL_MAX_LENGTH));
        }
        return label;
    }

    private static void requireNameFree(AppointmentLocationList locations, String label, Integer exceptId) {
        if (locations.findActiveByName(label, exceptId) != null) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.nameInUse", label.trim());
        }
    }

    private LookupList requireList() {
        LookupList list = lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME);
        if (list == null) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.notFound", null);
        }
        return list;
    }

    /* The migration seeds the list; a database that lost the row gets it back on the first add. */
    private LookupList findOrCreateList(LoggedInInfo loggedInInfo) {
        LookupList list = lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME);
        if (list != null) {
            return list;
        }
        list = new LookupList();
        list.setName(AppointmentLocationResolver.LIST_NAME);
        list.setListTitle("Appointment Locations");
        list.setDescription("Places an appointment can be booked");
        list.setActive(true);
        list.setCreatedBy(createdBy(loggedInInfo));
        list.setDateCreated(new Date());
        lookupListDao.persist(list);
        return list;
    }

    /*
     * The items as stored now, read inside this transaction rather than from the lookup-list cache,
     * so a name check or a reorder never works from a stale copy.
     */
    private AppointmentLocationList freshList(LookupList list) {
        List<LookupListItem> items = new ArrayList<>(lookupListItemDao.findByLookupListId(list.getId(), true));
        items.addAll(lookupListItemDao.findByLookupListId(list.getId(), false));
        return AppointmentLocationList.of(items, false);
    }

    /* Finds the item inside this transaction, refusing an id that names no item of this list. */
    private LookupListItem requireItem(LookupList list, int id) {
        LookupListItem item = id > 0 ? lookupListItemDao.find(id) : null;
        if (item == null || item.getLookupListId() == null || !item.getLookupListId().equals(list.getId())) {
            throw new AppointmentLocationChangeRefusedException("admin.appt.location.msg.notFound", null);
        }
        return item;
    }

    private static int nextDisplayOrder(AppointmentLocationList locations) {
        return locations.getActiveItems().stream().mapToInt(LookupListItem::getDisplayOrder).max().orElse(0) + 1;
    }

    private LookupListItem persistItem(LoggedInInfo loggedInInfo, LookupList list, String label, int displayOrder) {
        LookupListItem item = new LookupListItem();
        item.setLookupListId(list.getId());
        item.setLabel(label);
        item.setValue(StringUtils.left(label, VALUE_MAX_LENGTH));
        item.setActive(true);
        item.setDisplayOrder(displayOrder);
        item.setCreatedBy(createdBy(loggedInInfo));
        item.setDateCreated(new Date());
        lookupListItemDao.persist(item);
        return item;
    }

    private static String createdBy(LoggedInInfo loggedInInfo) {
        String providerNo = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        return StringUtils.left(StringUtils.defaultIfBlank(providerNo, "-1"), CREATED_BY_MAX_LENGTH);
    }

    /**
     * Writes the audit row for a saved change: the item id and the field, no names or patient data.
     *
     * @param loggedInInfo LoggedInInfo the current user
     * @param operation String the change applied
     * @param data String what changed
     */
    protected void audit(LoggedInInfo loggedInInfo, String operation, String data) {
        LogAction.addLogSynchronous(loggedInInfo, "AppointmentLocationService." + operation, data);
    }
}
