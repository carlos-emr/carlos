/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import javax.xml.transform.stream.StreamSource;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Runs unchanged synthetic clients against each binary's own classes, libraries and mapper beans. */
public final class WarWireCompatibilityProbe {
    private static final String PREFIX = "io.github.carlos_emr.carlos.";
    private static final Map<String, Object> CASES = new LinkedHashMap<>();
    private static final Map<String, Object> ERRORS = new LinkedHashMap<>();
    private static final Set<String> XML_PROVIDERS = new java.util.TreeSet<>();
    private static final Map<Class<?>, Map<String, String>> CONSTANTS = new java.util.HashMap<>();
    private static String xmlProvider = "automatic";
    @FunctionalInterface private interface Operation { String execute() throws Exception; }

    private static void observe(String name, String kind, Operation operation) {
        try { CASES.put(name, Map.of("kind", kind, "value", operation.execute())); }
        catch (Throwable failure) {
            while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
            ERRORS.put(name, failure.getClass().getName());
        }
    }

    private static Object bean(ClassLoader loader, String name, boolean populated) throws Exception {
        var type = loader.loadClass(PREFIX + name);
        Object value = type.getConstructor().newInstance();
        if (!populated) return value;
        if (name.endsWith("FilterDefinitionTransfer")) {
            type.getMethod("setFilterClassName", String.class).invoke(value, "FAKE-legacy-filter");
            type.getMethod("setOn", boolean.class).invoke(value, true);
            type.getMethod("setParams", Map.class).invoke(value, new LinkedHashMap<>(Map.of("FAKE-key", "café Ω")));
        } else if (name.endsWith("ProviderTransfer")) {
            type.getMethod("setProviderNo", String.class).invoke(value, "999998");
        } else {
            for (var setter : type.getMethods()) {
                if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
                Class<?> fieldType = setter.getParameterTypes()[0];
                Object field;
                if (fieldType == String.class) field = "7";
                else if (fieldType == int.class || fieldType == Integer.class) field = 7;
                else if (Calendar.class.isAssignableFrom(fieldType)) {
                    var calendar = new GregorianCalendar(TimeZone.getTimeZone("America/Toronto"));
                    calendar.clear(); calendar.set(2026, Calendar.JULY, 15, 13, 14, 15); field = calendar;
                } else if (fieldType.isEnum()) {
                    field = java.util.Arrays.stream(fieldType.getEnumConstants())
                            .filter(item -> ((Enum<?>) item).name().equals("OSCAR")).findFirst().orElseThrow();
                } else throw new IllegalStateException("Unreviewed transfer field type");
                setter.invoke(value, field);
            }
        }
        return value;
    }

    /** Selects the requested provider inside the binary's loader and records the actual implementation. */
    private static Object xmlContext(ClassLoader loader, Class<?> type) throws Exception {
        Object context;
        if (xmlProvider.equals("automatic")) {
            context = loader.loadClass("jakarta.xml.bind.JAXBContext").getMethod("newInstance", Class[].class)
                    .invoke(null, (Object) new Class<?>[]{type});
        } else {
            String factory = switch (xmlProvider) {
                case "glassfish" -> "org.glassfish.jaxb.runtime.v2.ContextFactory";
                case "moxy" -> "org.eclipse.persistence.jaxb.JAXBContextFactory";
                default -> throw new IllegalArgumentException("Unknown XML provider selection");
            };
            context = loader.loadClass(factory).getMethod("createContext", Class[].class, Map.class)
                    .invoke(null, new Class<?>[]{type}, Map.of());
        }
        XML_PROVIDERS.add(context.getClass().getName());
        return context;
    }

    /** Serializes a native DTO through the selected binary provider under a stable synthetic root. */
    private static String xml(ClassLoader loader, Object value) throws Exception {
        var contextType = loader.loadClass("jakarta.xml.bind.JAXBContext");
        var context = xmlContext(loader, value.getClass());
        var element = loader.loadClass("jakarta.xml.bind.JAXBElement")
                .getConstructor(QName.class, Class.class, Object.class)
                .newInstance(new QName("urn:carlos:jaxb-compatibility", "value"), value.getClass(), value);
        var marshaller = contextType.getMethod("createMarshaller").invoke(context);
        var writer = new StringWriter();
        loader.loadClass("jakarta.xml.bind.Marshaller").getMethod("marshal", Object.class, java.io.Writer.class)
                .invoke(marshaller, element, writer);
        return writer.toString();
    }

