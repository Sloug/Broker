package aqua.blatt1.client;

import aqua.blatt1.broker.AquaBroker;
import aqua.blatt1.common.Properties;

import javax.swing.SwingUtilities;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Aqualife {

	public static void main(String[] args) {
		Registry registry = null;
		AquaBroker broker = null;
		try {
			registry = LocateRegistry.getRegistry();
			broker = (AquaBroker) registry.lookup(Properties.BROKER_NAME);
		} catch (Exception e) {
			System.exit(1);
		}
//		ClientCommunicator communicator = new ClientCommunicator();
		TankModel tankModel = new TankModel(broker);

//		communicator.newClientReceiver(tankModel).start();

		SwingUtilities.invokeLater(new AquaGui(tankModel));

		tankModel.run();
	}
}
