package poldercast.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class GossipMsg extends NetworkMsg {
    private final HashSet<NodeProfile> profiles;
    private final Types type;

    public enum Types { GOSSIP_QUERY, GOSSIP_RESPONSE }

    public GossipMsg(HashSet<NodeProfile> profiles, Types type, PolderCastBaseNode sender) {
        super(sender);
        this.profiles = profiles;
        this.type = type;
    }

    public int getSizeInBits() {
        int size = 0;
        for(NodeProfile p : this.profiles) {
            if(p != null) size += p.getSizeInBits();
        }
        return size;
    }

    public HashSet<NodeProfile> getNodeProfiles() {
        HashSet<NodeProfile> nodeProfiles = new HashSet<NodeProfile>();
        nodeProfiles.addAll(this.profiles);
        return nodeProfiles;
    }

    public Types getType() { return this.type; }
}
