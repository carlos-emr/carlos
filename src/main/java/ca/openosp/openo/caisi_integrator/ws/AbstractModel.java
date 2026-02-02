package ca.openosp.openo.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Abstract base model class for CAISI Integrator web service data transfer objects.
 *
 * <p>This class serves as the foundation for JAXB-annotated model classes used in CAISI
 * (Client Access to Integrated Services and Information) Integrator web services. The
 * CAISI Integrator system enables inter-EMR data sharing and healthcare system integration
 * across multiple OpenO EMR installations within a healthcare network.</p>
 *
 * <p>This abstract model provides:</p>
 * <ul>
 *   <li>XML serialization support through JAXB annotations for SOAP web services</li>
 *   <li>Java serialization support for distributed system communication</li>
 *   <li>Common base structure for healthcare data transfer objects</li>
 *   <li>Field-level XML access for consistent marshalling/unmarshalling behavior</li>
 * </ul>
 *
 * <p>The {@code @XmlAccessorType(XmlAccessType.FIELD)} annotation ensures that all fields
 * in extending classes are automatically included in XML serialization without requiring
 * getter/setter annotations, promoting consistent XML schema generation.</p>
 *
 * <p><strong>Healthcare Context:</strong> This model is part of the CAISI Integrator
 * infrastructure that facilitates secure PHI (Protected Health Information) exchange
 * between healthcare facilities while maintaining compliance with HIPAA/PIPEDA regulations.
 * All extending classes must ensure proper handling of sensitive patient data.</p>
 *
 * <p><strong>Usage:</strong> This class is extended by specific data transfer models such as
 * {@link Referral}, which represent healthcare entities transmitted between facilities via
 * SOAP web services in the integrator system.</p>
 *
 * @see Referral
 * @see java.io.Serializable
 * @since 2026-01-23
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "abstractModel")
@XmlSeeAlso({ Referral.class })
public abstract class AbstractModel implements Serializable
{
    /**
     * Serial version UID for Java serialization compatibility.
     *
     * <p>This ensures that serialized instances of extending classes can be deserialized
     * correctly across different versions of the OpenO EMR system, maintaining compatibility
     * in distributed healthcare integration scenarios.</p>
     */
    private static final long serialVersionUID = 1L;
}
