//CHECKSTYLE:OFF
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
package io.github.carlos_emr.carlos.fax.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.http.HttpStatus;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;

public class FaxSender {

    private static String PATH = "/fax";
    private final FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private final FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private final FaxClientLogDao faxClientLogDao = SpringUtils.getBean(FaxClientLogDao.class);

    private Logger log = MiscUtils.getLogger();

    public void send() {

        List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);
        List<FaxJob> faxJobList;

        WebClient client;

        String document_dir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        for (FaxConfig faxConfig : faxConfigList) {
            if (faxConfig.isActive()) {

                client = WebClient.create(faxConfig.getUrl());
                client.path(PATH + "/send/" + faxConfig.getFaxUser());
                client.type(MediaType.APPLICATION_XML);
                client.accept(MediaType.APPLICATION_XML);

                String login = faxConfig.getSiteUser() + ":" + faxConfig.getPasswd();
                String authorizationHeader = "Basic " + Base64Utility.encode(login.getBytes());
                client.header("Authorization", authorizationHeader);

                /*
                 * Setting explicit timeout values to ensure that the WebClient does not wait indefinitely for responses.
                 * Connection timeout set to 30 seconds, which is the same as the default timeout.
                 * Receive timeout set to 60 seconds, which is also the same as the default timeout.
                 */
                HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
                HTTPClientPolicy policy = new HTTPClientPolicy();
                policy.setConnectionTimeout(30000); // 30 seconds
                policy.setReceiveTimeout(60000); // 60 seconds
                conduit.setClient(policy);

                faxJobList = faxJobDao.getReadyToSendFaxes(faxConfig.getFaxNumber());
                FaxJob faxJobId;

                log.info("SENDING " + faxJobList.size() + " faxes from fax account " + faxConfig.getSiteUser());

                String filename;
                Path filePath;

                for (FaxJob faxJob : faxJobList) {

                    FaxClientLog faxClientLog = faxClientLogDao.findClientLogbyFaxId(faxJob.getId());
                    STATUS faxStatus = STATUS.ERROR;

                    client.header("user", faxJob.getUser());
                    client.header("passwd", faxConfig.getFaxPasswd());

                    faxJob.setSenderEmail(faxConfig.getSenderEmail());
                    filename = faxJob.getFile_name();
                    filePath = Paths.get(filename);

                    /*
                     * the filename variable may be an absolute path to a temp directory
                     * at this point. Do a check to verify
                     */
                    if (!Files.exists(filePath)) {

                        /*
                         * The filename variable must point to a file name, not a file path
                         * Remove any file separators that may have slipped into the filename.
                         */
                        if (filename.contains(File.separator)) {
                            filename.replaceAll(File.separator, "");
                        }

                        /*
                         * the file may be located in the default documents directory if the filename
                         * is not a path to a temp directory
                         */
                        filePath = Paths.get(document_dir, filename);
                    }

                    log.info("sending fax from file path " + filePath);

                    try {

                        /*
                         * If the filepath still does not exist at this point; it is possible that
                         * the file was removed from the temp directory or document directory
                         * before a second or 3rd attempt to send this document out.
                         * A backup copy of the document should still exist in the database table
                         * This condition avoids overwriting
                         */
                        if (Files.exists(filePath) && Files.isReadable(filePath)) {
                            String base64 = Base64Utility.encode(Files.readAllBytes(filePath));

                            /*
                             * The database will hol\d a temp backup copy of the document
                             * until a successful send is done.
                             */
                            faxJob.setDocument(base64);
                        }

                        /*
                         * It's very bad if the document does not exist at this point.
                         */
                        if (faxJob.getDocument() == null) {
                            log.error("Fatal error locating document. Not found in any directory or database.");
                            throw new IOException();
                        }

                        Response httpResponse = client.post(faxJob);

                        if (httpResponse.getStatus() == HttpStatus.SC_OK) {
                            faxJobId = httpResponse.readEntity(FaxJob.class);
                            faxJob.setDocument(null);
                            faxJob.setJobId(faxJobId.getJobId());
                            faxJob.setStatusString(faxJobId.getStatusString());
                            faxStatus = faxJobId.getStatus();
                        } else {
                            faxJob.setStatusString("WEB SERVICE RESPONDED WITH " + httpResponse.getStatus());
                            log.error("WEB SERVICE RESPONDED WITH " + httpResponse.getStatus(), new IOException());
                        }

                    } catch (HttpHostConnectException e) {
                        faxStatus = FaxJob.STATUS.WAITING;
                        faxJob.setStatusString("Connection error. Check internet connection. Filepath: " + filePath);
                        log.error("Connection error. Check internet connection Filepath: " + filePath);
                    } catch (IOException e) {
                        faxJob.setStatusString("CANNOT FIND Filepath: " + filePath);
                        log.error("CANNOT FIND Filepath: " + filePath);
                    } catch (Exception e) {
                        faxJob.setStatusString("PROBLEM COMMUNICATING WITH WEB SERVICE");
                        log.error("PROBLEM COMMUNICATING WITH WEB SERVICE", e);
                    } finally {
                        faxJob.setStatus(faxStatus);
                        faxJobDao.merge(faxJob);
                        log.info("Updated Fax with jobid " + faxJob.getJobId() + " and status " + faxJob.getStatus());
                        if (faxClientLog != null) {
                            faxClientLog.setResult(faxStatus.name());
                            faxClientLog.setEndTime(new Date(System.currentTimeMillis()));
                            faxClientLogDao.merge(faxClientLog);
                        }
                    }
                }

            }
        }
    }

}
