package tests.util;

import peersim.core.CommonState;
import peersim.core.Network;
import poldercast.util.ID;

import java.util.ArrayList;
import java.util.Random;

public class Util {
    public static byte[] randomEventData() {
        return ("foobar"+CommonState.r.nextLong()).getBytes();
    }

    /**
     * Returns a pseudo-random number between min and max, inclusive.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimum value
     * @param max Maximum value.  Must be greater than min.
     * @return Integer between min and max, inclusive.
     * @see java.util.Random#nextInt(int)
     *
     * Thanks to Greg Case from http://stackoverflow.com/questions/363681/generating-random-integers-in-a-range-with-java
     */
    public static int randInt(Random r, int min, int max) {

        // Usually this should be a field rather than a method variable so
        // that it is not re-seeded every call.
        Random rand = new Random();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }

    public static double giniCoefficient(double numberOfNodes, double[] loadMatrix) {
        double partA;
        double partB;
        double partC = 0;
        double meanLoad = 0;
        for(int i = 0; i < numberOfNodes; i++) {
            meanLoad += loadMatrix[i];
            for(int j = 0; j < numberOfNodes; j++) {
                partC += Math.abs(loadMatrix[i] - loadMatrix[j]);
            }
        }
        meanLoad /= numberOfNodes;
        partA = 1.0/(2.0*meanLoad);
        partB = 1.0/(Math.pow(numberOfNodes, 2));
        double G = partA * partB * partC;
        return G;
    }

    public static double[] getTopicSubscriptionLoadMatrix() {
        double[] topicSubscriptionLoadMatrix = new double[Network.size()];
        for(int i = 0; i < Network.size(); i++) {
            BaseNode node = (BaseNode) Network.get(i);
            topicSubscriptionLoadMatrix[i] = node.getTopicSubscriptionLoad();
        }
        return topicSubscriptionLoadMatrix;
    }

    public static double[] getTopicPublicationLoadMatrix(ID topic) {
        ArrayList<Double> topicPublicationLoadMatrix = new ArrayList<Double>(Network.size());
        for(int i = 0; i < Network.size(); i++) {
            BaseNode node = (BaseNode) Network.get(i);
            if(node.isSubscribed(topic)) topicPublicationLoadMatrix.add(node.getTopicPublicationLoad(topic));
        }
        double[] matrix = new double[topicPublicationLoadMatrix.size()];
        int i = 0;
        for(Double d : topicPublicationLoadMatrix) {
            matrix[i] = d;
            i++;
        }
        return matrix;
    }

    public static BaseNode getRandomNodeForTopic(ID topic, BaseNode excludedNode) {
        ArrayList<BaseNode> nodes = new ArrayList<BaseNode>(Network.size());
        for(int i = 0; i<Network.size(); i++) {
            BaseNode node = (BaseNode) Network.get(i);
            if(node == excludedNode) continue;
            if(node.isSubscribed(topic)) nodes.add(node);
        }
        if(nodes.isEmpty()) throw new RuntimeException("No subscribers");
        return nodes.get(CommonState.r.nextInt(nodes.size()));
    }
}
