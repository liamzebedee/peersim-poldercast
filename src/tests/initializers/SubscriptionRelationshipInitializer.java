package tests.initializers;

import com.github.kohanyirobert.ebson.BsonDocument;
import com.github.kohanyirobert.ebson.BsonDocuments;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.sun.org.apache.xerces.internal.impl.xs.opti.DefaultDocument;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.ID;
import sun.security.krb5.Config;
import tests.util.BaseNode;
import tests.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/*
 * Initializes subscription relationships between nodes uniformly up to a certain size
 */
public class SubscriptionRelationshipInitializer implements NodeInitializer, Control {
    // http://forward.cs.illinois.edu/datasets/UDI/UDI-TwitterCrawl-Aug2012-Network.zip

    /*
    Over six months, we obtained via LiveJournal&rsquo;s RPC services
    information about 1.8 million users3. This included:
    (i) a list of users subscribing to their journals; and also (ii)
    a list of journals subscribed by these users. In order to obtain
    a self-contained non-biased universe of subscriptions, we
    randomly selected a small seed set of journals from the trace.
    Next, we gathered the list of all users subscribed to at least
    one journal in the seed set. These users (and their respective
    journals) form the universe of nodes in our simulation. As an
    example, a seed set of 10,000 journals gave us a universe of
    304,814 users. Next, based on the experiment, the X most
    subscribed-to journals in this universe were selected to be
    our publishers (the value of X depends on the experiment).
    Subscriptions of users outside the universe&rsquo;s publishers
    were pruned.
    "Note that using the most popular publishers does not bias correlation, as our seed set is unbiased"
     */

    public final int NUMBER_OF_NODES;
    public final int NUMBER_OF_TOPICS;
    private LinkedHashMap<Integer, HashSet<Integer>> nodesForSimulation = new LinkedHashMap<Integer, HashSet<Integer>>();
    private ArrayList<ID> subscriptions;
    // Rolling index for the interests we are going to set
    private int initializerIndex = 0;

    public SubscriptionRelationshipInitializer(String configPrefix) {
        this.NUMBER_OF_NODES = Configuration.getInt(configPrefix + ".numberOfNodes");
        this.NUMBER_OF_TOPICS = Configuration.getInt(configPrefix + ".numberOfTopics");
        this.subscriptions = new ArrayList<ID>(this.NUMBER_OF_TOPICS);

        // Generate topics
        for(int i = 0; i < this.NUMBER_OF_TOPICS; i++) {
            subscriptions.add(new ID(i));
        }

        /*String datasetFile = Configuration.getString(configPrefix + ".datasetFile");

        HashSet<Integer> subscriptionSpace = new HashSet<Integer>();
        // mapping of node number to list of topic numbers
        HashMap<Integer, HashSet<Integer>> sample = new HashMap<Integer, HashSet<Integer>>();
        // open twitter dataset
        System.out.println("SubscriptionRelationshipInitializer: loading dataset (this should take around 10s)...");
        BsonDocument document = null;
        try {
            document = BsonDocuments.readFrom(ByteBuffer.wrap(Files.toByteArray(new File(datasetFile))).order(ByteOrder.LITTLE_ENDIAN));
        } catch(Exception e) { e.printStackTrace(); }
        // ubjson is crap compared to the python library, but it works and will do for now
        for(Map.Entry<String, Object> entry : document.entrySet()) {
            int node = Integer.parseInt(entry.getKey());
            HashSet<Integer> subscriptions = new HashSet<Integer>();

            Collection<Object> subs = (((Map<String, Object>) entry.getValue()).values());
            for(Object sub : subs) {
                subscriptions.add((Integer) sub);
                subscriptionSpace.add((Integer) sub);
            }
            sample.put(node, subscriptions);
        }

        // 1. randomly select a small seed set of topics from the sample = seed set
        final int NUM_SEEDS = subscriptionSpace.size();
        HashSet<Integer> subscriptionSeedSet = new HashSet<Integer>();
        ArrayList<Integer> tmpSubscriptionSpace = new ArrayList<Integer>(subscriptionSpace);
        Collections.shuffle(tmpSubscriptionSpace, CommonState.r);
        for(int i = 0; i < NUM_SEEDS; i++) {
            subscriptionSeedSet.add(tmpSubscriptionSpace.get(i));
        }

        // 2. gather list of all users subscribed to at least one topic in the seed set = universe
        Iterator<Map.Entry<Integer, HashSet<Integer>>> subiter = sample.entrySet().iterator();
        while(nodesForSimulation.size() < this.NUMBER_OF_NODES && subiter.hasNext()) {
            Map.Entry<Integer, HashSet<Integer>> nodeWithSubscriptions = subiter.next();
            // Add node if it is subscribed to at least one topic of the seed set
            if(!Sets.intersection(nodeWithSubscriptions.getValue(), subscriptionSeedSet).isEmpty()) {
                nodesForSimulation.put(nodeWithSubscriptions.getKey(), nodeWithSubscriptions.getValue());
            }
        }

        // 3. select X-most subscribed-to topics out of the subscriptions of the nodesForSimulation
        HashSet<Integer> mostSubscribedToTopics = new HashSet<Integer>(this.NUMBER_OF_TOPICS);
        HashMap<Integer, Integer> mapOfTopicToPopularity = new HashMap<Integer, Integer>();
        // Do a tally of most popular topics
        for(HashSet<Integer> topics : this.nodesForSimulation.values()) {
            for(int topic : topics) {
                int score = 0;
                if(mapOfTopicToPopularity.containsKey(topic)) {
                    score = mapOfTopicToPopularity.get(topic);
                }
                mapOfTopicToPopularity.put(topic, score+1);
            }
        }
        // Sort them based on popularity descending
        List<Map.Entry<Integer, Integer>> topicPopularityEntries =
                new ArrayList<Map.Entry<Integer,Integer>>(mapOfTopicToPopularity.entrySet());
        Collections.sort(topicPopularityEntries, new EntryComparator<Integer, Integer>());
        // add them
        for(int i = 0; i < this.NUMBER_OF_TOPICS; i++) {
            mostSubscribedToTopics.add(topicPopularityEntries.get(i).getKey());
        }

        // 4. prune any subscriptions not found in mostSubscribedToTopics
        for(HashSet<Integer> subscriptions : this.nodesForSimulation.values()) {
            Iterator<Integer> iter = subscriptions.iterator();
            while(iter.hasNext()) {
                if(! mostSubscribedToTopics.contains(iter.next())) iter.remove();
            }
        }

        // 5. prune nodes until size is at NUMBER_OF_NODES*/
    }

    /**
     * Used to sort Map.Entry elements in decreasing order of value.

    static final class EntryComparator<K,V extends Comparable<V>> implements Comparator<Map.Entry<K,V>> {
        public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
            return - o1.getValue().compareTo(o2.getValue());
        }
    }*/

    @Override
    public void initialize(Node node) {
        this.initialise((BaseNode) node);
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
        int numTopicsToAdd = Util.randInt(CommonState.r, 100, this.NUMBER_OF_TOPICS);
        for(int i = 0; i < numTopicsToAdd; i++) {
            baseNode.subscribe(subscriptions.get(i));
        }
        /*HashSet<Integer> subscriptions = new ArrayList<HashSet<Integer>>(this.nodesForSimulation.values()).get(this.initializerIndex);
        System.out.println("initialising "+subscriptions.size()+" for node");
        for(int sub : subscriptions) baseNode.subscribe(new ID(sub));
        initializerIndex = (initializerIndex + 1) % this.NUMBER_OF_NODES;*/

    }
}
