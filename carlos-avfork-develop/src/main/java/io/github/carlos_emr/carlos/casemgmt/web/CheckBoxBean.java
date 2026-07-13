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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.web;

import java.io.Serializable;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction.IssueDisplay;

public class CheckBoxBean implements Serializable {
    private String checked = "off";
    private CaseManagementIssue issue = new CaseManagementIssue();
    private IssueDisplay issueDisplay = new IssueDisplay();
    private boolean isUsed = false;


    public CaseManagementIssue getIssue() {
        return issue;
    }

    public void setIssue(CaseManagementIssue issue) {
        this.issue = issue;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public void setUsed(boolean used) {
        this.isUsed = used;
    }

    public String getChecked() {
        return checked;
    }

    public void setChecked(String checked) {
        this.checked = checked;
    }

    public IssueDisplay getIssueDisplay() {
        return issueDisplay;
    }

    public void setIssueDisplay(IssueDisplay issueDisplay) {
        this.issueDisplay = issueDisplay;
    }

    /* this is for debugging in CaseManagementEntry2Action - noteSave(), please keep it for future development
    @Override
    public String toString() {
        return "CheckBoxBean{" +
                "checked='" + checked + '\'' +
                ", isUsed=" + isUsed +
                ", issueId=" + (issue != null ? issue.getId() : "null") +
                ", issueDisplay=" + (issueDisplay != null ? issueDisplay.toString() : "null") +
                '}';
    }*/
}
