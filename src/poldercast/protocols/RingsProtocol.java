package poldercast.protocols;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import poldercast.util.ID;
import poldercast.util.NodeProfile;
import poldercast.util.PolderCastBaseNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;

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

    }

    public synchronized void processEvent(Node node, int protocolID, java.lang.Object event) {

    }

    public void publishEvent(PolderCastBaseNode thisNode, ID topic, byte[] event) {}

    public synchronized void changeInSubscriptions(PolderCastBaseNode thisNode) {}








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