/**
 * Copyright (c) 2001-2002. Andromedia. All Rights Reserved.
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
 * Andromedia, to be provided as
 * part of the OSCAR McMaster
 * EMR System
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7MessageDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7ObrDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7PidDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Message;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7.V2_3.MSH;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7.V2_3.PID;
import io.github.carlos_emr.carlos.util.ConversionUtils;

public class Message {
    Logger _logger = MiscUtils.getLogger();
    private Hl7MessageDao hl7MessageDao = SpringUtils.getBean(Hl7MessageDao.class);

    private static final String lineBreak = "\n";

    private PID pid = null;
    private MSH msh = null;
    private Node current;

    private PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    private ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);

    public Message(String now) {
        this.current = null;
    }

    // Parses HL7 message. Splits at the line breaks and then checks to see what the
    // line starts with
    // If the line starts with MSH it create a new MSH message and calls parse on
    // the MSH object
    // If the line starts with PID it create a new PID Node and calls parse on it
    // Parse returns a in instance of itself (PID Node)
    // If the line starts with NTE it calls parse on current Node, which is the PID
    // node
    // If the line starts with anything else the pid.parse is called which. The PID
    // object handles parsing
    // any other line ie( ORC, OBR, OBX ) and returns and instance of them selves to
    // (Current)
    // This is how NTE objects get attached to OBX and OBR... because the NTE will
    // follow the OBR or OBX that it was intended for.
    public void Parse(String data) {
        _logger.debug("Parsing HL7 message");
        String[] lines = data.split(lineBreak);
        int count = lines.length;
        _logger.debug("Parsing " + count + " lines");
        for (int i = 0; i < count; ++i) {
            _logger.debug("line: " + lines[i]);
            if (lines[i].startsWith("MSH")) {
                this.msh = new MSH();
                current = this.msh.Parse(lines[i]);
            } else if (lines[i].startsWith("PID")) {
                this.pid = new PID();
                current = this.pid.Parse(lines[i]);
            } else if (lines[i].startsWith("NTE")) {
                if (current != null) {
                    current.Parse(lines[i]);
                }
            } else if (this.pid != null) {
                current = this.pid.Parse(lines[i]);
            }
        }
    }

    public String toString() {
        return pid.toString();
    }

    public void ToDatabase() throws SQLException {
        Hl7Message h = new Hl7Message();
        h.setDateTime(new Date());
        hl7MessageDao.persist(h);
        int parent = h.getId();

        msh.ToDatabase(parent);
        int id = pid.ToDatabase(parent);
        linkToProvider(parent, id);
        patientRouteReport(parent);
    }

    /**
     * Routes the message to its ordering and copied-to providers, or to provider "0" (unclaimed)
     * when the OBR is missing or names no known provider. Those are data conditions: the lab must
     * still be stored so it reaches the unclaimed inbox. Nothing is caught, so a failed lookup or
     * routing write propagates and the caller's transaction rolls the message back rather than
     * committing it unrouted or routed to the fallback instead of its provider.
     */
    public void linkToProvider(int parent, int id) {
        List<String> providers = resolveProviders(id);
        if (providers.isEmpty()) {
            ProviderLabRoutingModel l = new ProviderLabRoutingModel();
            l.setProviderNo("0");
            l.setLabNo(parent);
            l.setStatus("N");
            l.setLabType("BCP");
            providerLabRoutingDao.persist(l);
            return;
        }
        ProviderLabRouting routing = new ProviderLabRouting();
        for (String prov : providers) {
            routing.routeMagic(parent, prov, "BCP");
        }
    }

    // The ordering provider first, then each result-copies-to provider, resolved from the ministry
    // number in the first component. A blank or unknown number is skipped, not routed to "".
    private List<String> resolveProviders(int pidId) {
        List<String> providers = new ArrayList<>();
        List<Hl7Obr> obrs = SpringUtils.getBean(Hl7ObrDao.class).findByPid(pidId);
        if (obrs.isEmpty()) {
            return providers;
        }
        Hl7Obr obr = obrs.get(0);
        addResolvedProvider(providers, obr.getOrderingProvider());
        if (obr.getResultCopiesTo() != null) {
            for (String copiedTo : obr.getResultCopiesTo().split("~")) {
                addResolvedProvider(providers, copiedTo);
            }
        }
        return providers;
    }

    private void addResolvedProvider(List<String> providers, String hl7Provider) {
        if (hl7Provider == null || hl7Provider.isBlank()) {
            return;
        }
        String providerNo = getProviderNoFromBillingNo(hl7Provider.split("\\^")[0]);
        if (!providerNo.isEmpty() && !providers.contains(providerNo)) {
            providers.add(providerNo);
        }
    }

    public String getProviderNoFromBillingNo(String providerMinistryNo) {
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> providers = dao.getBillableProvidersByOHIPNo(providerMinistryNo);
        if (providers.isEmpty()) {
            return "";
        }
        return providers.get(0).getProviderNo();
    }

    ////
    /**
     * Links the message to its patient, or to demographic 0 (unmatched) when the PID is missing,
     * cannot be parsed or matches nobody. As in {@link #linkToProvider}, only those data conditions
     * fall back; a failed lookup or routing write propagates.
     */
    public void patientRouteReport(int segmentID) {
        Integer demographicNo = matchPatient(segmentID);
        PatientLabRouting l = new PatientLabRouting();
        l.setLabNo(segmentID);
        l.setLabType("BCP");
        l.setDemographicNo(demographicNo == null ? 0 : demographicNo);
        patientLabRoutingDao.persist(l);

        // NOT ALL DOCS WANT ALL LABS ECHO'D INTO THERE INBOX
        if (demographicNo != null) {
            patientProviderRoute("" + segmentID, demographicNo.toString());
        }
    }

    // Matches an active patient on initials, date of birth and sex, and the health number when
    // the PID carries one; null when the PID is absent, unparseable or matches nobody.
    private Integer matchPatient(int segmentID) {
        List<Hl7Pid> pids = SpringUtils.getBean(Hl7PidDao.class).findByMessageId(segmentID);
        if (pids.isEmpty()) {
            return null;
        }
        Hl7Pid pid = pids.get(0);
        String name = pid.getPatientName();
        Date dob = pid.getDateOfBirth();
        if (name == null || dob == null) {
            return null;
        }
        String[] nameParts = name.split("\\^");
        if (nameParts.length < 2 || nameParts[0].isEmpty() || nameParts[1].isEmpty()) {
            return null;
        }
        String lastInitial = nameParts[0].toUpperCase().substring(0, 1);
        String firstInitial = nameParts[1].toUpperCase().substring(0, 1);
        String dobYear = new SimpleDateFormat("yyyy").format(dob);
        String dobMonth = new SimpleDateFormat("MM").format(dob);
        String dobDay = new SimpleDateFormat("dd").format(dob);

        DemographicDaoImpl dDao = SpringUtils.getBean(DemographicDaoImpl.class);
        List<Demographic> demos = dDao.findByCriterion(new DemographicDaoImpl.DemographicCriterion(
                pid.getExternalId(), lastInitial, firstInitial, dobYear, dobMonth, dobDay, pid.getSex(), "AC"));
        return demos.isEmpty() ? null : demos.get(0).getDemographicNo();
    }

    ////
    public void patientProviderRoute(String lab_no, String demographic_no) {

        DemographicDao dao = SpringUtils.getBean(DemographicDao.class);
        Demographic demo = dao.getDemographic(demographic_no);

        if (demo != null) {
            String prov_no = demo.getProviderNo();

            if (prov_no != null && !prov_no.trim().equals("")) {
                ProviderLabRoutingDao pDao = SpringUtils.getBean(ProviderLabRoutingDao.class);
                List<ProviderLabRoutingModel> routings = pDao
                        .findByLabNoAndLabTypeAndProviderNo(ConversionUtils.fromIntString(lab_no), "BCP", prov_no);

                if (!routings.isEmpty()) {
                    ProviderLabRouting router = new ProviderLabRouting();
                    router.routeMagic(ConversionUtils.fromIntString(lab_no), prov_no, "BCP");
                } else {
                    MiscUtils.getLogger().debug("prov was " + prov_no);
                }
            }
        }
    }
}
