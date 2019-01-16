import SecureMessaging.SecureEndpoint;
import aqua.blatt1.broker.AquaBroker;
import aqua.blatt1.broker.ClientCollection;
import aqua.blatt1.client.AquaClient;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.common.msgtypes.NeighborUpdate.Neighbors;
import aqua.blatt2.broker.PoisonPill;
import messaging.*;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.*;

public class Broker implements AquaBroker {
    ReadWriteLock lock = new ReentrantReadWriteLock();
    private SecureEndpoint endpoint = new SecureEndpoint(4711);
    private volatile ClientCollection clientCollection = new ClientCollection();
    private volatile Integer registerCounter = 0;
    private volatile boolean stopRequested = false;
    private volatile Boolean firstRegister = true;

    public static void main(String[] args) {
//        StopGUIThread stopGUIThread = new StopGUIThread();
//        stopGUIThread.start();
        try {
            Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
            AquaBroker stub = (AquaBroker) UnicastRemoteObject.exportObject(new Broker(), 0);
            registry.rebind(Properties.BROKER_NAME, stub);
        } catch (RemoteException e) {
            System.exit(1);
        }

    }

    private AquaClient getAquaClient(String client) {
        Registry registry = null;
        AquaClient aquaClient = null;
        try {
            registry = LocateRegistry.getRegistry();
            aquaClient = (AquaClient) registry.lookup(client);
        } catch (Exception e) {
            System.exit(1);
        }
        return aquaClient;
    }

    @Override
    public String registerRequest(String stub) {
        AquaClient aquaClient = getAquaClient(stub);

        synchronized (registerCounter) {
            registerCounter++;
        }
        lock.writeLock().lock();
        String cliname = ("Sloug_" + registerCounter);
        clientCollection.add(cliname, stub);
        lock.writeLock().unlock();
        aquaClient.registeResponse(cliname);
        informNeighbors(stub, false);
        synchronized (firstRegister) {
            if (firstRegister) {
                aquaClient.token();
                firstRegister = false;
            }
        }
        return cliname;
    }

    @Override
    public void deregister(String stub, String id) throws RemoteException {
        informNeighbors(stub, true);

        lock.writeLock().lock();
        clientCollection.remove(clientCollection.indexOf(id));
        lock.writeLock().unlock();
        lock.readLock().lock();
        if (clientCollection.size() == 0) {
            synchronized (firstRegister) {
                firstRegister = true;
            }
        }
        lock.readLock().unlock();

    }

    @Override
    public void handoff(String stub, FishModel fishModel) throws RemoteException {
        Direction direction = fishModel.getDirection();
        if (direction == Direction.LEFT) {

            lock.readLock().lock();
            String stubLeft = (String) clientCollection.getLeftNeighorOf(clientCollection.indexOf(stub));
            AquaClient aquaClient = getAquaClient(stubLeft);
            aquaClient.handoff(fishModel);
            lock.readLock().unlock();
        } else {
            lock.readLock().lock();
            String stubRight = (String) clientCollection.getRightNeighorOf(clientCollection.indexOf(stub));
            AquaClient aquaClient = getAquaClient(stubRight);
            aquaClient.handoff(fishModel);
            lock.readLock().unlock();
        }
    }

    @Override
    public void nameResolutionResponse(String tankId, String requestId) throws RemoteException {

    }

    private Neighbors getNeighborsOfClient(String client) {
        String leftNeighborOfSender = (String) clientCollection.getLeftNeighorOf(clientCollection.indexOf(client));
        String rightNeighborOfSender = (String) clientCollection.getRightNeighorOf(clientCollection.indexOf(client));
        return new Neighbors(leftNeighborOfSender, rightNeighborOfSender);
    }

    private void passNeighborsToClient(String reciever, Neighbors neighbors) {
        NeighborUpdate neighborUpdate = new NeighborUpdate(neighbors.getLeftNeighbor(), neighbors.getRightNeighbor());
        AquaClient aquaClient = getAquaClient(reciever);
//        endpoint.send(reciever, neighborUpdate);
    }

