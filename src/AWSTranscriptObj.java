//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

// AWS transcribe, transcribed object
public class AWSTranscriptObj{	
	String jobName;
	String accountId;
	AWSTranscriptResultsObj results;
	String status;
	
	public AWSTranscriptObj(JSONObject transcriptJSON) {
		jobName = (String) transcriptJSON.get("jobName");
		accountId = (String) transcriptJSON.get("accountId");
		status = (String) transcriptJSON.get("status");

	    JSONObject resultsJSON = (JSONObject) transcriptJSON.get("results");
	    results = new AWSTranscriptResultsObj(resultsJSON);
	}
	
	// factory constructor from filename
	static public AWSTranscriptObj createFromFile(String filename) {
	    JSONParser parser = new JSONParser();
	    
	    JSONObject fileAsJSON = null;
		try {
			fileAsJSON = (JSONObject) parser.parse(new FileReader(filename));
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
		return new AWSTranscriptObj(fileAsJSON);
	}
}
