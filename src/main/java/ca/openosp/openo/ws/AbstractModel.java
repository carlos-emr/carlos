package ca.openosp.openo.ws;

import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Abstract base class for JAXB-enabled web service data transfer objects (DTOs) in the OpenO EMR system.
 *
 * <p>This class provides common JAXB XML binding configuration for all web service model objects,
 * ensuring consistent serialization behavior across REST API endpoints. The class is configured for
 * field-based XML access, meaning JAXB will directly access fields rather than using getter/setter methods
 * for XML marshalling and unmarshalling operations.</p>
 *
 * <p><strong>JAXB Configuration:</strong></p>
 * <ul>
 *   <li>{@code @XmlAccessorType(XmlAccessType.FIELD)} - Direct field access for XML binding (no getters/setters required)</li>
 *   <li>{@code @XmlType(name = "abstractModel")} - Defines the XML schema type name</li>
 *   <li>{@code @XmlSeeAlso} - Registers known subclasses for JAXB context initialization</li>
 * </ul>
 *
 * <p><strong>Serialization:</strong></p>
 * <p>Implements {@link Serializable} to support Java object serialization for caching, session storage,
 * and distributed computing scenarios. The serialVersionUID ensures version compatibility during
 * deserialization.</p>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <p>As part of the web service layer, subclasses of this model may contain Protected Health Information (PHI).
 * All implementations must ensure HIPAA/PIPEDA compliance by:</p>
 * <ul>
 *   <li>Never logging PHI data in debug/error messages</li>
 *   <li>Applying proper authorization checks before exposing patient data</li>
 *   <li>Using encrypted transport (HTTPS/TLS) for all web service communications</li>
 *   <li>Following audit logging requirements for PHI access</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Subclass implementation
 * public class Client extends AbstractModel {
 *     private String name;
 *     private String healthCardNumber;
 *     // fields are automatically XML-bound via JAXB
 * }
 *
 * // JAXB marshalling
 * JAXBContext context = JAXBContext.newInstance(Client.class);
 * Marshaller marshaller = context.createMarshaller();
 * marshaller.marshal(clientInstance, outputStream);
 * }</pre>
 *
 * @since 2026-01-18
 * @see Client
 * @see ca.openosp.openo.caisi_integrator.ws.HnrWs
 * @see javax.xml.bind.annotation.XmlAccessorType
 * @see javax.xml.bind.annotation.XmlType
 * @see java.io.Serializable
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "abstractModel")
@XmlSeeAlso({ Client.class })
public abstract class AbstractModel implements Serializable
{
    /**
     * Serial version UID for Java serialization compatibility.
     *
     * <p>This constant ensures that serialized instances of this class (and its subclasses)
     * can be reliably deserialized even if the class definition changes. When making
     * non-compatible changes to the class structure, this value should be updated to
     * prevent deserialization of incompatible versions.</p>
     */
    private static final long serialVersionUID = 1L;
}
