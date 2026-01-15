package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.IntId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;
import ca.openosp.openo.caisi_integrator.util.EncryptionUtils;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import org.apache.log4j.Logger;
import javax.persistence.Entity;
import ca.openosp.openo.caisi_integrator.util.Named;

@Entity(name = "IntegratorFacility")
public class Facility extends AbstractModel<Integer> implements Named, PersistenceCapable
{
    private static final long serialVersionUID = 1L;
    private static Logger logger;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 32, unique = true)
    private String name;
    @Column(nullable = false, columnDefinition = "tinyblob")
    private byte[] password;
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar lastLogin;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean disabled;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$util$GregorianCalendar;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$Facility;
    private transient Object pcDetachedState;
    
    public Facility() {
        this.id = null;
        this.name = null;
        this.password = null;
        this.lastLogin = null;
        this.disabled = false;
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public String getName() {
        return pcGetname(this);
    }
    
    public void setName(final String name) {
        pcSetname(this, MiscUtils.validateAndNormaliseUserName(name));
    }
    
    public void setPassword(final String password) {
        if (password == null) {
            throw new IllegalArgumentException("password can't be null");
        }
        pcSetpassword(this, EncryptionUtils.getSha1(password));
        if (Facility.logger.isDebugEnabled()) {
            Facility.logger.debug((Object)("setPassword provided pw : " + password));
            Facility.logger.debug((Object)("setPassword provided pw enc : " + Arrays.toString(EncryptionUtils.getSha1(password))));
        }
    }
    
    public String getPassword() {
        return null;
    }
    
    public String getPasswordAsBase64() {
        return DatatypeConverter.printBase64Binary(pcGetpassword(this));
    }
    
    public boolean checkPassword(final String password) {
        if (Facility.logger.isDebugEnabled()) {
            Facility.logger.debug((Object)("provided pw : " + password));
            Facility.logger.debug((Object)("db pw : " + Arrays.toString(pcGetpassword(this))));
            Facility.logger.debug((Object)("provided pw enc : " + Arrays.toString(EncryptionUtils.getSha1(password))));
        }
        return password != null && Arrays.equals(pcGetpassword(this), EncryptionUtils.getSha1(password));
    }
    
    public Calendar getLastLogin() {
        return pcGetlastLogin(this);
    }
    
    public void setLastLogin(final Calendar lastLogin) {
        pcSetlastLogin(this, lastLogin);
    }
    
    public boolean isDisabled() {
        return pcGetdisabled(this);
    }
    
    public void setDisabled(final boolean disabled) {
        pcSetdisabled(this, disabled);
    }
    
    static {
        Facility.logger = MiscUtils.getLogger();
        Facility.pcFieldNames = new String[] { "disabled", "id", "lastLogin", "name", "password" };
        Facility.pcFieldTypes = new Class[] { Boolean.TYPE, (Facility.class$Ljava$lang$Integer != null) ? Facility.class$Ljava$lang$Integer : (Facility.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Facility.class$Ljava$util$GregorianCalendar != null) ? Facility.class$Ljava$util$GregorianCalendar : (Facility.class$Ljava$util$GregorianCalendar = class$("java.util.GregorianCalendar")), (Facility.class$Ljava$lang$String != null) ? Facility.class$Ljava$lang$String : (Facility.class$Ljava$lang$String = class$("java.lang.String")), (Facility.class$L$B != null) ? Facility.class$L$B : (Facility.class$L$B = class$("[B")) };
        Facility.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility != null) ? Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility : (Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility = class$("ca.openosp.openo.caisi_integrator.dao.Facility")), Facility.pcFieldNames, Facility.pcFieldTypes, Facility.pcFieldFlags, Facility.pcPCSuperclass, "Facility", (PersistenceCapable)new Facility());
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }
    
    protected void pcClearFields() {
        this.disabled = false;
        this.id = null;
        this.lastLogin = null;
        this.name = null;
        this.password = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final Facility facility = new Facility();
        if (b) {
            facility.pcClearFields();
        }
        facility.pcStateManager = pcStateManager;
        facility.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facility;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final Facility facility = new Facility();
        if (b) {
            facility.pcClearFields();
        }
        facility.pcStateManager = pcStateManager;
        return (PersistenceCapable)facility;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 5;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - Facility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.disabled = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.lastLogin = (Calendar)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.password = (byte[])this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    public void pcProvideField(final int n) {
        final int n2 = n - Facility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.disabled);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastLogin);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.password);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final Facility facility, final int n) {
        final int n2 = n - Facility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.disabled = facility.disabled;
                return;
            }
            case 1: {
                this.id = facility.id;
                return;
            }
            case 2: {
                this.lastLogin = facility.lastLogin;
                return;
            }
            case 3: {
                this.name = facility.name;
                return;
            }
            case 4: {
                this.password = facility.password;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final Facility facility = (Facility)o;
        if (facility.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facility, array[i]);
        }
    }
    
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }
    
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }
    
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    public boolean pcIsDirty() {
        if (this.pcStateManager == null) {
            return false;
        }
        final StateManager pcStateManager = this.pcStateManager;
        RedefinitionHelper.dirtyCheck(pcStateManager);
        return pcStateManager.isDirty();
    }
    
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }
    
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }
    
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }
    
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }
    
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }
    
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }
    
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(1 + Facility.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility != null) ? Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility : (Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility = class$("ca.openosp.openo.caisi_integrator.dao.Facility")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility != null) ? Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility : (Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility = class$("ca.openosp.openo.caisi_integrator.dao.Facility")), this.id);
    }
    
    private static final boolean pcGetdisabled(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.disabled;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 0);
        return facility.disabled;
    }
    
    private static final void pcSetdisabled(final Facility facility, final boolean disabled) {
        if (facility.pcStateManager == null) {
            facility.disabled = disabled;
            return;
        }
        facility.pcStateManager.settingBooleanField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 0, facility.disabled, disabled, 0);
    }
    
    private static final Integer pcGetid(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.id;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 1);
        return facility.id;
    }
    
    private static final void pcSetid(final Facility facility, final Integer id) {
        if (facility.pcStateManager == null) {
            facility.id = id;
            return;
        }
        facility.pcStateManager.settingObjectField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 1, (Object)facility.id, (Object)id, 0);
    }
    
    private static final Calendar pcGetlastLogin(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.lastLogin;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 2);
        return facility.lastLogin;
    }
    
    private static final void pcSetlastLogin(final Facility facility, final Calendar lastLogin) {
        if (facility.pcStateManager == null) {
            facility.lastLogin = lastLogin;
            return;
        }
        facility.pcStateManager.settingObjectField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 2, (Object)facility.lastLogin, (Object)lastLogin, 0);
    }
    
    private static final String pcGetname(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.name;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 3);
        return facility.name;
    }
    
    private static final void pcSetname(final Facility facility, final String name) {
        if (facility.pcStateManager == null) {
            facility.name = name;
            return;
        }
        facility.pcStateManager.settingStringField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 3, facility.name, name, 0);
    }
    
    private static final byte[] pcGetpassword(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.password;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 4);
        return facility.password;
    }
    
    private static final void pcSetpassword(final Facility facility, final byte[] password) {
        if (facility.pcStateManager == null) {
            facility.password = password;
            return;
        }
        facility.pcStateManager.settingObjectField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 4, (Object)facility.password, (Object)password, 0);
    }
    
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
            if (this.id != null) {
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
    
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }
    
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }
    
    private void writeObject(final ObjectOutputStream objectOutputStream) throws IOException {
        final boolean pcSerializing = this.pcSerializing();
        objectOutputStream.defaultWriteObject();
        if (pcSerializing) {
            this.pcSetDetachedState(null);
        }
    }
    
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
