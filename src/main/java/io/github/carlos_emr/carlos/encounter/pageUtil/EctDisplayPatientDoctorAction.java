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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.encounter.display.EchartPatientDoctorResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared body of the eChart's Family Doctor and Referring Doctor left-navigation panels, OSCAR 19's
 * "fDoc"/"rDoc" widgets ({@code EctDisplayPatientClinicStatusAction} there).
 *
 * <p>Each panel is a heading plus one row with the doctor's name; the heading, the row and the
 * {@code +} all open the patient's Master Record, where the name is maintained. A patient with no
 * doctor of that kind recorded gets the heading and an empty row. Whether the panel is requested at
 * all is the provider's eChart setting ({@code newCaseManagementView.js.jsp}); this action only
 * answers when asked.</p>
 *
 * <p>Requires {@code _eChart} read for the patient (enforced by {@link EctDisplayAction}) and
 * {@code _demographic} read, since the names come from the master record.</p>
 *
 * @since 2026-10-16
 */
public abstract class EctDisplayPatientDoctorAction extends EctDisplayAction {

    private final DemographicManager demographicManager;
    private final EchartPatientDoctorResolver doctorResolver;

    protected EctDisplayPatientDoctorAction(DemographicManager demographicManager,
                                            EchartPatientDoctorResolver doctorResolver) {
        this.demographicManager = demographicManager;
        this.doctorResolver = doctorResolver;
    }

    /** @return the {@code oscarResources} key of the panel heading */
    protected abstract String headingKey();

    /** @return the doctor's display name for the patient, {@code ""} when none is recorded */
    protected abstract String doctorName(EchartPatientDoctorResolver resolver, LoggedInInfo loggedInInfo,
                                         Demographic demographic);

    @Override
    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO dao) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", bean.demographicNo)) {
            return true; // no panel content without access to the master record
        }
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, bean.demographicNo);
        String name = doctorName(doctorResolver, loggedInInfo, demographic);
        fillPanel(dao, getText(headingKey()), name, request.getContextPath(), bean.demographicNo, getCmd());
        return true;
    }

    /**
     * Writes the heading and the single name row. Package-private for unit tests.
     *
     * @param dao           the panel model
     * @param heading       the translated heading
     * @param name          the doctor's name, {@code ""} for none
     * @param contextPath   the web application context path
     * @param demographicNo the patient, numeric (validated by {@link EctDisplayAction})
     * @param cmd           the panel id ({@code fDoc} or {@code rDoc})
     */
    static void fillPanel(NavBarDisplayDAO dao, String heading, String name, String contextPath,
                          String demographicNo, String cmd) {
        String winName = "Master" + demographicNo;
        String masterRecordUrl = contextPath + "/demographic/DemographicEdit?demographic_no=" + demographicNo;
        dao.setLeftHeading(heading);
        dao.setLeftPopup(700, 1024, winName, masterRecordUrl);
        dao.setRightPopup(700, 1024, winName, masterRecordUrl);
        dao.setRightHeadingID(cmd);

        if (name == null || name.isBlank()) {
            return;
        }
        NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
        // The renderer writes the title as HTML and the link title as an encoded attribute.
        item.setLinkTitle(name);
        item.setTitle(SafeEncode.forHtml(StringUtils.maxLenString(name, MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES)));
        item.setURL("popupPage(700,1024,'" + SafeEncode.forJavaScript(winName) + "','"
                + SafeEncode.forJavaScript(masterRecordUrl) + "'); return false;");
        item.setURLJavaScript(true);
        dao.addItem(item);
    }
}
