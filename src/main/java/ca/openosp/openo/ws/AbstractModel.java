package ca.openosp.openo.ws;

import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Abstract base class for JAXB-enabled data transfer objects used in web service operations.
 *
 * <p>This class serves as the foundation for all web service DTOs in the Health Number Registry (HNR)
 * and related CAISI Integrator web services. It provides common JAXB configuration and serialization
 * support required for SOAP-based healthcare data exchange.</p>
 *
 * <p><strong>JAXB Configuration:</strong></p>
 * <ul>
 *   <li><strong>XmlAccessorType.FIELD</strong> - JAXB binds directly to fields rather than getter/setter methods,
 *       allowing simpler DTO classes without requiring JavaBeans conventions</li>
 *   <li><strong>Serializable</strong> - Enables Java serialization for session persistence and distributed caching</li>
 *   <li><strong>XmlType</strong> - Defines the XML schema type name for WSDL generation</li>
 *   <li><strong>XmlSeeAlso</strong> - Declares known subclasses for JAXB context initialization</li>
 * </ul>
 *
 * <p><strong>Serialization:</strong></p>
 * <p>This class implements {@link Serializable} to support:</p>
 * <ul>
 *   <li>HTTP session storage of web service responses</li>
 *   <li>Distributed caching of frequently accessed healthcare data</li>
 *   <li>Compatibility with legacy Java serialization frameworks</li>
 * </ul>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <p>All DTOs extending this class are used to transmit Protected Health Information (PHI) between
 * healthcare facilities in the CAISI Integrator system. Implementations must ensure:</p>
 * <ul>
 *   <li>HIPAA/PIPEDA compliance for PHI transmission and storage</li>
 *   <li>Secure transport layer (HTTPS/TLS) for web service communications</li>
 *   <li>Proper access controls and audit logging at the service layer</li>
 *   <li>Data minimization - only include necessary fields for the specific use case</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // JAXB marshalling to XML
 * JAXBContext context = JAXBContext.newInstance(Client.class);
 * Marshaller marshaller = context.createMarshaller();
 * marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
 *
 * Client client = new Client();
 * client.setFirstName("John");
 * client.setLastName("Doe");
 * client.setHin("1234567890");
 *
 * StringWriter writer = new StringWriter();
 * marshaller.marshal(client, writer);
 * String xml = writer.toString();
 * </pre>
 *
 * @see Client
 * @see ca.openosp.openo.caisi_integrator.ws.HnrWs
 * @see javax.xml.bind.annotation.XmlAccessorType
 * @see javax.xml.bind.annotation.XmlType
 * @see java.io.Serializable
 * @since 2026-01-18
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "abstractModel")
@XmlSeeAlso({ Client.class })
public abstract class AbstractModel implements Serializable
{
    /**
     * Serial version UID for Java serialization compatibility.
     *
     * <p>This constant ensures that serialized instances of subclasses can be deserialized
     * even as the class evolves. When making structural changes to subclasses, consider
     * updating this value to prevent deserialization of incompatible versions.</p>
     *
     * <p>For healthcare data models, changing the serialVersionUID should be done carefully
     * to avoid breaking session persistence or cached medical records.</p>
     */
    private static final long serialVersionUID = 1L;
}
