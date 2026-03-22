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


package io.github.carlos_emr.carlos.workflow;

/**
 * Represents a single state within a clinical workflow, identified by a key with a
 * human-readable name and description.
 *
 * <p>Used by {@link WorkFlow} implementations to define the possible states
 * a workflow instance can be in (e.g., "No Appt made", "Appt Booked", "Closed").</p>
 *
 * @see WorkFlow
 * @see RHWorkFlow
 * @since 2026-03-17
 */
public class WFState {

    private String key = null;
    private String name = null;
    private String desc = null;

    /**
     * Creates a new instance of WFState
     */
    public WFState() {
    }

    /**
     * Constructs a new workflow state with the specified key, name, and description.
     *
     * @param key String the unique state identifier key
     * @param name String the human-readable state name
     * @param desc String the state description
     */
    public WFState(String key, String name, String desc) {
        this.key = key;
        this.name = name;
        this.desc = desc;

    }

    /**
     * Returns the unique state key identifier.
     *
     * @return String the state key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the unique state key identifier.
     *
     * @param key String the state key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Returns the human-readable state name.
     *
     * @return String the state name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the human-readable state name.
     *
     * @param name String the state name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the state description.
     *
     * @return String the state description
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Sets the state description.
     *
     * @param desc String the state description to set
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }


}
