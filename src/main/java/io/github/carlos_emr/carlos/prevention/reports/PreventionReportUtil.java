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


package io.github.carlos_emr.carlos.prevention.reports;

import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public final class PreventionReportUtil {
    private static Logger logger = MiscUtils.getLogger();

    // Looked up on each use rather than kept in static fields: a bean cached when the class first
    // loads would outlive a refreshed Spring context, or a unit test's mocks, for the rest of the JVM.
    private static DemographicManager demographicManager() {
        return SpringUtils.getBean(DemographicManager.class);
    }

    private static DemographicArchiveDao demographicArchiveDao() {
        return SpringUtils.getBean(DemographicArchiveDao.class);
    }

    public static boolean wasRostered(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate) {
        // Patient and provider numbers stay out of the log: report queries call this for every row.
        logger.debug("Checking roster history for one patient");
        Demographic demographic = demographicManager().getDemographic(loggedInInfo, demographicId);

        if (rosteredDuringThisTimeDemographic(onThisDate, demographic.getRosterDate(), demographic.getRosterTerminationDate()))
            return (true);

        List<DemographicArchive> archives = demographicArchiveDao().findByDemographicNo(demographicId);
        for (DemographicArchive archive : archives) {
            if (rosteredDuringThisTimeDemographicArchive(onThisDate, archive.getRosterDate(), archive.getRosterTerminationDate()))
                return (true);
        }

        return (false);
    }


    public static boolean wasEnrolledToThisProvider(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate, String providerNo) {
        // Patient and provider numbers stay out of the log: report queries call this for every row.
        logger.debug("Checking roster history for one patient");
        if (providerNo == null) {
            return false;
        }


        Demographic demographic = demographicManager().getDemographic(loggedInInfo, demographicId);

        if (rosteredDuringThisTimeDemographic(onThisDate, demographic.getRosterDate(), demographic.getRosterTerminationDate()) && providerNo.equals(demographic.getRosterEnrolledTo()))
            return (true);

        // An archive row is a snapshot of the record, so its roster period and the provider the
        // patient was enrolled to in that period come from the same row. Comparing with today's
        // record dropped patients who have since moved and counted ones who were elsewhere (#5877).
        List<DemographicArchive> archives = demographicArchiveDao().findByDemographicNo(demographicId);
        for (DemographicArchive archive : archives) {
            if (rosteredDuringThisTimeDemographicArchive(onThisDate, archive.getRosterDate(), archive.getRosterTerminationDate()) && providerNo.equals(archive.getRosterEnrolledTo()))
                return (true);
        }

        return (false);
    }

    public static boolean wasRosteredToThisProvider(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate, String providerNo) {
        return providerNo != null && wasRosteredToAnyProvider(loggedInInfo, demographicId, onThisDate, Set.of(providerNo));
    }

    /**
     * Checks the patient's roster periods for any selected provider.
     *
     * @param loggedInInfo LoggedInInfo authorizing the demographic read
     * @param demographicId Integer patient whose periods are checked
     * @param onThisDate Date requested by the report
     * @param providerNumbers Set of selected provider numbers
     * @return true if a roster period covers the date and belongs to a selected provider
     */
    public static boolean wasRosteredToAnyProvider(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate,
                                                    Set<String> providerNumbers) {
        // Patient and provider numbers stay out of the log: report queries call this for every row.
        logger.debug("Checking roster history for one patient");
        if (providerNumbers.isEmpty()) {
            return false;
        }

        Demographic current = demographicManager().getDemographic(loggedInInfo, demographicId);
        Date currentRevision = current.getLastUpdateDate();
        if (currentRevision != null && !currentRevision.after(onThisDate)) {
            return matchesRoster(providerNumbers, current.getProviderNo(), onThisDate,
                    current.getRosterDate(), current.getRosterTerminationDate())
                    && !hasCompetingProviderAtRevision(demographicId, current, currentRevision, onThisDate);
        }

        // A demographic save archives the previous version with its original lastUpdateDate.
        // Reassigning the MRP starts a new version even when the roster dates stay the same.
        List<DemographicArchive> archives = demographicArchiveDao().findByDemographicNo(demographicId);
        var asOfVersion = archives.stream()
                .filter(archive -> archive.getLastUpdateDate() != null && !archive.getLastUpdateDate().after(onThisDate))
                .max(Comparator.comparing(DemographicArchive::getLastUpdateDate)
                        .thenComparing(DemographicArchive::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (asOfVersion.isPresent()) {
            DemographicArchive archive = asOfVersion.get();
            return matchesRoster(providerNumbers, archive.getProviderNo(), onThisDate,
                    archive.getRosterDate(), archive.getRosterTerminationDate())
                    && !hasCompetingArchiveAtRevision(archives, archive, onThisDate);
        }

        // Legacy history can establish a closed period from roster dates alone. An undated
        // archive can also precede a newer dated current version. Require agreement when more
        // than one legacy snapshot covers the date.
        Set<String> historicalProviders = archives.stream()
                .filter(archive -> archive.getRosterTerminationDate() != null
                        || (currentRevision != null && archive.getLastUpdateDate() == null))
                .filter(archive -> rosteredDuringThisTimeDemographic(onThisDate, archive.getRosterDate(),
                        archive.getRosterTerminationDate()))
                .map(DemographicArchive::getProviderNo)
                .collect(Collectors.toCollection(HashSet::new));
        if (!historicalProviders.isEmpty()) {
            return historicalProviders.size() == 1
                    && historicalProviders.stream().anyMatch(provider -> provider != null && providerNumbers.contains(provider));
        }
        // The current roster can fill a legacy gap with no archival period covering the date.
        return archives.stream().noneMatch(archive -> rosteredDuringThisTimeDemographic(onThisDate,
                archive.getRosterDate(), archive.getRosterTerminationDate()))
                && matchesRoster(providerNumbers, current.getProviderNo(), onThisDate,
                        current.getRosterDate(), current.getRosterTerminationDate());
    }

    private static boolean matchesRoster(Set<String> providers, String provider, Date date, Date start, Date end) {
        return provider != null && providers.contains(provider) && rosteredDuringThisTimeDemographic(date, start, end);
    }

    private static boolean hasCompetingProviderAtRevision(Integer demographicId, Demographic current,
                                                          Date currentRevision, Date reportDate) {
        // An older provider change could carry the same timestamp into both records. Competing
        // providers with that timestamp leave the assignment on the report date unresolved.
        return demographicArchiveDao().findByDemographicNo(demographicId).stream()
                .anyMatch(archive -> archive.getLastUpdateDate() != null
                        && archive.getLastUpdateDate().getTime() == currentRevision.getTime()
                        && !Objects.equals(archive.getProviderNo(), current.getProviderNo())
                        && rosteredDuringThisTimeDemographic(reportDate, archive.getRosterDate(),
                                archive.getRosterTerminationDate()));
    }

    private static boolean hasCompetingArchiveAtRevision(List<DemographicArchive> archives, DemographicArchive selected,
                                                         Date reportDate) {
        // Legacy MRP reassignments copied the previous version's lastUpdateDate, so two archived
        // versions can share one timestamp. The archive ID orders when they were archived, not
        // when each provider took over, so competing providers leave the report date unresolved.
        long revision = selected.getLastUpdateDate().getTime();
        return archives.stream()
                .anyMatch(archive -> archive != selected
                        && archive.getLastUpdateDate() != null
                        && archive.getLastUpdateDate().getTime() == revision
                        && !Objects.equals(archive.getProviderNo(), selected.getProviderNo())
                        && rosteredDuringThisTimeDemographic(reportDate, archive.getRosterDate(),
                                archive.getRosterTerminationDate()));
    }

    private static boolean rosteredDuringThisTimeDemographic(Date onThisDate, Date rosterStart, Date rosterEnd) {

        if (rosterStart != null) {
            if (rosterStart.before(onThisDate)) {
                if (rosterEnd == null || rosterEnd.after(onThisDate)) {
                    logger.debug("true:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
                    return (true);
                }
            }
        }

        logger.debug("false:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
        return (false);
    }

    private static boolean rosteredDuringThisTimeDemographicArchive(Date onThisDate, Date rosterStart, Date rosterEnd) {
        // algorithm for demographic archive must only look at archiv erecords with end dates as the archive is populated upon every change not just people being unrostered.
        if (rosterStart != null && rosterEnd != null) {
            if (rosterStart.before(onThisDate) && rosterEnd.after(onThisDate)) {
                logger.debug("true:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
                return (true);
            }
        }

        logger.debug("false:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
        return (false);
    }
}
