package eu.socialsensor.sfc.streams.management;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;

import eu.socialsensor.framework.Configuration;
import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.sfc.storages.Consumer;
import eu.socialsensor.sfc.storages.MultipleStorages;
import eu.socialsensor.sfc.storages.Storage;
import eu.socialsensor.sfc.streams.StreamException;
import eu.socialsensor.sfc.streams.StreamsManagerConfiguration;
import eu.socialsensor.sfc.streams.filters.ItemFilter;
import eu.socialsensor.sfc.streams.processors.Processor;

/**
 * @brief  Thread-safe class for managing the storage of items to databases 
 * The storage may be accomplished using multiple consumer-threads.
 * 
 * @author ailiakop
 * @email  ailiakop@iti.gr
 *
 */
public class StorageHandler {
	
	public final Logger logger = Logger.getLogger(StorageHandler.class);
	
	private MultipleStorages store = null;
	
	private BlockingQueue<Item> queue = new LinkedBlockingDeque<Item>();
	
	private StreamsManagerConfiguration config;
	
	private Integer numberOfConsumers = 16;
	private List<Consumer> consumers;
	
	private List<Storage> workingStorages = new ArrayList<Storage>();
	
	private List<ItemFilter> filters = new ArrayList<ItemFilter>();
	private List<Processor> processors = new ArrayList<Processor>();
	
	private StorageStatusThread statusThread;
	
	private Map<String, Boolean> workingStatus = new HashMap<String, Boolean>();
	private int items = 0;
	
	enum StorageHandlerState {
		OPEN, CLOSE
	}
	
	private StorageHandlerState state = StorageHandlerState.CLOSE;
	
	public StorageHandler(StreamsManagerConfiguration config) {
		
		state = StorageHandlerState.OPEN;
		this.config = config;
		
		consumers = new ArrayList<Consumer>(numberOfConsumers);
		
		try {
			createFilters();
			logger.info(filters.size() + " filters initialized!");
			
			createProcessors();
			logger.info(processors.size() + " processors initialized!");
			
			store = initStorage(config);	
		} catch (StreamException e) {
			e.printStackTrace();
			logger.error(e);
		}
		
		this.statusThread = new StorageStatusThread(this);
		
		this.statusThread.start();
		
	}
	
	public Map<String, Boolean> getWorkingDataBases() {
		return workingStatus;
	}
	
	/**
	 * Updates the status of the working storages (working or not)
	 * @param storageId
	 * @param status
	 */
	public void updateDataBasesStatus(String storageId, boolean status) {
		workingStatus.put(storageId, status);
	}
	
	public StorageHandlerState getState() {
		return state;
	}
	
	public MultipleStorages getStorages() {
		return store;
	}
	
	/**
	 * Removes a storage from the working storages list
	 * @param storage
	 */
	public void eliminateStorage(Storage storage) {
		store.remove(storage);
	}
	/**
	 * Adds a storage to the working storages list
	 * @param storage
	 */
	public void restoreStorage(Storage storage) {
		store.register(storage);
	}
	
	/**
	 * Starts the consumer threads responsible for storing
	 * items to the database.
	 */
	public void start() {
		
		for(int i=0; i<numberOfConsumers; i++) {
			Consumer consumer = new Consumer(queue, store, filters, processors);
			consumer.setName("Consumer_" + i);
			consumers.add(consumer);
		}
		
		for(Consumer consumer : consumers) {
			consumer.start();
		}
	}

	public void update(Item item) {
		try {
			items++;
			queue.add(item);
		}
		catch(Exception e) {
			logger.error(e);
		}
	}

	public void updates(Item[] items) {
		for (Item item : items) {
			update(item);
		}
	}
	
	
	public void delete(Item item) {
		queue.add(item);	
	}
	
	/**
	 * Deletes items older than a specific date
	 * @param dateThreshold
	 */
	public void deleteItemsOlderThan(long dateThreshold) {
		try {
			store.deleteItemsOlderThan(dateThreshold);
		} catch (IOException e) {
			logger.error(e);
		}
	}
	
	/**
	 * Initializes the databases that are going to be used in the service
	 * @param config
	 * @return
	 * @throws StreamException
	 */
	private MultipleStorages initStorage(StreamsManagerConfiguration config) throws StreamException {
		MultipleStorages storage = new MultipleStorages();
		
		for (String storageId : config.getStorageIds()) {
			
			Configuration storageConfig = config.getStorageConfig(storageId);
			Storage storageInstance;
			try {
				String storageClass = storageConfig.getParameter(Configuration.CLASS_PATH);
				Constructor<?> constructor = Class.forName(storageClass).getConstructor(Configuration.class);
				storageInstance = (Storage) constructor.newInstance(storageConfig);
				workingStorages.add(storageInstance);
			} catch (Exception e) {
				throw new StreamException("Error during storage initialization", e);
			}
			
			if(storage.open(storageInstance)) {
				workingStatus.put(storageId, true);
				storage.register(storageInstance);
			}
			else {
				workingStatus.put(storageId, false);	
			}
			
		}
		
		return storage;
	}
	
