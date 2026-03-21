/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.test.builders;

import io.github.carlos_emr.carlos.commn.model.Measurement;

import java.util.Date;

/**
 * Test data builder for {@link Measurement} entities.
 *
 * <p>Provides deterministic defaults for clinical measurement test data.
 * Default type is blood pressure ("BP") with a realistic value.
 * Note: Measurement entities are immutable after creation (PreUpdate throws).</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Measurement bp = MeasurementTestBuilder.aMeasurement().build();
 * Measurement weight = MeasurementTestBuilder.aMeasurement()
 *     .withType("WT")
 *     .withDataField("75.5")
 *     .withMeasuringInstruction("kg")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class MeasurementTestBuilder {

    private String type = "BP";
    private Integer demographicId = 1;
    private String providerNo = "999990";
    private String dataField = "120/80";
    private String measuringInstruction = "Sitting position";
    private String comments = "";
    private Date dateObserved = new Date(1704067200000L); // 2024-01-01
    private Integer appointmentNo;

    private MeasurementTestBuilder() {
    }

    /**
     * Creates a new builder with blood pressure measurement defaults.
     *
     * @return a new builder instance
     */
    public static MeasurementTestBuilder aMeasurement() {
        return new MeasurementTestBuilder();
    }

    public MeasurementTestBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public MeasurementTestBuilder withDemographicId(Integer demographicId) {
        this.demographicId = demographicId;
        return this;
    }

    public MeasurementTestBuilder withProviderNo(String providerNo) {
        this.providerNo = providerNo;
        return this;
    }

    public MeasurementTestBuilder withDataField(String dataField) {
        this.dataField = dataField;
        return this;
    }

    public MeasurementTestBuilder withMeasuringInstruction(String measuringInstruction) {
        this.measuringInstruction = measuringInstruction;
        return this;
    }

    public MeasurementTestBuilder withComments(String comments) {
        this.comments = comments;
        return this;
    }

    public MeasurementTestBuilder withDateObserved(Date dateObserved) {
        this.dateObserved = dateObserved;
        return this;
    }

    public MeasurementTestBuilder withAppointmentNo(Integer appointmentNo) {
        this.appointmentNo = appointmentNo;
        return this;
    }

    /**
     * Creates a weight measurement with standard defaults.
     *
     * @return this builder
     */
    public MeasurementTestBuilder asWeight() {
        this.type = "WT";
        this.dataField = "75.5";
        this.measuringInstruction = "kg";
        return this;
    }

    /**
     * Creates a height measurement with standard defaults.
     *
     * @return this builder
     */
    public MeasurementTestBuilder asHeight() {
        this.type = "HT";
        this.dataField = "175";
        this.measuringInstruction = "cm";
        return this;
    }

    public Measurement build() {
        Measurement m = new Measurement();
        m.setType(type);
        m.setDemographicId(demographicId);
        m.setProviderNo(providerNo);
        m.setDataField(dataField);
        m.setMeasuringInstruction(measuringInstruction);
        m.setComments(comments);
        m.setDateObserved(dateObserved);
        m.setAppointmentNo(appointmentNo);
        return m;
    }
}
