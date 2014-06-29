package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import poldercast.util.GossipMsg;
import poldercast.util.NodeProfile;
import poldercast.util.PolderCastBaseNode;
import poldercast.util.Util;

import java.util.*;

public class CyclonProtocol extends BandwidthTrackedProtocol implements CDProtocol, EDProtocol, Linkable {
    public LinkedHashSet<NodeProfile> routingTable;

    public final int protocolID;
    public final byte MAX_VIEW_SIZE;
    public final byte MAX_GOSSIP_LENGTH;
    public static final String CYCLON = "cyclon";

    public CyclonProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(CYCLON);
        this.MAX_GOSSIP_LENGTH = (byte) Configuration.getInt(configPrefix + ".maxGossipLength");
        this.MAX_VIEW_SIZE = (byte) Configuration.getInt(configPrefix + ".maxViewSize");
        this.routingTable = new LinkedHashSet<NodeProfile>(MAX_VIEW_SIZE);
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
        PolderCastBaseNode thisNode = (PolderCastBaseNode) node;
        CyclonProtocol protocol = thisNode.getCyclonProtocol();

        if(this.routingTable.isEmpty()) throw new RuntimeException("Node should be bootstrapped before first Cyclon cycle");

        // Increment the age of all nodes
        for(NodeProfile profile : protocol.routingTable) {
            profile.incrementAge();
        }

        // Select oldest node
        Iterator<NodeProfile> nodeProfileIterator = protocol.routingTable.iterator();
        NodeProfile oldestNode = nodeProfileIterator.next();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            if (profile.getAge() > oldestNode.getAge())
                oldestNode = profile;
        }

        // Gossip
        HashSet<NodeProfile> nodesToSend = protocol.getRoutingTableCopy();
        // remove the target and replace with our node
        nodesToSend.remove(oldestNode); nodesToSend.add(thisNode.getNodeProfile());
        nodeProfileIterator = nodesToSend.iterator();
        while(nodeProfileIterator.hasNext() && nodesToSend.size() > Math.min(nodesToSend.size(), protocol.MAX_GOSSIP_LENGTH)) {
            nodeProfileIterator.next();
            nodeProfileIterator.remove();
        }
        protocol.routingTable.remove(oldestNode); // proactive removal to combat churn
        GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
        protocol.messageSent(msg);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastBaseNode thisNode = (PolderCastBaseNode) node;
        CyclonProtocol protocol = (CyclonProtocol) thisNode.getProtocol(protocolID);
        GossipMsg receivedGossipMsg = (GossipMsg) event;

        if (!(event instanceof GossipMsg)) {
            throw new RuntimeException("CyclonProtocol should only receive GossipMsg events");
        }

        protocol.messageReceived(receivedGossipMsg);

        if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());

            // Send a reply
            HashSet<NodeProfile> nodesToSend = protocol.getRoutingTableCopy();
            // remove the target and replace with our node
            nodesToSend.remove(receivedGossipMsg.getSender().getNodeProfile());
            nodesToSend.add(thisNode.getNodeProfile());
            Iterator<NodeProfile> nodeProfileIterator = nodesToSend.iterator();
            while(nodeProfileIterator.hasNext() && nodesToSend.size() > Math.min(nodesToSend.size(), protocol.MAX_GOSSIP_LENGTH)) {
                nodeProfileIterator.next();
                nodeProfileIterator.remove();
            }
            GossipMsg replyGossipMsg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_RESPONSE, thisNode);
            Util.sendMsg(thisNode, receivedGossipMsg.getSender(), replyGossipMsg, protocolID);
            protocol.messageSent(replyGossipMsg);

        } else if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {
            // Merge nodes into routing table
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());
        } else {
            throw new RuntimeException("Bad GossipMsg");
        }
    }

    public synchronized void mergeNodes(PolderCastBaseNode thisNode, HashSet<NodeProfile> profiles) {
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
            Util.removeFirstInHashSet(this.routingTable);
        }
    }

    public synchronized void communicationReceivedFromNode(NodeProfile node) {
        // If we have the node in the routing table, zero its age
        if(this.routingTable.contains(node)) node.resetAge();
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
    public synchronized boolean addNeighbor(Node neighbour) {
        NodeProfile profile = ((PolderCastBaseNode) neighbour).getNodeProfile();

        if(this.routingTable.size() == this.MAX_VIEW_SIZE) {
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
        NodeProfile profile = ((PolderCastBaseNode)neighbor).getNodeProfile();
        return this.routingTable.contains(neighbor);
    }

    // Returns the size of the neighbor list.
    public int degree() {
        return this.routingTable.size();
    }

    // Returns the neighbor with the given index.
    public Node getNeighbor(int i) {
        throw new RuntimeException("Can't do this with Cyclon");
    }

    // A possibility for optimization.
    public void pack() {}

    public synchronized LinkedHashSet<NodeProfile> getRoutingTableCopy() {
        LinkedHashSet<NodeProfile> routingTableCopy = new LinkedHashSet<NodeProfile>();
        routingTableCopy.addAll(this.routingTable);
        return routingTableCopy;
    }
}
