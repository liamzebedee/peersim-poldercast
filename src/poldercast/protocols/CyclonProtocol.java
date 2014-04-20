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
import poldercast.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class CyclonProtocol implements CDProtocol, EDProtocol, Linkable {
    public int bitsSent = 0;
    public int bitsReceived = 0;
    public int messagesSent = 0;
    public int messagesReceived = 0;
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

    public synchronized void nextCycle(Node node, int protocolID) {
        PolderCastNode thisNode = (PolderCastNode) node;
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);

        // Increment the age of all nodes
        Iterator<NodeProfile> nodeProfileIterator = protocol.routingTable.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            profile.incrementAge();
        }

        // Select oldest node
        NodeProfile oldestNode = protocol.routingTable.get(0);
        nodeProfileIterator = protocol.routingTable.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            if (profile.getAge() > oldestNode.getAge()) oldestNode = profile;
        }
        // Gossip
        ArrayList<NodeProfile> nodesToSend = protocol.getRoutingTableCopy();
        // remove the target and replace with our node
        nodesToSend.set(nodesToSend.indexOf(oldestNode), thisNode.getNodeProfile());
        protocol.routingTable.remove(oldestNode); // proactive removal to combat churn
        GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;

        Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastNode thisNode = (PolderCastNode) node;
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);
        GossipMsg receivedGossipMsg = (GossipMsg) event;

        if (!(event instanceof GossipMsg)) {
            throw new RuntimeException("CyclonProtocol should only receive GossipMsg events");
        }

        protocol.bitsReceived += receivedGossipMsg.getSizeInBits();
        protocol.messagesReceived++;

        if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());

            // Send a reply
            ArrayList<NodeProfile> nodesToSend = protocol.getRoutingTableCopy();
            // remove the target and replace with our node
            nodesToSend.set(nodesToSend.indexOf(receivedGossipMsg.getSender().getNodeProfile()), thisNode.getNodeProfile());
            GossipMsg replyGossipMsg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_RESPONSE, thisNode);
            protocol.bitsSent += replyGossipMsg.getSizeInBits();
            protocol.messagesSent++;
            Util.sendMsg(thisNode, receivedGossipMsg.getSender(), replyGossipMsg, protocolID);

        } else if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {
            // Merge nodes into routing table
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());
        } else {
            throw new RuntimeException("Bad GossipMsg");
        }
    }

    public synchronized void mergeNodes(PolderCastNode thisNode, ArrayList<NodeProfile> profiles) {
        Iterator<NodeProfile> nodeProfileIterator = profiles.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            // Remove our profile if any
            if (profile.getID().equals(thisNode.getNodeProfile().getID())) {
                nodeProfileIterator.remove();
            }
            // Remove any duplicates before we add
            if (this.routingTable.contains(profile)) {
                // This is a much better place to remove the node.
                // We are going to add it back in later.
                this.routingTable.remove(profile);
            }
        }

        // Now we must merge, adding new entries and then replacing existing entries if necessary
        this.routingTable.addAll(profiles);
        while (this.routingTable.size() > MAX_VIEW_SIZE) {
            // Remove from the front of the list, as we favour new nodes over old ones
            this.routingTable.remove(0);
        }
    }

    public void communicationReceivedFromNode(NodeProfile node) {
        // If we have the node in the routing table, zero its age
        int i = this.routingTable.indexOf(node);
        if(i != -1) {
            this.routingTable.get(i).resetAge();
        }
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
            throw new RuntimeException("We shouldn't be attempting to bootstrap with more than 20 neighbours");
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
