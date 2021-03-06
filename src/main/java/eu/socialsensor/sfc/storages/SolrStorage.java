package eu.socialsensor.sfc.storages;

import java.io.IOException;
import java.util.List;

import org.apache.log4j.Logger;

import eu.socialsensor.framework.Configuration;
import eu.socialsensor.framework.client.search.solr.SolrItemHandler;
import eu.socialsensor.framework.client.search.solr.SolrMediaItemHandler;
import eu.socialsensor.framework.client.search.solr.SolrNewsFeedHandler;
import eu.socialsensor.framework.client.search.solr.SolrWebPageHandler;
import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.WebPage;

/**
 * Class for storing items to solr
 * @author manosetro
 * @email  manosetro@iti.gr
 *
 */
public class SolrStorage implements Storage {

	private Logger  logger = Logger.getLogger(SolrStorage.class);
	
	private static final String HOSTNAME = "solr.hostname";
	private static final String SERVICE = "solr.service";
	
	private static final String ITEMS_COLLECTION = "solr.items.collection";
	private static final String MEDIAITEMS_COLLECTION = "solr.mediaitems.collection";
	private static final String WEBPAGES_COLLECTION = "solr.webpages.collection";
	private static final String NEWSFEED_COLLECTION = "solr.newsfeed.collection";
	
	private static final String FACEBOOK_ITEMS_COLLECTION = "solr.facebook.items.collection";
	private static final String TWITTER_ITEMS_COLLECTION = "solr.twitter.items.collection";
	
	private static final String ONLY_ORIGINAL = "solr.onlyOriginal";
	
	private String hostname, service;
	
	private String itemsCollection = null;
	private String mediaItemsCollection = null;
	private String webPagesCollection = null;
	private String newsFeedCollection = null;
	
	private String facebookItemsCollection = null;
	private String twitterItemsCollection = null;
	
	private String storageName = "Solr";
	
	private SolrItemHandler solrItemHandler = null; 
	private SolrItemHandler solrFacebookItemHandler = null; 
	private SolrItemHandler solrTwitterItemHandler = null; 
	private SolrMediaItemHandler solrMediaHandler = null;
	private SolrWebPageHandler solrWebpageHandler = null;
	private SolrNewsFeedHandler solrNewsFeedHandler = null;
	
	private Boolean onlyOriginal = true;
	
	public SolrStorage(Configuration config) throws IOException {
		this.hostname = config.getParameter(SolrStorage.HOSTNAME);
		this.service = config.getParameter(SolrStorage.SERVICE);
		this.itemsCollection = config.getParameter(SolrStorage.ITEMS_COLLECTION);
		this.mediaItemsCollection = config.getParameter(SolrStorage.MEDIAITEMS_COLLECTION);
		this.webPagesCollection = config.getParameter(SolrStorage.WEBPAGES_COLLECTION);
		this.newsFeedCollection = config.getParameter(SolrStorage.NEWSFEED_COLLECTION);
		this.facebookItemsCollection = config.getParameter(SolrStorage.FACEBOOK_ITEMS_COLLECTION);
		this.twitterItemsCollection = config.getParameter(SolrStorage.TWITTER_ITEMS_COLLECTION);
		
		this.onlyOriginal = Boolean.valueOf(config.getParameter(SolrStorage.ONLY_ORIGINAL, "true"));
	}
	
	public SolrItemHandler getItemHandler() {
		return solrItemHandler;
	}
	
	public SolrItemHandler getFacebookItemHandler() {
		return solrFacebookItemHandler;
	}
	
	public SolrItemHandler getTwitterItemHandler() {
		return solrTwitterItemHandler;
	}
	
	public SolrMediaItemHandler getMediaItemHandler() {
		return solrMediaHandler;
	}
	
	public SolrNewsFeedHandler getNewsFeedHandler() {
		return solrNewsFeedHandler;
	}
	
