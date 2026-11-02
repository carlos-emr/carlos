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
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlRootElement;

import io.github.carlos_emr.carlos.webserv.rest.to.model.MeasurementTypeTo1;

/**
 * Envelope for {@code GET /measurements/types}. It carries only the public catalogue fields held by
 * {@link MeasurementTypeTo1}; no patient data and no validation rules.
 */
@XmlRootElement
public class MeasurementTypeResponse {
    private List<MeasurementTypeTo1> measurementTypes = new ArrayList<MeasurementTypeTo1>();

    public List<MeasurementTypeTo1> getMeasurementTypes() {
        return measurementTypes;
    }

    public void setMeasurementTypes(List<MeasurementTypeTo1> measurementTypes) {
        this.measurementTypes = measurementTypes;
    }
}
