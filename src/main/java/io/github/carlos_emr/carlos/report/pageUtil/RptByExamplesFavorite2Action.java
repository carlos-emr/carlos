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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.ReportByExamplesFavoriteDao;
import io.github.carlos_emr.carlos.commn.model.ReportByExamplesFavorite;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.report.bean.RptByExampleQueryBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class RptByExamplesFavorite2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ReportByExamplesFavoriteDao dao = SpringUtils.getBean(ReportByExamplesFavoriteDao.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_admin or _report)");
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (!StringUtils.isEmpty(this.getNewQuery())) {
            // Edit case
            if (hasFavoriteId()) {
                ReportByExamplesFavorite favorite = requireOwnedFavorite(providerNo, this.getId());
                this.setQuery(favorite.getQuery());
                this.setFavoriteName(favorite.getName());
            } else {
                prepareNewFavorite(providerNo);
            }
            return "edit";
        } else if ("true".equalsIgnoreCase(this.getToDelete())) {
            // Deletion case
            deleteQuery(providerNo, this.getId());
        } else if (hasFavoriteId()) {
            updateFavorite(providerNo, this.getId(), this.getFavoriteName(), this.getQuery());
        } else {
            // Add to favorite case
            String favoriteName = this.getFavoriteName();
            String query = this.getQuery();
            String queryWithEscapeChar = StringUtils.defaultString(query);
            write2Database(providerNo, favoriteName, queryWithEscapeChar);
        }

        // Sets all of the favorite queries, only used if the user adds or deletes a favorite query
        RptByExampleQueryBeanHandler hd = new RptByExampleQueryBeanHandler(providerNo);
        request.setAttribute("allFavorites", hd);
        return SUCCESS;
    }

    public void write2Database(String providerNo, String favoriteName, String query) {
        if (query == null || query.compareTo("") == 0) {
            return;
        }

        List<ReportByExamplesFavorite> favorites = dao.findByEverything(providerNo, favoriteName, query);
        if (favorites.isEmpty()) {
            ReportByExamplesFavorite r = new ReportByExamplesFavorite();
            r.setProviderNo(providerNo);
            r.setName(favoriteName);
            r.setQuery(query);
            dao.persist(r);
        } else {
            ReportByExamplesFavorite r = favorites.get(0);
            if (r != null) {
                r.setName(favoriteName);
                r.setQuery(query);
                dao.merge(r);
            }
        }

    }

    public void deleteQuery(String providerNo, String id) {
        dao.remove(requireOwnedFavorite(providerNo, id));
    }

    private void prepareNewFavorite(String providerNo) {
        this.setQuery(this.getNewQuery());
        if (!StringUtils.isEmpty(this.getNewName())) {
            this.setFavoriteName(this.getNewName());
            return;
        }
        List<ReportByExamplesFavorite> favorites = dao.findByProviderAndQuery(providerNo, this.getNewQuery());
        if (!favorites.isEmpty()) {
            this.setFavoriteName(favorites.get(0).getName());
        }
    }

    private void updateFavorite(String providerNo, String id, String favoriteName, String query) {
        ReportByExamplesFavorite favorite = requireOwnedFavorite(providerNo, id);
        favorite.setName(favoriteName);
        favorite.setQuery(StringUtils.defaultIfEmpty(query, favorite.getQuery()));
        dao.merge(favorite);
    }

    private ReportByExamplesFavorite requireOwnedFavorite(String providerNo, String id) {
        final int favoriteId;
        try {
            favoriteId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new SecurityException("Invalid favorite selection", e);
        }
        ReportByExamplesFavorite favorite = dao.find(favoriteId);
        if (favorite == null || !Objects.equals(providerNo, favorite.getProviderNo())) {
            throw new SecurityException("Favorite does not belong to the current provider");
        }
        return favorite;
    }

    private boolean hasFavoriteId() {
        return StringUtils.isNotBlank(this.getId()) && !"error".equals(this.getId());
    }


    String favoriteName = "";
    String query;
    String newQuery;
    String newName;
    String toDelete;
    String id;

    public String getFavoriteName() {
        return favoriteName;
    }

    @StrutsParameter
    public void setFavoriteName(String favoriteName) {
        this.favoriteName = favoriteName;
    }

    public String getQuery() {
        return query;
    }

    @StrutsParameter
    public void setQuery(String query) {
        this.query = query;
    }

    public String getNewQuery() {
        return newQuery;
    }

    @StrutsParameter
    public void setNewQuery(String newQuery) {
        this.newQuery = newQuery;
    }

    public String getNewName() {
        return newName;
    }

    @StrutsParameter
    public void setNewName(String newName) {
        this.newName = newName;
    }

    public String getToDelete() {
        return toDelete;
    }

    @StrutsParameter
    public void setToDelete(String toDelete) {
        this.toDelete = toDelete;
    }

    public String getId() {
        return id;
    }

    @StrutsParameter
    public void setId(String id) {
        this.id = id;
    }
}
