package subscription;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import sensors.Sensor;
import sensors.SensorDataCollector;
import sensors.SensorUpdateListener;

import network.ChannelSelectionHandler;
import network.MessageQueue;

/**
 * Klasa zajmująca się dystrybuowaniem pomiarów do zainteresowanych klientów
 * 
 * 
 */
public class Subscription implements ChannelSelectionHandler,
		SensorUpdateListener {
	private static int instancesCount = 0;

	public Subscription(MessageQueue messageQueue, Sensor sensor,
			SensorDataCollector collector) {
		this.sensor = sensor;
		this.id = instancesCount++;
		this.messageQueue = messageQueue;
		this.collector = collector;

		try {
			serverChannel = ServerSocketChannel.open();
			boolean bound = false;
			while (!bound) {
				try {
					// losuj port pomiedzy 1000, a 65000
					int port = 1000 + new Random().nextInt(64000);
					serverChannel.bind(new InetSocketAddress(port));
					bound = true;
					this.port = port;
					System.out.printf("Subskrybcja %s:%s na porcie %d\n",
							sensor.getResource(), sensor.getMetric(), port);
				} catch (IOException e) {
					// port zajety; próbuj jeszcze raz
				}
			}

			messageQueue.registerChannel(serverChannel, this,
					SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1); // temporary
		}

		collector.addSensorUpdateListener(sensor, this);

	}

	public int getPort() {
		return port;
	}

	public Sensor getSensor() {
		return sensor;
	}

	public int getId() {
		return id;
	}

	@Override
	public void onSelected(SelectableChannel channel, int readyOperationsMask) {
		if ((readyOperationsMask & SelectionKey.OP_ACCEPT) != 0) {
			ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
			try {
				SocketChannel socket = serverChannel.accept();
				System.out.printf("New connection from client at %s\n", socket
						.getRemoteAddress().toString());

				clients.add(socket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1); // temporary
			}
		}
	}

	@Override
	public void onUpdate(Sensor sensor) {
		String msg = createMessage(sensor);
		ByteBuffer buff = ByteBuffer.wrap(msg.getBytes());
		Iterator<SocketChannel> it = clients.iterator();
		while (it.hasNext()) {
			try {
				SocketChannel socket = it.next();
				socket.write(buff);
			} catch (IOException e) {
				System.out.println("Client no longer available");
				it.remove();
			}
		}
	}

	@Override
	public void onDisconnected(Sensor sensor) {
		close();
	}

	private String createMessage(Sensor sensor) {
		// format: #zasob#metryka#timestamp#wartosc#
		return String.format("#%s#%s#%f#", sensor.getResource(),
				sensor.getMetric(), sensor.getLastMeasurement());
	}

	public void close() {
		if (serverChannel != null) {
			messageQueue.unregisterChannel(serverChannel);
			serverChannel = null;
			for (SocketChannel client : clients) {
				try {
					client.close();
				} catch (IOException e) {
					// mały problem - można olać :P
				}
			}
			clients.clear();
			collector.removeSensorListener(sensor, this);
		}
	}

	/**
	 * Sprawdza czy skojarzony channel jest prawidlowy. Jezeli nie, to obiekt
	 * nie nadaje się dłużej do użytku
	 * 
	 * @return
	 */
	public boolean isValid() {
		return (serverChannel != null);
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			close();
		} finally {
			super.finalize();
		}
	}

	private ArrayList<SocketChannel> clients = new ArrayList<SocketChannel>();
	private ServerSocketChannel serverChannel;
	private MessageQueue messageQueue;
	private SensorDataCollector collector;
	private Sensor sensor;
	private int port;
	private int id;

}
