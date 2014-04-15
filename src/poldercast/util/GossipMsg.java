package poldercast.util;

public class GossipMsg implements SizeInBits {
    private NodeProfile[] profiles;
    private Types type;

    public enum Types { GOSSIP_QUERY, GOSSIP_RESPONSE }

    public GossipMsg(NodeProfile[] profiles, Types type) {
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
}
