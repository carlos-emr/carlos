/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.dashboard.display.beans.DashboardBean;
import io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean;
import io.github.carlos_emr.carlos.dashboard.display.beans.IndicatorBean;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface DashboardManager {
    public static final boolean MULTI_THREAD_ON = Boolean.TRUE;

    // static final boolean MULTI_THREAD_ON = Boolean.TRUE;

    enum ObjectName {
        IndicatorTemplate, Dashboard
    }

    /**
     * Toggles the active status of a given class name.
     * Options are:
     * - IndicatorTemplate
     * - Dashboard
     */
    void toggleStatus(LoggedInInfo loggedInInfo, int objectId, ObjectName objectClassName, Boolean state);

    /**
     * Retrieves all the information for each Indicator Template query
     * that is stored in the indicatorTemplate db table.
     */
    List<IndicatorTemplate> getIndicatorLibrary(LoggedInInfo loggedInInfo, boolean sharedOnly);

    List<IndicatorTemplate> getIndicatorLibrary(LoggedInInfo loggedInInfo);

    /**
     * Toggles the Indicator active boolean switch. True for active, false for not
     * active.
     */
    void toggleIndicatorActive(LoggedInInfo loggedInInfo, int indicatorId, Boolean state);

    /**
     * Returns ALL available Dashboards.
     */
    List<Dashboard> getDashboards(LoggedInInfo loggedInInfo);

    /**
     * Returns Dashboards that are active.
     */
    List<Dashboard> getActiveDashboards(LoggedInInfo loggedInInfo);

    /**
     * Add a new Dashboard entry or edit an old one.
     */
    boolean addDashboard(LoggedInInfo loggedInInfo, Dashboard dashboard);

    /**
     * Toggles the Dashboard active boolean switch. True for active, false for not
     * active.
     */
    void toggleDashboardActive(LoggedInInfo loggedInInfo, int dashboardId, Boolean state);

    /**
     * Retrieves an XML file from a servlet request object and then saves it to
     * the local file directory and finally writes an entry in the Indicator
     * Template db table.
     * <p>
     * Returns a JSON string: status=success, or status=error, message=[message]
     */
    String importIndicatorTemplate(LoggedInInfo loggedInInfo, byte[] bytearray);

    /**
     * Overload method with a indicatorId list parameter.
     */
    boolean assignIndicatorToDashboard(LoggedInInfo loggedInInfo, int dashboardId, List<Integer> indicatorId);

    /**
     * Assign an Indicator the Dashboard where the Indicator will be displayed.
     */
    boolean assignIndicatorToDashboard(LoggedInInfo loggedInInfo, int dashboardId, int indicatorId);

    /**
     * Returns the raw indicator template XML for download and editing.
     */
    String exportIndicatorTemplate(LoggedInInfo loggedInInfo, int indicatorId);

    /**
     * Returns a List of ACTIVE Indicator Templates based on the DashboardId
     */
    List<IndicatorTemplate> getIndicatorTemplatesByDashboardId(LoggedInInfo loggedInInfo, int dashboardId);

    /**
     * Get an entire Dashboard, with all of its Indicators in a List parameter.
     */
    DashboardBean getDashboard(LoggedInInfo loggedInInfo, int dashboardId);

    /**
     * Get an Indicator Template by Id.
     */
    IndicatorTemplate getIndicatorTemplate(LoggedInInfo loggedInInfo, int indicatorTemplateId);

    /**
     * Get the XML template that contains all the data and meta data for an
     * Indicator display.
     */
    IndicatorTemplateXML getIndicatorTemplateXML(LoggedInInfo loggedInInfo, int indicatorTemplateId);

    DrilldownBean getDrilldownData(LoggedInInfo loggedInInfo, int indicatorTemplateId, String metricLabel);

    /**
     * Create a DrilldownBean that contains the query results requested from a
     * specific Indicator by ID.
     */
    DrilldownBean getDrilldownData(LoggedInInfo loggedInInfo, int indicatorTemplateId, String providerNo,
                                   String metricLabel);

    String exportDrilldownQueryResultsToCSV(LoggedInInfo loggedInInfo, int indicatorId);

    String exportDrilldownQueryResultsToCSV(LoggedInInfo loggedInInfo, String providerNo, int indicatorId);

    /**
     * Get an Indicator Panel Bean with a fully executed query.
     */
    IndicatorBean getIndicatorPanel(LoggedInInfo loggedInInfo, int indicatorId);

    /**
     * Get an Indicator Panel Bean with a fully executed query.
     */
    IndicatorBean getIndicatorPanelForProvider(LoggedInInfo loggedInInfo, String providerNo, int indicatorId);

    // TODO add additional error check / filter class to carry out the following
    // methods.

    // TODO add check queries method.

    // TODO add duplicate Indicator Template upload check.

    // TODO add duplicate Dashboard name check.

    String getSharedOutcomesDashboardLaunchURL(LoggedInInfo loggedInInfo);

    void setRequestedProviderNo(LoggedInInfo loggedInInfo, String providerNo);

    String getRequestedProviderNo(LoggedInInfo loggedInInfo);

}
