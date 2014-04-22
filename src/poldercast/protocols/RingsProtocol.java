package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;
import poldercast.util.*;

import java.util.*;

class RingsTopicView {
    public ArrayList<NodeProfile> nodesWithLowerID = new ArrayList<NodeProfile>(RingsProtocol.MAX_VIEW_SIZE / 2);
    public ArrayList<NodeProfile> nodesWithHigherID = new ArrayList<NodeProfile>(RingsProtocol.MAX_VIEW_SIZE / 2);

    public RingsTopicView() {}

    public int degree() {
        return this.nodesWithLowerID.size() + this.nodesWithHigherID.size();
    }

    public boolean contains(NodeProfile profile) {
        return this.nodesWithLowerID.contains(profile) || this.nodesWithHigherID.contains(profile);
    }

    public void incrementAgeOfNodes() {
        for(NodeProfile profile : this.nodesWithLowerID) profile.incrementAge();
        for(NodeProfile profile : this.nodesWithHigherID) profile.incrementAge();
    }

    public synchronized void removeNode(NodeProfile nodeProfile) {
        if(this.nodesWithHigherID.contains(nodeProfile)) this.nodesWithHigherID.remove(nodeProfile);
        if(this.nodesWithLowerID.contains(nodeProfile)) this.nodesWithLowerID.remove(nodeProfile);
    }
}

public class RingsProtocol implements CDProtocol, EDProtocol, Linkable {
    public int bitsSent = 0;
    public int bitsReceived = 0;
    public int messagesSent = 0;
    public int messagesReceived = 0;
    public Map<ID, RingsTopicView> routingTable = new HashMap<ID, RingsTopicView>();

    public final int protocolID;
    public static final int MAX_GOSSIP_LENGTH = 10;
    public static final int MAX_VIEW_SIZE = 4;
    public static final String RINGS = "rings";

