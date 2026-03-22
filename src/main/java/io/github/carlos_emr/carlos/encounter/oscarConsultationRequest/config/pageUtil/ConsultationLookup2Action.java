package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceSpecialistsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialists;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data.ConsultationServiceDto;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.data.SpecialistDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action providing AJAX endpoints for consultation service and specialist lookups.
 *
 * This action replaces the legacy JavaScript generation approach where JavaScript code
 * was dynamically generated and stored in the specialistsJavascript database table.
 * Instead, this action provides real-time data access to consultation services and
 * their associated specialists via AJAX calls.
 *
 * Endpoints:
 * - ConsultationLookup2Action.do?method=getServices - Returns all active consultation services
 * - ConsultationLookup2Action.do?method=getSpecialists&amp;serviceId=X - Returns specialists for a service
 *
 * @since 2025-11-20
 */
/**
 * Struts2 action that performs specialist and service lookups for consultation requests.
 *
 * @since 2001-01-01
 */
public class ConsultationLookup2Action extends ActionSupport {

    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ConsultationServiceDao consultationServiceDao = SpringUtils.getBean(ConsultationServiceDao.class);
    private ServiceSpecialistsDao serviceSpecialistsDao = SpringUtils.getBean(ServiceSpecialistsDao.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main dispatcher that routes to appropriate method based on 'method' parameter.
     *
     * @return String action result (null if response already written, ERROR on failure)
     */
    @Override
    public String execute() {
        // Security check - user must have consultation read access
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "r", null)) {
            throw new SecurityException("missing required security object");
        }

        String method = request.getParameter("method");

        if ("getServices".equals(method)) {
            return getServices();
        } else if ("getSpecialists".equals(method)) {
            return getSpecialists();
        }

        MiscUtils.getLogger().warn("Invalid method parameter: " + method);
        return NONE;
    }

    /**
     * Returns all active consultation services as JSON.
     *
     * URL: ConsultationLookup2Action.do?method=getServices
     *
     * Response format:
     * [
     *   {"serviceId": 53, "serviceDesc": "Cardiology"},
     *   {"serviceId": 54, "serviceDesc": "Dermatology"}
     * ]
     *
     * @return null (response written directly to output stream)
     */
    private String getServices() {
        try {
            List<ConsultationServices> services = consultationServiceDao.findActive();
            List<ConsultationServiceDto> serviceList = new ArrayList<>();

            for (ConsultationServices service : services) {
                serviceList.add(new ConsultationServiceDto(
                    service.getServiceId(),
                    service.getServiceDesc()
                ));
            }

            writeJsonResponse(serviceList);
            return null; // Response already written

        } catch (Exception e) {
           return handleServerError(
                "Error retrieving consultation services",
                "Error retrieving services",
                e
            );
        }
    }

    /**
     * Returns specialists for a specific consultation service as JSON.
     *
     * URL: ConsultationLookup2Action.do?method=getSpecialists&serviceId=53
     *
     * Response format:
     * [
     *   {
     *     "specId": 297,
     *     "name": "Smith, John MD",
     *     "phone": "555-1234",
     *     "fax": "555-5678",
     *     "address": "123 Main St"
     *   }
     * ]
     *
     * @return null (response written directly to output stream)
     */
    private String getSpecialists() {
        try {
            String serviceIdParam = request.getParameter("serviceId");
            Integer serviceId = ConversionUtils.fromIntString(serviceIdParam);

            if (serviceId == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid serviceId parameter");
                return null;
            }

            List<Object[]> results = serviceSpecialistsDao.findSpecialists(serviceId);
            List<SpecialistDto> specialistList = new ArrayList<>();

            for (Object[] result : results) {
                ServiceSpecialists serviceSpec = (ServiceSpecialists) result[0];
                ProfessionalSpecialist specialist = (ProfessionalSpecialist) result[1];

                specialistList.add(new SpecialistDto(
                    serviceSpec.getId().getSpecId(),
                    formatSpecialistName(specialist),
                    nullSafe(specialist.getPhoneNumber()),
                    nullSafe(specialist.getFaxNumber()),
                    nullSafe(specialist.getStreetAddress()),
                    nullSafe(specialist.getAnnotation())
                ));
            }

            writeJsonResponse(specialistList);
            return null; // Response already written

        } catch (Exception e) {
            return handleServerError(
                "Error retrieving specialists",
                "Error retrieving specialists",
                e
            );
        }
    }

    /**
     * Handles server errors by logging and sending an error response.
     *
     * @param logMessage   Message to log
     * @param clientMessage Message to send to client
     * @param e            Exception that occurred
     * @return null (response written directly to output stream)
     */
    private String handleServerError(String logMessage, String clientMessage, Exception e) {
        MiscUtils.getLogger().error(logMessage, e);
        try {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, clientMessage);
        } catch (IOException ioException) {
            MiscUtils.getLogger().error("Error sending error response", ioException);
        }
        return null;
    }

    /**
     * Formats specialist name with professional letters.
     *
     * @param specialist ProfessionalSpecialist object
     * @return String formatted name (e.g., "Smith, John MD")
     */
    private String formatSpecialistName(ProfessionalSpecialist specialist) {
        StringBuilder name = new StringBuilder();
        name.append(specialist.getLastName()).append(", ").append(specialist.getFirstName());

        if (specialist.getProfessionalLetters() != null && !specialist.getProfessionalLetters().isEmpty()) {
            name.append(" ").append(specialist.getProfessionalLetters());
        }

        return name.toString();
    }

    /**
     * Returns empty string for null values to avoid JSON null issues.
     *
     * @param value String value (may be null)
     * @return String value or empty string if null
     */
    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    /**
     * Writes JSON response to the output stream.
     *
     * @param data Object to serialize as JSON
     * @throws IOException if writing to response fails
     */
    private void writeJsonResponse(Object data) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), data);
    }
}