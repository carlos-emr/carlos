/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.DuplicateKeyException;
import io.github.carlos_emr.carlos.managers.RxFavoriteGroupService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/** Patient-scoped entry point for the authenticated provider's favourite presentation groups. */
public class RxFavoriteGroups2Action extends ActionSupport {
    private final SecurityInfoManager security;
    private final RxFavoriteGroupService manager;
    private final org.springframework.transaction.PlatformTransactionManager transactions;

    public RxFavoriteGroups2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(RxFavoriteGroupService.class),
                SpringUtils.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    }

    RxFavoriteGroups2Action(SecurityInfoManager security, RxFavoriteGroupService manager,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        this.security = security;
        this.manager = manager;
        this.transactions = transactions;
    }

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
        if (info == null || !security.hasPrivilege(info, "_rx", mutation ? "u" : "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        var bean = RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r");
        if (bean == null) {
            response.sendError(404);
            return NONE;
        }
        String provider = info.getLoggedInProviderNo();
        String redirect = request.getContextPath() + "/rx/favoriteGroups?demographicNo=" + bean.getDemographicNo();
        if (mutation) {
            var attempt = io.github.carlos_emr.carlos.utility.FormSubmissionGuard.attempt(request.getSession(),
                    request.getParameter(io.github.carlos_emr.carlos.utility.FormSubmissionGuard.PARAMETER),
                    io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Scope.RX_FAVORITE_GROUP,
                    provider + ":" + bean.getDemographicNo(), io.github.carlos_emr.carlos.utility.FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
            if (attempt.verdict() == io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Verdict.ALREADY_SAVED) {
                response.sendRedirect(attempt.result());
                return NONE;
            }
            if (attempt.verdict() != io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Verdict.PROCEED
                    && attempt.verdict() != io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Verdict.ABSENT) {
                return io.github.carlos_emr.carlos.utility.FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
            try (var claim = attempt.claim()) {
                new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                    if (claim != null) claim.storageStarted();
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
                });
                if (claim != null) claim.saved(redirect);
                response.sendRedirect(redirect);
                return NONE;
            } catch (org.springframework.dao.ConcurrencyFailureException concurrency) {
                response.setStatus(409);
                request.setAttribute("favoriteGroupError", "oscarRx.groups.retry");
            } catch (DuplicateKeyException duplicate) {
                response.setStatus(409);
                request.setAttribute("favoriteGroupError", "oscarRx.groups.duplicate");
            } catch (RxFavoriteGroupService.GroupLimitException limit) {
                response.setStatus(400);
                request.setAttribute("favoriteGroupError", "oscarRx.groups.limit");
            } catch (IllegalArgumentException invalid) {
                response.setStatus(400);
                request.setAttribute("favoriteGroupError", "oscarRx.groups.invalid");
            } catch (NoSuchElementException missing) {
                // An unowned group or favourite is indistinguishable from a missing one.
                response.sendError(404);
                return NONE;
            }
        }
        request.setAttribute("favoriteGroupSubmission", io.github.carlos_emr.carlos.utility.FormSubmissionGuard.issue(
                request.getSession(), io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Scope.RX_FAVORITE_GROUP,
                provider + ":" + bean.getDemographicNo()));
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
