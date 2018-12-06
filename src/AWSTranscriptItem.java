//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

class AWSTranscriptItem{
	String start_time = null;
	String end_time = null;
	AWSTranscriptAlternatives[] alternatives;
	String type = null;
	
	AWSTranscriptItem(JSONObject itemJSON){
		if(itemJSON == null)
			System.out.println("!!!");
		start_time = (String) itemJSON.getOrDefault("start_time", null);
		end_time = (String) itemJSON.getOrDefault("end_time", null);
		
		JSONArray alts = (JSONArray) itemJSON.get("alternatives");
		JSONObject alt = (JSONObject) alts.get(0);	
		alternatives = new AWSTranscriptAlternatives[1];
		alternatives[0] = new AWSTranscriptAlternatives(alt);
	}
	
	AWSTranscriptItem(String content, String confidence, String start_time, String end_time){
		this.start_time = start_time;
		this.end_time = end_time;
		alternatives = new AWSTranscriptAlternatives[1];
		alternatives[0] = new AWSTranscriptAlternatives(confidence, content);
	}
	
	
	AWSTranscriptItem(){ // this is used as an intermediary to create a new vbox element. since I dont have my own vbox class, this comes in handy
		start_time = "";
		end_time = "";
		String confidence = "1";
		String content = "";
		alternatives = new AWSTranscriptAlternatives[1];
		alternatives[0] = new AWSTranscriptAlternatives(confidence, content);
	}
}