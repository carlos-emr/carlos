//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.PMmodule.caisi_integrator;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpSession;

import ca.openosp.openo.caisi_integrator.ws.transfer.FacilityConsentPair;
import ca.openosp.openo.caisi_integrator.ws.transfer.SetConsentTransfer;
import ca.openosp.openo.utility.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicNote;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicNoteCompositePk;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicPrevention;
import ca.openosp.openo.caisi_integrator.ws.CachedFacility;
import ca.openosp.openo.caisi_integrator.ws.CachedMeasurement;
import ca.openosp.openo.caisi_integrator.ws.CachedProgram;
import ca.openosp.openo.caisi_integrator.ws.CachedProvider;
import ca.openosp.openo.caisi_integrator.ws.ConnectException_Exception;
import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.caisi_integrator.ws.DemographicWsService;
import ca.openosp.openo.caisi_integrator.ws.DuplicateHinExceptionException;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdIntegerCompositePk;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdStringCompositePk;
import ca.openosp.openo.caisi_integrator.ws.FacilityWs;
import ca.openosp.openo.caisi_integrator.ws.FacilityWsService;
import ca.openosp.openo.caisi_integrator.ws.GetConsentTransfer;
import ca.openosp.openo.caisi_integrator.ws.HnrWs;
import ca.openosp.openo.caisi_integrator.ws.HnrWsService;
import ca.openosp.openo.caisi_integrator.ws.InvalidHinExceptionException;
import ca.openosp.openo.caisi_integrator.ws.MatchingDemographicParameters;
import ca.openosp.openo.caisi_integrator.ws.ProgramWs;
import ca.openosp.openo.caisi_integrator.ws.ProgramWsService;

import ca.openosp.openo.caisi_integrator.ws.ProviderWs;
import ca.openosp.openo.caisi_integrator.ws.ProviderWsService;
import ca.openosp.openo.caisi_integrator.ws.ReferralWs;
import ca.openosp.openo.caisi_integrator.ws.ReferralWsService;
import ca.openosp.openo.caisi_integrator.ws.ProviderCommunicationTransfer;
import ca.openosp.openo.commn.model.Consent;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.commn.model.IntegratorConsent;
import ca.openosp.openo.commn.model.IntegratorConsent.ConsentStatus;
import ca.openosp.openo.commn.model.IntegratorConsent.SignatureStatus;
import ca.openosp.openo.commn.model.OscarMsgType;
import ca.openosp.openo.ws.MatchingClientParameters;
import ca.openosp.openo.ws.MatchingClientScore;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest.SEARCHMODE;


/**
 * Central manager for all CAISI integrator web service interactions and data caching.
 * <p>
 * This class provides the primary interface for accessing the CAISI integrator system, which
 * enables data sharing and integration across multiple healthcare facilities. It manages:
 * <ul>
 *   <li>Web service client creation and authentication</li>
 *   <li>Facility discovery and metadata retrieval</li>
 *   <li>Demographic data exchange and linking</li>
 *   <li>Provider and program information synchronization</li>
 *   <li>Clinical data access (notes, preventions, measurements, etc.)</li>
 *   <li>Consent management for data sharing</li>
 *   <li>Health Network Registry (HNR) interactions</li>
 *   <li>Integrated referrals between facilities</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Caching Strategy:</strong>
 * The class implements a two-tier caching strategy:
 * <ol>
 *   <li><strong>Basic Data Cache:</strong> For non-audited, facility-level data (facilities, 
 *       programs, providers) that doesn't contain PHI. Cache duration: 1 hour.</li>
 *   <li><strong>Segmented Data Cache:</strong> For provider-specific data access (linked notes, 
 *       preventions, measurements). Keyed by facility, provider, and demographic. Also 1 hour.</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Privacy and Audit Compliance:</strong><br/>
 * As noted in the original documentation, all privacy-related data access should be logged 
 * (read and write). This class delegates audit logging responsibility to the integrator service 
 * providers when data is not cached locally. Client privacy (demographic PHI) is strictly 
 * controlled; provider information has reduced privacy expectations.
 * </p>
 * <p>
 * <strong>Connection Management:</strong><br/>
 * The class handles integrator connectivity issues by:
 * <ul>
 *   <li>Setting offline status in the session when connection exceptions occur</li>
 *   <li>Configuring CXF client proxies with authentication interceptors</li>
 *   <li>Building web service endpoints dynamically from facility configuration</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Key Web Service Types:</strong>
 * <ul>
 *   <li>{@link FacilityWs} - Facility metadata and synchronization status</li>
 *   <li>{@link DemographicWs} - Patient demographic data and linking</li>
 *   <li>{@link ProgramWs} - Program information and referral capabilities</li>
 *   <li>{@link ProviderWs} - Provider directory and communications</li>
 *   <li>{@link ReferralWs} - Integrated referrals between facilities</li>
 *   <li>{@link HnrWs} - Health Network Registry for patient matching</li>
 * </ul>
 * </p>
 * 
 * @see IntegratorFallBackManager
 * @see CaisiIntegratorUpdateTask
 * @see AuthenticationOutWSS4JInterceptorForIntegrator
 */
