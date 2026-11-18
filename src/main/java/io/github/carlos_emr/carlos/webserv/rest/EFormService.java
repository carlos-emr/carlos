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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.eform.EFormInstanceService;
import io.github.carlos_emr.carlos.eform.EFormInstanceService.EFormInstanceException;
import io.github.carlos_emr.carlos.webserv.rest.conversion.EFormConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormInstanceTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormTo1;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.persistence.PersistenceException;
import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * REST service for managing individual eForms in CARLOS EMR.
 *
 * <p>Provides endpoints to load, create, and update eForm templates, and to read, create,
 * revise and delete saved eForm instances ({@code /eform/instance}). All endpoints require
 * valid OAuth authentication and {@code _eform} privilege via {@link SecurityInfoManager};
 * instance endpoints also check that privilege for the instance's patient. eForm HTML content
 * may be large; callers should handle responses accordingly.</p>
 *
 * <p>Instance endpoints keep the web UI's rule that a saved instance is never rewritten: a
 * revision stores a new row and retires the old one, and a delete only retires the row. See
 * {@link EFormInstanceService}.</p>
 *
 * <p>Base path: {@code /eform}</p>
 *
 * @since 2026-03-13
 */
@Path("/eform")
@Component("EFormService")
public class EFormService extends AbstractServiceImpl {
	Logger logger = MiscUtils.getLogger();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private EFormDao eFormDao;

	@Autowired
	private SecurityInfoManager securityInfoManager;

	@Autowired
	private EFormInstanceService eFormInstanceService;

