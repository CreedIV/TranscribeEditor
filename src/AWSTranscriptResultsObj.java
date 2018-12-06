//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

// AWS transcribe->results object
public class AWSTranscriptResultsObj{
	AWSTranscripts[] transcripts;
	AWSTranscriptItem[] items;
	
	public AWSTranscriptResultsObj(JSONObject resultsJSON) {
		
	    JSONArray transcriptsJSON = (JSONArray) resultsJSON.get("transcripts");
	    JSONArray itemsJSON = (JSONArray) resultsJSON.get("items");
	    
	    int transcripts_size = transcriptsJSON.size();
	    int items_size = itemsJSON.size();
	    
	    transcripts = new AWSTranscripts[transcripts_size];
	    for(int i = 0 ; i < transcripts_size; i++) {
	    	transcripts[i] = new AWSTranscripts((JSONObject) transcriptsJSON.get(i));
	    }
	    
	   items = new AWSTranscriptItem[items_size];
	   for(int i = 0 ; i < items_size; i++) {
		   items[i] = new AWSTranscriptItem((JSONObject) itemsJSON.get(i));
	   }
	}
}
