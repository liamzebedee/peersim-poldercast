package poldercast.util;

import java.math.BigInteger;
import java.util.Random;

public class ID {
    /**
     * Number of bits composing a PolderCast identifier.
     */
    public final static short BITS = 128;
    /**
     * The actual PolderCast identifier.
     */
    private final BigInteger id;

    public ID(Random prng) {
        // randomly generate a value in the [0..2^(BITS)-1] range
        this.id = new BigInteger(BITS, prng);
    }
}
