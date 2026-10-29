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
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
public class LookupListItem extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer lookupListId;

    private String value;

    private String label;

    private boolean active = true;

    private int displayOrder = 0;

    private String createdBy;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated = new Date();

    public LookupListItem() {
    }

    /*
     * Copies the label field directly: getLabel() turns null into "", and a copy must hold
     * exactly what the row holds.
     */
    private LookupListItem(LookupListItem source) {
        this.id = source.id;
        this.lookupListId = source.lookupListId;
        this.value = source.value;
        this.label = source.label;
        this.active = source.active;
        this.displayOrder = source.displayOrder;
        this.createdBy = source.createdBy;
        this.dateCreated = source.dateCreated == null ? null : (Date) source.dateCreated.clone();
    }

    /**
     * Returns a detached copy that shares no mutable state with {@code source}.
     *
     * <p>Used by {@link LookupList#copyOf(LookupList)} so a cached lookup list's items are
     * copied along with the list. The creation date is cloned (keeping its runtime type, such
     * as {@link java.sql.Timestamp}). The copy keeps the database id, so
     * {@code equals}/{@code hashCode} still match the row, but it is not managed by any
     * persistence context: changing it writes nothing.</p>
     *
     * @param source the item to copy; may be {@code null}
     * @return a new detached copy, or {@code null} when {@code source} is {@code null}
     */
    public static LookupListItem copyOf(LookupListItem source) {
        return source == null ? null : new LookupListItem(source);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        if (label == null) {
            return "";
        }
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Integer getLookupListId() {
        return lookupListId;
    }

    public void setLookupListId(Integer lookupListId) {
        this.lookupListId = lookupListId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }


}
