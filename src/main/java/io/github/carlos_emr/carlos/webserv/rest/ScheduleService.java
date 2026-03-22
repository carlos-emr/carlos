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
package io.github.carlos_emr.carlos.webserv.rest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.util.DateUtils;
import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.commn.dao.AppointmentSearchDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentSearch;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.web.PatientListApptBean;
import io.github.carlos_emr.carlos.web.PatientListApptItemBean;
import io.github.carlos_emr.carlos.webserv.rest.conversion.AppointmentConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.AppointmentStatusConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.AppointmentTypeConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.BillingDetailConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.LookupListItemConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.NewAppointmentConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ScheduleCodesConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.AppointmentExtResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.ProviderApptsCountResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.ProviderPeriodAppsResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.SchedulingResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentExtTo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentSearchTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentStatusTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.BillingDetailTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NewAppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProviderApptsCountTo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProviderPeriodAppsTo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ScheduleTemplateCodeTo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

/**
 * JAX-RS REST service for appointment scheduling and management.
 *
 * <p>Provides endpoints under {@code /schedule} for CRUD operations on appointments,
 * day/monthly schedule retrieval, appointment status/type management, search
 * configuration, and reporting. All endpoints consume and produce JSON.</p>
 *
 * <p>Supports convenience path substitutions: "me" for the current provider number,
 * and "today" for the current date.</p>
 *
 * @since 2026-03-17
 */
@Path("/schedule")
@Component("scheduleService")
@Consumes(MediaType.APPLICATION_JSON)
public class ScheduleService extends AbstractServiceImpl {

    Logger logger = MiscUtils.getLogger();

    @Autowired
    private ScheduleManager scheduleManager;
    @Autowired
    private AppointmentManager appointmentManager;
    @Autowired
    private DemographicManager demographicManager;
    @Autowired
    private SecurityInfoManager securityInfoManager;
    @Autowired
    private AppointmentSearchDao appointmentSearchDao;
    @Autowired
    private BillingONCHeader1Dao billingONCHeader1Dao;

    /**
     * Retrieves appointments for the current provider on a specific date.
     *
     * @param date String the date in ISO 8601 format (yyyy-MM-dd), or "today"
     * @return PatientListApptItemBean[] array of appointment items for the day
     */
    @GET
    @Path("/day/{date}")
    @Produces("application/json")
    public PatientListApptItemBean[] getAppointmentsForDay(@PathParam("date") String date) {
        String providerNo = this.getCurrentProvider().getProviderNo();
        return getAppointmentsForDay(providerNo, date);
    }

    /**
     * Retrieves appointments for a specific provider on a specific date.
     *
     * <p>Supports convenience substitutions: "me" resolves to the current
     * provider number, and "today" resolves to the current date.
     * Example: {@code /schedule/me/day/today}</p>
     *
     * @param providerNo String the provider number, or "me" for the current provider
     * @param date String the date in ISO 8601 format (yyyy-MM-dd), or "today"
     * @return PatientListApptItemBean[] array of appointment items for the day
     * @throws RuntimeException if the date format is invalid
     */
    @GET
    @Path("/{providerNo}/day/{date}")
    @Produces("application/json")
    public PatientListApptItemBean[] getAppointmentsForDay(@PathParam("providerNo") String providerNo, @PathParam("date") String date) {
        SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm aa");
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        PatientListApptBean response = new PatientListApptBean();

        try {
            Date dateObj = null;
            if ("today".equals(date)) {
                dateObj = new Date();
            } else {
                dateObj = DateUtils.parseIso8601Date(date);
            }

            if ("".equals(providerNo)) {
                providerNo = loggedInInfo.getLoggedInProviderNo();
            }

            List<Appointment> appts = scheduleManager.getDayAppointments(loggedInInfo, providerNo, dateObj);
            for (Appointment appt : appts) {
                PatientListApptItemBean item = new PatientListApptItemBean();
                item.setDemographicNo(appt.getDemographicNo());
                if (appt.getDemographicNo() == 0) {
                    item.setName(appt.getName());
                } else {
                    item.setName(demographicManager.getDemographicFormattedName(loggedInInfo, appt.getDemographicNo()));
                }
                item.setStartTime(timeFormatter.format(appt.getStartTime()));
                item.setReason(appt.getReason());
                item.setStatus(appt.getStatus());
                item.setAppointmentNo(appt.getId());
                item.setDate(appt.getStartTimeAsFullDate());
                item.setDuration(appt.getDuration());
                item.setType(appt.getType());
                item.setNotes(appt.getNotes());
                response.getPatients().add(item);
            }
        } catch (ParseException e) {
            throw new RuntimeException("Invalid Date sent, use yyyy-MM-dd format");
        }
        return response.getPatients().toArray(new PatientListApptItemBean[response.getPatients().size()]);
    }

