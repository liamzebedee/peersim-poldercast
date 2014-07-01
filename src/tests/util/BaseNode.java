package tests.util;

import peersim.config.Configuration;
import peersim.core.GeneralNode;
import poldercast.util.ID;

import java.util.ArrayList;

public abstract class BaseNode extends GeneralNode {
    public int load = 0;
    public boolean measureTopicSubscriptionLoad;
    public boolean measureTopicPublicationLoad;

    public BaseNode(String configPrefix) {
        super(configPrefix);
        System.out.println(configPrefix);
        this.measureTopicSubscriptionLoad = Configuration.getBoolean(configPrefix + ".measureTopicSubscriptionLoad");
        this.measureTopicPublicationLoad = Configuration.getBoolean(configPrefix + ".measureTopicPublicationLoad");
    }

    public abstract void publish(ID topic, byte[] event);
    public abstract void subscribe(ID topic);
    public abstract boolean isSubscribed(ID topic);
    public abstract boolean hasReceivedEvent(byte[] event);
    public abstract ArrayList<ID> getSubscriptions();
}
