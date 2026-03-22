/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.services;

import java.sql.SQLException;
import java.util.List;

import io.github.carlos_emr.carlos.daos.LookupDao;
import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;

/**
 * Implementation of the {@link LookupManager} interface that delegates
 * lookup table operations to the {@link LookupDao} data access object.
 *
 * <p>This service provides CRUD operations on configurable lookup tables
 * and their code values. It is typically configured via Spring dependency
 * injection with a {@link LookupDao} instance.</p>
 *
 * @see LookupManager
 * @see LookupDao
 * @since 2026-03-17
 */
public class LookupManagerImpl implements LookupManager {
    private LookupDao lookupDao = null;

    /** {@inheritDoc} */
    public List LoadCodeList(String tableId, boolean activeOnly, String code, String codeDesc) {
        return lookupDao.LoadCodeList(tableId, activeOnly, code, codeDesc);
    }

    /** {@inheritDoc} */
    public List LoadCodeList(String tableId, boolean activeOnly, String parentCode, String code, String codeDesc) {
        return lookupDao.LoadCodeList(tableId, activeOnly, parentCode, code, codeDesc);
    }

    /** {@inheritDoc} */
    public LookupTableDefValue GetLookupTableDef(String tableId) {
        return lookupDao.GetLookupTableDef(tableId);
    }

    /** {@inheritDoc} */
    public LookupCodeValue GetLookupCode(String tableId, String code) {
        return lookupDao.GetCode(tableId, code);
    }

    /** {@inheritDoc} */
    public List LoadFieldDefList(String tableId) {
        return lookupDao.LoadFieldDefList(tableId);
    }

    /** {@inheritDoc} */
    public List GetCodeFieldValues(LookupTableDefValue tableDef, String code) {
        return lookupDao.GetCodeFieldValues(tableDef, code);
    }

    /** {@inheritDoc} */
    public List GetCodeFieldValues(LookupTableDefValue tableDef) {
        return lookupDao.GetCodeFieldValues(tableDef);
    }

    /** {@inheritDoc} */
    public String SaveCodeValue(boolean isNew, LookupTableDefValue tableDef, List fieldDefList) throws SQLException {
        return lookupDao.SaveCodeValue(isNew, tableDef, fieldDefList);
    }

    /** {@inheritDoc} */
    public int getCountOfActiveClient(String orgCd) throws SQLException {
        return lookupDao.getCountOfActiveClient(orgCd);
    }

    /**
     * Returns the lookup data access object used by this manager.
     *
     * @return LookupDao the current DAO instance
     */
    public LookupDao getLookupDao() {
        return lookupDao;
    }

    /**
     * Sets the lookup data access object via Spring dependency injection.
     *
     * @param lookupDao LookupDao the DAO instance to use for database operations
     */
    public void setLookupDao(LookupDao lookupDao) {
        this.lookupDao = lookupDao;
    }
}
