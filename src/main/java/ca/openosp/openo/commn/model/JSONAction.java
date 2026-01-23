package ca.openosp.openo.commn.model;

import ca.openosp.openo.utility.MiscUtils;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class JSONAction extends ActionSupport {

    private final String ENCODING = "UTF-8";
    private final String CONTENT_TYPE = "application/json";
    private final Logger logger = MiscUtils.getLogger();

    protected HttpServletRequest request = ServletActionContext.getRequest();
    protected HttpServletResponse response = ServletActionContext.getResponse();

    protected void jsonResponse(JSONObject jsonObject) {
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

    protected void jsonResponse(String name, String value) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(name, value);
        jsonResponse(jsonObject);
    }

    protected void errorResponse(String name, String value) throws JSONException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        jsonResponse(name, value);
    }
}