	/**
	 * Retrieves an eForm with the given id, including full HTML content.
	 *
	 * <p>HTTP: {@code GET /eform/{dataId}} — produces {@code application/json}</p>
	 *
	 * @param id Integer the unique eForm identifier (path parameter {@code dataId})
	 * @return {@link RestResponse} containing an {@link EFormTo1} transfer object with full
	 *         HTML content on success, or an error response if the form is not found
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@GET
	@Path("/{dataId}")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> loadEForm(@PathParam("dataId") Integer id) {
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "r", null)) {
			return RestResponse.errorResponse("Access Denied");
		}

		EForm eform = eFormDao.findById(id);

		if (eform == null) {
			logger.warn("EForm not found for id: {}", id);
			throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .entity(RestResponse.errorResponse("Failed to find EForm")).build());
		}
		EFormTo1 transferObj = new EFormConverter(false).getAsTransferObject(getLoggedInInfo(), eform);
		return RestResponse.successResponse(transferObj);
	}

	/**
	 * Saves a new eForm using a typed transfer object.
	 *
	 * <p>Performs name-uniqueness validation before persisting. If the form name is already
	 * in use, the save is aborted and an error response is returned.</p>
	 *
	 * <p>HTTP: {@code POST /eform/} — consumes and produces {@code application/json}</p>
	 *
	 * @param eformTo1 {@link EFormTo1} the eForm data transfer object deserialized from the request body;
	 *                  must contain a unique {@code formName} and non-empty {@code formHtml}
	 * @return {@link RestResponse} containing the persisted {@link EFormTo1} (without HTML) on success,
	 *         or an error response with a descriptive message on failure
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@POST
	@Path("/")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> saveEForm(EFormTo1 eformTo1) {
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "w", null)) {
			return RestResponse.errorResponse("Access Denied");
		}

		EForm eForm = new EFormConverter(false).getAsDomainObject(getLoggedInInfo(), eformTo1);

		EForm nameMatch = eFormDao.findByName(eForm.getFormName());
		if (nameMatch != null) {
			logger.warn("EForm Name Already in Use. Save Aborted");
			return RestResponse.errorResponse("EForm Name Already in Use");
		}

		if (isValidEformData(eForm)) {
			try {
				eFormDao.persist(eForm);
			} catch (PersistenceException e) {
				logger.error("Failed to persist eForm", e);
				return RestResponse.errorResponse("Failed to save EForm");
			}
			LogAction.addLog(getLoggedInInfo().getLoggedInProviderNo(), LogConst.ADD,
					LogConst.CON_FORM, String.valueOf(eForm.getId()), getLoggedInInfo().getIp());

			EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
			return RestResponse.successResponse(transferObj);
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * Saves a new eForm by parsing a raw JSON string.
	 *
	 * <p>Provides an alternative to the typed-object endpoint, useful when the caller
	 * cannot produce a typed {@link EFormTo1} payload. Performs name-uniqueness validation
	 * before persisting.</p>
	 *
	 * <p>HTTP: {@code POST /eform/json} — consumes and produces {@code application/json}</p>
	 *
	 * <p>Expected JSON keys: {@code formName} (String, required), {@code formHtml} (String, required),
	 * {@code formSubject} (String, optional), {@code roleType} (String, optional),
	 * {@code showLatestFormOnly} (boolean, default {@code false}),
	 * {@code patientIndependent} (boolean, default {@code false}).</p>
	 *
	 * @param jsonString String the raw JSON request body; must not be null or blank
	 * @return {@link RestResponse} containing the persisted {@link EFormTo1} (without HTML) on success,
	 *         or an error response with a descriptive message on failure
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@POST
	@Path("/json")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> saveEForm(String jsonString) {
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "w", null)) {
			return RestResponse.errorResponse("Access Denied");
		}

		if (jsonString == null || jsonString.trim().isEmpty()) {
			return RestResponse.errorResponse("Invalid JSON");
		}

		JsonNode jsonObject;
		try {
			jsonObject = objectMapper.readTree(jsonString);
		} catch (IOException | IllegalArgumentException e) {
			logger.error("Failed to parse eForm JSON", e);
			return RestResponse.errorResponse("Invalid JSON");
		}

		String formName = jsonObject.path("formName").asText();
		String formSubject = jsonObject.path("formSubject").asText();
		String formHtml = jsonObject.path("formHtml").asText();

		JsonNode roleTypeNode = jsonObject.path("roleType");
		String roleType = (!roleTypeNode.isMissingNode() && !roleTypeNode.isNull()) ? roleTypeNode.asText() : null;
		boolean showLatestFormOnly = jsonObject.path("showLatestFormOnly").asBoolean(false);
		boolean patientIndependent = jsonObject.path("patientIndependent").asBoolean(false);


		EForm nameMatch = eFormDao.findByName(formName);
		if (nameMatch != null) {
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

		if (isValidEformData(eForm)) {
			try {
				eFormDao.persist(eForm);
			} catch (PersistenceException e) {
				logger.error("Failed to persist eForm from JSON", e);
				return RestResponse.errorResponse("Failed to save EForm");
			}
			LogAction.addLog(getLoggedInInfo().getLoggedInProviderNo(), LogConst.ADD,
					LogConst.CON_FORM, String.valueOf(eForm.getId()), getLoggedInInfo().getIp());

			EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
			return RestResponse.successResponse(transferObj);
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * Updates an existing eForm using a typed transfer object.
	 *
	 * <p>The path parameter {@code dataId} must match the id in the request body;
	 * mismatches are rejected with an error response to prevent accidental cross-form updates.</p>
	 *
	 * <p>HTTP: {@code PUT /eform/{dataId}} — consumes and produces {@code application/json}</p>
	 *
	 * @param dataId   Integer the eForm id from the URL path (must match body id)
	 * @param eformTo1 {@link EFormTo1} the eForm data transfer object; must have a non-null id
	 *                  matching {@code dataId}, and non-empty {@code formHtml} and {@code formName}
	 * @return {@link RestResponse} containing the updated {@link EFormTo1} (without HTML) on success,
	 *         or an error response with a descriptive message on failure
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@PUT
	@Path("/{dataId}")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> updateEForm(@PathParam("dataId") Integer dataId, EFormTo1 eformTo1) {
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "w", null)) {
			return RestResponse.errorResponse("Access Denied");
		}

		if (eformTo1 == null || eformTo1.getId() == null || !dataId.equals(eformTo1.getId())) {
			return RestResponse.errorResponse("Path id does not match payload id");
		}

		EForm eForm = new EFormConverter(false).getAsDomainObject(getLoggedInInfo(), eformTo1);

		if (isValidEformData(eForm)) {
			try {
				eFormDao.merge(eForm);
			} catch (PersistenceException e) {
				logger.error("Failed to merge eForm id={}", dataId, e);
				return RestResponse.errorResponse("Failed to update EForm");
			}
			LogAction.addLog(getLoggedInInfo().getLoggedInProviderNo(), LogConst.UPDATE,
					LogConst.CON_FORM, String.valueOf(eForm.getId()), getLoggedInInfo().getIp());
			EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
			return RestResponse.successResponse(transferObj);
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	/**
	 * Updates an existing eForm by parsing a raw JSON string.
	 *
	 * <p>Authentication is enforced at the start of this method before any parsing or
	 * database operations are performed. The path parameter {@code dataId} is used as the
	 * authoritative form id; any {@code id} field in the JSON body is ignored to prevent
	 * URL/body id mismatch exploits.</p>
	 *
	 * <p>HTTP: {@code PUT /eform/{dataId}/json} — consumes and produces {@code application/json}</p>
	 *
	 * <p>Expected JSON keys: {@code formName} (String, required), {@code formHtml} (String, required),
	 * {@code formSubject} (String, optional — preserved from existing record if absent),
	 * {@code current} (boolean, optional), {@code roleType} (String, optional),
	 * {@code showLatestFormOnly} (boolean, optional), {@code patientIndependent} (boolean, optional).</p>
	 *
	 * @param dataId     Integer the eForm id from the URL path (used exclusively; body id is ignored)
	 * @param jsonString String the raw JSON request body; must not be null or blank
	 * @return {@link RestResponse} containing the updated {@link EFormTo1} (without HTML) on success,
	 *         or an error response with a descriptive message on failure
	 * @throws IllegalStateException if the caller is not authenticated
	 */
	@PUT
	@Path("/{dataId}/json")
	@Consumes("application/json")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormTo1> updateEFormJson(@PathParam("dataId") Integer dataId, String jsonString) {

		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", "w", null)) {
			return RestResponse.errorResponse("Access Denied");
		}

