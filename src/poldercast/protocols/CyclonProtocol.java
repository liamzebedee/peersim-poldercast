package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;
import poldercast.util.GossipMsg;
import poldercast.util.NodeProfile;
import poldercast.util.PublishMsg;

public class CyclonProtocol implements EDProtocol, CDProtocol {
    private int bitsSent = 0;
    private int bitsReceived = 0;
    private int messagesSent = 0;
    private int messagesReceived = 0;

    public NodeProfile[] routingTable = new NodeProfile[20];
    public final int protocolID;
    public static final String CYCLON = "cyclon";

    public CyclonProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(CYCLON);
        System.out.println(this.protocolID);
    }

    @Override
    public Object clone() {
        Object clone = null;
        try {
            clone = super.clone();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return clone;
    }

    public void nextCycle(Node node, int protocolID) {
        CyclonProtocol protocol = (CyclonProtocol) node.getProtocol(protocolID);
        GossipMsg msg = new GossipMsg(routingTable, GossipMsg.Types.GOSSIP_QUERY);
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;
        // Select oldest node
        NodeProfile oldestNode = protocol.routingTable[0];
        for(NodeProfile profile : protocol.routingTable) {
            if(profile == null) continue;
            if(profile.getAge() > oldestNode.getAge()) oldestNode = profile;
        }

        this.sendMsg(node, oldestNode, msg);
    }

    public void processEvent(Node node, int protocolID, java.lang.Object event) {
        CyclonProtocol protocol = (CyclonProtocol) node.getProtocol(protocolID);
        if(event instanceof GossipMsg) {
            GossipMsg gossipMsg = (GossipMsg) event;
            protocol.bitsReceived += gossipMsg.getSizeInBits();
            protocol.messagesReceived++;

        } else {
            throw new RuntimeException("The PolderCast-protocol is only capable of handling events of type GossipMsg/PublishMsg" +
                    ", while an event of type " + event.getClass().getName() + " has been received.");
        }
    }

    private Transport getTransportForProtocol(Node node, int pid) {
        return (Transport) node.getProtocol(FastConfig.getTransport(pid));
    }

    private void sendMsg(Node from, Node to, Object msg) {
        int protocolID = this.protocolID;
        Transport t = getTransportForProtocol(from, protocolID);
        t.send(from, to, msg, protocolID);
    }
}
