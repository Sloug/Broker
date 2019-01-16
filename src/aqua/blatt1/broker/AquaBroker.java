package aqua.blatt1.broker;

import aqua.blatt1.common.FishModel;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AquaBroker extends Remote {
    public String registerRequest(String stub) throws RemoteException;

    public void deregister(String id) throws RemoteException;

    public void handoff(String stub, FishModel fish) throws RemoteException;

    public String nameResolutionRequest(String tankId) throws RemoteException;
}