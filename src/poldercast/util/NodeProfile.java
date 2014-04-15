package poldercast.util;

import peersim.core.GeneralNode;

import java.util.Map;

public class NodeProfile extends GeneralNode implements SizeInBits {
    private int age;
    // ip (not needed)
    private final ID id;
    // mapping between topic ID and priority of finding nodes for that topic
    private Map<ID, Byte> interests;

    public NodeProfile(String prefix, byte[] ip, ID id, Map<ID,Byte> interests) {
        super(prefix);
        this.age = 0;
        this.id = id;
        this.interests = interests;
    }

    public int getAge() { return age; }

    public ID getId() {
        return id;
    }

    public void incrementAge() {
        this.age++;
    }

    public int getSizeInBits() {
        int size = 0;
        size += this.id.BITS;
        size += 32; // age
        for(Map.Entry<ID, Byte> s : interests.entrySet()) {
            if(s.getKey() != null) size += (id.BITS + 8);
        }
        return size;
    }
}
