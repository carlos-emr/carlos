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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.prop;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Vector;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;

/*
 * @Author: Ivy Chan
 * @Company: iConcept Technologes Inc.
 * @Created on: October 31, 2004
 */
@XmlRootElement(name = "formProp")
@XmlAccessorType(XmlAccessType.NONE)
public class EctFormProp {
    static EctFormProp fProp = new EctFormProp();
    static private Vector measurementTypes;

    @XmlElement(name = "measurement")
    private Vector<EctMeasurementTypesBean> measurements;

    public EctFormProp() {
        measurementTypes = new Vector();
        measurements = new Vector<>();
    }

    /**
     * @return EctFormProp the instance of EctFormProp
     */
    public static EctFormProp getInstance() {
        return fProp;
    }

    public void addMeasurementType(EctMeasurementTypesBean measurementType) {
        measurementTypes.addElement(measurementType);
    }

    static public Vector getMeasurementTypes() {
        return measurementTypes;
    }

    /**
     * Called by JAXB after unmarshalling to populate the static measurementTypes vector.
     */
    @SuppressWarnings("unused")
    private void afterUnmarshal(jakarta.xml.bind.Unmarshaller unmarshaller, Object parent) {
        if (measurements != null) {
            for (EctMeasurementTypesBean mt : measurements) {
                measurementTypes.addElement(mt);
            }
        }
    }

}
