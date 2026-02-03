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


package io.github.carlos_emr.carlos.tags;

import java.util.ArrayList;

/**
 * Represents a generic object within the application that can have tags associated with it.
 * This class acts as a data transfer object (DTO) to bundle an object's identity
 * with a list of tags that have been assigned to it. It is used to manage the
 * relationship between any taggable item and its tags.
 */
public class TagObject {
    /**
     * A list of tags that are currently assigned to this object.
     * Note: This is a raw {@link ArrayList} for compatibility with older Java versions.
     * It is intended to store a collection of {@link String} objects, where each string
     * is the name of a {@link Tag}.
     */
    private ArrayList assignedTags;
    /**
     * The unique identifier for the object being tagged. This ID allows the application
     * to retrieve the specific instance of the object from the database or another data source.
     * For example, this could be a patient's demographic number or a unique ID for a document.
     */
    private String objectId;
    /**
     * The fully qualified class name of the object being tagged (e.g., "io.github.carlos_emr.carlos.demographic.Demographic").
     * This provides crucial context, allowing the application to know what type of object
     * the {@link #objectId} refers to.
     */
    private String objectClass;

    /**
     * Assigns a new tag to this object by adding the tag's name to the list of assigned tags.
     * This is a convenience method that simplifies the process of adding a single tag.
     *
     * @param tagName The name of the tag to be assigned. This should correspond to the
     *                {@link Tag#tagName} of an existing tag.
     */
    public void assignTag(String tagName) {
        getAssignedTags().add(tagName);
    }

    /**
     * Retrieves the list of tags that have been assigned to this object.
     * The list contains the names of the tags as {@link String}s.
     *
     * @return An {@link ArrayList} of {@link String}s, where each string is the name of an assigned tag.
     *         Returns the underlying list directly.
     */
    public ArrayList getAssignedTags() {
        return assignedTags;
    }

    /**
     * Sets the list of assigned tags for this object. This method will replace any
     * existing list of tags with the one provided.
     *
     * @param assignedTags An {@link ArrayList} containing the names (as {@link String}s)
     *                     of all tags to be assigned to this object.
     */
    public void setAssignedTags(ArrayList assignedTags) {
        this.assignedTags = assignedTags;
    }

    /**
     * Retrieves the unique identifier of the object that this TagObject represents.
     *
     * @return A {@link String} containing the unique ID of the tagged object.
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * Sets the unique identifier of the object that this TagObject represents.
     *
     * @param objectId A {@link String} containing the unique ID to be assigned to this object.
     */
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    /**
     * Retrieves the fully qualified class name of the object that this TagObject represents.
     * This helps in identifying what kind of entity is being tagged.
     *
     * @return A {@link String} containing the class name of the tagged object.
     */
    public String getObjectClass() {
        return objectClass;
    }

    /**
     * Sets the fully qualified class name of the object that this TagObject represents.
     *
     * @param objectClass A {@link String} containing the fully qualified class name of the object.
     */
    public void setObjectClass(String objectClass) {
        this.objectClass = objectClass;
    }


}
