package poldercast.util;

import java.util.ArrayList;

public class GossipMsg implements SizeInBits {
    private final ArrayList<NodeProfile> profiles;
    private final Types type;
    private final NodeProfile sender;

    public enum Types { GOSSIP_QUERY, GOSSIP_RESPONSE }

    public GossipMsg(ArrayList<NodeProfile> profiles, Types type, NodeProfile sender) {
        this.profiles = profiles;
        this.type = type;
        this.sender = sender;
    }

    public int getSizeInBits() {
        int size = 0;
        for(NodeProfile p : this.profiles) {
            if(p != null) size += p.getSizeInBits();
        }
        return size;
    }

    public ArrayList<NodeProfile> getNodeProfiles() {
        return (ArrayList<NodeProfile>) this.profiles.clone();
    }

    public NodeProfile getSender() {
        return this.sender;
    }
}
