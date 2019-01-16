package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.broker.AquaBroker;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import messaging.Message;


public class TankModel extends Observable implements Iterable<FishModel> ,AquaClient{

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected volatile int fishCounter = 0;
    protected final AquaBroker broker;
//    protected final ClientCommunicator.ClientForwarder forwarder;
    protected NeighborUpdate.Neighbors neighbors;
    protected volatile Boolean token = false;
    protected Timer timer = new Timer();
    protected volatile Mode mode = Mode.IDLE;
    protected volatile Save backup;
    protected volatile boolean initiator = false;
    protected volatile int snapshot;
    protected volatile boolean snapshotFlag = false;
    private static final Set<FishEntity> fishLocations = new HashSet<>();
    private static final Set<FishEntity2> homeAgent = new HashSet<>();

    public TankModel(AquaBroker broker) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.broker = broker;
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
    public void handoff(String stub, FishModel fish) {
        if (mode != Mode.IDLE) {
            if (mode == Mode.BOTH)
                backup.addFish();
            else if (mode == Mode.RIGHT && neighbors.isRightNeighbor(stub))
                backup.addFish();
            else if (mode == Mode.LEFT && neighbors.isLeftNeighbor(stub))
                backup.addFish();
        }

        if (fish.getTankId().equals(id))
            updateHomeAgent(fish.getId(), null);
        else {
            broker.nameResolutionRequest(fish.getTankId());
            AquaClient client = getAquaClient(stub);
            client.locationUpdate(fish.getId());
//            forwarder.sendNameResolutionRequest(fish.getTankId(), fish.getId());
        }

        if (!fishLocations.stream().anyMatch(fishLocation -> {
            if (fishLocation.fishId.equals(fish.getId())) {
                fishLocation.fishLocation = FishLocation.HERE;
                return true;
            }
            return false;
        }))
            fishLocations.add(new FishEntity(fish.getId()));
        fish.setToStart();
        fishies.add(fish);
    }

    @Override
    public void updateNeighbor(NeighborUpdate.Neighbors neighbors) {

    }

    @Override
    public void token() {

    }

    @Override
    public void snapshotMarker(String stub) {

    }

    @Override
    public void snapshotCollectionToken(int fishPopulation) {

    }

    @Override
    public void locationRequest(String fishId) {

    }

    @Override
    public void nameResolutionRequest(String stub, String requestId) {

    }

    @Override
    public void locationUpdate(String fishId) {

    }

    private class FishEntity2 {
        final String fishId;
        String location;
//        InetSocketAddress location;

        FishEntity2(String fishId) {
            this.fishId = fishId;
            fishId = null;
        }
    }

    private class FishEntity {
        final String fishId;
        FishLocation fishLocation;

        FishEntity(String fishId) {
            this.fishId = fishId;
            fishLocation = FishLocation.HERE;
        }
    }

    private enum FishLocation {
        HERE, LEFT, RIGHT
    }

    synchronized void locateFishGloballyHomeAgent(String fishId) {
        if (!homeAgent.stream().anyMatch((fishEntity2 -> {
            if (fishEntity2.fishId.equals(fishId)) {
                if (fishEntity2.location == null) {
                    fishies.stream().forEach(fishModel -> {
                        if (fishModel.getId().equals(fishEntity2.fishId))
                            fishModel.toggle();
                    });

                } else {
                    AquaClient client = getAquaClient(fishEntity2.location);
                    client.locationRequest(fishId);
//                    forwarder.sendLocationRequest(fishEntity2.location, fishId);
                }
                return true;
            }
            return false;

        })))
            fishies.stream().forEach(fishModel -> {
                if (fishModel.getId().equals(fishId))
                    fishModel.toggle();
            });
    }

    synchronized void locateFishGlobally(String fishId) {

        if (!locateFishLocally(fishId)) {
            fishLocations.stream().forEach(fishEntity -> {
                if (fishEntity.fishId.equals(fishId)) {
                    AquaClient client = getAquaClient(fishEntity.fishLocation == FishLocation.RIGHT ?
                            neighbors.getRightNeighbor() : neighbors.getLeftNeighbor());
                    client.locationRequest(fishId);
//                    forwarder.sendLocationRequest(fishEntity.fishLocation == FishLocation.RIGHT ?
//                            neighbors.getRightNeighbor() : neighbors.getLeftNeighbor(), fishId);

                    return;
                }
            });
        }
    }

