//CHECKSTYLE:OFF
package ca.openosp.openo.eform;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.jsoup.internal.StringUtil;
import org.apache.commons.text.StringEscapeUtils;

public class EformLogError2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() throws Exception {
        String formId = request.getParameter("formId");
        String error = request.getParameter("error");

        /*
         * silent update to the eform error log.
         */

         if(formId != null && !formId.isEmpty() && StringUtil.isNumeric(formId)) {
		String sanitizedError = StringEscapeUtils.escapeHtml4(error);
		EFormUtil.logError(Integer.parseInt(formId), sanitizedError);
	 }

         return null;
    }
}
