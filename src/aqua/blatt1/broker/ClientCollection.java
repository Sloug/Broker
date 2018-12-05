package aqua.blatt1.broker;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or 
 * externally synchronized. 
 */

public class ClientCollection<T> {
	public class Client {
		public final String id;
		public final T client;
		Timestamp timestamp;

		Client(String id, T client) {
			this.id = id;
			this.client = client;
			this.timestamp = new Timestamp(System.currentTimeMillis());
		}

		private void setTimestamp() {
			this.timestamp = new Timestamp(System.currentTimeMillis());
		}
	}

	private final List<Client> clients;
	private static final int TIME_MILLIS_TO_SECOND = 1000;
	private static final int TIME_SECONDS_TO_MINUTES = 60;

	public List<Client> checkTimestamps(int duration) {
	    System.out.println("Clean");
	    List<Client> outdated = new ArrayList<>();
		clients.stream().forEach(client -> {
			if ((new Timestamp(System.currentTimeMillis())).getTime() - client.timestamp.getTime() > (1 * TIME_MILLIS_TO_SECOND)) {
			    //deregister
                System.out.println("outdated");
                outdated.add(client);
//                clients.remove(clients.indexOf(client.id));
			}
		});
		return outdated;
	}

	public ClientCollection() {
		clients = new ArrayList<Client>();
	}

	public ClientCollection<T> add(String id, T client) {
		clients.add(new Client(id, client));
		return this;
	}

	public ClientCollection<T> setTimer(int index) {
		clients.get(index).timestamp = new Timestamp(System.currentTimeMillis());
		return this;
	}

	public ClientCollection<T> remove(int index) {
		clients.remove(index);
		return this;
	}

	public int indexOf(String id) {
		for (int i = 0; i < clients.size(); i++)
			if (clients.get(i).id.equals(id))
				return i;
		return -1;
	}

	public int indexOf(T client) {
		for (int i = 0; i < clients.size(); i++)
			if (clients.get(i).client.equals(client))
				return i;
		return -1;
	}

	public T getClient(int index) {
		return clients.get(index).client;
	}

	public String getId(int index) {
	    return clients.get(index).id;
    }

	public int size() {
		return clients.size();
	}

	public T getLeftNeighorOf(int index) {
		if (clients.size() == 1) {
			return clients.get(index).client; }
		return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
	}

	public T getRightNeighorOf(int index) {
		if (clients.size() == 1)
			return clients.get(index).client;
		return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
	}

}
