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
package io.github.carlos_emr.carlos.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.WaitlistDao;
import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.PMmodule.model.VacancyClientMatch;
import io.github.carlos_emr.carlos.match.client.ClientData;
import io.github.carlos_emr.carlos.match.vacancy.VacancyData;
import io.github.carlos_emr.carlos.match.vacancy.VacancyTemplateData;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Matching engine that computes compatibility scores between clients on a waitlist
 * and available program vacancies.
 *
 * <p>Compares client data attributes against vacancy template criteria using weighted
 * Levenshtein distance for string matching and range checking for numeric values.
 * Results are returned as sorted lists of {@link VacancyClientMatch} objects with
 * computed match percentages.</p>
 *
 * @see VacancyData
 * @see ClientData
 * @see VacancyClientMatch
 * @since 2026-03-17
 */
public class Matcher {

    private WaitlistDao waitlistDao = SpringUtils.getBean(WaitlistDao.class);
    private VacancyDao vacancyDao = SpringUtils.getBean(VacancyDao.class);


    /**
     * Lists all waitlisted clients matched against a specific vacancy, sorted by match percentage.
     *
     * @param vacancyId int the unique identifier of the vacancy to match against
     * @return List of {@link VacancyClientMatch} sorted by descending match percentage
     */
    public List<VacancyClientMatch> listClientMatchesForVacancy(int vacancyId) {
        List<VacancyClientMatch> vacancyClientMatches = new ArrayList<VacancyClientMatch>();
        VacancyData vacancyData = waitlistDao.loadVacancyData(vacancyId);
        List<ClientData> clientDatas = waitlistDao.getAllClientsData();
        for (ClientData clientData : clientDatas) {
            VacancyClientMatch vcMatch = match(clientData, vacancyData);
            vacancyClientMatches.add(vcMatch);
        }
        Collections.sort(vacancyClientMatches);
        return vacancyClientMatches;
    }

    /**
     * Lists clients matched against a vacancy within a specific waitlist program.
     *
     * @param vacancyId int the unique identifier of the vacancy
     * @param wlProgramId int the waitlist program identifier to scope the client search
     * @return List of {@link VacancyClientMatch} sorted by descending match percentage
     */
    public List<VacancyClientMatch> listClientMatchesForVacancy(int vacancyId, int wlProgramId) {
        List<VacancyClientMatch> vacancyClientMatches = new ArrayList<VacancyClientMatch>();
        VacancyData vacancyData = waitlistDao.loadVacancyData(vacancyId, wlProgramId);
        List<ClientData> clientDatas = waitlistDao.getAllClientsDataByProgramId(wlProgramId);
        for (ClientData clientData : clientDatas) {
            VacancyClientMatch vcMatch = match(clientData, vacancyData);
            vacancyClientMatches.add(vcMatch);
        }
        Collections.sort(vacancyClientMatches);
        return vacancyClientMatches;
    }

    /**
     * Lists all vacancies matched against a specific client within a program.
     *
     * @param clientId int the demographic number of the client
     * @param programId int the program identifier to scope the vacancy search
     * @return List of {@link VacancyClientMatch} sorted by descending match percentage
     */
    public List<VacancyClientMatch> listVacancyMatchesForClient(int clientId, int programId) {
        List<VacancyClientMatch> vacancyClientMatches = new ArrayList<VacancyClientMatch>();
        ClientData clientData = waitlistDao.getClientData(clientId);
        List<VacancyData> vacancyDataList = loadAllVacancies(programId);
        for (VacancyData vData : vacancyDataList) {
            VacancyClientMatch vcMatch = match(clientData, vData);
            vacancyClientMatches.add(vcMatch);
        }
        Collections.sort(vacancyClientMatches);
        return vacancyClientMatches;
    }

    /**
     * Lists all current vacancies matched against a specific client across all programs.
     *
     * @param clientId int the demographic number of the client
     * @return List of {@link VacancyClientMatch} sorted by descending match percentage;
     *         empty list if the client has no data attributes
     */
    public List<VacancyClientMatch> listVacancyMatchesForClient(int clientId) {
        List<VacancyClientMatch> vacancyClientMatches = new ArrayList<VacancyClientMatch>();
        ClientData clientData = waitlistDao.getClientData(clientId);
        if (clientData.getClientData().size() == 0) return vacancyClientMatches;
        List<VacancyData> vacancyDataList = loadAllVacancies();
        for (VacancyData vData : vacancyDataList) {
            VacancyClientMatch vcMatch = match(clientData, vData);
            vacancyClientMatches.add(vcMatch);
        }
        Collections.sort(vacancyClientMatches);
        return vacancyClientMatches;
    }

    private List<VacancyData> loadAllVacancies(int programId) {
        List<VacancyData> vacancies = new ArrayList<VacancyData>();
        List<Integer> vacancyDataList = new ArrayList<Integer>();
        for (Vacancy v : vacancyDao.findCurrent()) {
            vacancyDataList.add(v.getId());
        }
        for (Integer vacancyId : vacancyDataList) {
            VacancyData vacancyData = waitlistDao.loadVacancyData(vacancyId, programId);
            vacancies.add(vacancyData);
        }
        return vacancies;

    }

    private List<VacancyData> loadAllVacancies() {
        List<VacancyData> vacancies = new ArrayList<VacancyData>();
        List<Integer> vacancyDataList = new ArrayList<Integer>();
        for (Vacancy v : vacancyDao.findCurrent()) {
            vacancyDataList.add(v.getId());
        }
        for (Integer vacancyId : vacancyDataList) {
            VacancyData vacancyData = waitlistDao.loadVacancyData(vacancyId);
            vacancies.add(vacancyData);
        }
        return vacancies;

    }


    private VacancyClientMatch match(ClientData clientData,
                                     VacancyData vacancyData) {
        VacancyClientMatch vacancyClientMatch = new VacancyClientMatch();
        vacancyClientMatch.setClient_id(clientData.getClientId());
        vacancyClientMatch.setVacancy_id(vacancyData.getVacancy_id());
        vacancyClientMatch.setForm_id(clientData.getFormId());
        vacancyClientMatch.setStatus(VacancyClientMatch.PENDING);

        int paramCntPercentage = vacancyData.getVacancyData().size();
        int paramMatch = getParamMatch(clientData, vacancyData);
        if (paramCntPercentage == 0) {
            vacancyClientMatch.setMatchPercentage(0);
        } else {
            vacancyClientMatch.setMatchPercentage(paramMatch / vacancyData.getVacancyData().size());
            String proportion = String.format("%d/%d", paramMatch / 100, paramCntPercentage);
            vacancyClientMatch.setProportion(proportion);
        }
        return vacancyClientMatch;
    }

    private int getParamMatch(ClientData clientData, VacancyData vacancyData) {
        int paramMatch = 0;
        for (Entry<String, String> paramEntry : clientData.getClientData()
                .entrySet()) {
            VacancyTemplateData vacancyTemlateData = vacancyData.getVacancyData().get(paramEntry.getKey());
            if (vacancyTemlateData != null) {
                paramMatch += vacancyTemlateData.matches(paramEntry.getValue());
            }
        }
        return paramMatch;
    }
}
