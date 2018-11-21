package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class SnapshotCollectionToken implements Serializable {
    private int globalFishPopulation;

    public SnapshotCollectionToken(int fishPopulation) {
        this.globalFishPopulation = fishPopulation;
    }

    public void addFishesToPopulation(int fishPopulation) {
        this.globalFishPopulation += fishPopulation;
    }

    public int getGlobalFishPopulation() {
        return globalFishPopulation;
    }
}