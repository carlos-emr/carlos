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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.model.Episode;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;
import io.github.carlos_emr.carlos.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

public class EctDisplayEpisode2Action extends EctDisplayAction {

    private static final String cmd = "episode";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {
        boolean a = true;
        Vector v = OscarRoleObjectPrivilege.getPrivilegeProp("_newCasemgmt.episode");
        String roleName = (String) request.getSession().getAttribute("userrole") + "," + (String) request.getSession().getAttribute("user");
        a = OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (Vector) v.get(1));
        a = true;
        if (!a) {
            return true;
        } else {
            try {
                String appointmentNo = request.getParameter("appointment_no");

                //Set lefthand module heading and link
                String winName = "episode" + bean.demographicNo;
                String pathview, pathedit;

                pathview = request.getContextPath() + "/Episode.do?method=list&demographicNo=" + bean.demographicNo;
                pathedit = request.getContextPath() + "/Episode.do?method=edit&demographicNo=" + bean.demographicNo;


                String url = "popupPage(500,900,'" + winName + "','" + pathview + "')";
                Dao.setLeftHeading(getText("global.episode"));
                Dao.setLeftURL(url);

                //set right hand heading link
                winName = "AddEpisode" + bean.demographicNo;
                url = "popupPage(500,600,'" + winName + "','" + pathedit + "'); return false;";
                Dao.setRightURL(url);
                Dao.setRightHeadingID(cmd);


                EpisodeDao episodeDao = SpringUtils.getBean(EpisodeDao.class);
                List<Episode> episodes = episodeDao.findAllCurrent(Integer.parseInt(bean.demographicNo));

                for (Episode episode : episodes) {
                    NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                    String itemHeader = StringUtils.maxLenString(episode.getDescription(), MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                    item.setLinkTitle(itemHeader);
                    item.setTitle(itemHeader);
                    item.setDate(episode.getStartDate());
                    int hash = Math.abs(winName.hashCode());
                    url = "popupPage(500,900,'" + hash + "','" + request.getContextPath() + "/Episode.do?method=edit&episode.id=" + episode.getId() + "'); return false;";
                    item.setURL(url);
                    Dao.addItem(item);
                }

            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                return false;
            }
            return true;
        }
    }

    public String getCmd() {
        return cmd;
    }
}