    public RingsProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(RINGS);
    }

    @Override
    public Object clone() {
        RingsProtocol clone = null;
        try {
            clone = (RingsProtocol) super.clone();
            //clone.routingTable = this.getRoutingTableCopy();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return clone;
    }

    public synchronized void nextCycle(Node node, int protocolID) {
        PolderCastNode thisNode = (PolderCastNode) node;
        RingsProtocol protocol = (RingsProtocol) thisNode.getProtocol(protocolID);

        ArrayList<NodeProfile> profiles = protocol.getLinearView();
        if(profiles.isEmpty()) protocol.bootstrapFromOtherModules(thisNode);
        // Increment age of all nodes
        Iterator<NodeProfile> nodeProfileIterator = profiles.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            profile.incrementAge();
        }
        // Select oldest node
        NodeProfile oldestNode = profiles.get(0);
        nodeProfileIterator = profiles.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            if (profile.getAge() > oldestNode.getAge()) oldestNode = profile;
        }

        ArrayList<NodeProfile> nodesToSend = protocol.selectNodesToSend(thisNode, oldestNode);
        protocol.removeNode(oldestNode); // proactive removal to combat churn
        GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        protocol.bitsSent += msg.getSizeInBits();
        protocol.messagesSent++;

        Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastNode thisNode = (PolderCastNode) node;
        RingsProtocol protocol = (RingsProtocol) thisNode.getProtocol(protocolID);
        GossipMsg receivedGossipMsg = (GossipMsg) event;

        if (!(event instanceof GossipMsg)) {
            throw new RuntimeException("RingsProtocol should only receive GossipMsg events");
        }

        protocol.bitsReceived += receivedGossipMsg.getSizeInBits();
        protocol.messagesReceived++;

        if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());

            ArrayList<NodeProfile> nodesToSend = protocol.selectNodesToSend(thisNode, receivedGossipMsg.getSender().getNodeProfile());
            GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
            protocol.bitsSent += msg.getSizeInBits();
            protocol.messagesSent++;

            Util.sendMsg(thisNode, receivedGossipMsg.getSender(), msg, protocolID);

        } else if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {
            protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());
            protocol.communicationReceivedFromNode(receivedGossipMsg.getSender().getNodeProfile());

        } else {
            throw new RuntimeException("Bad GossipMsg");
        }
    }
    public synchronized ArrayList<NodeProfile> selectNodesToSend(PolderCastNode thisNode, NodeProfile gossipNode) {
        ArrayList<ID> subscriptionsInCommon = new ArrayList<ID>(thisNode.getNodeProfile().getSubscriptions().keySet());
        subscriptionsInCommon.retainAll(gossipNode.getSubscriptions().keySet());

        Set<NodeProfile> nodesToSend = new LinkedHashSet<NodeProfile>();
        ArrayList<NodeProfile> candidates = new ArrayList<NodeProfile>(thisNode.getUnionOfAllViews());
        candidates.add(thisNode.getNodeProfile()); // since we are selecting nodes to send, we add our own
        // Remove duplicates
        Set setItems = new LinkedHashSet(candidates);
        candidates.clear();
        candidates.addAll(setItems);
        for (ID subscription : subscriptionsInCommon) {
            ArrayList<NodeProfile> candidatesThatShareInterest = new ArrayList<NodeProfile>();
            for (NodeProfile profile : candidates) {
                if (profile.getSubscriptions().containsKey(subscription)) {
                    candidatesThatShareInterest.add(profile);
                }
            }

            if(candidatesThatShareInterest.isEmpty()) continue;

            // So we can compute indexOfGossipNode, and thus, the justLower and justHigher nodes
            candidatesThatShareInterest.add(gossipNode);
            // Order by ID
            Collections.sort(candidatesThatShareInterest, new IDComparator());
            int indexOfGossipNode = candidatesThatShareInterest.indexOf(gossipNode);

            ArrayList<NodeProfile> justLower = new ArrayList<NodeProfile>();
            ArrayList<NodeProfile> justHigher = new ArrayList<NodeProfile>();
            int justLowerIndex;
            int justHigherIndex;

            // While we still need nodes to fill the justLower list
            while(((RingsProtocol.MAX_VIEW_SIZE / 2) - justLower.size()) > 0) {
                justLowerIndex = (indexOfGossipNode - 1) % justLower.size();
                NodeProfile toAdd = candidatesThatShareInterest.get(justLowerIndex);
                if(justLower.contains(toAdd)) break;
                else justLower.add(toAdd);
            }
            // While we still need nodes to fill the justHigher list
            while(((RingsProtocol.MAX_VIEW_SIZE / 2) - justHigher.size()) > 0) {
                justHigherIndex  = (indexOfGossipNode + 1) % justHigher.size();
                NodeProfile toAdd = candidatesThatShareInterest.get(justHigherIndex);
                if(justHigher.contains(toAdd)) break;
                else justHigher.add(toAdd);
            }

            nodesToSend.addAll(justLower);
            nodesToSend.addAll(justHigher);
        }

        ArrayList<NodeProfile> listOfNodesToSend = new ArrayList<NodeProfile>(nodesToSend);
        if(nodesToSend.size() > RingsProtocol.MAX_GOSSIP_LENGTH) {
            Collections.shuffle(listOfNodesToSend);
            listOfNodesToSend = new ArrayList<NodeProfile>(listOfNodesToSend.subList(0, RingsProtocol.MAX_GOSSIP_LENGTH));
        }

        return listOfNodesToSend;
    }

    public synchronized void mergeNodes(PolderCastNode thisNode, ArrayList<NodeProfile> profiles) {
        Iterator<NodeProfile> nodeProfileIterator = profiles.iterator();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            // Remove our profile if any
            if (profile.getID().equals(thisNode.getNodeProfile().getID())) {
                nodeProfileIterator.remove();
            }
        }

        ArrayList<NodeProfile> candidates = profiles;
        candidates.addAll(thisNode.getUnionOfAllViews());
        // Remove duplicates
        Set setItems = new LinkedHashSet(candidates);
        candidates.clear();
        candidates.addAll(setItems);

        for(ID subscription : thisNode.getNodeProfile().getSubscriptions().keySet()) {
            ArrayList<NodeProfile> candidatesThatShareInterest = new ArrayList<NodeProfile>();
            for (NodeProfile profile : candidates) {
                if (profile.getSubscriptions().containsKey(subscription)) {
                    candidatesThatShareInterest.add(profile);
                }
            }

            if(candidatesThatShareInterest.isEmpty()) continue;

            // So we can compute indexOfOurNode, and thus, the justLower and justHigher nodes
            candidatesThatShareInterest.add(thisNode.getNodeProfile());
            // Order by ID
            Collections.sort(candidatesThatShareInterest, new IDComparator());
            int indexOfOurNode = candidatesThatShareInterest.indexOf(thisNode.getNodeProfile());

            ArrayList<NodeProfile> justLower = new ArrayList<NodeProfile>();
            ArrayList<NodeProfile> justHigher = new ArrayList<NodeProfile>();
            int justLowerIndex;
            int justHigherIndex;

            // While we still need nodes to fill the justLower list
            while(((RingsProtocol.MAX_VIEW_SIZE / 2) - justLower.size()) > 0) {
                justLowerIndex = (indexOfOurNode - 1) % justLower.size();
                NodeProfile toAdd = candidatesThatShareInterest.get(justLowerIndex);
                if(justLower.contains(toAdd)) break;
                else justLower.add(toAdd);
            }
            // While we still need nodes to fill the justHigher list
            while(((RingsProtocol.MAX_VIEW_SIZE / 2) - justHigher.size()) > 0) {
                justHigherIndex  = (indexOfOurNode + 1) % justHigher.size();
                NodeProfile toAdd = candidatesThatShareInterest.get(justHigherIndex);
                if(justHigher.contains(toAdd)) break;
                else justHigher.add(toAdd);
            }

            this.routingTable.get(subscription).nodesWithLowerID = justLower;
            this.routingTable.get(subscription).nodesWithHigherID = justHigher;
        }

    }

    private void bootstrapFromOtherModules(PolderCastNode thisNode) {
        this.mergeNodes(thisNode, new ArrayList<NodeProfile>());
    }

    private void communicationReceivedFromNode(NodeProfile node) {
        ArrayList<NodeProfile> view = this.getLinearView();
        if(view.contains(node)) node.resetAge();
    }

    public ArrayList<NodeProfile> getLinearView() {
        ArrayList<NodeProfile> profiles = new ArrayList<NodeProfile>();
        for(RingsTopicView ringsTopicView : this.routingTable.values()) {
            profiles.addAll(ringsTopicView.nodesWithHigherID);
            profiles.addAll(ringsTopicView.nodesWithLowerID);
        }
        return profiles;
    }

    private synchronized void removeNode(NodeProfile nodeProfile) {
        for(RingsTopicView view : this.routingTable.values()) {
            view.removeNode(nodeProfile);
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
    public boolean addNeighbor(Node neighbour) {
        /*
        NOTE: Perhaps when people are testing simulations of PolderCast solely with the Rings module on its own
              then this would be acceptable, but I doubt anyone would do this.
         */
        throw new RuntimeException("We shouldn't be attempting to bootstrap the Rings module");
    }

    // Returns true if the given node is a member of the neighbor set.
    public boolean contains(Node neighbor) {
        NodeProfile profile = ((PolderCastNode) neighbor).getNodeProfile();
        for(RingsTopicView ringsTopicView : this.routingTable.values()) {
            if(ringsTopicView.contains(profile)) return true;
        }
        return false;
    }

    // Returns the size of the neighbor list.
    public int degree() {
        int degree = 0;
        for(RingsTopicView ringsTopicView : this.routingTable.values()) {
            degree += ringsTopicView.degree();
        }
        return degree;
    }

    // Returns the neighbor with the given index.
    public Node getNeighbor(int i) {
        throw new RuntimeException("Cannot returns the neighbor with the given index" +
                " - Rings has no concept of a linear view, only a view for each topic");
    }

    // A possibility for optimization.
    public void pack() {}

    public Map<ID, RingsTopicView> getRoutingTableCopy() {
        Map<ID, RingsTopicView> routingTableCopy = new HashMap<ID, RingsTopicView>();
        routingTableCopy.putAll(this.routingTable);
        return routingTableCopy;
    }
}
