/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;

import io.github.carlos_emr.carlos.commn.dao.OceanSettingDao;
import io.github.carlos_emr.carlos.commn.model.OceanSetting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.webserv.rest.to.model.OceanSettingsTo1;

/**
 * Backs the Ocean Toolbar's own {@code getSettings}/{@code saveSettings} REST
 * calls (verified against Ocean's live client script, {@code ConfigUtil.js},
 * served from {@code ocean.cognisantmd.com}). This endpoint was never
 * implemented in the open-source OSCAR19/OpenOSP lineage CARLOS forked from
 * (only in the closed-source commercial OscarPro fork) — see
 * {@code js/jquery_oscar_defaults.js} for that history.
 *
 * <p>CARLOS stores only the opaque settings blob the toolbar itself manages
 * (site number, encrypted secret key, etc.) — it never inspects, generates,
 * or validates that value. {@code getSettings} is readable by any
 * authenticated provider, matching every browser session that opens an
 * eChart encounter and needs the toolbar to initialize; the session and OAuth REST surfaces
 * authenticate the provider before every method here. {@code saveSettings}
 * additionally requires {@code _admin}, since it persists a credential-bearing
 * clinic-wide integration blob and should only be set up by an administrator,
 * matching the {@code _admin} gate on the eChart Display Settings admin page.</p>
 *
 * @since 2026-09-18
 */
@Path("/ocean")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OceanService extends AbstractServiceImpl {

    private final OceanSettingDao oceanSettingDao;
    private final SecurityInfoManager securityInfoManager;

    public OceanService(OceanSettingDao oceanSettingDao, SecurityInfoManager securityInfoManager) {
        this.oceanSettingDao = oceanSettingDao;
        this.securityInfoManager = securityInfoManager;
    }

    @GET
    @Path("/getSettings")
    public OceanSettingsTo1 getSettings() {
        // Authentication only (see class Javadoc); no further privilege check —
        // every logged-in provider viewing an eChart needs this to initialize.
        getLoggedInInfo();

        OceanSetting setting = oceanSettingDao.getSettings();
        return new OceanSettingsTo1(setting == null ? null : setting.getSettings());
    }

    @POST
    @Path("/saveSettings")
    public OceanSettingsTo1 saveSettings(OceanSettingsTo1 request) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new ForbiddenException("missing required sec object (_admin)");
        }

        if (request == null) {
            throw new BadRequestException("A settings object is required");
        }
        String settings = request.getSettings();
        OceanSetting saved = oceanSettingDao.saveSettings(settings, loggedInInfo.getLoggedInProviderNo());
        return new OceanSettingsTo1(saved.getSettings());
    }
}