		if (jsonString == null || jsonString.trim().isEmpty()) {
			return RestResponse.errorResponse("Invalid JSON");
		}

		JsonNode jsonObject;
		try {
			jsonObject = objectMapper.readTree(jsonString);
		} catch (IOException | IllegalArgumentException e) {
			logger.error("Failed to parse eForm JSON", e);
			return RestResponse.errorResponse("Invalid JSON");
		}

		String formName = jsonObject.path("formName").asText();
		String formHtml = jsonObject.path("formHtml").asText();

		EForm eForm = eFormDao.findById(dataId);

		if (eForm != null && eForm.getId() > 0) {

			// only update optional parameters if they are given and non-null
			String formSubject = jsonObject.hasNonNull("formSubject") ? jsonObject.get("formSubject").asText() : eForm.getSubject();
			boolean current = jsonObject.hasNonNull("current") ? jsonObject.get("current").asBoolean() : eForm.isCurrent();
			JsonNode roleTypeNode = jsonObject.path("roleType");
			String roleType = (!roleTypeNode.isMissingNode() && !roleTypeNode.isNull()) ? roleTypeNode.asText() : eForm.getRoleType();
			boolean showLatestFormOnly = jsonObject.hasNonNull("showLatestFormOnly") ? jsonObject.get("showLatestFormOnly").asBoolean() : eForm.isShowLatestFormOnly();
			boolean patientIndependent = jsonObject.hasNonNull("patientIndependent") ? jsonObject.get("patientIndependent").asBoolean() : eForm.isPatientIndependent();


			eForm.setFormName(formName);
			eForm.setFormHtml(formHtml);
			eForm.setSubject(formSubject);
			eForm.setCurrent(current);
			eForm.setShowLatestFormOnly(showLatestFormOnly);
			eForm.setPatientIndependent(patientIndependent);

			eForm.setRoleType(roleType);

			if (isValidEformData(eForm)) {
				try {
					eFormDao.merge(eForm);
				} catch (PersistenceException e) {
					logger.error("Failed to merge eForm id={}", dataId, e);
					return RestResponse.errorResponse("Failed to update EForm");
				}
				LogAction.addLog(getLoggedInInfo().getLoggedInProviderNo(), LogConst.UPDATE,
						LogConst.CON_FORM, String.valueOf(eForm.getId()), getLoggedInInfo().getIp());
				EFormTo1 transferObj = new EFormConverter(true).getAsTransferObject(getLoggedInInfo(), eForm);
				return RestResponse.successResponse(transferObj);
			}
		}
		return RestResponse.errorResponse("Invalid Eform Data");
	}

	// ─── Saved instances (eform_data) ──────────────────────────────────────────

	/**
	 * Retrieves a saved eForm instance with its rendered HTML and stored field values. Retired
	 * instances (deleted or replaced by a revision) are returned too, with {@code current=false}.
	 *
	 * <p>HTTP: {@code GET /eform/instance/{fdid}} — produces {@code application/json}</p>
	 *
	 * @param fdid Integer the instance id
	 * @return {@link RestResponse} with the {@link EFormInstanceTo1}, including {@code formHtml};
	 *         HTTP 404 when no instance has this id
	 */
	@GET
	@Path("/instance/{fdid}")
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormInstanceTo1> getEFormInstance(@PathParam("fdid") Integer fdid) {
		// Coarse gate; EFormInstanceService then checks the right for the instance's patient and role.
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", SecurityInfoManager.READ, null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		try {
			EFormInstanceService.Instance instance = eFormInstanceService.get(getLoggedInInfo(), fdid);
			EFormInstanceTo1 transferObj = toInstanceTransferObject(instance.data());
			transferObj.setValues(new LinkedHashMap<>(instance.values()));
			transferObj.setFormHtml(instance.data().getFormData());
			return RestResponse.successResponse(transferObj);
		} catch (SecurityException e) {
			return RestResponse.errorResponse("Access Denied");
		} catch (EFormInstanceException e) {
			return instanceErrorResponse(e);
		}
	}

	/**
	 * Saves a new instance of an active eForm template for a patient, filling the template with the
	 * given field values the same way the browser save does.
	 *
	 * <p>HTTP: {@code POST /eform/instance} — consumes and produces {@code application/json}</p>
	 *
	 * @param request {@link EFormInstanceTo1} with {@code fid} and {@code demographicNo} (required),
	 *                and {@code subject} (defaults to the template's) and {@code values} (optional)
	 * @return {@link RestResponse} with the saved {@link EFormInstanceTo1} (without HTML); its
	 *         {@code fdid} identifies the new instance
	 */
	@POST
	@Path("/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormInstanceTo1> createEFormInstance(EFormInstanceTo1 request) {
		// Coarse gate; EFormInstanceService then checks the right for the instance's patient and role.
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", SecurityInfoManager.WRITE, null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		if (request == null || request.getFid() == null || request.getDemographicNo() == null) {
			return RestResponse.errorResponse("fid and demographicNo are required");
		}
		try {
			EFormData saved = eFormInstanceService.create(getLoggedInInfo(), request.getFid(),
					request.getDemographicNo(), request.getSubject(), request.getValues());
			return RestResponse.successResponse(toSavedInstanceTransferObject(saved, request));
		} catch (SecurityException e) {
			return RestResponse.errorResponse("Access Denied");
		} catch (EFormInstanceException e) {
			return instanceErrorResponse(e);
		}
	}

	/**
	 * Revises a saved instance: stores a new instance built from the template and the given values,
	 * and retires the old one, in one transaction. The response carries the new {@code fdid}; the
	 * old id stays readable as history but can no longer be revised or deleted.
	 *
	 * <p>The values replace the old ones completely: a field left out gets the template default.
	 * {@code fid} and {@code demographicNo} cannot be changed and are ignored in the body.</p>
	 *
	 * <p>HTTP: {@code PUT /eform/instance/{fdid}} — consumes and produces {@code application/json}</p>
	 *
	 * @param fdid    Integer the instance to revise; it must be current
	 * @param request {@link EFormInstanceTo1} with {@code values} and optionally {@code subject}
	 *                (defaults to the old instance's)
	 * @return {@link RestResponse} with the new {@link EFormInstanceTo1} (without HTML); HTTP 404
	 *         when no instance has this id, HTTP 409 when it was already deleted or revised
	 */
	@PUT
	@Path("/instance/{fdid}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<EFormInstanceTo1> reviseEFormInstance(@PathParam("fdid") Integer fdid, EFormInstanceTo1 request) {
		// Coarse gate; EFormInstanceService then checks the right for the instance's patient and role.
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", SecurityInfoManager.UPDATE, null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		if (request == null) {
			return RestResponse.errorResponse("Request body is required");
		}
		try {
			EFormData saved = eFormInstanceService.revise(getLoggedInInfo(), fdid, request.getSubject(),
					request.getValues());
			return RestResponse.successResponse(toSavedInstanceTransferObject(saved, request));
		} catch (SecurityException e) {
			return RestResponse.errorResponse("Access Denied");
		} catch (EFormInstanceException e) {
			return instanceErrorResponse(e);
		}
	}

	/**
	 * Deletes a saved instance the way the eForm list's delete button does: the row is retired
	 * ({@code current=false}), not removed, and can be restored from the deleted-eForms list.
	 *
	 * <p>HTTP: {@code DELETE /eform/instance/{fdid}} — produces {@code application/json}</p>
	 *
	 * @param fdid Integer the instance to delete; it must be current
	 * @return {@link RestResponse} whose body is the retired {@code fdid}; HTTP 404 when no instance
	 *         has this id, HTTP 409 when it was already deleted or revised
	 */
	@DELETE
	@Path("/instance/{fdid}")
	// Explicit, although DELETE has no body: without it CXF answers 415 to a client that sends
	// Content-Type: application/json on every request, as JSON clients commonly do.
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	public RestResponse<Integer> deleteEFormInstance(@PathParam("fdid") Integer fdid) {
		// Coarse gate; EFormInstanceService then checks the right for the instance's patient and role.
		if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_eform", SecurityInfoManager.WRITE, null)) {
			return RestResponse.errorResponse("Access Denied");
		}
		try {
			eFormInstanceService.delete(getLoggedInInfo(), fdid);
			return RestResponse.successResponse(fdid);
		} catch (SecurityException e) {
			return RestResponse.errorResponse("Access Denied");
		} catch (EFormInstanceException e) {
			return instanceErrorResponse(e);
		}
	}

	private static EFormInstanceTo1 toInstanceTransferObject(EFormData data) {
		EFormInstanceTo1 transferObj = new EFormInstanceTo1();
		transferObj.setFdid(data.getId());
		transferObj.setFid(data.getFormId());
		transferObj.setDemographicNo(data.getDemographicId());
		transferObj.setFormName(data.getFormName());
		transferObj.setSubject(data.getSubject());
		transferObj.setProviderNo(data.getProviderNo());
		transferObj.setFormDate(data.getFormDate());
		transferObj.setFormTime(data.getFormTime());
		transferObj.setCurrent(data.isCurrent());
		return transferObj;
	}

	/** A just-saved instance echoes the values it was saved from, without the (large) HTML. */
	private static EFormInstanceTo1 toSavedInstanceTransferObject(EFormData saved, EFormInstanceTo1 request) {
		EFormInstanceTo1 transferObj = toInstanceTransferObject(saved);
		if (request.getValues() != null) {
			transferObj.setValues(new LinkedHashMap<>(request.getValues()));
		}
		return transferObj;
	}

	/**
	 * Maps a refused instance operation to a response. A missing instance is a 404, as for
	 * templates, and a retired one is a 409, so a client can tell "re-read for the newer revision"
	 * apart from a bad request. Other refusals keep this service's 200-with-error convention.
	 */
	private <T> RestResponse<T> instanceErrorResponse(EFormInstanceException e) {
		Response.Status status = switch (e.getReason()) {
			case NOT_FOUND -> Response.Status.NOT_FOUND;
			case RETIRED -> Response.Status.CONFLICT;
			default -> null;
		};
		if (status == null) {
			return RestResponse.errorResponse(e.getReason().message());
		}
		throw new WebApplicationException(Response.status(status)
				.entity(RestResponse.errorResponse(e.getReason().message())).build());
	}

	/**
	 * Validates basic eForm data before persisting or merging.
	 *
	 * @param eForm {@link EForm} the eForm entity to validate; may be {@code null}
	 * @return {@code true} if the eForm is non-null with non-empty {@code formHtml} and {@code formName};
	 *         {@code false} otherwise
	 */
	private boolean isValidEformData(EForm eForm) {
		if (eForm == null)
			return false;
		if (eForm.getFormHtml() == null || eForm.getFormHtml().trim().isEmpty())
			return false;
		if (eForm.getFormName() == null || eForm.getFormName().trim().isEmpty())
			return false;
		return true;
	}
}
