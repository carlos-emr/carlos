package ca.openosp.openo.integration.ebs.client.ng;

import java.util.List;
import java.util.Map;

import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;

/**
 * Overrides CXF action processing to overcome limitation of non-symmetric
 * service call encryptions.
 * 
 * <p/>
 * 
 * By default CXF requires identical actions to be configured on In and Out
 * interceptors. Otherwise, it triggers a WSSecurityException.
 * In MCEDT case, actions can not be identical, as there is SOAP request must not be encrypted,
 * but SOAP response comes back in an encrypted form. To allow for that,
 * {@link #checkReceiverResultsAnyOrder(List, List)} is overridden to accept all
 * results.
 */
public class WSS4JInNonValidatingActionInterceptor extends WSS4JInInterceptor {

	/**
	 * Constructs a new instance of the interceptor with default settings.
	 */
	public WSS4JInNonValidatingActionInterceptor() {
		super();
	}

	/**
	 * Constructs a new instance of the interceptor, allowing the option to ignore 
	 * specific validation settings.
	 * 
	 * @param ignore if {@code true}, the interceptor will ignore validation
	 */
	public WSS4JInNonValidatingActionInterceptor(boolean ignore) {
		super(ignore);
	}

	/**
	 * Constructs a new instance of the interceptor with specified properties.
	 * 
	 * @param properties a map of properties used to configure the interceptor
	 */
	public WSS4JInNonValidatingActionInterceptor(Map<String, Object> properties) {
		super(properties);
	}
	
	/**
	 * Overrides parent method to allow all results.
	 *
	 * @param wsResult the list of security engine results
	 * @param actions the list of expected actions
	 * @return Returns true for all parameters.
	 */
	@Override
	protected boolean checkReceiverResultsAnyOrder(
			List<WSSecurityEngineResult> wsResult, List<Integer> actions) {
		return true;
	}

}
