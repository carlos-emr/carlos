package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.wss4j.common.ext.WSPasswordCallback;

/**
 * Callback handler used for handling password and keystore alias requests in WS-Security operations.
 * 
 * <h3>Purpose</h3>
 * 
 * The {@code ClientPasswordCallback} class is a custom implementation of the {@link CallbackHandler}
 * interface, which is used to provide passwords and keystore aliases for WS-Security operations 
 * such as user authentication (via username tokens) and cryptographic operations (e.g., signature and decryption).
 * 
 * <h3>Usage</h3>
 * 
 * This class is typically used in the context of Apache CXF-based www services that rely on WS-Security for
 * securing messages. It handles the following WS-Security password callbacks:
 * <ul>
 *   <li>USERNAME_TOKEN: Provides the username and password for user authentication.</li>
 *   <li>SIGNATURE/DECRYPT: Provides the keystore alias and password for signing and decrypting messages.</li>
 * </ul>
 * 
 * <h3>Implementation Notes</h3>
 * 
 * - For {@code WSPasswordCallback.USERNAME_TOKEN}, the class overrides the username to ensure proper authentication,
 *   as Apache CXF may use the same identifier for both the keystore and the username token.
 * - It throws an {@link UnsupportedCallbackException} for unsupported callback types.
 */
public class ClientPasswordCallback implements CallbackHandler {

	private String username;
	private String password;

	private String keystoreAlias;
	private String keystorePassword;

	/**
	 * Constructs a new {@code ClientPasswordCallback} instance with the specified credentials.
	 * 
	 * @param username         The username for authentication.
	 * @param password         The password associated with the username.
	 * @param keystoreAlias    The alias of the keystore for signing/decrypting.
	 * @param keystorePassword The password for the keystore alias.
	 */
	public ClientPasswordCallback(String username, String password,
			String keystoreAlias, String keystorePassword) {
		this.username = username;
		this.password = password;
		this.keystoreAlias = keystoreAlias;
		this.keystorePassword = keystorePassword;
	}

	/**
	 * Handles the provided callback requests, populating the necessary credentials for WS-Security operations.
	 * 
	 * @param callbacks                      An array of {@link Callback} objects to handle.
	 * @throws IOException                   If an I/O error occurs while processing the callbacks.
	 * @throws UnsupportedCallbackException  If an unsupported callback type is encountered.
	 */
	public void handle(Callback[] callbacks) throws IOException,
			UnsupportedCallbackException {
		for (Callback c : callbacks) {
			if (c instanceof WSPasswordCallback) {
				WSPasswordCallback pc = (WSPasswordCallback) c;
				switch (pc.getUsage()) {
				case WSPasswordCallback.USERNAME_TOKEN:
					// for some reason, CXF uses the same username for both
					// the keystore and the username token - we need to override it to make it work
					pc.setIdentifier(username);
					pc.setPassword(password);
					break;
				case WSPasswordCallback.DECRYPT:
				case WSPasswordCallback.SIGNATURE:
					pc.setIdentifier(keystoreAlias);
					pc.setPassword(keystorePassword);
					break;
				default:
					throw new UnsupportedCallbackException(c,
							"Unsupported callback type");
				}
			}
		}
	}
	
}
