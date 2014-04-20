package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import poldercast.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class VicinityProtocol implements CDProtocol, EDProtocol, Linkable {
    public int bitsSent = 0;
    public int bitsReceived = 0;
    public int messagesSent = 0;
    public int messagesReceived = 0;
    public ArrayList<NodeProfile> routingTable = new ArrayList<NodeProfile>(MAX_VIEW_SIZE * 2);

    public final int protocolID;
    public static final int MAX_VIEW_SIZE = 20;
    public static final String VICINITY = "vicinity";

    public VicinityProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(VICINITY);
    }

    @Override
    public Object clone() {
        VicinityProtocol clone = null;
        try {
            clone = (VicinityProtocol) super.clone();
            clone.routingTable = this.getRoutingTableCopy();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return clone;
    }

    public synchronized void nextCycle(Node node, int protocolID) {
        PolderCastNode thisNode = (PolderCastNode) node;
        VicinityProtocol protocol = (VicinityProtocol) thisNode.getProtocol(protocolID);

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

        ArrayList<NodeProfile> profilesToSend = this.selectClosestNodesForNode(thisNode, oldestNode, thisNode.getUnionOfAllViews());

        GossipMsg msg = new GossipMsg(profilesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;
        Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastNode thisNode = (PolderCastNode) node;
        VicinityProtocol protocol = (VicinityProtocol) thisNode.getProtocol(protocolID);
        GossipMsg receivedGossipMsg = (GossipMsg) event;

        if (!(event instanceof GossipMsg)) {
            throw new RuntimeException("CyclonProtocol should only receive GossipMsg events");
        }

        protocol.bitsReceived += receivedGossipMsg.getSizeInBits();
        protocol.messagesReceived++;

        if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            ArrayList<NodeProfile> profilesToSend = this.selectClosestNodesForNode(thisNode,
                    receivedGossipMsg.getSender().getNodeProfile(), thisNode.getUnionOfAllViews());

            GossipMsg msg = new GossipMsg(profilesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
            protocol.bitsSent += msg.getSizeInBits();
            protocol.messagesSent++;
            Util.sendMsg(thisNode, receivedGossipMsg.getSender(), msg, protocolID);

        } else if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());

        } else {
            throw new RuntimeException("Bad GossipMsg");
        }
    }

    public synchronized ArrayList<NodeProfile> selectClosestNodesForNode(PolderCastNode thisNode, NodeProfile node,
                                                                         ArrayList<NodeProfile> nodeSelection) {
        ArrayList<NodeProfile> closestNodes;
        Collections.sort(nodeSelection, new VicinityComparator(node));
        closestNodes = new ArrayList<NodeProfile>(nodeSelection.subList(0, VicinityProtocol.MAX_VIEW_SIZE));
        return closestNodes;
    }

    public synchronized void mergeNodes(PolderCastNode thisNode, ArrayList<NodeProfile> profiles) {
        // Set age to 0 for all profiles received
        Iterator<NodeProfile> nodeProfileIterator = profiles.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            profile.resetAge();
            // Remove our profile if any
            if (profile.getID().equals(thisNode.getNodeProfile().getID())) {
                nodeProfileIterator.remove();
            }
            // Remove any duplicates before we add
            if (this.routingTable.contains(profile)) {
                nodeProfileIterator.remove();
            }
        }

        // Consider the union of all views with these nodes
        ArrayList<NodeProfile> candidatesToAdd = thisNode.getUnionOfAllViews();
        candidatesToAdd.addAll(profiles);

        this.routingTable = this.selectClosestNodesForNode(thisNode, thisNode.getNodeProfile(), candidatesToAdd);
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
