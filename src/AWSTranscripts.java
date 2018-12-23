//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import org.json.simple.JSONObject;

// AWS transcribe->results->transcript object
public class AWSTranscripts{
	String transcript;
	
	public AWSTranscripts(JSONObject transcriptJSON) {
		transcript = (String)transcriptJSON.get("transcript");
	}
	
	public AWSTranscripts() {
	}
}
