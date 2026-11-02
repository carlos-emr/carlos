/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Read-only description of a measurement type for {@code GET /measurements/types}.
 *
 * <p>Carries only the four fields a client needs to label and instruct a measurement. The
 * entity's internal {@code id}, {@code validation} rules and {@code createDate} are
 * deliberately absent so they can never reach the wire.</p>
 */
@XmlRootElement(name = "measurementType")
public class MeasurementTypeTo1 {

    private String type;
    private String typeDisplayName;
    private String typeDescription;
    private String measuringInstruction;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeDisplayName() {
        return typeDisplayName;
    }

    public void setTypeDisplayName(String typeDisplayName) {
        this.typeDisplayName = typeDisplayName;
    }

    public String getTypeDescription() {
        return typeDescription;
    }

    public void setTypeDescription(String typeDescription) {
        this.typeDescription = typeDescription;
    }

    public String getMeasuringInstruction() {
        return measuringInstruction;
    }

    public void setMeasuringInstruction(String measuringInstruction) {
        this.measuringInstruction = measuringInstruction;
    }
}
