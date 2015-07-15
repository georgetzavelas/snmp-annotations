package com.tzavelas.snmp;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.agent.DuplicateRegistrationException;
import org.snmp4j.smi.OID;

/**
 * Test if the stats annotation work well.
 */

class ObjectDerived{
    @Override
    public String toString(){
        return "ObjectDrived";
    }
}

class SampleTestStats{
    protected static final String OID_PREFIX = "1.3.6.1.4.1.29506.1";
    private static final String BASE_STR = "5";

    @MibObject(oid=OID_PREFIX+".1")
    private byte privByteVar = (byte)1;

    @MibObject(oid=OID_PREFIX+".2")
    private Short privShortVar = (short)2;

    @MibObject(oid=OID_PREFIX+".3")
    private int privIntVar = 3;

    @MibObject(oid=OID_PREFIX+".4")
    public long privLongVar = 4;

    @MibObject(oid=OID_PREFIX+".5")
    private String privStrVar = BASE_STR;

    @MibObject(oid=OID_PREFIX+".6")
    private ObjectDerived privObjDerived = new ObjectDerived();

    @MibObject(oid=OID_PREFIX+".7", type="OctetString")
    private int privManualInt = 7;

    @MibObject(type="Integer32")
    private int privIncompleteAnnoation = 8;

    @MibObject(oid=OID_PREFIX+".11")
    private Byte getPrivateByte(){
        return (byte)(100+privByteVar);
    }

    @MibObject(oid=OID_PREFIX+".12")
    private short getPrivateShort(){
        return (short)(100+privShortVar);
    }

    @MibObject(oid=OID_PREFIX+".13")
    private int getPrivateInt(){
        return 100+privIntVar;
    }

    @MibObject(oid=OID_PREFIX+".14")
    public long getPrivateLong(){
        return 100+privLongVar;
    }

    @MibObject(oid=OID_PREFIX+".15")
    private String getPrivateString(){
        return "STR_"+BASE_STR;
    }

    @MibObject(oid=OID_PREFIX+".16")
    private ObjectDerived getPrivateObject(){
        return privObjDerived;
    }

    @MibObject(oid=OID_PREFIX+".23")
    private static int pubStaticInt = 23;

    @MibObject(oid=OID_PREFIX+".24")
    public static long privStaticLong =  24;

    @MibObject(oid=OID_PREFIX+".33")
    private static int getPublicStaticInt(){
        return 100+pubStaticInt;
    }

    @MibObject(oid=OID_PREFIX+".34")
    public static long getPrivateStaticLong(){
        return 100+privStaticLong;
    }

    public static void setPublicStaticInt(int v){
        pubStaticInt = v;
    }

    public static void setPrivateStaticLong(long v){
        privStaticLong = v;
    }

    public void setPrivateByte(byte v){
        privByteVar = v;
    }

    public void setPrivateShort(short v){
        privShortVar = v;
    }

    public void setPrivateInt(int v){
        privIntVar = v;
    }

    public void setPrivateLong(long v){
        privLongVar = v;
    }

    public void setPrivateStr(String v){
        privStrVar = v;
    }
}

public class TestAnnotatedSNMPServer {
    private static VanillaSNMPClient client = null;
    private static VanillaSNMPAgent agent = null;
    private static SampleTestStats statsObj = null;
    private static AnnotatedStatsMOGroup moGroup = null;

    private static int testPort = 2001;
    private static String testIP = "127.0.0.1";
    //private static

    @BeforeClass
    public static void setupClass() throws IOException, DuplicateRegistrationException{
        String address = testIP+"/"+testPort;

        statsObj = new SampleTestStats();
        moGroup = new AnnotatedStatsMOGroup();
        moGroup.addAnnotatedMibObject(statsObj);

        agent = new VanillaSNMPAgent(address);
        agent.start();

        moGroup.registerMOs(agent.getServer(), null);

        client = new VanillaSNMPClient(address);
        client.start();
    }

    public static void teardownClass(){
        moGroup.unregisterMOs(agent.getServer(), null);

        agent.stop();
    }

    private void testOIDIntVal(String oid, int expected) throws IOException{
        int val = client.getMibObjectAsInteger( new OID(oid));
        Assert.assertTrue( "OID "+oid+" Int value is not as expected: "+expected+", actual: "+val,
                val == expected );
    }

    private void testOIDLongVal(String oid, long expected) throws IOException{
        long val = client.getMibObjectAsLong( new OID(oid));
        Assert.assertTrue( "OID "+oid+" Long value is not as expected: "+expected+", actual: "+val,
                val == expected );
    }

    ///////////////////////////////////////////////////////////////
    //Test Accessing private attributes
    ///////////////////////////////////////////////////////////////
    private static final String OID_PRIVATE_FIELD = SampleTestStats.OID_PREFIX + ".3.0";
    private static final String OID_PRIVATE_FUNC = SampleTestStats.OID_PREFIX + ".13.0";
    private static final String OID_PRIVATE_STATIC_FIELD = SampleTestStats.OID_PREFIX + ".23.0";
    private static final String OID_PRIVATE_STATIC_FUNC = SampleTestStats.OID_PREFIX + ".33.0";

    @Test
    public void canAccessPrivateField() throws Exception{
        testOIDIntVal(OID_PRIVATE_FIELD, 3);
    }

    @Test
    public void canAccessPrivateFunction()throws Exception{
        testOIDIntVal(OID_PRIVATE_FUNC, 100+3);
    }

    @Test
    public void canAccessPrivateStaticField()throws Exception{
        testOIDIntVal(OID_PRIVATE_STATIC_FIELD, 23);
    }

