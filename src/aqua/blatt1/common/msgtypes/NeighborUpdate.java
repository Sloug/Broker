package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class NeighborUpdate implements Serializable {
    private final Neighbors neighbors;

    public static final class Neighbors implements Serializable {
        public Neighbors(String leftNeighbor, String rightNeighbor) {
            this.leftNeighbor = leftNeighbor;
            this.rightNeighbor = rightNeighbor;
        }

        private final String leftNeighbor;
        private final String rightNeighbor;

        public String getLeftNeighbor() {
            return leftNeighbor;
        }

        public String getRightNeighbor() {
            return rightNeighbor;
        }

        public boolean isRightNeighbor(String toCompare) {
            return rightNeighbor.equals(toCompare);
        }

        public boolean isLeftNeighbor(String toCompare) {
            return leftNeighbor.equals(toCompare);
        }
    }


    public NeighborUpdate(String leftNeighbor, String rightNeighbor) {
        this.neighbors = new Neighbors(leftNeighbor, rightNeighbor);
    }

    public Neighbors getNeighbors() {
        return neighbors;
    }
}
