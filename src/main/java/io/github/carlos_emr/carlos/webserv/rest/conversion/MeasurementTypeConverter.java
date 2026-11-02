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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MeasurementTypeTo1;

/**
 * One-way converter from {@link MeasurementType} to {@link MeasurementTypeTo1}. It copies the
 * four public fields only; {@code id}, {@code validation} and {@code createDate} stay behind.
 */
public class MeasurementTypeConverter extends AbstractConverter<MeasurementType, MeasurementTypeTo1> {

    @Override
    public MeasurementType getAsDomainObject(LoggedInInfo loggedInInfo, MeasurementTypeTo1 t) throws ConversionException {
        throw new ConversionException("not yet implemented");
    }

    @Override
    public MeasurementTypeTo1 getAsTransferObject(LoggedInInfo loggedInInfo, MeasurementType d) throws ConversionException {
        MeasurementTypeTo1 t = new MeasurementTypeTo1();
        t.setType(d.getType());
        t.setTypeDisplayName(d.getTypeDisplayName());
        t.setTypeDescription(d.getTypeDescription());
        t.setMeasuringInstruction(d.getMeasuringInstruction());
        return t;
    }
}
