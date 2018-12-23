import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class GoogleSpeechResponse {
	//String @type
	GoogleSpeechResult results[];
	
	public GoogleSpeechResponse(JSONObject responseJSON) {
		
	    JSONArray resultsJSON = (JSONArray) responseJSON.get("results");
	    
	    int results_size = resultsJSON.size();
	    
	    results = new GoogleSpeechResult[results_size];
	    for(int i = 0 ; i < results_size; i++) {
	    	results[i] = new GoogleSpeechResult((JSONObject) resultsJSON.get(i));
	    }
	    
	}
}
