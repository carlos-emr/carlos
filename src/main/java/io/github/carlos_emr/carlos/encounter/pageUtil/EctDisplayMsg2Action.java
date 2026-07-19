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


package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.commn.model.OscarMsgType;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EctDisplayMsg2Action extends EctDisplayAction {

    private static final String cmd = "msgs";
    private static MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", "r", null)) {
            return true; //Oscar message link won't show up on new CME screen.
        } else {
            //set text for lefthand module title
            Dao.setLeftHeading(getText("oscarEncounter.LeftNavBar.Messages"));

            //set link for lefthand module title
            String winName = "ViewMsg" + bean.demographicNo;
            String url = "popupPage(600,900,'" + winName + "','" + request.getContextPath() + "/messenger/DisplayDemographicMessages.do?orderby=date&boxType=3&demographic_no=" + bean.demographicNo + "&providerNo=" + bean.providerNo + "&userName=" + bean.userName + "')";
            Dao.setLeftURL(url);

            //set the right hand heading link
            winName = "SendMsg" + bean.demographicNo;
            url = "popupPage(700,960,'" + winName + "','" + request.getContextPath() + "/messenger/SendDemoMessage.do?demographic_no=" + bean.demographicNo + "'); return false;";
            Dao.setRightURL(url);
            Dao.setRightHeadingID(cmd);  //no menu so set div id to unique id for this action

            // set lefthand data link
            List<MessageTbl> messageTblList = messagingManager.getMessagesLinkedToDemographic(loggedInInfo, Integer.parseInt(bean.demographicNo));
            int msgId;
            String msgSubject;
            String msgDate;
            int hash;
            int messageType;

            for (MessageTbl message : messageTblList) {
                msgId = message.getId();
                msgSubject = message.getSubject();
                messageType = message.getType();
                Date date = message.getDate();
                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                item.setDate(date);
                hash = winName.hashCode();
                hash = hash < 0 ? hash * -1 : hash;

                msgDate = new SimpleDateFormat("dd-M-yyyy", request.getLocale()).format(date);

                url = "popupPage(600,900,'" + hash + "','" + request.getContextPath() + "/messenger/ViewMessage.do?from=encounter&boxType=0&messageID=" + msgId + "'); return false;";
                item.setURL(url);
                item.setTitle(StringUtils.maxLenString(msgSubject, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES));
                item.setLinkTitle(msgSubject + " " + msgDate);
                if (messageType == OscarMsgType.INTEGRATOR_TYPE) {
                    item.setBgColour("#FFCCCC");
                }
                Dao.addItem(item);
            }

            return true;
        }
    }

    public String getCmd() {
        return cmd;
    }
}
