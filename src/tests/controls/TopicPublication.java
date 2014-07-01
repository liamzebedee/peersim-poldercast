package tests.controls;

import org.apache.commons.math3.util.Pair;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import poldercast.util.ID;
import poldercast.util.PolderCastBaseNode;
import tests.initializers.SubscriptionRelationshipInitializer;
import tests.util.BaseNode;
import tests.util.Util;

import java.util.*;

public class TopicPublication extends BaseControl {
    private final int STARTING_DELAY;
    private final double PERCENTAGE_TOPIC_ACTIVITY;
    private final int NUMBER_OF_TOPICS_TO_MEASURE;
    private final int THRESHOLD_NODES;
    private ArrayList<Double> giniCoefficients = new ArrayList<Double>();
    // topic -> event
    private HashMap<ID, Integer> mapOfTopicToEvent = new HashMap<ID, Integer>();
    // event -> list<time, hitRatio>
    private HashMap<Integer, List<Pair<Integer, Double>>> mapOfEventToHitRatio
            = new HashMap<Integer, List<Pair<Integer, Double>>>(); // java is not verbose

    public TopicPublication(String prefix) {
        super(prefix);
        this.STARTING_DELAY = Configuration.getInt(prefix + ".startingDelay");
        this.PERCENTAGE_TOPIC_ACTIVITY = Configuration.getDouble(prefix + ".percentageTopicActivity");
        this.NUMBER_OF_TOPICS_TO_MEASURE = Configuration.getInt(prefix + ".numberOfTopicsToMeasure");
        this.THRESHOLD_NODES = Configuration.getInt(prefix + ".thresholdNodes");
    }

    @Override
    public boolean execute() {
        /*
         * measure the dissemination speed through estimating the number of cycles it takes to reach 95%+ of subscribers
         * measure the hit ratio starting at the Nth cycle for 100 cycles
         */
        long time = CommonState.getTime();
        if(time == this.STARTING_DELAY) {
            System.out.println("Publishing events, measuring time to receive");
            for(int i = 0; i < this.NUMBER_OF_TOPICS_TO_MEASURE; i++) {
                // Measure top 5 most popular topics
                ID topic = SubscriptionRelationshipInitializer.subscriptions[i+1]; // starts at index 1
                BaseNode node = Util.getRandomNodeForTopic(topic, null);
                byte[] event = getUniqueEventToBePublished(topic);
                mapOfTopicToEvent.put(topic, Arrays.hashCode(event));
                mapOfEventToHitRatio.put(Arrays.hashCode(event), new ArrayList<Pair<Integer, Double>>());
                node.publish(topic, event);
            }

        } else if(time == CommonState.getEndTime() - Configuration.getInt("CYCLE")) {
            System.out.println("Logging TopicPub stats");
            out.println("event \t cycle # \t hit ratio");
            double averageTime = 0;
            for(Map.Entry<Integer, List<Pair<Integer, Double>>> entry : this.mapOfEventToHitRatio.entrySet()) {
                double timeForEvent = 0;
                int eventID = entry.getKey();
                List<Pair<Integer, Double>> data = entry.getValue();
                for(Pair<Integer,Double> pair : data) {
                    out.println(eventID+"\t"+pair.getKey()+"\t"+pair.getValue());
                    if(timeForEvent == 0 && pair.getValue() > THRESHOLD_NODES) {
                        timeForEvent = pair.getKey();
                    }
                }
                out.println("Cycles to get to threshold = "+timeForEvent+"\n");
                averageTime += timeForEvent;
            }
            averageTime /= (double) this.mapOfEventToHitRatio.size();
            out.println("Average cycles to get to threshold = "+averageTime);

            double averageGiniCoefficient = 0;
            for(Double giniCoefficient : this.giniCoefficients) {
                averageGiniCoefficient += giniCoefficient;
            }
            averageGiniCoefficient /= this.giniCoefficients.size();

            out.println("Average Load distribution (Gini coefficient) = " + averageGiniCoefficient);

        } else if(time > this.STARTING_DELAY) {
            // Each period this runs
            // EMULATE ACTIVITY
            double avgGini = 0;
            double avgGini_i = 1;
            for(int i = 1; i < SubscriptionRelationshipInitializer.subscriptions.length; i++) {
                ID topic = SubscriptionRelationshipInitializer.subscriptions[i];
                int numSubs = SubscriptionRelationshipInitializer.subscriptionToFrequency[i];
                if(numSubs < 1) continue;
                avgGini += Util.giniCoefficient(numSubs, Util.getTopicPublicationLoadMatrix(topic));
                avgGini_i++;
                if(CommonState.r.nextDouble() < PERCENTAGE_TOPIC_ACTIVITY) {
                    BaseNode node = Util.getRandomNodeForTopic(topic, null);
                    byte[] event = Util.randomEventData();
                    node.publish(topic, event);
                }
            }
            this.giniCoefficients.add(avgGini/avgGini_i);

            // Log hit ratio of events
            for(Map.Entry<ID, Integer> entry : this.mapOfTopicToEvent.entrySet()) {
                double hits = 0;
                double subscribers = 0;
                ID topic = entry.getKey();
                int eventID = entry.getValue();
                for(int i = 0; i < Network.size(); i++) {
                    BaseNode node = (BaseNode) Network.get(i);
                    if(!node.isSubscribed(topic)) continue;
                    subscribers++;
                    if(node.hasReceivedEvent(eventID)) hits++;
                }
                hits /= subscribers;
                List<Pair<Integer,Double>> list = this.mapOfEventToHitRatio.get(eventID);
                list.add(new Pair<Integer, Double>((int) time, hits));
            }
        }
        return false;
    }

    private byte[] getUniqueEventToBePublished(ID topic) {
        return ("unique event "+topic.toString()+CommonState.r.nextDouble()).getBytes();
    }
}
