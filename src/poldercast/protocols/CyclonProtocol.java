package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;
import poldercast.util.GossipMsg;
import poldercast.util.NodeProfile;
import poldercast.util.PolderCastNode;
import poldercast.util.PublishMsg;

import java.util.ArrayList;

public class CyclonProtocol implements EDProtocol, CDProtocol {
    private int bitsSent = 0;
    private int bitsReceived = 0;
    private int messagesSent = 0;
    private int messagesReceived = 0;

    public static final int VIEW_SIZE = 20;
    public ArrayList<NodeProfile> routingTable = new ArrayList<NodeProfile>(VIEW_SIZE*2);
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
        PolderCastNode thisNode = (PolderCastNode) node;
        // Increment the age of all nodes
        for(NodeProfile profile : this.routingTable) {
            profile.incrementAge();
        }

        // Gossip
        CyclonProtocol protocol = (CyclonProtocol) node.getProtocol(protocolID);
        GossipMsg msg = new GossipMsg((ArrayList<NodeProfile>) routingTable.clone(), GossipMsg.Types.GOSSIP_QUERY, thisNode);
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;
        // Select oldest node
        NodeProfile oldestNode = protocol.routingTable.get(0);
        for(NodeProfile profile : protocol.routingTable) {
            if(profile.getAge() > oldestNode.getAge()) oldestNode = profile;
        }

        protocol.sendMsg(thisNode, oldestNode, msg);
    }

    public void processEvent(Node node, int protocolID, java.lang.Object event) {
        CyclonProtocol protocol = (CyclonProtocol) node.getProtocol(this.protocolID);
        PolderCastNode thisNode = (PolderCastNode) node;
        if(event instanceof GossipMsg) {
            GossipMsg receivedGossipMsg = (GossipMsg) event;
            protocol.bitsReceived += receivedGossipMsg.getSizeInBits();
            protocol.messagesReceived++;
            ArrayList<NodeProfile> profilesReceived = receivedGossipMsg.getNodeProfiles();
            // Set age to 0 for all profiles received
            for(NodeProfile profile : profilesReceived) {
                profile.resetAge();
                // Remove our profile if any
                if(profile.getId().equals(thisNode.getID())) profilesReceived.remove(profile);
            }

            // Remove any duplicates before we add
            for(NodeProfile profile : profilesReceived) {
                if(this.routingTable.contains(profile)) profilesReceived.remove(profile);
            }
            // Now we must merge, adding new entries and then replacing existing entries if necessary
            this.routingTable.addAll(profilesReceived);
            while(this.routingTable.size() > VIEW_SIZE) {
                // Remove from the front of the list, as we favour new nodes over old ones
                this.routingTable.remove(0);
            }


            // Send a reply
            GossipMsg replyGossipMsg = new GossipMsg((ArrayList<NodeProfile>) this.routingTable.clone(),
                    GossipMsg.Types.GOSSIP_RESPONSE, thisNode);
            protocol.bitsSent += replyGossipMsg.getSizeInBits();
            protocol.messagesSent++;
            protocol.sendMsg(thisNode, receivedGossipMsg.getSender(), replyGossipMsg);

        } else {
            throw new RuntimeException("The PolderCast protocol is only capable of handling events of type GossipMsg/PublishMsg" +
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
