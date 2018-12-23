import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class GoogleSpeechResult {
	String languageCode;
	GoogleSpeechAlternatives alternatives[];
	
	GoogleSpeechResult(JSONObject resultJSON){
		languageCode = (String) resultJSON.get("languageCode");
		
	    JSONArray alternativesJSON = (JSONArray) resultJSON.get("alternatives");
	    
	    int alts_size = alternativesJSON.size();
	    
	    alternatives = new GoogleSpeechAlternatives[alts_size];
	    for(int i = 0 ; i < alts_size; i++) {
	    	alternatives[i] = new GoogleSpeechAlternatives((JSONObject) alternativesJSON.get(i));
	    }
	    
	}
}