    /**
     * Retrieves all configured appointment statuses.
     *
     * @return AbstractSearchResponse containing AppointmentStatusTo1 transfer objects
     */
    @GET
    @Path("/statuses")
    @Produces("application/json")
    public AbstractSearchResponse<AppointmentStatusTo1> getAppointmentStatuses() {
        AbstractSearchResponse<AppointmentStatusTo1> response = new AbstractSearchResponse<AppointmentStatusTo1>();

        List<AppointmentStatus> results = scheduleManager.getAppointmentStatuses(getLoggedInInfo());
        AppointmentStatusConverter converter = new AppointmentStatusConverter();

        response.setContent(converter.getAllAsTransferObjects(getLoggedInInfo(), results));
        response.setTotal(results.size());

        return response;
    }

    /**
     * Creates a new appointment.
     *
     * @param appointmentTo NewAppointmentTo1 the appointment data to create
     * @return SchedulingResponse containing the newly created appointment
     */
    @POST
    @Path("/add")
    @Produces("application/json")
    @Consumes("application/json")
    public SchedulingResponse addAppointment(NewAppointmentTo1 appointmentTo) {
        SchedulingResponse response = new SchedulingResponse();

        NewAppointmentConverter converter = new NewAppointmentConverter();

        //TODO: Need to add some more validation here

        Appointment appt = converter.getAsDomainObject(getLoggedInInfo(), appointmentTo);

        appointmentManager.addAppointment(getLoggedInInfo(), appt);

        response.setAppointment(new AppointmentConverter().getAsTransferObject(getLoggedInInfo(), appt));

        return response;
    }

    /**
     * Retrieves a single appointment by ID with full details.
     *
     * @param appointmentTo AppointmentTo1 containing the appointment ID to retrieve
     * @return SchedulingResponse containing the appointment details
     */
    @POST
    @Path("/getAppointment")
    @Produces("application/json")
    @Consumes("application/json")
    public SchedulingResponse getAppointment(AppointmentTo1 appointmentTo) {
        SchedulingResponse response = new SchedulingResponse();

        AppointmentConverter converter = new AppointmentConverter(true, true);

        Appointment appt = appointmentManager.getAppointment(getLoggedInInfo(), appointmentTo.getId());

        response.setAppointment(converter.getAsTransferObject(getLoggedInInfo(), appt));

        return response;
    }

    /**
     * Deletes an appointment by ID.
     *
     * @param appointmentTo AppointmentTo1 containing the appointment ID to delete
     * @return Response with OK status on success
     */
    @POST
    @Path("/deleteAppointment")
    @Consumes("application/json")
    @Produces("application/json")
    public Response deleteAppointment(AppointmentTo1 appointmentTo) {

        appointmentManager.deleteAppointment(getLoggedInInfo(), appointmentTo.getId());

        return Response.status(Status.OK).build();
    }

    /**
     * Updates an existing appointment.
     *
     * @param appointmentTo AppointmentTo1 the updated appointment data
     * @return SchedulingResponse containing the updated appointment
     */
    @POST
    @Path("/updateAppointment")
    @Consumes("application/json")
    @Produces("application/json")
    public SchedulingResponse updateAppointment(AppointmentTo1 appointmentTo) {
        SchedulingResponse response = new SchedulingResponse();

        AppointmentConverter converter = new AppointmentConverter();
        Appointment appt = converter.getAsDomainObject(getLoggedInInfo(), appointmentTo);

        scheduleManager.updateAppointment(getLoggedInInfo(), appt);

        response.setAppointment(converter.getAsTransferObject(getLoggedInInfo(), appt));
        return response;
    }

