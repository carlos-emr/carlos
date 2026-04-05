/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for demographic record update (save). Replaces the
 * {@code demographiccontrol.jsp} {@code displaymode=Update Record} route.
 * Forwards to {@code demographicupdatearecord.jsp} which handles all save logic.
 *
 * @since 2026-04-04
 */
public class DemographicUpdate2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Validates session and demographic write privileges, then forwards to the
     * update record JSP which performs the actual save.
     *
     * @return {@link #SUCCESS} on successful authorization
     * @throws SecurityException if the session is missing or the user lacks
     *         {@code _demographic} write privilege
     */
    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            logger.warn("DemographicUpdate2Action: missing session");
            throw new SecurityException("missing required session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            logger.warn("DemographicUpdate2Action: provider {} lacks _demographic write privilege",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_demographic)");
        }
        return SUCCESS;
    }
}
