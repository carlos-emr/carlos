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

package ca.openosp.openo.report.pageUtil;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.dao.ReportByExamplesFavoriteDao;
import ca.openosp.openo.commn.model.ReportByExamplesFavorite;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.report.bean.RptByExampleQueryBeanHandler;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class RptByExamplesFavorite2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ReportByExamplesFavoriteDao dao = SpringUtils.getBean(ReportByExamplesFavoriteDao.class);

    public String execute() throws ServletException, IOException {
        String providerNo = (String) request.getSession().getAttribute("user");

        if (!StringUtils.isEmpty(this.getNewQuery())) {
            // Edit case
            this.setQuery(this.getNewQuery());
            if (!StringUtils.isEmpty(this.getNewName())) {
                this.setFavoriteName(this.getNewName());
            } else {
                ReportByExamplesFavoriteDao dao = SpringUtils.getBean(ReportByExamplesFavoriteDao.class);
                for (ReportByExamplesFavorite f : dao.findByQuery(this.getNewQuery())) {
                    this.setFavoriteName(f.getName());
                }
            }
            return "edit";
        } else if ("true".equalsIgnoreCase(this.getToDelete())) {
            // Deletion case
            deleteQuery(this.getId());
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

        MiscUtils.getLogger().debug("Fav " + favoriteName + " query " + query);

        ReportByExamplesFavoriteDao dao = SpringUtils.getBean(ReportByExamplesFavoriteDao.class);
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

    public void deleteQuery(String id) {
        dao.remove(Integer.parseInt(id));
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

    public void setFavoriteName(String favoriteName) {
        this.favoriteName = favoriteName;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getNewQuery() {
        return newQuery;
    }

    public void setNewQuery(String newQuery) {
        this.newQuery = newQuery;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public String getToDelete() {
        return toDelete;
    }

    public void setToDelete(String toDelete) {
        this.toDelete = toDelete;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
