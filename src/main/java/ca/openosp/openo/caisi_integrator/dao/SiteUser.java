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
import java.util.Arrays;
import ca.openosp.openo.caisi_integrator.util.EncryptionUtils;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;
import ca.openosp.openo.caisi_integrator.util.Named;

@Entity
public class SiteUser extends AbstractModel<Integer> implements Named, PersistenceCapable
{
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
    static /* synthetic */ Class class$Ljava$util$Calendar;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$SiteUser;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public SiteUser() {
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
    }
    
    public boolean checkPassword(final String password) {
        return password != null && (Arrays.equals(pcGetpassword(this), password.getBytes()) || Arrays.equals(pcGetpassword(this), EncryptionUtils.getSha1(password)));
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
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 8746124247439157163L;
        SiteUser.pcFieldNames = new String[] { "disabled", "id", "lastLogin", "name", "password" };
        SiteUser.pcFieldTypes = new Class[] { Boolean.TYPE, (SiteUser.class$Ljava$lang$Integer != null) ? SiteUser.class$Ljava$lang$Integer : (SiteUser.class$Ljava$lang$Integer = class$("java.lang.Integer")), (SiteUser.class$Ljava$util$Calendar != null) ? SiteUser.class$Ljava$util$Calendar : (SiteUser.class$Ljava$util$Calendar = class$("java.util.Calendar")), (SiteUser.class$Ljava$lang$String != null) ? SiteUser.class$Ljava$lang$String : (SiteUser.class$Ljava$lang$String = class$("java.lang.String")), (SiteUser.class$L$B != null) ? SiteUser.class$L$B : (SiteUser.class$L$B = class$("[B")) };
        SiteUser.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser != null) ? SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser : (SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser = class$("ca.openosp.openo.caisi_integrator.dao.SiteUser")), SiteUser.pcFieldNames, SiteUser.pcFieldTypes, SiteUser.pcFieldFlags, SiteUser.pcPCSuperclass, "SiteUser", (PersistenceCapable)new SiteUser());
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
        final SiteUser siteUser = new SiteUser();
        if (b) {
            siteUser.pcClearFields();
        }
        siteUser.pcStateManager = pcStateManager;
        siteUser.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)siteUser;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final SiteUser siteUser = new SiteUser();
        if (b) {
            siteUser.pcClearFields();
        }
        siteUser.pcStateManager = pcStateManager;
        return (PersistenceCapable)siteUser;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 5;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - SiteUser.pcInheritedFieldCount;
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
        final int n2 = n - SiteUser.pcInheritedFieldCount;
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
    
    protected void pcCopyField(final SiteUser siteUser, final int n) {
        final int n2 = n - SiteUser.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.disabled = siteUser.disabled;
                return;
            }
            case 1: {
                this.id = siteUser.id;
                return;
            }
            case 2: {
                this.lastLogin = siteUser.lastLogin;
                return;
            }
            case 3: {
                this.name = siteUser.name;
                return;
            }
            case 4: {
                this.password = siteUser.password;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final SiteUser siteUser = (SiteUser)o;
        if (siteUser.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(siteUser, array[i]);
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
        fieldConsumer.storeObjectField(1 + SiteUser.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser != null) ? SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser : (SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser = class$("ca.openosp.openo.caisi_integrator.dao.SiteUser")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser != null) ? SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser : (SiteUser.class$Lca$openosp$openo$caisi_integrator$dao$SiteUser = class$("ca.openosp.openo.caisi_integrator.dao.SiteUser")), this.id);
    }
    
    private static final boolean pcGetdisabled(final SiteUser siteUser) {
        if (siteUser.pcStateManager == null) {
            return siteUser.disabled;
        }
        siteUser.pcStateManager.accessingField(SiteUser.pcInheritedFieldCount + 0);
        return siteUser.disabled;
    }
    
    private static final void pcSetdisabled(final SiteUser siteUser, final boolean disabled) {
        if (siteUser.pcStateManager == null) {
            siteUser.disabled = disabled;
            return;
        }
        siteUser.pcStateManager.settingBooleanField((PersistenceCapable)siteUser, SiteUser.pcInheritedFieldCount + 0, siteUser.disabled, disabled, 0);
    }
    
    private static final Integer pcGetid(final SiteUser siteUser) {
        if (siteUser.pcStateManager == null) {
            return siteUser.id;
        }
        siteUser.pcStateManager.accessingField(SiteUser.pcInheritedFieldCount + 1);
        return siteUser.id;
    }
    
    private static final void pcSetid(final SiteUser siteUser, final Integer id) {
        if (siteUser.pcStateManager == null) {
            siteUser.id = id;
            return;
        }
        siteUser.pcStateManager.settingObjectField((PersistenceCapable)siteUser, SiteUser.pcInheritedFieldCount + 1, (Object)siteUser.id, (Object)id, 0);
    }
    
    private static final Calendar pcGetlastLogin(final SiteUser siteUser) {
        if (siteUser.pcStateManager == null) {
            return siteUser.lastLogin;
        }
        siteUser.pcStateManager.accessingField(SiteUser.pcInheritedFieldCount + 2);
        return siteUser.lastLogin;
    }
    
    private static final void pcSetlastLogin(final SiteUser siteUser, final Calendar lastLogin) {
        if (siteUser.pcStateManager == null) {
            siteUser.lastLogin = lastLogin;
            return;
        }
        siteUser.pcStateManager.settingObjectField((PersistenceCapable)siteUser, SiteUser.pcInheritedFieldCount + 2, (Object)siteUser.lastLogin, (Object)lastLogin, 0);
    }
    
    private static final String pcGetname(final SiteUser siteUser) {
        if (siteUser.pcStateManager == null) {
            return siteUser.name;
        }
        siteUser.pcStateManager.accessingField(SiteUser.pcInheritedFieldCount + 3);
        return siteUser.name;
    }
    
    private static final void pcSetname(final SiteUser siteUser, final String name) {
        if (siteUser.pcStateManager == null) {
            siteUser.name = name;
            return;
        }
        siteUser.pcStateManager.settingStringField((PersistenceCapable)siteUser, SiteUser.pcInheritedFieldCount + 3, siteUser.name, name, 0);
    }
    
    private static final byte[] pcGetpassword(final SiteUser siteUser) {
        if (siteUser.pcStateManager == null) {
            return siteUser.password;
        }
        siteUser.pcStateManager.accessingField(SiteUser.pcInheritedFieldCount + 4);
        return siteUser.password;
    }
    
    private static final void pcSetpassword(final SiteUser siteUser, final byte[] password) {
        if (siteUser.pcStateManager == null) {
            siteUser.password = password;
            return;
        }
        siteUser.pcStateManager.settingObjectField((PersistenceCapable)siteUser, SiteUser.pcInheritedFieldCount + 4, (Object)siteUser.password, (Object)password, 0);
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
