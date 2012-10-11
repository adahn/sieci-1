package network;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Set;

public class MessageQueue {
	
	private static class ChannelRecord {
		
		public ChannelRecord(SelectionKey selectionKey, ChannelSelectionHandler handler) {
			this.key = selectionKey;
			this.handler = handler;
		}
		
		public SelectionKey getKey() {
			return key;
		}
		
		public ChannelSelectionHandler getHandler() {
			return handler;
		}
		
		private SelectionKey key;
		private ChannelSelectionHandler handler;
	}
	
	private Selector selector;
	private ArrayList<ChannelRecord> channelRecords = new ArrayList<ChannelRecord>(); 
	
	public MessageQueue() {
		try {
			selector = Selector.open();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Unable to create Selector. Exiting...");
			throw new RuntimeException();
		}
	}

	public SelectionKey registerChannel(SelectableChannel channel, ChannelSelectionHandler selectionHandler, int selectionOptions) throws IOException {
		SelectionKey key;
		try {
			channel.configureBlocking(false);
			key = channel.register(selector, selectionOptions);
		} catch (IOException e) {
			System.err.println("Unable to register channel.");
			throw e;
		}		
		channelRecords.add(new ChannelRecord(key, selectionHandler));
		return key;
	}
	
	public void run() {
		while (true) {
			try {
				selector.select();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			Set<SelectionKey> keys = selector.selectedKeys();
			for (int i = 0; i < channelRecords.size(); ++i) {
				ChannelRecord record = channelRecords.get(i);
				if (keys.contains(record.getKey())) {
					record.getHandler().onSelected(record.getKey().channel(), record.getKey().readyOps());
					keys.remove(record.getKey());
				}
			}
		}
	}
}
