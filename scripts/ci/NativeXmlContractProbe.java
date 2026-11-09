/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.xml.namespace.QName;
import javax.xml.datatype.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.stream.*;
import jakarta.xml.bind.*;
import org.glassfish.jaxb.runtime.v2.model.runtime.*;
import org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl;
import org.apache.cxf.message.*;
import org.apache.cxf.phase.*;

/** Same native fixture producer, run unchanged with each verified binary's own classes/libraries. */
public final class NativeXmlContractProbe {
    private static final String BASE="io.github.carlos_emr.carlos.";
    private static final TimeZone TORONTO=TimeZone.getTimeZone("America/Toronto");
    private static String provider, profile;
    private static JAXBContext context(String factory,Class<?> type) throws Exception {
        Class<?> allowed = switch (factory) {
            case "org.glassfish.jaxb.runtime.v2.ContextFactory" -> Class.forName("org.glassfish.jaxb.runtime.v2.ContextFactory");
            case "org.eclipse.persistence.jaxb.JAXBContextFactory" -> Class.forName("org.eclipse.persistence.jaxb.JAXBContextFactory");
            default -> throw new IllegalArgumentException("Unsupported native JAXB factory");
        };
        return (JAXBContext)allowed.getMethod("createContext",Class[].class,Map.class).invoke(null,new Class<?>[]{type},Map.of());
    }
    /** Fixed issue inventory: no caller-selected class name reaches reflection. */
    private static Class<?> fixtureClass(String name) throws ClassNotFoundException {
        return switch (name) {
            case "commn.model.Demographic" -> Class.forName(BASE+"commn.model.Demographic");
            case "commn.model.ProfessionalSpecialist" -> Class.forName(BASE+"commn.model.ProfessionalSpecialist");
            case "commn.model.Provider" -> Class.forName(BASE+"commn.model.Provider");
            case "model.LookupCodeValue" -> Class.forName(BASE+"model.LookupCodeValue");
            case "webserv.rest.to.AbstractSearchResponse" -> Class.forName(BASE+"webserv.rest.to.AbstractSearchResponse");
            case "webserv.rest.to.AllergyResponse" -> Class.forName(BASE+"webserv.rest.to.AllergyResponse");
            case "webserv.rest.to.AppointmentExtResponse" -> Class.forName(BASE+"webserv.rest.to.AppointmentExtResponse");
            case "webserv.rest.to.DemographicResponse" -> Class.forName(BASE+"webserv.rest.to.DemographicResponse");
            case "webserv.rest.to.DrugSearchResponse" -> Class.forName(BASE+"webserv.rest.to.DrugSearchResponse");
            case "webserv.rest.to.InboxResponse" -> Class.forName(BASE+"webserv.rest.to.InboxResponse");
            case "webserv.rest.to.MessagingResponse" -> Class.forName(BASE+"webserv.rest.to.MessagingResponse");
            case "webserv.rest.to.NavbarResponse" -> Class.forName(BASE+"webserv.rest.to.NavbarResponse");
            case "webserv.rest.to.OscarSearchResponse" -> Class.forName(BASE+"webserv.rest.to.OscarSearchResponse");
            case "webserv.rest.to.PreventionResponse" -> Class.forName(BASE+"webserv.rest.to.PreventionResponse");
            case "webserv.rest.to.ProviderApptsCountResponse" -> Class.forName(BASE+"webserv.rest.to.ProviderApptsCountResponse");
            case "webserv.rest.to.ProviderPeriodAppsResponse" -> Class.forName(BASE+"webserv.rest.to.ProviderPeriodAppsResponse");
            case "webserv.rest.to.SchedulingResponse" -> Class.forName(BASE+"webserv.rest.to.SchedulingResponse");
            case "webserv.rest.to.TicklerResponse" -> Class.forName(BASE+"webserv.rest.to.TicklerResponse");
            case "webserv.rest.to.model.AdmissionTo1" -> Class.forName(BASE+"webserv.rest.to.model.AdmissionTo1");
            case "webserv.rest.to.model.AllergyTo1" -> Class.forName(BASE+"webserv.rest.to.model.AllergyTo1");
            case "webserv.rest.to.model.AllowedAppointmentTypeTransfer" -> Class.forName(BASE+"webserv.rest.to.model.AllowedAppointmentTypeTransfer");
            case "webserv.rest.to.model.AppointmentExtTo" -> Class.forName(BASE+"webserv.rest.to.model.AppointmentExtTo");
            case "webserv.rest.to.model.AppointmentTo1" -> Class.forName(BASE+"webserv.rest.to.model.AppointmentTo1");
            case "webserv.rest.to.model.BookingProviderTransfer" -> Class.forName(BASE+"webserv.rest.to.model.BookingProviderTransfer");
            case "webserv.rest.to.model.BookingScheduleTemplateCodeTransfer" -> Class.forName(BASE+"webserv.rest.to.model.BookingScheduleTemplateCodeTransfer");
            case "webserv.rest.to.model.CaseManagementIssueTo1" -> Class.forName(BASE+"webserv.rest.to.model.CaseManagementIssueTo1");
            case "webserv.rest.to.model.ConsultationAttachmentTo1" -> Class.forName(BASE+"webserv.rest.to.model.ConsultationAttachmentTo1");
            case "webserv.rest.to.model.ConsultationRequestTo1" -> Class.forName(BASE+"webserv.rest.to.model.ConsultationRequestTo1");
            case "webserv.rest.to.model.ConsultationResponseTo1" -> Class.forName(BASE+"webserv.rest.to.model.ConsultationResponseTo1");
            case "webserv.rest.to.model.DemographicTo1" -> Class.forName(BASE+"webserv.rest.to.model.DemographicTo1");
            case "webserv.rest.to.model.DocumentTo1" -> Class.forName(BASE+"webserv.rest.to.model.DocumentTo1");
            case "webserv.rest.to.model.EFormTo1" -> Class.forName(BASE+"webserv.rest.to.model.EFormTo1");
            case "webserv.rest.to.model.FilterDefinitionTransfer" -> Class.forName(BASE+"webserv.rest.to.model.FilterDefinitionTransfer");
            case "webserv.rest.to.model.MeasurementTo1" -> Class.forName(BASE+"webserv.rest.to.model.MeasurementTo1");
            case "webserv.rest.to.model.PharmacyInfoTo1" -> Class.forName(BASE+"webserv.rest.to.model.PharmacyInfoTo1");
            case "webserv.rest.to.model.PreventionTo1" -> Class.forName(BASE+"webserv.rest.to.model.PreventionTo1");
            case "webserv.rest.to.model.ProgramProviderTo1" -> Class.forName(BASE+"webserv.rest.to.model.ProgramProviderTo1");
            case "webserv.rest.to.model.ProgramTo1" -> Class.forName(BASE+"webserv.rest.to.model.ProgramTo1");
            case "webserv.rest.to.model.ScheduleTemplateCodeTo" -> Class.forName(BASE+"webserv.rest.to.model.ScheduleTemplateCodeTo");
            case "webserv.rest.to.model.SearchConfigTo1" -> Class.forName(BASE+"webserv.rest.to.model.SearchConfigTo1");
            case "webserv.rest.to.model.TicklerTo1" -> Class.forName(BASE+"webserv.rest.to.model.TicklerTo1");
            case "webserv.transfer_objects.AppointmentArchiveTransfer" -> Class.forName(BASE+"webserv.transfer_objects.AppointmentArchiveTransfer");
            case "webserv.transfer_objects.AppointmentTransfer" -> Class.forName(BASE+"webserv.transfer_objects.AppointmentTransfer");
            case "webserv.transfer_objects.CalendarScheduleCodePairTransfer" -> Class.forName(BASE+"webserv.transfer_objects.CalendarScheduleCodePairTransfer");
            case "webserv.transfer_objects.DayWorkScheduleTransfer" -> Class.forName(BASE+"webserv.transfer_objects.DayWorkScheduleTransfer");
            case "webserv.transfer_objects.DocumentTransfer" -> Class.forName(BASE+"webserv.transfer_objects.DocumentTransfer");
            case "webserv.transfer_objects.DrugTransfer" -> Class.forName(BASE+"webserv.transfer_objects.DrugTransfer");
            case "webserv.transfer_objects.PrescriptionTransfer" -> Class.forName(BASE+"webserv.transfer_objects.PrescriptionTransfer");
            case "webserv.transfer_objects.ScheduleTemplateCodeTransfer" -> Class.forName(BASE+"webserv.transfer_objects.ScheduleTemplateCodeTransfer");
            default -> throw new IllegalArgumentException("Unsupported native XML fixture class");
        };
    }
    private static RuntimeTypeInfoSet model(Class<?> type) throws Exception {
        return ((JAXBContextImpl)context("org.glassfish.jaxb.runtime.v2.ContextFactory",type)).getTypeInfoSet();
    }
    private static Object snapshot(String name,Object source) throws Exception {
        return Arrays.stream(Class.forName(BASE+"webserv.rest.conversion.EmbeddedRecordSnapshots").getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount()==2).findFirst().orElseThrow().invoke(null,null,source);
    }
    private static void copy(Object source,Object target,String property) throws Exception {
        Object value=source.getClass().getMethod("get"+property).invoke(source);
        Arrays.stream(target.getClass().getMethods()).filter(method -> method.getName().equals("set"+property)).findFirst().orElseThrow().invoke(target,value);
    }
    private static Object instance(Class<?> type) throws Exception {
        if(type.isEnum())return type.getEnumConstants()[0];
        if(type.getName().endsWith(".CdsTemporalNativeDateDto") || type.getName().endsWith(".PreventionDateDTO"))
            return Arrays.stream(type.getMethods()).filter(method -> method.getName().equals("from") && method.getParameterCount()==2).findFirst().orElseThrow().invoke(null,null,null);
        if(type.getName().endsWith(".ConsultationAttachment"))return type.getConstructors()[0].newInstance(null,null,null,null);
        if(type.getName().endsWith(".RestResponse"))return Arrays.stream(type.getMethods()).filter(method -> method.getName().equals("successResponse") && method.getParameterCount()==2).findFirst().orElseThrow().invoke(null,null,null);
        if(type.getName().endsWith(".GenericRestResponse")) {
            var constructor=Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.getParameterCount()==4).findFirst().orElseThrow();constructor.setAccessible(true);
            Object success=Arrays.stream(constructor.getParameterTypes()[3].getEnumConstants()).filter(value -> ((Enum<?>)value).name().equals("SUCCESS")).findFirst().orElseThrow();
            return constructor.newInstance(null,null,null,success);
        }
        var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object value=constructor.newInstance();
        for(Class<?> owner=type;owner!=Object.class;owner=owner.getSuperclass())for(Field field:owner.getDeclaredFields()) {
            if(!Modifier.isStatic(field.getModifiers()) && Date.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);if(field.get(value)!=null)field.set(value,field.getType().getConstructor(long.class).newInstance(1784135655120L));
            }
        }
        return value;
    }
    private static Object populated(Type generic, Set<Class<?>> parents, int depth, RuntimeTypeInfoSet model) throws Exception {
        Class<?> type = generic instanceof Class<?> c ? c
                : generic instanceof ParameterizedType p ? (Class<?>) p.getRawType() : Object.class;
        if (type == String.class || type == Object.class || type == java.io.Serializable.class) return "7";
        if (type == char.class || type == Character.class) return 'A';
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == byte.class || type == Byte.class) return (byte) 7;
        if (type == short.class || type == Short.class) return (short) 7;
        if (type == int.class || type == Integer.class) return 7;
        if (type == long.class || type == Long.class) return 7L;
        if (type == float.class || type == Float.class) return 7.25f;
        if (type == double.class || type == Double.class) return 7.25d;
        if (type == BigInteger.class) return BigInteger.valueOf(7);
        if (type == BigDecimal.class) return new BigDecimal("7.25");
        if (type == QName.class) return new QName("urn:synthetic", "code");
        GregorianCalendar date = new GregorianCalendar(TORONTO);
        date.clear();
        date.set(2026, Calendar.JULY, 15, 13, 14, 15);
        if (Date.class.isAssignableFrom(type)) return type.getConstructor(long.class).newInstance(date.getTimeInMillis());
        if (Calendar.class.isAssignableFrom(type)) return date;
        if (XMLGregorianCalendar.class.isAssignableFrom(type)) return DatatypeFactory.newInstance().newXMLGregorianCalendar(date);
        if (type.isEnum()) return instance(type);
        if (type == JAXBElement.class && generic instanceof ParameterizedType p) {
            Class<?> valueType = (Class<?>) p.getActualTypeArguments()[0];
            return new JAXBElement(new QName("urn:synthetic", "text"), valueType, populated(valueType, parents, depth, model));
        }
        if (java.text.DateFormat.class.isAssignableFrom(type)) return new java.text.SimpleDateFormat("yyyy-MM-dd");
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, populated(type.getComponentType(), parents, depth, model));
            return array;
        }
        if (Collection.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            Collection<Object> values = !type.isInterface() && !Modifier.isAbstract(type.getModifiers())
                    ? (Collection<Object>) instance(type)
                    : Set.class.isAssignableFrom(type) ? new HashSet<>() : new ArrayList<>();
            if (generic instanceof ParameterizedType p) values.add(populated(p.getActualTypeArguments()[0], parents, depth, model));
            return values;
        }
        if (Map.class.isAssignableFrom(type)) {
            Map<Object, Object> values = new LinkedHashMap<>();
            if (generic instanceof ParameterizedType p) values.put(populated(p.getActualTypeArguments()[0], parents, depth, model),
                    populated(p.getActualTypeArguments()[1], parents, depth, model));
            return values;
        }
        if (depth > 5 || parents.contains(type)) return null;
        if (!(type.getName().startsWith(BASE) || type.getName().startsWith("ca.ontario.")
                || type.getName().startsWith("org.w3."))) throw new IllegalArgumentException("Missing fixture for " + generic);
        Set<Class<?>> branch = new HashSet<>(parents);
        branch.add(type);
        if (type.getName().endsWith(".EmbeddedProfessionalSpecialistTo1")) {
            Class<?> original = Class.forName(BASE + "commn.model.ProfessionalSpecialist");
            Object source = populated(original, parents, depth, model(original));
            return snapshot("professionalSpecialist", source);
        }
        if (type.getName().endsWith(".EmbeddedDemographicTo1")) {
            Class<?> original = Class.forName(BASE + "commn.model.Demographic");
            Object source = populated(original, parents, depth, model(original));
            Object value = snapshot("demographic", source);
            copy(source, value, "Sin"); copy(source, value, "Hin");
            for (String member : List.of("Provider", "Mrp")) {
                Object provider = original.getMethod("get" + member).invoke(source);
                Object detached = snapshot("provider", provider);
                if (detached != null) for (String field : List.of("BillingNo", "OhipNo", "HsoNo", "RmaNo")) copy(provider, detached, field);
                Arrays.stream(type.getMethods()).filter(method -> method.getName().equals("set" + member)).findFirst().orElseThrow().invoke(value, detached);
            }
            return value;
        }
        Object bean = instance(type);
        if (type.getName().endsWith(".ProfessionalSpecialist")) {
            Field address = backingField(type, "addressArray");
            address.setAccessible(true);
            address.set(bean, new String[]{"7,", "7,", "7,", "7,", "7,"});
        }
        for (RuntimeClassInfo info = model.beans().get(type); info != null; info = info.getBaseClass()) {
            for (RuntimePropertyInfo property : info.getProperties()) {
                // These aliases all mutate the same comma-delimited address. Populate its backing
                // representation once, rather than exercising UI setter normalization as a fixture builder.
                if (type.getName().endsWith(".ProfessionalSpecialist") && Set.of(
                        "address", "address2", "city", "country", "postal", "province", "organizationName")
                        .contains(property.getName())) continue;
                Object value = populated(propertyType(type, property), branch, depth + 1, model);
                if (value instanceof String && property.getName().equals("dateOfBirth")) value = "15";
                if (value instanceof String && property.getName().equals("monthOfBirth")) value = "07";
                if (value instanceof String && property.getName().equals("yearOfBirth")) value = "1980";
                if ((type.getName().endsWith(".BillingONItem") || type.getName().endsWith(".EmbeddedBillingONItemTo1")) && property.getName().equals("fee")) value = "7.25";
                if (type.getName().endsWith(".PhoneNumber") && property.getName().equals("content")) {
                    value = new ArrayList<>(List.of(Class.forName(BASE + "hospitalReportManager.xsd.ObjectFactory").getMethod("createPhoneNumberNumber", String.class)
                            .invoke(Class.forName(BASE + "hospitalReportManager.xsd.ObjectFactory").getConstructor().newInstance(), "5550100")));
                }
                if (type.getName().endsWith(".ProfessionalSpecialist") && property.getName().equals("streetAddress"))
                    value = "7,7,7,7,7";
                if (type.getName().endsWith(".ProfessionalSpecialist") && property.getName().equals("addressArray"))
                    value = new String[]{"7,", "7,", "7,", "7,", "7,"};
                if (property.getName().equals("content") && type.getName().contains("SearchResponse")) {
                    var document = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
                    var element = document.createElementNS("urn:synthetic", "content");
                    element.setTextContent("Synthetic & <value> é");
                    value = new ArrayList<>(List.of(element));
                }
                try {
                    Field backing = backingField(type, property.getName());
                    if (backing != null && backing.getType().isAssignableFrom(value == null ? backing.getType() : value.getClass())) {
                        backing.setAccessible(true);
                        backing.set(bean, value);
                    } else property.getAccessor().setUnadapted(bean, value);
                } catch (IllegalArgumentException mismatch) {
                    throw new IllegalArgumentException(type.getName() + "." + property.getName()
                            + " raw=" + property.getRawType() + " value=" + (value == null ? "null" : value.getClass()), mismatch);
                } catch (org.glassfish.jaxb.runtime.api.AccessorException readOnly) {
                    Object existing = property.getAccessor().getUnadapted(bean);
                    if (existing instanceof Collection collection && value instanceof Collection values) {
                        collection.clear();
                        collection.addAll(values);
                    } else {
                        throw new IllegalStateException(type.getName() + "." + property.getName(), readOnly);
                    }
                }
            }
        }
        return bean;
    }

    private record FixtureType(Class<?> raw, Type[] arguments) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return arguments.clone(); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return null; }
    }

    /** Retain collection/map arguments, including type variables resolved against a concrete response. */
    private static Type propertyType(Class<?> owner, RuntimePropertyInfo property) throws Exception {
        Field field = backingField(owner, property.getName());
        org.springframework.core.ResolvableType resolved = field == null ? null
                : org.springframework.core.ResolvableType.forField(field, owner);
        if (resolved == null) {
            for (var descriptor : java.beans.Introspector.getBeanInfo(owner).getPropertyDescriptors()) {
                if (descriptor.getName().equals(property.getName()) && descriptor.getReadMethod() != null) {
                    resolved = org.springframework.core.ResolvableType.forMethodReturnType(descriptor.getReadMethod(), owner);
                    break;
                }
            }
        }
        // For a non-container use JAXB's property type, not a differently typed backing field
        // (e.g. boolean properties backed by a char flag).
        Class<?> raw = resolved == null ? null : resolved.resolve();
        if (raw == null || !(Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)))
            return property.getRawType();
        return fixtureType(resolved);
    }

    private static Type fixtureType(org.springframework.core.ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        var generics = type.getGenerics();
        if (generics.length == 0) return raw;
        Type[] arguments = Stream.of(generics).map(NativeXmlContractProbe::fixtureType).toArray(Type[]::new);
        return new FixtureType(raw, arguments);
    }

    private static Field backingField(Class<?> type, String property) {
        for (Class<?> owner = type; owner != Object.class; owner = owner.getSuperclass()) {
            try { return owner.getDeclaredField(property); } catch (NoSuchFieldException _) { /* inherited property */ }
        }
        return null;
    }

    @FunctionalInterface interface Operation {Object run() throws Exception;}
    private static Object selected(Operation operation) throws Exception {
        var phases=new TreeSet<Phase>();phases.add(new Phase("native-contract",1));var chain=new PhaseInterceptorChain(phases);
        var message=new MessageImpl();message.setExchange(new ExchangeImpl());message.setInterceptorChain(chain);
        Object[] result=new Object[1];Throwable[] error=new Throwable[1];
        chain.add(new AbstractPhaseInterceptor<Message>("native-contract") {
            @Override public void handleMessage(Message message) {
                try {
                    if(!profile.equals("none")) {
                        Class<?> type=Class.forName(BASE+"utility.xml.XmlWireProfile");
                        Object selected=type.getMethod("parse",String.class).invoke(null,profile);
                        type.getMethod("select",Message.class,type).invoke(null,message,selected);
                    }
                    result[0]=operation.run();
                } catch(Throwable failed){error[0]=failed;}
            }
        });
        chain.doIntercept(message);
        if(error[0]!=null){if(error[0] instanceof Exception exception)throw exception;throw new IllegalStateException(error[0]);}
        return result[0];
    }
    private static Throwable root(Throwable failure){while(failure.getCause()!=null && failure.getCause()!=failure)failure=failure.getCause();return failure;}
    public static void main(String[] args) throws Exception {
        provider=args[0];profile=args[1];
        if(!Set.of("glassfish","moxy").contains(provider) || !Set.of("none","published","moxy-legacy","moxy-date-only-birthday","glassfish-numeric-characters","glassfish-legacy").contains(profile)) throw new IllegalArgumentException("Unknown native probe provider or profile");
        Class.forName(provider.equals("glassfish")?"org.glassfish.jaxb.runtime.v2.ContextFactory":"org.eclipse.persistence.jaxb.JAXBContextFactory");
        Path inventory=Path.of(args[2]),output=Path.of(args[3]);
        if(Files.size(inventory)!=2987)throw new IllegalArgumentException("Native XML inventory differs from its reviewed pin");
        byte[] inventoryBytes=Files.readAllBytes(inventory);
        String inventoryHash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(inventoryBytes));
        if(!inventoryHash.equals("41d1fc8b1eba3c63effa2ead971490549c8c2d9a52d2b1d81a68bbe309a91693"))
            throw new IllegalArgumentException("Native XML inventory differs from its reviewed pin");
        Files.createDirectories(output);
        TimeZone.setDefault(TORONTO);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();List<String> ids=json.readValue(inventoryBytes,List.class);
        if(ids.size()!=56 || new HashSet<>(ids).size()!=56)throw new IllegalArgumentException("Native inventory must contain all 56 unique issue observations");
        Map<String,Object> cases=new LinkedHashMap<>();
        for(String id:ids) {
            String[] parts=id.split(":");Class<?> type=fixtureClass(parts[0]);Map<String,Object> record=new LinkedHashMap<>();cases.put(id,record);
            try {
                selected(() -> {
                    Object value=parts[1].equals("populated")?populated(type,new HashSet<>(),0,model(type)):instance(type);
                    JAXBContext binding=context(provider.equals("glassfish")?"org.glassfish.jaxb.runtime.v2.ContextFactory":"org.eclipse.persistence.jaxb.JAXBContextFactory",type);
                    record.put("provider",binding.getClass().getName());
                    var writer=new StringWriter();XMLStreamWriter xml=XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
                    if(!profile.equals("none"))xml=(XMLStreamWriter)Class.forName(BASE+"utility.xml.CompatibilityJaxbProvider").getMethod("compatibilityWriter",XMLStreamWriter.class).invoke(null,xml);
                    binding.createMarshaller().marshal(new JAXBElement(new QName("value"),type,value),xml);xml.flush();xml.close();
                    record.put("xml",writer.toString());
                    String file=parts[0]+"-"+parts[1]+".xml";Files.writeString(output.resolve(file),writer.toString());
                    var schemas=new ArrayList<StringWriter>();binding.generateSchema(new SchemaOutputResolver(){
                        @Override public Result createOutput(String namespace,String name){var content=new StringWriter();schemas.add(content);var source=new StreamResult(content);source.setSystemId(output.resolve(parts[0]+"-"+name).toUri().toString());return source;}
                    });record.put("schemas",schemas.stream().map(StringWriter::toString).toList());
                    javax.xml.validation.Schema[] strictSchema=new javax.xml.validation.Schema[1];
                    try {
                        var schemaFiles=new ArrayList<Path>();
                        for(int index=0;index<schemas.size();index++) {
                            Path schemaFile=output.resolve(parts[0]+"-schema"+(index+1)+".xsd");
                            Files.writeString(schemaFile,schemas.get(index).toString());schemaFiles.add(schemaFile);
                        }
                        QName typeName=model(type).beans().get(type).getTypeName();
                        var document=DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
                        var wrapper=document.createElementNS("http://www.w3.org/2001/XMLSchema","xs:schema");document.appendChild(wrapper);
                        var reference=document.createElementNS("http://www.w3.org/2001/XMLSchema",typeName.getNamespaceURI().isEmpty()?"xs:include":"xs:import");
                        if(!typeName.getNamespaceURI().isEmpty()){reference.setAttribute("namespace",typeName.getNamespaceURI());wrapper.setAttributeNS("http://www.w3.org/2000/xmlns/","xmlns:native",typeName.getNamespaceURI());}
                        reference.setAttribute("schemaLocation",schemaFiles.getFirst().toUri().toString());wrapper.appendChild(reference);
                        var element=document.createElementNS("http://www.w3.org/2001/XMLSchema","xs:element");element.setAttribute("name","value");element.setAttribute("type",(typeName.getNamespaceURI().isEmpty()?"":"native:")+typeName.getLocalPart());wrapper.appendChild(element);
                        var factory=javax.xml.validation.SchemaFactory.newDefaultInstance();factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"file");
                        var compiled=factory.newSchema(new javax.xml.transform.dom.DOMSource(document));strictSchema[0]=compiled;record.put("schemaCompiles",true);
                        compiled.newValidator().validate(new StreamSource(new StringReader(writer.toString())));record.put("schemaValid",true);
                    }catch(Throwable failed){record.put("schemaError",root(failed).getClass().getName());}
                    String input=writer.toString();
                    if(args.length>4) input=Files.readString(Path.of(args[4]).resolve(file));
                    for(boolean strict:new boolean[]{false,true}) {
                        String mode=strict?"strictRead":"defaultRead";
                        try {
                            var reader=binding.createUnmarshaller();if(strict){reader.setEventHandler(event -> false);if(strictSchema[0]!=null)reader.setSchema(strictSchema[0]);else record.put("strictSchemaUnavailable",true);}
                            Object read=reader.unmarshal(new StreamSource(new StringReader(input)),type).getValue();
                            StringWriter roundtrip=new StringWriter();binding.createMarshaller().marshal(new JAXBElement(new QName("value"),type,read),roundtrip);
                            record.put(mode,roundtrip.toString());
                        }catch(Throwable failed){record.put(mode+"Error",root(failed).getClass().getName());}
                    }
                    return null;
                });
            } catch(Exception failed){record.put("error",root(failed).getClass().getName());}
        }
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("observations.json").toFile(),Map.of("schemaVersion",1,"provider",provider,"profile",profile,"cases",cases));
    }
}
