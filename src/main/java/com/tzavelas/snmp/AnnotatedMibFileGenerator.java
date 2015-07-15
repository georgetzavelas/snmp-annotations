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

import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariantVariableCallback;

import com.google.common.collect.ImmutableMap;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

/**
 * Given a set of fully-qualified class names with MibObject annotation.
 * This class produce a Mib definition file written in SMI-v2;
 *
 *  NOTE: this is in alpha stage
 */
public class AnnotatedMibFileGenerator {
    public class ObjDef{
        private String name;
        private String type;
        private String access;
        private String status;
        private String description;
        private String oid;
        private String comment;
        public ObjDef(String name,
                String type,
                String access,
                String status,
                String desc,
                String oid,
                String comment){
            this.name = name;
            this.type = type;
            this.access = access;
            this.status = status;
            this.description = desc;
            this.oid = oid;
            this.comment = comment;
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
    }

    private static interface MemberSubAccessor{
        public Class<?> getValueType(Object obj);
        public MibObject getMibObjectAnnotation(Object obj);
        public boolean isSynthetic(Object obj);
        public VariantVariableCallback createVariantCallback(Object obj, Object clsMember, Logger logger);

    }
    private static MemberSubAccessor FieldSubAccessor = new MemberSubAccessor(){
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
            return new DynamicVariantVariableCallback(obj, (Field)clsMember, logger);
        }
    };

    public static MemberSubAccessor MethodSubAccessor = new MemberSubAccessor(){
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
            return new DynamicVariantVariableCallback(obj, (Method)clsMember, logger);
        }
    };

    private static String EMPTY_OID = "";
    private static String EMPTY_TYPE = "";
    private static Map<String, String> VALID_TYPES =
            new ImmutableMap.Builder<String,String>()
            .put("integer32", "Integer32")
            .put("counter64", "Counter64")
            .put("octetstring", "OctetString")
            .put(Byte.class.getName(), "Integer32")     //class type
            .put(Short.class.getName(), "Integer32")
            .put(Integer.class.getName(), "Integer32")
            .put(Long.class.getName(), "Counter64")
            .put(String.class.getName(), "OctetString")
            .put("byte", "Integer32")           //primitive type
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

    public AnnotatedMibFileGenerator(String templateLocation) throws IOException{
        cfg = new Configuration(Configuration.VERSION_2_3_21);
        cfg.setDirectoryForTemplateLoading(new File(templateLocation));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    private String getMibObjectType(String givenObjType, Class<?> valType){
        String typeNorm = givenObjType.trim().toLowerCase();
        String ret = null;
        if( typeNorm.equals(EMPTY_TYPE) ){
            ret = VALID_TYPES.get(valType.getName());
            if(ret == null){
                ret = "OctetString";
            }
        }else if( VALID_TYPES.containsKey(typeNorm) ){
            ret = VALID_TYPES.get(valType.getName());
        }
        return ret;
    }

    private boolean isAnnotationValid(MibObject annotation){
        if(annotation == null){
            return false;
        }else if( annotation.oid().trim().equals(EMPTY_OID) ){
            return false;
        }
        return true;
    }

    private void processFieldsInto(
            Object[] members,
            MemberSubAccessor accessor,
            List<ObjDef> objDefs) throws InstantiationException, IllegalAccessException{
        int ret = 0;
        for(Object member: members){
            Variable var = null;

            String oidSuffix = "";
            MibObject mibAnnotation = accessor.getMibObjectAnnotation(member);

            if( !isAnnotationValid(mibAnnotation) || accessor.isSynthetic(member) ){
                continue;
            }

            String objType = getMibObjectType( mibAnnotation.type(), accessor.getValueType(member));
            if(objType == null){
                //TODO: log unsupported types
                continue;
            }

            objDefs.add(
                    new ObjDef(
                            mibAnnotation.name(),
                            objType,
                            "read-only",
                            "current",
                            mibAnnotation.description(),
                            mibAnnotation.oid(),
                            mibAnnotation.comment()
                    ));
        }
    }

    public void processIntoMibDefinition(
            OutputStream os,
            Class<?> ... inputClasses)
            throws Exception{
        Date date = new Date();
        String dateStr = dateFormat.format(date);

        objTemplate = cfg.getTemplate("ModuleDefinition.template");
        Map<String, Object> data = new HashMap<String, Object>();

        //Generate the mib module header
        //TODO: allow this value to be provided on the commandline
        data.put(MOD_DEF_NAME, "annotated_definition_name");
        data.put(MOD_NAME, "annotated_module_name");
        data.put(MOD_TS_UPDATED, dateStr);
        data.put(MOD_ORG_NAME, "Company");
        data.put(MOD_CONTACT, "support@company.com");
        data.put(MOD_DESC, "N/A");
        data.put(MOD_TS_REVISION, dateStr);
        data.put(MOD_OID, "");
        data.put(MOD_COMMENT, "N/A");

        List<ObjDef> objDefs = new ArrayList<ObjDef>();
        for(Class<?> aCls: inputClasses){
            //Generate mib object definition
            processFieldsInto( aCls.getDeclaredFields(), FieldSubAccessor,objDefs );
            processFieldsInto( aCls.getDeclaredMethods(), MethodSubAccessor,objDefs );
        }
        data.put("obj_defs", objDefs);

        /**
         * TODOs:
         * 1. Allow more parameters to be passed in
         * 2. Make use of object-identity, and other decorative mib syntax
         * 3. Remove copy and paste copy from AnnotatedStatsModule
         */

        // Console output
        Writer out = new OutputStreamWriter(System.out);
        objTemplate.process(data, out);
        out.flush();
    }

    public static void main(String[] argv) throws Exception{
        if(argv.length == 0){
            System.out.println("No class path provided");
            return;
        }

        Class<?>[] inpClasses = new Class<?>[argv.length];
        for(int i=0;i<argv.length;i++){
            inpClasses[i] = Class.forName(argv[i]);
        }

        AnnotatedMibFileGenerator generator = new AnnotatedMibFileGenerator("./conf/mib/template");
        generator.processIntoMibDefinition(System.out, inpClasses);
    }
}
