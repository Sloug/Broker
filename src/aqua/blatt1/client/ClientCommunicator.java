package aqua.blatt1.client;

import java.net.InetSocketAddress;

import SecureMessaging.SecureEndpoint;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
    private final SecureEndpoint endpoint;

    public ClientCommunicator() {
        endpoint = new SecureEndpoint();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;

        private ClientForwarder() {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        public void handOff(FishModel fish, NeighborUpdate.Neighbors neighbors) {
            Direction direction = fish.getDirection();
            InetSocketAddress receiver = (direction == Direction.LEFT) ?
                    neighbors.getLeftNeighbor() : neighbors.getRightNeighbor();
            endpoint.send(receiver, new HandoffRequest(fish));
        }

        public void sendToken(NeighborUpdate.Neighbors neighbors) {
            endpoint.send(neighbors.getRightNeighbor(), new Token());
        }

        public void sendMarkers(NeighborUpdate.Neighbors neighbors) {
            endpoint.send(neighbors.getRightNeighbor(), new SnapshotMarker());
            endpoint.send(neighbors.getLeftNeighbor(), new SnapshotMarker());
        }

        public void sendSnapshotCollectionToken(InetSocketAddress reciever, SnapshotCollectionToken snapshot) {
            endpoint.send(reciever, snapshot);
        }

        public void sendLocationRequest(InetSocketAddress reciever, String fishId) {
            endpoint.send(reciever, new LocationRequest(fishId));
        }

        public void sendNameResolutionRequest(String tankId, String fishId) {
            endpoint.send(broker, new NameResolutionRequest(tankId, fishId));
        }

        public void sendLocationUpdate(InetSocketAddress reciever, String fishId) {
            endpoint.send(reciever, new LocationUpdate(fishId));
        }
    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();
                synchronized (tankModel) {
                    if (tankModel.mode != TankModel.Mode.IDLE) {
                        if (tankModel.mode == TankModel.Mode.BOTH)
                            if (tankModel.neighbors.isRightNeighbor(msg.getSender()))
                                tankModel.backup.rightSaveList.add(msg);
                            else
                                tankModel.backup.leftSaveList.add(msg);
                        else if (tankModel.mode == TankModel.Mode.RIGHT && tankModel.neighbors.isRightNeighbor(msg.getSender()))
                            tankModel.backup.rightSaveList.add(msg);
                        else if (tankModel.mode == TankModel.Mode.LEFT && tankModel.neighbors.isLeftNeighbor(msg.getSender()))
                            tankModel.backup.leftSaveList.add(msg);
                    }
                }

                if (msg.getPayload() instanceof RegisterResponse)
                    tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());

                if (msg.getPayload() instanceof HandoffRequest)
                    tankModel.receiveFish(msg.getSender(), ((HandoffRequest) msg.getPayload()).getFish());

                if (msg.getPayload() instanceof NeighborUpdate)
                    tankModel.receiveNeighbors(((NeighborUpdate) msg.getPayload()).getNeighbors());

                if (msg.getPayload() instanceof Token)
                    tankModel.receiveToken();

                if (msg.getPayload() instanceof SnapshotMarker)
                    tankModel.createLocalSnapshot(msg.getSender());

                if (msg.getPayload() instanceof SnapshotCollectionToken)
                    tankModel.receiveSnapshotCollectionToken((SnapshotCollectionToken) msg.getPayload());

                if (msg.getPayload() instanceof LocationRequest)
                    tankModel.locateFishGloballyHomeAgent(((LocationRequest) msg.getPayload()).getFishId());

                if (msg.getPayload() instanceof NameResolutionResponse)
                    tankModel.recieveNameResolutionResponse((NameResolutionResponse) msg.getPayload());

                if (msg.getPayload() instanceof LocationUpdate)
                    tankModel.recieveLocationUpdate(msg);
            }
        }
    }

    public ClientForwarder newClientForwarder() {
        return new ClientForwarder();
    }

    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }

}
