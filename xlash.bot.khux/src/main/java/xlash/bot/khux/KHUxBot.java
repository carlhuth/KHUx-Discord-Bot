package xlash.bot.khux;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.util.concurrent.FutureCallback;

import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import de.btobastian.javacord.listener.server.ServerJoinListener;

public class KHUxBot {
	
	public static final String VERSION = "1.1.0";
	
	public String lastTwitterUpdate;
	public String updateChannel;
	public DiscordAPI api;
	public boolean shouldUpdate;
	public volatile Channel luxOn;

	public static void main(String[] args){
		findUpdate();
		String token = "";
		String updateChannel = "";
		if(args.length > 0){
			token = args[0];
			if(args.length > 1){
				updateChannel = args[1];
			}
			new KHUxBot(token, updateChannel);
			return;
		}
		System.err.println("You must define a token for the bot.");
	}
	
	public HashMap<String, String> nicknames = new HashMap<String, String>();
	
	public HashMap<String, String> medalNamesAndLink;
	
	public KHUxBot(final String token, String updateChannel){
		this.updateChannel = updateChannel;
		this.initialize();
		api = Javacord.getApi(token, true);
		api.setAutoReconnect(false);
        connect(api);
        System.out.println("Waiting for server response...");
        while(api.getServers().size()==0){}
        System.out.println("Server connected! Let's go!");
        this.shouldUpdate = api.getChannelById(updateChannel)!=null;
        if(shouldUpdate){
        	Thread grabTwitterUpdate = new Thread("Grab Twitter Update"){
        		@Override
        		public void run(){
        			while(true){
        				try {
							Thread.sleep(400);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
        				if(Integer.parseInt(getGMTTime("mm"))%2==0 && getGMTTime("ss").equals("05")){
        					getTwitterUpdate(api);
        				}
        			}
        		}
        	};
        	grabTwitterUpdate.start();
        }
        Thread botUpdate = new Thread("Bot Update"){
        	@Override
        	public void run(){
        		while(true){
        			try {
						Thread.sleep(1800000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
        			findUpdate();
        		}
        	}
        };
        botUpdate.start();
        Thread luxTimes = new Thread("Lux Times"){
        	@Override
        	public void run(){
        		while(true){
        			if(luxOn!=null){
        				try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
        				if(getGMTTime("hh:mm:ss").equals("09:00:00")||getGMTTime("hh:mm:ss").equals("03:00:00")){
        					if(luxOn!=null) luxOn.sendMessage("Double lux active!");
        				}else if(getGMTTime("hh:mm:ss").equals("10:00:00")||getGMTTime("hh:mm:ss").equals("04:00:00")){
        					if(luxOn!=null) luxOn.sendMessage("Double lux has faded...");
        				}
        			}
        		}
        	}
        };
        luxTimes.start();
	}
	
	public void initialize(){
		System.out.println("Initializing...");
		this.nicknames = new HashMap<String, String>();
		this.medalNamesAndLink = new HashMap<String, String>();
		getMedalList();
		System.out.println("Got medal list");
		createNicknames();
		lastTwitterUpdate = getTwitterUpdateLink(0);
		System.out.println("Created nicknames");
		System.out.println("Initialization finished!");
	}
	
	public void getTwitterUpdate(DiscordAPI api){
		ArrayList<String> links = new ArrayList<String>();
		String current = getTwitterUpdateLink(0);
		int i=0;
		while(!lastTwitterUpdate.equals(current)){
			current = getTwitterUpdateLink(i);
			links.add(0, current);
			i++;
		}
		for(String link : links){
			if(link!=null) api.getChannelById(updateChannel).sendMessage(link);
		}
		if(links.size()>0) lastTwitterUpdate = links.get(links.size()-1);
	}
	
	public String getTwitterUpdateLink(int recent){
		try {
			Document doc = Jsoup.connect("https://twitter.com/kh_ux_na").get();
			String shortUrl = doc.getElementsByClass("js-tweet-text-container").get(recent).getElementsByTag("a").get(1).attr("href");
			Document doc2 = Jsoup.connect(shortUrl).get();
			return doc2.getElementsByTag("title").text();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Failed to get update from Twitter, but that won't stop me!");
		}
		return null;
	}
	
	public String getRealNameByNickname(String name){
		for(String test : this.medalNamesAndLink.keySet()){
			if(test.equalsIgnoreCase(name)) return test;
		}
		for(String test : this.nicknames.keySet()){
			if(test.equalsIgnoreCase(name.replace(" ", ""))) return nicknames.get(test);
		}
		return null;
	}
	
	public void getMedalInfo(String realName, Message message){
		String website = this.medalNamesAndLink.get(realName);
		
		if(website == null) website = "http://www.khunchainedx.com/wiki/Donald_A";
		Document doc;
		try {
			URL url = new URL(website);
			doc = Jsoup.parse(url.openStream(), null, "");
			//TODO Make compatible with non-6* versions
			Elements medalMaxInfo = doc.getElementById("mw-content-text").getElementsByTag("div").get(1).getElementsByAttributeValueStarting("title", "6").get(0).getElementsByTag("td");
			Elements attributes = new Elements();
			for(int i=0;i<medalMaxInfo.size();i++){
				if(!medalMaxInfo.get(i).hasAttr("colspan")) attributes.add(medalMaxInfo.get(i));
				if(i>1&&medalMaxInfo.get(i).getElementsByAttributeValueMatching("colspan", "4").size()>0) attributes.add(medalMaxInfo.get(i));
			}
			
			String medalAttribute = attributes.get(9).text();
			String medalStrength = attributes.get(10).text();
			String medalDefense = attributes.get(11).text();
			String medalAbility = attributes.get(13).text();
			String medalTarget = attributes.get(18).text();
			String medalTier = attributes.get(19).text();
			String medalMultiplier = attributes.get(20).text();
			String medalGuages = attributes.get(21).text();
			
			String reply = "======== \n"+
					realName+": \n"+
					"Attribute: " + medalAttribute+" \n"+
					"Ability: " + medalAbility+" \n"+
					"Str/Def + " + medalStrength + "/" + medalDefense+" \n"+
					"Target: " + medalTarget+" \n"+
					"Tier " + medalTier+" \n"+
					"Multiplier: " + medalMultiplier+" \n"+
					"Cost: " + medalGuages + " SP \n"+
					"========";
			message.reply(reply);
		} catch (Exception e) {
			e.printStackTrace();
			message.reply("Oh dear... something went wrong...");
		}
	}
	
	public void getMedalList(){
		try {
			Document doc = Jsoup.connect("http://www.khunchainedx.com/wiki/Medal").get();
			Elements medalList = doc.getElementById("mw-content-text").getElementsByClass("collapsible collapsed").get(0).getElementsByTag("tr");
			for(int i=1;i<medalList.size();i++){
				Elements medalLinks = medalList.get(i).getElementsByTag("a");
				for(Element link : medalLinks){
					this.medalNamesAndLink.put(link.attr("title"), link.absUrl("href").replaceAll("%26", "&"));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void createNicknames(){
		for(String name : this.medalNamesAndLink.keySet()){
			String original = name.substring(0, name.length());
			name = name.replace("KH II ", "KH2");
			name = name.replaceAll("The ", "");
			name = name.replace("WORLD OF FF", "WOFF");
			name = name.replace("Timeless River", "TR");
			name = name.replace("Halloween", "HT");
			name = name.replace("Atlantica", "AT");
			name = name.replace("Key Art ", "KA");
			name = name.replace("(Medal)", "");
			if(name.contains("Illustrated")){
				name = name.replace("Illustrated", "i");
				if(name.split(" ").length>1){
					String[] words = name.split(" ");
					if(name.contains("&")){
						String product = "";
						boolean skip = name.split("&").length > 2;
						for(String word : words){
							if(skip && word.equals("&")) continue;
							product += word.substring(0, 1);
						}
						name = product;
					}
				}
			}else if(name.contains("&")){
				String[] words = name.split(" ");
				String product = "";
				boolean skip = name.split("&").length > 2;
				for(String word : words){
					if(skip && word.equals("&")) continue;
					product += word.substring(0, 1);
				}
				name = product;
			}
			
			name = name.replace(" ", "");
			nicknames.put(name, original);
		}
		nicknames.put("Tieri", "Illustrated KH II Kairi");
		nicknames.put("Pooglet", "Pooh & Piglet");
		nicknames.put("BronzeDonald", "Donald A");
	}
	
	public void connect(DiscordAPI api){
		api.connect(new FutureCallback<DiscordAPI>() {
            public void onSuccess(DiscordAPI api) {
                api.registerListener(new MessageCreateListener() {
                    public void onMessageCreate(DiscordAPI api, Message message) {
                        if (message.getContent().startsWith("!medal ")) {
                            String medal = message.getContent().substring(7);
                            System.out.println("Medal in question: " + medal);
                            String realName = getRealNameByNickname(medal);
                            System.out.println("Interpreted as: " + realName);
                            if(realName != null){
                            	getMedalInfo(realName, message);
                            }else{
                            	message.reply("I don't know what medal that is.");
                            }
                        }else if(message.getContent().equalsIgnoreCase("!tweet")){
                        	message.reply(getTwitterUpdateLink(0));
                        }else if(message.getContent().startsWith("!lux")){
                        	String param = message.getContent().substring(5);
                        	if(param.equalsIgnoreCase("on")){
                        		message.reply("Double lux reminders have been turned on.");
                        		luxOn = message.getChannelReceiver();
                        	}else{
                        		message.reply("Double lux reminders have been turned off.");
                        		luxOn = null;
                        	}
                        }
                    }
                });
            }

            public void onFailure(Throwable t) {
                t.printStackTrace();
            }
        });
	}
	
	public static void findUpdate(){
		try {
			Document doc = Jsoup.connect("https://github.com/xlash123/KHUx-Discord-Bot/releases").get();
			String newVersion = doc.getElementsByClass("css-truncate-target").get(0).text();
			if(!VERSION.equals(newVersion)){
				System.out.println("New update avaialbe. Download at - https://github.com/xlash123/KHUx-Discord-Bot/releases");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String getGMTTime(String format){
		final Date currentTime = new Date();

		final SimpleDateFormat sdf =
		        new SimpleDateFormat(format);

		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf.format(currentTime);
	}
	
	public static String getGMTTime(){
		return getGMTTime("HH:mm:ss");
	}
	
}
