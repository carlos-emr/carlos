//CHECKSTYLE:OFF
/**
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
 */
package ca.openosp.openo.utility;

/**
 * Session attribute key constants for legacy session management.
 * 
 * <p><strong>DEPRECATED:</strong> This class is deprecated. Use the {@link LoggedInInfo}
 * class instead for session management and accessing logged-in user information.</p>
 * 
 * <p>These constants define HTTP session attribute keys that were historically used
 * to store user session information such as:</p>
 * <ul>
 *   <li>Current program and facility context</li>
 *   <li>Logged-in provider information and preferences</li>
 *   <li>Security and authentication state</li>
 *   <li>Integrator connectivity status</li>
 * </ul>
 * 
 * @deprecated Use the LoggedInInfo class for accessing session-related information
 * @see ca.openosp.openo.utility.LoggedInInfo
 */
@Deprecated
public class SessionConstants {
    
    /** Session key for current program ID in the infirmary view */
    public static final String CURRENT_PROGRAM_ID = "infirmaryView_programId";
    
    /** Session key for the currently selected facility */
    public static final String CURRENT_FACILITY = "currentFacility";
    
    /** Session key indicating if intake client is dependent of a family */
    public static final String INTAKE_CLIENT_IS_DEPENDENT_OF_FAMILY = "isClientDependentOfFamily";
    
    /** Session key for the logged-in provider information */
    public static final String LOGGED_IN_PROVIDER = "providers";
    
    /** Session key for the logged-in user's security context */
    public static final String LOGGED_IN_SECURITY = "loggedInSecurity";
    
    /** Session key for the logged-in provider's preferences */
    public static final String LOGGED_IN_PROVIDER_PREFERENCE = "providerPreference";
    
    /** Session key indicating if the integrator is offline */
    public static final String INTEGRATOR_OFFLINE = "integratorOffline";
}
