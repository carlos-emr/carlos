package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.signature.XMLSignatureStreamInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;

import org.apache.cxf.message.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.AttachmentCachingInterceptor;

/**
 * Resource resolver class that detects if a uriToResolve starts with "cid:", as in
 * the case of MCEDT responses. It locates the attached resources using the uriToResolve
 * with "cid:" removed, allowing access of resource despite uri prefix
 * Each resolution uses the current response thread's attachments; a globally
 * registered resolver must not retain a previous response's collection.
 */
public class CidPrefixResourceResolver extends ResourceResolverSpi {

    private static final String SUPPORTED_URI_PREFIX = "cid:";

    private static final String ATTACHMENT_PREFIX = "cid:";

    private static final Logger logger = LoggerFactory.getLogger(CidPrefixResourceResolver.class);

    public boolean engineIsThreadSafe() {
        return false;
    }

    @Override
    public boolean engineCanResolveURI(ResourceResolverContext context) {
        if (context.uriToResolve == null) {
            return false;
        }
        return context.uriToResolve.startsWith(SUPPORTED_URI_PREFIX);
    }

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
            logger.error("EBS attachment signature input could not be opened");
            throw resolutionFailure(context, "EBS attachment signature input could not be opened");
        }
        return result;
    }

    /** Keeps resolver metadata available without including provider content in the exception message. */
    private static ResourceResolverException resolutionFailure(ResourceResolverContext context, String message) {
        return new ResourceResolverException("generic.EmptyMessage", new Object[]{message},
                context.uriToResolve, context.baseUri);
    }

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

    protected Collection<Attachment> getAttachments() {
        // Resolver instances may be retained or globally registered; only the current
        // response thread owns the attachment collection, including after cleanup.
        return AttachmentCachingInterceptor.getAttachments();
    }
}