public class CaisiIntegratorManager {

    /**
     * Cache for non-audited, basic data (facilities, programs, providers).
     * <p>
     * This cache stores data that is not privacy-sensitive and doesn't require
     * per-access audit logging. Capacity: 100 items, TTL: 1 hour.
     * </p>
     */
    private static QueueCache<String, Object> basicDataCache = new QueueCache<String, Object>(4, 100, org.apache.commons.lang3.time.DateUtils.MILLIS_PER_HOUR, null);

    /**
     * Cache for provider-segmented data access (notes, preventions, measurements).
     * <p>
     * Data in this cache MUST be segmented by the requesting provider as part of the cache key
     * to ensure proper access control. This prevents one provider from accessing another
     * provider's cached view of patient data. Capacity: 100 items, TTL: 1 hour.
     * </p>
     */
    private static QueueCache<String, Object> segmentedDataCache = new QueueCache<String, Object>(4, 100, org.apache.commons.lang3.time.DateUtils.MILLIS_PER_HOUR, null);

    /**
     * Sets the integrator offline status in the HTTP session.
     * <p>
     * When set to true, this flag indicates that the integrator service is unreachable.
     * UI components can check this flag to disable integrator-dependent features and
     * display appropriate user messaging.
     * </p>
     * 
     * @param session the HTTP session to store the status in
     * @param status true to mark as offline, false to clear offline status
     */
    public static void setIntegratorOffline(HttpSession session, boolean status) {
        if (status) {
            session.setAttribute(SessionConstants.INTEGRATOR_OFFLINE, true);
        } else {
            session.removeAttribute(SessionConstants.INTEGRATOR_OFFLINE);
        }
    }

    /**
     * Checks if an exception indicates a connection failure and sets offline status if so.
     * <p>
     * This method examines the root cause of an exception chain to detect network-level
     * failures (ConnectException, SocketTimeoutException). When detected, it automatically
     * sets the integrator offline status in the session.
     * </p>
     * 
     * @param session the HTTP session to update
     * @param exception the exception to analyze
     */
    public static void checkForConnectionError(HttpSession session, Throwable exception) {
        // Drill down to the root cause
        Throwable rootException = ExceptionUtils.getRootCause(exception);
        MiscUtils.getLogger().debug("Exception: " + exception.getClass().getName() + " --- " + rootException.getClass().getName());

        // Check if it's a connectivity issue
        if (rootException instanceof java.net.ConnectException || rootException instanceof java.net.SocketTimeoutException) {
            setIntegratorOffline(session, true);
        }
    }

    /**
     * Checks if the integrator is currently marked as offline in the session.
     * 
     * @param session the HTTP session to check
     * @return true if integrator is offline, false otherwise
     */
    public static boolean isIntegratorOffline(HttpSession session) {
        Object object = session.getAttribute(SessionConstants.INTEGRATOR_OFFLINE);
        if (object != null) {
            return true;
        }
        return false;
    }

    /**
     * Determines if integrated referrals are enabled for a facility.
     * <p>
     * Requires both the integrator to be enabled AND the specific integrated referrals
     * feature to be turned on for the facility.
     * </p>
     * 
     * @param facility the facility to check
     * @return true if integrated referrals should be available
     */
    public static boolean isEnableIntegratedReferrals(Facility facility) {
        return (facility.isIntegratorEnabled() && facility.isEnableIntegratedReferrals());
    }

    /**
     * Adds WS-Security authentication interceptor to a web service client.
     * <p>
     * This configures the CXF client to include:
     * <ul>
     *   <li>WS-Security username/password token for facility authentication</li>
     *   <li>Custom SOAP header with the requesting provider number for audit trails</li>
     * </ul>
     * </p>
     * 
     * @param loggedInInfo the current user session (for provider number)
     * @param facility the facility configuration (for credentials and URL)
     * @param wsPort the web service port proxy to configure
     */
    private static void addAuthenticationInterceptor(LoggedInInfo loggedInInfo, Facility facility, Object wsPort) {
        // Get the underlying CXF client from the JAX-WS proxy
        Client cxfClient = ClientProxy.getClient(wsPort);
        String providerNo = null;
        if (loggedInInfo != null) providerNo = loggedInInfo.getLoggedInProviderNo();
        // Add the authentication interceptor to the outbound chain
        cxfClient.getOutInterceptors().add(new AuthenticationOutWSS4JInterceptorForIntegrator(facility.getIntegratorUser(), facility.getIntegratorPassword(), providerNo));
    }

