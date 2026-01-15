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

package ca.openosp.openo.admin.lookUpLists;

import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.LookupListItem;
import ca.openosp.openo.managers.LookupListManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class LookupListManager2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);

    public LookupListManager2Action() {
        super();
    }

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

    @SuppressWarnings("unused")
    public String manageSingle() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String listName = request.getParameter("listName");
        if (listName != null && !listName.isEmpty()) {
            request.setAttribute("lookupListSingle", lookupListManager.findLookupListByName(loggedInInfo, listName));
        }

        return SUCCESS;
    }

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
