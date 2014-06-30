package poldercast.util;

import poldercast.protocols.RingsProtocol;

import java.util.HashMap;
import java.util.Map;

public class NodeProfile implements SizeInBits {
    private int age;
    private ID id;
    // mapping between topic ID and priority of finding nodes for that topic
    private Map<ID, Byte> subscriptions;
    private PolderCastBaseNode node;

    public NodeProfile(PolderCastBaseNode node, ID id) {
        this.age = 0;
        this.id = id;
        this.subscriptions = new HashMap<ID, Byte>();
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
        for(Map.Entry<ID, Byte> s : subscriptions.entrySet()) {
            if(s.getKey() != null) size += (id.BITS + 8);
        }
        return size;
    }

    public PolderCastBaseNode getNode() { return node; }

    public String toString() {
        return this.node.getID()+"";
    }

    public Map<ID, Byte> getSubscriptions() { return this.subscriptions; }

    public synchronized void addSubscription(ID topic) {
        // priority is defined as the number of nodes we need to fill the view
        byte defaultPriority = 4; // TODO
        this.subscriptions.put(topic, defaultPriority);
    }

    @Override
    public boolean equals(Object nodeProfile) {
        NodeProfile node = (NodeProfile) nodeProfile;
        return this.id.equals(node.id);
    }

    public int compare(NodeProfile nodeB) {
        return this.getID().id.compareTo(nodeB.getID().id);
    }
}
