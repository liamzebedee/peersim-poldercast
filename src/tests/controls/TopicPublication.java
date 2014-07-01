package tests.controls;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import poldercast.util.ID;
import poldercast.util.PolderCastBaseNode;
import tests.initializers.SubscriptionRelationshipInitializer;
import tests.util.BaseNode;
import tests.util.Util;

import java.util.ArrayList;
import java.util.Arrays;

public class TopicPublication extends BaseControl {
    private final int STARTING_DELAY;
    private final double PERCENTAGE_TOPIC_ACTIVITY;
    private ArrayList<Double> giniCoefficients = new ArrayList<Double>();

    public TopicPublication(String prefix) {
        super(prefix);
        this.STARTING_DELAY = Configuration.getInt(prefix + ".startingDelay");
        this.PERCENTAGE_TOPIC_ACTIVITY = Configuration.getDouble(prefix + ".percentageTopicActivity");
    }

    @Override
    public boolean execute() {
        /*
         * measure the dissemination speed through estimating the number of cycles it takes to reach 95%+ of subscribers
         * measure the hit ratio starting at the Nth cycle for 100 cycles
         */
        long time = CommonState.getTime();
        if(time == this.STARTING_DELAY) {
            System.out.println("");

        } else if(time == CommonState.getEndTime() - Configuration.getInt("CYCLE")) {
            System.out.println("Logging TopicPub stats");

            out.println();

            double averageGiniCoefficient = 0;
            for(Double giniCoefficient : this.giniCoefficients) {
                averageGiniCoefficient += giniCoefficient;
                out.println(giniCoefficient);
            }
            averageGiniCoefficient /= this.giniCoefficients.size();

            out.println("Average Load distribution (Gini coefficient) = " + averageGiniCoefficient);

        } else if(time > this.STARTING_DELAY) {
            System.out.println(((PolderCastBaseNode) Network.get(0)).getNodeProfile().getAge());
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

        }
        return false;
    }
}
