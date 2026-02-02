//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.tags;

/**
 * Represents a tag that can be associated with various objects within the application.
 * This class is a simple Plain Old Java Object (POJO) or JavaBean that encapsulates the properties of a tag.
 * A tag primarily consists of a unique identifier and a human-readable name.
 * For example, a tag could be used to categorize patients, documents, or appointments.
 */
public class Tag {
    /**
     * The unique identifier for the tag. This is typically a primary key from a database,
     * ensuring that each tag can be uniquely referenced.
     */
    private String tagId;
    /**
     * The human-readable name of the tag. This is the value that is typically displayed
     * to the user in the user interface. For example, "High Priority" or "Follow-up Required".
     */
    private String tagName;

    /**
     * Creates a new, empty instance of a Tag.
     * This no-argument constructor is useful for frameworks that require it for instantiation,
     * after which the properties of the tag can be set using the available setter methods.
     */
    public Tag() {
    }

    /**
     * Retrieves the name of the tag.
     * This name is the human-readable identifier for the tag.
     *
     * @return A <code>String</code> representing the name of the tag.
     */
    public String getTagName() {
        return tagName;
    }

    /**
     * Sets the name of the tag.
     * This method allows for updating the human-readable name that represents this tag.
     *
     * @param tagName The <code>String</code> to be used as the new name for the tag.
     */
    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    /**
     * Retrieves the unique identifier of the tag.
     * This ID is used to uniquely identify the tag within the system, often corresponding
     * to a database primary key.
     *
     * @return A <code>String</code> representing the unique identifier of the tag.
     */
    public String getTagId() {
        return tagId;
    }

    /**
     * Sets the unique identifier for the tag.
     * This method is used to assign a specific, unique ID to this tag instance.
     *
     * @param tagId The <code>String</code> to be used as the unique identifier for the tag.
     */
    public void setTagId(String tagId) {
        this.tagId = tagId;
    }


}
