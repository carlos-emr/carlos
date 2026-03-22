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


package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;


/**
 * Immutable bean representing a measurement type definition with its ID, type code,
 * display name, description, measuring instruction, and validation rule reference.
 * Used by CDM report configuration UIs.
 *
 * @see RptMeasurementTypesBeanHandler
 * @since 2001-01-01
 */
public class RptMeasurementTypesBean {

    int id;
    String type;
    String typeDisplayName;
    String typeDesc;
    String measuringInstrc;
    String validation;


    public RptMeasurementTypesBean(int id, String type, String typeDisplayName, String typeDesc, String measuringInstrc, String validation) {
        this.id = id;
        this.type = type;
        this.typeDisplayName = typeDisplayName;
        this.typeDesc = typeDesc;
        this.measuringInstrc = measuringInstrc;
        this.validation = validation;
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTypeDisplayName() {
        return typeDisplayName;
    }

    public String getTypeDesc() {
        return typeDesc;
    }

    public String getMeasuringInstrc() {
        return measuringInstrc;
    }

    public String getValidation() {
        return validation;
    }


}