    /**
     * Builds a web service endpoint URL from facility configuration.
     * 
     * @param facility the facility configuration
     * @param servicePoint the service name (e.g., "FacilityService", "DemographicService")
     * @return the complete WSDL URL
     * @throws MalformedURLException if the URL cannot be constructed
     */
    private static URL buildURL(Facility facility, String servicePoint) throws MalformedURLException {
        return (new URL(facility.getIntegratorUrl() + '/' + servicePoint + "?wsdl"));
    }

    /*
     * Web Service Client Factory Methods
     * ===================================
     * 
     * The following methods follow a consistent pattern for creating configured web service clients:
     * 
     * 1. get<Service>Ws(loggedInInfo, facility):
     *    - Creates a service instance from the WSDL URL
     *    - Retrieves the port (JAX-WS proxy)
     *    - Configures CXF client connection settings (timeouts, etc.)
     *    - Adds authentication interceptor for WS-Security
     *    - Returns the configured port ready for use
     * 
     * Services include:
     * - FacilityWs: Facility metadata and synchronization
     * - DemographicWs: Patient demographics and linking
     * - ProgramWs: Program information and referrals
     * - ProviderWs: Provider directory and communications
     * - ReferralWs: Integrated referrals
     * - HnrWs: Health Network Registry for patient matching
     * 
     * All web service calls should use these factory methods to ensure proper
     * authentication and configuration.
     */

    /**
     * Creates and configures a Facility web service client.
     * <p>
     * Used for facility discovery, synchronization status checks, and facility metadata retrieval.
     * </p>
     * 
     * @param loggedInInfo the current user session
     * @param facility the facility configuration
     * @return configured facility web service port
     * @throws MalformedURLException if the service URL is invalid
     */
    public static FacilityWs getFacilityWs(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        FacilityWsService service = new FacilityWsService(buildURL(facility, "FacilityService"));
        FacilityWs port = service.getFacilityWsPort();

        CxfClientUtilsOld.configureClientConnection(port);
        addAuthenticationInterceptor(loggedInInfo, facility, port);

        return (port);
    }

    public static boolean haveAllRemoteFacilitiesSyncedIn(LoggedInInfo loggedInInfo, Facility facility, int seconds) throws MalformedURLException {
        return haveAllRemoteFacilitiesSyncedIn(loggedInInfo, facility, seconds, true);
    }

    public static boolean haveAllRemoteFacilitiesSyncedIn(LoggedInInfo loggedInInfo, Facility facility, int seconds, boolean useCachedData) throws MalformedURLException {
        boolean synced = true;
        Calendar timeConsideredStale = Calendar.getInstance();

        timeConsideredStale.add(Calendar.SECOND, -seconds);

        List<CachedFacility> remoteFacilities = getRemoteFacilities(loggedInInfo, facility, useCachedData);
        for (CachedFacility remoteFacility : remoteFacilities) {
            Calendar lastDataUpdate = remoteFacility.getLastDataUpdate();
            if (lastDataUpdate == null || timeConsideredStale.after(lastDataUpdate)) {
                return false;
            }
        }
        return synced;
    }

    public static List<CachedFacility> getRemoteFacilities(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        return getRemoteFacilities(loggedInInfo, facility, true);
    }

    public static List<CachedFacility> getRemoteFacilitiesExcludingCurrent(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        return getRemoteFacilitiesExcludingCurrent(loggedInInfo, facility, true);
    }

    public static List<CachedFacility> getRemoteFacilities(LoggedInInfo loggedInInfo, Facility facility, boolean useCachedData) throws MalformedURLException {

        @SuppressWarnings("unchecked")
        List<CachedFacility> results = (List<CachedFacility>) basicDataCache.get("ALL_FACILITIES");

        if (!useCachedData || results == null) {
            FacilityWs facilityWs = getFacilityWs(loggedInInfo, facility);
            results = Collections.unmodifiableList(facilityWs.getAllFacility());
            basicDataCache.put("ALL_FACILITIES", results);
        }

        return (results);
    }

    public static List<CachedFacility> getRemoteFacilitiesExcludingCurrent(LoggedInInfo loggedInInfo, Facility facility, boolean useCachedData) throws MalformedURLException {

        CachedFacility currentFacility = getCurrentRemoteFacility(loggedInInfo, facility);
        List<CachedFacility> remoteFacilities = getRemoteFacilities(loggedInInfo, facility, useCachedData);
        List<CachedFacility> results = new ArrayList<CachedFacility>();
        for (CachedFacility cachedFacility : remoteFacilities) {
            if (cachedFacility.getIntegratorFacilityId() != currentFacility.getIntegratorFacilityId()) {
                results.add(cachedFacility);
            }
        }
        return Collections.unmodifiableList(results);
    }


    public static CachedFacility getRemoteFacility(LoggedInInfo loggedInInfo, Facility currentFacility, int remoteFacilityId) throws MalformedURLException {
        for (CachedFacility facility : getRemoteFacilities(loggedInInfo, currentFacility)) {
            if (facility.getIntegratorFacilityId().equals(remoteFacilityId)) return (facility);
        }

        return (null);
    }

