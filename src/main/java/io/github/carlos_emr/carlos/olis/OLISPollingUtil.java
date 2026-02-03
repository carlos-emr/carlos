package io.github.carlos_emr.carlos.olis;
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
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.olis.dao.OLISProviderPreferencesDao;
import io.github.carlos_emr.carlos.olis.dao.OLISSystemPreferencesDao;
import io.github.carlos_emr.carlos.olis.model.OLISProviderPreferences;
import io.github.carlos_emr.carlos.olis.model.OLISSystemPreferences;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

import io.github.carlos_emr.carlos.olis1.Driver;
import io.github.carlos_emr.carlos.olis1.parameters.OBR22;
import io.github.carlos_emr.carlos.olis1.parameters.ORC21;
import io.github.carlos_emr.carlos.olis1.parameters.ZRP1;
import io.github.carlos_emr.carlos.olis1.queries.Z04Query;
import io.github.carlos_emr.carlos.olis1.queries.Z06Query;

public class OLISPollingUtil {

    private static final Logger logger = MiscUtils.getLogger();

    static ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    static OLISSystemPreferencesDao olisSystemPreferencesDao = SpringUtils.getBean(OLISSystemPreferencesDao.class);
    static OLISProviderPreferencesDao olisProviderPreferencesDao = SpringUtils.getBean(OLISProviderPreferencesDao.class);
    static UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    static String[] dateFormat = new String[]{"yyyyMMddHHmmssZ"};

    public OLISPollingUtil() {
        super();
    }

    public static void requestResults(LoggedInInfo loggedInInfo) {
        OLISSystemPreferences olisSystemPreferences = olisSystemPreferencesDao.getPreferences();
        String defaultStartTime = Misc.getStr(olisSystemPreferences.getStartTime(), "").trim();
        String defaultEndTime = Misc.getStr(olisSystemPreferences.getEndTime(), "").trim();

        pollZ04Query(loggedInInfo, defaultStartTime, defaultEndTime);

        String facilityId = OscarProperties.getInstance().getProperty("olis_polling_facility"); //Most of the time this will default to null.
        if (facilityId != null) {
            pollZ06Query(loggedInInfo, defaultStartTime, defaultEndTime, facilityId);
        }
    }

    private static void updateProviderLastRun(String providerNo) {
        OLISProviderPreferences olisProviderPreferences = olisProviderPreferencesDao.findById(providerNo);
        if (olisProviderPreferences == null) {
            olisProviderPreferences = new OLISProviderPreferences();
            olisProviderPreferences.setProviderId(providerNo);
            olisProviderPreferences.setLastRun(new Date());
            olisProviderPreferences.setStartTime("");
            olisProviderPreferencesDao.persist(olisProviderPreferences);
        } else {
            olisProviderPreferences.setLastRun(new Date());
            olisProviderPreferencesDao.merge(olisProviderPreferences);
        }
    }

    public static void doZ04PollOnBehalfOf(LoggedInInfo loggedInInfo, String providerNo) {
        OLISSystemPreferences olisSystemPreferences = olisSystemPreferencesDao.getPreferences();
        OLISProviderPreferences olisProviderPreferences = olisProviderPreferencesDao.findById(providerNo);

        String defaultStartTime = Misc.getStr(olisSystemPreferences.getStartTime(), "").trim();
        String defaultEndTime = Misc.getStr(olisSystemPreferences.getEndTime(), "").trim();

        String providerStartTime = null;
        if (olisProviderPreferences != null) {
            providerStartTime = Misc.getStr(olisProviderPreferences.getStartTime(), "").trim();
        }

        if (!StringUtils.isEmpty(providerStartTime)) {
            defaultStartTime = providerStartTime;
        }

        logger.info("Z04 Polling: startDateTime = " + defaultStartTime + ", endDateTime=" + defaultEndTime);

        updateProviderLastRun(providerNo);

        pollZ04Query(loggedInInfo, providerNo, defaultStartTime, defaultEndTime);
    }

