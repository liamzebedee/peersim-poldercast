package tests.initializers;

import org.apache.commons.math3.distribution.ZipfDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.ID;
import tests.util.BaseNode;
import tests.util.Util;

import java.util.Map;

public class SubscriptionRelationshipInitializer implements NodeInitializer, Control {
    public final int NUMBER_OF_NODES;
    public final int NUMBER_OF_TOPICS;
    public static ID[] subscriptions;
    public static double[] subscriptionProbabilitiesCache;
    public static int[] subscriptionToFrequency;
    private ZipfDistribution dist;
    private JDKRandomGenerator rng;

    public SubscriptionRelationshipInitializer(String configPrefix) {
        this.NUMBER_OF_NODES = Configuration.getInt(configPrefix + ".numberOfNodes");
        this.NUMBER_OF_TOPICS = Configuration.getInt(configPrefix + ".numberOfTopics");
        this.subscriptions = new ID[this.NUMBER_OF_TOPICS + 1];
        this.subscriptionProbabilitiesCache = new double[this.NUMBER_OF_TOPICS + 1];
        subscriptionToFrequency = new int[NUMBER_OF_TOPICS + 1];

        // Apache math needs its own rng type
        this.rng = new JDKRandomGenerator();
        rng.setSeed(CommonState.r.getLastSeed());

        // create zipf distribution for topics
        this.dist = new ZipfDistribution(rng, this.NUMBER_OF_TOPICS, 1.37);
        for(int i = 1; i < this.NUMBER_OF_TOPICS; i++) {
            this.subscriptionToFrequency[i] = 0;
            this.subscriptions[i] = new ID(i);
            this.subscriptionProbabilitiesCache[i] = this.dist.probability(i);
        }
    }

    @Override
    public void initialize(Node node) {
        this.initialise((BaseNode) node);
    }

    public boolean shouldBeSubscribedToTopicN(int topicN) {
        return this.rng.nextDouble() < this.subscriptionProbabilitiesCache[topicN];
    }

    @Override
    public boolean execute() {
        for(int i = 0; i < Network.size(); i++) {
            BaseNode node = (BaseNode) Network.get(i);
            this.initialise(node);
        }
        return false;
    }

    public void initialise(BaseNode baseNode) {
        boolean hasSub = false;
        int retries = -1;
        while(!hasSub) {
            retries++;
            for(int i = 1; i < this.subscriptionProbabilitiesCache.length; i++) {
                if(shouldBeSubscribedToTopicN(i)) {
                    baseNode.subscribe(subscriptions[i]);
                    subscriptionToFrequency[i] = subscriptionToFrequency[i] + 1;
                    hasSub = true;
                }
            }
        }
    }

    private void estimateProbability() {
        double success = 0;
        int trials = 10000;
        for(int trial = 0; trial < trials; trial++) {
            System.out.println(trial);
            if(this.shouldBeSubscribedToTopicN(1)) success++;
        }
        double percentSuccess = (success/trials);
        System.out.println("from probability "+this.dist.probability(1)+" success was "+percentSuccess);
    }
}
