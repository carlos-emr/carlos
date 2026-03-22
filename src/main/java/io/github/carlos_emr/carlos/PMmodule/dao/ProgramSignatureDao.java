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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramSignature;

/**
 * Data access interface for managing {@link ProgramSignature} entities that track
 * provider signatures (approvals) on programs.
 *
 * @since 2005-01-18
 * @see ProgramSignature
 * @see ProgramSignatureDaoImpl
 */
public interface ProgramSignatureDao {

    /**
     * Retrieves the first (earliest) signature for a program, representing the program creator.
     *
     * @param programId Integer the program ID
     * @return ProgramSignature the first signature, or {@code null} if not found
     */
    public ProgramSignature getProgramFirstSignature(Integer programId);

    /**
     * Retrieves all signatures for a program, ordered by update date ascending.
     *
     * @param programId Integer the program ID
     * @return List&lt;ProgramSignature&gt; signatures for the program, or {@code null} if ID is invalid
     */
    public List<ProgramSignature> getProgramSignatures(Integer programId);

    /**
     * Saves or updates a program signature, setting the update date to the current time.
     *
     * @param programSignature ProgramSignature the signature to save
     * @throws IllegalArgumentException if programSignature is {@code null}
     */
    public void saveProgramSignature(ProgramSignature programSignature);
}
