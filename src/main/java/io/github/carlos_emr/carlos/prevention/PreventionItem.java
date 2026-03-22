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

package io.github.carlos_emr.carlos.prevention;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.Prevention;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Represents a single prevention or immunization record used as a fact in the
 * Drools decision support engine.
 *
 * <p>Each item captures the prevention type name, date performed, next scheduled date,
 * and status flags (never, refused, ineligible, remote entry). Instances are added
 * to a {@link Prevention} profile and evaluated by the prevention rule base.</p>
 *
 * @since 2001-2002
 * @see Prevention
 * @see PreventionData
 */
public class PreventionItem {

    String id = null;
    String name = null;
    Date datePreformed = null;
    Date nextDate = null;
    String never = null;
    boolean refused;
    private boolean inelligible = false;
    private boolean remoteEntry = false;

    /** Default no-argument constructor. */
    public PreventionItem() {
    }

    /**
     * Constructs a prevention item with a type name and date performed.
     *
     * @param name String the prevention type name
     * @param d Date the date the prevention was performed
     */
    public PreventionItem(String name, Date d) {
        this.name = name;
        datePreformed = d;
    }

    /**
     * Constructs a prevention item with type, dates, and never-warn flag.
     *
     * @param name String the prevention type name
     * @param dPreformed Date the date the prevention was performed
     * @param never String "1" if marked as never, "0" otherwise
     * @param dNext Date the next scheduled prevention date
     */
    public PreventionItem(String name, Date dPreformed, String never, Date dNext) {
        this.name = name;
        this.datePreformed = dPreformed;
        this.never = never;
        this.nextDate = dNext;
    }

    /**
     * Constructs a prevention item with type, dates, never-warn flag, and result code.
     *
     * @param name String the prevention type name
     * @param dPreformed Date the date the prevention was performed
     * @param never String "1" if marked as never, "0" otherwise
     * @param dNext Date the next scheduled prevention date
     * @param result String the result code; "2" indicates ineligible
     */
    public PreventionItem(String name, Date dPreformed, String never, Date dNext, String result) {
        this.name = name;
        this.datePreformed = dPreformed;
        this.never = never;
        this.nextDate = dNext;
        this.inelligible = result.equalsIgnoreCase("2");
    }

    /**
     * Constructs a prevention item from a persistent {@link Prevention} model entity.
     *
     * @param pp Prevention the database prevention entity to copy from
     */
    public PreventionItem(Prevention pp) {
        this.name = pp.getPreventionType();
        this.datePreformed = pp.getPreventionDate();
        this.never = ConversionUtils.toBoolString(pp.isNever());
        this.nextDate = pp.getNextDate();
        this.refused = pp.isRefused();
        this.inelligible = pp.isIneligible();
    }

    /**
     * Returns whether the prevention is marked as "never" (value "1").
     *
     * @return boolean {@code true} if the never flag equals "1"
     */
    public boolean getNeverVal() {
        boolean ret = false;
        if (never != null && never.equals("1")) {
            ret = true;
        }
        return ret;
    }

    /**
     * Getter for property datePreformed.
     *
     * @return Value of property datePreformed.
     */
    public java.util.Date getDatePreformed() {
        return datePreformed;
    }

    /**
     * Setter for property datePreformed.
     *
     * @param datePreformed New value of property datePreformed.
     */
    public void setDatePreformed(java.util.Date datePreformed) {
        this.datePreformed = datePreformed;
    }

    /**
     * Getter for property next_date.
     *
     * @return Value of property next_date.
     */
    public java.util.Date getNextDate() {
        return nextDate;
    }

    /**
     * Setter for property next_date.
     *
     * @param nextDate New value of property next_date.
     */
    public void setNextDate(java.util.Date nextDate) {
        this.nextDate = nextDate;
    }

    /**
     * Returns whether this prevention was recorded as a remote entry.
     *
     * @return boolean {@code true} if this is a remote entry
     */
    public boolean isRemoteEntry() {
        return remoteEntry;
    }

    /**
     * Sets whether this prevention was recorded as a remote entry.
     *
     * @param remoteEntry boolean {@code true} if this is a remote entry
     */
    public void setRemoteEntry(boolean remoteEntry) {
        this.remoteEntry = remoteEntry;
    }

    /**
     * Returns whether the patient is ineligible for this prevention.
     *
     * @return boolean {@code true} if ineligible
     */
    public boolean isInelligible() {
        return this.inelligible;
    }

    /**
     * Sets whether the patient is ineligible for this prevention.
     *
     * @param inelligible boolean {@code true} if ineligible
     */
    public void setInelligible(boolean inelligible) {
        this.inelligible = inelligible;
    }
}
