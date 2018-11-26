package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.NeighborUpdate;
import aqua.blatt1.common.msgtypes.SnapshotCollectionToken;
import messaging.Message;


public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected volatile int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;
    protected NeighborUpdate.Neighbors neighbors;
    protected volatile Boolean token = false;
    protected Timer timer = new Timer();
    protected volatile Mode mode = Mode.IDLE;
    protected volatile Save backup;
    protected volatile boolean initiator = false;
    protected volatile int snapshot;
    protected volatile boolean snapshotFlag = false;
    private static final List<FishEntity> fishLocations = new ArrayList<>();

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
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

    synchronized void locateFishGlobally(String fishId) {
        if (!locateFishLocally(fishId)) {
            fishLocations.stream().forEach(fishEntity -> {
                if (fishEntity.fishId.equals(fishId)) {
                    forwarder.sendLocationRequest(fishEntity.fishLocation == FishLocation.RIGHT ?
                            neighbors.getRightNeighbor() : neighbors.getLeftNeighbor(), fishId);

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
        forwarder.sendMarkers(neighbors);
    }

    public void initiateSnapshot() {
        initSnapOp();
        pollIdle();
        synchronized (forwarder) {
            forwarder.sendSnapshotCollectionToken(neighbors.getLeftNeighbor(), new SnapshotCollectionToken(backup.fishCounterBackup));
        }
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


    synchronized void createLocalSnapshot(InetSocketAddress sender) {
        if (mode == Mode.IDLE) {
            backup = new Save(fishCounter);
            if (neighbors.isRightNeighbor(sender)) {
                backup.rightSaveList = Collections.EMPTY_LIST;
                mode = Mode.LEFT;
            } else {
                backup.leftSaveList = Collections.EMPTY_LIST;
                mode = Mode.RIGHT;
            }
            forwarder.sendMarkers(neighbors);
        } else if (mode == Mode.RIGHT && neighbors.isRightNeighbor(sender)) {
            mode = Mode.IDLE;
        } else if (mode == Mode.LEFT && neighbors.isLeftNeighbor(sender)) {
            mode = Mode.IDLE;
        } else {
            if (neighbors.isRightNeighbor(sender)) {
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
        forwarder.sendSnapshotCollectionToken(neighbors.getLeftNeighbor(), globalSnapshot);
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

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(InetSocketAddress sender, FishModel fish) {
        if (mode != Mode.IDLE) {
            if (mode == Mode.BOTH)
                backup.addFish();
            else if (mode == Mode.RIGHT && neighbors.isRightNeighbor(sender))
                backup.addFish();
            else if (mode == Mode.LEFT && neighbors.isLeftNeighbor(sender))
                backup.addFish();
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
                    //update fish
                    if (fish.getDirection() == Direction.LEFT)
                        updateFishLocations(fish, FishLocation.LEFT);
                    else
                        updateFishLocations(fish, FishLocation.RIGHT);
                    forwarder.handOff(fish, neighbors);
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
        forwarder.register();

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
        forwarder.deregister(id);
    }

}