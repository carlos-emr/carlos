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
package io.github.carlos_emr.carlos.appointment.search;

import java.util.ArrayList;
import java.util.HashMap;

import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTypeTransfer;
import org.w3c.dom.Node;

/**
 * Represents a type of appointment available for online booking.
 *
 * <p>Defines an appointment category with its name, default duration, role-specific
 * duration overrides, and a mapping to the internal CARLOS appointment type. Instances
 * can be created from XML configuration or from REST transfer objects.</p>
 *
 * @since 2026-03-17
 */
public final class AppointmentType {
    private Long id;
    private String name;
    private int defaultDurationMinutes;
    private Integer mappingOscarApptType;
    private HashMap<String, Integer> roleDurations = new HashMap<String, Integer>();

    /**
     * Returns the unique identifier for this appointment type.
     *
     * @return Long the appointment type ID
     */
    public Long getId() {
        return (id);
    }

    /**
     * Sets the unique identifier for this appointment type.
     *
     * @param id Long the appointment type ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the display name of this appointment type.
     *
     * @return String the appointment type name
     */
    public String getName() {
        return (name);
    }

    /**
     * Sets the display name of this appointment type.
     *
     * @param name String the appointment type name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the default duration in minutes for this appointment type.
     *
     * @return int the default duration in minutes
     */
    public int getDefaultDurationMinutes() {
        return (defaultDurationMinutes);
    }

    /**
     * Sets the default duration in minutes for this appointment type.
     *
     * @param defaultDurationMinutes int the default duration in minutes
     */
    public void setDefaultDurationMinutes(int defaultDurationMinutes) {
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    /**
     * Returns role-specific duration overrides keyed by role name.
     *
     * @return HashMap&lt;String, Integer&gt; map of role names to durations in minutes
     */
    public HashMap<String, Integer> getRoleDurations() {
        return (roleDurations);
    }

    /**
     * Sets role-specific duration overrides.
     *
     * @param roleDurations HashMap&lt;String, Integer&gt; map of role names to durations in minutes
     */
    public void setRoleDurations(HashMap<String, Integer> roleDurations) {
        this.roleDurations = roleDurations;
    }

    /**
     * Creates an {@code AppointmentType} from a child-element-based XML node structure.
     *
     * @param node Node the XML node containing child elements for id, name, duration, and roles
     * @return AppointmentType the parsed appointment type
     */
    public static AppointmentType fromXml(Node node) {
        AppointmentType result = new AppointmentType();

        result.id = XmlUtils.getChildNodeLongContents(node, "id");
        result.name = XmlUtils.getChildNodeTextContents(node, "name");


        try {
            result.defaultDurationMinutes = XmlUtils.getChildNodeIntegerContents(node, "default_duration_minutes");
        } catch (Exception e) {
            //may not have a duration
        }
        try {
            result.mappingOscarApptType = XmlUtils.getChildNodeIntegerContents(node, "mappingOscarApptType");
        } catch (Exception e) {
            //may not have a duration
        }

        ArrayList<Node> roles = XmlUtils.getChildNodes(node, "role");
        for (Node roleNode : roles) {
            String roleName = XmlUtils.getChildNodeTextContents(roleNode, "role_name");
            Integer durationMinutes = XmlUtils.getChildNodeIntegerContents(roleNode, "duration_minutes");

            result.roleDurations.put(roleName, durationMinutes);
        }

        return (result);
    }

    /**
     * Creates an {@code AppointmentType} from an attribute-based XML node structure.
     *
     * <p>Reads id, name, and mappingOscarApptType from XML attributes rather than child elements.</p>
     *
     * @param node Node the XML node with attributes for id, name, and mappingOscarApptType
     * @return AppointmentType the parsed appointment type
     */
    public static AppointmentType fromXml2(Node node) {
        AppointmentType result = new AppointmentType();

        result.id = Long.parseLong(XmlUtils.getAttributeValue(node, "id"));
        result.name = XmlUtils.getAttributeValue(node, "name");
        try {
            result.mappingOscarApptType = Integer.parseInt(XmlUtils.getAttributeValue(node, "mappingOscarApptType"));
        } catch (Exception e) {
            //may not have a duration
        }

        return (result);
    }


    /**
     * Creates an {@code AppointmentType} from a REST transfer object.
     *
     * @param apptNode AppointmentTypeTransfer the transfer object containing appointment type data
     * @return AppointmentType the converted appointment type
     */
    public static AppointmentType fromAppointmentTypeTransfer(AppointmentTypeTransfer apptNode) {
        AppointmentType result = new AppointmentType();

        result.id = apptNode.getId();
        result.name = apptNode.getName();
        result.mappingOscarApptType = apptNode.getMappingOscarApptType();

        return (result);
    }

    public Integer getMappingOscarApptType() {
        return mappingOscarApptType;
    }

    public void setMappingOscarApptType(Integer mappingOscarApptType) {
        this.mappingOscarApptType = mappingOscarApptType;
    }

}
