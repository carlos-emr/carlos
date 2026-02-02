package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import java.util.List;
import java.util.Iterator;
import java.util.HashSet;
import org.apache.openjpa.enhance.StateManager;
import ca.openosp.openo.caisi_integrator.util.CodeType;
import org.apache.log4j.Logger;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * Embeddable JPA entity representing a clinical issue associated with a clinical note in the CAISI integrator system.
 *
 * <p>This class represents a medical issue or diagnosis code that can be attached to clinical notes.
 * It combines a code type (indicating the coding system used, such as ICD-9, ICD-10, SNOMED CT) with
 * an issue code value to uniquely identify a clinical condition or diagnosis. NoteIssue instances are
 * typically embedded within clinical note entities to track what medical issues or diagnoses were
 * addressed during a patient encounter.</p>
 *
 * <p>The class is enhanced by OpenJPA for persistence and includes extensive JPA persistence capability
 * methods for state management, field access control, and serialization support. It provides utility
 * methods for converting between object and string representations to facilitate data interchange
 * across CAISI integrator components.</p>
 *
 * <p><strong>Healthcare Context:</strong> Clinical issues are fundamental to medical documentation,
 * enabling proper tracking of diagnoses, billing code assignment, and clinical decision support.
 * The code type enumeration supports multiple medical coding systems to accommodate different
 * provincial and international healthcare standards.</p>
 *
 * @see ca.openosp.openo.caisi_integrator.util.CodeType
 * @since 2026-01-24
 */
@Embeddable
public class NoteIssue implements Comparable<NoteIssue>, Serializable, PersistenceCapable
{
    private static final long serialVersionUID = 1L;
    private static final Logger logger;
    private CodeType codeType;
    private String issueCode;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$util$CodeType;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$NoteIssue;
    private transient Object pcDetachedState;
    
    /**
     * Default no-argument constructor required by JPA.
     *
     * <p>Creates an uninitialized NoteIssue instance. The code type and issue code
     * should be set using the appropriate setter methods after construction.</p>
     */
    public NoteIssue() {
    }

    /**
     * Constructs a NoteIssue with the specified code type and issue code.
     *
     * @param codeType CodeType the coding system type (e.g., ICD-9, ICD-10, SNOMED CT)
     * @param issueCode String the specific code value within the coding system
     */
    private NoteIssue(final CodeType codeType, final String issueCode) {
        this.codeType = codeType;
        this.issueCode = issueCode;
    }
    
    /**
     * Gets the code type indicating which coding system this issue belongs to.
     *
     * @return CodeType the coding system type (e.g., ICD-9, ICD-10, SNOMED CT), or null if not set
     */
    public CodeType getCodeType() {
        return pcGetcodeType(this);
    }

    /**
     * Sets the code type indicating which coding system this issue belongs to.
     *
     * @param codeType CodeType the coding system type to set
     */
    public void setCodeType(final CodeType codeType) {
        pcSetcodeType(this, codeType);
    }

    /**
     * Gets the specific issue code value within the coding system.
     *
     * @return String the issue code value, or null if not set
     */
    public String getIssueCode() {
        return pcGetissueCode(this);
    }

    /**
     * Sets the specific issue code value within the coding system.
     *
     * @param issueCode String the issue code value to set
     */
    public void setIssueCode(final String issueCode) {
        pcSetissueCode(this, issueCode);
    }
    
    /**
     * Converts this NoteIssue to a string representation in the format "CodeType.IssueCode".
     *
     * <p>This method provides a concise string representation suitable for display and logging.
     * The format follows the pattern: CodeType enumeration name, a period separator, and the issue code.</p>
     *
     * @return String the formatted string representation (e.g., "ICD9.250.00")
     */
    public final String asString() {
        return String.format("%s.%s", this.getCodeType().name(), this.getIssueCode());
    }
    
    /**
     * Converts a collection of NoteIssue objects to their string representations.
     *
     * <p>This utility method transforms a set of NoteIssue instances into a set of formatted
     * strings following the "CodeType.IssueCode" pattern. This is useful for serialization,
     * display, or data interchange scenarios.</p>
     *
     * @param issues HashSet&lt;NoteIssue&gt; the collection of NoteIssue objects to convert
     * @return HashSet&lt;String&gt; a new set containing string representations of the issues
     */
    public static final HashSet<String> toStrings(final HashSet<NoteIssue> issues) {
        final HashSet<String> tmp = new HashSet<String>();
        for (final NoteIssue issue : issues) {
            tmp.add(String.format("%s.%s", issue.getCodeType().name(), issue.getIssueCode()));
        }
        return tmp;
    }
    
