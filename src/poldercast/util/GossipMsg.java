package poldercast.util;

import java.util.ArrayList;
import java.util.Collections;

public class GossipMsg implements SizeInBits {
    private final ArrayList<NodeProfile> profiles;
    private final Types type;
    private final PolderCastNode sender;

    public enum Types { GOSSIP_QUERY, GOSSIP_RESPONSE }

    public GossipMsg(ArrayList<NodeProfile> profiles, Types type, PolderCastNode sender) {
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
        ArrayList<NodeProfile> nodeProfiles = new ArrayList<NodeProfile>(this.profiles);
        Collections.copy(nodeProfiles, this.profiles);
        return nodeProfiles;
    }

    public PolderCastNode getSender() {
        return this.sender;
    }

    public Types getType() { return this.type; }
}
