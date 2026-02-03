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


package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.Communication;

import java.io.IOException;
import java.io.InputStream;


import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpEntity;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/*
 * @author Jesse Bank
 * For The Oscar McMaster Project
 * Developed By Andromedia
 * www.andromedia.ca
 */
public class HTTP {
    private static Logger logger = MiscUtils.getLogger();

    private String url;

    public HTTP(String url) {
        this.url = url;
    }

    /**
     * Executes HTTP GET request and returns response as InputStream.
     * 
     * @param queryString the query string to append to the URL
     * @return InputStream containing response body, or null if no content. 
     *         <b>Caller must close this stream.</b>
     * @throws IOException if the HTTP request fails
     */
    public InputStream Get(String queryString) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.setCustomQuery(queryString);
            HttpGet get = new HttpGet(uriBuilder.build());

            CloseableHttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();

            logger.debug("Status code: " + response.getStatusLine().getStatusCode());

            if (entity != null) {
                return entity.getContent(); // caller must close this InputStream
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new IOException("HTTP GET failed", e);
        }
    }

    public String GetString(String queryString) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.setCustomQuery(queryString);
            HttpGet get = new HttpGet(uriBuilder.build());

            try (CloseableHttpResponse response = client.execute(get)) {
                logger.error("Status code: " + response.getStatusLine().getStatusCode());
                return EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        } catch (Exception e) {
            throw new IOException("HTTP GET failed", e);
        }
    }
}
