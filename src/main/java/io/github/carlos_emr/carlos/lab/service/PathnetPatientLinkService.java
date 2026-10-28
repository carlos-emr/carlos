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
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7LinkDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7PidDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Link;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.List;

/**
 * Keeps legacy PID links and inbox/chart message routing in one transaction.
 *
 * @since 2026-10-23
 */
@Service
public class PathnetPatientLinkService {
    private final Hl7PidDao pids;
    private final Hl7LinkDao links;
    private final MrpRoutingService routing;

    public PathnetPatientLinkService(Hl7PidDao pids, Hl7LinkDao links, MrpRoutingService routing) {
        this.pids = pids;
        this.links = links;
        this.routing = routing;
    }

    /** A PID selection; a null patient confirms its existing positive legacy link. */
    public record Selection(int pidId, Integer demographicNo) { }

    /**
     * Applies every selection from one form submission atomically.
     *
     * @param selections List&lt;Selection&gt; parsed lab identifiers and optional patients
     * @param actor String the provider number performing the match
     * @throws IllegalArgumentException if any lab or patient selection is invalid; all selections roll back
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void linkAll(List<Selection> selections, String actor) {
        for (Selection selection : selections) linkWithinTransaction(selection.pidId(), selection.demographicNo(), actor);
    }

    /**
     * Links a PathNet lab to a patient through the shared, locked matching path.
     *
     * @param pidId int the {@code hl7_pid} identifier, distinct from its message identifier
     * @param demographicNo Integer the selected patient, or null to confirm the existing positive link
     * @param actor String the provider number performing the match
     * @throws IllegalArgumentException if the lab is absent or disconnected, or the patient selection is invalid
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void link(int pidId, Integer demographicNo, String actor) {
        linkWithinTransaction(pidId, demographicNo, actor);
    }

    private void linkWithinTransaction(int pidId, Integer demographicNo, String actor) {
        Hl7Pid pid = pids.find(pidId);
        if (pid == null || pid.getMessageId() <= 0) throw new IllegalArgumentException("Lab does not exist");
        // Explicit matches let the shared routing service read/update links after acquiring its locks.
        if (demographicNo == null) {
            Hl7Link link = links.find(pidId);
            if (link == null || link.getDemographicNo() <= 0) {
                throw new IllegalArgumentException("Select a patient before confirming the lab");
            }
            demographicNo = link.getDemographicNo();
        }
        routing.matchPatientLab(Integer.toString(pid.getMessageId()), "BCP", demographicNo, actor);
    }
}
