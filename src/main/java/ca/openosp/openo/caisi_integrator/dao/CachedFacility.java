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
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Entity;

@Entity
public class CachedFacility extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    private Integer integratorFacilityId;
    @Column(length = 64)
    private String name;
    @Column(length = 255)
    private String description;
    @Column(length = 64)
    private String contactName;
    @Column(length = 64)
    private String contactEmail;
    @Column(length = 64)
    private String contactPhone;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean hic;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean enableIntegratedReferrals;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean allowSims;
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastDataUpdate;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedFacility() {
        this.integratorFacilityId = null;
        this.name = null;
        this.description = null;
        this.contactName = null;
        this.contactEmail = null;
        this.contactPhone = null;
        this.hic = false;
        this.enableIntegratedReferrals = true;
        this.allowSims = true;
        this.lastDataUpdate = null;
    }
    
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }
    
    public String getName() {
        return pcGetname(this);
    }
    
    public void setName(final String name) {
        pcSetname(this, StringUtils.trimToNull(name));
    }
    
    public String getDescription() {
        return pcGetdescription(this);
    }
    
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }
    
    public String getContactName() {
        return pcGetcontactName(this);
    }
    
    public void setContactName(final String contactName) {
        pcSetcontactName(this, StringUtils.trimToNull(contactName));
    }
    
    public String getContactEmail() {
        return pcGetcontactEmail(this);
    }
    
    public void setContactEmail(final String contactEmail) {
        pcSetcontactEmail(this, StringUtils.trimToNull(contactEmail));
    }
    
    public String getContactPhone() {
        return pcGetcontactPhone(this);
    }
    
    public void setContactPhone(final String contactPhone) {
        pcSetcontactPhone(this, StringUtils.trimToNull(contactPhone));
    }
    
    public Date getLastDataUpdate() {
        return pcGetlastDataUpdate(this);
    }
    
    public void setLastDataUpdate(final Date lastDataUpdate) {
        pcSetlastDataUpdate(this, lastDataUpdate);
    }
    
    public boolean isHic() {
        return pcGethic(this);
    }
    
    public void setHic(final boolean hic) {
        pcSethic(this, hic);
    }
    
    public boolean isEnableIntegratedReferrals() {
        return pcGetenableIntegratedReferrals(this);
    }
    
    public boolean isAllowSims() {
        return pcGetallowSims(this);
    }
    
    public void setAllowSims(final boolean allowSims) {
        pcSetallowSims(this, allowSims);
    }
    
    public void setEnableIntegratedReferrals(final boolean enableIntegratedReferrals) {
        pcSetenableIntegratedReferrals(this, enableIntegratedReferrals);
    }
    
    @Override
    public Integer getId() {
        return pcGetintegratorFacilityId(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -2517194288896303617L;
        CachedFacility.pcFieldNames = new String[] { "allowSims", "contactEmail", "contactName", "contactPhone", "description", "enableIntegratedReferrals", "hic", "integratorFacilityId", "lastDataUpdate", "name" };
        CachedFacility.pcFieldTypes = new Class[] { Boolean.TYPE, (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, Boolean.TYPE, (CachedFacility.class$Ljava$lang$Integer != null) ? CachedFacility.class$Ljava$lang$Integer : (CachedFacility.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedFacility.class$Ljava$util$Date != null) ? CachedFacility.class$Ljava$util$Date : (CachedFacility.class$Ljava$util$Date = class$("java.util.Date")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedFacility.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility != null) ? CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility : (CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility = class$("ca.openosp.openo.caisi_integrator.dao.CachedFacility")), CachedFacility.pcFieldNames, CachedFacility.pcFieldTypes, CachedFacility.pcFieldFlags, CachedFacility.pcPCSuperclass, "CachedFacility", (PersistenceCapable)new CachedFacility());
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
        this.allowSims = false;
        this.contactEmail = null;
        this.contactName = null;
        this.contactPhone = null;
        this.description = null;
        this.enableIntegratedReferrals = false;
        this.hic = false;
        this.integratorFacilityId = null;
        this.lastDataUpdate = null;
        this.name = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedFacility cachedFacility = new CachedFacility();
        if (b) {
            cachedFacility.pcClearFields();
        }
        cachedFacility.pcStateManager = pcStateManager;
        cachedFacility.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedFacility;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedFacility cachedFacility = new CachedFacility();
        if (b) {
            cachedFacility.pcClearFields();
        }
        cachedFacility.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedFacility;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 10;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedFacility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.allowSims = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.contactEmail = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.contactName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.contactPhone = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.enableIntegratedReferrals = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.hic = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.lastDataUpdate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedFacility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.allowSims);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contactEmail);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contactName);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contactPhone);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 5: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.enableIntegratedReferrals);
                return;
            }
            case 6: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.hic);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastDataUpdate);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
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
    
    protected void pcCopyField(final CachedFacility cachedFacility, final int n) {
        final int n2 = n - CachedFacility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.allowSims = cachedFacility.allowSims;
                return;
            }
            case 1: {
                this.contactEmail = cachedFacility.contactEmail;
                return;
            }
            case 2: {
                this.contactName = cachedFacility.contactName;
                return;
            }
            case 3: {
                this.contactPhone = cachedFacility.contactPhone;
                return;
            }
            case 4: {
                this.description = cachedFacility.description;
                return;
            }
            case 5: {
                this.enableIntegratedReferrals = cachedFacility.enableIntegratedReferrals;
                return;
            }
            case 6: {
                this.hic = cachedFacility.hic;
                return;
            }
            case 7: {
                this.integratorFacilityId = cachedFacility.integratorFacilityId;
                return;
            }
            case 8: {
                this.lastDataUpdate = cachedFacility.lastDataUpdate;
                return;
            }
            case 9: {
                this.name = cachedFacility.name;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedFacility cachedFacility = (CachedFacility)o;
        if (cachedFacility.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedFacility, array[i]);
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
        fieldConsumer.storeObjectField(7 + CachedFacility.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.integratorFacilityId = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility != null) ? CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility : (CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility = class$("ca.openosp.openo.caisi_integrator.dao.CachedFacility")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility != null) ? CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility : (CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility = class$("ca.openosp.openo.caisi_integrator.dao.CachedFacility")), this.integratorFacilityId);
    }
    
    private static final boolean pcGetallowSims(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.allowSims;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 0);
        return cachedFacility.allowSims;
    }
    
    private static final void pcSetallowSims(final CachedFacility cachedFacility, final boolean allowSims) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.allowSims = allowSims;
            return;
        }
        cachedFacility.pcStateManager.settingBooleanField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 0, cachedFacility.allowSims, allowSims, 0);
    }
    
    private static final String pcGetcontactEmail(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.contactEmail;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 1);
        return cachedFacility.contactEmail;
    }
    
    private static final void pcSetcontactEmail(final CachedFacility cachedFacility, final String contactEmail) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.contactEmail = contactEmail;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 1, cachedFacility.contactEmail, contactEmail, 0);
    }
    
    private static final String pcGetcontactName(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.contactName;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 2);
        return cachedFacility.contactName;
    }
    
    private static final void pcSetcontactName(final CachedFacility cachedFacility, final String contactName) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.contactName = contactName;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 2, cachedFacility.contactName, contactName, 0);
    }
    
    private static final String pcGetcontactPhone(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.contactPhone;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 3);
        return cachedFacility.contactPhone;
    }
    
    private static final void pcSetcontactPhone(final CachedFacility cachedFacility, final String contactPhone) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.contactPhone = contactPhone;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 3, cachedFacility.contactPhone, contactPhone, 0);
    }
    
    private static final String pcGetdescription(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.description;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 4);
        return cachedFacility.description;
    }
    
    private static final void pcSetdescription(final CachedFacility cachedFacility, final String description) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.description = description;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 4, cachedFacility.description, description, 0);
    }
    
    private static final boolean pcGetenableIntegratedReferrals(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.enableIntegratedReferrals;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 5);
        return cachedFacility.enableIntegratedReferrals;
    }
    
    private static final void pcSetenableIntegratedReferrals(final CachedFacility cachedFacility, final boolean enableIntegratedReferrals) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.enableIntegratedReferrals = enableIntegratedReferrals;
            return;
        }
        cachedFacility.pcStateManager.settingBooleanField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 5, cachedFacility.enableIntegratedReferrals, enableIntegratedReferrals, 0);
    }
    
    private static final boolean pcGethic(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.hic;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 6);
        return cachedFacility.hic;
    }
    
    private static final void pcSethic(final CachedFacility cachedFacility, final boolean hic) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.hic = hic;
            return;
        }
        cachedFacility.pcStateManager.settingBooleanField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 6, cachedFacility.hic, hic, 0);
    }
    
    private static final Integer pcGetintegratorFacilityId(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.integratorFacilityId;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 7);
        return cachedFacility.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final CachedFacility cachedFacility, final Integer integratorFacilityId) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.integratorFacilityId = integratorFacilityId;
            return;
        }
        cachedFacility.pcStateManager.settingObjectField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 7, (Object)cachedFacility.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final Date pcGetlastDataUpdate(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.lastDataUpdate;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 8);
        return cachedFacility.lastDataUpdate;
    }
    
    private static final void pcSetlastDataUpdate(final CachedFacility cachedFacility, final Date lastDataUpdate) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.lastDataUpdate = lastDataUpdate;
            return;
        }
        cachedFacility.pcStateManager.settingObjectField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 8, (Object)cachedFacility.lastDataUpdate, (Object)lastDataUpdate, 0);
    }
    
    private static final String pcGetname(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.name;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 9);
        return cachedFacility.name;
    }
    
    private static final void pcSetname(final CachedFacility cachedFacility, final String name) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.name = name;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 9, cachedFacility.name, name, 0);
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
