package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class NeighborUpdate implements Serializable {
    private final Neighbors neighbors;

    public static final class Neighbors implements Serializable {
        public Neighbors(InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
            this.leftNeighbor = leftNeighbor;
            this.rightNeighbor = rightNeighbor;
        }

        private final InetSocketAddress leftNeighbor;
        private final InetSocketAddress rightNeighbor;

        public InetSocketAddress getLeftNeighbor() {
            return leftNeighbor;
        }

        public InetSocketAddress getRightNeighbor() {
            return rightNeighbor;
        }

        public boolean isRightNeighbor(InetSocketAddress toCompare) {
            return rightNeighbor.equals(toCompare);
        }

        public boolean isLeftNeighbor(InetSocketAddress toCompare) {
            return leftNeighbor.equals(toCompare);
        }
    }


    public NeighborUpdate(InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
        this.neighbors = new Neighbors(leftNeighbor, rightNeighbor);
    }

    public Neighbors getNeighbors() {
        return neighbors;
    }
}