    @Test
    public void canAccessPrivateStaticMethod()throws Exception{
        testOIDIntVal(OID_PRIVATE_STATIC_FUNC, 100+23);
    }

    ///////////////////////////////////////////////////////////////
    //Test Accessing public attributes
    ///////////////////////////////////////////////////////////////
    private static final String OID_PUBLIC_FIELD = SampleTestStats.OID_PREFIX + ".4.0";
    private static final String OID_PUBLIC_FUNC = SampleTestStats.OID_PREFIX + ".14.0";
    private static final String OID_PUBLIC_STATIC_FIELD = SampleTestStats.OID_PREFIX + ".24.0";
    private static final String OID_PUBLIC_STATIC_FUNC = SampleTestStats.OID_PREFIX + ".34.0";

    @Test
    public void canAccessPublicField()throws Exception{
        testOIDLongVal(OID_PUBLIC_FIELD, 4);
    }

    @Test
    public void canAccessPublicFunction()throws Exception{
        testOIDLongVal(OID_PUBLIC_FUNC, 100+4);
    }

    @Test
    public void canAccessPublicStaticField()throws Exception{
        testOIDLongVal(OID_PUBLIC_STATIC_FIELD, 24);
    }

    @Test
    public void canAccessPublicStaticMethod()throws Exception{
        testOIDLongVal(OID_PUBLIC_STATIC_FUNC, 100+24);
    }

    ///////////////////////////////////////////////////////////////
    //Test Accessing all different annotated types
    ///////////////////////////////////////////////////////////////
    @Test
    public void canAccessAnnotatedByte() throws IOException{
        testOIDIntVal(SampleTestStats.OID_PREFIX+".1.0", 1);
    }

    @Test
    public void canAccessAnnotatedShort() throws IOException{
        testOIDIntVal(SampleTestStats.OID_PREFIX+".2.0", 2);
    }

    @Test
    public void canAccessAnnotatedInt() throws IOException{
        testOIDIntVal(SampleTestStats.OID_PREFIX+".3.0", 3);
    }

    @Test
    public void canAccessAnnotatedLong() throws IOException{
        testOIDLongVal(SampleTestStats.OID_PREFIX+".4.0", 4);
    }

    @Test
    public void canAccessAnnotatedString() throws IOException{
        OID oid = new OID( SampleTestStats.OID_PREFIX+".5.0");
        String result = client.getMibObjectAsString( new OID(oid));
        result.equals("5");
    }

    @Test
    public void canAccessAnnotatedObject() throws IOException{
        OID oid = new OID( SampleTestStats.OID_PREFIX+".6.0");
        String result = client.getMibObjectAsString( new OID(oid));
        result.equals("ObjectDrived");
    }

    ///////////////////////////////////////////////////////////////
    //Test value change
    ///////////////////////////////////////////////////////////////
    @Test
    public void canAccessChangedInt() throws Exception{
        try{
            int newVal = 12341234;
            statsObj.setPrivateInt(newVal);
            testOIDIntVal(SampleTestStats.OID_PREFIX+".3.0", newVal);
        }finally{
            statsObj.setPrivateInt(3);  //set back to default value
        }
    }

    @Test
    public void canAccessChangedLong() throws Exception{
        try{
            long newVal = 1255555;
            statsObj.setPrivateLong(newVal);
            testOIDLongVal(SampleTestStats.OID_PREFIX+".4.0", newVal);
        }finally{
            statsObj.setPrivateLong(4);  //set back to default value
        }
    }

    @Test
    public void canAccessChangedShortObj() throws Exception{
        try{
            short newVal = 6548;
            statsObj.setPrivateShort(newVal);
            testOIDIntVal(SampleTestStats.OID_PREFIX+".2.0", newVal);
        }finally{
            statsObj.setPrivateShort((short)2);  //set back to default value
        }
    }

    @Test
    public void canAccessChangedString() throws Exception{
        try{
            String newVal = "NewString";
            statsObj.setPrivateStr(newVal);
            String result = client.getMibObjectAsString(
                    new OID(SampleTestStats.OID_PREFIX+".5.0"));
            result.equals(newVal);
        }finally{
            statsObj.setPrivateStr("5");  //set back to default value
        }
    }

    ///////////////////////////////////////////////////////////////
    //Test manual type specification works
    ///////////////////////////////////////////////////////////////
    @Test
    public void canAccessManualTypeIntAsString() throws Exception{
        OID oid = new OID( SampleTestStats.OID_PREFIX+".7.0");
        String result = client.getMibObjectAsString( new OID(oid));
        result.equals("7");
    }

    ///////////////////////////////////////////////////////////////
    //Test impcomlete annotation not processed
    ///////////////////////////////////////////////////////////////
    @Test
    public void cannotAddIncompleteAnnotation() throws Exception{
        SampleTestStats statsObj2 = new SampleTestStats();
        AnnotatedStatsMOGroup moGroup2 = new AnnotatedStatsMOGroup();
        int moAdded = moGroup2.addAnnotatedMibObject(statsObj2);

        int moDeclared = 0;
        for(Field f: statsObj2.getClass().getDeclaredFields() ){
            if(!f.isSynthetic() && f.getAnnotation(MibObject.class) != null){
                moDeclared ++;
            }
        }

        for(Method m: statsObj2.getClass().getDeclaredMethods() ){
            if(!m.isSynthetic() && m.getAnnotation(MibObject.class) != null){
                moDeclared ++;
            }
        }

        Assert.assertTrue("MO added is incorrect: declared: "+moDeclared+", added: "+moAdded,
                (moDeclared-1) == moAdded);
    }
}
