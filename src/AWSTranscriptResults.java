//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

// AWS transcribe->results object
public class AWSTranscriptResults{
	AWSTranscripts[] transcripts;
	AWSTranscriptItem[] items;
	AWSSpeakerLabels speaker_labels;
	
	public AWSTranscriptResults(JSONObject resultsJSON) {
		
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
	   	   
	   // go through speaker_labels if it exists and
	   // go through the items inside the segments assigning items speaker label to transcriptItems in order, skipping ones which aren't pronunciations because these dont have labels....
	   JSONObject speaker_labelsJSON = (JSONObject) resultsJSON.getOrDefault("speaker_labels", null);
	   if(speaker_labelsJSON != null) {
		   
		   JSONArray segmentsJSON = (JSONArray) speaker_labelsJSON.get("segments");
		   int segment_size = segmentsJSON.size();
		   
		   int result_idx = 0;
		   for(int i = 0; i < segment_size; i++) {
			   
			   JSONObject segmentJSON = (JSONObject) segmentsJSON.get(i);
			   JSONArray segitemsJSON = (JSONArray) segmentJSON.get("items");
			   
			   int segitems_size = segitemsJSON.size();
			   for(int j = 0; j < segitems_size; j++) {
				   // get segitems speaker_label and assign to next result Item which is a pronunciation
				   while(!items[result_idx].type.equals("pronunciation"))
					   result_idx++;
				   JSONObject segitemJSON = (JSONObject) segitemsJSON.get(j);
				   items[result_idx].speaker_label = (String) segitemJSON.get("speaker_label");
				   result_idx++;
			   }
		   }
	   }
	}
	
	public AWSTranscriptResults() {
	}
}