    /**
     * Converts a collection of string representations to NoteIssue objects.
     *
     * <p>This utility method parses a list of strings formatted as "CodeType.IssueCode" and
     * creates corresponding NoteIssue instances. Invalid or malformed strings are logged as
     * errors but do not interrupt processing; they are simply skipped.</p>
     *
     * @param strings List&lt;String&gt; the list of string representations to convert
     * @return HashSet&lt;NoteIssue&gt; a new set containing the successfully parsed NoteIssue objects
     */
    public static final HashSet<NoteIssue> fromStrings(final List<String> strings) {
        final HashSet<NoteIssue> tmp = new HashSet<NoteIssue>();
        for (final String item : strings) {
            try {
                tmp.add(valueOf(item));
            }
            catch (final Exception e) {
                NoteIssue.logger.error((Object)"Unexpected error.", (Throwable)e);
            }
        }
        return tmp;
    }
    
    /**
     * Parses a string representation into a NoteIssue object.
     *
     * <p>This method accepts a string in the format "CodeType.IssueCode" and constructs a
     * corresponding NoteIssue instance. The code type portion must match a valid CodeType
     * enumeration value.</p>
     *
     * @param s String the string to parse (e.g., "ICD9.250.00")
     * @return NoteIssue a new NoteIssue instance with the parsed code type and issue code
     * @throws IllegalArgumentException if the string format is invalid or does not contain exactly one period separator
     */
    public static NoteIssue valueOf(final String s) {
        final String[] tempSplit = s.split("\\.");
        if (tempSplit.length == 2) {
            return new NoteIssue(CodeType.valueOf(tempSplit[0]), tempSplit[1]);
        }
        throw new IllegalArgumentException(s + ", split=" + tempSplit);
    }
    
    /**
     * Returns a string representation of this NoteIssue in the format "CodeType.IssueCode".
     *
     * @return String the formatted string representation
     */
    @Override
    public String toString() {
        return pcGetcodeType(this).name() + '.' + pcGetissueCode(this);
    }

    /**
     * Returns a hash code value for this NoteIssue based on the issue code.
     *
     * @return int the hash code value
     */
    @Override
    public int hashCode() {
        return pcGetissueCode(this).hashCode();
    }

    /**
     * Compares this NoteIssue to another object for equality.
     *
     * <p>Two NoteIssue instances are considered equal if they have the same code type
     * and the same issue code value.</p>
     *
     * @param o Object the object to compare with
     * @return boolean true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(final Object o) {
        try {
            final NoteIssue o2 = (NoteIssue)o;
            return pcGetcodeType(this).equals(pcGetcodeType(o2)) && pcGetissueCode(this).equals(pcGetissueCode(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Compares this NoteIssue to another NoteIssue for ordering.
     *
     * <p>The comparison is based on the lexicographic ordering of the string representations
     * in "CodeType.IssueCode" format.</p>
     *
     * @param o NoteIssue the NoteIssue to compare to
     * @return int a negative integer, zero, or a positive integer as this NoteIssue is less than,
     *         equal to, or greater than the specified NoteIssue
     */
    @Override
    public int compareTo(final NoteIssue o) {
        return this.toString().compareTo(o.toString());
    }
    
