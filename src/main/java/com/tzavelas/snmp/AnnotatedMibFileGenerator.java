package com.tzavelas.snmp;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.snmp4j.smi.VariantVariableCallback;

import com.google.common.collect.ImmutableMap;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/**
 * Given a set of fully-qualified class names with MibObject annotation.
 * This class produce a Mib definition file written in SMI-v2;
 *
 * TODO: More testing
 */
public class AnnotatedMibFileGenerator {
    /**
     * Interface for accessors
     */
    private static interface MemberSubAccessor {
        public Class<?> getValueType(Object obj);
        public MibObject getMibObjectAnnotation(Object obj);
        public boolean isSynthetic(Object obj);
        public VariantVariableCallback createVariantCallback(Object obj, Object clsMember, Logger logger);

    }

    /**
     * Accessor that is aware of fields that have been annotated with @MibObject
     */
    private static MemberSubAccessor FieldSubAccessor = new MemberSubAccessor() {
        @Override
        public Class<?> getValueType(Object obj) {
            return ((Field)obj).getType();
        }

        @Override
        public MibObject getMibObjectAnnotation(Object obj) {
            return ((Field)obj).getAnnotation(MibObject.class);
        }

        @Override
        public boolean isSynthetic(Object obj) {
            return ((Field)obj).isSynthetic();
        }

        @Override
        public VariantVariableCallback createVariantCallback(Object obj,
                                                             Object clsMember, Logger logger) {
            return new DynamicVariantVariableCallback
                    .DynamicVariantVariableCallbackBuilder(obj)
                    .field((Field)clsMember)
                    .logger(logger)
                    .build();
        }
    };

    /**
     * Accessor that is aware of methods that have been annotated with @MibObject
     */
    public static MemberSubAccessor MethodSubAccessor = new MemberSubAccessor() {
        @Override
        public Class<?> getValueType(Object obj) {
            return ((Method)obj).getReturnType();
        }

        @Override
        public MibObject getMibObjectAnnotation(Object obj) {
            return ((Method)obj).getAnnotation(MibObject.class);
        }

        @Override
        public boolean isSynthetic(Object obj) {
            return ((Method)obj).isSynthetic();
        }

        @Override
        public VariantVariableCallback createVariantCallback(Object obj,
                                                             Object clsMember, Logger logger) {
            return new DynamicVariantVariableCallback
                    .DynamicVariantVariableCallbackBuilder(obj)
                    .method((Method)clsMember)
                    .logger(logger)
                    .build();
        }
    };

    private static final String EMPTY_OID = "";
    private static final String EMPTY_TYPE = "";
    private static Map<String, String> OBJECT_CLASS_TO_MIB_CLASS_MAPPING =
            new ImmutableMap.Builder<String,String>()
            .put("integer32", "Integer32")
            .put("counter64", "Counter64")
            .put("octetstring", "OctetString")
            .put("gauge32", "Gauge32")
            .put(Byte.class.getName(), "Integer32")     //class type
            .put(Short.class.getName(), "Integer32")
            .put(Integer.class.getName(), "Integer32")
            .put(Long.class.getName(), "Counter64")
            .put(String.class.getName(), "OctetString")
            .put("byte", "Integer32")                   //primitive type
            .put("short", "Integer32")
            .put("int", "Integer32")
            .put("long", "Counter64")
            .build();

    private static final String MOD_DEF_NAME = "module_definition_name";
    private static final String MOD_NAME = "module_name";
    private static final String MOD_TS_UPDATED ="module_ts_updated";
    private static final String MOD_ORG_NAME ="module_org_name" ;
    private static final String MOD_CONTACT ="module_contact_info";
    private static final String MOD_DESC ="module_description" ;
    private static final String MOD_TS_REVISION ="module_ts_revision";
    private static final String MOD_OID="module_oid";
    private static final String MOD_COMMENT ="module_comment";

    private static final String OIB_TYPE = "module_type";
    private static final String OIB_ACCESS = "module_access";
    private static final String OID_STATUS = "module_status";

    private DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private Configuration cfg;
    private Template objTemplate;

