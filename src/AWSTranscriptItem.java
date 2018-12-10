//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

class AWSTranscriptItem{
	transient String speaker_label = null;  // exclude this from serialzation when saving json...
	String start_time = null;
	String end_time = null;
	AWSTranscriptAlternatives[] alternatives;
	String type = null;
	
	AWSTranscriptItem(JSONObject itemJSON){
		start_time = (String) itemJSON.getOrDefault("start_time", null);
		end_time = (String) itemJSON.getOrDefault("end_time", null);
		type = (String) itemJSON.getOrDefault("type", null);
		
		JSONArray alts = (JSONArray) itemJSON.get("alternatives");
		JSONObject alt = (JSONObject) alts.get(0);	
		alternatives = new AWSTranscriptAlternatives[1];
		alternatives[0] = new AWSTranscriptAlternatives(alt);
	}
	
	AWSTranscriptItem(String content, String speaker_label, String confidence, String start_time, String end_time, String type){
		this.start_time = start_time;
		this.end_time = end_time;
		this.speaker_label = speaker_label;
		this.type = type;
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