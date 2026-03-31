/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.test.mocks;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secuserrole;

import java.util.Collections;
import java.util.List;

/**
 * Mock implementation of SecurityInfoManager that always grants access.
 * Used for testing to bypass security checks.
 */
public class MockSecurityInfoManager implements SecurityInfoManager {

    @Override
    public List<Secuserrole> getRoles(LoggedInInfo loggedInInfo) {
        return Collections.emptyList();
    }

    @Override
    public List<Secobjprivilege> getSecurityObjects(LoggedInInfo loggedInInfo) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
        // Always grant access in tests
        return true;
    }

    @Override
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo) {
        // Always grant access in tests
        return true;
    }

    @Override
    public boolean isAllowedAccessToPatientRecord(LoggedInInfo loggedInInfo, Integer demographicNo) {
        // Always grant access in tests
        return true;
    }
}