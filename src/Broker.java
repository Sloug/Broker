import aqua.blatt1.broker.ClientCollection;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.common.msgtypes.NeighborUpdate.Neighbors;
import aqua.blatt2.broker.PoisonPill;
import messaging.*;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.JOptionPane;

public class Broker {
    private final int DURATION_CONSTANT = 3;
    private Endpoint endpoint;
    private volatile ClientCollection clientCollection;
    private volatile Integer registerCounter;
    private volatile boolean stopRequested = false;
    private volatile Boolean firstRegister = true;
    private Timer timer = new Timer();
    ReadWriteLock lock = new ReentrantReadWriteLock();

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

    private void checkClientCollection() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                clientCollection.checkTimestamps(3000);
                List<ClientCollection.Client> outdatedCLients = clientCollection.checkTimestamps(3);
                outdatedCLients.stream().forEach(client -> {
                    deRegClient((InetSocketAddress) client.client, client.id);
                });
                checkClientCollection();
            }
        }, 3000);
    }

    private void broker() {

        StopGUIThread stopGUIThread = new StopGUIThread();
        stopGUIThread.start();

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        checkClientCollection();
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


    private Neighbors getNeighborsOfClient(InetSocketAddress client) {
        InetSocketAddress leftNeighborOfSender = (InetSocketAddress) clientCollection.getLeftNeighorOf(clientCollection.indexOf(client));
        InetSocketAddress rightNeighborOfSender = (InetSocketAddress) clientCollection.getRightNeighorOf(clientCollection.indexOf(client));
        return new Neighbors(leftNeighborOfSender, rightNeighborOfSender);
    }

    private void passNeighborsToClient(InetSocketAddress reciever, Neighbors neighbors) {
        NeighborUpdate neighborUpdate = new NeighborUpdate(neighbors.getLeftNeighbor(), neighbors.getRightNeighbor());
        endpoint.send(reciever, neighborUpdate);
    }

    public void deRegClient(InetSocketAddress sender, String id) {
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

    private class BrokerTask implements Runnable {

        private Message msg;

        @Override
        public void run() {
            if (msg.getPayload() instanceof RegisterRequest) {
                register(msg);
            } else if (msg.getPayload() instanceof DeregisterRequest) {
                deregister(msg);
            } else if (msg.getPayload() instanceof HandoffRequest) {
                handoffFish(msg);
            }
        }



        private void register(Message msg) {
            InetSocketAddress sender = msg.getSender();
            Serializable payloadmsg = msg.getPayload();
            int indexClient = clientCollection.indexOf(sender);
            if (indexClient != -1) {
                System.out.println("reregister broker");
                lock.writeLock().lock();
                clientCollection.setTimer(indexClient);
                lock.writeLock().unlock();
                endpoint.send(sender, new RegisterResponse(clientCollection.getId(indexClient), DURATION_CONSTANT));
                return;
            }
            else
                System.out.println("regreg");
            synchronized (registerCounter) {
                registerCounter++;
            }
            lock.writeLock().lock();
            String cliname = ("Sloug_" + registerCounter);
            clientCollection.add(cliname, sender);
            lock.writeLock().unlock();
            endpoint.send(sender, new RegisterResponse(cliname, DURATION_CONSTANT));
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
            deRegClient(sender, id);
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
