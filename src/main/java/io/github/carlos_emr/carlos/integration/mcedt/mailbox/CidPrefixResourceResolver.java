package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;

import org.apache.cxf.message.Attachment;
import org.apache.log4j.Logger;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.AttachmentCachingInterceptor;

/**
 * Resource resolver class that detects if a uriToResolve starts with "cid:", as in
 * the case of MCEDT responses. It locates the attached resources using the uriToResolve
 * with "cid:" removed, allowing access of resource despite uri prefix
 */
public class CidPrefixResourceResolver extends ResourceResolverSpi {

    private static final String SUPPORTED_URI_PREFIX = "cid:";

    private static final String ATTACHMENT_PREFIX = "cid:";

    private static Logger logger = Logger.getLogger(CidPrefixResourceResolver.class);

    private Collection<Attachment> attachments;

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
        String resourceId = getResourceId(context);
        Attachment attachment = getAttachment(resourceId);

        if (attachment == null) {
            String errorMessage = String.format("Unable to resolve attachment for %s with attr %s", resourceId, context.attr);
            logger.error(errorMessage);
            throw new ResourceResolverException(errorMessage, context.uriToResolve, context.baseUri);
        }

        XMLSignatureInput result;
        try {
            result = new XMLSignatureInput(attachment.getDataHandler().getInputStream());
        } catch (IOException e) {
            logger.error("Unable to create xml signature input", e);

            throw new ResourceResolverException("Unable to create xml signature input", context.uriToResolve, context.baseUri);
        }
        return result;
    }

    private String getResourceId(ResourceResolverContext context) {
        String resourceId = context.uriToResolve;
        try {
            resourceId = URLDecoder.decode(resourceId, "UTF-8");
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
            logger.debug("Comparing " + a.getId() + " with " + resourceId);

            if (a.getId().equals(resourceId)) {
                return a;
            }
        }
        return null;
    }

    protected Collection<Attachment> getAttachments() {
        if (attachments == null) {
            attachments = AttachmentCachingInterceptor.getAttachments();
        }
        return attachments;
    }
}