    private static void pollZ04Query(LoggedInInfo loggedInInfo, String providerNo, String defaultStartTime, String defaultEndTime) {
        OLISProviderPreferences olisProviderPreferences = olisProviderPreferencesDao.findById(providerNo);
        Provider provider = providerDao.getProvider(providerNo);
        if (provider == null) return;

        String officialLastName = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME);
        String officialfirstName = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME);
        String officialSecondName = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME);
        String officialIdType = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_OLIS_IDTYPE);

        if (officialLastName == null || officialLastName.trim().equals("")) {
            logger.info("No official name..aborting");
            return;
        }

        Z04Query providerQuery = new Z04Query();


        try {
            OBR22 obr22 = new OBR22();
            List<Date> dateList = new LinkedList<Date>();
            if (olisProviderPreferences != null) {
                if (StringUtils.isEmpty(olisProviderPreferences.getStartTime())) {
                    olisProviderPreferences.setStartTime(defaultStartTime);
                }
                obr22.setValue(DateUtils.parseDate(olisProviderPreferences.getStartTime(), dateFormat));
            } else {
                if (defaultEndTime.equals("")) {
                    obr22.setValue(DateUtils.parseDate(defaultStartTime, dateFormat));
                } else {
                    dateList.add(DateUtils.parseDate(defaultStartTime, dateFormat));
                    dateList.add(DateUtils.parseDate(defaultEndTime, dateFormat));
                    obr22.setValue(dateList);
                }

                olisProviderPreferences = new OLISProviderPreferences();
                olisProviderPreferences.setProviderId(provider.getProviderNo());
            }
            providerQuery.setStartEndTimestamp(obr22);

            ZRP1 zrp1 = new ZRP1(provider.getPractitionerNo(), StringUtils.trimToEmpty(officialIdType), "ON", "HL70347", StringUtils.trimToEmpty(officialLastName), StringUtils.trimToEmpty(officialfirstName), StringUtils.trimToEmpty(officialSecondName));
            providerQuery.setRequestingHic(zrp1);

            String response = Driver.submitOLISQuery(loggedInInfo, null, providerQuery);

            if (!response.startsWith("<Response")) {
                logger.error("response does not match, aborting " + response);
                return;
            }
            String timeStampForNextStartDate = OLISPollingUtil.parseAndImportResponse(loggedInInfo, response);
            logger.info("timeSlot " + timeStampForNextStartDate);

            if (timeStampForNextStartDate != null) {
                olisProviderPreferences.setStartTime(timeStampForNextStartDate);
            }

            if (olisProviderPreferences.getId() != null) {
                olisProviderPreferencesDao.merge(olisProviderPreferences);
            } else {
                olisProviderPreferencesDao.persist(olisProviderPreferences);
            }
        } catch (Exception e) {
            logger.error("Error polling OLIS for providers " + provider.getProviderNo(), e);
        }

    }

    private static void pollZ04Query(LoggedInInfo loggedInInfo, String defaultStartTime, String defaultEndTime) {
        //Z04Query providerQuery;
        List<Provider> allProvidersList = providerDao.getActiveProviders();
        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        for (Provider provider : allProvidersList) {
            try {
                String officialLastName = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME);
                String officialfirstName = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME);
                String officialSecondName = userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME);

                //There is no need to query for users without this configured, it will just end in an error.
                if (officialLastName == null || officialLastName.trim().equals("")) {
                    continue;
                }

                Z04Query providerQuery = new Z04Query();
                OLISProviderPreferences olisProviderPreferences = olisProviderPreferencesDao.findById(provider.getProviderNo());

                // Creating OBR22 for this request.
                OBR22 obr22 = new OBR22();
                List<Date> dateList = new LinkedList<Date>();
                if (olisProviderPreferences != null) {
                    if (olisProviderPreferences.getStartTime() == null) {
                        olisProviderPreferences.setStartTime(defaultStartTime);
                    }
                    obr22.setValue(DateUtils.parseDate(olisProviderPreferences.getStartTime(), dateFormat));
                } else {
                    if (defaultEndTime.equals("")) {
                        obr22.setValue(DateUtils.parseDate(defaultStartTime, dateFormat));
                    } else {
                        dateList.add(DateUtils.parseDate(defaultStartTime, dateFormat));
                        dateList.add(DateUtils.parseDate(defaultEndTime, dateFormat));
                        obr22.setValue(dateList);
                    }

                    olisProviderPreferences = new OLISProviderPreferences();
                    olisProviderPreferences.setProviderId(provider.getProviderNo());
                }
                providerQuery.setStartEndTimestamp(obr22);

                // Setting HIC for Z04 Request
                ZRP1 zrp1 = new ZRP1(provider.getPractitionerNo(), userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_OLIS_IDTYPE), "ON", "HL70347", officialLastName, officialfirstName, officialSecondName);
                providerQuery.setRequestingHic(zrp1);
                String response = Driver.submitOLISQuery(loggedInInfo, null, providerQuery);

                if (!response.startsWith("<Response")) {
                    logger.error("response does not match, aborting " + response);
                    continue;
                }
                String timeStampForNextStartDate = OLISPollingUtil.parseAndImportResponse(loggedInInfo, response);
                logger.info("timeSlot " + timeStampForNextStartDate);

                if (timeStampForNextStartDate != null) {
                    olisProviderPreferences.setStartTime(timeStampForNextStartDate);
                }

                if (olisProviderPreferences.getId() != null) {
                    olisProviderPreferencesDao.merge(olisProviderPreferences);
                } else {
                    olisProviderPreferencesDao.persist(olisProviderPreferences);
                }
            } catch (Exception e) {
                logger.error("Error polling OLIS for providers " + provider.getProviderNo(), e);
            }
        }
    }

    private static void pollZ06Query(LoggedInInfo loggedInInfo, String defaultStartTime, String defaultEndTime, String facilityId) {
        try {
            Z06Query facilityQuery = new Z06Query();
            OLISProviderPreferences olisProviderPreferences = olisProviderPreferencesDao.findById("-1");
            // Creating OBR22 for this request.
            OBR22 obr22 = new OBR22();
            List<Date> dateList = new LinkedList<Date>();
            if (olisProviderPreferences != null) {
                if (olisProviderPreferences.getStartTime() == null) {
                    olisProviderPreferences.setStartTime(defaultStartTime);
                }
                obr22.setValue(DateUtils.parseDate(olisProviderPreferences.getStartTime(), dateFormat));
            } else {
                if (defaultEndTime.equals("")) {
                    obr22.setValue(DateUtils.parseDate(defaultStartTime, dateFormat));
                } else {
                    dateList.add(DateUtils.parseDate(defaultStartTime, dateFormat));
                    dateList.add(DateUtils.parseDate(defaultEndTime, dateFormat));
                    obr22.setValue(dateList);
                }

                olisProviderPreferences = new OLISProviderPreferences();
                olisProviderPreferences.setProviderId("-1");
            }
            facilityQuery.setStartEndTimestamp(obr22);
            ORC21 orc21 = new ORC21();
            orc21.setValue(6, 2, "^" + facilityId);
            orc21.setValue(6, 3, "^ISO");
            facilityQuery.setOrderingFacilityId(orc21);

            String response = Driver.submitOLISQuery(loggedInInfo, null, facilityQuery);

            if (!response.startsWith("<Response")) {
                logger.debug("Didn't equal response.  Returning " + response);
                return;
            }

            String timeStampForNextStartDate = OLISPollingUtil.parseAndImportResponse(loggedInInfo, response);

            if (timeStampForNextStartDate != null) {
                olisProviderPreferences.setStartTime(timeStampForNextStartDate);
            }

            if (olisProviderPreferences.getId() != null) {
                olisProviderPreferencesDao.merge(olisProviderPreferences);
            } else {
                olisProviderPreferencesDao.persist(olisProviderPreferences);
            }
        } catch (Exception e) {
            logger.error("Error polling OLIS for facility", e);
        }
    }

    public static String parseAndImportResponse(LoggedInInfo loggedInInfo, String response) throws Exception {
        String timeStampForNextStartDate = null;
        UUID uuid = UUID.randomUUID();
        String originalFile = "olis_" + uuid.toString() + ".response";
        String hl7Filename = "olis_" + uuid.toString() + ".hl7";
        //write full response to disk, this will make diagnosing issues easier
        Utilities.saveFile(new ByteArrayInputStream(response.getBytes("UTF-8")), originalFile);

        //Get HL7 Content from xml
        String responseContent = OLISUtils.getOLISResponseContent(response);

        //Write HL7 file to disk.
        String fileLocation = Utilities.saveFile(new ByteArrayInputStream(responseContent.getBytes("UTF-8")), hl7Filename);
        logger.debug(fileLocation);
        File file = new File(fileLocation);
        MessageHandler msgHandler = HandlerClassFactory.getHandler("OLIS_HL7");
        try {
            InputStream is = new FileInputStream(fileLocation);
            int check = FileUploadCheck.addFile(file.getName(), is, "0");
            if (check != FileUploadCheck.UNSUCCESSFUL_SAVE) {
                timeStampForNextStartDate = msgHandler.parse(loggedInInfo, "OLIS_HL7", fileLocation, check, null);

                if (timeStampForNextStartDate != null) {
                    logger.info("Lab successfully added.");
                } else {
                    logger.info("Error adding lab.");
                }
            } else {
                logger.info("Lab already in system.");
            }
            is.close();

        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't add requested OLIS lab to Inbox.", e);
        }
        return timeStampForNextStartDate;
    }
}
