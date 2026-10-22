/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.prescript.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.BeanUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.FavoritesDao;
import io.github.carlos_emr.carlos.commn.dao.FavoritesPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Favorites;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 *
 * @author toby
 */
public class CopyFavorites2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    FavoritesPrivilegeDao favoritesPrivilegeDao = SpringUtils.getBean(FavoritesPrivilegeDao.class);
    FavoritesDao favoritesDao = SpringUtils.getBean(FavoritesDao.class);
    
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null || providerNo.isBlank()) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        request.setAttribute("providerNo", providerNo);
        String method = request.getParameter("dispatch");
        // Targeted conditional guard from Copilot's PR #2478: the sharing editor remains
        // readable by GET, but changing sharing permissions or copying favourites requires POST.
        if (("update".equals(method) || "copy".equals(method))
                && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (!validDestination(providerNo)) return NONE;
        if ("update".equals(method)) return update();
        if ("copy".equals(method)) return copy();
        return refresh();
    }

    /** Legacy hidden destinations may only identify the authenticated provider. */
    private boolean validDestination(String providerNo) throws IOException {
        for (String name : List.of("providerNo", "userProviderNo")) {
            String[] submitted = request.getParameterValues(name);
            if (submitted == null) continue;
            for (String target : submitted) {
                if (!providerNo.equals(target)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return false;
                }
            }
        }
        return true;
    }

    public String update() throws IOException {
        String share = request.getParameter("rb_share");
        if (!"0".equals(share) && !"1".equals(share)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        try {
            favoritesPrivilegeDao.setFavoritesPrivilege(currentProvider(), "1".equals(share), false);
        } catch (RuntimeException e) {
            return failedOperation(e, "Favorite sharing was not confirmed. Reload before retrying.");
        }
        request.setAttribute("favoriteSharingUpdated", Boolean.TRUE);
        return refresh();
    }

    /** Loads only the public source selected for this request, or the caller's own favourites. */
    public String refresh() throws IOException {
        String source = requestedSource();
        try {
            if (!source.isEmpty() && !mayReadSource(source)) return NONE;
            request.setAttribute("copyProviderNo", source);
            request.setAttribute("copyFavorites", source.isEmpty() ? List.of() : favoritesDao.findByProviderNo(source));
            request.setAttribute("sharedProviders", favoritesPrivilegeDao.getProviders());
            var ownPrivilege = favoritesPrivilegeDao.findByProviderNo(currentProvider());
            request.setAttribute("shareFavorites", ownPrivilege != null && ownPrivilege.isOpenToPublic());
            return SUCCESS;
        } catch (RuntimeException e) {
            if (request.getAttribute("copiedFavoritesCount") != null) {
                return failedOperation(e, "Favorites were copied, but the page could not be loaded. Review your favorites before copying again.");
            }
            if (Boolean.TRUE.equals(request.getAttribute("favoriteSharingUpdated"))) {
                return failedOperation(e, "Favorite sharing was saved, but the page could not be loaded. Reload to review the saved preference.");
            }
            return failedOperation(e, "Favorites could not be loaded. Reload before retrying.");
        }
    }

    private String currentProvider() {
        return (String) request.getAttribute("providerNo");
    }

    private String requestedSource() {
        String source = request.getParameter("ddl_provider");
        return source == null ? "" : source.trim();
    }

    private boolean mayReadSource(String source) throws IOException {
        if (source.equals(currentProvider())) return true;
        var privilege = favoritesPrivilegeDao.findByProviderNo(source);
        if (privilege == null || !privilege.isOpenToPublic()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    // FindSecBugs BEAN_PROPERTY_INJECTION: fixed JavaBean descriptors between authorised model types.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Copies fixed JavaBean properties from an ownership-checked favourite")
    public String copy() throws IOException {
        String source = requestedSource();
        if (source.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        List<Favorites> selected = new ArrayList<>();
        try {
            if (!mayReadSource(source)) return NONE;
            String rawCount = request.getParameter("countFavorites");
            int count = rawCount != null && rawCount.matches("[0-9]{1,5}")
                    ? Integer.parseInt(rawCount) : -1;
            if (count < 0 || count > 10_000) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
            for (int i = 0; i < count; i++) {
                if (request.getParameter("selected" + i) == null) continue;
                int id;
                try {
                    id = Integer.parseInt(request.getParameter("fldFavoriteId" + i));
                } catch (NumberFormatException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }
                if (id <= 0) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }
                ids.add(id);
            }
            if (ids.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            // Validate the complete batch before any write: a later private/missing favourite
            // must not leave the earlier rows copied or disclose another provider's templates.
            for (Integer id : ids) {
                Favorites favorite = favoritesDao.find(id);
                if (favorite == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return NONE;
                }
                if (!source.equals(favorite.getProviderNo())) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return NONE;
                }
                selected.add(favorite);
            }
        } catch (RuntimeException e) {
            return failedOperation(e, "Favorites could not be validated. Nothing was copied.");
        }
        try {
            for (Favorites favorite : selected) {
                Favorites copy = new Favorites();
                BeanUtils.copyProperties(favorite, copy);
                copy.setProviderNo(currentProvider());
                copy.setId(null);
                favoritesDao.persist(copy);
            }
        } catch (RuntimeException e) {
            return failedOperation(e, "Favorite copy did not complete. Some items may already have been copied; review your favorites before retrying.");
        }
        request.setAttribute("copiedFavoritesCount", selected.size());
        return refresh();
    }

    private String failedOperation(RuntimeException failure, String message) throws IOException {
        logger.error("Favorite operation failed ({})", failure.getClass().getSimpleName());
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
        return NONE;
    }
}
