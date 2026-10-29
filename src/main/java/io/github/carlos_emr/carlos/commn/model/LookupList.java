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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
public class LookupList extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    private String listTitle;

    private String description;

    private Integer categoryId;

    private boolean active = true;

    private String createdBy;

    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated = new Date();

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "lookupListId", referencedColumnName = "id")
    @OrderBy("displayOrder")
    private List<LookupListItem> items = new ArrayList<LookupListItem>();

    public LookupList() {
    }

    private LookupList(LookupList source) {
        this.id = source.id;
        this.name = source.name;
        this.listTitle = source.listTitle;
        this.description = source.description;
        this.categoryId = source.categoryId;
        this.active = source.active;
        this.createdBy = source.createdBy;
        this.dateCreated = source.dateCreated == null ? null : (Date) source.dateCreated.clone();
        this.items = copyItems(source.items);
    }

    /**
     * Returns a detached deep copy that shares no mutable state with {@code source}.
     *
     * <p>{@code LookupListDaoImpl} keeps copies in the shared {@code lookupLists} cache and
     * hands each caller its own copy, so one caller changing a list, its item list, or one of
     * its items cannot change what every other reader sees until the cache expires. The items
     * are copied one by one, in order, into a new modifiable list; the creation date is cloned
     * (keeping its runtime type, such as {@link java.sql.Timestamp}). The copy and its items
     * keep their database ids, so {@code equals}/{@code hashCode} still match the rows, but
     * none of them is managed by a persistence context: changing them writes nothing.</p>
     *
     * <p>The source's items must already be loaded (they are fetched eagerly).</p>
     *
     * @param source the lookup list to copy; may be {@code null}
     * @return a new detached copy, or {@code null} when {@code source} is {@code null}
     */
    public static LookupList copyOf(LookupList source) {
        return source == null ? null : new LookupList(source);
    }

    private static List<LookupListItem> copyItems(List<LookupListItem> source) {
        if (source == null) {
            return null;
        }
        List<LookupListItem> copies = new ArrayList<>(source.size());
        for (LookupListItem item : source) {
            copies.add(LookupListItem.copyOf(item));
        }
        return copies;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getListTitle() {
        return listTitle;
    }

    public void setListTitle(String listTitle) {
        this.listTitle = listTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public List<LookupListItem> getItems() {
        return items;
    }

    public void setItems(List<LookupListItem> items) {
        this.items = items;
    }


}
