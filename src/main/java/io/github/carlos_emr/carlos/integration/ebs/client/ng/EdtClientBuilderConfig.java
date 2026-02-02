package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.util.UUID;

/**
 * Configuration class for building the EDT client. This class encapsulates various 
 * settings required to authenticate and interact with the MCEDT service, such as 
 * user credentials, keystore information, and service-specific details.
 * 
 * <p/>
 * 
 * Additional options like logging and MTOM (Message Transmission Optimization Mechanism) 
 * are also configurable.
 * 
 * <p/>
 * 
 * An instance of this class can be passed to the EDt client builder to initialize 
 * and configure the service client appropriately.
 */
public class EdtClientBuilderConfig {

	private String userNameTokenUser;
	private String userNameTokenPassword;
	private String keystoreUser;
	private String keystorePassword;
	private String serviceUrl;
	private String conformanceKey;
	private String auditId;
	private String serviceId;

	private boolean loggingRequired;
	private boolean mtomEnabled;
	
	/**
 	 * Creates a new instance of {@code EdtClientBuilderConfig} with a randomly generated 
 	 * audit ID. This audit ID uniquely identifies the client session in the logs,
	 * facilitating traceability and debugging. It is automatically assigned upon
 	 * instantiation and remains constant throughout the client's lifecycle.
 	 */
	public EdtClientBuilderConfig() {
		setAuditId(UUID.randomUUID().toString());
	}
	
	/**
	 * Returns the username for the WS-Security UsernameToken.
	 * 
	 * @return the username used for authentication
	 */
	public String getUserNameTokenUser() {
		return userNameTokenUser;
	}

	/**
	 * Sets the username for the WS-Security UsernameToken.
	 * 
	 * @param username the username used for authentication
	 */
	public void setUserNameTokenUser(String username) {
		this.userNameTokenUser = username;
	}

	/**
	 * Returns the password for the WS-Security UsernameToken.
	 * 
	 * @return the password used for authentication
	 */	
	public String getUserNameTokenPassword() {
		return userNameTokenPassword;
	}

	/**
	 * Sets the password for the WS-Security UsernameToken.
	 * 
	 * @param password the password used for authentication
	 */
	public void setUserNameTokenPassword(String password) {
		this.userNameTokenPassword = password;
	}

	/**
	 * Returns the URL of the MCEDT service.
	 * 
	 * @return the service URL
	 */
	public String getServiceUrl() {
		return serviceUrl;
	}

	/**
	 * Sets the URL of the MCEDT service.
	 * 
	 * @param serviceUrl the URL of the service
	 */
	public void setServiceUrl(String serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	/**
	 * Returns the software conformance key required for the MCEDT service.
	 * 
	 * @return the conformance key
	 */
	public String getConformanceKey() {
		return conformanceKey;
	}

	/**
	 * Sets the software conformance key required for the MCEDT service.
	 * 
	 * @param conformanceKey the conformance key
	 */
	public void setConformanceKey(String conformanceKey) {
		this.conformanceKey = conformanceKey;
	}

	/**
	 * Returns the audit ID that uniquely identifies the client session.
	 * 
	 * @return the audit ID
	 */
	public String getAuditId() {
		return auditId;
	}

	/**
	 * Sets the audit ID that uniquely identifies the client session.
	 * 
	 * @param auditId the audit ID
	 */
	public void setAuditId(String auditId) {
		this.auditId = auditId;
	}

	/**
	 * Returns the keystore username used for SSL/TLS authentication.
	 * 
	 * @return the keystore username
	 */
	public String getKeystoreUser() {
		return keystoreUser;
	}

	/**
	 * Sets the keystore username used for SSL/TLS authentication.
	 * 
	 * @param keystoreUsername the keystore username
	 */
	public void setKeystoreUser(String keystoreUsername) {
		this.keystoreUser = keystoreUsername;
	}

	/**
	 * Returns the keystore password used for SSL/TLS authentication.
	 * 
	 * @return the keystore password
	 */
	public String getKeystorePassword() {
		return keystorePassword;
	}

	/**
	 * Sets the keystore password used for SSL/TLS authentication.
	 * 
	 * @param keystorePassword the keystore password
	 */
	public void setKeystorePassword(String keystorePassword) {
		this.keystorePassword = keystorePassword;
	}

	/**
	 * Returns the service ID that identifies the specific MCEDT service being accessed.
	 * 
	 * @return the service ID
	 */
	public String getServiceId() {
		return serviceId;
	}

	/**
	 * Sets the service ID that identifies the specific MCEDT service being accessed.
	 * 
	 * @param serviceId the service ID
	 */
	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	/**
	 * Returns whether logging is required for the service client.
	 * 
	 * @return {@code true} if logging is enabled, {@code false} otherwise
	 */
	public boolean isLoggingRequired() {
		return loggingRequired;
	}

	/**
	 * Sets whether logging is required for the service client.
	 * 
	 * @param loggingRequired {@code true} to enable logging, {@code false} to disable
	 */
	public void setLoggingRequired(boolean loggingRequired) {
		this.loggingRequired = loggingRequired;
	}

	/**
	 * Returns whether MTOM (Message Transmission Optimization Mechanism) is enabled.
	 * 
	 * @return {@code true} if MTOM is enabled, {@code false} otherwise
	 */
	public boolean isMtomEnabled() {
		return mtomEnabled;
	}

	/**
	 * Sets whether MTOM (Message Transmission Optimization Mechanism) is enabled.
	 * 
	 * @param mtomEnabled {@code true} to enable MTOM, {@code false} to disable
	 */
	public void setMtomEnabled(boolean mtomEnabled) {
		this.mtomEnabled = mtomEnabled;
	}
	
}
