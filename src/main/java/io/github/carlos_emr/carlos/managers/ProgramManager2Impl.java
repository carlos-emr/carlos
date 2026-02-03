/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.ProviderDefaultProgramDao;
import io.github.carlos_emr.carlos.commn.model.ProviderDefaultProgram;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProgramManager2Impl implements ProgramManager2 {

    @Autowired
    private ProgramDao programDao;

    @Autowired
    private ProgramProviderDAO programProviderDAO;

    @Autowired
    private ProviderDefaultProgramDao providerDefaultProgramDao;

    @Override
    public Program getProgram(LoggedInInfo loggedInInfo, Integer programId) {
        return programDao.getProgram(programId);
    }

    @Override
    public List<Program> getAllPrograms(LoggedInInfo loggedInInfo) {
        return programDao.findAll();
    }

    @Override
    public List<ProgramProvider> getAllProgramProviders(LoggedInInfo loggedInInfo) {
        return programProviderDAO.getAllProgramProviders();
    }

    @Override
    public List<ProgramProvider> getProgramDomain(LoggedInInfo loggedInInfo, String providerNo) {
        return programProviderDAO.getProgramProvidersByProvider(providerNo);
    }

    @Override
    public ProgramProvider getCurrentProgramInDomain(LoggedInInfo loggedInInfo) {
        return getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
    }

    @Override
    public ProgramProvider getCurrentProgramInDomain(LoggedInInfo loggedInInfo, String providerNo) {
        ProgramProvider result = null;
        int defProgramId = 0;
        List<ProviderDefaultProgram> rs = providerDefaultProgramDao.getProgramByProviderNo(providerNo);
        if (!rs.isEmpty()) {
            defProgramId = rs.get(0).getProgramId();
            if (defProgramId > 0) {
                result = programProviderDAO.getProgramProvider(providerNo, Long.valueOf(defProgramId));
            }
        }
        return (result);
    }

    @Override
    public void setCurrentProgramInDomain(String providerNo, Integer programId) {

        if (programProviderDAO.getProgramProvider(providerNo, programId.longValue()) != null) {
            providerDefaultProgramDao.setDefaultProgram(providerNo, programId);
        } else {
            throw new RuntimeException("Program not in user's domain");
        }
    }
}
