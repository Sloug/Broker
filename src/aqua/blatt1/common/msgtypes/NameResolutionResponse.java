package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public class NameResolutionResponse implements Serializable {
    private final InetSocketAddress tankAddress;
    private final String requestId;

    public NameResolutionResponse(InetSocketAddress tankAddress, String requestId) {
        this.tankAddress = tankAddress;
        this.requestId = requestId;
    }

    public InetSocketAddress getTankAddress() {
        return tankAddress;
    }

    public String getRequestId() {
        return requestId;
    }
}
