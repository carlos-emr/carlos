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
 */
package io.github.carlos_emr.carlos.services;

import java.sql.SQLException;
import java.util.List;

import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;

public interface LookupManager {
    List LoadCodeList(String tableId, boolean activeOnly, String code, String codeDesc);

    List LoadCodeList(String tableId, boolean activeOnly, String parentCode, String code, String codeDesc);

    LookupTableDefValue GetLookupTableDef(String tableId);

    LookupCodeValue GetLookupCode(String tableId, String code);

    List LoadFieldDefList(String tableId);

    List GetCodeFieldValues(LookupTableDefValue tableDef, String code);

    List GetCodeFieldValues(LookupTableDefValue tableDef);

    String SaveCodeValue(boolean isNew, LookupTableDefValue tableDef, List fieldDefList) throws SQLException;

    int getCountOfActiveClient(String orgCd) throws SQLException;
}