    private synchronized boolean locateFishLocally(String fishId) {
        //find any can lead to failures (one tank handoff)
        Optional<FishModel> tmp = fishies.stream().filter(fishModel -> fishModel.getId().equals(fishId)).findAny();
        if (tmp.isPresent()) {
            tmp.get().toggle();
            return true;
        }
        return false;
    }

    enum Mode {
        IDLE, LEFT, RIGHT, BOTH
    }

    class Save {
        private int fishCounterBackup;
        protected List<Message> rightSaveList;
        protected List<Message> leftSaveList;

        private void addFish() {
            fishCounterBackup++;
        }

        Save(int fishCounter) {
            fishCounterBackup = fishCounter;
            rightSaveList = new ArrayList<>();
            leftSaveList = new ArrayList<>();
        }
    }

    public synchronized void resetSnapshot() {
        initiator = false;
        snapshotFlag = false;
        snapshot = 0;
        backup = null;
    }

    private synchronized void initSnapOp() {
        backup = new Save(fishCounter);
        initiator = true;
        mode = Mode.BOTH;
        AquaClient clientRight = getAquaClient(neighbors.getRightNeighbor());
        AquaClient clientLeft = getAquaClient(neighbors.getLeftNeighbor());
        clientRight.snapshotMarker();
        clientLeft.snapshotMarker();
//        endpoint.send(neighbors.getRightNeighbor(), new SnapshotMarker());
//        endpoint.send(neighbors.getLeftNeighbor(), new SnapshotMarker());
//        forwarder.sendMarkers(neighbors);
    }

    public void initiateSnapshot() {
        initSnapOp();
        pollIdle();
        AquaClient client = getAquaClient(neighbors.getLeftNeighbor());
        client.snapshotCollectionToken(backup.fishCounterBackup);
    }

