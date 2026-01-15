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
package ca.openosp.openo.commn.web;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.apache.commons.text.WordUtils;
import ca.openosp.openo.commn.dao.AbstractCodeSystemDao;
import ca.openosp.openo.commn.model.AbstractCodeSystemModel;
import ca.openosp.openo.utility.SpringUtils;

/**
 * @author marc
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.util.LabelValueBean;

public class CodeSearchService2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute() throws Exception {
        List<LabelValueBean> results = new ArrayList<LabelValueBean>();
        String codingSystem = request.getParameter("codingSystem");

        AbstractCodeSystemDao dao = (AbstractCodeSystemDao) SpringUtils.getBean(WordUtils.uncapitalize(codingSystem) + "Dao");

        if (request.getParameter("term") != null && request.getParameter("term").length() > 0) {
            List<AbstractCodeSystemModel> r = dao.searchCode(request.getParameter("term"));
            for (AbstractCodeSystemModel result : r) {
                results.add(new LabelValueBean(result.getDescription(), result.getCode()));
            }
        }
        response.setContentType("text/x-json");
        ArrayNode jsonArray = objectMapper.valueToTree(results);
        response.getWriter().write(jsonArray.toString());

        return null;
    }


}