    private void informNeighbors(String sender, boolean remove) {
        Neighbors neighborsMiddleClient = getNeighborsOfClient(sender);
        Neighbors neighborsLeftClient = getNeighborsOfClient(neighborsMiddleClient.getLeftNeighbor());
        Neighbors neighborsRightClient = getNeighborsOfClient(neighborsMiddleClient.getRightNeighbor());

        if (remove) {
            neighborsLeftClient = new Neighbors(neighborsLeftClient.getLeftNeighbor(), neighborsMiddleClient.getRightNeighbor());
            neighborsRightClient = new Neighbors(neighborsMiddleClient.getLeftNeighbor(), neighborsRightClient.getRightNeighbor());
        } else {
            passNeighborsToClient(sender, neighborsMiddleClient);
        }
        passNeighborsToClient(neighborsMiddleClient.getLeftNeighbor(), neighborsLeftClient);
        passNeighborsToClient(neighborsMiddleClient.getRightNeighbor(), neighborsRightClient);
    }

//    private class StopGUIThread extends Thread {
//        @Override
//        public void run() {
//            JOptionPane.showMessageDialog(null, "Shutdown Server?");
//            stopRequested = true;
//        }
//    }

//    private void broker() {
//
//        StopGUIThread stopGUIThread = new StopGUIThread();
//        stopGUIThread.start();
//
//        ExecutorService executorService = Executors.newFixedThreadPool(4);
//
//        while (!stopRequested) {
//            Message msg = endpoint.blockingReceive();
//            if (msg.getPayload() instanceof PoisonPill) {
//                stopGUIThread.interrupt();
//                break;
//            }
//            executorService.execute(new BrokerTask(msg));
//        }
//        executorService.shutdown();
//    }

//    private class BrokerTask implements Runnable {
//        ReadWriteLock lock = new ReentrantReadWriteLock();
//        private Message msg;
//
//        @Override
//        public void run() {
//            if (msg.getPayload() instanceof RegisterRequest)
//                register(msg);
//
//            if (msg.getPayload() instanceof DeregisterRequest)
//                deregister(msg);
//
//            if (msg.getPayload() instanceof HandoffRequest)
//                handoffFish(msg);
//
//            if (msg.getPayload() instanceof NameResolutionRequest)
//                sendNameResolutionResponse(msg);
//        }
//
//        private Neighbors getNeighborsOfClient(InetSocketAddress client) {
//            InetSocketAddress leftNeighborOfSender = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(client));
//            InetSocketAddress rightNeighborOfSender = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(client));
//            return new Neighbors(leftNeighborOfSender, rightNeighborOfSender);
//        }
//
//        private void passNeighborsToClient(InetSocketAddress reciever, Neighbors neighbors) {
//            NeighborUpdate neighborUpdate = new NeighborUpdate(neighbors.getLeftNeighbor(), neighbors.getRightNeighbor());
//            endpoint.send(reciever, neighborUpdate);
//        }
//
//        private void informNeighbors(InetSocketAddress sender, boolean remove) {
//            Neighbors neighborsMiddleClient = getNeighborsOfClient(sender);
//            Neighbors neighborsLeftClient = getNeighborsOfClient(neighborsMiddleClient.getLeftNeighbor());
//            Neighbors neighborsRightClient = getNeighborsOfClient(neighborsMiddleClient.getRightNeighbor());
//
//            if (remove) {
//                neighborsLeftClient = new Neighbors(neighborsLeftClient.getLeftNeighbor(), neighborsMiddleClient.getRightNeighbor());
//                neighborsRightClient = new Neighbors(neighborsMiddleClient.getLeftNeighbor(), neighborsRightClient.getRightNeighbor());
//            } else {
//                passNeighborsToClient(sender, neighborsMiddleClient);
//            }
//            passNeighborsToClient(neighborsMiddleClient.getLeftNeighbor(), neighborsLeftClient);
//            passNeighborsToClient(neighborsMiddleClient.getRightNeighbor(), neighborsRightClient);
//        }
//
//        private void sendNameResolutionResponse(Message msg) {
//            InetSocketAddress reciever = msg.getSender();
//            NameResolutionRequest nameResolutionRequest = (NameResolutionRequest) msg.getPayload();
//            endpoint.send(reciever, new NameResolutionResponse((InetSocketAddress) clientCollection
//                    .getClient(clientCollection.indexOf(nameResolutionRequest.getTankId())), nameResolutionRequest
//                    .getRequestId()));
//        }
//
//        private void register(Message msg) {
//            InetSocketAddress sender = msg.getSender();
//            Serializable payloadmsg = msg.getPayload();
//            synchronized (registerCounter) {
//                registerCounter++;
//            }
//            lock.writeLock().lock();
//            String cliname = ("Sloug_" + registerCounter);
//            clientCollection.add(cliname, sender);
//            lock.writeLock().unlock();
//            endpoint.send(sender, new RegisterResponse(cliname));
//            informNeighbors(sender, false);
//            synchronized (firstRegister) {
//                if (firstRegister) {
//                    endpoint.send(sender, new Token());
//                    firstRegister = false;
//                }
//            }
//        }
//
//        private void deregister(Message msg) {
//            InetSocketAddress sender = msg.getSender();
//            Serializable payloadmsg = msg.getPayload();
//            String id = ((DeregisterRequest) payloadmsg).getId();
//
//            informNeighbors(sender, true);
//
//            lock.writeLock().lock();
//            clientCollection.remove(clientCollection.indexOf(id));
//            lock.writeLock().unlock();
//            lock.readLock().lock();
//            if (clientCollection.size() == 0) {
//                synchronized (firstRegister) {
//                    firstRegister = true;
//                }
//            }
//            lock.readLock().unlock();
//
//        }
//
//        private void handoffFish(Message msg) {
//            InetSocketAddress send = msg.getSender();
//            Serializable payloadmsg = msg.getPayload();
//            Direction direction = ((HandoffRequest) payloadmsg).getFish().getDirection();
//            if (direction == Direction.LEFT) {
//                lock.readLock().lock();
//                endpoint.send((InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(send)), payloadmsg);
//                lock.readLock().unlock();
//            } else {
//                lock.readLock().lock();
//                endpoint.send((InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(send)), payloadmsg);
//                lock.readLock().unlock();
//            }
//        }
//
//
//        private BrokerTask(Message msg) {
//            this.msg = msg;
//        }
//    }

//    public Broker() {
//        super();
//        try {
//            super();
//        }catch (Exception e) {
//            System.exit(1);
//        }
//
//        this.endpoint = new SecureEndpoint(port);
//        this.clientCollection = new ClientCollection();
//        this.registerCounter = 0;
//    }

}
