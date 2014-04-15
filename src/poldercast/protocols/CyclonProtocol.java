package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;
import poldercast.initializers.PolderCastIdAssigner;
import poldercast.util.GossipMsg;
import poldercast.util.NodeProfile;
import poldercast.util.PolderCastNode;

import java.util.ArrayList;

public class CyclonProtocol implements EDProtocol, CDProtocol, Linkable {
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
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);

        // Increment the age of all nodes
        for(NodeProfile profile : this.routingTable) {
            profile.incrementAge();
        }

        // Gossip
        GossipMsg msg = new GossipMsg((ArrayList<NodeProfile>) routingTable.clone(), GossipMsg.Types.GOSSIP_QUERY, thisNode);
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;
        // Select oldest node
        NodeProfile oldestNode = protocol.routingTable.get(0);
        for(NodeProfile profile : protocol.routingTable) {
            if(profile.getAge() > oldestNode.getAge()) oldestNode = profile;
        }

        protocol.sendMsg(thisNode, oldestNode.getNode(), msg);
    }

    public void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastNode thisNode = (PolderCastNode) node;
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);

        if(event instanceof GossipMsg) {
            GossipMsg receivedGossipMsg = (GossipMsg) event;
            protocol.bitsReceived += receivedGossipMsg.getSizeInBits();
            protocol.messagesReceived++;
            ArrayList<NodeProfile> profilesReceived = receivedGossipMsg.getNodeProfiles();
            // Set age to 0 for all profiles received
            for(NodeProfile profile : profilesReceived) {
                profile.resetAge();
                // Remove our profile if any
                if(profile.getID().equals(thisNode.getID())) profilesReceived.remove(profile);
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

    /*
     * Performs cleanup when removed from the network.
     * This is called by the host node when its fail state is set to Fallible.DEAD.
     * It is very important that after calling this method,
     * NONE of the methods of the implementing object are guaranteed to work any longer.
     * They might throw arbitrary exceptions, etc. The idea is that after calling this, typically no one should access this object.
     * However, as a recommendation, at least toString should be guaranteed to execute normally, to aid debugging.
     */
    public void onKill() {
        
    }

    // Add a neighbor to the current set of neighbors.
    public boolean addNeighbor(Node neighbour) {
        NodeProfile profile = ((PolderCastNode)neighbour).getNodeProfile();
        if(this.routingTable.contains(profile)) {
            return false;
        } else {
            this.routingTable.add(profile);
            return true;
        }
    }

    // Returns true if the given node is a member of the neighbor set.
    public boolean contains(Node neighbor) {
        NodeProfile profile = ((PolderCastNode)neighbor).getNodeProfile();
        return this.routingTable.contains(neighbor);
    }

    // Returns the size of the neighbor list.
    public int degree() {
        return this.routingTable.size();
    }

    // Returns the neighbor with the given index.
    public Node getNeighbor(int i) {
        return this.routingTable.get(i).getNode();
    }

    // A possibility for optimization.
    public void pack() {}

}
