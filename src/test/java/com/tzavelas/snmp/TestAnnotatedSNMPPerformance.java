package com.tzavelas.snmp;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.snmp4j.agent.BaseAgent;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariantVariable;

/**
 * Class to compare reflection vs native access.
 * Due to Java HotSpot VM, this comparison is not valid.
 *
 */
public class TestAnnotatedSNMPPerformance {
    private static final String OID_PREFIX = "1.3.6.1.4.1.29506.100";
    private static int PORT_POJO = 1161;
    private static int PORT_ANNOTATED = 2161;

    private OID byteOID = new OID(OID_PREFIX + ".1.0");
    private OID shortOID = new OID(OID_PREFIX + ".2.0");
    private OID intOID = new OID(OID_PREFIX + ".3.0");
    private OID longOID = new OID(OID_PREFIX + ".4.0");
    private OID strOID = new OID(OID_PREFIX + ".5.0");

    private String host;
    private int execTimes;

    @MibObject(oid=OID_PREFIX+".1")
    private byte privByteVal = (byte)1;
    @MibObject(oid=OID_PREFIX+".2")
    private short privShortVal = (short)2;
    @MibObject(oid=OID_PREFIX+".3")
    private int privIntVal = 3;
    @MibObject(oid=OID_PREFIX+".4")
    private long privLongVal = 4l;
    @MibObject(oid=OID_PREFIX+".5")
    private String privStrVal = "ABCDEDFGAAJFKLAJ";

    private MOScalar moByte = new MOScalar(byteOID, MOAccessImpl.ACCESS_READ_ONLY, new Integer32(101));
    private MOScalar moShort = new MOScalar(shortOID, MOAccessImpl.ACCESS_READ_ONLY, new Integer32(102));
    private MOScalar moInt = new MOScalar(intOID, MOAccessImpl.ACCESS_READ_ONLY, new Integer32(103));
    private MOScalar moLong = new MOScalar(longOID, MOAccessImpl.ACCESS_READ_ONLY, new Counter64(104l));
    private MOScalar moString = new MOScalar(strOID, MOAccessImpl.ACCESS_READ_ONLY, new OctetString("asdbasdfasdfER"));


    private int getIntMethod(){
        return privIntVal;
    }

    public TestAnnotatedSNMPPerformance(String host, int execTimes){
        this.host = host;
        this.execTimes = execTimes;
    }

    private BaseAgent buildPojoAgent(String host, int port) throws Exception{
        SimpleSNMPAgent ret = new SimpleSNMPAgent(host, port);
        ret.start();
        ret.registerManagedObject(moByte);
        ret.registerManagedObject(moShort);
        ret.registerManagedObject(moInt);
        ret.registerManagedObject(moLong);
        ret.registerManagedObject(moString);
        return ret;
    }

    private BaseAgent buildAnnotatedAgent(String host, int port) throws Exception{
        SimpleSNMPAgent ret = new SimpleSNMPAgent(host, port);
        ret.start();

        AnnotatedStatsMOGroup moGroup = new AnnotatedStatsMOGroup();
        moGroup.addAnnotatedMibObject(this);
        moGroup.registerMOs(ret.getServer(), null);

        return ret;
    }

    private SimpleSNMPClient buildSNMPClient(String host, int port) throws Exception{
        SimpleSNMPClient client = new SimpleSNMPClient(host+"/"+port);
        client.start();
        return client;
    }

    private long getFetchDuration(SimpleSNMPClient client, OID oid, int times)throws Exception{
        long startTime = System.nanoTime();
        for(int i=times;i>0;i--){
            ResponseEvent ev = client.getMibObjects(oid);
        }
        long endTime = System.nanoTime();
        return endTime - startTime;
    }

    private long[] getFetchDurations( String host, int port, OID[] oids, int times ) throws Exception{
        BaseAgent agent = buildPojoAgent(host, port);
        SimpleSNMPClient client = buildSNMPClient(host, port);

        long[] ret = new long[oids.length];
        int idx = 0;
        for(OID oid: oids){
             ret[idx++] = getFetchDuration( client, oid, times);
        }
        client.shutdown();
        agent.stop();

        return ret;
    }


