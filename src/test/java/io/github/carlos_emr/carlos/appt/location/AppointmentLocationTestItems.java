/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import io.github.carlos_emr.carlos.commn.model.LookupListItem;

/** Builds Location List items for the appointment location tests. */
final class AppointmentLocationTestItems {

    static final int LIST_ID = 40;

    private AppointmentLocationTestItems() {
    }

    static LookupListItem item(int id, String label, int displayOrder, boolean active) {
        LookupListItem item = new LookupListItem();
        item.setId(id);
        item.setLookupListId(LIST_ID);
        item.setLabel(label);
        item.setValue(label);
        item.setDisplayOrder(displayOrder);
        item.setActive(active);
        return item;
    }
}
