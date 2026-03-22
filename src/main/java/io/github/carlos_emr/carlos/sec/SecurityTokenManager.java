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


package io.github.carlos_emr.carlos.sec;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Abstract base class for token-based authentication management in the CARLOS EMR system.
 *
 * <p>This class provides a singleton-based factory pattern for creating and managing
 * security tokens used for stateless API authentication. The concrete implementation
 * is determined by the {@code sec.token.manager} configuration property, allowing
 * different token strategies to be plugged in without changing the consuming code.</p>
 *
 * <p>Token authentication flow:</p>
 * <ol>
 *   <li>Client requests a token via {@link #requestToken(ServletRequest, ServletResponse, FilterChain)}</li>
 *   <li>Server generates and returns a token</li>
 *   <li>Client includes the token in subsequent requests</li>
 *   <li>Server validates the token via {@link #handleToken(ServletRequest, ServletResponse, FilterChain)}</li>
 * </ol>
 *
 * @since 2001-01-01
 * @see LoginFilter
 */
public abstract class SecurityTokenManager {

    /** Singleton instance, lazily initialized from configuration. */
    static SecurityTokenManager instance = null;

    /**
     * Returns the singleton SecurityTokenManager instance, creating it if necessary.
     *
     * <p>The concrete implementation class is loaded from the {@code sec.token.manager}
     * property in the CARLOS configuration. Returns null if the property is not set
     * or the class cannot be instantiated.</p>
     *
     * @return SecurityTokenManager the singleton instance, or null if not configured
     */
    public static SecurityTokenManager getInstance() {
        if (instance != null) {
            return instance;
        }

        String managerName = CarlosProperties.getInstance().getProperty("sec.token.manager");
        if (managerName != null) {
            try {
                instance = (SecurityTokenManager) Class.forName(managerName).newInstance();
            } catch (Exception e) {
                MiscUtils.getLogger().error("Unable to load token manager");
            }
        }

        return instance;
    }

    /**
     * Generates a new security token and sets it as a request attribute.
     *
     * <p>Implementations should generate a secure, unique token and associate it
     * with the requesting user's session or credentials. The token is set as the
     * "token" attribute in the request for downstream processing.</p>
     *
     * @param request ServletRequest the incoming request containing authentication credentials
     * @param response ServletResponse the response to write the token to
     * @param chain FilterChain the filter chain for continued processing
     * @throws IOException if an I/O error occurs during token generation or response writing
     * @throws ServletException if a servlet-level error occurs
     */
    public abstract void requestToken(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException;

    /**
     * Validates a security token and establishes an authenticated session if successful.
     *
     * <p>Implementations should verify the token from the request parameters or attributes,
     * and if valid, set the "user" attribute in the HTTP session to indicate an
     * authenticated user.</p>
     *
     * @param request ServletRequest the incoming request containing the token to validate
     * @param response ServletResponse the response for writing error information if needed
     * @param chain FilterChain the filter chain for continued processing
     * @return boolean true if the token is valid and authentication succeeded, false otherwise
     * @throws IOException if an I/O error occurs during token validation
     * @throws ServletException if a servlet-level error occurs
     */
    public abstract boolean handleToken(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException;

}
