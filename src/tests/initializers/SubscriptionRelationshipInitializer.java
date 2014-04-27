package tests.initializers;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.ID;
import sun.security.krb5.Config;
import tests.util.BaseNode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.*;
import java.util.*;

/*
 * Initializes subscription relationships between nodes uniformly up to a certain size
 * Designed with this dataset https://wiki.engr.illinois.edu/display/forward/Dataset-UDI-TwitterCrawl-Aug2012 in mind
 */
public class SubscriptionRelationshipInitializer implements NodeInitializer {
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

    objective: form list of 10K nodes that have pub-sub relationships

    open twitter dataset
    parse into list of nodes and their subsequent subscriptions = sample.{nodes,subscriptions}
    nodesForSimulation = []
    subscriptionSeedSet = select at random 10 subscriptions from sample.subscriptions
    subiter = subscriptionSeedSet.iter
    while nodesForSimulation.size < 10000:
        nodesForSimulation += getSubscribers(subiter.next)
    X = 1000
    subscriptionSpace = select X-most subscribed-to topics out of nodesForSimulation
    for node in nodesForSimulation:
        prune any subscriptions not found in subscriptionSpace

    1. randomly select a small seed set of topics from the sample = seed set
    2. gather list of all users subscribed to at least one topic in the seed set = universe
    3. select X-most subscribed-to topics to be the subscriptionSpace
    4. subscriptions of users outside the universe's subscriptionSpace were pruned
    "Note that using the most popular publishers does not bias correlation, as our seed set is unbiased"
     */

    public final int NUMBER_OF_NODES;
    public final int NUMBER_OF_TOPICS;
    private HashMap<Integer, HashSet<Integer>> nodesForSimulation = new HashMap<Integer, HashSet<Integer>>();

    public SubscriptionRelationshipInitializer(String configPrefix) {
        this.NUMBER_OF_NODES = Configuration.getInt(configPrefix + ".numberOfNodes");
        this.NUMBER_OF_TOPICS = Configuration.getInt(configPrefix + ".numberOfTopics");
        String datasetFile = Configuration.getString(configPrefix + ".datasetFile");

        HashSet<Integer> subscriptionSpace = new HashSet<Integer>();
        HashMap<Integer, HashSet<Integer>> sample = new HashMap<Integer, HashSet<Integer>>();
        // open twitter dataset
        // parse into list of nodes and their subsequent subscriptions
        // Format: [ID1]\t[ID2]
        try {
            BufferedReader reader = new BufferedReader(new FileReader(datasetFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] items = line.split("\t");
                int id1 = Integer.parseInt(items[0]);
                int id2 = Integer.parseInt(items[1]);
                subscriptionSpace.add(id2);
                if(sample.containsKey(id1)) {
                    HashSet<Integer> subs = sample.get(id1);
                    subs.add(id2);
                } else {
                    HashSet<Integer> subs = new HashSet<Integer>();
                    subs.add(id2);
                    sample.put(id1, subs);
                }
            }
            reader.close();
        } catch(Exception e) { e.printStackTrace(); }

        // subscriptionSeedSet = select at random 10 subscriptions from sample.subscriptions
        HashSet<Integer> subscriptionSeedSet = new HashSet<Integer>();
        ArrayList<Integer> tmpSubscriptionSpace = new ArrayList<Integer>(subscriptionSpace);
        Collections.shuffle(tmpSubscriptionSpace, CommonState.r);
        for(int i = 0; (i < 10); i++) {
            // while we haven't gotten 10 unique subs
            subscriptionSeedSet.add(tmpSubscriptionSpace.get(i));
        }

        // 1. randomly select a small seed set of topics from the sample = seed set


        // 2. gather list of all users subscribed to at least one topic in the seed set = universe
        Iterator<Integer> subiter = subscriptionSeedSet.iterator();
        while(nodesForSimulation.size() < this.NUMBER_OF_NODES) {
            int node = subiter.next();
            nodesForSimulation.put(node, nodesForSimulation.get(node));
        }

        // 3. select X-most subscribed-to topics out of nodesForSimulation
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
    }

    /**
     * Used to sort Map.Entry elements in decreasing order of value.
     */
    static final class EntryComparator<K,V extends Comparable<V>> implements Comparator<Map.Entry<K,V>> {
        public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
            return - o1.getValue().compareTo(o2.getValue());
        }
    }

    public void initSQLDb() {
        // load the sqlite-JDBC driver using the current class loader
        try { Class.forName("org.sqlite.JDBC"); } catch(ClassNotFoundException e) { e.printStackTrace(); }
        Connection connection = null;
        try {
            // create a database connection
            connection = DriverManager.getConnection("jdbc:sqlite:dataset1.db");
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(50);
            statement.execute("SELECT *");
        } catch(SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if(connection != null) connection.close();
            }
            catch(SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void initialize(Node node) {

    }
}
