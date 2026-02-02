//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package io.github.carlos_emr.carlos.PMmodule.web.formbean;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;

public class ProgramManagerViewFormBean {

    public static final String[] tabs = {"General", "Staff", "Teams", "Clients", "Queue", "Access", "Client Status", "Service Restrictions", "Vacancies"};

    private String tab;
    private String subtab;
    private String clientId;
    private String queueId;
    private String vacancyOrTemplateId;

    private String radioRejectionReason;
    private ProgramClientRestriction serviceRestriction;

    public String getRadioRejectionReason() {
        return radioRejectionReason;
    }

    public void setRadioRejectionReason(String radioRejectionReason) {
        this.radioRejectionReason = radioRejectionReason;
    }

    /**
     * @return Returns the tab.
     */
    public String getTab() {
        return tab;
    }

    /**
     * @param tab The tab to set.
     */
    public void setTab(String tab) {
        this.tab = tab;
    }

    /**
     * @return the subtab
     */
    public String getSubtab() {
        return subtab;
    }

    /**
     * @param subtab the subtab to set
     */
    public void setSubtab(String subtab) {
        this.subtab = subtab;
    }

    /**
     * @return Returns the clientId.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * @param clientId The clientId to set.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getQueueId() {
        return queueId;
    }

    public void setQueueId(String queueId) {
        this.queueId = queueId;
    }

    public ProgramClientRestriction getServiceRestriction() {
        return serviceRestriction;
    }

    public void setServiceRestriction(ProgramClientRestriction serviceRestriction) {
        this.serviceRestriction = serviceRestriction;
    }

    public String getVacancyOrTemplateId() {
        return vacancyOrTemplateId;
    }

    public void setVacancyOrTemplateId(String vacancyOrTemplateId) {
        this.vacancyOrTemplateId = vacancyOrTemplateId;
    }

}
