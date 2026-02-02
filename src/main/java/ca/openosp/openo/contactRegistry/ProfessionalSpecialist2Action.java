//CHECKSTYLE:OFF
package ca.openosp.openo.contactRegistry;

import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.managers.ProfessionalSpecialistsManager;
import ca.openosp.openo.utility.JsonUtil;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.form.JSONUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Struts2 action class for managing professional specialist contact registry operations in the OpenO EMR system.
 *
 * <p>This action handles retrieval and search functionality for professional specialists (physicians, consultants,
 * and other healthcare professionals) that healthcare providers may need to contact for referrals, consultations,
 * or collaboration. The action provides JSON-based API endpoints for retrieving individual specialist records
 * and performing keyword-based searches across the specialist registry.</p>
 *
 * <p>This class follows the 2Action migration pattern, extending Struts2's ActionSupport and providing both
 * backwards-compatible dispatch routing via the execute() method and direct method-based routing for modern
 * integrations. The action integrates with Spring-managed business services through SpringUtils and maintains
 * healthcare context through LoggedInInfo session tracking.</p>
 *
 * <p><strong>Security Note:</strong> This action does not currently implement SecurityInfoManager privilege
 * checks. All methods require valid session context via LoggedInInfo but do not enforce role-based access
 * control for specialist registry access.</p>
 *
 * @since 2026-01-24
 * @see ca.openosp.openo.commn.model.ProfessionalSpecialist
 * @see ca.openosp.openo.managers.ProfessionalSpecialistsManager
 * @see ca.openosp.openo.utility.LoggedInInfo
 */
public class ProfessionalSpecialist2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ProfessionalSpecialistsManager professionalSpecialistsManager = SpringUtils.getBean(ProfessionalSpecialistsManager.class);

    /**
     * Main execution entry point for backwards-compatible action routing.
     *
     * <p>This method provides dispatch routing based on the actionType parameter to maintain
     * backwards compatibility with legacy URL patterns. Modern integrations should invoke
     * the get() or search() methods directly rather than relying on this dispatch mechanism.</p>
     *
     * <p>Supported action types:</p>
     * <ul>
     *   <li>/getProfessionalSpecialist - Routes to get() method for retrieving a single specialist by ID</li>
     *   <li>/searchProfessionalSpecialist - Routes to search() method for keyword-based specialist search</li>
     * </ul>
     *
     * @return String always returns null as this action writes directly to the response stream
     */
    public String execute() {

        /*
         * Designed for backwards compatibility.
         * Otherwise use the dispatch action methods.
         */
        if ("/getProfessionalSpecialist".equals(actionType)) {
            this.get();
        }

        if ("/searchProfessionalSpecialist".equals(actionType)) {
            this.search();
        }

        return null;
    }

    /**
     * Retrieves a single professional specialist record by ID and returns it as JSON.
     *
     * <p>This method fetches a specific professional specialist from the contact registry using
     * the specialist's unique identifier provided via the "id" request parameter. The specialist
     * record is serialized to JSON format and written directly to the HTTP response stream.</p>
     *
     * <p>The method extracts the logged-in user's healthcare context to ensure proper audit
     * logging and facility-scoped data access. If the specialist ID is not provided or the
     * specialist record is not found, the method returns without writing to the response stream.</p>
     *
     * <p><strong>Request Parameters:</strong></p>
     * <ul>
     *   <li>id (String) - The unique identifier of the professional specialist to retrieve.
     *       Must be a valid integer value. If null or empty, no response is generated.</li>
     * </ul>
     *
     * <p><strong>Response Format:</strong></p>
     * <ul>
     *   <li>Content-Type: application/json</li>
     *   <li>Body: JSON representation of ProfessionalSpecialist entity with all fields</li>
     *   <li>Returns nothing if specialist not found or ID invalid</li>
     * </ul>
     *
     * @throws NumberFormatException if the id parameter cannot be parsed as an integer
     */
    public void get() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String specialistId = request.getParameter("id");
        ProfessionalSpecialist professionalSpecialist = null;

        if (specialistId != null && !specialistId.isEmpty()) {
            professionalSpecialist = professionalSpecialistsManager.getProfessionalSpecialist(loggedInInfo, Integer.parseInt(specialistId));
        }

        if (professionalSpecialist != null) {
            ObjectNode professionalSpecialistJSON = JsonUtil.pojoToJson(professionalSpecialist);
            JSONUtil.jsonResponse(response, professionalSpecialistJSON.toString());
        }
    }

    /**
     * Performs a keyword-based search across the professional specialist registry and returns matching records as JSON.
     *
     * <p>This method searches the professional specialist contact registry using a keyword provided via
     * the "keyword" request parameter. The search typically matches against specialist names, specialties,
     * and other relevant contact information fields. All matching specialist records are serialized to a
     * JSON array and written directly to the HTTP response stream.</p>
     *
     * <p>The method extracts the logged-in user's healthcare context to ensure proper audit logging and
     * facility-scoped data access. If the keyword parameter is not provided or no matching specialists
     * are found, the method returns without writing to the response stream.</p>
     *
     * <p><strong>Request Parameters:</strong></p>
     * <ul>
     *   <li>keyword (String) - The search term to match against specialist records. Common search fields
     *       include specialist name, specialty type, and contact information. If null or empty, no
     *       response is generated.</li>
     * </ul>
     *
     * <p><strong>Response Format:</strong></p>
     * <ul>
     *   <li>Content-Type: application/json</li>
     *   <li>Body: JSON array of ProfessionalSpecialist entities matching the search criteria</li>
     *   <li>Returns nothing if keyword not provided or no matches found</li>
     * </ul>
     */
    public void search() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String search_keyword = request.getParameter("keyword");
        List<ProfessionalSpecialist> professionalSpecialist = null;

        if (search_keyword != null && !search_keyword.isEmpty()) {
            professionalSpecialist = professionalSpecialistsManager.searchProfessionalSpecialist(loggedInInfo, search_keyword);
        }

        if (professionalSpecialist != null) {
            String professionalSpecialistJSON = JsonUtil.pojoCollectionToJson(professionalSpecialist);
            JSONUtil.jsonResponse(response, professionalSpecialistJSON);
        }
    }

    private String actionType; // Determines the type of action

    /**
     * Retrieves the action type used for backwards-compatible dispatch routing.
     *
     * <p>The action type determines which method is invoked by the execute() method's dispatch
     * logic. This field supports legacy URL patterns that specify the operation type as a
     * parameter value.</p>
     *
     * @return String the action type path (e.g., "/getProfessionalSpecialist", "/searchProfessionalSpecialist")
     */
    public String getActionType() {
        return actionType;
    }

    /**
     * Sets the action type for backwards-compatible dispatch routing.
     *
     * <p>This setter is invoked by Struts2's parameter interceptor to populate the actionType
     * field from the request parameters, enabling the execute() method to route to the
     * appropriate operation handler.</p>
     *
     * @param actionType String the action type path that determines which method to invoke
     */
    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
}
