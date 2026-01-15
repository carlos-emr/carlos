//CHECKSTYLE:OFF
/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

/*
 * GstControl2Action.java
 *
 * Created on July 18, 2007, 12:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.billings.ca.on.administration;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.billing.CA.dao.GstControlDao;
import ca.openosp.openo.billing.CA.model.GstControl;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

public class GstControl2Action extends ActionSupport implements ServletRequestAware, ServletResponseAware  {
    private HttpServletRequest request;
    private HttpServletResponse response;
    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.response = response;
    }


    private GstControlDao dao = SpringUtils.getBean(GstControlDao.class);


    public String execute() throws ServletException, IOException {
        writeDatabase(this.getGstPercent());

        return SUCCESS;
    }

    public void writeDatabase(String percent) {
        for (GstControl g : dao.findAll()) {
            g.setGstPercent(BigDecimal.valueOf(Double.valueOf(percent)));
            dao.merge(g);
        }
    }

    public Properties readDatabase() {
        Properties props = new Properties();
        for (GstControl g : dao.findAll()) {
            props.setProperty("gstPercent", g.getGstPercent().toString());
        }
        return props;
    }
    String gstPercent;

    public String getGstPercent() {
        return gstPercent;
    }

    public void setGstPercent(String gstPercent) {
        this.gstPercent = gstPercent;
    }
}
