import aqua.blatt1.broker.ClientCollection;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.common.msgtypes.NeighborUpdate.Neighbors;
import aqua.blatt2.broker.PoisonPill;
import messaging.*;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.JOptionPane;

public class Broker {
    private Endpoint endpoint;
    private volatile ClientCollection clientCollection;
    private volatile Integer registerCounter;
    private volatile boolean stopRequested = false;
    private volatile Boolean firstRegister = true;

    public static void main(String[] args) {
        Broker broker = new Broker(4711);
        broker.broker();
    }

    private class StopGUIThread extends Thread {
        @Override
        public void run() {
            JOptionPane.showMessageDialog(null, "Shutdown Server?");
            stopRequested = true;
        }
    }

    private void broker() {

        StopGUIThread stopGUIThread = new StopGUIThread();
        stopGUIThread.start();

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        while (!stopRequested) {
            Message msg = endpoint.blockingReceive();
            if (msg.getPayload() instanceof PoisonPill) {
                stopGUIThread.interrupt();
                break;
            }
            executorService.execute(new BrokerTask(msg));
        }
        executorService.shutdown();
    }

    private class BrokerTask implements Runnable {
        ReadWriteLock lock = new ReentrantReadWriteLock();
        private Message msg;

        @Override
        public void run() {
            if (msg.getPayload() instanceof RegisterRequest)
                register(msg);

            if (msg.getPayload() instanceof DeregisterRequest)
                deregister(msg);

            if (msg.getPayload() instanceof HandoffRequest)
                handoffFish(msg);

            if (msg.getPayload() instanceof NameResolutionRequest)
                sendNameResolutionResponse(msg);
        }

        private Neighbors getNeighborsOfClient(InetSocketAddress client) {
            InetSocketAddress leftNeighborOfSender = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(client));
            InetSocketAddress rightNeighborOfSender = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(client));
            return new Neighbors(leftNeighborOfSender, rightNeighborOfSender);
        }

        private void passNeighborsToClient(InetSocketAddress reciever, Neighbors neighbors) {
            NeighborUpdate neighborUpdate = new NeighborUpdate(neighbors.getLeftNeighbor(), neighbors.getRightNeighbor());
            endpoint.send(reciever, neighborUpdate);
        }

        private void informNeighbors(InetSocketAddress sender, boolean remove) {
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

        private void sendNameResolutionResponse(Message msg) {
            InetSocketAddress reciever = msg.getSender();
            NameResolutionRequest nameResolutionRequest = (NameResolutionRequest) msg.getPayload();
            endpoint.send(reciever, new NameResolutionResponse((InetSocketAddress) clientCollection
                    .getClient(clientCollection.indexOf(nameResolutionRequest.getTankId())), nameResolutionRequest
                    .getRequestId()));
        }

        private void register(Message msg) {
            InetSocketAddress sender = msg.getSender();
            Serializable payloadmsg = msg.getPayload();
            synchronized (registerCounter) {
                registerCounter++;
            }
            lock.writeLock().lock();
            String cliname = ("Sloug_" + registerCounter);
            clientCollection.add(cliname, sender);
            lock.writeLock().unlock();
            endpoint.send(sender, new RegisterResponse(cliname));
            informNeighbors(sender, false);
            synchronized (firstRegister) {
                if (firstRegister) {
                    endpoint.send(sender, new Token());
                    firstRegister = false;
                }
            }
        }

        private void deregister(Message msg) {
            InetSocketAddress sender = msg.getSender();
            Serializable payloadmsg = msg.getPayload();
            String id = ((DeregisterRequest) payloadmsg).getId();

            informNeighbors(sender, true);

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

        private void handoffFish(Message msg) {
            InetSocketAddress send = msg.getSender();
            Serializable payloadmsg = msg.getPayload();
            Direction direction = ((HandoffRequest) payloadmsg).getFish().getDirection();
            if (direction == Direction.LEFT) {
                lock.readLock().lock();
                endpoint.send((InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(send)), payloadmsg);
                lock.readLock().unlock();
            } else {
                lock.readLock().lock();
                endpoint.send((InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(send)), payloadmsg);
                lock.readLock().unlock();
            }
        }


        private BrokerTask(Message msg) {
            this.msg = msg;
        }
    }

    public Broker(int port) {
        this.endpoint = new Endpoint(port);
        this.clientCollection = new ClientCollection();
        this.registerCounter = 0;
    }

}
