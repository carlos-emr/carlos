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

    private String icon;

    private String colour;

    private boolean active = true;

    private int displayOrder = 0;

    private String createdBy;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated = new Date();

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

    /**
     * Returns the icon drawn for this item, such as on an appointment location chip.
     *
     * @return String a whitelisted Font Awesome class such as {@code fa-house}, or null when unset
     * @since 2026-11-04
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Sets the icon drawn for this item. Writers validate it against a whitelist first, because
     * pages write it into a {@code class} attribute.
     *
     * @param icon String a whitelisted Font Awesome class, or null to clear it
     * @since 2026-11-04
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * Returns the colour this item is drawn in, such as on an appointment location chip.
     *
     * @return String a {@code #rrggbb} colour, or null when unset
     * @since 2026-11-04
     */
    public String getColour() {
        return colour;
    }

    /**
     * Sets the colour this item is drawn in. Writers validate it as {@code #rrggbb} first, because
     * pages write it into a {@code style} attribute.
     *
     * @param colour String a {@code #rrggbb} colour, or null to clear it
     * @since 2026-11-04
     */
    public void setColour(String colour) {
        this.colour = colour;
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
