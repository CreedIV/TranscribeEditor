//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

// AWS transcribe, transcribed object
public class AWSTranscript{	
	String jobName;
	String accountId;
	AWSTranscriptResults results;
	String status;
	
	public AWSTranscript(JSONObject transcriptJSON) {
		jobName = (String) transcriptJSON.get("jobName");
		accountId = (String) transcriptJSON.get("accountId");
		status = (String) transcriptJSON.get("status");

	    JSONObject resultsJSON = (JSONObject) transcriptJSON.get("results");
	    results = new AWSTranscriptResults(resultsJSON);
	}
	
	// factory constructor from filename
	static public AWSTranscript createFromFile(String filename) {
	    JSONParser parser = new JSONParser();
	    
	    JSONObject fileAsJSON = null;
		try {
			fileAsJSON = (JSONObject) parser.parse(new FileReader(filename));
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
		return new AWSTranscript(fileAsJSON);
	}
	
	public AWSTranscript(GoogleTranscript gTranscript) {
		jobName = gTranscript.name;
		accountId = "";
		status = "COMPLETED"; 
		
		results = new AWSTranscriptResults();
		results.speaker_labels = null;
		
		int item_count = 0;
		for(GoogleSpeechResult result : gTranscript.response.results) {
			for(GoogleSpeechWords word : result.alternatives[0].words) {
				item_count++;
			}
		}	
		
		String transcriptText = "";
		results.items = new AWSTranscriptItem[item_count];
		int i = 0;
		for(GoogleSpeechResult result : gTranscript.response.results) {
			for(GoogleSpeechWords word : result.alternatives[0].words) {
				results.items[i] = new AWSTranscriptItem();
				results.items[i].start_time = word.startTime.replaceAll("s", "");
				results.items[i].end_time = word.endTime.replaceAll("s", "");
				results.items[i].alternatives = new AWSTranscriptAlternatives[1];
				results.items[i].alternatives[0] = new AWSTranscriptAlternatives();
				results.items[i].alternatives[0].confidence = word.confidence;
				results.items[i].alternatives[0].content = word.word;
				transcriptText += word.word;
				results.items[i].type = "pronunciation";  // note that google combines puncuation with a word, maybe we need to seperate them for AWS reuslts?? first just try combined.
				i++;
			}
		}
		results.transcripts = new AWSTranscripts[1];
		results.transcripts[0] = new AWSTranscripts();
		results.transcripts[0].transcript = transcriptText;
	}
	
}