    /**
     * Constructor
     *
     * @param templateLocation
     * @throws IOException
     */
    public AnnotatedMibFileGenerator(File templateLocation) throws IOException {
        cfg = new Configuration(Configuration.VERSION_2_3_21);
        cfg.setDirectoryForTemplateLoading(templateLocation);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /**
     * Get MIB type from @MibObject type
     *
     * @param mibAnnotationType
     * @param mibClass
     * @return MIB type
     */
    private String getMibObjectType(String mibAnnotationType, Class<?> mibClass) {
        String typeNorm = mibAnnotationType.trim().toLowerCase();
        String ret = null;
        if (typeNorm.equals(EMPTY_TYPE)) {
            ret = OBJECT_CLASS_TO_MIB_CLASS_MAPPING.get(mibClass.getName());
            if (ret == null) {
                ret = "OctetString";
            }
        } else if (OBJECT_CLASS_TO_MIB_CLASS_MAPPING.containsKey(typeNorm)) {
            ret = OBJECT_CLASS_TO_MIB_CLASS_MAPPING.get(typeNorm);
        } else {
            ret = OBJECT_CLASS_TO_MIB_CLASS_MAPPING.get(mibClass.getName());
        }
        return ret;
    }

    /**
     * Determine if the annotation is valid
     *
     * @param annotation
     * @return
     */
    private boolean isAnnotationValid(MibObject annotation) {
        if (annotation == null) {
            return false;
        } else if (annotation.oid().trim().equals(EMPTY_OID)) {
            return false;
        }
        return true;
    }

    /**
     * Process all the member to find the @MibObject fields in the object
     *
     * @param members
     * @param accessor
     * @param objectDefinitions
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void processFieldsInto(Object[] members, String objectIdentifier, MemberSubAccessor accessor,
                                   List<ObjectDefinition> objectDefinitions)
            throws InstantiationException, IllegalAccessException {

        for (Object member: members) {
            MibObject mibAnnotation = accessor.getMibObjectAnnotation(member);

            if (!isAnnotationValid(mibAnnotation) || accessor.isSynthetic(member)) {
                continue;
            }

            String objType = getMibObjectType(mibAnnotation.type(), accessor.getValueType(member));
            if (objType == null) {
                //TODO: log unsupported types
                continue;
            }

            objectDefinitions.add(new ObjectDefinition(mibAnnotation.name(), objType, "read-only", "current",
                    mibAnnotation.description(), mibAnnotation.oid(), mibAnnotation.comment(), objectIdentifier));
        }
    }

    public void processIntoMibDefinition(OutputStream os, List<ObjectIdentifier> objectIdentifiers) throws Exception {
        Date date = new Date();
        String dateStr = dateFormat.format(date);

        objTemplate = cfg.getTemplate("ModuleDefinition.template");
        Map<String, Object> data = new HashMap<>();

        //Generate the mib module header
        //TODO: allow this value to be provided on the commandline
        data.put(MOD_DEF_NAME, "annotated_definition_name");
        data.put(MOD_NAME, "annotated_module_name");
        data.put(MOD_TS_UPDATED, dateStr);
        data.put(MOD_ORG_NAME, "Company");
        data.put(MOD_CONTACT, "support@company.com");
        data.put(MOD_DESC, "N/A");
        data.put(MOD_TS_REVISION, dateStr);
        data.put(MOD_OID, "subtree oid"); // subtree linkage
        data.put(MOD_COMMENT, "N/A");

        List<ObjectDefinition> objectDefinitions = new ArrayList<>();
        for (ObjectIdentifier objectIdentifier: objectIdentifiers) {
            objectIdentifier.setModuleName((String) data.get(MOD_NAME));
            processFieldsInto(objectIdentifier.getObjectIdentifierClass().getDeclaredFields(), objectIdentifier.getName(),
                    FieldSubAccessor, objectDefinitions);
            processFieldsInto(objectIdentifier.getObjectIdentifierClass().getDeclaredMethods(), objectIdentifier.getName(),
                    MethodSubAccessor, objectDefinitions);
        }
        data.put("obj_idents", objectIdentifiers);
        data.put("obj_defs", objectDefinitions);

        // TODO: Make use of object-identity, and other decorative mib syntax?

        // Console output
        Writer out = new OutputStreamWriter(os);
        objTemplate.process(data, out);
        out.flush();
    }

    public static void main(String[] argv) throws Exception {
        if (argv.length == 0) {
            System.out.println("No class path provided");
            return;
        } else if (argv.length % 2 != 0) {
            System.out.println("Either object identifier or class path is missing");
            return;
        }

        int count = 1;
        List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();
        for (int i = 0; i < argv.length; i+=2) {
            objectIdentifiers.add(new ObjectIdentifier(argv[i], argv[i+1], count + ""));
            count++;
        }

        AnnotatedMibFileGenerator generator = new AnnotatedMibFileGenerator(
                new File(AnnotatedMibFileGenerator.class.getResource("/resources/mib/template").toURI()));
        generator.processIntoMibDefinition(System.out, objectIdentifiers);
    }

    public class ObjectDefinition {
        private String name;
        private String type;
        private String access;
        private String status;
        private String description;
        private String oid;
        private String comment;
        private String objectIdentifier;

        public ObjectDefinition(String name, String type, String access, String status, String desc, String oid,
                                String comment, String objectIdentifier) {
            this.name = name;
            this.type = type;
            this.access = access;
            this.status = status;
            this.description = desc;
            this.oid = oid;
            this.comment = comment;
            this.objectIdentifier = objectIdentifier;
        }
        public String getName() {
            return name;
        }
        public String getType() {
            return type;
        }
        public String getAccess() {
            return access;
        }
        public String getStatus() {
            return status;
        }
        public String getDescription() {
            return description;
        }
        public String getOid() {
            return oid;
        }
        public String getComment() {
            return comment;
        }
        public String getObjectIdentifier() {
            return objectIdentifier;
        }
    }

    public static class ObjectIdentifier {
        private Class<?> aClass;
        private String name;
        private String oid;
        private String moduleName;

        public ObjectIdentifier(String objectIdentifierName, String className, String oid) throws ClassNotFoundException {
            this.aClass = Class.forName(className);
            this.name = objectIdentifierName;
            this.oid = oid;
        }

        public Class<?> getObjectIdentifierClass() {
            return aClass;
        }

        public String getName() {
            return name;
        }

        public String getOid() {
            return oid;
        }

        public String getModuleName(){
            return moduleName;
        }

        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }
    }
}
