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


package io.github.carlos_emr.carlos.login.tld;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

public class SecurityTag implements Tag {
    private PageContext pageContext;
    private Tag parentTag;
    private String roleName;
    private String objectName;
    private String rights = "r";
    private boolean reverse = false;

    public void setPageContext(PageContext arg0) {
        this.pageContext = arg0;

    }

    public void setParent(Tag arg0) {
        this.parentTag = arg0;
    }

    public Tag getParent() {
        return this.parentTag;
    }

    public int doStartTag() throws JspException {
        int ret = 0;
        Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
        /*TODO: temporily allow current sec work, the if statement should be removed */
        if (roleName == null)
        {
            ret = SKIP_BODY;
        }
        else
        {
            if(OscarProperties.getInstance().isPropertyActive("ENABLE_SECURITY_OBJECT_DEBUG")) {
                try {
                    JspWriter out = pageContext.getOut();
                    out.println(
                            "<div class='role-object' style='font-size:12px;color:red;z-index:100000; background-color:white; padding:5px;'>"
                                    + objectName
                                    + "</div>"
                    );
                } catch (Exception e) {
                    // do nothing.
                }
            }
            if (OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (List<String>) v.get(1), (List<String>) v.get(2), rights)) {
                ret = EVAL_BODY_INCLUDE;
            } else {
                ret = SKIP_BODY;
            }
        }

        if (reverse) {
            if (ret == EVAL_BODY_INCLUDE)
                ret = SKIP_BODY;
            else
                ret = EVAL_BODY_INCLUDE;
        }
        return ret;
    }

    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    public void release() {
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRights() {
        return rights;
    }

    public void setRights(String rights) {
        this.rights = rights;
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public ApplicationContext getAppContext() {
        return WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext());
    }

}
