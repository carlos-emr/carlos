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

package io.github.carlos_emr.carlos.admin.lookUpLists;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for managing configurable lookup lists in CARLOS EMR.
 *
 * <p>Provides CRUD operations for lookup list items used throughout the application
 * (e.g., dropdown options). Supports managing individual lists, reordering items,
 * adding new items with auto-generated UUIDs, and removing items.
 *
 * <p>Routes via the "method" request parameter:
 * <ul>
 *   <li>manage - Lists all active lookup lists</li>
 *   <li>order - Updates display order of a lookup list item</li>
 *   <li>add - Adds a new item to a lookup list</li>
 *   <li>remove - Removes an item from a lookup list</li>
 *   <li>(default) - Displays a single lookup list by name</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.managers.LookupListManager
 * @since 2026-03-17
 */
public class LookupListManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);

    public LookupListManager2Action() {
        super();
    }

    /**
     * Routes to the appropriate handler method based on the "method" request parameter.
     *
     * @return String the Struts2 result name
     */
    public String execute() {
        String method = request.getParameter("method");
        if ("manage".equals(method)) {
            return manage();
        } else if ("order".equals(method)) {
            return order();
        } else if ("add".equals(method)) {
            return add();
        } else if ("remove".equals(method)) {
            return remove();
        }
        return manageSingle();
    }

    /**
     * Displays a single lookup list identified by the "listName" request parameter.
     *
     * @return String SUCCESS with the lookup list set as a request attribute
     */
    @SuppressWarnings("unused")
    public String manageSingle() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String listName = request.getParameter("listName");
        if (listName != null && !listName.isEmpty()) {
            request.setAttribute("lookupListSingle", lookupListManager.findLookupListByName(loggedInInfo, listName));
        }

        return SUCCESS;
    }

    /**
     * Lists all active lookup lists and sets them as a request attribute.
     *
     * @return String SUCCESS with all active lookup lists
     */
    @SuppressWarnings("unused")
    public String manage() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        request.setAttribute("lookupLists", lookupListManager.findAllActiveLookupLists(loggedInInfo));

        return SUCCESS;
    }

    @SuppressWarnings("unused")
    public String order() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String lookupListItemId = request.getParameter("lookupListItemId");
        String lookupListItemDisplayOrder = request.getParameter("lookupListItemDisplayOrder");

        if (lookupListItemId != null && !lookupListItemId.isEmpty() &&
                lookupListItemDisplayOrder != null && !lookupListItemDisplayOrder.isEmpty()) {

            lookupListManager.updateLookupListItemDisplayOrder(loggedInInfo, Integer.parseInt(lookupListItemId),
                    Integer.parseInt(lookupListItemDisplayOrder));

        }

        return SUCCESS;
    }

    @SuppressWarnings("unused")
    public String add() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String lookupListItemLabel = request.getParameter("lookupListItemLabel");
        String lookupListId = request.getParameter("lookupListId");
        String user = (String) request.getSession().getAttribute("user");
        LookupListItem lookupListItem;
        int lookupListIdInteger;
        List<LookupListItem> lookupListItems;

        if (user == null) {
            user = "";
        }

        if (lookupListItemLabel != null && !lookupListItemLabel.isEmpty() &&
                lookupListId != null && !lookupListId.isEmpty()) {

            lookupListIdInteger = Integer.parseInt(lookupListId);
            lookupListItems = lookupListManager.findLookupListItemsByLookupListId(loggedInInfo, lookupListIdInteger);
            lookupListItem = new LookupListItem();
            lookupListItem.setActive(true);
            lookupListItem.setCreatedBy(user);

            lookupListItem.setDisplayOrder(1);

            if (!lookupListItems.isEmpty()) {
                lookupListItem.setDisplayOrder(lookupListItems.get(lookupListItems.size() - 1).getDisplayOrder() + 1);
            }

            lookupListItem.setLabel(lookupListItemLabel);
            lookupListItem.setLookupListId(lookupListIdInteger);
            lookupListItem.setValue(UUID.randomUUID().toString());

            lookupListManager.addLookupListItem(loggedInInfo, lookupListItem);
        }

        request.setAttribute("lookupLists", lookupListManager.findAllActiveLookupLists(loggedInInfo));

        return SUCCESS;
    }

    @SuppressWarnings("unused")
    public String remove() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String lookupListItemId = request.getParameter("lookupListItemId");
        int id = 0;

        if (lookupListItemId != null && !lookupListItemId.isEmpty()) {
            id = Integer.parseInt(lookupListItemId);
        }

        if (id > 0) {
            lookupListManager.removeLookupListItem(loggedInInfo, id);
        }

        request.setAttribute("lookupLists", lookupListManager.findAllActiveLookupLists(loggedInInfo));

        return SUCCESS;
    }
}
