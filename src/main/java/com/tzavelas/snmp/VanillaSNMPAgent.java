package com.tzavelas.snmp;

import java.io.File;
import java.io.IOException;

import org.snmp4j.TransportMapping;
import org.snmp4j.agent.BaseAgent;
import org.snmp4j.agent.CommandProcessor;
import org.snmp4j.agent.DuplicateRegistrationException;
import org.snmp4j.agent.MOGroup;
import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.mo.MOTable;
import org.snmp4j.agent.mo.MOTableRow;
import org.snmp4j.agent.mo.snmp.RowStatus;
import org.snmp4j.agent.mo.snmp.SnmpCommunityMIB;
import org.snmp4j.agent.mo.snmp.SnmpNotificationMIB;
import org.snmp4j.agent.mo.snmp.SnmpTargetMIB;
import org.snmp4j.agent.mo.snmp.StorageType;
import org.snmp4j.agent.mo.snmp.VacmMIB;
import org.snmp4j.agent.security.MutableVACM;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;
import org.snmp4j.transport.TransportMappings;
/**
 * A very simple minimalistic SNMP Agent
 *
 * @source: http://www.jayway.com/2010/05/21/introduction-to-snmp4j/
 */
public class VanillaSNMPAgent extends BaseAgent {
    private String address;

    /**
     * Creates a minimalistic SNMP agent
     *
     * @param address: should match format IP/Port. e.g. 0.0.0.0/2001
     * @throws IOException
     */
    public VanillaSNMPAgent(String address) throws IOException {
        // These files does not exist and are not used but has to be specified
        // Read snmp4j docs for more info
        super(new File("conf.agent"), new File("bootCounter.agent"),
                new CommandProcessor(
                        new OctetString(MPv3.createLocalEngineID())));
        this.address = address;
    }

    public VanillaSNMPAgent(String host, int port) throws IOException {
        this(host+"/"+port);
    }

    /**
     * Clients can register the MO they need
     */
    public void registerManagedObject(ManagedObject mo) {
        try {
            server.register(mo, null);
        } catch (DuplicateRegistrationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void unregisterManagedObject(MOGroup moGroup) {
        moGroup.unregisterMOs(server, getContext(moGroup));
    }

    /**
     * Minimal View based Access Control
     *
     * http://www.faqs.org/rfcs/rfc2575.html
     */
    @Override
    protected void addViews(VacmMIB vacm) {
        vacm.addGroup(SecurityModel.SECURITY_MODEL_SNMPv2c, new OctetString(
                "cpublic"), new OctetString("v1v2group"),
                StorageType.nonVolatile);

        vacm.addAccess(new OctetString("v1v2group"), new OctetString("public"),
                SecurityModel.SECURITY_MODEL_ANY, SecurityLevel.NOAUTH_NOPRIV,
                MutableVACM.VACM_MATCH_EXACT, new OctetString("fullReadView"),
                new OctetString("fullWriteView"), new OctetString(
                        "fullNotifyView"), StorageType.nonVolatile);

        vacm.addViewTreeFamily(new OctetString("fullReadView"), new OID("1.3"),
                new OctetString(), VacmMIB.vacmViewIncluded,
                StorageType.nonVolatile);
    }

    @Override
    protected void initTransportMappings() throws IOException {
        Address addr = GenericAddress.parse(address);

        transportMappings = new TransportMapping[1];
        transportMappings[0] = TransportMappings.getInstance()
                .createTransportMapping(addr);
    }

    /**
     * Start method invokes some initialization methods needed to
     * start the agent
     * @throws IOException
     */
    public void start() throws IOException {
        init();
        // This method reads some old config from a file and causes
        // unexpected behavior.
        // loadConfig(ImportModes.REPLACE_CREATE);
        addShutdownHook();
        getServer().addContext(new OctetString("public"));
        finishInit();
        run();
        sendColdStartNotification();
    }

    /**
     * The table of community strings configured in the SNMP
     * engine's Local Configuration Datastore (LCD).
     *
     * We only configure one, "public".
     */
    @Override
    protected void addCommunities(SnmpCommunityMIB communityMIB) {
        Variable[] com2sec = new Variable[] {
                new OctetString("public"), // community name
                new OctetString("cpublic"), // security name
                getAgent().getContextEngineID(), // local engine ID
                new OctetString("public"), // default context name
                new OctetString(), // transport tag
                new Integer32(StorageType.nonVolatile), // storage type
                new Integer32(RowStatus.active) // row status
        };
        MOTable table = communityMIB.getSnmpCommunityEntry();
        MOTableRow row = table.createRow(
                new OctetString("public2public").toSubIndex(true), com2sec);
        table.addRow(row);
    }


    ///////////////////////////////////////////////////////////
    // Empty Implementation
    ///////////////////////////////////////////////////////////
    @Override
    protected void addNotificationTargets(SnmpTargetMIB targetMIB,
            SnmpNotificationMIB notificationMIB) {
        // Intentional empty
    }

    /**
     * We let clients of this agent register the MO they
     * need so this method does nothing
     */
    @Override
    protected void registerManagedObjects() {
        // Intentional empty
    }

    /**
     * User based Security Model, only applicable to
     * SNMP v.3
     *
     */
    @Override
    protected void addUsmUser(USM usm) {
        // Intentional empty
    }


    @Override
    protected void unregisterManagedObjects() {
        // here we should unregister those objects previously registered...
    }
}