    static {
        logger = MiscUtils.getLogger();
        NoteIssue.pcFieldNames = new String[] { "codeType", "issueCode" };
        NoteIssue.pcFieldTypes = new Class[] { (NoteIssue.class$Lca$openosp$openo$caisi_integrator$util$CodeType != null) ? NoteIssue.class$Lca$openosp$openo$caisi_integrator$util$CodeType : (NoteIssue.class$Lca$openosp$openo$caisi_integrator$util$CodeType = class$("ca.openosp.openo.caisi_integrator.util.CodeType")), (NoteIssue.class$Ljava$lang$String != null) ? NoteIssue.class$Ljava$lang$String : (NoteIssue.class$Ljava$lang$String = class$("java.lang.String")) };
        NoteIssue.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((NoteIssue.class$Lca$openosp$openo$caisi_integrator$dao$NoteIssue != null) ? NoteIssue.class$Lca$openosp$openo$caisi_integrator$dao$NoteIssue : (NoteIssue.class$Lca$openosp$openo$caisi_integrator$dao$NoteIssue = class$("ca.openosp.openo.caisi_integrator.dao.NoteIssue")), NoteIssue.pcFieldNames, NoteIssue.pcFieldTypes, NoteIssue.pcFieldFlags, NoteIssue.pcPCSuperclass, (String)null, (PersistenceCapable)new NoteIssue());
    }
    
