/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.webserv.rest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.webserv.rest.conversion.EFormConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormTo1;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST service for retrieving eForm listings and supporting reference data in CARLOS EMR.
 *
 * <p>Provides read-only endpoints to query eForm collections, available images, and eForm
 * database tag names. All endpoints require the caller to have {@code _eform} read
 * privilege; requests failing authorization receive an error {@link RestResponse}.</p>
 *
 * <p>Base path: {@code /eforms}</p>
 *
 * @since 2026-03-13
 */
@Path("/eforms")
@Component("EFormsService")
/**
 * REST service for bulk electronic forms listing and management operations.
 *
 * @since 2012-08-13
 */
public class EFormsService extends AbstractServiceImpl
{
	Logger logger = MiscUtils.getLogger();

	@Autowired
	private FormsManager formsManager;

	@Autowired
	private SecurityInfoManager securityInfoManager;

	/**
	 * Retrieves a list of all active eForms sorted by name.
	 *
	 * <p>eForm responses do not contain HTML content; use {@code GET /eform/{dataId}} to
	 * retrieve full HTML for a specific form.</p>
	 *
	 * <p>HTTP: {@code GET /eforms/} — produces {@code application/json}</p>
	 *
	 * @return {@link RestResponse} containing a {@code List<}{@link EFormTo1}{@code >} of all
	 *         active eForms sorted by name on success, or an error response if authorization fails
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<List<EFormTo1>> getEFormList()
	{
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "r", null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		List<EFormTo1> allEforms = new EFormConverter(true).getAllAsTransferObjects(getLoggedInInfo(),
				formsManager.findByStatus(getLoggedInInfo(), true, EFormDao.EFormSortOrder.NAME));
		return RestResponse.successResponse(allEforms);
	}

	/**
	 * Retrieves a sorted list of all eForm image filenames from the configured image directory.
	 *
	 * <p>Only files matching the extensions {@code .jpg}, {@code .jpeg}, {@code .png}, or
	 * {@code .gif} are included.</p>
	 *
	 * <p>HTTP: {@code GET /eforms/images} — produces {@code application/json}</p>
	 *
	 * @return {@link RestResponse} containing a {@code List<String>} of image filenames
	 *         (sorted alphabetically) on success, or an error response if authorization fails
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@GET
	@Path("/images")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<List<String>> getEFormImageList()
	{
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "r", null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		String imageHomeDir = CarlosProperties.getInstance().getEformImageDirectory();
		if (imageHomeDir == null || imageHomeDir.trim().isEmpty()) {
			logger.error("eForm image directory is not configured");
			return RestResponse.errorResponse("Image directory not configured");
		}
		File directory = new File(imageHomeDir);
		if (!directory.isDirectory()) {
			logger.error("eForm image directory does not exist: {}", imageHomeDir);
			return RestResponse.errorResponse("Image directory not available");
		}

		ArrayList<String> imagesNames = DisplayImage2Action.getFiles(directory, ".*\\.(jpg|jpeg|png|gif)$", null);
		Collections.sort(imagesNames);
		return RestResponse.successResponse(imagesNames);
	}

	/**
	 * Retrieves the list of all eForm database tag names supported by the eForm engine.
	 *
	 * <p>These tags are used in eForm HTML to reference patient and provider data
	 * (e.g., {@code patient_name}, {@code today}, {@code hin}).</p>
	 *
	 * <p>HTTP: {@code GET /eforms/databaseTags} — produces {@code application/json}</p>
	 *
	 * @return {@link RestResponse} containing a {@code List<String>} of database tag names
	 *         on success, or an error response if authorization fails or the tag list cannot
	 *         be loaded
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@GET
	@Path("/databaseTags")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<List<String>> getEFormDatabaseTagList()
	{
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "r", null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		List<String> dbTagList;
		try
		{
			EFormLoader loader = EFormLoader.getInstance();
			dbTagList = loader.getNames();
		}
		catch (RuntimeException e)
		{
			logger.error("DB tag Error: ", e);
			return RestResponse.errorResponse("Error retrieving database tag list");
		}
		return RestResponse.successResponse(dbTagList);
	}
}
