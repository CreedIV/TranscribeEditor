import org.json.simple.JSONObject;

public class GoogleSpeechWords {
	String startTime;
	String endTime;
	String word;
	String confidence;
	
	GoogleSpeechWords(JSONObject wordJSON){
		startTime = (String) wordJSON.get("startTime");
		endTime = (String) wordJSON.get("endTime");
		word = (String) wordJSON.get("word");
		confidence = (String) wordJSON.get("confidence").toString();
	}
}
