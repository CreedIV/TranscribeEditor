//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

// Google speech API, transcribed object
public class GoogleTranscript{	
	String name;
	String done;
	GoogleSpeechMetaData metadata;
	GoogleSpeechResponse response;
	
	public GoogleTranscript(JSONObject transcriptJSON) {
		name = (String) transcriptJSON.get("name");
		done = transcriptJSON.get("done").toString();

	    JSONObject responseJSON = (JSONObject) transcriptJSON.get("response");
	    response = new GoogleSpeechResponse(responseJSON);
	    
	    JSONObject metaDataJSON = (JSONObject) transcriptJSON.get("metadata");
	    metadata = new GoogleSpeechMetaData(metaDataJSON);	    
	}
	
	/* dont need to create GOOGLe transcript from aws, need other way, just add missing constuctors if wanted
	public GoogleTranscript(AWSTranscript awsTranscript) {
		name = awsTranscript.jobName;
		done = "true";   // maybe handle this buy using aws.status values???
		metadata = GoogleSpeechMetaData();
		
		response = new GoogleSpeechResponse();
		response.results = new GoogleSpeechResult[1]; // only create one result, with all words
		response.results[0] = new GoogleSpeechResult();
		response.results[0].languageCode = "en-us";
		
		response.results[0].alternatives = new GoogleSpeechAlternatives[1];
		response.results[0].alternatives[0] = GoogleSpeechAlternatives();
		response.results[0].alternatives[0].transcript = awsTranscript.results.transcripts[0].transcript;
		
		AWSTranscriptItem[] awsItems = awsTranscript.results.items;
		response.results[0].alternatives[0].words = new GoogleSpeechWords[awsItems.length];

		int i = 0;
		for(AWSTranscriptItem awsItem : awsItems) {
			response.results[0].alternatives[0].words[i] = new GoogleSpeechWords();
			response.results[0].alternatives[0].words[i].startTime = awsItem.start_time + "s";
			response.results[0].alternatives[0].words[i].endTime = awsItem.end_time + "s";
			response.results[0].alternatives[0].words[i].confidence = awsItem.alternatives[0].confidence;
			response.results[0].alternatives[0].words[i].word = awsItem.alternatives[0].content;
		}
	}
	*/
	
	// factory constructor from filename
	static public GoogleTranscript createFromFile(String filename) {
	    JSONParser parser = new JSONParser();
	    
	    JSONObject fileAsJSON = null;
		try {
			fileAsJSON = (JSONObject) parser.parse(new FileReader(filename));
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
		return new GoogleTranscript(fileAsJSON);
	}
}
