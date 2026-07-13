MCEDT and HCV Issue Knowledge

The MCEDT and HCV functionalities are working now, this document is a reference for future review / update / fix.

# NoClassDefFoundError

This error was caused after the "Apache CXF" was updated from version 2.7.11 to 3.2.0. The "WSPasswordCallBack" class is moved from "org.apache.ws.security" to "org.apache.wssj4.common.ext" in 3.2.0.

To resolve this error, we had to update the "ebs-client" library to make it compatible with Apache CXF 3.2.0. Since the latest published version of the ebs-client is 0.0.6, which is what we had in the application, we modified the ebs-client core classes, implemented these classes into the OSCAR source code, and removed the ebs-client 0.0.6 dependency to avoid any future conflict between two different versions.

The updated ebs-client classes are located in package "org.oscarehr.integration.ebs.client.ng" (file path: src/main/java/org/oscarehr/integration/ebs/client/ng).

# XOP References Issue

MCEDT does not accept SOAP requests containing XOP references. We added the following two properties in "WSS4JOutInterceptor" to fix the issue:

"outProps.put(WSHandlerConstants.STORE_BYTES_IN_ATTACHMENT, "0");"
"outProps.put(WSHandlerConstants.EXPAND_XOP_INCLUDE, "0");"

These two properties ensure that the signature and attachment are included directly in the outgoing message without XOP reference.

# Header Parsing Error

The error "org.apache.cxf.binding.soap.SoapFault: An error was discovered processing the wsse:Security header" threw when getting list on both Sent and Download pages, which was caused due to the algorithm RSA1.5 being unsupported by the "WSS4J 2.2.0" because of its security risk (WSS4J 2.2.0 is the default version in CXF 3.2.0, we can't manually downgrade or upgrade it to another version).

We had to insert a line of code into the EDTClientBuilder.java (one of the classes of ebs-client), newWSSInInterceptorConfiguration method:

"inProps.put(WSHandlerConstants.ALLOW_RSA15_KEY_TRANSPORT_ALGORITHM, "true");"

This enabled the use of the RSA 1.5 algorithm in the current WSS4J version, and the header parsing error was resolved. 

Note: Periodically check for updates to WSS4J and CXF versions, as newer releases may address this issue differently or provide alternative solutions.

# Signature Invalid Error

The error "org.apache.ws.security.WSSecurityException: The signature or decryption was invalid" threw when attempting to download the file on the Download page.

We had to insert two lines of code into the EDTClientBuilder.java, newWSSInInterceptorConfiguration method:

"inProps.put(WSHandlerConstants.STORE_BYTES_IN_ATTACHMENT, "false");"

"inProps.put(WSHandlerConstants.EXPAND_XOP_INCLUDE, "false");"

After setting the "STORE_BYTES_IN_ATTACHMENT" and "EXPAND_XOP_INCLUDE" to false, the binary data is forced to be included inline in the SOAP message (STORE_BYTES_IN_ATTACHMENT, "false"), ensuring no issues arise from processing attachments. By setting "EXPAND_XOP_INCLUDE" to false, we avoided looking for XOP references in the response. The error was resolved after changing the setting.

The default setting of the two properties is false. However, the setting has been switched to true due to other configurations (likely when MTOM is enabled, the setting changed to true automatically). In this case, we had to configure the properties to "false".

# EdtClientBuilder.java

The SOAP message must contain the EBS and IDP headers in the SOAP message header with the user name and password (for the Go-Secure IDP in a WS-Security username token, it is used for authentication. Go-Secure IDP ensures secure access to the system by verifying user credentials). The SOAP headers and body are then digitally signed to guarantee message integrity and source.

If any request data is specified to be encrypted, by the specific web service, it will use the public key of the EBS system. SOAP must be signed with a Timestamp element for each message TTL for the SOAP message will be 10 minutes. Each message must also include the Username token.

# Unit Tests of MCEDT and HCV

## MCEDT Tests

The error "NullPointerException" was caused by "SpringUtils.getBean", which led to most of the test cases running to fail. This is because test cases use Spring components, which are not properly set up in the BaseTest class. We solved the error by adding spring initializing code in the BaseTest class. 

After we fixed the error, most of the test cases passed.

There are 7 test cases failing with a "NumberFormatException" error since we used "List<BigInteger>" as the parameter type, but the ID attempts to be passed as a string. Therefore, due to the current implementation of the dependency, we are not catching the exact error "Rejected By Policy." The MOH MCEDT Conformance Testing team has confirmed that not catching these exceptions is acceptable in this case, and we are not going to fix them at this point.

## HCV Tests

There are 18 test cases skipped since they requested the online test environment (the annotation "@Ignore("Valid only for online validation")" was added to the 18 test cases).

The context is that the test cases were written to match what was supposed to happen according to the conformance certification, but in REAL-LIFE, that's not what actually is happening. Therefore, the tests that were written to be failed, and we will not enable them to run at this point. 
