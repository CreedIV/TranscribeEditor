import org.json.simple.JSONObject;

public class GoogleSpeechMetaData {
	//String @type = null;
	String progressPercent;
	String startTime;
	String lastUpdateTime;
	
	public GoogleSpeechMetaData(JSONObject metaDataJSON) {
		startTime = (String) metaDataJSON.get("startTime");
		progressPercent = (String) metaDataJSON.get("progressPercent").toString();
		lastUpdateTime = (String) metaDataJSON.get("lastUpdateTime");
	}
	
}
