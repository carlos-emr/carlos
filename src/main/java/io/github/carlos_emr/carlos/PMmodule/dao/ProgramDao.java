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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.Program;

public interface ProgramDao {


    public boolean isServiceProgram(Integer programId);

    public boolean isCommunityProgram(Integer programId);

    public boolean isExternalProgram(Integer programId);

    public Program getProgram(Integer programId);

    public Program getProgramForApptView(Integer programId);

    public String getProgramName(Integer programId);

    public Integer getProgramIdByProgramName(String programName);

    public List<Program> findAll();

    public List<Program> getAllPrograms();

    public List<Program> getAllActivePrograms();

    public List<Program> getAllPrograms(String programStatus, String type, int facilityId);

    public List<Program> getPrograms();

    public List<Program> getActivePrograms();

    public List<Program> getProgramsByFacilityId(Integer facilityId);

    public List<Program> getProgramsByFacilityIdAndFunctionalCentreId(Integer facilityId, String functionalCentreId);

    public List<Program> getCommunityProgramsByFacilityId(Integer facilityId);

    public List<Program> getProgramsByType(Integer facilityId, String type, Boolean active);

    public List<Program> getProgramByGenderType(String genderType);

    public void saveProgram(Program program);

    public void removeProgram(Integer programId);

    public List<Program> search(Program program);

    public List<Program> searchByFacility(Program program, Integer facilityId);

    public void resetHoldingTank();

    public Program getHoldingTankProgram();

    public boolean programExists(Integer programId);


    public boolean isInSameFacility(Integer programId1, Integer programId2);

    public Program getProgramBySiteSpecificField(String value);

    public Program getProgramByName(String value);

    public List<Integer> getRecordsAddedAndUpdatedSinceTime(Integer facilityId, Date date);

    public List<Integer> getRecordsByFacilityId(Integer facilityId);

    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date);
}
