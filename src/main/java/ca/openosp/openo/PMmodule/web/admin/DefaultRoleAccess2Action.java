//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.PMmodule.web.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.PMmodule.model.DefaultRoleAccess;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.utility.RoleCache;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.services.security.RolesManager;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for managing default role access permissions in the Program Management module.
 * <p>
 * This action handles CRUD operations for default role access configurations, which define
 * what access types (read, write, update, etc.) are granted to specific roles by default
 * when accessing program resources.
 * <p>
 * Operations supported:
 * <ul>
 * <li>List all default role access configurations</li>
 * <li>Edit existing default role access</li>
 * <li>Save new or updated default role access</li>
 * <li>Delete default role access</li>
 * </ul>
 * <p>
 * After save or delete operations, the RoleCache is automatically reloaded to ensure
 * the system reflects the latest permissions.
 *
 * @since 2005-10-01
 */
public class DefaultRoleAccess2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private RolesManager roleManager = SpringUtils.getBean(RolesManager.class);

    /**
     * Main execution method that routes to appropriate sub-methods based on method parameter.
     * <p>
     * Routes to:
     * <ul>
     * <li>save() - Saves a new or updated default role access</li>
     * <li>edit() - Loads default role access for editing</li>
     * <li>delete() - Deletes a default role access</li>
     * <li>list() - Lists all default role accesses (default if no method specified)</li>
     * </ul>
     *
     * Expected request parameters:
     * <ul>
     * <li>method - String method name ("save", "edit", "delete", or none for list)</li>
     * </ul>
     *
     * @return String result name for Struts 2 result mapping
     */
    public String execute() {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        if ("edit".equals(request.getParameter("method"))) {
            return edit();
        }
        if ("delete".equals(request.getParameter("method"))) {
            return delete();
        }
        return list();
    }

    /**
     * Lists all default role access configurations.
     * <p>
     * Retrieves all default role access records and sets them as a request attribute
     * for display in the list view.
     *
     * @return String "list" to forward to the list view
     */
    public String list() {
        request.setAttribute("default_roles", programManager.getDefaultRoleAccesses());
        return "list";
    }

    /**
     * Loads a default role access for editing or creates a new one.
     * <p>
     * If an ID is provided, loads the existing default role access. Otherwise, creates
     * a new empty DefaultRoleAccess object. Sets available roles and access types as
     * request attributes and reloads the role cache.
     *
     * Expected request parameters:
     * <ul>
     * <li>id - String ID of the default role access to edit (optional)</li>
     * </ul>
     *
     * @return String "form" to forward to the edit form view
     */
    public String edit() {
        DefaultRoleAccess dra = null;

        String id = request.getParameter("id");

        if (id != null) {
            dra = programManager.getDefaultRoleAccess(id);
            if (dra != null) {
                this.setForm(dra);
            }
        }
        if (dra == null) {
            dra = new DefaultRoleAccess();
        }

        this.setForm(dra);
        request.setAttribute("roles", roleManager.getRoles());
        request.setAttribute("access_types", programManager.getAccessTypes());

        RoleCache.reload();
        return "form";
    }

    /**
     * Saves a new or updated default role access configuration.
     * <p>
     * If the form object has an ID of 0, treats it as a new record and sets ID to null.
     * Only saves if a default role access with the same roleId and accessTypeId combination
     * doesn't already exist (prevents duplicates). Reloads the role cache after saving
     * to ensure permissions are up-to-date throughout the system.
     *
     * @return String "rlist" to redirect to the list view
     */
    public String save() {
        DefaultRoleAccess dra = this.getForm();

        if (dra.getId().longValue() == 0) {
            dra.setId(null);
        }

        if (programManager.findDefaultRoleAccess(dra.getRoleId(), dra.getAccessTypeId()) == null) {
            programManager.saveDefaultRoleAccess(dra);
        }
        this.addMessage(request, "message", "Saved Access");

        RoleCache.reload();

        return "rlist";
    }

    /**
     * Deletes a default role access configuration by ID.
     * <p>
     * Removes the specified default role access and reloads the role cache to ensure
     * the deletion is reflected throughout the system.
     *
     * Expected request parameters:
     * <ul>
     * <li>id - String ID of the default role access to delete</li>
     * </ul>
     *
     * @return String "rlist" to redirect to the list view
     */
    public String delete() {
        String id = request.getParameter("id");

        if (id != null) {
            programManager.deleteDefaultRoleAccess(id);
        }

        this.addMessage(request, "message", "Removed Access");

        RoleCache.reload();

        return "rlist";
    }

    /**
     * Sets the program manager service.
     * <p>
     * This setter can be used for dependency injection, though the class
     * currently uses SpringUtils for bean retrieval.
     *
     * @param mgr ProgramManager service instance
     */
    public void setProgramManager(ProgramManager mgr) {
        this.programManager = mgr;
    }

    /**
     * Sets the roles manager service.
     * <p>
     * This setter can be used for dependency injection, though the class
     * currently uses SpringUtils for bean retrieval.
     *
     * @param mgr RolesManager service instance
     */
    public void setRolesManager(RolesManager mgr) {
        this.roleManager = mgr;
    }

    /**
     * Helper method to add action messages using Struts 2 message system.
     *
     * @param req HttpServletRequest (not used, kept for compatibility)
     * @param key String message key
     * @param val String message value
     */
    private void addMessage(HttpServletRequest req, String key, String val) {
        addActionMessage(getText(key, val));
    }

    /**
     * The DefaultRoleAccess form object being edited or saved.
     * <p>
     * This property is populated via Struts 2 property injection when
     * form data is submitted, or set explicitly during edit operations.
     */
    private DefaultRoleAccess form;

    /**
     * Gets the DefaultRoleAccess form object.
     *
     * @return DefaultRoleAccess the form object being edited or saved
     */
    public DefaultRoleAccess getForm() {
        return form;
    }

    /**
     * Sets the DefaultRoleAccess form object.
     * <p>
     * Called by Struts 2 during form submission to populate the object
     * with submitted form data, or called explicitly during edit operations.
     *
     * @param form DefaultRoleAccess the form object to set
     */
    public void setForm(DefaultRoleAccess form) {
        this.form = form;
    }
}
