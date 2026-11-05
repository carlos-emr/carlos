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

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.model.Episode;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public class EctDisplayPregnancy2Action extends EctDisplayAction {

    private static final String cmd = "pregnancy";
    private EpisodeDao episodeDao = SpringUtils.getBean(EpisodeDao.class);


    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO dao) {
        SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        int patient = Integer.parseInt(EncounterPatientContext.requireDemographicNo(request));
        if (!security.isAllowedAccessToPatientRecord(info, patient)
                || !security.hasPrivilege(info, "_pregnancy", "r", patient)
                || !security.hasPrivilege(info, "_newCasemgmt.pregnancy", "r", patient)) return true;

        String root = request.getContextPath() + "/Pregnancy?demographicNo=" + patient;
        String window = "pregnancy" + patient;
        dao.setLeftHeading(getText("global.pregnancy"));
        dao.setLeftPopup(500, 900, window, root + "&method=list");
        if (security.hasPrivilege(info, "_pregnancy", "w", patient)) {
            dao.setRightHeadingID("5");
            dao.setRightURL("return !showMenu('5', event);");
            dao.setMenuHeader("Pregnancy Type");
            String[][] types = {{"72892002", "Normal"}, {"47200007", "High Risk"}, {"16356006", "Multiple"}, {"34801009", "Ectopic"}};
            for (String[] type : types) {
                dao.addPopUpMenu(500, 900, "AddPregnancy" + patient, root + "&method=new&code=" + type[0]);
                dao.addPopUpText(type[1]);
            }
        }
        List<String> codes = List.of("72892002", "47200007", "16356006", "34801009");
        for (Episode episode : episodeDao.findCurrentByCodeTypeAndCodes(patient, "SnomedCore", codes)) {
            addEpisode(dao, episode, root, window, false);
        }
        for (Episode episode : episodeDao.findCompletedByCodeTypeAndCodes(patient, "SnomedCore", codes)) {
            addEpisode(dao, episode, root, window, true);
        }
        return true;
    }

    private void addEpisode(NavBarDisplayDAO dao, Episode episode, String root, String window, boolean completed) {
        NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
        String title = StringUtils.maxLenString(episode.getDescription(), MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
        item.setTitle((completed ? "Completed: " : "") + SafeEncode.forHtmlContent(title == null ? "" : title));
        // LeftNavBarDisplay encodes the tooltip attribute; the title permits deliberate HTML.
        item.setLinkTitle(episode.getNotes() == null ? "" : episode.getNotes());
        item.setDate(episode.getStartDate());
        item.setURL("popupPage(500,900,'" + SafeEncode.forJavaScript(window) + "','"
                + SafeEncode.forJavaScript(root + "&method=edit&episodeId=" + episode.getId()) + "'); return false;");
        dao.addItem(item);
    }

    public String getCmd() {
        return cmd;
    }
}
