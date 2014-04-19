package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;
import poldercast.util.GossipMsg;
import poldercast.util.NodeProfile;
import poldercast.util.PolderCastNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class CyclonProtocol implements CDProtocol, EDProtocol, Linkable {
    private int bitsSent = 0;
    private int bitsReceived = 0;
    private int messagesSent = 0;
    private int messagesReceived = 0;
    public ArrayList<NodeProfile> routingTable = new ArrayList<NodeProfile>(MAX_VIEW_SIZE * 2);

    public final int protocolID;
    public static final int MAX_VIEW_SIZE = 20;
    public static final String CYCLON = "cyclon";

    public CyclonProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(CYCLON);
    }

    @Override
    public Object clone() {
        CyclonProtocol clone = null;
        try {
            clone = (CyclonProtocol) super.clone();
            clone.routingTable = this.getRoutingTableCopy();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return clone;
    }

    public void nextCycle(Node node, int protocolID) {
        PolderCastNode thisNode = (PolderCastNode) node;
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);

        // Increment the age of all nodes
        Iterator<NodeProfile> nodeProfileIterator = protocol.routingTable.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            profile.incrementAge();
        }

        // Gossip
        GossipMsg msg = new GossipMsg(protocol.getRoutingTableCopy(), GossipMsg.Types.GOSSIP_QUERY, thisNode);
        // TODO synchronize
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;
        // Select oldest node
        NodeProfile oldestNode = protocol.routingTable.get(0);
        nodeProfileIterator = protocol.routingTable.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            if (profile.getAge() > oldestNode.getAge()) oldestNode = profile;
        }

        protocol.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
    }

    public void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastNode thisNode = (PolderCastNode) node;
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);
        GossipMsg receivedGossipMsg = (GossipMsg) event;

        if(!(event instanceof GossipMsg)) {
            throw new RuntimeException("CyclonProtocol should only receive GossipMsg events");
        }

        if(receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
            protocol.bitsReceived += receivedGossipMsg.getSizeInBits();
            protocol.messagesReceived++;
            ArrayList<NodeProfile> profilesReceived = receivedGossipMsg.getNodeProfiles();
            // Set age to 0 for all profiles received
            Iterator<NodeProfile> nodeProfileIterator = profilesReceived.iterator();
            while(nodeProfileIterator.hasNext()) {
                NodeProfile profile = nodeProfileIterator.next();
                profile.resetAge();
                // Remove our profile if any
                if(profile.getID().equals(thisNode.getNodeProfile().getID())) {
                    nodeProfileIterator.remove();
                }
                // Remove any duplicates before we add
                if(protocol.routingTable.contains(profile)) {
                    nodeProfileIterator.remove();
                }
            }

            // Now we must merge, adding new entries and then replacing existing entries if necessary
            protocol.routingTable.addAll(profilesReceived);
            while(protocol.routingTable.size() > MAX_VIEW_SIZE) {
                // Remove from the front of the list, as we favour new nodes over old ones
                protocol.routingTable.remove(0);
            }

            // Send a reply
            GossipMsg replyGossipMsg = new GossipMsg(protocol.getRoutingTableCopy(), GossipMsg.Types.GOSSIP_RESPONSE, thisNode);
            protocol.bitsSent += replyGossipMsg.getSizeInBits();
            protocol.messagesSent++;
            protocol.sendMsg(thisNode, receivedGossipMsg.getSender(), replyGossipMsg, protocolID);

        } else if(receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {

        } else {
            throw new RuntimeException("Bad GossipMsg");
        }
    }

    private Transport getTransportForProtocol(Node node, int pid) {
        return (Transport) node.getProtocol(FastConfig.getTransport(pid));
    }

    private void sendMsg(Node from, Node to, Object msg, int protocolID) {
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
    // NOTE this method should only be called at node initialization and by a bootstrapping class
    // Cyclon uses gossipping to find and add new neighbours
    public boolean addNeighbor(Node neighbour) {
        NodeProfile profile = ((PolderCastNode) neighbour).getNodeProfile();

        if(this.routingTable.size() == 20) {
            //throw new RuntimeException("We shouldn't be attempting to bootstrap with more than 20 neighbours");
            return true;
        }

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

    public ArrayList<NodeProfile> getRoutingTableCopy() {
        ArrayList<NodeProfile> routingTableCopy = new ArrayList<NodeProfile>(this.routingTable);
        Collections.copy(routingTableCopy, this.routingTable);
        return routingTableCopy;
    }
}
