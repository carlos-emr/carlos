package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;

import org.apache.cxf.message.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.signature.XMLSignatureStreamInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;

/**
 * Resolver for accessing attachment parts within a message.
 * 
 * <h3>Purpose</h3>
 * 
 * This class resolves URIs that refer to message attachments using the "cid:" (Content-ID) URI scheme.
 * It is primarily used in the context of XML signatures, where the signature refers to an attachment 
 * within a SOAP message.
 * 
 * <h3>Implementation notes</h3>
 * 
 * - The globally registered resolver retains no response state. It depends on
 *   {@link AttachmentCachingInterceptor} for attachments belonging to the current
 *   response thread; a prior response's collection must never be reused.
 * - It resolves URIs that start with a specific prefix (`cid:urn`), extracting the relevant attachment 
 *   and returning its data stream in an {@link XMLSignatureInput} object for further processing.
 *
 * <h3>Error Handling</h3>
 * 
 * If an attachment cannot be found or an I/O error occurs while reading the attachment data, 
 * a {@link ResourceResolverException} is thrown.
 * 
 * <h3>Usage</h3>
 * 
 * This resolver should be used in conjunction with the {@link AttachmentCachingInterceptor} to ensure 
 * that the message attachments are properly cached before they are accessed.
 */
public class AttachmentResolverSpi extends ResourceResolverSpi {
	
	private static final String SUPPORTED_URI_PREFIX = "cid:urn";
	private static final String ATTACHMENT_PREFIX = "cid:";

	private static final Logger logger = LoggerFactory.getLogger(AttachmentResolverSpi.class);
	
	/**
	 * Indicates whether this resource resolver is thread-safe.
	 * 
	 * @return false, indicating this resolver is not thread-safe.
	 */
    public boolean engineIsThreadSafe() {
        return false;
    }

	/**
	 * Checks if the resolver can handle the given URI.
	 * 
	 * @param context The context containing the URI to resolve.
	 * @return true if the URI can be resolved by this resolver, false otherwise.
	 */
	@Override
	public boolean engineCanResolveURI(ResourceResolverContext context) {
		if (context.uriToResolve == null) {
			return false;
		}
		return context.uriToResolve.startsWith(SUPPORTED_URI_PREFIX);
	}

	/**
	 * Resolves the URI to the corresponding XMLSignatureInput.
	 * 
	 * @param context The context containing the URI to resolve.
	 * @return An XMLSignatureInput object containing the data of the resolved attachment.
	 * @throws ResourceResolverException if the attachment cannot be found or an I/O error occurs.
	 */
	@Override
	public XMLSignatureInput engineResolveURI(ResourceResolverContext context)
			throws ResourceResolverException {
		String resourceId;
		try {
			resourceId = getResourceId(context);
		} catch (IllegalArgumentException e) {
			logger.error("EBS attachment URI could not be decoded");
			throw resolutionFailure(context, "EBS attachment URI could not be decoded");
		}
		
		Attachment attachment = getAttachment(resourceId);
		
		if (attachment == null) {
			logger.error("EBS attachment could not be resolved");
			throw resolutionFailure(context, "EBS attachment could not be resolved");
		}
		
		logger.debug("EBS attachment resolved");
		
		XMLSignatureInput result;
		try {
			result = new XMLSignatureStreamInput(attachment.getDataHandler().getInputStream());
		} catch (IOException e) {
			// Provider I/O messages can contain content IDs, filenames or credentials.
			logger.error("EBS attachment signature input could not be opened");
			throw resolutionFailure(context, "EBS attachment signature input could not be opened");
		}
		return result;
	}

	/** Keeps resolver context metadata available without copying it into a logged exception message. */
	private static ResourceResolverException resolutionFailure(ResourceResolverContext context, String message) {
		return new ResourceResolverException("generic.EmptyMessage", new Object[]{message},
				context.uriToResolve, context.baseUri);
	}

	/**
	 * Extracts the resource ID from the context's URI.
	 * 
	 * @param context The context containing the URI to resolve.
	 * @return The decoded resource ID without the "cid:" prefix.
	 */
	private String getResourceId(ResourceResolverContext context) {
		String resourceId = context.uriToResolve;
		try {
			// A cid URI is not form data: a literal '+' is part of the Content-ID.
			resourceId = URLDecoder.decode(resourceId.replace("+", "%2B"), "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			throw new RuntimeException("Unable to decode", e1);
		}
		if (resourceId.startsWith(ATTACHMENT_PREFIX)) {
			resourceId = resourceId.substring(ATTACHMENT_PREFIX.length());
		}
		return resourceId;
	}

	/**
	 * Retrieves the attachment corresponding to the given resource ID.
	 * 
	 * @param resourceId The resource ID of the attachment to retrieve.
	 * @return The Attachment object if found, null otherwise.
	 */	
	private Attachment getAttachment(String resourceId) {
		Collection<Attachment> attachments = getAttachments();
		if (attachments == null) {
			return null;
		}
		
		for (Attachment a : attachments) {
			if (a.getId().equals(resourceId)) {
				return a;
			}
		}
		return null;
	}

	/**
	 * Retrieves the cached attachments.
	 * 
	 * @return A collection of Attachment objects, or null if none are available.
	 */
	protected Collection<Attachment> getAttachments() {
		return AttachmentCachingInterceptor.getAttachments();
	}

}
