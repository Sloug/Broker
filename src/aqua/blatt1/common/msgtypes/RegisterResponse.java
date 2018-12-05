package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
	private final String id;
	private final int duration;

	public RegisterResponse(String id, int duration) {
		this.id = id;
		this.duration = duration;
	}

	public String getId() {
		return id;
	}

	public int getDuration() {
		return duration;
	}
}
