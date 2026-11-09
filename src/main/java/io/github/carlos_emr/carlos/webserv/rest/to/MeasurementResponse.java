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
package io.github.carlos_emr.carlos.webserv.rest.to;

import io.github.carlos_emr.carlos.commn.model.Measurement;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@XmlRootElement
@jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.NONE)
public class MeasurementResponse implements Serializable {
    private Map<String, List<io.github.carlos_emr.carlos.webserv.rest.to.model.EmbeddedMeasurementTo1>> measurementMap = new HashMap<>();

    public void addMeasurements(List<Measurement> measurementList) {
        for (Measurement measurement : measurementList) {
            String type = measurement.getType();
            if (!measurementMap.containsKey(type)) {
                measurementMap.put(type, new ArrayList<>());
            }
            measurementMap.get(type).add(io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots.measurement(null, measurement));
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.xml.bind.annotation.XmlTransient
    public Map<String, List<Measurement>> getMeasurements() {
        Map<String, List<Measurement>> result = new java.util.LinkedHashMap<>();
        measurementMap.forEach((key, rows) -> result.put(key, rows.stream()
                .map(io.github.carlos_emr.carlos.webserv.rest.conversion.EmbeddedRecordSnapshots::measurementDomain).collect(java.util.stream.Collectors.toCollection(ArrayList::new))));
        return result;
    }
    @com.fasterxml.jackson.annotation.JsonProperty("measurements")
    public Map<String, List<io.github.carlos_emr.carlos.webserv.rest.to.model.EmbeddedMeasurementTo1>> getMeasurementSnapshots() {
        return measurementMap;
    }

    public void setMeasurementSnapshots(Map<String, List<io.github.carlos_emr.carlos.webserv.rest.to.model.EmbeddedMeasurementTo1>> value) {
        measurementMap = value == null ? new HashMap<>() : new java.util.LinkedHashMap<>(value);
    }

    public Map<String, String> getMeasurementUnits() {
        return MeasurementUnitConstants.measurementUnitMap;
    }
}