    /**
     * Retrieves appointment history for a patient, including billing details where available.
     *
     * @param demographicNo Integer the patient demographic number
     * @return SchedulingResponse containing the patient's appointment history with billing info
     */
    @POST
    @Path("/{demographicNo}/appointmentHistory")
    @Consumes("application/json")
    @Produces("application/json")
    public SchedulingResponse findExistAppointments(@PathParam("demographicNo") Integer demographicNo) {
        SchedulingResponse response = new SchedulingResponse();
        List<AppointmentTo1> appts = getAppointmentHistoryWithoutDeleted(demographicNo);

        Map<Integer, BillingDetailTo1> apptIdBillingMap = getAppointmentIdToBillingDetailMap(demographicNo);

        for (AppointmentTo1 appt : appts) {
            if (apptIdBillingMap.containsKey(appt.getId())) {
                appt.setBillingDetail(apptIdBillingMap.get(appt.getId()));
            }
        }

        response.setAppointments(appts);
        return response;
    }

    /**
     * Builds a map of appointment IDs to their corresponding billing details for a patient.
     *
     * @param demographicNo Integer the patient demographic number
     * @return Map mapping appointment IDs to BillingDetailTo1 transfer objects
     */
    private Map<Integer, BillingDetailTo1> getAppointmentIdToBillingDetailMap(Integer demographicNo) {
        List<BillingONCHeader1> billingHeaders = billingONCHeader1Dao.findByDemoNo(demographicNo, 0, OscarAppointmentDao.MAX_LIST_RETURN_SIZE);
        if (billingHeaders.size() == OscarAppointmentDao.MAX_LIST_RETURN_SIZE) {
            logger.warn("Billing history over MAX_LIST_RETURN_SIZE for demographic " + demographicNo);
        }

        BillingDetailConverter converter = new BillingDetailConverter();
        List<BillingDetailTo1> billingDetails = converter.getAllAsTransferObjects(getLoggedInInfo(), billingHeaders);

        Map<Integer, BillingDetailTo1> apptIdBillingMap = new HashMap<Integer, BillingDetailTo1>();
        for (BillingDetailTo1 billingDetail : billingDetails) {
            apptIdBillingMap.put(billingDetail.getAppointmentNo(), billingDetail);
        }
        return apptIdBillingMap;
    }

    /**
     * Retrieves appointment history for a patient, excluding deleted appointments.
     *
     * @param demographicNo Integer the patient demographic number
     * @return List of AppointmentTo1 transfer objects
     */
    private List<AppointmentTo1> getAppointmentHistoryWithoutDeleted(Integer demographicNo) {
        SchedulingResponse response = new SchedulingResponse();
        List<Appointment> appts = appointmentManager.getAppointmentHistoryWithoutDeleted(getLoggedInInfo(), demographicNo, 0, OscarAppointmentDao.MAX_LIST_RETURN_SIZE);
        if (appts.size() == OscarAppointmentDao.MAX_LIST_RETURN_SIZE) {
            logger.warn("appointment history over MAX_LIST_RETURN_SIZE for demographic " + demographicNo);
        }
        AppointmentConverter converter = new AppointmentConverter();
        return converter.getAllAsTransferObjects(getLoggedInInfo(), appts);
    }

    /**
     * Updates the status of an existing appointment.
     *
     * @param id Integer the appointment ID
     * @param appt AppointmentTo1 containing the new status value
     * @return SchedulingResponse containing the updated appointment
     */
    @POST
    @Path("/appointment/{id}/updateStatus")
    @Produces("application/json")
    @Consumes("application/json")
    public SchedulingResponse updateAppointmentStatus(@PathParam("id") Integer id, AppointmentTo1 appt) {
        SchedulingResponse response = new SchedulingResponse();
        AppointmentConverter converter = new AppointmentConverter();
        String status = appt.getStatus();

        Appointment appointment = appointmentManager.updateAppointmentStatus(getLoggedInInfo(), id, status);

        response.setAppointment(converter.getAsTransferObject(getLoggedInInfo(), appointment));

        return response;
    }

