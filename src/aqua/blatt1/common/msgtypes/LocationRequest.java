package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.FishModel;

import java.io.Serializable;

public class LocationRequest implements Serializable {
    private final String fishId;

    public LocationRequest(String fishId) {
        this.fishId = fishId;
    }

    public String getFishId() {
        return fishId;
    }
}
