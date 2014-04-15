package poldercast.util;

import peersim.core.CommonState;
import peersim.core.GeneralNode;

import java.util.Map;

public class NodeProfile implements SizeInBits {
    private int age;
    private ID id;
    // mapping between topic ID and priority of finding nodes for that topic
    private Map<ID, Byte> interests;
    private PolderCastNode node;

    public NodeProfile(PolderCastNode node, ID id, Map<ID,Byte> interests) {
        this.age = 0;
        this.id = id;
        this.interests = interests;
        // NOTE in the real implementation, this would include network details, but this isn't necessary here
        // instead we use the node to send messages via the @Link{Transport}
        this.node = node;
    }

    public int getAge() { return age; }

    public ID getID() {
        return id;
    }

    public void incrementAge() {
        this.age++;
    }

    public void resetAge() { this.age = 0; }

    public int getSizeInBits() {
        int size = 0;
        size += this.id.BITS;
        size += 32; // age
        for(Map.Entry<ID, Byte> s : interests.entrySet()) {
            if(s.getKey() != null) size += (id.BITS + 8);
        }
        return size;
    }

    public PolderCastNode getNode() { return node; }
}
