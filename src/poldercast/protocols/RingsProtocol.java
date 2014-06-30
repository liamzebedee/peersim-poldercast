package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import poldercast.util.*;

import java.util.*;

public class RingsProtocol extends BandwidthTrackedProtocol implements CDProtocol, EDProtocol, Linkable {
    public Map<ID, RingsTopicView> routingTable = new HashMap<ID, RingsTopicView>();
    public HashSet<Integer> receivedEvents = new HashSet<Integer>();

    public final int protocolID;
    public final byte MAX_GOSSIP_LENGTH;
    public final byte MAX_VIEW_SIZE;
    public final byte FANOUT;
    public static final String RINGS = "rings";

    public RingsProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(RINGS);
        this.MAX_GOSSIP_LENGTH = (byte) Configuration.getInt(configPrefix + ".maxGossipLength");
        this.MAX_VIEW_SIZE = (byte) Configuration.getInt(configPrefix + ".maxViewSize");
        this.FANOUT = (byte) Configuration.getInt(configPrefix + ".fanout");
    }

    @Override
    public RingsProtocol clone() {
        RingsProtocol clone = null;
        try {
            clone = (RingsProtocol) super.clone();
            clone.routingTable = this.getRoutingTableCopy();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return clone;
    }

    public Map<ID, RingsTopicView> getRoutingTableCopy() {
        Map<ID, RingsTopicView> routingTableCopy = new HashMap<ID, RingsTopicView>();
        routingTableCopy.putAll(this.routingTable);
        return routingTableCopy;
    }

    public HashSet<NodeProfile> getLinearView() {
        HashSet<NodeProfile> profiles = new HashSet<NodeProfile>();
        for(RingsTopicView ringsTopicView : this.routingTable.values()) {
            profiles.addAll(ringsTopicView.nodesWithHigherID);
            profiles.addAll(ringsTopicView.nodesWithLowerID);
        }
        return profiles;
    }

    public synchronized void nextCycle(Node node, int protocolID) {
        PolderCastBaseNode thisNode = (PolderCastBaseNode) node;
        RingsProtocol protocol = (RingsProtocol) thisNode.getProtocol(protocolID);

        // Increment age of all nodes
        for(RingsTopicView view : this.routingTable.values()) {
            view.incrementAgeOfNodes();
        }

        // Get node profiles
        if(thisNode.getNodeProfile().getSubscriptions().size() == 0) return;
        HashSet<NodeProfile> profiles = protocol.getLinearView();
        if(profiles.isEmpty()) protocol.bootstrapFromOtherModules(thisNode);
        if(!profiles.isEmpty()) {
            profiles = protocol.getLinearView();
        } else return;

        // Select oldest node
        Iterator<NodeProfile> nodeProfileIterator = profiles.iterator();
        NodeProfile oldestNode = nodeProfileIterator.next();
        while (nodeProfileIterator.hasNext()) {
            NodeProfile profile = nodeProfileIterator.next();
            if (profile.getAge() > oldestNode.getAge())
                oldestNode = profile;
        }

        ArrayList<NodeProfile> nodesToSend = protocol.selectNodesToSend(thisNode, oldestNode);
        //protocol.removeNode(oldestNode); // proactive removal to combat churn
        //GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        //if(thisNode.measureTopicSubscriptionLoad) thisNode.load++; // TODO load
        //Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
        //protocol.messageSent(msg);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {

    }

    public void publishEvent(PolderCastBaseNode thisNode, ID topic, byte[] event) {

    }

    public synchronized void changeInSubscriptions(PolderCastBaseNode thisNode) {

    }

    private void bootstrapFromOtherModules(PolderCastBaseNode thisNode) {
        this.mergeNodes(thisNode, new HashSet<NodeProfile>());
    }

    public synchronized void mergeNodes(PolderCastBaseNode thisNode, HashSet<NodeProfile> profiles) {
        HashSet<NodeProfile> candidates = new HashSet<NodeProfile>();
        candidates.addAll(profiles);
        candidates.addAll(thisNode.getUnionOfAllViews());

        candidates.remove(thisNode.getNodeProfile());

        for(ID subscription : thisNode.getNodeProfile().getSubscriptions().keySet()) {
            this.routingTable.put(subscription, this.selectBestNodesForTopic(subscription, candidates, thisNode.getNodeProfile()));
        }
    }

    public RingsTopicView selectBestNodesForTopic(ID topic, HashSet<NodeProfile> candidates, NodeProfile perspectiveNode) {
        RingsTopicView bestNodes = new RingsTopicView();

        ArrayList<NodeProfile> candidatesThatShareInterest = new ArrayList<NodeProfile>();
        for (NodeProfile profile : candidates) {
            if (profile.getSubscriptions().containsKey(topic)) {
                candidatesThatShareInterest.add(profile);
            }
        }

        if(candidatesThatShareInterest.isEmpty()) return bestNodes;

        // So we can compute indexOfOurNode, and thus, the justLower and justHigher nodes
        candidatesThatShareInterest.add(perspectiveNode);
        // Order by ID
        Collections.sort(candidatesThatShareInterest, new IDComparator());
        int indexOfOurNode = candidatesThatShareInterest.indexOf(perspectiveNode);

        RingsTopicView view = new RingsTopicView();
        LinkedHashSet justLower = new LinkedHashSet();
        LinkedHashSet justHigher = new LinkedHashSet<NodeProfile>();

        int justHigherNodesNeeded, justHigherIndex;
        int justLowerNodesNeeded, justLowerIndex;
        justHigherNodesNeeded = justLowerNodesNeeded = this.MAX_VIEW_SIZE / 2;
        justHigherIndex = justLowerIndex = 0;

        do {
            // add one to indexOfOurNode to get the node below it
            int g = (indexOfOurNode+1 + justHigherIndex) % (candidatesThatShareInterest.size()-1);
            justHigher.add(candidatesThatShareInterest.get(g));
            justHigherNodesNeeded--;
            justHigherIndex++;
        } while((justHigherNodesNeeded > 0) && (justHigherIndex != indexOfOurNode));

        do {
            // sub one from indexOfOurNode to get the node above it
            int g = (indexOfOurNode-1 + justHigherIndex) % (candidatesThatShareInterest.size()-1);
            justLower.add(candidatesThatShareInterest.get(g));
            justLowerNodesNeeded--;
            justLowerIndex++;
        } while((justLowerNodesNeeded > 0) && (justLowerIndex != indexOfOurNode));

        bestNodes.nodesWithLowerID = justLower;
        bestNodes.nodesWithHigherID = justHigher;

        return bestNodes;
    }

    public synchronized ArrayList<NodeProfile> selectNodesToSend(PolderCastBaseNode thisNode, NodeProfile gossipNode) {
        HashSet<ID> subscriptionsInCommon = new HashSet<ID>(thisNode.getNodeProfile().getSubscriptions().keySet());
        subscriptionsInCommon.retainAll(gossipNode.getSubscriptions().keySet());

        HashSet<NodeProfile> nodesToSend = new HashSet<NodeProfile>();
        HashSet<NodeProfile> candidates = thisNode.getUnionOfAllViews();
        candidates.add(thisNode.getNodeProfile()); // since we are selecting nodes to send, we add our own

        for (ID subscription : subscriptionsInCommon) {
            RingsTopicView view = this.selectBestNodesForTopic(subscription, candidates, gossipNode);

            nodesToSend.addAll(view.nodesWithLowerID);
            nodesToSend.addAll(view.nodesWithHigherID);
        }

        ArrayList<NodeProfile> listOfNodesToSend = new ArrayList<NodeProfile>(nodesToSend);
        if(nodesToSend.size() > this.MAX_GOSSIP_LENGTH) {
            Collections.shuffle(listOfNodesToSend, CommonState.r);
            listOfNodesToSend = new ArrayList<NodeProfile>(listOfNodesToSend.subList(0, this.MAX_GOSSIP_LENGTH-1));
        }

        return listOfNodesToSend;
    }






    public void onKill() {}
    public boolean addNeighbor(Node neighbour) {
        throw new RuntimeException("We shouldn't be attempting to bootstrap the Rings module using this method");
    }
    public boolean contains(Node neighbor) {
        NodeProfile profile = ((PolderCastBaseNode) neighbor).getNodeProfile();
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
    public void pack() {}
}

class RingsTopicView {
    public LinkedHashSet<NodeProfile> nodesWithLowerID = new LinkedHashSet<NodeProfile>();
    public LinkedHashSet<NodeProfile> nodesWithHigherID = new LinkedHashSet<NodeProfile>();

    public RingsTopicView() {}

    public int degree() {
        return this.nodesWithLowerID.size() + this.nodesWithHigherID.size();
    }

    public boolean contains(NodeProfile profile) {
        return this.nodesWithLowerID.contains(profile) || this.nodesWithHigherID.contains(profile);
    }

    public synchronized void incrementAgeOfNodes() {
        for(NodeProfile profile : this.nodesWithLowerID) profile.incrementAge();
        for(NodeProfile profile : this.nodesWithHigherID) profile.incrementAge();
    }

    public synchronized void removeNode(NodeProfile nodeProfile) {
        if(this.nodesWithHigherID.contains(nodeProfile)) this.nodesWithHigherID.remove(nodeProfile);
        if(this.nodesWithLowerID.contains(nodeProfile)) this.nodesWithLowerID.remove(nodeProfile);
    }
}