    /** Feed the identical legacy client XML to each deployed provider, including nested DTOs. */
    private static String roundTrip(ClassLoader loader, String name, String input) throws Exception {
        var type = loader.loadClass(name);
        var contextType = loader.loadClass("jakarta.xml.bind.JAXBContext");
        var context = xmlContext(loader, type);
        var unmarshaller = contextType.getMethod("createUnmarshaller").invoke(context);
        var element = loader.loadClass("jakarta.xml.bind.Unmarshaller")
                .getMethod("unmarshal", javax.xml.transform.Source.class, Class.class)
                .invoke(unmarshaller, new StreamSource(new java.io.StringReader(input)), type);
        var writer = new StringWriter();
        var marshaller = contextType.getMethod("createMarshaller").invoke(context);
        loader.loadClass("jakarta.xml.bind.Marshaller").getMethod("marshal", Object.class, java.io.Writer.class)
                .invoke(marshaller, element, writer);
        return writer.toString();
    }

    private static String publicApi(ClassLoader loader, Path inventory) throws Exception {
        var result = new java.util.TreeMap<String, Object>();
        var visited = new java.util.HashSet<Class<?>>();
        for (String name : Files.readAllLines(inventory)) {
            try { publicType(loader.loadClass(name), result, visited, true); }
            catch (ClassNotFoundException absent) { result.put(name, List.of()); }
            catch (Exception | LinkageError incomplete) {
                // Retain all other types instead of discarding an entire API
                // inventory when one protected signature needs a provided API.
                result.put(name, List.of("unresolved-api:" + incomplete.getClass().getName()));
                System.err.println("Incomplete API inventory for " + name + ": " + incomplete.getClass().getName());
            }
        }
        return encode(result);
    }