    public void genComparisionReport(PrintStream ps)throws Exception{
        OID[] oids = new OID[]{byteOID, shortOID, intOID, longOID, strOID};

        //TODO: java warmup problem: weird, having either of them executed first gets differnet numbers
        long[] durationAnnotated = getFetchDurations(host, PORT_ANNOTATED, oids, execTimes );
        long[] durationPojo = getFetchDurations(host, PORT_POJO, oids, execTimes );

        ps.println(String.format( "%25s\t%10s\t%10s\n", "OID", "POJO", "Annotated" ));
        for(int i=0;i<oids.length;i++){
            ps.println(String.format( "%25s\t%10d\t%10d\n",
                    oids[i], durationPojo[i], durationAnnotated[i] ));
        }
    }

    public void genVarAccessComparisonReport(PrintStream ps) throws Exception{
        MOScalar simpleInt = new MOScalar(
                intOID,
                MOAccessImpl.ACCESS_READ_ONLY,
                new Integer32(128708) );

        MOScalar annotatedInt = new MOScalar(
                intOID,
                MOAccessImpl.ACCESS_READ_ONLY,
                new VariantVariable(
                        new Integer32(0),
                        new DynamicVariantVariableCallback
                                .DynamicVariantVariableCallbackBuilder(this)
                                .field(this.getClass().getDeclaredField("privIntVal"))
                                .build()));

        MOScalar annotatedIntMethod = new MOScalar(
                intOID,
                MOAccessImpl.ACCESS_READ_ONLY,
                new VariantVariable(
                        new Integer32(0),
                        new DynamicVariantVariableCallback
                                .DynamicVariantVariableCallbackBuilder(this)
                                .method(this.getClass().getDeclaredMethod("getIntMethod"))
                                .build()));

        long duration[] = new long[3];
        int idx = 0;
        for(MOScalar mo: new MOScalar[]{ simpleInt, annotatedInt, annotatedIntMethod } ){
            long timeStarted = System.nanoTime();
            for(int i = execTimes;i>0;i--){
                Object obj = mo.getValue();
            }
            long timeEnded = System.nanoTime();
            duration[idx++] = timeEnded - timeStarted;
        }
        ps.println(String.format( "MOScalar Access Comparison"));
        ps.println(String.format( "%20s\t%20s\t%20s\n","Pojo", "AnnotatedVarible", "AnnotatedMethod"));
        ps.println(String.format( "%20d\t%20d\t%20d\n", duration[0], duration[1], duration[2] ));
    }

    public void genReflectionAccessComparison(PrintStream ps) throws Exception{
        int idx = 0;
        long durationDirect = 0;
        long durationFieldRefl = 0;
        long durationMethodRefl = 0;
        Method method = this.getClass().getDeclaredMethod("getIntMethod");
        Field field = this.getClass().getDeclaredField("privIntVal");
        field.setAccessible(true);
        method.setAccessible(true);
        long startTime = System.nanoTime();
        for(int i = execTimes;i>0;i--){
            int v = privIntVal;
        }
        durationDirect = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        for(int i = execTimes;i>0;i--){
//            boolean isAccessible = field.isAccessible();
//            try{
//                field.setAccessible(true);
                int v = field.getInt(this);
//            }finally{
//                field.setAccessible(isAccessible);
//            }
        }
        durationFieldRefl = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        for(int i = execTimes;i>0;i--){
//            boolean isAccessible = field.isAccessible();
//            try{
//
                int v = (Integer) method.invoke(this);
//            }finally{
//                field.setAccessible(isAccessible);
//            }
        }
        durationMethodRefl = System.nanoTime() - startTime;

        ps.println(String.format( "Reflection Access Comparison"));
        ps.println(String.format( "%20s\t%20s\t%20s\n","Pojo", "AnnotatedVarible", "AnnotatedMethod"));
        ps.println(String.format( "%20d\t%20d\t%20d\n", durationDirect, durationFieldRefl, durationMethodRefl ));
    }

    public static void main(String argv[])throws Exception{
        //There is always one warm up and one test
        //TODO: get around the java warmup issues, perhaps https://code.google.com/p/caliper/

        TestAnnotatedSNMPPerformance obj = new TestAnnotatedSNMPPerformance("10.16.1.169", 1000000);
        obj.genVarAccessComparisonReport(System.out);
        obj.genVarAccessComparisonReport(System.out);

        obj.genReflectionAccessComparison(System.out);
        obj.genReflectionAccessComparison(System.out);

//        obj.genComparisionReport(System.out);
//        obj.genComparisionReport(System.out);
    }
}