    /**
     * Gets the OpenJPA enhancement contract version for this persistence-capable class.
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }

    /**
     * Reflectively loads a class by name.
     *
     * @param className String the fully qualified class name
     * @return Class the loaded class
     * @throws NoClassDefFoundError if the class cannot be found
     */
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }

    /**
     * Clears all managed fields to their default null values.
     *
     * <p>This method is used by OpenJPA during entity lifecycle management to reset
     * the entity's state.</p>
     */
    protected void pcClearFields() {
        this.codeType = null;
        this.issueCode = null;
    }
    
    /**
     * Creates a new NoteIssue instance with the specified state manager and object ID.
     *
     * <p>This method is used by OpenJPA to create managed instances during entity lifecycle operations.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean if true, clears all fields after construction
     * @return PersistenceCapable the newly created NoteIssue instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final NoteIssue noteIssue = new NoteIssue();
        if (b) {
            noteIssue.pcClearFields();
        }
        noteIssue.pcStateManager = pcStateManager;
        noteIssue.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)noteIssue;
    }

    /**
     * Creates a new NoteIssue instance with the specified state manager.
     *
     * <p>This method is used by OpenJPA to create managed instances during entity lifecycle operations.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean if true, clears all fields after construction
     * @return PersistenceCapable the newly created NoteIssue instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final NoteIssue noteIssue = new NoteIssue();
        if (b) {
            noteIssue.pcClearFields();
        }
        noteIssue.pcStateManager = pcStateManager;
        return (PersistenceCapable)noteIssue;
    }

    /**
     * Gets the total number of managed fields in this persistence-capable class.
     *
     * @return int the number of managed fields (2: codeType and issueCode)
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    /**
     * Replaces a managed field value using the state manager.
     *
     * <p>This method is called by OpenJPA to update field values during persistence operations.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - NoteIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.codeType = (CodeType)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.issueCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple managed field values using the state manager.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a managed field value to the state manager.
     *
     * <p>This method is called by OpenJPA to retrieve field values during persistence operations.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - NoteIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.codeType);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.issueCode);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple managed field values to the state manager.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single field value from another NoteIssue instance.
     *
     * @param noteIssue NoteIssue the source NoteIssue to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final NoteIssue noteIssue, final int n) {
        final int n2 = n - NoteIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.codeType = noteIssue.codeType;
                return;
            }
            case 1: {
                this.issueCode = noteIssue.issueCode;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another NoteIssue instance.
     *
     * @param o Object the source NoteIssue to copy from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final NoteIssue noteIssue = (NoteIssue)o;
        if (noteIssue.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(noteIssue, array[i]);
        }
    }
    
    /**
     * Gets the generic context from the state manager.
     *
     * @return Object the generic context, or null if there is no state manager
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID from the state manager.
     *
     * @return Object the object ID, or null if there is no state manager
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity has been marked for deletion.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since it was loaded or last persisted.
     *
     * @return boolean true if the entity is dirty, false otherwise
     */
    public boolean pcIsDirty() {
        if (this.pcStateManager == null) {
            return false;
        }
        final StateManager pcStateManager = this.pcStateManager;
        RedefinitionHelper.dirtyCheck(pcStateManager);
        return pcStateManager.isDirty();
    }

    /**
     * Checks if this entity is newly created and not yet persisted.
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by JPA).
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is involved in a transaction.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty to trigger persistence on commit.
     *
     * @param s String the field name to mark as dirty
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Gets the state manager for this persistence-capable instance.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version identifier for optimistic locking.
     *
     * @return Object the version identifier, or null if there is no state manager
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the state manager for this persistence-capable instance.
     *
     * @param pcStateManager StateManager the new state manager
     * @throws SecurityException if the state manager replacement is not allowed
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    /**
     * Copies key fields to an object ID using a field supplier.
     *
     * <p>This embeddable has no key fields, so this method has no implementation.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier
     * @param o Object the object ID
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
    }

    /**
     * Copies key fields to an object ID.
     *
     * <p>This embeddable has no key fields, so this method has no implementation.</p>
     *
     * @param o Object the object ID
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * <p>This embeddable has no key fields, so this method has no implementation.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer
     * @param o Object the object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }

    /**
     * Copies key fields from an object ID.
     *
     * <p>This embeddable has no key fields, so this method has no implementation.</p>
     *
     * @param o Object the object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }

    /**
     * Creates a new object ID instance.
     *
     * <p>This embeddable has no object ID, so this method returns null.</p>
     *
     * @return Object null
     */
    public Object pcNewObjectIdInstance() {
        return null;
    }

    /**
     * Creates a new object ID instance from another object.
     *
     * <p>This embeddable has no object ID, so this method returns null.</p>
     *
     * @param o Object the object to create an ID from
     * @return Object null
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }
    
    private static final CodeType pcGetcodeType(final NoteIssue noteIssue) {
        if (noteIssue.pcStateManager == null) {
            return noteIssue.codeType;
        }
        noteIssue.pcStateManager.accessingField(NoteIssue.pcInheritedFieldCount + 0);
        return noteIssue.codeType;
    }
    
    private static final void pcSetcodeType(final NoteIssue noteIssue, final CodeType codeType) {
        if (noteIssue.pcStateManager == null) {
            noteIssue.codeType = codeType;
            return;
        }
        noteIssue.pcStateManager.settingObjectField((PersistenceCapable)noteIssue, NoteIssue.pcInheritedFieldCount + 0, (Object)noteIssue.codeType, (Object)codeType, 0);
    }
    
    private static final String pcGetissueCode(final NoteIssue noteIssue) {
        if (noteIssue.pcStateManager == null) {
            return noteIssue.issueCode;
        }
        noteIssue.pcStateManager.accessingField(NoteIssue.pcInheritedFieldCount + 1);
        return noteIssue.issueCode;
    }
    
    private static final void pcSetissueCode(final NoteIssue noteIssue, final String issueCode) {
        if (noteIssue.pcStateManager == null) {
            noteIssue.issueCode = issueCode;
            return;
        }
        noteIssue.pcStateManager.settingStringField((PersistenceCapable)noteIssue, NoteIssue.pcInheritedFieldCount + 1, noteIssue.issueCode, issueCode, 0);
    }
    
    /**
     * Checks if this entity is detached from the persistence context.
     *
     * @return Boolean true if detached, false if attached, or null if the detached state is indeterminate
     */
    public Boolean pcIsDetached() {
        if (this.pcStateManager != null) {
            if (this.pcStateManager.isDetached()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        else {
            if (this.pcGetDetachedState() != null && this.pcGetDetachedState() != PersistenceCapable.DESERIALIZED) {
                return Boolean.TRUE;
            }
            if (!this.pcisDetachedStateDefinitive()) {
                return null;
            }
            if (this.pcGetDetachedState() == null) {
                return Boolean.FALSE;
            }
            return null;
        }
    }

    /**
     * Checks if the detached state is definitive.
     *
     * @return boolean always returns false for this embeddable
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state object.
     *
     * @return Object the detached state
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }
    
    /**
     * Custom serialization method to handle persistence-capable state.
     *
     * @param objectOutputStream ObjectOutputStream the output stream to write to
     * @throws IOException if an I/O error occurs during serialization
     */
    private void writeObject(final ObjectOutputStream objectOutputStream) throws IOException {
        final boolean pcSerializing = this.pcSerializing();
        objectOutputStream.defaultWriteObject();
        if (pcSerializing) {
            this.pcSetDetachedState(null);
        }
    }

    /**
     * Custom deserialization method to handle persistence-capable state.
     *
     * @param objectInputStream ObjectInputStream the input stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
