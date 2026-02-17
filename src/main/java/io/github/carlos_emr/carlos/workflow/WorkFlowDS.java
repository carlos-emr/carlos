/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.workflow;

import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author jay
 */
public class WorkFlowDS {

    KieBase kieBase = null;

    /**
     * Creates a new instance of WorkFlowDS
     */
    public WorkFlowDS() {
    }

    public WorkFlowDS(KieBase kb) {
        this.kieBase = kb;
    }

    public WorkFlowInfo getMessages(WorkFlowInfo w) throws Exception {
        KieSession kieSession = kieBase.newKieSession();
        try {
            KieSession kieSession = kieBase.newKieSession();
            try {
                kieSession.insert(w);
                kieSession.fireAllRules();
            } finally {
                kieSession.dispose();
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            throw new Exception("ERROR: Drools ", e);
        } finally {
            kieSession.dispose();
        }
        return w;
    }


}
