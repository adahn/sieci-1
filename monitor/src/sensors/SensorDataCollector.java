package sensors;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;

import network.ChannelSelectionHandler;
import network.MessageQueue;

public class SensorDataCollector {
	private class NewConnectionHandler implements ChannelSelectionHandler {

		@Override
		public void onSelected(SelectableChannel channel,
				int readyOperationsMask) {
			if ((readyOperationsMask & SelectionKey.OP_ACCEPT) != 0) {
				ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
				try {
					SocketChannel socket = serverChannel.accept();
					System.out.printf("New connection from %s\n", socket
							.getRemoteAddress().toString());

					messageQueue.registerChannel(socket,
							new SensorMessageHandler(socket),
							SelectionKey.OP_READ);

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1); // temporary
				}
			}
		}
	}

	private class SensorMessageHandler implements ChannelSelectionHandler {

		public SensorMessageHandler(SocketChannel socket) {
			this.socket = socket;
		}

		@Override
		public void onSelected(SelectableChannel channel,
				int readyOperationsMask) {
			if ((readyOperationsMask & SelectionKey.OP_READ) != 0
					&& channel == this.socket) {
				SocketChannel socketChannel = (SocketChannel) channel;
				try {
					ByteBuffer buff = ByteBuffer.allocate(1024);
					int readed = socketChannel.read(buff);
					buff.flip();

					if (readed == -1) {
						System.out.printf("Sensor %s has disconnected\n",
								socketChannel.getRemoteAddress().toString());

						close();
					} else {
						CharBuffer cbuff = Charset.defaultCharset()
								.decode(buff);

						updateMeasurement(cbuff.toString());

					}

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1); // temporary
				}
			}
		}

		void close() {
			if (socket != null) {
				try {
					socket.close();
					messageQueue.unregisterChannel(socket);
					if (listeners.containsKey(sensor)) {
						for (SensorUpdateListener listener : listeners
								.get(sensor))
							listener.onDisconnected(sensor);
					}
					socket = null;
				} catch (Exception e) {
					// ignore
				}
			}
		}
		
		@Override
		protected void finalize() throws Throwable{
			try {
				close();
			} finally {
				super.finalize();
			}
		}

		void createSensor(String msg) {
			String[] tokens = msg.split("#");
			sensor = makeSensor(tokens[0], tokens[1]);
			// tymczasowo do testow:
			// new Distributor(messageQueue, sensor, SensorDataCollector.this);
		}

		void updateMeasurement(String msg) {
			if (sensor == null)
				createSensor(msg);
			String[] tokens = msg.split("#");
			sensor.updateMeasurement(Float.parseFloat(tokens[2]));
			if (listeners.containsKey(sensor)) {
				for (SensorUpdateListener listener : listeners.get(sensor))
					listener.onUpdate(sensor);
			}

		}

		private Sensor sensor;
		private SocketChannel socket;

	}

	private static final int PORT = 12087;

	public SensorDataCollector(MessageQueue messageQueue) {
		this.messageQueue = messageQueue;
		ServerSocketChannel serverChannel;
		try {
			serverChannel = ServerSocketChannel.open();
			serverChannel.bind(new InetSocketAddress(SensorDataCollector.PORT));
			messageQueue.registerChannel(serverChannel,
					new NewConnectionHandler(), SelectionKey.OP_ACCEPT);
			System.out.printf("Oczekiwanie na sensory na porcie %d\n",
					SensorDataCollector.PORT);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1); // temporary
		}
	}

	/**
	 * Pobiera sensor odpowiadający podanemu zasobowi i metryce (lub null, jeśli
	 * taki nie istnieje)
	 * 
	 * @param resource
	 * @param metric
	 * @return
	 */
	public Sensor findSensor(String resource, String metric) {
		for (Sensor sensor : sensors) {
			if (sensor.getResource().equals(resource)
					&& sensor.getMetric().equals(metric)) {
				return sensor;
			}
		}
		return null;
	}

	/**
	 * Listuje wszystkie zasoby, dla których istnieje co najmniej jeden
	 * zarejestrowany sensor.
	 * 
	 * @return
	 */
	public ArrayList<String> listResources() {
		// TODO zmienić reprezentację sensorów, żeby to zczytywanie było szybsze
		// (sortowanie?)
		ArrayList<String> resources = new ArrayList<String>();
		for (Sensor sensor : sensors) {
			if (!resources.contains(sensor.getResource())) {
				resources.add(sensor.getResource());
			}
		}

		return resources;
	}
	
	public MessageQueue getMessageQueue(){
		return this.messageQueue;
	}

	/**
	 * Listuje wszystkie metryki, dla podanego zasobu, dla których istnieje co
	 * najmniej jeden zarejestrowany sensor
	 * 
	 * @param resource
	 * @return
	 */
	public ArrayList<String> listMetrics(String resource) {
		ArrayList<String> metrics = new ArrayList<String>();
		for (Sensor sensor : sensors) {
			if (sensor.getResource().equals(resource)
					&& !metrics.contains(sensor.getMetric())) {
				metrics.add(sensor.getMetric());
			}
		}

		return metrics;
	}

	public void addSensorUpdateListener(Sensor sensor,
			SensorUpdateListener listener) {
		if (!listeners.containsKey(sensor)) {
			listeners.put(sensor, new ArrayList<SensorUpdateListener>());
		}
		listeners.get(sensor).add(listener);
	}

	public void removeSensorListener(Sensor sensor,
			SensorUpdateListener listener) {
		ArrayList<SensorUpdateListener> listenersList = listeners.get(sensor);
		if (listenersList != null) {
			listenersList.remove(listener);
		}
	}

	public ArrayList<Sensor> getSensors() {
		return sensors;
	}

	// Tworzenie sensorów tylko przez tę metodę!
	Sensor makeSensor(String resource, String metric) {
		Sensor sensor = new Sensor(resource, metric);
		sensors.add(sensor);
		return sensor;
	}

	private MessageQueue messageQueue;
	private ArrayList<Sensor> sensors = new ArrayList<Sensor>();
	private HashMap<Sensor, ArrayList<SensorUpdateListener>> listeners = new HashMap<Sensor, ArrayList<SensorUpdateListener>>();
}
