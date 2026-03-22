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

/**
 * Application-scoped singleton registry that maps IP addresses or usernames to their
 * {@link LoginInfoBean} login attempt tracking entries.
 *
 * <p>This class extends {@link Hashtable} to provide a thread-safe, application-wide
 * registry of failed login attempts. It uses the singleton pattern to ensure a single
 * shared instance across all login requests.
 *
 * <p>Keys are IP addresses (for IP-based blocking) or usernames (for account-based locking),
 * and values are {@link LoginInfoBean} instances that track attempt counts and block status.
 *
 * @see LoginInfoBean for individual attempt tracking
 * @see LoginCheckLogin for the coordinator that uses this registry
 * @since 2026-03-17
 */
public final class LoginList extends Hashtable {
    //only one instance to use
    private static LoginList loginList;

    private LoginList() {
    }

    /**
     * Returns the singleton LoginList instance, creating it if necessary.
     *
     * @return LoginList the shared singleton instance
     */
    public synchronized static LoginList getLoginListInstance() {
        if (loginList == null) {
            loginList = new LoginList();
        }
        return loginList;
    }
}
