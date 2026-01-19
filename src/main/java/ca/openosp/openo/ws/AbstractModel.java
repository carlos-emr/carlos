package ca.openosp.openo.ws;

import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Abstract base class for JAXB-annotated data transfer objects used in web service operations.
 *
 * <p>This class provides the foundation for healthcare data models that are marshalled to and from
 * XML for SOAP-based web services in the CAISI Integrator system. The Integrator enables multiple
 * OpenO EMR installations to share patient information while maintaining separate local databases
 * and privacy controls.</p>
 *
 * <p><strong>JAXB Configuration:</strong></p>
 * <p>The class uses field-based XML access ({@code @XmlAccessorType(XmlAccessType.FIELD)}) which means:
 * <ul>
 *   <li>All non-static, non-transient fields are automatically bound to XML elements</li>
 *   <li>Getters and setters don't need JAXB annotations</li>
 *   <li>Field visibility (public/protected/private) doesn't affect XML binding</li>
 *   <li>Use {@code @XmlTransient} to exclude specific fields from XML marshalling</li>
 * </ul>
 * </p>
 *
 * <p><strong>Serialization:</strong></p>
 * <p>This class implements {@link Serializable} to support:
 * <ul>
 *   <li>HTTP session storage for web service contexts</li>
 *   <li>Distributed caching across multiple application servers</li>
 *   <li>Message queue serialization for asynchronous processing</li>
 * </ul>
 * The {@code serialVersionUID} ensures version compatibility during deserialization.</p>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <p>Subclasses of this model typically contain Protected Health Information (PHI) and must be
 * handled according to HIPAA/PIPEDA compliance requirements:
 * <ul>
 *   <li>All access to model instances should be logged for audit trails</li>
 *   <li>Sensitive fields (HIN, SIN, images) must be encrypted at rest</li>
 *   <li>Authorization checks are required before accessing or modifying data</li>
 *   <li>Privacy controls (hidden, lockbox flags) must be enforced by web service implementations</li>
 * </ul>
 * </p>
 *
 * <p><strong>Known Subclasses:</strong></p>
 * <p>The {@code @XmlSeeAlso} annotation registers concrete implementations with the JAXB context
 * for polymorphic XML binding. This is essential for SOAP web services to correctly marshal and
 * unmarshal subclass instances.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * // JAXB automatically handles XML marshalling
 * Client client = new Client();
 * client.setFirstName("John");
 * client.setLastName("Doe");
 *
 * // Marshal to XML (handled by web service framework)
 * JAXBContext context = JAXBContext.newInstance(AbstractModel.class);
 * Marshaller marshaller = context.createMarshaller();
 * marshaller.marshal(client, outputStream);
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
     * Serialization version identifier for maintaining compatibility during object deserialization.
     *
     * <p>This constant ensures that serialized instances of this class (or its subclasses) can be
     * successfully deserialized even if the class definition has changed between versions. If the
     * class structure changes in a way that breaks compatibility (e.g., removing fields, changing
     * field types), this value should be incremented.</p>
     *
     * <p>The current value of {@code 1L} indicates this is the initial version of the serialization
     * format.</p>
     */
    private static final long serialVersionUID = 1L;
}
