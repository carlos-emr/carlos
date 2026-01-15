package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.ObjectId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedDemographic extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedDemographic>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityDemographicPk;
    private String firstName;
    private String lastName;
    @Temporal(TemporalType.DATE)
    private Date birthDate;
    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private Gender gender;
    @Column(length = 32)
    private String hin;
    @Column(length = 32)
    private String hinType;
    @Column(length = 8)
    private String hinVersion;
    @Temporal(TemporalType.DATE)
    private Date hinValidStart;
    @Temporal(TemporalType.DATE)
    private Date hinValidEnd;
    @Column(length = 32)
    private String sin;
    @Column(length = 4)
    private String province;
    @Column(length = 128)
    private String city;
    @Column(length = 16)
    private String caisiProviderId;
    @Column(length = 32)
    private String idHash;
    @Column(length = 128)
    private String streetAddress;
    @Column(length = 64)
    private String phone1;
    @Column(length = 64)
    private String phone2;
    @Column(length = 16)
    private String lastUpdateUser;
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdateDate;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedDemographic() {
        this.firstName = null;
        this.lastName = null;
        this.birthDate = null;
        this.gender = null;
        this.hin = null;
        this.hinType = null;
        this.hinVersion = null;
        this.hinValidStart = null;
        this.hinValidEnd = null;
        this.sin = null;
        this.province = null;
        this.city = null;
        this.caisiProviderId = null;
        this.idHash = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityDemographicPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityDemographicPk) {
        pcSetfacilityDemographicPk(this, facilityDemographicPk);
    }
    
    public String getFirstName() {
        return pcGetfirstName(this);
    }
    
    public void setFirstName(final String firstName) {
        pcSetfirstName(this, StringUtils.trimToNull(firstName));
    }
    
    public String getLastName() {
        return pcGetlastName(this);
    }
    
    public void setLastName(final String lastName) {
        pcSetlastName(this, StringUtils.trimToNull(lastName));
    }
    
    public Date getBirthDate() {
        return pcGetbirthDate(this);
    }
    
    public void setBirthDate(final Date birthDate) {
        pcSetbirthDate(this, birthDate);
    }
    
    public Gender getGender() {
        return pcGetgender(this);
    }
    
    public void setGender(final Gender gender) {
        pcSetgender(this, gender);
    }
    
    public String getHin() {
        return pcGethin(this);
    }
    
    public void setHin(final String hin) {
        pcSethin(this, MiscUtils.trimToNullLowerCase(hin));
    }
    
    public String getHinVersion() {
        return pcGethinVersion(this);
    }
    
    public void setHinVersion(final String hinVersion) {
        pcSethinVersion(this, StringUtils.trimToNull(hinVersion));
    }
    
    public String getSin() {
        return pcGetsin(this);
    }
    
    public void setSin(final String sin) {
        pcSetsin(this, MiscUtils.trimToNullLowerCase(sin));
    }
    
    public String getProvince() {
        return pcGetprovince(this);
    }
    
    public void setProvince(final String province) {
        pcSetprovince(this, MiscUtils.trimToNullLowerCase(province));
    }
    
    public String getCity() {
        return pcGetcity(this);
    }
    
    public void setCity(final String city) {
        pcSetcity(this, MiscUtils.trimToNullLowerCase(city));
    }
    
    public String getHinType() {
        return pcGethinType(this);
    }
    
    public void setHinType(final String hinType) {
        pcSethinType(this, StringUtils.trimToNull(hinType));
    }
    
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }
    
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, StringUtils.trimToNull(caisiProviderId));
    }
    
    public String getIdHash() {
        return pcGetidHash(this);
    }
    
    public void setIdHash(final String idHash) {
        pcSetidHash(this, StringUtils.trimToNull(idHash));
    }
    
    public String getStreetAddress() {
        return pcGetstreetAddress(this);
    }
    
    public void setStreetAddress(final String streetAddress) {
        pcSetstreetAddress(this, streetAddress);
    }
    
    public String getPhone1() {
        return pcGetphone1(this);
    }
    
    public void setPhone1(final String phone1) {
        pcSetphone1(this, phone1);
    }
    
    public String getPhone2() {
        return pcGetphone2(this);
    }
    
    public void setPhone2(final String phone2) {
        pcSetphone2(this, phone2);
    }
    
    public Date getHinValidStart() {
        return pcGethinValidStart(this);
    }
    
    public void setHinValidStart(final Date hinValidStart) {
        pcSethinValidStart(this, hinValidStart);
    }
    
    public Date getHinValidEnd() {
        return pcGethinValidEnd(this);
    }
    
    public void setHinValidEnd(final Date hinValidEnd) {
        pcSethinValidEnd(this, hinValidEnd);
    }
    
    @Override
    public int compareTo(final CachedDemographic o) {
        return pcGetfacilityDemographicPk(this).getCaisiItemId() - pcGetfacilityDemographicPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityDemographicPk(this);
    }
    
    public String getLastUpdateUser() {
        return pcGetlastUpdateUser(this);
    }
    
    public void setLastUpdateUser(final String lastUpdateUser) {
        pcSetlastUpdateUser(this, lastUpdateUser);
    }
    
    public Date getLastUpdateDate() {
        return pcGetlastUpdateDate(this);
    }
    
    public void setLastUpdateDate(final Date lastUpdateDate) {
        pcSetlastUpdateDate(this, lastUpdateDate);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3212280391303991557L;
        CachedDemographic.pcFieldNames = new String[] { "birthDate", "caisiProviderId", "city", "facilityDemographicPk", "firstName", "gender", "hin", "hinType", "hinValidEnd", "hinValidStart", "hinVersion", "idHash", "lastName", "lastUpdateDate", "lastUpdateUser", "phone1", "phone2", "province", "sin", "streetAddress" };
        CachedDemographic.pcFieldTypes = new Class[] { (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic$Gender")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographic.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic")), CachedDemographic.pcFieldNames, CachedDemographic.pcFieldTypes, CachedDemographic.pcFieldFlags, CachedDemographic.pcPCSuperclass, "CachedDemographic", (PersistenceCapable)new CachedDemographic());
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
        this.birthDate = null;
        this.caisiProviderId = null;
        this.city = null;
        this.facilityDemographicPk = null;
        this.firstName = null;
        this.gender = null;
        this.hin = null;
        this.hinType = null;
        this.hinValidEnd = null;
        this.hinValidStart = null;
        this.hinVersion = null;
        this.idHash = null;
        this.lastName = null;
        this.lastUpdateDate = null;
        this.lastUpdateUser = null;
        this.phone1 = null;
        this.phone2 = null;
        this.province = null;
        this.sin = null;
        this.streetAddress = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographic cachedDemographic = new CachedDemographic();
        if (b) {
            cachedDemographic.pcClearFields();
        }
        cachedDemographic.pcStateManager = pcStateManager;
        cachedDemographic.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographic;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographic cachedDemographic = new CachedDemographic();
        if (b) {
            cachedDemographic.pcClearFields();
        }
        cachedDemographic.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographic;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 20;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographic.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.birthDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.city = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.facilityDemographicPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.firstName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.gender = (Gender)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.hin = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.hinType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.hinValidEnd = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.hinValidStart = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.hinVersion = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.idHash = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.lastName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.lastUpdateDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.lastUpdateUser = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.phone1 = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.phone2 = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.province = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.sin = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 19: {
                this.streetAddress = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedDemographic.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.birthDate);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.city);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDemographicPk);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.firstName);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.gender);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.hin);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.hinType);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.hinValidEnd);
                return;
            }
            case 9: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.hinValidStart);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.hinVersion);
                return;
            }
            case 11: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.idHash);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lastName);
                return;
            }
            case 13: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastUpdateDate);
                return;
            }
            case 14: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lastUpdateUser);
                return;
            }
            case 15: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.phone1);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.phone2);
                return;
            }
            case 17: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.province);
                return;
            }
            case 18: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.sin);
                return;
            }
            case 19: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.streetAddress);
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
    
    protected void pcCopyField(final CachedDemographic cachedDemographic, final int n) {
        final int n2 = n - CachedDemographic.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.birthDate = cachedDemographic.birthDate;
                return;
            }
            case 1: {
                this.caisiProviderId = cachedDemographic.caisiProviderId;
                return;
            }
            case 2: {
                this.city = cachedDemographic.city;
                return;
            }
            case 3: {
                this.facilityDemographicPk = cachedDemographic.facilityDemographicPk;
                return;
            }
            case 4: {
                this.firstName = cachedDemographic.firstName;
                return;
            }
            case 5: {
                this.gender = cachedDemographic.gender;
                return;
            }
            case 6: {
                this.hin = cachedDemographic.hin;
                return;
            }
            case 7: {
                this.hinType = cachedDemographic.hinType;
                return;
            }
            case 8: {
                this.hinValidEnd = cachedDemographic.hinValidEnd;
                return;
            }
            case 9: {
                this.hinValidStart = cachedDemographic.hinValidStart;
                return;
            }
            case 10: {
                this.hinVersion = cachedDemographic.hinVersion;
                return;
            }
            case 11: {
                this.idHash = cachedDemographic.idHash;
                return;
            }
            case 12: {
                this.lastName = cachedDemographic.lastName;
                return;
            }
            case 13: {
                this.lastUpdateDate = cachedDemographic.lastUpdateDate;
                return;
            }
            case 14: {
                this.lastUpdateUser = cachedDemographic.lastUpdateUser;
                return;
            }
            case 15: {
                this.phone1 = cachedDemographic.phone1;
                return;
            }
            case 16: {
                this.phone2 = cachedDemographic.phone2;
                return;
            }
            case 17: {
                this.province = cachedDemographic.province;
                return;
            }
            case 18: {
                this.sin = cachedDemographic.sin;
                return;
            }
            case 19: {
                this.streetAddress = cachedDemographic.streetAddress;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographic cachedDemographic = (CachedDemographic)o;
        if (cachedDemographic.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographic, array[i]);
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
        fieldConsumer.storeObjectField(3 + CachedDemographic.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDemographicPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographic\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic")), (Object)this.facilityDemographicPk);
    }
    
    private static final Date pcGetbirthDate(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.birthDate;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 0);
        return cachedDemographic.birthDate;
    }
    
    private static final void pcSetbirthDate(final CachedDemographic cachedDemographic, final Date birthDate) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.birthDate = birthDate;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 0, (Object)cachedDemographic.birthDate, (Object)birthDate, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.caisiProviderId;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 1);
        return cachedDemographic.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedDemographic cachedDemographic, final String caisiProviderId) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.caisiProviderId = caisiProviderId;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 1, cachedDemographic.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final String pcGetcity(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.city;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 2);
        return cachedDemographic.city;
    }
    
    private static final void pcSetcity(final CachedDemographic cachedDemographic, final String city) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.city = city;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 2, cachedDemographic.city, city, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityDemographicPk(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.facilityDemographicPk;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 3);
        return cachedDemographic.facilityDemographicPk;
    }
    
    private static final void pcSetfacilityDemographicPk(final CachedDemographic cachedDemographic, final FacilityIdIntegerCompositePk facilityDemographicPk) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.facilityDemographicPk = facilityDemographicPk;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 3, (Object)cachedDemographic.facilityDemographicPk, (Object)facilityDemographicPk, 0);
    }
    
    private static final String pcGetfirstName(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.firstName;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 4);
        return cachedDemographic.firstName;
    }
    
    private static final void pcSetfirstName(final CachedDemographic cachedDemographic, final String firstName) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.firstName = firstName;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 4, cachedDemographic.firstName, firstName, 0);
    }
    
    private static final Gender pcGetgender(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.gender;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 5);
        return cachedDemographic.gender;
    }
    
    private static final void pcSetgender(final CachedDemographic cachedDemographic, final Gender gender) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.gender = gender;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 5, (Object)cachedDemographic.gender, (Object)gender, 0);
    }
    
    private static final String pcGethin(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hin;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 6);
        return cachedDemographic.hin;
    }
    
    private static final void pcSethin(final CachedDemographic cachedDemographic, final String hin) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hin = hin;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 6, cachedDemographic.hin, hin, 0);
    }
    
    private static final String pcGethinType(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinType;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 7);
        return cachedDemographic.hinType;
    }
    
    private static final void pcSethinType(final CachedDemographic cachedDemographic, final String hinType) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinType = hinType;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 7, cachedDemographic.hinType, hinType, 0);
    }
    
    private static final Date pcGethinValidEnd(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinValidEnd;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 8);
        return cachedDemographic.hinValidEnd;
    }
    
    private static final void pcSethinValidEnd(final CachedDemographic cachedDemographic, final Date hinValidEnd) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinValidEnd = hinValidEnd;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 8, (Object)cachedDemographic.hinValidEnd, (Object)hinValidEnd, 0);
    }
    
    private static final Date pcGethinValidStart(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinValidStart;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 9);
        return cachedDemographic.hinValidStart;
    }
    
    private static final void pcSethinValidStart(final CachedDemographic cachedDemographic, final Date hinValidStart) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinValidStart = hinValidStart;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 9, (Object)cachedDemographic.hinValidStart, (Object)hinValidStart, 0);
    }
    
    private static final String pcGethinVersion(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinVersion;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 10);
        return cachedDemographic.hinVersion;
    }
    
    private static final void pcSethinVersion(final CachedDemographic cachedDemographic, final String hinVersion) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinVersion = hinVersion;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 10, cachedDemographic.hinVersion, hinVersion, 0);
    }
    
    private static final String pcGetidHash(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.idHash;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 11);
        return cachedDemographic.idHash;
    }
    
    private static final void pcSetidHash(final CachedDemographic cachedDemographic, final String idHash) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.idHash = idHash;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 11, cachedDemographic.idHash, idHash, 0);
    }
    
    private static final String pcGetlastName(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.lastName;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 12);
        return cachedDemographic.lastName;
    }
    
    private static final void pcSetlastName(final CachedDemographic cachedDemographic, final String lastName) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.lastName = lastName;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 12, cachedDemographic.lastName, lastName, 0);
    }
    
    private static final Date pcGetlastUpdateDate(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.lastUpdateDate;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 13);
        return cachedDemographic.lastUpdateDate;
    }
    
    private static final void pcSetlastUpdateDate(final CachedDemographic cachedDemographic, final Date lastUpdateDate) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.lastUpdateDate = lastUpdateDate;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 13, (Object)cachedDemographic.lastUpdateDate, (Object)lastUpdateDate, 0);
    }
    
    private static final String pcGetlastUpdateUser(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.lastUpdateUser;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 14);
        return cachedDemographic.lastUpdateUser;
    }
    
    private static final void pcSetlastUpdateUser(final CachedDemographic cachedDemographic, final String lastUpdateUser) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.lastUpdateUser = lastUpdateUser;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 14, cachedDemographic.lastUpdateUser, lastUpdateUser, 0);
    }
    
    private static final String pcGetphone1(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.phone1;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 15);
        return cachedDemographic.phone1;
    }
    
    private static final void pcSetphone1(final CachedDemographic cachedDemographic, final String phone1) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.phone1 = phone1;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 15, cachedDemographic.phone1, phone1, 0);
    }
    
    private static final String pcGetphone2(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.phone2;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 16);
        return cachedDemographic.phone2;
    }
    
    private static final void pcSetphone2(final CachedDemographic cachedDemographic, final String phone2) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.phone2 = phone2;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 16, cachedDemographic.phone2, phone2, 0);
    }
    
    private static final String pcGetprovince(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.province;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 17);
        return cachedDemographic.province;
    }
    
    private static final void pcSetprovince(final CachedDemographic cachedDemographic, final String province) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.province = province;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 17, cachedDemographic.province, province, 0);
    }
    
    private static final String pcGetsin(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.sin;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 18);
        return cachedDemographic.sin;
    }
    
    private static final void pcSetsin(final CachedDemographic cachedDemographic, final String sin) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.sin = sin;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 18, cachedDemographic.sin, sin, 0);
    }
    
    private static final String pcGetstreetAddress(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.streetAddress;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 19);
        return cachedDemographic.streetAddress;
    }
    
    private static final void pcSetstreetAddress(final CachedDemographic cachedDemographic, final String streetAddress) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.streetAddress = streetAddress;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 19, cachedDemographic.streetAddress, streetAddress, 0);
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
    
    public enum Gender
    {
        M, 
        F, 
        T, 
        O, 
        U;
    }
}
