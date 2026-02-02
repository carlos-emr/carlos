package ca.openosp.openo.caisi_integrator.dao;

import java.util.Iterator;
import java.util.List;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import java.io.Serializable;

/**
 * Abstract base class for CAISI Integrator data access objects (models).
 *
 * <p>This class provides common functionality for all CAISI Integrator models including
 * identity management, equality comparison, and utility methods for working with model collections.
 * The CAISI (Client Access to Integrated Services and Information) Integrator is a healthcare
 * information exchange system that enables sharing of patient data across multiple OpenO EMR
 * installations and external healthcare systems.</p>
 *
 * <p>All CAISI Integrator model classes extend this abstract base to ensure consistent
 * object identity semantics based on persistent identifiers. The class enforces that models
 * warns when models are not persisted (null ID) before participating in operations like
 * equality comparison and hash-based collections.
 *
 * <p><strong>Type Parameter:</strong></p>
 * <ul>
 *   <li>{@code T} - The type of the model's primary key identifier (e.g., Integer, Long, String,
 *       or composite key types like {@link FacilityIdStringCompositePk})</li>
 * </ul>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Generic type support for various primary key types</li>
 *   <li>Identity-based equality and hash code implementation</li>
 *   <li>Reflection-based toString for debugging and logging</li>
 *   <li>Utility methods for collection operations</li>
 *   <li>Validation warnings for operations on non-persisted objects</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * public class CachedDemographic extends AbstractModel&lt;Integer&gt; {
 *     private Integer id;
 *
 *     {@literal @}Override
 *     public Integer getId() {
 *         return id;
 *     }
 * }
 * </pre>
 *
 * @param <T> the type of the primary key identifier for this model
 *
 * @see CachedDemographic
 * @see CachedProvider
 * @see Facility
 * @see FacilityIdStringCompositePk
 *
 * @since 2026-01-23
 */
abstract class AbstractModel<T> implements Serializable
{
    /**
     * Error message constant used when operations requiring a persisted object are performed
     * on objects that have not yet been saved to the database.
     *
     * <p>This message is logged as a warning when methods like {@link #hashCode()} or
     * {@link #equals(Object)} are called on models with null identifiers, indicating that
     * the object has not been persisted to the database yet.</p>
     */
    protected static final String OBJECT_NOT_YET_PERISTED = "The object is not persisted yet, this operation requires the object to already be persisted.";

    /**
     * Returns the primary key identifier for this model instance.
     *
     * <p>Subclasses must implement this method to provide access to their primary key field.
     * The identifier should be null for transient (non-persisted) objects and non-null for
     * objects that have been saved to the database.</p>
     *
     * <p>This method is used internally by {@link #hashCode()}, {@link #equals(Object)}, and
     * {@link #existsId(List, AbstractModel)} to determine object identity.</p>
     *
     * @return T the primary key identifier, or null if the object has not been persisted
     */
    public abstract T getId();

    /**
     * Returns a string representation of this model instance using reflection.
     *
     * <p>This method uses Apache Commons Lang's {@link ReflectionToStringBuilder} to
     * automatically generate a string representation that includes all fields and their
     * values. This is particularly useful for debugging, logging (non-PHI data only),
     * and development purposes.</p>
     *
     * <p><strong>Security Note:</strong> The generated string may contain sensitive patient
     * health information (PHI). Care must be taken to ensure this method is only used in
     * contexts where PHI logging is permitted and properly secured.</p>
     *
     * @return String a reflection-based string representation of this object including
     *         all field names and values
     */
    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString((Object)this);
    }

    /**
     * Returns a hash code value for this model based on its primary key identifier.
     *
     * <p>This implementation provides identity-based hashing using the model's primary key.
     * For persisted objects (non-null ID), the hash code is computed from the identifier
     * to ensure consistent hashing behavior across sessions and JVM instances.</p>
     *
     * <p>For non-persisted objects (null ID), this method falls back to the default
     * {@link Object#hashCode()} implementation and logs a warning, as using non-persisted
     * objects in hash-based collections may lead to unexpected behavior after persistence.</p>
     *
     * <p><strong>Important:</strong> Objects with null IDs should not be added to hash-based
     * collections (HashSet, HashMap) if they will be persisted later, as their hash code
     * will change upon persistence.</p>
     *
     * @return int the hash code value computed from the primary key ID, or the default
     *         object hash code if the ID is null
     */
    @Override
    public int hashCode() {
        if (this.getId() == null) {
            MiscUtils.getLogger().warn((Object)"The object is not persisted yet, this operation requires the object to already be persisted.", (Throwable)new Exception());
            return super.hashCode();
        }
        return this.getId().hashCode();
    }

    /**
     * Compares this model to another object for equality based on class type and primary key.
     *
     * <p>Two model instances are considered equal if and only if:</p>
     * <ul>
     *   <li>They are instances of the exact same class (not just compatible types)</li>
     *   <li>Their primary key identifiers are equal according to the ID's equals method</li>
     * </ul>
     *
     * <p>This implementation ensures that model equality is based on database identity
     * (primary key) rather than object instance identity, which is appropriate for
     * persistent domain objects.</p>
     *
     * <p>If this object's ID is null (not yet persisted), a warning is logged. Comparing
     * non-persisted objects may produce unexpected results and should generally be avoided
     * in business logic.</p>
     *
     * @param object Object the reference object with which to compare
     *
     * @return boolean true if this model is equal to the object argument; false otherwise
     */
    @Override
    public boolean equals(final Object object) {
        if (this.getClass() != object.getClass()) {
            return false;
        }
        final AbstractModel<T> abstractModel = (AbstractModel<T>)object;
        if (this.getId() == null) {
            MiscUtils.getLogger().warn((Object)"The object is not persisted yet, this operation requires the object to already be persisted.", (Throwable)new Exception());
        }
        return this.getId().equals(abstractModel.getId());
    }

    /**
     * Checks if a model with the same primary key identifier exists in the given list.
     *
     * <p>This utility method searches through a list of models to determine if any model
     * in the list has the same primary key identifier as the search model. The comparison
     * is based solely on ID equality, not on object instance equality or full object equality.</p>
     *
     * <p>This method is useful for checking duplicates, validating data before insertion,
     * or determining if a particular entity is already present in a collection when you
     * only care about database identity rather than complete object state.</p>
     *
     * <p><strong>Type Parameter:</strong></p>
     * <ul>
     *   <li>{@code X} - A type that extends AbstractModel with any primary key type</li>
     * </ul>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * List&lt;CachedDemographic&gt; demographics = ...;
     * CachedDemographic newDemographic = ...;
     * if (!AbstractModel.existsId(demographics, newDemographic)) {
     *     demographics.add(newDemographic);
     * }
     * </pre>
     *
     * @param <X> the specific AbstractModel subtype contained in the list
     * @param list List&lt;X&gt; the list of models to search through
     * @param searchModel X the model whose ID to search for in the list
     *
     * @return Boolean true if a model with the same ID exists in the list; false otherwise
     */
    public static <X extends AbstractModel<?>> boolean existsId(final List<X> list, final X searchModel) {
        final Object searchPk = ((AbstractModel<Object>)searchModel).getId();
        for (final X tempModel : list) {
            final Object tempPk = ((AbstractModel<Object>)tempModel).getId();
            if (searchPk.equals(tempPk)) {
                return Boolean.TRUE;
            }
        }
        return false;
    }
}
