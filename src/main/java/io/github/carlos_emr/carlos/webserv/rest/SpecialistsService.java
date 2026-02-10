/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Originally written for the Department of Family Medicine, McMaster University.
 * Ported from JunoEMR to the CARLOS EMR Project, 2026.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ProfessionalSpecialistConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProfessionalSpecialistTo1;

/**
 * REST service providing access to the professional specialist directory.
 *
 * <p>Enables programmatic search of specialists for referral workflows. Specialists
 * can be searched by name (last name, or "lastName, firstName" format) and/or by
 * referral number, with paginated results.</p>
 *
 * <p>All operations require OAuth authentication and READ privilege on the consultation
 * security object ({@code _con}).</p>
 *
 * <h3>Endpoint</h3>
 * <pre>GET /ws/services/specialists/</pre>
 *
 * <h3>Query Parameters</h3>
 * <ul>
 *   <li>{@code searchName} - Name filter: "lastName" or "lastName, firstName"</li>
 *   <li>{@code searchRefNo} - Referral number filter</li>
 *   <li>{@code page} - Page number, 1-indexed (default: 1)</li>
 *   <li>{@code perPage} - Results per page (default: 10)</li>
 * </ul>
 *
 * @see ProfessionalSpecialist
 * @see ProfessionalSpecialistDao
 * @see RestResponse
 * @since 2026-02-10
 */
@Path("/specialists")
@Component("SpecialistsService")
public class SpecialistsService extends AbstractServiceImpl {

    private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private ProfessionalSpecialistDao specialistDao;

    @Autowired
    private ProfessionalSpecialistConverter converter;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    /**
     * Searches the professional specialist directory with optional name and referral number filters.
     *
     * <p>The {@code searchName} parameter supports two formats:</p>
     * <ul>
     *   <li>{@code "Smith"} - searches by last name only</li>
     *   <li>{@code "Smith, Jane"} - searches by last name and first name</li>
     * </ul>
     *
     * <p>Results are paginated using 1-indexed page numbers. Hidden specialists
     * (those with {@code hideFromView = true}) are excluded from results.</p>
     *
     * @param searchName String optional name filter in "lastName" or "lastName, firstName" format
     * @param searchRefNo String optional referral number filter
     * @param page Integer the 1-indexed page number (default: 1)
     * @param perPage Integer the number of results per page (default: 10)
     * @return RestResponse&lt;List&lt;ProfessionalSpecialistTo1&gt;&gt; a response containing
     *         matching specialists or an error message
     * @throws SecurityException if the current user lacks READ privilege on the
     *                           consultation security object (_con)
     */
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<List<ProfessionalSpecialistTo1>> searchSpecialists(
            @QueryParam("searchName") String searchName,
            @QueryParam("searchRefNo") String searchRefNo,
            @QueryParam("page") @DefaultValue("1") Integer page,
            @QueryParam("perPage") @DefaultValue("10") Integer perPage) {

        LoggedInInfo loggedInInfo = getLoggedInInfo();

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, null)) {
            throw new SecurityException("Missing required security object (_con)");
        }

        if (page < 1) {
            page = 1;
        }
        int offset = perPage * (page - 1);

        try {
            String[] names = splitSearchString(searchName);
            List<ProfessionalSpecialist> specialists =
                    specialistDao.findByFullNameAndReferralNo(names[0], names[1], searchRefNo, offset, perPage);

            List<ProfessionalSpecialistTo1> transfers = new ArrayList<>();
            for (ProfessionalSpecialist specialist : specialists) {
                transfers.add(converter.getAsTransferObject(loggedInInfo, specialist));
            }

            return RestResponse.successResponse(transfers);
        } catch (Exception e) {
            logger.error("Error searching specialists", e);
            return RestResponse.errorResponse("Unexpected error");
        }
    }

    /**
     * Splits a search string into last name and first name components.
     *
     * <p>Handles three cases:</p>
     * <ul>
     *   <li>{@code null} input returns {@code [null, null]}</li>
     *   <li>{@code "Smith"} returns {@code ["Smith", null]}</li>
     *   <li>{@code "Smith, Jane"} returns {@code ["Smith", "Jane"]}</li>
     * </ul>
     *
     * <p>Whitespace around each name component is trimmed, and empty strings
     * are converted to {@code null}.</p>
     *
     * @param searchText String the search string to split
     * @return String[] a two-element array with [lastName, firstName], either may be null
     */
    static String[] splitSearchString(String searchText) {
        if (searchText == null) {
            return new String[]{null, null};
        }

        String[] terms = searchText.split(",");
        String lastName = StringUtils.trimToNull(terms[0]);
        String firstName = terms.length > 1 ? StringUtils.trimToNull(terms[1]) : null;

        return new String[]{lastName, firstName};
    }
}