    private void pollIdle() {
        while (true) {
            synchronized (mode) {
                if (mode == Mode.IDLE) {
                    break;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    synchronized void createLocalSnapshot(String stub) {
        if (mode == Mode.IDLE) {
            backup = new Save(fishCounter);
            if (neighbors.isRightNeighbor(stub)) {
                backup.rightSaveList = Collections.EMPTY_LIST;
                mode = Mode.LEFT;
            } else {
                backup.leftSaveList = Collections.EMPTY_LIST;
                mode = Mode.RIGHT;
            }
            AquaClient clientRight = getAquaClient(neighbors.getRightNeighbor());
            AquaClient clientLeft = getAquaClient(neighbors.getLeftNeighbor());
            clientRight.snapshotMarker();
            clientLeft.snapshotMarker();
//            forwarder.sendMarkers(neighbors);
        } else if (mode == Mode.RIGHT && neighbors.isRightNeighbor(stub)) {
            mode = Mode.IDLE;
        } else if (mode == Mode.LEFT && neighbors.isLeftNeighbor(stub)) {
            mode = Mode.IDLE;
        } else {
            if (neighbors.isRightNeighbor(stub)) {
                mode = Mode.LEFT;
            } else {
                mode = Mode.RIGHT;
            }
        }
    }

    private synchronized boolean ifInitiatior(SnapshotCollectionToken globalSnapshot) {
        if (initiator) {
            snapshot = globalSnapshot.getGlobalFishPopulation();
            snapshotFlag = true;
        }
        return initiator;
    }

    private synchronized void sendSnapToken(SnapshotCollectionToken globalSnapshot) {
        globalSnapshot.addFishesToPopulation(backup.fishCounterBackup);
        AquaClient client = getAquaClient(neighbors.getLeftNeighbor());
        client.snapshotCollectionToken(globalSnapshot);
//        forwarder.sendSnapshotCollectionToken(neighbors.getLeftNeighbor(), globalSnapshot);
        resetSnapshot();
    }

    void receiveSnapshotCollectionToken(SnapshotCollectionToken globalSnapshot) {
        if (!ifInitiatior(globalSnapshot)) {
            pollIdle();
            sendSnapToken(globalSnapshot);
        }
    }

    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;
            String fishId = "fish" + (++fishCounter) + "@" + getId();
            FishModel fish = new FishModel(fishId, x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishLocations.add(new FishEntity(fishId));
            homeAgent.add(new FishEntity2(fishId));

            fishies.add(fish);
        }
    }

    synchronized void recieveNameResolutionResponse(String stub, String fishId) {
        AquaClient client = getAquaClient(stub);
        client.locationUpdate(fishId);
//        forwarder.sendLocationUpdate(nameResolutionResponse.getTankAddress(), nameResolutionResponse.getRequestId());
    }

    synchronized void recieveLocationUpdate(Message msg) {
        LocationUpdate locationUpdate = (LocationUpdate) msg.getPayload();
        updateHomeAgent(locationUpdate.getFishId(), msg.getSender());
        locationUpdate.getFishId();
    }

    private synchronized void updateHomeAgent(String fishId, InetSocketAddress fishLocation) {
        homeAgent.stream().forEach(fishEntity2 -> {
            if (fishEntity2.fishId.equals(fishId)) {
                fishEntity2.location = fishLocation;
                return;
            }
        });
    }

    synchronized void receiveFish(String stub, FishModel fish) {
        if (mode != Mode.IDLE) {
            if (mode == Mode.BOTH)
                backup.addFish();
            else if (mode == Mode.RIGHT && neighbors.isRightNeighbor(stub))
                backup.addFish();
            else if (mode == Mode.LEFT && neighbors.isLeftNeighbor(stub))
                backup.addFish();
        }

        if (fish.getTankId().equals(id))
            updateHomeAgent(fish.getId(), null);
        else {
                broker.nameResolutionRequest(fish.getTankId());
            AquaClient client = getAquaClient(stub);
            client.locationUpdate(fish.getId());
//            forwarder.sendNameResolutionRequest(fish.getTankId(), fish.getId());
        }

        if (!fishLocations.stream().anyMatch(fishLocation -> {
            if (fishLocation.fishId.equals(fish.getId())) {
                fishLocation.fishLocation = FishLocation.HERE;
                return true;
            }
            return false;
        }))
            fishLocations.add(new FishEntity(fish.getId()));
        fish.setToStart();
        fishies.add(fish);
    }

    synchronized void receiveNeighbors(NeighborUpdate.Neighbors neighbors) {
        this.neighbors = neighbors;
    }

    synchronized boolean hasToken() {
        return token;
    }

    synchronized void receiveToken() {
        token = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                token = false;
                forwarder.sendToken(neighbors);
            }
        }, 2000);
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishLocations(FishModel fish, FishLocation fishLocation) {
        fishLocations.stream().forEach(fishEntity -> {
            if (fishEntity.fishId.equals(fish.getId()))
                fishEntity.fishLocation = fishLocation;
        });
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()) {
                if (token) {
                    if (fish.getDirection() == Direction.LEFT)
                        updateFishLocations(fish, FishLocation.LEFT);
                    else
                        updateFishLocations(fish, FishLocation.RIGHT);

                    Direction direction = fish.getDirection();
                    String receiverStub = (direction == Direction.LEFT) ?
                            neighbors.getLeftNeighbor() : neighbors.getRightNeighbor();
                    AquaClient client = getAquaClient(receiverStub);
                    client.handoff(fish);
                    endpoint.send(receiver, new HandoffRequest(fish));
//                    forwarder.handOff(fish, neighbors);
                } else
                    fish.reverse();
            }


            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        String name = "";
        try {
            Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
            AquaClient stub = (AquaClient) UnicastRemoteObject.exportObject(new TankModel(broker), 0);
            registry.rebind(name, stub);
            String a = broker.registerRequest(name);
        } catch (RemoteException e) {
            System.exit(1);
        }

//        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        try {
            broker.deregister(id, id);
        } catch (RemoteException e) {
            System.exit(1);
        }
//        forwarder.deregister(id);
    }

}