    private static String constantValue(java.lang.reflect.Field field) throws Exception {
        Class<?> owner = field.getDeclaringClass();
        var values = CONSTANTS.get(owner);
        if (values == null) {
            values = new java.util.HashMap<>();
            String resource = "/" + owner.getName().replace('.', '/') + ".class";
            try (var stream = owner.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing declared class bytes");
                var model = java.lang.classfile.ClassFile.of().parse(stream.readAllBytes());
                if (!model.thisClass().asInternalName().equals(owner.getName().replace('.', '/')))
                    throw new IllegalStateException("Class resource belongs to another type");
                for (var member : model.fields()) {
                    var attribute = member.findAttribute(java.lang.classfile.Attributes.constantValue());
                    if (attribute.isPresent()) {
                        Object constant = attribute.get().constant().constantValue();
                        String typed = member.fieldType().stringValue() + ":" + encode(constant);
                        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                .digest(typed.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        values.put(member.fieldName().stringValue(), digest);
                    }
                }
            }
            CONSTANTS.put(owner, values);
        }
        // Read ConstantValue bytes, never Field.get(): that could initialize the
        // application and conceal old clients' inlined compile-time constants.
        return values.get(field.getName());
    }

    private static void publicType(Class<?> type, Map<String, Object> result, Set<Class<?>> visited, boolean root) throws Exception {
        if (!visited.add(type)) return;
        if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())
                && (root || !java.lang.reflect.Modifier.isProtected(type.getModifiers()))) {
            if (root) result.put(type.getName(), List.of("not-public"));
            return;
        }
        var members = new java.util.TreeSet<String>();
        members.add("type:" + java.lang.reflect.Modifier.toString(type.getModifiers()));
        for (var base = type.getSuperclass(); base != null; base = base.getSuperclass()) members.add("base:" + base.getName());
        for (var member : type.getMethods()) {
            if (!member.isSynthetic() && !member.isBridge()) members.add("method:" + member.toGenericString());
        }
        // Existing clients can subclass public types and call protected members.
        for (var base = type; base != null; base = base.getSuperclass()) {
            for (var member : base.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isProtected(member.getModifiers())
                        && !member.isSynthetic() && !member.isBridge()) members.add("method:" + member.toGenericString());
            }
            for (var member : base.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isProtected(member.getModifiers()) && !member.isSynthetic())
                    members.add("field:" + member.toGenericString());
                if (java.lang.reflect.Modifier.isProtected(member.getModifiers()) && !member.isSynthetic()
                        && constantValue(member) != null)
                    members.add("constant:" + member.toGenericString() + ":" + constantValue(member));
            }
        }
        for (var member : type.getDeclaredConstructors()) {
            if (java.lang.reflect.Modifier.isPublic(member.getModifiers())
                    || java.lang.reflect.Modifier.isProtected(member.getModifiers()))
                members.add("constructor:" + member.toGenericString());
        }
        for (var member : type.getFields()) {
            if (!member.isSynthetic()) members.add("field:" + member.toGenericString());
            if (!member.isSynthetic() && constantValue(member) != null)
                members.add("constant:" + member.toGenericString() + ":" + constantValue(member));
            if (member.isEnumConstant()) members.add("enum-constant:" + member.getName());
        }
        // Include inherited interfaces: removing an implemented contract can break an unchanged client.
        var interfaces = new java.util.ArrayDeque<Class<?>>();
        for (var base = type; base != null; base = base.getSuperclass()) interfaces.addAll(List.of(base.getInterfaces()));
        var seen = new java.util.HashSet<Class<?>>();
        while (!interfaces.isEmpty()) {
            var contract = interfaces.removeFirst();
            if (seen.add(contract)) {
                members.add("interface:" + contract.getName());
                interfaces.addAll(List.of(contract.getInterfaces()));
            }
        }
        result.put(type.getName(), members);
        // Protected member types are part of the contract exposed to subclasses too.
        for (var nested : type.getDeclaredClasses()) publicType(nested, result, visited, false);
    }

    private static Document configuration(ClassLoader loader, String resource) throws Exception {
        var parser = DocumentBuilderFactory.newDefaultInstance(); parser.setNamespaceAware(true);
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try (var input = loader.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing production mapper configuration");
            return parser.newDocumentBuilder().parse(input);
        }
    }

    private static Object mapperFactory(ClassLoader loader, String resource, String id) throws Exception {
        Document document = configuration(loader, resource);
        Element root = document.getDocumentElement();
        // Retain the mapper's real module-registration factory as well as its referenced module.
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling(); boolean keep = false;
            if (child instanceof Element element && "bean".equals(element.getLocalName())) {
                keep = Set.of(id, "smartDateModule").contains(element.getAttribute("id"));
                if ("org.springframework.beans.factory.config.MethodInvokingFactoryBean".equals(element.getAttribute("class"))) {
                    var references = element.getElementsByTagNameNS("http://www.springframework.org/schema/beans", "property");
                    for (int i = 0; i < references.getLength(); i++) {
                        var property = (Element) references.item(i);
                        keep |= "targetObject".equals(property.getAttribute("name")) && id.equals(property.getAttribute("ref"));
                    }
                }
            }
            if (!keep) root.removeChild(child); child = next;
        }
        var factoryType = loader.loadClass("org.springframework.beans.factory.support.DefaultListableBeanFactory");
        var factory = factoryType.getConstructor().newInstance();
        factoryType.getMethod("setBeanClassLoader", ClassLoader.class).invoke(factory, loader);
        var readerType = loader.loadClass("org.springframework.beans.factory.xml.XmlBeanDefinitionReader");
        var reader = readerType.getConstructor(loader.loadClass("org.springframework.beans.factory.support.BeanDefinitionRegistry"))
                .newInstance(factory);
        var classResource = loader.loadClass("org.springframework.core.io.ClassPathResource")
                .getConstructor(String.class, ClassLoader.class).newInstance(resource, loader);
        readerType.getMethod("registerBeanDefinitions", Document.class, loader.loadClass("org.springframework.core.io.Resource"))
                .invoke(reader, document, classResource);
        factoryType.getMethod("preInstantiateSingletons").invoke(factory);
        return factory;
    }

    private static String json(ClassLoader loader, String resource, String id, Object value) throws Exception {
        var factory = mapperFactory(loader, resource, id);
        try {
            var mapper = factory.getClass().getMethod("getBean", String.class).invoke(factory, id);
            if ("applicationContextREST.xml".equals(resource)) {
                var modules = (Set<?>) mapper.getClass().getMethod("getRegisteredModuleIds").invoke(mapper);
                if (!modules.contains("SmartDateModule")) throw new IllegalStateException("OAuth module registration was omitted");
            }
            return (String) mapper.getClass().getMethod("writeValueAsString", Object.class).invoke(mapper, value);
        } finally { factory.getClass().getMethod("destroySingletons").invoke(factory); }
    }

    private static String soapLeak(ClassLoader loader) throws Exception {
        String canary = "FAKE_MAIN_RELEASE_SOAP_CANARY";
        var messageType = loader.loadClass("org.apache.cxf.message.Message");
        var message = loader.loadClass("org.apache.cxf.message.MessageImpl").getConstructor().newInstance();
        messageType.getMethod("setContent", Class.class, Object.class).invoke(message, java.io.InputStream.class,
                new ByteArrayInputStream(("<synthetic>" + canary + "</synthetic>").getBytes(StandardCharsets.UTF_8)));
        var captured = new ByteArrayOutputStream(); var original = System.out;
        try (var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            var interceptorType = loader.loadClass(PREFIX + "integration.ebs.client.ng.DownloadInInterceptor");
            interceptorType.getMethod("handleMessage", messageType).invoke(interceptorType.getConstructor().newInstance(), message);
        } finally { System.setOut(original); }
        return Boolean.toString(captured.toString(StandardCharsets.UTF_8).contains(canary));
    }

    private static String encode(Object value) {
        if (value instanceof Iterable<?> items) {
            var entries = new ArrayList<String>();
            for (var item : items) entries.add(encode(item));
            return "[" + String.join(",", entries) + "]";
        }
        if (value instanceof Map<?, ?> map) {
            var entries = new ArrayList<String>();
            for (var entry : map.entrySet()) entries.add(encode(entry.getKey()) + ":" + encode(entry.getValue()));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        var text = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') text.append('\\').append(c);
            else if (c < 32) text.append(String.format("\\u%04x", (int) c)); else text.append(c);
        }
        return text.append('"').toString();
    }

    /**
     * Runs the unchanged client corpus in Tomcat's isolated loader and retains every observation.
     *
     * @param args String[] containing the webapp directory, result file, XML corpus file,
     *             public-API inventory file, and optional XML-provider selector
     * @throws Exception if the isolated container lifecycle, input loading, or result writing fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 5) xmlProvider = args[4];
        if (!Set.of("automatic", "glassfish", "moxy").contains(xmlProvider)) {
            throw new IllegalArgumentException("Unknown XML provider selection");
        }
        Path webapp = Path.of(args[0]);
        // Maven and sorted URL class paths can select a different JAXB service from Tomcat.
        // Use the actual container resource/class-loader implementation, without starting the app.
        var contextType = Class.forName("org.apache.catalina.core.StandardContext");
        var context = contextType.getConstructor().newInstance();
        contextType.getMethod("setDocBase", String.class).invoke(context, webapp.toAbsolutePath().toString());
        contextType.getMethod("setName", String.class).invoke(context, "/compatibility");
        var resourcesType = Class.forName("org.apache.catalina.webresources.StandardRoot");
        var resources = resourcesType.getConstructor(Class.forName("org.apache.catalina.Context")).newInstance(context);
        contextType.getMethod("setResources", Class.forName("org.apache.catalina.WebResourceRoot")).invoke(context, resources);
        resourcesType.getMethod("start").invoke(resources);
        var loaderType = Class.forName("org.apache.catalina.loader.ParallelWebappClassLoader");
        var loader = (URLClassLoader) loaderType.getConstructor(ClassLoader.class).newInstance(ClassLoader.getSystemClassLoader());
        loaderType.getMethod("setResources", Class.forName("org.apache.catalina.WebResourceRoot")).invoke(loader, resources);
        loaderType.getMethod("start").invoke(loader);
        var previous = Thread.currentThread().getContextClassLoader();
        try (loader) {
            Thread.currentThread().setContextClassLoader(loader);
            var jsonType = loader.loadClass("com.fasterxml.jackson.databind.ObjectMapper");
            var corpus = (Map<?, ?>) jsonType.getMethod("readValue", String.class, Class.class)
                    .invoke(jsonType.getConstructor().newInstance(), Files.readString(Path.of(args[2])), Map.class);
            for (var entry : corpus.entrySet()) {
                for (var sample : ((Map<?, ?>) entry.getValue()).entrySet()) {
                    String name = (String) entry.getKey(), input = (String) sample.getValue();
                    observe("legacy-input:" + name + ":" + sample.getKey() + ":xml", "xml",
                            () -> roundTrip(loader, name, input));
                }
            }
            for (String type : List.of("webserv.rest.to.model.FilterDefinitionTransfer", "webserv.transfer_objects.AppointmentArchiveTransfer")) {
                for (boolean populated : new boolean[]{false, true}) {
                    String name = type.substring(type.lastIndexOf('.') + 1) + (populated ? "-values" : "-default");
                    observe(name + "-xml", "xml", () -> xml(loader, bean(loader, type, populated)));
                    observe(name + "-session-json", "json", () -> json(loader, "spring_ws.xml", "jacksonObjectMapper", bean(loader, type, populated)));
                    observe(name + "-oauth-json", "json", () -> json(loader, "applicationContextREST.xml", "oauthJacksonObjectMapper", bean(loader, type, populated)));
                }
            }
            String provider = "webserv.transfer_objects.ProviderTransfer";
            observe("provider-session-json", "json", () -> json(loader, "spring_ws.xml", "jacksonObjectMapper", bean(loader, provider, true)));
            observe("provider-oauth-json", "json", () -> json(loader, "applicationContextREST.xml", "oauthJacksonObjectMapper", bean(loader, provider, true)));
            observe("opt-in-soap-payload-leaked", "boolean", () -> soapLeak(loader));
            observe("changed-public-java-api", "api", () -> publicApi(loader, Path.of(args[3])));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            loaderType.getMethod("stop").invoke(loader);
            resourcesType.getMethod("stop").invoke(resources);
            resourcesType.getMethod("destroy").invoke(resources);
        }
        Files.writeString(Path.of(args[1]), encode(Map.of("schemaVersion", 1, "cases", CASES, "errors", ERRORS,
                "xmlProviders", XML_PROVIDERS)) + "\n");
        if (!ERRORS.isEmpty()) System.exit(1);
    }
}
