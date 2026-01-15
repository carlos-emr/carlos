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

public class ProfessionalSpecialist2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ProfessionalSpecialistsManager professionalSpecialistsManager = SpringUtils.getBean(ProfessionalSpecialistsManager.class);

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
    // Getter and Setter for actionType
    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
}
