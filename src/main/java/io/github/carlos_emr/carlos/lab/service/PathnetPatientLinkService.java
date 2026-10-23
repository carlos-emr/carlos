/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7LinkDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7PidDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Link;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the legacy PID link and the inbox/chart's message routing in one transaction. */
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

    @Transactional
    public void link(int pidId, Integer demographicNo, String actor) {
        Hl7Pid pid = pids.find(pidId);
        if (pid == null || pid.getMessageId() <= 0) throw new IllegalArgumentException("Lab does not exist");
        Hl7Link link = links.find(pidId);
        if (demographicNo == null) {
            if (link == null || link.getDemographicNo() <= 0) {
                throw new IllegalArgumentException("Select a patient before confirming the lab");
            }
            demographicNo = link.getDemographicNo();
        }
        routing.matchPatientLab(Integer.toString(pid.getMessageId()), "BCP", demographicNo, actor);
    }
}
