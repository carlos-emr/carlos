/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package org.oscarehr.ws.rest;

import net.sf.json.JSONObject;
import org.apache.logging.log4j.Logger;
import org.oscarehr.util.MiscUtils;
import org.oscarehr.common.dao.EFormDao;
import org.oscarehr.common.model.EForm;
import org.oscarehr.ws.rest.conversion.EFormConverter;
import org.oscarehr.ws.rest.response.RestResponse;
import org.oscarehr.ws.rest.to.model.EFormTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import oscar.log.LogAction;
import oscar.log.LogConst;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/eform")
@Component("EFormService")
public class EFormService extends AbstractServiceImpl {
	Logger logger = MiscUtils.getLogger();

	@Autowired
	private EFormDao eFormDao;

	/**
	 * retrieves an eForm with the given id.
	 * @return ResponseEntity containing data for the eForm. includes full html
	 */
	@GET
	@Path("/{dataId}")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> loadEForm(@PathParam("dataId") Integer id) {

		EForm eform = eFormDao.findById(id);

		if(eform == null) {
			return RestResponse.errorResponse("Failed to find EForm");
		}
		EFormTo1 transferObj = new EFormConverter(false).getAsTransferObject(getLoggedInInfo(), eform);
		return RestResponse.successResponse(transferObj);
	}

	/**
	 * Saves an eform. Performs an update if the eform has an fid, otherwise it will save a new eform.
	 * @return RestResponse with data from the eform saved. response does not include html
	 */
	@POST
	@Path("/")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> saveEForm(EFormTo1 eformTo1) {

		EForm eForm = new EFormConverter(false).getAsDomainObject(getLoggedInInfo(), eformTo1);

		EForm nameMatch = eFormDao.findByName(eForm.getFormName());
		if(nameMatch != null) {
			logger.warn("EForm Name Already in Use. Save Aborted");
			return RestResponse.errorResponse("EForm Name Already in Use");
		}

		if(isValidEformData(eForm)) {
			eFormDao.persist(eForm);
			LogAction.addLogEntry(getLoggedInInfo().getLoggedInProviderNo(), null,
					LogConst.ACTION_ADD, LogConst.CON_EFORM_TEMPLATE, LogConst.STATUS_SUCCESS,
					String.valueOf(eForm.getId()), getLoggedInInfo().getIp(), eForm.getFormName());

			EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
			return RestResponse.successResponse(transferObj);
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * Saves an eform. Parses plain json instead of a transfer object
	 * @param jsonString
	 * @return RestResponse with data from the eform saved. response does not include html
	 */
	@POST
	@Path("/json")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> saveEForm(String jsonString) {

		JSONObject jsonObject = JSONObject.fromObject(jsonString);

		String formName = jsonObject.getString("formName");
		String formSubject = jsonObject.optString("formSubject");
		String formHtml = jsonObject.getString("formHtml");

		String roleType = jsonObject.optString("roleType", null);
		Boolean showLatestFormOnly = jsonObject.optBoolean("showLatestFormOnly", false);
		Boolean patientIndependent = jsonObject.optBoolean("patientIndependant", false);
		

		EForm nameMatch = eFormDao.findByName(formName);
		if(nameMatch != null) {
			logger.warn("EForm Name Already in Use. Save Aborted");
			return RestResponse.errorResponse("EForm Name Already in Use");
		}

		EForm eForm = new EForm();
		String creatorId = getLoggedInInfo().getLoggedInProviderNo();
		eForm.setCreator(creatorId);

		eForm.setFormName(formName);
		eForm.setSubject(formSubject);
		eForm.setFormHtml(formHtml);
		eForm.setCurrent(true);
		eForm.setShowLatestFormOnly(showLatestFormOnly);
		eForm.setPatientIndependent(patientIndependent);
		
		eForm.setRoleType(roleType);

		if(isValidEformData(eForm)) {
			eFormDao.persist(eForm);
			LogAction.addLogEntry(getLoggedInInfo().getLoggedInProviderNo(), null,
					LogConst.ACTION_ADD, LogConst.CON_EFORM_TEMPLATE, LogConst.STATUS_SUCCESS,
					String.valueOf(eForm.getId()), getLoggedInInfo().getIp(), eForm.getFormName());

			EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
			return RestResponse.successResponse(transferObj);
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * Updates an eform with the given id.
	 * @return RestResponse with data from the eform saved. response does not include html
	 */
	@PUT
	@Path("/{dataId}")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> updateEForm(EFormTo1 eformTo1) {

		EForm eForm = new EFormConverter(false).getAsDomainObject(getLoggedInInfo(), eformTo1);

		if(isValidEformData(eForm)) {
			eFormDao.merge(eForm);
			LogAction.addLogEntry(getLoggedInInfo().getLoggedInProviderNo(), null,
					LogConst.ACTION_UPDATE, LogConst.CON_EFORM_TEMPLATE, LogConst.STATUS_SUCCESS,
					String.valueOf(eForm.getId()), getLoggedInInfo().getIp(), eForm.getFormName());
			EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
			return RestResponse.successResponse(transferObj);
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * Updates an eform with the given id. Parses plain json instead of a transfer object
	 * @param jsonString
	 * @return RestResponse with data from the eform saved. response does not include html
	 */
	@PUT
	@Path("/{dataId}/json")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> updateEFormJson(String jsonString) {

		JSONObject jsonObject = JSONObject.fromObject(jsonString);

		Integer fid = jsonObject.optInt("id");
		String formName = jsonObject.getString("formName");
		String formHtml = jsonObject.getString("formHtml");

		EForm eForm = eFormDao.findById(fid);

		if(eForm != null && eForm.getId() > 0) {

			// only update optional parameters if they are given
			String formSubject = jsonObject.optString("formSubject", eForm.getSubject());
			Boolean current = jsonObject.optBoolean("current", eForm.isCurrent());
			String roleType = jsonObject.optString("roleType", eForm.getRoleType());
			Boolean showLatestFormOnly = jsonObject.optBoolean("showLatestFormOnly", eForm.isShowLatestFormOnly());
			Boolean patientIndependant = jsonObject.optBoolean("patientIndependant", eForm.isPatientIndependent());
			

			eForm.setFormName(formName);
			eForm.setFormHtml(formHtml);
			eForm.setSubject(formSubject);
			eForm.setCurrent(current);
			eForm.setShowLatestFormOnly(showLatestFormOnly);
			eForm.setPatientIndependent(patientIndependant);
			
			eForm.setRoleType(roleType);

			if(isValidEformData(eForm)) {
				eFormDao.merge(eForm);
				LogAction.addLogEntry(getLoggedInInfo().getLoggedInProviderNo(), null,
						LogConst.ACTION_UPDATE, LogConst.CON_EFORM_TEMPLATE, LogConst.STATUS_SUCCESS,
						String.valueOf(eForm.getId()), getLoggedInInfo().getIp(), eForm.getFormName());
				EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
				return RestResponse.successResponse(transferObj);
			}
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * basic validation for eform data
	 * @param eForm
	 * @return true if data is valid, false otherwise
	 */
	private boolean isValidEformData(EForm eForm) {
		if(eForm == null)
			return false;
		if(eForm.getFormHtml() == null || eForm.getFormHtml().trim().isEmpty())
			return false;
		if(eForm.getFormName() == null || eForm.getFormName().trim().isEmpty())
			return false;
		return true;
	}
}
