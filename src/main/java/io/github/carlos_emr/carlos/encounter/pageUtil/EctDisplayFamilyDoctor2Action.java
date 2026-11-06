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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.encounter.display.EchartPatientDoctorResolver;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The eChart's Family Doctor panel at the top of the left navigation column (OSCAR 19's "fDoc" widget),
 * shown to providers whose eChart preferences turn it on. See {@link EctDisplayPatientDoctorAction}.
 *
 * @since 2026-10-16
 */
public class EctDisplayFamilyDoctor2Action extends EctDisplayPatientDoctorAction {

    static final String CMD = "fDoc";

    /** Struts-created router: resolves collaborators from the Spring context. */
    public EctDisplayFamilyDoctor2Action() {
        this(SpringUtils.getBean(DemographicManager.class), SpringUtils.getBean(EchartPatientDoctorResolver.class));
    }

    EctDisplayFamilyDoctor2Action(DemographicManager demographicManager, EchartPatientDoctorResolver doctorResolver) {
        super(demographicManager, doctorResolver);
    }

    @Override
    protected String headingKey() {
        return "demographic.procontact.role.familyDoctor";
    }

    @Override
    protected String doctorName(EchartPatientDoctorResolver resolver, LoggedInInfo loggedInInfo, Demographic demographic) {
        return resolver.familyDoctorName(loggedInInfo, demographic);
    }

    @Override
    public String getCmd() {
        return CMD;
    }
}
