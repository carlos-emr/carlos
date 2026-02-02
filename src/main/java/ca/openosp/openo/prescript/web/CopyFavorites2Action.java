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
 */

package ca.openosp.openo.prescript.web;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeanUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.FavoritesDao;
import ca.openosp.openo.commn.dao.FavoritesPrivilegeDao;
import ca.openosp.openo.commn.model.Favorites;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;

/**
 *
 * @author toby
 */
public class CopyFavorites2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    FavoritesPrivilegeDao favoritesPrivilegeDao = SpringUtils.getBean(FavoritesPrivilegeDao.class);
    FavoritesDao favoritesDao = SpringUtils.getBean(FavoritesDao.class);
    
    public String execute() {
        String method = request.getParameter("dispatch");
        if ("update".equals(method)) {
            return update();
        } else if ("copy".equals(method)) {
            return copy();
        }
        return refresh();
    }


    public String update() {
        logger.debug("copyFavorites-update");
        
        //LazyValidatorForm lazyForm = (LazyValidatorForm) form;
        String providerNo = request.getParameter("userProviderNo");//lazyForm.get("userProviderNo").toString();
        int share = Integer.parseInt(request.getParameter("rb_share"));//Integer.parseInt(lazyForm.get("rb_share").toString());
        favoritesPrivilegeDao.setFavoritesPrivilege(providerNo, share==0?false:true, false);

        return SUCCESS;
    }

    public String refresh() {
        logger.debug("copyFavorites-refresh");

        //LazyValidatorForm lazyForm = (LazyValidatorForm) form;
        //String providerNo = lazyForm.get("ddl_provider").toString();
        String providerNo = request.getParameter("ddl_provider");
        request.setAttribute("copyProviderNo", providerNo);

        return SUCCESS;
    }

    public String copy() {
        logger.debug("copyFavorites-copy");

        //LazyValidatorForm lazyForm = (LazyValidatorForm) form;
        //String providerNo = lazyForm.get("userProviderNo").toString();
        
        String providerNo = request.getParameter("providerNo");
        //if( lazyForm.get("ddl_provider") == null || lazyForm.get("ddl_provider").toString()=="")
        if (request.getParameter("ddl_provider") == null || request.getParameter("ddl_provider").equals(""))
            return SUCCESS;

        //int count = Integer.parseInt(lazyForm.get("countFavorites").toString());
        int count = Integer.parseInt(request.getParameter("countFavorites"));
        List<Integer> favIDs = new ArrayList<Integer>();
        for (int i=0;i<count;i++){
            String search = "selected"+i;
            //if (lazyForm.get(search)!=null){
            if (request.getParameter(search) != null) {
                //int id = Integer.parseInt(lazyForm.get("fldFavoriteId"+i).toString());
                int id = Integer.parseInt(request.getParameter("fldFavoriteId"+i));
                favIDs.add(id);
            }
        }
       
        for(Integer id:favIDs) {
        	Favorites f = favoritesDao.find(id);
        	Favorites copy = new Favorites();
        	try {
	        	BeanUtils.copyProperties(f, copy);
	        	copy.setProviderNo(providerNo);
	        	copy.setId(null);
	        	favoritesDao.persist(copy);
        	}catch(Exception e) {
        		logger.error("error",e);
        	}
        }
         
        return SUCCESS;
    }

}
