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


package io.github.carlos_emr.carlos.dxresearch.bean;


/**
 * Bean representing a diagnosis code quick list entry.
 *
 * <p>Quick lists are provider-configurable shortcut lists of commonly used
 * diagnosis codes. This bean holds the quick list name, who created it,
 * and a selection indicator for UI rendering.</p>
 *
 * @since 2026-03-17
 */
public class dxQuickListBean {

    String quickListName;
    String createdBy;
    String lastUsed = "";

    /**
     * Default no-argument constructor.
     */
    public dxQuickListBean() {
    }

    /**
     * Constructs a quick list bean with name and creator.
     *
     * @param quickListName String the name of the quick list
     * @param createdBy String the provider who created this quick list
     */
    public dxQuickListBean(String quickListName,
                           String createdBy) {
        this.quickListName = quickListName;
        this.createdBy = createdBy;
    }

    /**
     * Constructs a quick list bean with only a name.
     *
     * @param quickListName String the name of the quick list
     */
    public dxQuickListBean(String quickListName) {
        this.quickListName = quickListName;
    }

    /**
     * Returns the name of the quick list.
     *
     * @return String the quick list name
     */
    public String getQuickListName() {
        return quickListName;
    }

    /**
     * Sets the name of the quick list.
     *
     * @param quickListName String the quick list name
     */
    public void setQuickListName(String quickListName) {
        this.quickListName = quickListName;
    }

    /**
     * Returns the provider who created this quick list.
     *
     * @return String the creator's provider identifier
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the provider who created this quick list.
     *
     * @param createdBy String the creator's provider identifier
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns the selection indicator for this quick list.
     *
     * @return String "Selected" if this is the active quick list, empty string otherwise
     */
    public String getLastUsed() {
        return lastUsed;
    }

    /**
     * Sets the selection indicator for this quick list.
     *
     * @param lastUsed String "Selected" to mark as active
     */
    public void setLastUsed(String lastUsed) {
        this.lastUsed = lastUsed;
    }

}
