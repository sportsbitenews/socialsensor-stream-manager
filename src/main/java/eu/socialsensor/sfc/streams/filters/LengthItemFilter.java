package eu.socialsensor.sfc.streams.filters;

import java.net.URL;

import org.apache.log4j.Logger;

import eu.socialsensor.framework.Configuration;
import eu.socialsensor.framework.common.domain.Item;

public class LengthItemFilter extends ItemFilter {

	private int minTextLenth = 10;

	public LengthItemFilter(Configuration configuration) {
		super(configuration);
		String lenStr =configuration.getParameter("length", "15");
		this.minTextLenth  = Integer.parseInt(lenStr);
		
		Logger.getLogger(LengthItemFilter.class).info("Initialized. Min Text Lenth: " + minTextLenth);
	}
	
	@Override
	public boolean accept(Item item) {
		if(item == null) {
			incrementDiscarded();
			return false;
		}
		
		String title = item.getTitle();
		if(title == null) {
			incrementDiscarded();
			return false;
		}
		
		try {
			String[] tags = item.getTags();
			if(tags != null) {
				for(String tag : tags) {
					title = title.replaceAll(tag, " ");
				}
			}
		
			String[] mentions = item.getMentions();
			if(mentions != null) {
				for(String mention : mentions) {
					title = title.replaceAll(mention, " ");
				}
			}
		
			URL[] links = item.getLinks();
			if(links != null) {
				for(URL link : links) {
					title = title.replaceAll(link.toString(), " ");
				}
			}
		
			title = title.replaceAll("#", " ");
			title = title.replaceAll("@", " ");
			title = title.replaceAll("\\s+", " ");
		}
		catch(Exception e) {
			
		}
		
		if(title.length() < minTextLenth) {
			incrementDiscarded();
			return false;
		}
		
		incrementAccepted();
		return true;
	}

	@Override
	public String name() {
		return "LengthItemFilter";
	}
}
