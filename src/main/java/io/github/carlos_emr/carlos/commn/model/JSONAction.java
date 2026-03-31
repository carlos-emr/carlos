package io.github.carlos_emr.carlos.commn.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class JSONAction extends ActionSupport {

    private final String ENCODING = "UTF-8";
    private final String CONTENT_TYPE = "application/json";
    private final Logger logger = MiscUtils.getLogger();

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    protected HttpServletRequest request = ServletActionContext.getRequest();
    protected HttpServletResponse response = ServletActionContext.getResponse();

    protected void jsonResponse(ObjectNode jsonObject) {
        try (PrintWriter out = response.getWriter()) {
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding(ENCODING);
            out.print(jsonObject.toString());
            out.flush();
        } catch (IOException e) {
            logger.error("Error while creating JSON response", e);
        }
    }

    protected void jsonResponse(String jsonString) {
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

    protected void errorResponse(String name, String value) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        jsonResponse(name, value);
    }
}