    /**
     * Updates the type of an existing appointment.
     *
     * @param id Integer the appointment ID
     * @param appt AppointmentTo1 containing the new type value
     * @return SchedulingResponse containing the updated appointment
     */
    @POST
    @Path("/appointment/{id}/updateType")
    @Produces("application/json")
    @Consumes("application/json")
    public SchedulingResponse updateAppointmentType(@PathParam("id") Integer id, AppointmentTo1 appt) {
        SchedulingResponse response = new SchedulingResponse();
        AppointmentConverter converter = new AppointmentConverter();
        String type = appt.getType();

        Appointment appointment = appointmentManager.updateAppointmentType(getLoggedInInfo(), id, type);

        response.setAppointment(converter.getAsTransferObject(getLoggedInInfo(), appointment));

        return response;
    }

    /**
     * Updates the urgency level of an existing appointment.
     *
     * @param id Integer the appointment ID
     * @param appt AppointmentTo1 containing the new urgency value
     * @return SchedulingResponse containing the updated appointment
     */
    @Path("/appointment/{id}/updateUrgency")
    @Produces("application/json")
    @Consumes("application/json")
    public SchedulingResponse updateAppointmentUrgency(@PathParam("id") Integer id, AppointmentTo1 appt) {
        SchedulingResponse response = new SchedulingResponse();
        AppointmentConverter converter = new AppointmentConverter();
        String urgency = appt.getUrgency();

        Appointment appointment = appointmentManager.updateAppointmentUrgency(getLoggedInInfo(), id, urgency);

        response.setAppointment(converter.getAsTransferObject(getLoggedInInfo(), appointment));

        return response;
    }

    /**
     * Retrieves all appointments for a provider in a specific month.
     *
     * @param year Integer the four-digit year
     * @param month Integer the month (1-12)
     * @param providerNo String the provider number
     * @return SchedulingResponse containing the month's appointments
     */
    @GET
    @Path("/fetchMonthly/{providerNo}/{year}/{month}")
    @Produces("application/json")
    public SchedulingResponse fetchMonthlyData(@PathParam("year") Integer year, @PathParam("month") Integer month, @PathParam("providerNo") String providerNo) {
        SchedulingResponse response = new SchedulingResponse();

        List<Appointment> appts = appointmentManager.findMonthlyAppointments(getLoggedInInfo(), providerNo, year, month);

        AppointmentConverter converter = new AppointmentConverter();
        response.setAppointments(converter.getAllAsTransferObjects(getLoggedInInfo(), appts));

        return response;
    }

	/*
	 * These are some method from the ERO branch which I didn't get to.
	 * 
	@GET
	@Path("/{startDate}/{endDate}/{providerId}/fetchFlipView")
	@Produces("application/json")
	public Response fetchFlipView(@PathParam("startDate") String startDate, @PathParam("endDate") String endDate, @PathParam("providerId") String providerId) {
		return Response.status(Status.OK).build();
	}

	@GET
	@Path("/{appDate}/{providerId}/{startTime}/{endTime}/checkProvAvali")
	@Produces("application/json")
	public Response checkProviderAvaliablity(@PathParam("appDate") String appDate, @PathParam("providerId") String providerId, @PathParam("startTime") String startTime, @PathParam("endTime") String endTime) {
		return Response.status(Status.OK).build();
	}

	@GET
	@Path("/blockreason/get")
	@Produces("application/json")
	public Response getBlockTimeReason() {
		return Response.status(Status.OK).build();
	}

	@GET
	@Path("/scheduleTempCode/get")
	@Produces("application/json")
	public Response fetchScheduleTempCode() {
		return Response.status(Status.OK).build();
	}
*/

    /**
     * Retrieves all configured appointment types.
     *
     * @return SchedulingResponse containing the list of appointment types
     */
    @GET
    @Path("/types")
    @Produces("application/json")
    public SchedulingResponse getAppointmentTypes() {
        SchedulingResponse response = new SchedulingResponse();

        List<AppointmentType> types = scheduleManager.getAppointmentTypes();

        AppointmentTypeConverter converter = new AppointmentTypeConverter();

        response.setTypes(converter.getAllAsTransferObjects(getLoggedInInfo(), types));

        return response;
    }

