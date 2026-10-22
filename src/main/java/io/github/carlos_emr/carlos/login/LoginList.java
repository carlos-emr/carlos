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


/*
 *
 */

package io.github.carlos_emr.carlos.login;

import java.util.Hashtable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Class LoginList : an application level login list: ip/LoginInfoBean 2003-01-29
 */
public final class LoginList extends Hashtable {
    //only one instance to use
    private static LoginList loginList;

    private LoginList() {
    }

    // FindSecBugs USO_UNSAFE_METHOD_SYNCHRONIZATION, USO_UNSAFE_STATIC_METHOD_SYNCHRONIZATION (CWE-412): only code running inside the CARLOS JVM can acquire this monitor; no request or user input can hold it. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = {"USO_UNSAFE_METHOD_SYNCHRONIZATION", "USO_UNSAFE_STATIC_METHOD_SYNCHRONIZATION"}, justification = "only code running inside the CARLOS JVM can acquire this monitor; no request or user input can hold it")
    public synchronized static LoginList getLoginListInstance() {
        if (loginList == null) {
            loginList = new LoginList();
        }
        return loginList;
    }
}