    public static DemographicWs getDemographicWs(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        DemographicWsService service = new DemographicWsService(buildURL(facility, "DemographicService"));
        DemographicWs port = service.getDemographicWsPort();

        CxfClientUtilsOld.configureClientConnection(port);
        addAuthenticationInterceptor(loggedInInfo, facility, port);

        return (port);
    }

    public static ProgramWs getProgramWs(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        ProgramWsService service = new ProgramWsService(buildURL(facility, "ProgramService"));
        ProgramWs port = service.getProgramWsPort();

        CxfClientUtilsOld.configureClientConnection(port);
        addAuthenticationInterceptor(loggedInInfo, facility, port);

        return (port);
    }

    public static List<CachedProgram> getAllPrograms(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        @SuppressWarnings("unchecked")
        List<CachedProgram> allPrograms = (List<CachedProgram>) basicDataCache.get("ALL_PROGRAMS");

        if (allPrograms == null) {
            allPrograms = Collections.unmodifiableList(getProgramWs(loggedInInfo, facility).getAllPrograms());
            basicDataCache.put("ALL_PROGRAMS", allPrograms);
        }

        return (allPrograms);
    }

    /**
     * @param type should not be null
     * @return a list of cached programs matching the program type
     */
    public static ArrayList<CachedProgram> getRemotePrograms(LoggedInInfo loggedInInfo, Facility facility, String type) throws MalformedURLException {
        ArrayList<CachedProgram> results = new ArrayList<CachedProgram>();

        for (CachedProgram cachedProgram : getAllPrograms(loggedInInfo, facility)) {
            if (type.equals(cachedProgram.getType())) results.add(cachedProgram);
        }

        return (results);
    }

    public static CachedProgram getRemoteProgram(LoggedInInfo loggedInInfo, Facility facility, FacilityIdIntegerCompositePk remoteProgramPk) throws MalformedURLException {
        List<CachedProgram> programs = getAllPrograms(loggedInInfo, facility);

        for (CachedProgram cachedProgram : programs) {
            if (facilityIdIntegerPkEquals(cachedProgram.getFacilityIdIntegerCompositePk(), remoteProgramPk)) {
                return (cachedProgram);
            }
        }

        return (null);
    }

    private static boolean facilityIdIntegerPkEquals(FacilityIdIntegerCompositePk o1, FacilityIdIntegerCompositePk o2) {
        try {
            return (o1.getIntegratorFacilityId().equals(o2.getIntegratorFacilityId()) && o1.getCaisiItemId().equals(o2.getCaisiItemId()));
        } catch (RuntimeException e) {
            return (false);
        }
    }

    public static List<CachedProgram> getRemoteProgramsAcceptingReferrals(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        List<CachedProgram> filteredResults = new ArrayList<CachedProgram>();

        ProgramWs programWs = getProgramWs(loggedInInfo, facility);
        List<CachedProgram> results = programWs.getAllProgramsAllowingIntegratedReferrals();
        for (CachedProgram result : results) {
            if (!result.getType().equals("community")) {
                filteredResults.add(result);
            }
        }

        return (results);
    }