    /**
     * Retrieves all schedule template codes (appointment slot type definitions).
     *
     * @return List of ScheduleTemplateCodeTo transfer objects
     */
    @GET
    @Path("/codes")
    @Produces("application/json")
    public List<ScheduleTemplateCodeTo> getScheduleTemplateCodes() {
        SchedulingResponse response = new SchedulingResponse();
        List<ScheduleTemplateCode> codes = scheduleManager.getScheduleTemplateCodes();

        ScheduleCodesConverter converter = new ScheduleCodesConverter();

        return converter.getAllAsTransferObjects(getLoggedInInfo(), codes);


    }


    /**
     * Retrieves all configured appointment reasons.
     *
     * @return SchedulingResponse containing the list of appointment reasons
     */
    @GET
    @Path("/reasons")
    @Produces("application/json")
    public SchedulingResponse getAppointmentReasons() {

        SchedulingResponse response = new SchedulingResponse();

        List<LookupListItem> items = appointmentManager.getReasons();

        LookupListItemConverter converter = new LookupListItemConverter();

        response.setReasons(converter.getAllAsTransferObjects(getLoggedInInfo(), items));

        return response;
    }

    /**
     * Lists appointments with extended demographic details for a date range and set of providers.
     *
     * @param sDateStr String start date in ISO 8601 format (yyyy-MM-dd)
     * @param eDateStr String end date in ISO 8601 format (yyyy-MM-dd)
     * @param providers String comma-separated list of provider numbers
     * @return AppointmentExtResponse containing extended appointment data
     * @throws WebApplicationException if parameters are missing or malformed
     */
    @GET
    @Path("/fetchDays/{sDate}/{eDate}/{providers}")
    @Produces(MediaType.APPLICATION_JSON)
    public AppointmentExtResponse listAppointmentsByPeriodProvider(@PathParam(value = "sDate") String sDateStr,
                                                                   @PathParam(value = "eDate") String eDateStr,
                                                                   @PathParam(value = "providers") String providers) {
        if (sDateStr == null || sDateStr.length() == 0
                || eDateStr == null || sDateStr.length() == 0
                || providers == null)
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity("Required path parameter is missing").build());

        try {
            Date sDate = null;
            Date eDate = null;
            try {
                sDate = org.apache.tools.ant.util.DateUtils.parseIso8601Date(sDateStr);
                eDate = org.apache.tools.ant.util.DateUtils.parseIso8601Date(eDateStr);
            } catch (Exception e) {
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity("Path parameter has the wrong format").build());
            }

            List<Object[]> items = scheduleManager.listAppointmentsByPeriodProvider(getLoggedInInfo(), sDate, eDate, providers);

            AppointmentExtResponse response = new AppointmentExtResponse();
            if (items != null && items.size() > 0) {
                if (response.getContent() == null) response.setContent(new ArrayList<AppointmentExtTo>());
                for (Object[] obj : items) {
                    Integer appointmentNo = (Integer) obj[0];
                    String providerNo = (String) obj[1];
                    Date appointmentDate = (Date) obj[2];
                    Date startTime = (Date) obj[3];
                    Integer demographicNo = (Integer) obj[4];
                    String notes = (String) obj[5];
                    String location = (String) obj[6];
                    String resources = (String) obj[7];
                    Character status = (Character) obj[8];
                    String lastName = (String) obj[9];
                    String firstName = (String) obj[10];
                    String phone = (String) obj[11];
                    String phone2 = (String) obj[12];
                    String email = (String) obj[13];
                    String demoCell = (String) obj[14];
                    String reminderPreference = (String) obj[15];
                    String hPhoneExt = (String) obj[16];
                    String wPhoneExt = (String) obj[17];

                    AppointmentExtTo to = new AppointmentExtTo(appointmentNo, providerNo, appointmentDate, startTime,
                            demographicNo, notes, location, resources, status,
                            lastName, firstName, phone, phone2, email,
                            demoCell, reminderPreference, hPhoneExt, wPhoneExt);

                    response.getContent().add(to);
                }
            }

