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


package io.github.carlos_emr.carlos.login.tld;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.Tag;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * JSP custom tag that conditionally renders its body based on the user's role-based security privileges.
 *
 * <p>Evaluates whether the current user's role has the required rights (read, write, etc.)
 * for the specified security object. If the user has the privilege, the tag body is rendered;
 * otherwise it is skipped. The {@code reverse} attribute inverts this behavior.
 *
 * <p>When the ENABLE_SECURITY_OBJECT_DEBUG property is active, the tag also renders
 * a visible debug overlay showing the security object name.
 *
 * <p>Usage in JSP:
 * <pre>
 * &lt;security:oscarSec roleName="${roleName}" objectName="_demographic" rights="r"&gt;
 *   ...secured content...
 * &lt;/security:oscarSec&gt;
 * </pre>
 *
 * @see io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege
 * @since 2026-03-17
 */
public class SecurityTag implements Tag {
    private PageContext pageContext;
    private Tag parentTag;
    private String roleName;
    private String objectName;
    private String rights = "r";
    private boolean reverse = false;

    /** {@inheritDoc} */
    public void setPageContext(PageContext arg0) {
        this.pageContext = arg0;

    }

    /** {@inheritDoc} */
    public void setParent(Tag arg0) {
        this.parentTag = arg0;
    }

    /** {@inheritDoc} */
    public Tag getParent() {
        return this.parentTag;
    }

    /**
     * Evaluates the user's privilege and decides whether to include or skip the tag body.
     *
     * @return int EVAL_BODY_INCLUDE if the user has the required privilege, SKIP_BODY otherwise
     *         (inverted when reverse is true)
     * @throws JspException if an error occurs during tag evaluation
     */
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
            if (CarlosProperties.getInstance().isPropertyActive("ENABLE_SECURITY_OBJECT_DEBUG")) {
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

    /**
     * Always continues evaluating the rest of the page after this tag.
     *
     * @return int EVAL_PAGE
     * @throws JspException if an error occurs
     */
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    public void release() {
    }

    /** @return String the security object name to check privileges for */
    public String getObjectName() {
        return objectName;
    }

    /** @param objectName String the security object name to check privileges for */
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    /** @return String the role name of the current user */
    public String getRoleName() {
        return roleName;
    }

    /** @param roleName String the role name of the current user */
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    /** @return String the required rights (e.g., "r" for read, "w" for write) */
    public String getRights() {
        return rights;
    }

    /** @param rights String the required rights (e.g., "r" for read, "w" for write) */
    public void setRights(String rights) {
        this.rights = rights;
    }

    /** @return boolean true if the privilege check result should be inverted */
    public boolean isReverse() {
        return reverse;
    }

    /** @param reverse boolean true to invert the privilege check (show body when user lacks privilege) */
    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    /**
     * Gets the Spring application context from the servlet context.
     *
     * @return ApplicationContext the Spring web application context
     */
    public ApplicationContext getAppContext() {
        return WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext());
    }

}
