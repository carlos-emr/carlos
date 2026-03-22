package io.github.carlos_emr.carlos.prev.reports;

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

import java.util.List;

import io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionSearchTo1;

import io.github.carlos_emr.carlos.prevention.reports.ReportItem;

/**
 * Data model representing a prevention report summary for a specific prevention type.
 *
 * <p>Tracks aggregate statistics for patient prevention compliance including total patients
 * evaluated, ineligible patients excluded, and patients who are up-to-date on the prevention.
 * Contains the search configuration used to generate the report and a list of individual
 * report items with per-patient details.</p>
 *
 * @since 2001-01-01
 * @see io.github.carlos_emr.carlos.prevention.reports.ReportItem
 * @see io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionSearchTo1
 */
public class Report {

    private int totalPatients = 0;
    private int ineligiblePatients = 0;
    private int up2date = 0;
    private PreventionSearchTo1 searchConfig = null;
    private boolean active = true;

    /*
     h.put("up2date",""+Math.round(done));
          h.put("percent",percentStr);
          h.put("percentWithGrace",percentWithGraceStr);
          h.put("returnReport",returnReport);
          h.put("inEligible", ""+inList);
          h.put("eformSearch","Mam");
          h.put("followUpType","PAPF");
          h.put("BillCode", "Q001A");
     */
    private List<ReportItem> items;

    /**
     * Returns the list of individual report items for this prevention report.
     *
     * @return List of ReportItem objects containing per-patient prevention details
     */
    public List<ReportItem> getItems() {
        return items;
    }

    /**
     * Sets the list of individual report items for this prevention report.
     *
     * @param items List of ReportItem objects containing per-patient prevention details
     */
    public void setItems(List<ReportItem> items) {
        this.items = items;
    }

    /**
     * Returns the total number of patients evaluated in this prevention report.
     *
     * @return int the total patient count
     */
    public int getTotalPatients() {
        return totalPatients;
    }

    /**
     * Sets the total number of patients evaluated in this prevention report.
     *
     * @param totalPatients int the total patient count
     */
    public void setTotalPatients(int totalPatients) {
        this.totalPatients = totalPatients;
    }

    /**
     * Returns the number of patients who are ineligible for this prevention.
     *
     * @return int the ineligible patient count
     */
    public int getIneligiblePatients() {
        return ineligiblePatients;
    }

    /**
     * Sets the number of patients who are ineligible for this prevention.
     *
     * @param ineligiblePatients int the ineligible patient count
     */
    public void setIneligiblePatients(int ineligiblePatients) {
        this.ineligiblePatients = ineligiblePatients;
    }

    /**
     * Returns the number of patients who are up-to-date on this prevention.
     *
     * @return int the up-to-date patient count
     */
    public int getUp2date() {
        return up2date;
    }

    /**
     * Sets the number of patients who are up-to-date on this prevention.
     *
     * @param up2date int the up-to-date patient count
     */
    public void setUp2date(int up2date) {
        this.up2date = up2date;
    }

    /**
     * Returns the search configuration used to generate this report.
     *
     * @return PreventionSearchTo1 the search configuration transfer object
     */
    public PreventionSearchTo1 getSearchConfig() {
        return searchConfig;
    }

    /**
     * Sets the search configuration used to generate this report.
     *
     * @param searchConfig PreventionSearchTo1 the search configuration transfer object
     */
    public void setSearchConfig(PreventionSearchTo1 searchConfig) {
        this.searchConfig = searchConfig;
    }

    /**
     * Increments the total patient count by one.
     */
    public void incrementTotalPatients() {
        this.totalPatients++;
    }

    /**
     * Increments the ineligible patient count by one.
     */
    public void incrementIneligiblePatients() {
        this.ineligiblePatients++;
    }

    /**
     * Increments the up-to-date patient count by one.
     */
    public void incrementUp2Date() {
        this.up2date++;
    }

    /**
     * Returns whether this prevention report is active.
     *
     * @return boolean true if the report is active, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets whether this prevention report is active.
     *
     * @param active boolean true to mark the report as active, false otherwise
     */
    public void setActive(boolean active) {
        this.active = active;
    }
}