	private void createFilters() throws StreamException {
		for (String filterId : config.getFilterIds()) {
			try {
				Configuration fconfig = config.getFilterConfig(filterId);
				String className = fconfig.getParameter(Configuration.CLASS_PATH);
				Constructor<?> constructor = Class.forName(className).getConstructor(Configuration.class);
				ItemFilter filterInstance = (ItemFilter) constructor.newInstance(fconfig);
			
				filters.add(filterInstance);
			}
			catch(Exception e) {
				e.printStackTrace();
				logger.error("Error during filter " + filterId + "initialization", e);
			}
		}
	}
	
	private void createProcessors() throws StreamException {
		for (String processorId : config.getProcessorsIds()) {
			try {
					
				Configuration pconfig = config.getProcessorConfig(processorId);
				String className = pconfig.getParameter(Configuration.CLASS_PATH);
				Constructor<?> constructor = Class.forName(className).getConstructor(Configuration.class);
				Processor processorInstance = (Processor) constructor.newInstance(pconfig);
			
				processors.add(processorInstance);
			}
			catch(Exception e) {
				e.printStackTrace();
				logger.error("Error during processor " + processorId + " initialization", e);
			}
		}
	}
	
	/**
	 * Stops all consumer threads and all the databases used
	 */
	public void stop() {
		for(Consumer consumer : consumers) {
			consumer.die();
		}
		store.close();
		
		state = StorageHandlerState.CLOSE;
		do {
			statusThread.interrupt();
		}
		while(statusThread.isAlive());
	}
	
	/**
	 * Re-initializes all databases in case of error response
	 * @throws StreamException
	 */
	public void reset() throws StreamException {
		System.out.println("Try to connect to server again - Reinitialization.... ");
		if (this != null) {
			this.stop();
		}
		
		this.store = initStorage(config);
		logger.info("Dumper has started - I can store items again!");
	}
	/**
	 * Thread for monitoring the process of storing 
	 * items to multiple databases. 
	 * @author ailiakop
	 *
	 */
	public class StorageStatusThread extends Thread {
		
		// Runs every one minute by default
		private long sleepTime = 1 * 60000;
		
		private StorageHandler handler;
		
		public StorageStatusThread(StorageHandler handler) {
			this.handler = handler;
			logger.info("Status Check Thread initialized");
			
			this.setName("StorageStatusThread");
		}
		
		public void run() {
			
			int p = items;
			long T0 = System.currentTimeMillis();
			long T = System.currentTimeMillis();
			
			while(handler.getState().equals(StorageHandlerState.OPEN)) {
				
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					if(handler.getState().equals(StorageHandlerState.CLOSE)) {
						logger.info("StorageStatusAgent interrupted from sleep to stop.");
						break;
					}
					else {
						logger.error("Exception in StorageStatusAgent. ", e);
					}
				}
				
				logger.info("Mean Storing Time: " + ((double)store.totalTime / (double)store.totalItems) + " msec / item");
				
				for(Storage storage : workingStorages) {
					String storageId = storage.getStorageName();
					Boolean status = storage.checkStatus();
					
					if(!status && handler.getWorkingDataBases().get(storageId)) {     
						//was working and now is not responding
						logger.info(storageId + " was working and now is not responding");
						handler.updateDataBasesStatus(storageId, status);
						handler.eliminateStorage(storage);
					}
					else if(status && !handler.getWorkingDataBases().get(storageId)) {
						//was not working and now is working
						logger.info(storageId + " was not working and now is working");
						handler.updateDataBasesStatus(storageId, status);
						handler.restoreStorage(storage);
					}
				}
				
				// Print handle rates
				long T1 = System.currentTimeMillis();
				logger.info("Queue size: " + queue.size());
				logger.info("Handle rate: " + (items-p)/((T1-T)/60000) + " items/min");
				logger.info("Mean handle rate: " + (items)/((T1-T0)/60000) + " items/min");
				
				for(ItemFilter filter : filters) {
					logger.info(filter.name() + ": " + filter.status());
				}
				
				T = System.currentTimeMillis();
				p = items;
				
				// This should never happen
				if(queue.size() > 500) {
					synchronized(queue) {
						logger.error("Queue size " + queue.size() + " > 500. Clear queue to prevent heap overflow.");
						queue.clear();
					}
				}
			}
		
		}	
	}
}