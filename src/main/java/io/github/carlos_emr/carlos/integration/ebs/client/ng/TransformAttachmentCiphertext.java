package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.signature.XMLSignatureByteInput;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.transforms.TransformSpi;
import org.apache.xml.security.transforms.TransformationException;
import org.xml.sax.SAXException;

/**
 * This class implements a custom transformation for handling attachment ciphertext in XML signatures.
 * It extends the {@link TransformSpi} class from the Apache XML Security library.
 * 
 * <p/>
 * 
 * This transformation is part of the OASIS WSS (Web Services Security) SwA (SOAP with Attachments) 
 * Profile 1.1 and is used to process attachments in a secure manner.</p>
 * 
 * <p/>
 * 
 * The transformation URI is {@link #TRANSFORM_ATTACHMENT_CIPHERTEXT}.
 */
public class TransformAttachmentCiphertext extends TransformSpi {

	public static final String TRANSFORM_ATTACHMENT_CIPHERTEXT = 
			"http://docs.oasis-open.org/wss/oasis-wss-SwAProfile-1.1#Attachment-Ciphertext-Transform";

	/**
	 * @see org.apache.xml.security.transforms.TransformSpi#engineGetURI()
	 */
	@Override
	public String engineGetURI() {
		return TRANSFORM_ATTACHMENT_CIPHERTEXT;
	}

	/**
	 * @see org.apache.xml.security.transforms.TransformSpi#enginePerformTransform(org.apache.xml.security.signature.XMLSignatureInput,
	 *      java.io.OutputStream, Element, String, boolean)
	 */
	@Override
	protected XMLSignatureInput enginePerformTransform(XMLSignatureInput input,
			OutputStream os, Element transformObject, String baseURI, boolean secureValidation) throws IOException,
			CanonicalizationException, InvalidCanonicalizerException,
			TransformationException, ParserConfigurationException, SAXException {
		
		if (input.hasUnprocessedInput() || input.isNodeSet()) {
			if (os == null) {
				byte[] contentBytes = input.getBytes();
				XMLSignatureByteInput output = new XMLSignatureByteInput(contentBytes);
				return output;
			}

			if (input instanceof XMLSignatureByteInput || input.isNodeSet()) {
				os.write(input.getBytes());
			} else {
				try {
					org.apache.xml.security.utils.Base64.decode(new BufferedInputStream(input.getUnprocessedInput()), os);
				} catch (Base64DecodingException e) {
					throw new IOException("Unable to decode real octet stream", e);
				}
			}

			XMLSignatureByteInput output = new XMLSignatureByteInput(new byte[] {});
			output.setOutputStream(os);
			return output;
		}
		return input;
	}

}
