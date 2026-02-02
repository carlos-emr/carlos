//CHECKSTYLE:OFF
package ca.openosp.openo.eform;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.jsoup.internal.StringUtil;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Struts2 action for logging errors that occur during eForm processing.
 *
 * <p>This action provides a silent logging mechanism for eForm errors without
 * returning any user-facing response. It receives error details via HTTP parameters,
 * sanitizes the error message to prevent XSS vulnerabilities, and persists the
 * error information to the eForm error log.</p>
 *
 * <p>This is a 2Action class following the OpenO EMR Struts2 migration pattern,
 * designed to coexist with legacy Struts 1.x actions during the framework transition.</p>
 *
 * <p><strong>Security Features:</strong></p>
 * <ul>
 *   <li>Validates formId is numeric to prevent injection attacks</li>
 *   <li>Sanitizes error messages using HTML entity encoding via StringEscapeUtils</li>
 *   <li>Performs null and empty checks on the formId parameter</li>
 * </ul>
 *
 * @see EFormUtil#logError(int, String)
 * @see com.opensymphony.xwork2.ActionSupport
 * @since 2026-01-24
 */
public class EformLogError2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Processes and logs eForm errors received via HTTP request parameters.
     *
     * <p>This method extracts the form ID and error message from the request,
     * validates the form ID is numeric, sanitizes the error message to prevent
     * cross-site scripting (XSS) attacks, and persists the error to the eForm
     * error log. The action returns null to indicate no view should be rendered,
     * making this a silent logging operation suitable for AJAX calls.</p>
     *
     * <p><strong>Expected Request Parameters:</strong></p>
     * <ul>
     *   <li><code>formId</code> (String) - The numeric identifier of the eForm that encountered an error</li>
     *   <li><code>error</code> (String) - The error message to be logged</li>
     * </ul>
     *
     * <p><strong>Validation Rules:</strong></p>
     * <ul>
     *   <li>formId must be non-null, non-empty, and numeric</li>
     *   <li>If validation fails, the error is silently ignored (no logging occurs)</li>
     *   <li>Error message is sanitized using HTML4 entity encoding</li>
     * </ul>
     *
     * @return String Always returns null to prevent view rendering
     * @throws Exception if an error occurs during error logging or parameter processing
     */
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
