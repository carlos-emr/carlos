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
package io.github.carlos_emr.carlos.managers;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao;
import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDaoImpl;
import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Service;

/**
 * Service for resolving clinical coding system descriptions in the CARLOS EMR system.
 *
 * <p>Provides a generic lookup mechanism for retrieving human-readable descriptions
 * from any registered coding system (ICD-9, ICD-10, SNOMED CT, ATC, etc.) by
 * dynamically resolving the appropriate DAO based on the coding system name.</p>
 *
 * @see io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao
 * @since 2026-03-17
 */
@Service
public class CodingSystemManager {

    /**
     * Retrieves the description for a code within a specified coding system.
     *
     * @param codingSystem String the coding system identifier (e.g., "icd9", "icd10")
     * @param code String the code to look up
     * @return String the code description, or null if not found or parameters are empty
     */
    public String getCodeDescription(String codingSystem, String code) {
        if (codingSystem != null && !codingSystem.isEmpty() && code != null && !code.isEmpty()) {
            Class<?> daoClass = AbstractCodeSystemDao.getDaoName(AbstractCodeSystemDaoImpl.codingSystem.valueOf(codingSystem));
            if (daoClass != null) {
                AbstractCodeSystemDao<AbstractCodeSystemModel<?>> csDao = (AbstractCodeSystemDao<AbstractCodeSystemModel<?>>) SpringUtils.getBean(daoClass);
                if (csDao != null) {
                    AbstractCodeSystemModel<?> codingSystemEntity = csDao.findByCode(code);
                    if (codingSystemEntity != null) {
                        return codingSystemEntity.getDescription();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks whether a code exists and has a non-blank description in the specified coding system.
     *
     * @param codingSystem String the coding system identifier
     * @param code String the code to check
     * @return boolean true if the code exists with a non-blank description
     */
    public boolean isCodeAvailable(String codingSystem, String code) {
        String description = getCodeDescription(codingSystem, code);
        return StringUtils.isNotBlank(description);
    }
}