	@Override
	public boolean open(){
		
		try {
			if(itemsCollection != null) {
				solrItemHandler = SolrItemHandler.getInstance(hostname+"/"+service+"/"+itemsCollection);
			}
			if(mediaItemsCollection != null) {	
				solrMediaHandler = SolrMediaItemHandler.getInstance(hostname+"/"+service+"/"+mediaItemsCollection);
			}
			if(webPagesCollection != null) {	
				solrWebpageHandler = SolrWebPageHandler.getInstance(hostname+"/"+service+"/"+webPagesCollection);
			}
			if(newsFeedCollection != null) {	
				solrNewsFeedHandler = SolrNewsFeedHandler.getInstance(hostname+"/"+service+"/"+newsFeedCollection);
				
			}
			if(facebookItemsCollection != null) {
				solrFacebookItemHandler = SolrItemHandler.getInstance(hostname+"/"+service+"/"+facebookItemsCollection);
				
			}
			if(twitterItemsCollection != null) {
				solrTwitterItemHandler = SolrItemHandler.getInstance(hostname+"/"+service+"/"+twitterItemsCollection);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e);
			return false;
		}
		return true;
		
	}

	@Override
	public void store(Item item) throws IOException {
		
		// Index only original Items and MediaItems come from original Items
		if(!item.isOriginal() && onlyOriginal) {
			return;
		}
		
		//System.out.println(item.toJSONString());
		if(solrItemHandler != null) {
			solrItemHandler.insertItem(item);
		}
		
		if(solrFacebookItemHandler != null && item.getStreamId().equals("Facebook")) {
			solrFacebookItemHandler.insertItem(item);
		}
		
		if(solrTwitterItemHandler != null && item.getStreamId().equals("Twitter")) {
			solrTwitterItemHandler.insertItem(item);
		}
		
		if(solrNewsFeedHandler != null) {
			solrNewsFeedHandler.insertItem(item);
		}
		
		if(solrMediaHandler != null) {
			
			for(MediaItem mediaItem : item.getMediaItems()) {
				if(!solrMediaHandler.isIndexed(mediaItem.getId())) {
					solrMediaHandler.insertMediaItem(mediaItem);
				}
				//else {
				//	solrMediaHandler.insertMediaItem(mi);
				//}
			}
		}
		if(solrWebpageHandler != null) {
			List<WebPage> webPages = item.getWebPages();
			if(webPages != null) {
				solrWebpageHandler.insertWebPages(webPages);
			}
		}
		
	}
	

	@Override
	public void update(Item update) throws IOException {
		store(update);
	}
	
	@Override
	public boolean delete(String itemId) throws IOException {
		//logger.info("Delete item with id " + itemId + " from Solr.");
		solrItemHandler.deleteItem(itemId);
		return true;
	}
	
	@Override
	public boolean deleteItemsOlderThan(long dateThreshold) throws IOException{
		if(itemsCollection != null){
			Item latestItem = solrItemHandler.findLatestItem();
			long latestDateTime = latestItem.getPublicationTime();
			long dateTimeToDeleteFrom = latestDateTime - dateThreshold;
			
			return solrItemHandler.deleteItemsOlderThan(dateTimeToDeleteFrom);
		}
		if(mediaItemsCollection != null){
			
		}
		if(newsFeedCollection != null){
			Item latestItem = solrNewsFeedHandler.findLatestItem();
			long latestDateTime = latestItem.getPublicationTime();
			long dateTimeToDeleteFrom = latestDateTime - Math.abs(dateThreshold);
			return solrNewsFeedHandler.deleteItemsOlderThan(dateTimeToDeleteFrom);
		}
		return true;
	}
	
	@Override
	public boolean checkStatus() {
		
		if(itemsCollection != null){
			try {
				solrItemHandler.checkServerStatus();
				return true;
			} catch (Exception e) {
				logger.error(e);
				return false;
			}
		}
		if(mediaItemsCollection != null){
			try {
				solrMediaHandler.checkServerStatus();
				return true;
			} catch (Exception e) {
				logger.error(e);
				return false;
			}
		}
		if(newsFeedCollection != null){
			try {
				solrNewsFeedHandler.checkServerStatus();
				return true;
			} catch (Exception e) {
				logger.error(e);
				return false;
			}
		}
  
		return false;
	}
	

	@Override
	public void close() {

	}

	@Override
	public void updateTimeslot() {

	}
	
	@Override
	public String getStorageName(){
		return this.storageName;
	}
	
	public static void main(String[] args) throws IOException {
		
	}
	
}
