package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import poldercast.util.*;

import java.util.*;

public class VicinityProtocol extends BandwidthTrackedProtocol implements CDProtocol, EDProtocol, Linkable {
    public LinkedHashSet<NodeProfile> routingTable;

    public final int protocolID;
    public static final byte MAX_VIEW_SIZE;
    public static final byte MAX_GOSSIP_LENGTH;
    public static final String VICINITY = "vicinity";
    static {
        MAX_GOSSIP_LENGTH = (byte) Configuration.getInt("protocol.vicinity.maxGossipLength");
        MAX_VIEW_SIZE = (byte) Configuration.getInt("protocol.vicinity.maxViewSize");
    }

    public VicinityProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(VICINITY);
        this.routingTable = new LinkedHashSet<NodeProfile>(MAX_VIEW_SIZE);
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
        PolderCastBaseNode thisNode = (PolderCastBaseNode) node;
        VicinityProtocol protocol = (VicinityProtocol) thisNode.getProtocol(protocolID);

        // Increment the age of all nodes
        Iterator<NodeProfile> nodeProfileIterator = protocol.routingTable.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            profile.incrementAge();
        }

        // Select oldest node
        // If we haven't got any nodes in the view yet, bootstrap with the nodes from the Cyclon view
        if(protocol.routingTable.isEmpty()) protocol.bootstrapFromOtherModules(thisNode);
        if(protocol.routingTable.isEmpty()) throw new RuntimeException("Vicinity view has no entries after bootstrapping");
        // Select oldest node
        nodeProfileIterator = protocol.routingTable.iterator();
        NodeProfile oldestNode = nodeProfileIterator.next();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            if (profile.getAge() > oldestNode.getAge())
                oldestNode = profile;
        }

        HashSet<NodeProfile> tmpProfilesToSend = thisNode.getUnionOfAllViews();
        // remove the target and also add our node into the mix
        tmpProfilesToSend.remove(oldestNode); tmpProfilesToSend.add(thisNode.getNodeProfile());
        HashSet<NodeProfile> profilesToSend = protocol.selectClosestNodesForNode(thisNode, oldestNode,
                tmpProfilesToSend, protocol.MAX_GOSSIP_LENGTH);

        GossipMsg msg = new GossipMsg(profilesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        protocol.routingTable.remove(oldestNode); // proactive removal to combat churn
        protocol.messageSent(msg);
        Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastBaseNode thisNode = (PolderCastBaseNode) node;
        VicinityProtocol protocol = (VicinityProtocol) thisNode.getProtocol(protocolID);
        GossipMsg receivedGossipMsg = (GossipMsg) event;

        if (!(event instanceof GossipMsg)) {
            throw new RuntimeException("CyclonProtocol should only receive GossipMsg events");
        }

        protocol.messageSent(receivedGossipMsg);

        if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());

            HashSet<NodeProfile> profilesToSend = protocol.selectClosestNodesForNode(thisNode,
                    receivedGossipMsg.getSender().getNodeProfile(),
                    thisNode.getUnionOfAllViews(), protocol.MAX_GOSSIP_LENGTH);

            GossipMsg msg = new GossipMsg(profilesToSend, GossipMsg.Types.GOSSIP_RESPONSE, thisNode);
            Util.sendMsg(thisNode, receivedGossipMsg.getSender(), msg, protocolID);
            protocol.messageSent(msg);

        } else if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());

        } else {
            throw new RuntimeException("Bad GossipMsg");
        }
    }

    public synchronized LinkedHashSet<NodeProfile> selectClosestNodesForNode(PolderCastBaseNode thisNode, NodeProfile node,
                                                                         HashSet<NodeProfile> nodeSelection, byte maxNodes) {
        ArrayList<NodeProfile> closestNodes = new ArrayList<NodeProfile>();
        closestNodes.addAll(nodeSelection);
        closestNodes.remove(node); // TODO technically we should stop this at the source - where nodeSelection is passed
        Collections.sort(closestNodes, new VicinityComparator(node));
        // Get up to 20 of the closest nodes
        // TODO probably a bug, Vicinity module never seems to exceed 19 nodes
        if(closestNodes.subList(0, Math.min(closestNodes.size(), maxNodes) - 1).size() == 20) System.out.println("woot");
        return new LinkedHashSet<NodeProfile>( closestNodes.subList(0, Math.min(closestNodes.size(), maxNodes) - 1 ) );
    }

    public synchronized void mergeNodes(PolderCastBaseNode thisNode, HashSet<NodeProfile> profiles) {
        Iterator<NodeProfile> nodeProfileIterator = profiles.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            // Remove our profile if any
            if (profile.equals(thisNode.getNodeProfile())) {
                nodeProfileIterator.remove();
            }
            // Remove any duplicates before we add
            if (this.routingTable.contains(profile)) {
                // This is a much better place to remove the node.
                // We are most likely going to add it back in later.
                this.routingTable.remove(profile);
            }
        }

        // Consider the union of all views with these nodes
        HashSet<NodeProfile> toSelect = thisNode.getUnionOfAllViews();
        toSelect.addAll(profiles);
        this.routingTable = this.selectClosestNodesForNode(thisNode, thisNode.getNodeProfile(),
                toSelect, this.MAX_VIEW_SIZE);
    }

    private void bootstrapFromOtherModules(PolderCastBaseNode thisNode) {
        // NOTE: although it wasn't explicitly defined in the design paper, intuition tells me this is how it works
        this.mergeNodes(thisNode, new HashSet<NodeProfile>());
    }






    public synchronized void communicationReceivedFromNode(NodeProfile node) {
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
    public boolean addNeighbor(Node neighbour) {
        /*
        NOTE: Perhaps when people are testing simulations of PolderCast solely with the Vicinity module on its own
              then this would be acceptable, but I doubt anyone would do this.
         */
        throw new RuntimeException("We shouldn't be attempting to bootstrap the Vicinity module");
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
        throw new RuntimeException("This doesn't work with Vicinity");
    }

    // A possibility for optimization.
    public void pack() {}

    public LinkedHashSet<NodeProfile> getRoutingTableCopy() {
        LinkedHashSet<NodeProfile> routingTableCopy = new LinkedHashSet<NodeProfile>();
        routingTableCopy.addAll(this.routingTable);
        return routingTableCopy;
    }
}
