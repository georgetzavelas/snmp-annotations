package com.tzavelas.snmp;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A custom annotation to mark a field or zero-arg method in a class to be
 * used as a MIB Object. The decorated field will only be read-only
 *
 * Of all the attributes defined, only type and oid is relevant for
 * server implementation. The other attributes are used to generate
 * SMIv2 MIB definition.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface MibObject {
    //TODO: support SNMP Tables
    /**
     * The syntax/type of the MIB object, valid values are restricted to:
     *     - Integer32
     *     - Counter64
     *     - OctetString
     *
     * If unspecified, null or "", it will be using the type of the following mapping:
     *     - (Java) byte, short, int    => (SNMP) Integer32
     *     - (Java) long                => (SNMP) Counter64
     *     - (Java) String              => (SNMP) OctetString
     *     - (Java) other type          => (SNMP) OctetString   (Use the .toString())
     **/
    String type() default "";

    /** OID of the MIB object **/
    String oid() default "";

    /** Description of this mib object **/
    String description() default "";

    /**
     * Status of this MIB object. Values can be "current","deprecated", or "obsolete".
     * default is "current".
     **/
    String status() default "current";

    /** A one-liner comment to add to the generated MIB Object block. */
    String comment() default "";

    /** A name for the object. */
    String name() default "";

    //String access() default "read-only";  -- let's not support write yet
}