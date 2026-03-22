/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.MeasurementType;

/**
 * DAO interface for clinical measurement operations.
 *
 * @since 2001
 */

public interface MeasurementTypeDao extends AbstractDao<MeasurementType> {
    /**
     * Find All.
     * @return List<MeasurementType>
     */
    List<MeasurementType> findAll();

    /**
     * Find All Order By Name.
     * @return List<MeasurementType>
     */
    List<MeasurementType> findAllOrderByName();

    /**
     * Find All Order By Id.
     * @return List<MeasurementType>
     */
    List<MeasurementType> findAllOrderById();

    /**
     * Find By Type.
     *
     * @param type String the type
     * @return List<MeasurementType>
     */
    List<MeasurementType> findByType(String type);

    /**
     * Find By Measuring Instruction And Type Display Name.
     *
     * @param measuringInstruction String the measuringInstruction
     * @param typeDisplayName String the typeDisplayName
     * @return List<MeasurementType>
     */
    List<MeasurementType> findByMeasuringInstructionAndTypeDisplayName(String measuringInstruction, String typeDisplayName);

    /**
     * Find By Type Display Name.
     *
     * @param typeDisplayName String the typeDisplayName
     * @return List<MeasurementType>
     */
    List<MeasurementType> findByTypeDisplayName(String typeDisplayName);

    /**
     * Find By Type And Measuring Instruction.
     *
     * @param type String the type
     * @param measuringInstruction String the measuringInstruction
     * @return List<MeasurementType>
     */
    List<MeasurementType> findByTypeAndMeasuringInstruction(String type, String measuringInstruction);

    /**
     * Find Unique Type Display Names.
     * @return List<Object>
     */
    List<Object> findUniqueTypeDisplayNames();
}
