package poldercast.util;

import com.sun.org.apache.xml.internal.security.utils.Base64;

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

    public String toString() {
        return new String(Base64.encode(this.id));
    }

    @Override
    public boolean equals(Object id) {
        ID anotherID = (ID) id;
        return this.id.equals(anotherID.id);
    }
}
