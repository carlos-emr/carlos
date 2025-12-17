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
package ca.openosp.openo.fax.core;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.FaxConfigDao;
import ca.openosp.openo.commn.dao.FaxJobDao;
import ca.openosp.openo.commn.model.FaxConfig;
import ca.openosp.openo.commn.model.FaxJob;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

public class FaxStatusUpdater {

    private FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
    private FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
    private Logger log = MiscUtils.getLogger();

    public void updateStatus() {

        List<FaxJob> faxJobList = faxJobDao.getInprogressFaxesByJobId();
        FaxConfig faxConfig;
        DefaultHttpClient client = new DefaultHttpClient();
        FaxJob faxJobUpdated;

        log.info("CHECKING STATUS OF " + faxJobList.size() + " FAXES");

        for (FaxJob faxJob : faxJobList) {
            faxConfig = faxConfigDao.getConfigByNumber(faxJob.getFax_line());

            if (faxConfig == null) {
                //log.error("Could not find faxConfig while processing fax id: " + faxJob.getId() + " Has the fax number changed?");
            } else if (faxConfig.isActive()) {

                Credentials credentials = new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd());
                client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

                HttpGet mGet = new HttpGet(faxConfig.getUrl() + "/" + faxJob.getJobId());
                mGet.setHeader("accept", "application/json");
                mGet.setHeader("user", faxConfig.getFaxUser());
                mGet.setHeader("passwd", faxConfig.getFaxPasswd());

                try {
                    HttpResponse response = client.execute(mGet);
                    log.info("RESPONSE: " + response.getStatusLine().getStatusCode());

                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                        HttpEntity httpEntity = response.getEntity();
                        String content = EntityUtils.toString(httpEntity);

                        ObjectMapper mapper = new ObjectMapper();
                        faxJobUpdated = mapper.readValue(content, FaxJob.class);

                        faxJob.setStatus(faxJobUpdated.getStatus());
                        faxJob.setStatusString(faxJobUpdated.getStatusString());

                        log.info("UPDATED FAX JOB ID " + faxJob.getJobId() + " WITH STATUS " + faxJob.getStatus());
                        faxJobDao.merge(faxJob);

                    } else {
                        log.error("WEB SERVICE RESPONDED WITH " + response.getStatusLine().getStatusCode(), new IOException());
                    }

                } catch (ClientProtocolException e) {
                    log.error("HTTP WS CLIENT ERROR", e);

                } catch (IOException e) {
                    log.error("IO ERROR", e);
                } finally {
                    if (mGet != null) {
                        mGet.reset();
                    }
                }

            }

        }
    }

}
