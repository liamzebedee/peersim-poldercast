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
    public HashMap<ID, RingsTopicView> routingTable = new HashMap<ID, RingsTopicView>();
    public HashSet<Integer> receivedEvents = new HashSet<Integer>();

    public final int protocolID;
    public static final byte MAX_GOSSIP_LENGTH;
    public static final byte MAX_VIEW_SIZE;
    public static final byte FANOUT;
    public static final String RINGS = "rings";
    static {
        MAX_GOSSIP_LENGTH = (byte) Configuration.getInt("protocol.rings.maxGossipLength");
        MAX_VIEW_SIZE = (byte) Configuration.getInt("protocol.rings.maxViewSize");
        FANOUT = (byte) Configuration.getInt("protocol.rings.fanout");
    }

    public RingsProtocol(String configPrefix) {
        this.protocolID = Configuration.lookupPid(RINGS);
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

    public HashMap<ID, RingsTopicView> getRoutingTableCopy() {
        HashMap<ID, RingsTopicView> routingTableCopy = new HashMap<ID, RingsTopicView>();
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

        // Update priorities for topics
        protocol.updatePrioritiesForSubscriptions(thisNode.getNodeProfile());

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

        HashSet<NodeProfile> nodesToSend = protocol.selectNodesToSend(thisNode, oldestNode);
        protocol.removeNode(oldestNode); // proactive removal to combat churn
        GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_QUERY, thisNode);
        thisNode.topicSubscriptionLoad++;
        Util.sendMsg(thisNode, oldestNode.getNode(), msg, protocolID);
        protocol.messageSent(msg);
    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {
        PolderCastBaseNode thisNode = (PolderCastBaseNode) node;
        RingsProtocol protocol = (RingsProtocol) thisNode.getProtocol(protocolID);

        NetworkMsg networkMsg = (NetworkMsg) event;
        protocol.messageReceived(networkMsg);

        // TODO bug node age is supposed to be separate from profile
        NodeProfile sender = networkMsg.getSender().getNodeProfile();
        HashSet<NodeProfile> view = this.getLinearView();
        if(view.contains(sender)) sender.resetAge();

        if(event instanceof GossipMsg) {
            GossipMsg receivedGossipMsg = (GossipMsg) event;

            if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_QUERY) {
                protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());

                HashSet<NodeProfile> nodesToSend = protocol.selectNodesToSend(thisNode, receivedGossipMsg.getSender().getNodeProfile());
                GossipMsg msg = new GossipMsg(nodesToSend, GossipMsg.Types.GOSSIP_RESPONSE, thisNode);
                thisNode.topicSubscriptionLoad++;
                Util.sendMsg(thisNode, receivedGossipMsg.getSender(), msg, protocolID);
                protocol.messageSent(msg);

            } else if (receivedGossipMsg.getType() == GossipMsg.Types.GOSSIP_RESPONSE) {
                protocol.mergeNodes(thisNode, receivedGossipMsg.getNodeProfiles());

            } else {
                throw new RuntimeException("Bad GossipMsg received by RingsProtocol");
            }
        } else if(event instanceof PublishMsg) {
            PublishMsg receivedPublishMsg = (PublishMsg) event;

            if(protocol.receivedEvents.contains(receivedPublishMsg.getUniqueIdentifier())) return;
            protocol.receivedEvents.add(receivedPublishMsg.getUniqueIdentifier());

            RingsTopicView ringsTopicView = protocol.routingTable.get(receivedPublishMsg.getTopic());
            PublishMsg publishMsgToSend = new PublishMsg(receivedPublishMsg.getEvent(), receivedPublishMsg.getTopic(), thisNode);
            HashSet<NodeProfile> nodesToPropagateEventTo = new HashSet<NodeProfile>();
            if(ringsTopicView.contains(receivedPublishMsg.getSender().getNodeProfile())) {
                // If the event has been received through the node’s successor (or predecessor),
                // it is propagated down the line to its predecessor (or successor) and
                // (F - 1) arbitrary subscribers of the topic (as sourced from Vicinity)
                if(ringsTopicView.nodesWithHigherID.contains(receivedPublishMsg.getSender().getNodeProfile())) {
                    // Received through successor, propagate to predecessor
                    /*
                     * TODO in the paper the view size is set to be 2 so we have backup nodes
                     * TODO it's never mentioned, but I assume that if sending to one of these nodes fails
                     * TODO we attempt to send to the next best
                     */
                    // TODO solution is to always check Node.isUp(), this works with churn model
                    nodesToPropagateEventTo.add(ringsTopicView.nodesWithHigherID.iterator().next());
                } else {
                    // Received through predecessor, propagate to successor
                    nodesToPropagateEventTo.add(ringsTopicView.nodesWithLowerID.iterator().next());
                }

                nodesToPropagateEventTo.addAll(protocol.getArbitrarySubscribersOfTopic(thisNode, receivedPublishMsg.getTopic(),
                        protocol.FANOUT - 1));
                protocol.propagateEvent(thisNode, nodesToPropagateEventTo, publishMsgToSend);

            } else {
                // If the event was received through some third node, or if it originated at the node
                // in question, it is propagated to both the successor and the predecessor,
                // as well as to (F - 2) arbitrary subscribers of the topic
                if(!ringsTopicView.nodesWithHigherID.isEmpty()) {
                    nodesToPropagateEventTo.add(ringsTopicView.nodesWithHigherID.iterator().next());
                }
                if(!ringsTopicView.nodesWithLowerID.isEmpty()) {
                    nodesToPropagateEventTo.add(ringsTopicView.nodesWithLowerID.iterator().next());
                }
                nodesToPropagateEventTo.addAll(protocol.getArbitrarySubscribersOfTopic(thisNode, receivedPublishMsg.getTopic(),
                        protocol.FANOUT - 2));
                protocol.propagateEvent(thisNode, nodesToPropagateEventTo, publishMsgToSend);
            }

        } else {
            throw new RuntimeException("RingsProtocol should only receive GossipMsg/PublishMsg events");
        }
    }

    // TODO this could be refactored with common code above
    public void publishEvent(PolderCastBaseNode thisNode, ID topic, byte[] event) {
        RingsTopicView ringsTopicView = this.routingTable.get(topic);
        PublishMsg publishMsgToSend = new PublishMsg(event, topic, thisNode);
        HashSet<NodeProfile> nodesToPropagateEventTo = new HashSet<NodeProfile>();
        if(!ringsTopicView.nodesWithHigherID.isEmpty()) {
            nodesToPropagateEventTo.add(ringsTopicView.nodesWithHigherID.iterator().next());
        }
        if(!ringsTopicView.nodesWithLowerID.isEmpty()) {
            nodesToPropagateEventTo.add(ringsTopicView.nodesWithLowerID.iterator().next());
        }
        nodesToPropagateEventTo.addAll(this.getArbitrarySubscribersOfTopic(thisNode, topic,
                this.FANOUT - 2));
        this.propagateEvent(thisNode, nodesToPropagateEventTo, publishMsgToSend);
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

    public synchronized HashSet<NodeProfile> selectNodesToSend(PolderCastBaseNode thisNode, NodeProfile gossipNode) {
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

        return new HashSet<NodeProfile>(listOfNodesToSend);
    }

    private void updatePrioritiesForSubscriptions(NodeProfile thisNode) {
        for(Map.Entry<ID,Byte> entry : thisNode.getSubscriptions().entrySet()) {
            RingsTopicView view = this.routingTable.get(entry.getKey());
            if(view == null) {
                view = new RingsTopicView();
                this.routingTable.put(entry.getKey(), view);
            }
            thisNode.getSubscriptions().put(entry.getKey(), (byte) (this.MAX_VIEW_SIZE - view.degree()));
        }
    }

    private void propagateEvent(PolderCastBaseNode thisNode, HashSet<NodeProfile> nodesToPropagateEventTo, PublishMsg publishMsgToSend) {
        for(NodeProfile nodeToPropagateEventTo : nodesToPropagateEventTo) {
            thisNode.topicPublicationLoad++;
            Util.sendMsg(thisNode, nodeToPropagateEventTo.getNode(), publishMsgToSend, protocolID);
            this.messageSent(publishMsgToSend);
        }
    }

    private HashSet<NodeProfile> getArbitrarySubscribersOfTopic(PolderCastBaseNode thisNode, ID topic, int n) {
        ArrayList<NodeProfile> candidates = new ArrayList<NodeProfile>();
        for(NodeProfile nodeCandidate : thisNode.getVicinityProtocol().getRoutingTableCopy()) {
            if(nodeCandidate.getSubscriptions().keySet().contains(topic)) candidates.add(nodeCandidate);
        }
        Collections.shuffle(candidates, CommonState.r);
        Iterator<NodeProfile> iter = candidates.iterator();
        HashSet<NodeProfile> arbitrarySubscribers = new HashSet<NodeProfile>();
        int i = 0;
        while(iter.hasNext() && (i < n)) {
            arbitrarySubscribers.add(iter.next());
            i++;
        }
        return arbitrarySubscribers;
    }

    private synchronized void removeNode(NodeProfile nodeProfile) {
        for(RingsTopicView view : this.routingTable.values()) {
            view.removeNode(nodeProfile);
        }
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