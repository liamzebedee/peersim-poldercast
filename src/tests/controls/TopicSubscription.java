package tests.controls;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import poldercast.util.ID;
import poldercast.util.PolderCastBaseNode;
import tests.initializers.SubscriptionRelationshipInitializer;
import tests.util.BaseNode;
import tests.util.Util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TopicSubscription extends BaseControl {
    public final int STARTING_DELAY;
    public final int NUMBER_OF_TOP_TOPICS_TO_RECORD;
    private HashMap<ID, BaseNode> topicsAndNodes = new HashMap<ID, BaseNode>();
    private LinkedHashMap<ID, Integer> speedToSubscribe = new LinkedHashMap<ID, Integer>();

    public TopicSubscription(String prefix) {
        super(prefix);
        this.STARTING_DELAY = Configuration.getInt(prefix+".startingDelay");
        this.NUMBER_OF_TOP_TOPICS_TO_RECORD = Configuration.getInt(prefix+".numberOfTopTopicsToRecord");
    }

    public static byte[] eventData(ID topic) {
        return ("event data swag "+topic.toString()).getBytes();
    }

    @Override
    public boolean execute() {
        /*
         * Measure speed (in cycles) at which a node can subscribe to a new topic and start receiving events published to it
         * That is, the number of cycles it takes for a new node to start receiving events on the topic.
         * One event published from a random subscriber each cycle.
         */

        long time = CommonState.getTime();
        if(time == this.STARTING_DELAY) {
            System.out.println("Selecting nodes for most-popular/least-popular topic subscription test");

            for(int i = 0; i < NUMBER_OF_TOP_TOPICS_TO_RECORD; i++) {
                ID topic = SubscriptionRelationshipInitializer.subscriptions[i+1]; // starts at index 1
                BaseNode node = Util.getRandomNodeForTopic(topic, null);
                topicsAndNodes.put(topic, node);
                speedToSubscribe.put(topic, null);
            }

        } else if(time == CommonState.getEndTime() - Configuration.getInt("CYCLE")) {
            System.out.println("Logging TopicSub stats");
            out.println("Number of subscribers | Cycles to subscribe and receive events");
            int i = 0;
            for(Map.Entry<ID, Integer> entry : this.speedToSubscribe.entrySet()) {
                out.println(SubscriptionRelationshipInitializer.subscriptionToFrequency[i+1] + " | " + entry.getValue());
                i++;
            }
            out.println();
            for(int j = 0; j < Network.size(); j++) {
                BaseNode node = (BaseNode) Network.get(i);
                //System.out.println(node.getTopicPublicationLoad());
            }

            out.println("Load distribution (Gini coefficient) = "
                    + Util.giniCoefficient(Network.size(), Util.getTopicSubscriptionLoadMatrix()));

        } else if(time > this.STARTING_DELAY) {
            // Check if node has received event on topic yet
            for(Map.Entry<ID, BaseNode> entry : this.topicsAndNodes.entrySet()) {
                ID topic = entry.getKey();
                BaseNode node = entry.getValue();
                // Has not received event on it since last cycle, still waiting
                if(this.speedToSubscribe.get(topic) == null) {
                    if(node.hasReceivedEvent(this.eventData(topic))) {
                        System.out.println("boom");
                        this.speedToSubscribe.put(topic, (int) time);
                    } else {
                        // no, send out events from random node
                        BaseNode randomNode = Util.getRandomNodeForTopic(topic, node);
                        randomNode.publish(topic, this.eventData(topic));
                    }
                }
            }
        }

        return false;
    }

}