    public static ProviderWs getProviderWs(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {

        try {
            ProviderWsService service = new ProviderWsService(buildURL(facility, "ProviderService"));
            ProviderWs port = service.getProviderWsPort();

            CxfClientUtilsOld.configureClientConnection(port);
            addAuthenticationInterceptor(loggedInInfo, facility, port);

            return port;
        } catch (MalformedURLException e) {
            throw e;
        } catch (Exception e) {
            // do nothing.
            MiscUtils.getLogger().error("Error connecting to Provider Webservice ", e);
            return null;
        }
        /*
         * There should be a global method to handle these uncaught connectivity exceptions
         * more gracefully
         */
    }

    public static List<CachedProvider> getAllProviders(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {

        @SuppressWarnings("unchecked")
        List<CachedProvider> results = (List<CachedProvider>) basicDataCache.get("ALL_PROVIDERS");


        if (results == null) {
            ProviderWs providerWs = getProviderWs(loggedInInfo, facility);
            if (providerWs != null) {
                results = providerWs.getAllProviders();
            }
        }

        if (results == null) {
            results = Collections.emptyList();
        }

        results = Collections.unmodifiableList(results);

        if (!results.isEmpty()) {
            basicDataCache.put("ALL_PROVIDERS", results);
        }

        return results;
    }

    public static CachedProvider getProvider(LoggedInInfo loggedInInfo, Facility facility, FacilityIdStringCompositePk remoteProviderPk) throws MalformedURLException {
        List<CachedProvider> providers = getAllProviders(loggedInInfo, facility);

        for (CachedProvider cachedProvider : providers) {
            if (facilityProviderPrimaryKeyEquals(cachedProvider.getFacilityIdStringCompositePk(), remoteProviderPk)) {
                return (cachedProvider);
            }
        }

        return (null);
    }

    private static boolean facilityProviderPrimaryKeyEquals(FacilityIdStringCompositePk o1, FacilityIdStringCompositePk o2) {
        try {
            return (o1.getIntegratorFacilityId().equals(o2.getIntegratorFacilityId()) && o1.getCaisiItemId().equals(o2.getCaisiItemId()));
        } catch (RuntimeException e) {
            return (false);
        }
    }

    public static ReferralWs getReferralWs(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        ReferralWsService service = new ReferralWsService(buildURL(facility, "ReferralService"));
        ReferralWs port = service.getReferralWsPort();

        CxfClientUtilsOld.configureClientConnection(port);
        addAuthenticationInterceptor(loggedInInfo, facility, port);

        return (port);
    }

    public static HnrWs getHnrWs(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        HnrWsService service = new HnrWsService(buildURL(facility, "HnrService"));
        HnrWs port = service.getHnrWsPort();

        CxfClientUtilsOld.configureClientConnection(port);
        addAuthenticationInterceptor(loggedInInfo, facility, port);

        return (port);
    }

    public static List<MatchingClientScore> searchHnrForMatchingClients(LoggedInInfo loggedInInfo, Facility facility, MatchingClientParameters matchingClientParameters) throws MalformedURLException, ConnectException_Exception {
        HnrWs hnrWs = getHnrWs(loggedInInfo, facility);
        List<MatchingClientScore> potentialMatches = hnrWs.getMatchingHnrClients(matchingClientParameters);
        return (potentialMatches);
    }

    public static ca.openosp.openo.ws.Client getHnrClient(LoggedInInfo loggedInInfo, Facility facility, Integer linkingId) throws MalformedURLException, ConnectException_Exception {
        HnrWs hnrWs = getHnrWs(loggedInInfo, facility);
        ca.openosp.openo.ws.Client client = hnrWs.getHnrClient(linkingId);
        return (client);
    }

    public static Integer setHnrClient(LoggedInInfo loggedInInfo, Facility facility, ca.openosp.openo.ws.Client hnrClient) throws MalformedURLException, DuplicateHinExceptionException, InvalidHinExceptionException, ConnectException_Exception {
        HnrWs hnrWs = getHnrWs(loggedInInfo, facility);
        return (hnrWs.setHnrClientData(hnrClient));
    }

    /**
     * You can not determine which facility "you are" from the facility listing. You must use this method to determine which facility you authenticated as.
     */
    public static CachedFacility getCurrentRemoteFacility(LoggedInInfo loggedInInfo, Facility facility) throws MalformedURLException {
        FacilityWs facilityWs = getFacilityWs(loggedInInfo, facility);
        CachedFacility cachedFacility = facilityWs.getMyFacility();
        return (cachedFacility);
    }

    public static GetConsentTransfer getConsentState(LoggedInInfo loggedInInfo, Facility facility, Integer localDemographicId) throws MalformedURLException {
        CachedFacility localIntegratorFacility = getCurrentRemoteFacility(loggedInInfo, facility);

        return (getConsentState(loggedInInfo, facility, localIntegratorFacility.getIntegratorFacilityId(), localDemographicId));
    }

    public static GetConsentTransfer getConsentState(LoggedInInfo loggedInInfo, Facility facility, Integer integratorFacilityId, Integer caisiDemographicId) throws MalformedURLException {
        FacilityIdIntegerCompositePk pk = new FacilityIdIntegerCompositePk();
        pk.setIntegratorFacilityId(integratorFacilityId);
        pk.setCaisiItemId(caisiDemographicId);

        DemographicWs demographicWs = getDemographicWs(loggedInInfo, facility);
        GetConsentTransfer getConsentTransfer = demographicWs.getConsentState(pk);
        return (getConsentTransfer);
    }

    public static void pushConsent(LoggedInInfo loggedInInfo, Facility facility, Consent consent) throws MalformedURLException {
        IntegratorConsent integratorConsent = makeIntegratorConsent(consent);
        pushConsent(loggedInInfo, facility, integratorConsent);
    }

    public static void pushConsent(LoggedInInfo loggedInInfo, Facility facility, IntegratorConsent integratorConsent) throws MalformedURLException {
        if (integratorConsent.getClientConsentStatus() == ConsentStatus.GIVEN || integratorConsent.getClientConsentStatus() == ConsentStatus.REVOKED) {
            integratorConsent.setFacilityId(facility.getId());
            ca.openosp.openo.caisi_integrator.ws.SetConsentTransfer consentTransfer = makeSetConsentTransfer(integratorConsent);
            getDemographicWs(loggedInInfo, facility).setCachedDemographicConsent(consentTransfer);
        }
    }

    public static IntegratorConsent makeIntegratorConsent(Consent consent) {

        IntegratorConsent integratorConsent = new IntegratorConsent();
        HashMap<Integer, Boolean> shareData = new HashMap<Integer, Boolean>();
        shareData.put(1, Boolean.TRUE);

        ConsentStatus consentStatus = null;
        if (consent.getPatientConsented()) {
            consentStatus = IntegratorConsent.ConsentStatus.GIVEN;
        } else if (consent.isOptout()) {
            consentStatus = IntegratorConsent.ConsentStatus.REVOKED;
            integratorConsent.setExpiry(consent.getOptoutDate());
        }

        SignatureStatus signatureStatus = null;
        if (consent.isExplicit()) {
            signatureStatus = IntegratorConsent.SignatureStatus.PAPER;
        } else {
            signatureStatus = IntegratorConsent.SignatureStatus.ELECTRONIC;
        }

        integratorConsent.setClientConsentStatus(consentStatus);
        integratorConsent.setCreatedDate(consent.getEditDate());
        integratorConsent.setDemographicId(consent.getDemographicNo());
        integratorConsent.setProviderNo(consent.getLastEnteredBy());
        integratorConsent.setSignatureStatus(signatureStatus);
        integratorConsent.setConsentToShareData(shareData);

        return integratorConsent;
    }

    /**
     * Method to be used for transmitting Consents via www services.
     */
    private static ca.openosp.openo.caisi_integrator.ws.SetConsentTransfer makeSetConsentTransfer(IntegratorConsent integratorConsent) {
        Calendar calendar = Calendar.getInstance();
        ca.openosp.openo.caisi_integrator.ws.SetConsentTransfer consentTransfer = new ca.openosp.openo.caisi_integrator.ws.SetConsentTransfer();
        consentTransfer.setConsentStatus(integratorConsent.getClientConsentStatus().name());

        // this should never be null
        calendar.setTime(integratorConsent.getCreatedDate());
        consentTransfer.setCreatedDate(calendar);
        consentTransfer.setDemographicId(integratorConsent.getDemographicId());
        consentTransfer.setExcludeMentalHealthData(integratorConsent.isExcludeMentalHealthData());

        // this will be null if the status is not revoked.
        if (integratorConsent.getClientConsentStatus().equals(IntegratorConsent.ConsentStatus.REVOKED)) {
            calendar.setTime(integratorConsent.getExpiry());
            consentTransfer.setExpiry(calendar);
        }

        for (Entry<Integer, Boolean> entry : integratorConsent.getConsentToShareData().entrySet()) {
            ca.openosp.openo.caisi_integrator.ws.FacilityConsentPair pair = new ca.openosp.openo.caisi_integrator.ws.FacilityConsentPair();
            pair.setRemoteFacilityId(entry.getKey());
            pair.setShareData(entry.getValue());
            consentTransfer.getConsentToShareData().add(pair);
        }

        return consentTransfer;
    }

    /**
     * Method to be used for serializing Consent objects for transmission by file transfer.
     */
    public static SetConsentTransfer makeSetConsentTransfer2(IntegratorConsent consent) {

        SetConsentTransfer consentTransfer = new SetConsentTransfer();
        consentTransfer.setConsentStatus(consent.getClientConsentStatus().name());
        consentTransfer.setCreatedDate(consent.getCreatedDate());
        consentTransfer.setDemographicId(consent.getDemographicId());
        consentTransfer.setExcludeMentalHealthData(consent.isExcludeMentalHealthData());
        consentTransfer.setExpiry(consent.getExpiry());

        List<FacilityConsentPair> pairList = new ArrayList<FacilityConsentPair>();
        for (Entry<Integer, Boolean> entry : consent.getConsentToShareData().entrySet()) {
            FacilityConsentPair pair = new FacilityConsentPair();
            pair.setRemoteFacilityId(entry.getKey());
            pair.setShareData(entry.getValue());
            pairList.add(pair);

        }

        consentTransfer.setConsentToShareData(pairList.toArray(new FacilityConsentPair[pairList.size()]));

        return consentTransfer;
    }

    public static List<CachedDemographicNote> getLinkedNotes(LoggedInInfo loggedInInfo, Integer demographicNo) throws MalformedURLException {
        String sessionCacheKey = "LINKED_NOTES:" + loggedInInfo.getCurrentFacility().getId() + ":" + loggedInInfo.getLoggedInProviderNo() + ":" + demographicNo;

        @SuppressWarnings("unchecked")
        List<CachedDemographicNote> linkedNotes = (List<CachedDemographicNote>) segmentedDataCache.get(sessionCacheKey);

        if (linkedNotes == null) {
            DemographicWs demographicWs = getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            linkedNotes = Collections.unmodifiableList(demographicWs.getLinkedCachedDemographicNotes(demographicNo));
            segmentedDataCache.put(sessionCacheKey, linkedNotes);
        }

        return (linkedNotes);
    }


    public static List<CachedDemographicPrevention> getLinkedPreventions(LoggedInInfo loggedInInfo, Integer demographicNo) throws MalformedURLException {
        String sessionCacheKey = "LINKED_PREVS:" + loggedInInfo.getCurrentFacility().getId() + ":" + loggedInInfo.getLoggedInProviderNo() + ":" + demographicNo;

        @SuppressWarnings("unchecked")
        List<CachedDemographicPrevention> remotePreventions = (List<CachedDemographicPrevention>) segmentedDataCache.get(sessionCacheKey);

        if (remotePreventions == null) {
            DemographicWs demographicWs = getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remotePreventions = Collections.unmodifiableList(demographicWs.getLinkedCachedDemographicPreventionsByDemographicId(demographicNo));
            segmentedDataCache.put(sessionCacheKey, remotePreventions);
        }

        return (remotePreventions);
    }

    public static List<CachedMeasurement> getLinkedMeasurements(LoggedInInfo loggedInInfo, Integer demographicNo) throws MalformedURLException {
        String sessionCacheKey = "LINKED_MEASUREMENTS:" + loggedInInfo.getCurrentFacility().getId() + ":" + loggedInInfo.getLoggedInProviderNo() + ":" + demographicNo;

        @SuppressWarnings("unchecked")
        List<CachedMeasurement> remoteMeasurements = (List<CachedMeasurement>) segmentedDataCache.get(sessionCacheKey);

        if (remoteMeasurements == null) {
            DemographicWs demographicWs = getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            remoteMeasurements = Collections.unmodifiableList(demographicWs.getLinkedCachedDemographicMeasurementByDemographicId(demographicNo));
            segmentedDataCache.put(sessionCacheKey, remoteMeasurements);
        }

        return (remoteMeasurements);
    }


    public static List<CachedDemographicNote> getLinkedNotesMetaData(LoggedInInfo loggedInInfo, Integer demographicNo) throws MalformedURLException {
        String sessionCacheKey = "LINKED_NOTES_META:" + loggedInInfo.getCurrentFacility().getId() + ":" + loggedInInfo.getLoggedInProvider().getPractitionerNo() + ":" + demographicNo;

        @SuppressWarnings("unchecked")
        List<CachedDemographicNote> linkedNotes = (List<CachedDemographicNote>) segmentedDataCache.get(sessionCacheKey);

        if (linkedNotes == null) {
            DemographicWs demographicWs = getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            linkedNotes = Collections.unmodifiableList(demographicWs.getLinkedCachedDemographicNoteMetaData(demographicNo));
            segmentedDataCache.put(sessionCacheKey, linkedNotes);
        }

        return (linkedNotes);
    }

    public static List<CachedDemographicNote> getLinkedNotes(LoggedInInfo loggedInInfo, List<CachedDemographicNoteCompositePk> ids) throws MalformedURLException {
        String sessionCacheKey = "LINKED_NOTES_META:" + loggedInInfo.getCurrentFacility().getId() + ":" + loggedInInfo.getLoggedInProvider().getPractitionerNo() + ":" + ids;

        @SuppressWarnings("unchecked")
        List<CachedDemographicNote> linkedNotes = (List<CachedDemographicNote>) segmentedDataCache.get(sessionCacheKey);

        if (linkedNotes == null) {
            DemographicWs demographicWs = getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            linkedNotes = Collections.unmodifiableList(demographicWs.getLinkedCachedDemographicNotesByIds(ids));
            segmentedDataCache.put(sessionCacheKey, linkedNotes);
        }

        return (linkedNotes);
    }

    /**
     * The purpose of this method is to retrieve a remote demographic record and populate it in a local demographic object. It is ready to be persisted.
     * We do not automatically persist it as there appears to be some code logic where a subsequent approval process may choose not to persist it.
     */
    public static Demographic makeUnpersistedDemographicObjectFromRemoteEntry(LoggedInInfo loggedInInfo, Facility facility, int remoteFacilityId, int remoteDemographicId) throws MalformedURLException {
        DemographicWs demographicWs = getDemographicWs(loggedInInfo, facility);
        DemographicTransfer demographicTransfer = demographicWs.getDemographicByFacilityIdAndDemographicId(remoteFacilityId, remoteDemographicId);

        Demographic demographic = new Demographic();
        demographic.setFirstName(demographicTransfer.getFirstName());
        demographic.setLastName(demographicTransfer.getLastName());
        demographic.setMiddleNames("");

        if (demographicTransfer.getBirthDate() != null) demographic.setBirthDay(demographicTransfer.getBirthDate());
        if (demographicTransfer.getGender() != null) demographic.setSex(demographicTransfer.getGender().name());

        copyDemographicFieldsIfNotNull(demographicTransfer, demographic);

        demographic.setPatientStatus("AC");
        demographic.setDateJoined(new Date());
        demographic.setRosterDate(new Date());

        return (demographic);
    }

    public static void copyDemographicFieldsIfNotNull(DemographicTransfer demographicTransfer, Demographic demographic) {
        if (demographicTransfer.getBirthDate() != null) demographic.setBirthDay(demographicTransfer.getBirthDate());
        if (demographicTransfer.getCity() != null) demographic.setCity(demographicTransfer.getCity());
        if (demographicTransfer.getFirstName() != null) demographic.setFirstName(demographicTransfer.getFirstName());
        if (demographicTransfer.getGender() != null) demographic.setSex(demographicTransfer.getGender().name());
        if (demographicTransfer.getHin() != null) demographic.setHin(demographicTransfer.getHin());
        if (demographicTransfer.getHinType() != null) demographic.setHcType(demographicTransfer.getHinType());
        if (demographicTransfer.getHinVersion() != null) demographic.setVer(demographicTransfer.getHinVersion());
        if (demographicTransfer.getHinValidStart() != null)
            demographic.setEffDate(demographicTransfer.getHinValidStart().getTime());
        if (demographicTransfer.getHinValidEnd() != null)
            demographic.setHcRenewDate(demographicTransfer.getHinValidEnd().getTime());
        if (demographicTransfer.getLastName() != null) demographic.setLastName(demographicTransfer.getLastName());
        if (demographicTransfer.getProvince() != null) demographic.setProvince(demographicTransfer.getProvince());
        if (demographicTransfer.getSin() != null) demographic.setSin(demographicTransfer.getSin());
        if (demographicTransfer.getStreetAddress() != null)
            demographic.setAddress(demographicTransfer.getStreetAddress());
        if (demographicTransfer.getPhone1() != null) demographic.setPhone(demographicTransfer.getPhone1());
        if (demographicTransfer.getPhone2() != null) demographic.setPhone2(demographicTransfer.getPhone2());
    }

    public static MatchingDemographicParameters getMatchingDemographicParameters(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest) {
        MatchingDemographicParameters matchingDemographicParameters = null;


        if (searchRequest.getMode() == SEARCHMODE.HIN) {
            matchingDemographicParameters = new MatchingDemographicParameters();
            matchingDemographicParameters.setHin(searchRequest.getKeyword());
        }
        if (searchRequest.getMode() == SEARCHMODE.DOB) {
            try {
                String year = searchRequest.getKeyword().substring(0, 4);
                String month = searchRequest.getKeyword().substring(5, 7);
                String day = searchRequest.getKeyword().substring(8);

                GregorianCalendar cal = new GregorianCalendar(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(day));
                matchingDemographicParameters = new MatchingDemographicParameters();
                matchingDemographicParameters.setBirthDate(cal);
            } catch (Exception e) {
                matchingDemographicParameters = null;
            }
        }

        if (searchRequest.getMode() == SEARCHMODE.Name) {
            matchingDemographicParameters = new MatchingDemographicParameters();
            String[] lastfirst = searchRequest.getKeyword().split(",");

            if (lastfirst.length > 1) {
                matchingDemographicParameters.setLastName(lastfirst[0].trim());
                matchingDemographicParameters.setFirstName(lastfirst[1].trim());
            } else {
                matchingDemographicParameters.setLastName(lastfirst[0].trim());
            }

        }

        return matchingDemographicParameters;
    }

    public static void linkIntegratedDemographicFiles(LoggedInInfo loggedInInfo, int demographicNo, int remoteFacilityId, int remoteDemographicNo) throws MalformedURLException {
        DemographicWs demographicWs = getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
        demographicWs.linkDemographics(loggedInInfo.getLoggedInProviderNo(), demographicNo, remoteFacilityId, remoteDemographicNo);
    }

    /**
     * Get Oscar Messenger messages from the integrator.
     * Unlike other Integrated objects - Provider communication will be saved into the local facility
     *
     * @param loggedInInfo the logged in user information
     * @return list of provider communication transfers
     * @throws MalformedURLException if URL is malformed
     */
    public static List<ProviderCommunicationTransfer> getProviderCommunication(LoggedInInfo loggedInInfo) throws MalformedURLException {

        ProviderWs providerWs = getProviderWs(loggedInInfo, loggedInInfo.getCurrentFacility());

        // an empty providers number forces a return of all the messages for the facility.
        return providerWs.getProviderCommunications("", OscarMsgType.INTEGRATOR_TYPE + "", true);
    }

    public static void updateProviderCommunicationStatus(LoggedInInfo loggedInInfo, List<Integer> providerCommunicationIdList) throws MalformedURLException {
        for (Integer providerCommunicationId : providerCommunicationIdList) {
            updateProviderCommunicationStatus(loggedInInfo, providerCommunicationId);
        }
    }

    public static void updateProviderCommunicationStatus(LoggedInInfo loggedInInfo, Integer providerCommunicationId) throws MalformedURLException {
        ProviderWs providerWs = getProviderWs(loggedInInfo, loggedInInfo.getCurrentFacility());
        providerWs.deactivateProviderCommunication(providerCommunicationId);
    }
}
