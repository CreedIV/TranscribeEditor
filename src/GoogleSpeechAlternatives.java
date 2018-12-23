import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class GoogleSpeechAlternatives {
	String transcript;
	String confidence;
	GoogleSpeechWords words[];
	
	GoogleSpeechAlternatives(JSONObject alternativeJSON){
		transcript = (String) alternativeJSON.get("transcript");
		confidence = (String) alternativeJSON.get("confidence").toString();
		
	    JSONArray wordsJSON = (JSONArray) alternativeJSON.get("words");
	    
	    int words_size = wordsJSON.size();
	    
	    words = new GoogleSpeechWords[words_size];
	    for(int i = 0 ; i < words_size; i++) {
	    	words[i] = new GoogleSpeechWords((JSONObject) wordsJSON.get(i));
	    }
	    
	}
}
