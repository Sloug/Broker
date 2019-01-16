package aqua.blatt1.client;

import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.NeighborUpdate;

import java.rmi.Remote;

public interface AquaClient extends Remote {

    public void handoff(FishModel fish);

    public void updateNeighbor(NeighborUpdate.Neighbors neighbors);

    public void token();

    public void snapshotMarker(String stub);

    public void snapshotCollectionToken(int fishPopulation);

    public void locationRequest(String fishId);

    public void nameResolutionRequest(String stub, String requestId);

    public void locationUpdate(String fishId);
}