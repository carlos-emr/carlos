/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2017-2024. Juno EMR. All Rights Reserved.
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
 * Portions contributed by Juno EMR.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.springframework.stereotype.Component;

/**
 * Provider client that preserves the existing middleware fax API contract.
 *
 * <p>This class extracts all middleware transport details from core fax orchestration so the
 * pipeline can remain provider-agnostic.</p>
 *
 * @since 2026-02-11
 */
@Component
public class MiddlewareFaxProviderClient implements FaxProviderClient {

    private static final String PATH = "/fax";
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public FaxConfig.ProviderType getProviderType() {
        return FaxConfig.ProviderType.MIDDLEWARE;
    }

    /**
     * Sends outbound fax through existing middleware /fax/send endpoint.
     */
    @Override
    public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) throws FaxProviderException {
        try {
            WebClient client = WebClient.create(faxConfig.getUrl());
            client.path(PATH + "/send/" + faxConfig.getFaxUser());
            client.type(MediaType.APPLICATION_XML);
            client.accept(MediaType.APPLICATION_XML);

            String login = faxConfig.getSiteUser() + ":" + faxConfig.getPasswd();
            String authorizationHeader = "Basic " + Base64Utility.encode(login.getBytes());
            client.header("Authorization", authorizationHeader);
            client.header("user", faxJob.getUser());
            client.header("passwd", faxConfig.getFaxPasswd());

            HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
            HTTPClientPolicy policy = new HTTPClientPolicy();
            policy.setConnectionTimeout(30000);
            policy.setReceiveTimeout(60000);
            conduit.setClient(policy);

            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                faxJob.setDocument(Base64Utility.encode(Files.readAllBytes(filePath)));
            }

            if (StringUtils.isBlank(faxJob.getDocument())) {
                throw new FaxProviderException("Fatal error locating document. Not found in filesystem or database backup");
            }

            Response httpResponse = client.post(faxJob);
            if (httpResponse.getStatus() != HttpStatus.SC_OK) {
                throw new FaxProviderException("WEB SERVICE RESPONDED WITH " + httpResponse.getStatus());
            }

            FaxJob faxJobId = httpResponse.readEntity(FaxJob.class);
            faxJob.setDocument(null);
            faxJob.setJobId(faxJobId.getJobId());
            faxJob.setStatusString(faxJobId.getStatusString());
            faxJob.setStatus(faxJobId.getStatus());
            return faxJob;
        } catch (FaxProviderException e) {
            // Re-throw FaxProviderException as-is to preserve specific error messages
            throw e;
        } catch (IOException e) {
            throw new FaxProviderException("CANNOT FIND Filepath: " + filePath, e);
        } catch (Exception e) {
            throw new FaxProviderException("PROBLEM COMMUNICATING WITH WEB SERVICE", e);
        }
    }

    /**
     * Lists inbound fax metadata from middleware endpoint.
     */
    @Override
    public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) throws FaxProviderException {
        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpGet get = new HttpGet(faxConfig.getUrl() + PATH + "/" + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8"));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            HttpResponse response = client.execute(get);
            if (response == null || response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new FaxProviderException("HTTP Status error with HTTP code: " +
                        (response == null ? "null" : response.getStatusLine().getStatusCode()));
            }

            HttpEntity httpEntity = response.getEntity();
            String content = EntityUtils.toString(httpEntity);
            return mapper.readValue(content, new TypeReference<List<FaxJob>>() { });
        } catch (IOException e) {
            throw new FaxProviderException("HTTP WS CLIENT ERROR", e);
        }
    }

    /**
     * Downloads an inbound fax from middleware endpoint.
     */
    @Override
    public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpGet get = new HttpGet(faxConfig.getUrl() + PATH + "/"
                    + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8") + "/"
                    + URLEncoder.encode(fax.getFile_name(), "UTF-8"));
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            HttpResponse response = client.execute(get);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new FaxProviderException("Error downloading fax file " + fax.getFile_name());
            }

            String content = EntityUtils.toString(response.getEntity());
            FaxJob downloaded = mapper.readValue(content, FaxJob.class);
            fax.setStatus(downloaded.getStatus());
            fax.setStatusString(downloaded.getStatusString());
            if (FaxJob.STATUS.ERROR.equals(downloaded.getStatus())) {
                throw new FaxProviderException("Downloaded fax is in ERROR status");
            }
            return downloaded;
        } catch (IOException e) {
            throw new FaxProviderException("IO ERROR", e);
        }
    }

    /**
     * Deletes an inbound fax on middleware after local import succeeds.
     */
    @Override
    public void deleteFax(FaxConfig faxConfig, FaxJob fax) throws FaxProviderException {
        try (CloseableHttpClient client = createDownloadClient(faxConfig)) {
            HttpDelete delete = new HttpDelete(faxConfig.getUrl() + PATH + "/"
                    + URLEncoder.encode(faxConfig.getFaxUser(), "UTF-8") + "/"
                    + URLEncoder.encode(fax.getFile_name(), "UTF-8"));
            delete.setHeader("accept", "application/json");
            delete.setHeader("user", faxConfig.getFaxUser());
            delete.setHeader("passwd", faxConfig.getFaxPasswd());

            HttpResponse response = client.execute(delete);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                throw new FaxProviderException("CANNOT DELETE " + fax.getFile_name());
            }
        } catch (IOException e) {
            throw new FaxProviderException("CANNOT DELETE " + fax.getFile_name(), e);
        }
    }

    /**
     * Retrieves outbound fax delivery status from middleware.
     */
    @Override
    public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) throws FaxProviderException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            Credentials credentials = new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd());
            client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);

            HttpGet get = new HttpGet(faxConfig.getUrl() + "/" + faxJob.getJobId());
            get.setHeader("accept", "application/json");
            get.setHeader("user", faxConfig.getFaxUser());
            get.setHeader("passwd", faxConfig.getFaxPasswd());

            HttpResponse response = client.execute(get);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new FaxProviderException("WEB SERVICE RESPONDED WITH " + response.getStatusLine().getStatusCode());
            }

            String content = EntityUtils.toString(response.getEntity());
            return mapper.readValue(content, FaxJob.class);
        } catch (IOException e) {
            throw new FaxProviderException("HTTP WS CLIENT ERROR", e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // ignore close exception
            }
        }
    }

    /**
     * Creates authenticated HTTP client for middleware pull endpoints.
     */
    private CloseableHttpClient createDownloadClient(FaxConfig faxConfig) {
        Credentials credentials = new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd());
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), credentials);
        return HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider).build();
    }
}
