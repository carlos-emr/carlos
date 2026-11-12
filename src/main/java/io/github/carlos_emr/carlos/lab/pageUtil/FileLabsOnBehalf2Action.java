/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.pageUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.lab.service.LabFilingService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Atomic, POST-only on-behalf filing; identity and consent are resolved by the service. */
public class FileLabsOnBehalf2Action extends ActionSupport {
    @Override
    public String execute() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        int status = 200;
        Map<String, Object> result;
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            status = 405;
            result = Map.of("success", false, "error", "POST required");
        } else {
            try {
                String[] labs = request.getParameterValues("labNo");
                String[] types = request.getParameterValues("labType");
                String[] providers = request.getParameterValues("providerNo");
                String[] comments = request.getParameterValues("comment");
                if (labs == null || labs.length != 1 || !labs[0].matches("[1-9][0-9]{0,9}")
                        || types == null || types.length != 1 || providers == null
                        || (comments != null && comments.length != 1)) {
                    throw new IllegalArgumentException("Invalid filing selection");
                }
                int changed = SpringUtils.getBean(LabFilingService.class).fileForProviders(
                        LoggedInInfo.getLoggedInInfoFromSession(request), Integer.parseInt(labs[0]), types[0],
                        Arrays.asList(providers), comments == null ? null : comments[0], true);
                result = Map.of("success", true, "filed", changed);
            } catch (SecurityException refused) {
                status = 403;
                result = Map.of("success", false, "error", "Filing was refused");
            } catch (IllegalArgumentException invalid) {
                status = 400;
                result = Map.of("success", false, "error", "Invalid filing selection");
            } catch (RuntimeException failure) {
                MiscUtils.getLogger().error("On-behalf filing was not confirmed");
                status = 500;
                result = Map.of("success", false, "error", "Filing was not confirmed; refresh before trying again");
            }
        }
        response.setStatus(status);
        // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- application/json, fixed messages and numeric counts serialized by Jackson
        new ObjectMapper().writeValue(response.getOutputStream(), result);
        return NONE;
    }
}
