package io.github.carlos_emr.carlos.commn.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
/**
 * Base action or helper class for rendering JSON responses in the web tier.
 * Simplifies Struts-based endpoints that need to output structured JSON data.
 */

public class JSONAction extends ActionSupport {

    private final String ENCODING = "UTF-8";
    private final String CONTENT_TYPE = "application/json";
    private final Logger logger = MiscUtils.getLogger();

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    protected HttpServletRequest request;
    protected HttpServletResponse response;

    protected JSONAction() {
        // Format the current action context to a JSON stream, satisfying AJAX client requirements
        if (ActionContext.getContext() != null) {
            request = ServletActionContext.getRequest();
            response = ServletActionContext.getResponse();
        }
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    protected void jsonResponse(ObjectNode jsonObject) {
        if (!hasResponseContext()) {
            return;
        }
        try (PrintWriter out = response.getWriter()) {
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding(ENCODING);
            out.print(jsonObject.toString());
            out.flush();
        } catch (IOException e) {
            logger.error("Error while creating JSON response", e);
        }
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    protected void jsonResponse(String jsonString) {
        if (!hasResponseContext()) {
            return;
        }
        try (PrintWriter out = response.getWriter()) {
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding(ENCODING);
            out.print(jsonString);
            out.flush();
        } catch (IOException e) {
            logger.error("Error while creating JSON response", e);
        }
    }

    protected void jsonResponse(String name, String value) {
        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put(name, value);
        jsonResponse(jsonObject);
    }

    private boolean hasResponseContext() {
        if (response != null) {
            return true;
        }
        logger.warn("Cannot create JSON response without an active servlet response context");
        return false;
    }

    protected void errorResponse(String name, String value) {
        if (!hasResponseContext()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        jsonResponse(name, value);
    }
}
