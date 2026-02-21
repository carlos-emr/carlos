/*
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

/**
 * Shared utility functions for the CARLOS EMR messaging module.
 *
 * Extracted from DisplayMessages.jsp, CreateMessage.jsp, ViewMessage.jsp,
 * and SentMessage.jsp to eliminate duplication.
 */

/**
 * Notifies the parent (opener) window to refresh message alert badges,
 * then closes this popup window after a short delay.
 *
 * Falls back to a plain window.close() if the opener is unavailable or
 * does not expose callRefreshTabAlerts.
 */
function BackToCarlos() {
    if (opener && opener.callRefreshTabAlerts) {
        opener.callRefreshTabAlerts("oscar_new_msg");
        setTimeout(function() { window.close(); }, 100);
    } else {
        window.close();
    }
}

/**
 * Opens the demographic search popup for linking a patient to a message.
 *
 * @param {string} keyword - Search keyword entered by the user
 */
function popupSearchDemo(keyword) {
    var vheight = 700;
    var vwidth = 980;
    var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
    var page = 'msgSearchDemo.jsp?keyword=' + encodeURIComponent(keyword) + '&firstSearch=' + true;
    var popUp = window.open(page, "msgSearchDemo", windowprops);
    if (popUp != null) {
        if (popUp.opener == null) {
            popUp.opener = self;
        }
        popUp.focus();
    }
}
