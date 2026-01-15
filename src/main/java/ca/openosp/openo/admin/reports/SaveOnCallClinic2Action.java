//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.admin.reports;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.OnCallClinicDao;
import ca.openosp.openo.commn.model.OnCallClinic;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.struts2.ServletActionContext;

public class SaveOnCallClinic2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {
        String method = request.getParameter("method");
        if ("Save".equals(method)) {
            return Save();
        } else if ("Delete".equals(method)) {
            return Delete();
        }
        return Load();
    }

    public String Save() {
        String json = "{\"error\" : \"false\"}";
        try {
            // configure JSON mapper to parse dates in the expected format
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setDateFormat(sdf);

            // deserialize the incoming JSON payload
            OnCallClinic onCallClinic =
                objectMapper.readValue(request.getParameter("event"), OnCallClinic.class);

            // persist
            OnCallClinicDao dao = SpringUtils.getBean(OnCallClinicDao.class);
            dao.persist(onCallClinic);
        } catch (Exception e) {
            MiscUtils.getLogger().error("ERROR SAVING ", e);
            json = "{\"error\" : \"true\"}";
        }

        try {
            PrintWriter out = response.getWriter();
            out.print(json);
            out.flush();
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error writing response in Save method", e);
        }

        return null;
    }

    public String Delete() {
        OnCallClinicDao dao = SpringUtils.getBean(OnCallClinicDao.class);
        String json = "{\"error\" : \"false\"}";
        try {
            Long id = Long.valueOf(request.getParameter("id"));
            if (!dao.remove(id)) {
                json = "{\"error\" : \"true\"}";
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("ERROR DELETING ", e);
            json = "{\"error\" : \"true\"}";
        }

        try {
            PrintWriter out = response.getWriter();
            out.print(json);
            out.flush();
        } catch (IOException ignored) {
        }
        return null;
    }

    public String Load() {
        OnCallClinicDao dao = SpringUtils.getBean(OnCallClinicDao.class);
        List<OnCallClinic> onCallClinics = dao.findAll(null, null);

        ObjectMapper mapper = new ObjectMapper();
        // write dates as timestamps (true) or disable for ISO strings if desired
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

        try (OutputStream out = response.getOutputStream()) {
            mapper.writeValue(out, onCallClinics);
        } catch (IOException ignored) {
        }

        return null;
    }
}
