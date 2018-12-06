//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import org.json.simple.JSONObject;

public class AWSTranscriptAlternatives{
	String confidence = null;
	String content = null;
	
	AWSTranscriptAlternatives(String confidence, String content){
		this.confidence = confidence;
		this.content = content;
	}

	public AWSTranscriptAlternatives(JSONObject alt) {
		confidence = (String) alt.getOrDefault("confidence", null);
		content = (String) alt.getOrDefault("content", null);	}
}
