/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.DuplicateKeyException;
import io.github.carlos_emr.carlos.managers.RxFavoriteGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Patient-scoped entry point for the authenticated provider's favourite presentation groups. */
public class RxFavoriteGroups2Action extends ActionSupport {
    @Override
    public String execute() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        String command = request.getParameter("command");
        boolean mutation = command != null;
        if (mutation && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (!mutation && !"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        var info = LoggedInInfo.getLoggedInInfoFromSession(request);
        var security = SpringUtils.getBean(SecurityInfoManager.class);
        if (info == null || !security.hasPrivilege(info, "_rx", mutation ? "u" : "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        var bean = RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r");
        if (bean == null) {
            response.sendError(404);
            return NONE;
        }
        var manager = SpringUtils.getBean(RxFavoriteGroupManager.class);
        String provider = info.getLoggedInProviderNo();
        if (mutation) {
            try {
                switch (command) {
                    case "create" -> manager.create(provider, request.getParameter("name"));
                    case "rename" -> manager.rename(provider, positiveId(request.getParameter("groupId")), request.getParameter("name"));
                    case "delete" -> manager.delete(provider, positiveId(request.getParameter("groupId")));
                    case "up" -> manager.move(provider, positiveId(request.getParameter("groupId")), -1);
                    case "down" -> manager.move(provider, positiveId(request.getParameter("groupId")), 1);
                    case "assign" -> {
                        String[] selected = request.getParameterValues("groupIds");
                        if (selected != null && selected.length > 100) throw new IllegalArgumentException();
                        List<Integer> ids = selected == null ? List.of() : Arrays.stream(selected)
                                .map(RxFavoriteGroups2Action::positiveId).toList();
                        manager.assign(provider, positiveId(request.getParameter("favoriteId")), ids);
                    }
                    default -> throw new IllegalArgumentException();
                }
                response.sendRedirect(request.getContextPath() + "/rx/favoriteGroups?demographicNo=" + bean.getDemographicNo());
                return NONE;
            } catch (DuplicateKeyException duplicate) {
                response.setStatus(409);
                request.setAttribute("favoriteGroupError", "oscarRx.groups.duplicate");
            } catch (IllegalArgumentException invalid) {
                response.setStatus(400);
                request.setAttribute("favoriteGroupError", "oscarRx.groups.invalid");
            } catch (NoSuchElementException missing) {
                // An unowned group or favourite is indistinguishable from a missing one.
                response.sendError(404);
                return NONE;
            }
        }
        request.setAttribute("favoriteGroupView", manager.view(provider));
        request.setAttribute("favoriteGroupPatient", bean.getDemographicNo());
        return SUCCESS;
    }

    private static int positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException();
        int id = Integer.parseInt(value);
        if (id <= 0) throw new IllegalArgumentException();
        return id;
    }
}