            return response;
        } catch (Exception e) {
            logger.error("ScheduleService.listAppointmentsByPeriodProvider error", e);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity("Internal server error").build());
        }

    }


    /**
     * Lists appointment counts for all providers within a date range.
     *
     * @param sDateStr start date in format "yyyy-MM-dd"
     * @param eDateStr end date in format "yyyy-MM-dd"
     * @return ProviderApptsCountResponse containing appointment counts
     */
    @GET
    @Path("/fetchProvidersApptsCount/{sDate}/{eDate}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProviderApptsCountResponse listProviderAppointmentCounts(@PathParam(value = "sDate") String sDateStr, @PathParam(value = "eDate") String eDateStr) {
        if (sDateStr == null || eDateStr == null)
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity("Required path parameter is miossing").build());

        try {
            List<Object[]> items = scheduleManager.listProviderAppointmentCounts(getLoggedInInfo(), sDateStr, eDateStr);

            ProviderApptsCountResponse response = new ProviderApptsCountResponse();
            if (items != null && items.size() > 0) {
                if (response.getContent() == null) response.setContent(new ArrayList<ProviderApptsCountTo>());
                for (Object[] obj : items) {
                    String providerNo = (String) obj[0];
                    String firstName = (String) obj[1];
                    String lastName = (String) obj[2];
                    Long appointmentsCount = ((Number) obj[3]).longValue();

                    ProviderApptsCountTo to = new ProviderApptsCountTo(providerNo, lastName + ", " + firstName, appointmentsCount);

                    if (appointmentsCount > 0) {
                        if (response.getContent() == null) response.setContent(new ArrayList<ProviderApptsCountTo>());
                        response.getContent().add(to);
                    }
                }
            }

            return response;
        } catch (Exception e) {
            logger.error("ScheduleService.listProviderAppointmentCounts error", e);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity("Internal server error").build());
        }
    }

    /**
     * Lists a specific provider's appointments within a date range.
     *
     * @param providerNo String the provider number
     * @param sDateStr String start date in ISO 8601 format (yyyy-MM-dd)
     * @param eDateStr String end date in ISO 8601 format (yyyy-MM-dd)
     * @return ProviderPeriodAppsResponse containing the provider's appointments
     * @throws WebApplicationException if parameters are missing or malformed
     */
    @GET
    @Path("/fetchProviderAppts/{providerNo}/{sDate}/{eDate}")
    @Produces("application/json")
    public ProviderPeriodAppsResponse listProviderApptsForPeriod(@PathParam("providerNo") String providerNo, @PathParam("sDate") String sDateStr, @PathParam("eDate") String eDateStr) {
        if (providerNo == null || providerNo.length() == 0 || sDateStr == null || eDateStr == null)
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity("Required path parameter is miossing").build());

        try {
            Date sDate = null;
            Date eDate = null;
            try {
                sDate = org.apache.tools.ant.util.DateUtils.parseIso8601Date(sDateStr);
                eDate = org.apache.tools.ant.util.DateUtils.parseIso8601Date(eDateStr);
            } catch (Exception e) {
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity("Path parameter has the wrong format").build());
            }

            List<Appointment> appts = scheduleManager.getAppointmentsForDateRangeAndProvider(getLoggedInInfo(), sDate, eDate, providerNo);

            ProviderPeriodAppsResponse response = new ProviderPeriodAppsResponse();
            if (appts != null && appts.size() > 0) {
                response.setContent(new ArrayList<ProviderPeriodAppsTo>());
                for (Appointment appt : appts) {
                    ProviderPeriodAppsTo to = new ProviderPeriodAppsTo(appt);
                    response.getContent().add(to);
                }
            }
            return response;
        } catch (Exception e) {
            logger.error("ScheduleService.listProviderApptsForPeriod error", e);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity("Internal server error").build());
        }
    }


    /**
     * Saves or updates a search configuration, creating a new version and deactivating the old one.
     *
     * @param id Integer the ID of the existing search configuration to update
     * @param searchConfigTo SearchConfigTo1 the updated search configuration data
     * @return Response containing the new search configuration ID
     */
    @POST
    @Path("/searchConfig/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public Response saveSearchConfig(@PathParam("id") Integer id, SearchConfigTo1 searchConfigTo) {
        if (id == null || id.intValue() == 0) {
            return null;
        }


        AppointmentSearchTo1 forNewId = new AppointmentSearchTo1();
        try {
            SearchConfig oldConfig = null;
            AppointmentSearch currentAppointmentSearch = null;
            AppointmentSearch appointmentSearch = new AppointmentSearch(); // new object to be returned

            currentAppointmentSearch = appointmentSearchDao.find(id);
            if (currentAppointmentSearch == null || currentAppointmentSearch.getFileContents() == null) {
                oldConfig = new SearchConfig();
            } else {
                Document doc = XmlUtils.toDocument(currentAppointmentSearch.getFileContents());
                oldConfig = SearchConfig.fromDocument(doc);
            }

            SearchConfig searchConfig = SearchConfig.fromTransfer(searchConfigTo, oldConfig);
            Document d = SearchConfig.toDocument(searchConfig);


            appointmentSearch.setFileContents(XmlUtils.toBytes(d, false));
            appointmentSearch.setProviderNo(currentAppointmentSearch.getProviderNo());
            appointmentSearch.setSearchName(currentAppointmentSearch.getSearchName());
            appointmentSearch.setSearchType(currentAppointmentSearch.getSearchType());
            appointmentSearch.setUuid(currentAppointmentSearch.getUuid());
            appointmentSearch.setActive(true);


            appointmentSearchDao.persist(appointmentSearch);
            Integer newSearchConfigId = appointmentSearch.getId();
            forNewId.setId(newSearchConfigId);
            forNewId.setActive(appointmentSearch.isActive());
            forNewId.setCreateDate(appointmentSearch.getCreateDate());
            forNewId.setProviderNo(appointmentSearch.getProviderNo());
            forNewId.setSearchName(appointmentSearch.getSearchName());
            forNewId.setSearchType(appointmentSearch.getSearchType());

            if (currentAppointmentSearch != null) {
                currentAppointmentSearch.setActive(false);
                appointmentSearchDao.merge(currentAppointmentSearch);
            }

            logger.info("searchConfig\n" + XmlUtils.toString(d, true));
        } catch (Exception e) {
            logger.error("save Search Config Error ", e);
        }


        return Response.ok(forNewId).build();
    }

    /**
     * Retrieves a search configuration by its ID.
     *
     * @param id Integer the search configuration ID
     * @return SearchConfigTo1 the search configuration, or null on error
     */
    @GET
    @Path("/searchConfig/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public SearchConfigTo1 getSearchConfig(@PathParam("id") Integer id) {
        SearchConfigTo1 response = null;

        try {
            AppointmentSearch appointmentSearch = appointmentSearchDao.find(id);

            Document doc = XmlUtils.toDocument(appointmentSearch.getFileContents());

            SearchConfig searchConfig = SearchConfig.fromDocument(doc);

            response = SearchConfigTo1.fromClinic(searchConfig);

        } catch (Exception e) {
            logger.error("get Search Config Error ", e);
        }

        return response;
    }

    /**
     * Retrieves the search configuration associated with a specific provider.
     *
     * @param id String the provider number
     * @return SearchConfigTo1 the provider's search configuration, or a minimal object with just the ID
     */
    @GET
    @Path("/searchConfig/byProvider/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public SearchConfigTo1 getSearchConfigByProvider(@PathParam("id") String id) {
        SearchConfigTo1 response = null;
        Integer apptSearchId = null;
        try {
            AppointmentSearch appointmentSearch = appointmentSearchDao.findForProvider(id);
            apptSearchId = appointmentSearch.getId();

            Document doc = XmlUtils.toDocument(appointmentSearch.getFileContents());

            SearchConfig searchConfig = SearchConfig.fromDocument(doc);


            response = SearchConfigTo1.fromClinic(searchConfig);
            response.setId(apptSearchId);
        } catch (Exception e) {
            logger.error("get Search Config Error ", e);
        }

        if (response == null && apptSearchId != null) {
            response = new SearchConfigTo1();
            response.setId(apptSearchId);
        }

        return response;
    }

    @GET
    @Path("/searchConfig/list")
    @Produces("application/json")
    @Consumes("application/json")
    public List<AppointmentSearchTo1> getSearchConfig() {
        List<AppointmentSearchTo1> response = new ArrayList<AppointmentSearchTo1>();

        try {
            List<AppointmentSearch> appointmentSearchList = appointmentSearchDao.findAll();
            logger.error("list size" + appointmentSearchList.size());

            for (AppointmentSearch search : appointmentSearchList) {
                AppointmentSearchTo1 appSearch = new AppointmentSearchTo1();
                appSearch.setActive(search.isActive());
                appSearch.setCreateDate(search.getCreateDate());
                appSearch.setId(search.getId());
                appSearch.setProviderNo(search.getProviderNo());
                appSearch.setSearchName(search.getSearchName());
                appSearch.setSearchType(search.getSearchType());
                appSearch.setUpdateDate(search.getUpdateDate());

                response.add(appSearch);
            }


        } catch (Exception e) {
            logger.error("save Search Config Error ", e);
        }

        return response;
    }


    @POST
    @Path("/searchConfig/add")
    @Produces("application/json")
    @Consumes("application/json")
    public AppointmentSearchTo1 addSearchConfig(AppointmentSearchTo1 appointmentSearchTo) {


        AppointmentSearch appointmentSearch = new AppointmentSearch();
        appointmentSearch.setProviderNo(appointmentSearchTo.getProviderNo());
        appointmentSearch.setSearchName(appointmentSearchTo.getSearchName());
        appointmentSearch.setUuid(UUID.randomUUID().toString());
        if (AppointmentSearch.ONLINE.equals(appointmentSearchTo.getSearchType())) {
            appointmentSearch.setSearchType(AppointmentSearch.ONLINE);
        } else {
            appointmentSearch.setSearchType(AppointmentSearch.ONLINE);
        }

        appointmentSearchDao.persist(appointmentSearch);

        appointmentSearchTo.setId(appointmentSearch.getId());


        return appointmentSearchTo;
    }


    @POST
    @Path("/searchConfig/enable/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public AppointmentSearchTo1 enableSearchConfig(@PathParam("id") Integer id) {

        //Will need to disable any searchconfigs that are enabled.
        AppointmentSearch appointmentSearch = appointmentSearchDao.find(id);
        appointmentSearch.setActive(true);


        List<AppointmentSearch> uuidList = appointmentSearchDao.findByUUID(appointmentSearch.getUuid(), true);
        for (AppointmentSearch asearch : uuidList) {
            asearch.setActive(false);
            appointmentSearchDao.merge(asearch);
        }

        appointmentSearchDao.merge(appointmentSearch);

        AppointmentSearchTo1 appointmentSearchTo = new AppointmentSearchTo1();
        appointmentSearchTo.setActive(appointmentSearch.isActive());
        appointmentSearchTo.setProviderNo(appointmentSearch.getProviderNo());
        appointmentSearchTo.setSearchName(appointmentSearch.getSearchName());
        appointmentSearchTo.setSearchType(appointmentSearch.getSearchType());
        appointmentSearchTo.setCreateDate(appointmentSearch.getCreateDate());
        appointmentSearchTo.setUpdateDate(appointmentSearch.getUpdateDate());
        appointmentSearchTo.setId(appointmentSearch.getId());

        return appointmentSearchTo;
    }

    @POST
    @Path("/searchConfig/disable/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public AppointmentSearchTo1 disableSearchConfig(@PathParam("id") Integer id) {


        AppointmentSearch appointmentSearch = appointmentSearchDao.find(id);
        appointmentSearch.setActive(false);
        appointmentSearchDao.merge(appointmentSearch);


        AppointmentSearchTo1 appointmentSearchTo1 = new AppointmentSearchTo1();
        appointmentSearchTo1.setId(appointmentSearch.getId());
        appointmentSearchTo1.setSearchName(appointmentSearch.getSearchName());

        return appointmentSearchTo1;
    }


}
