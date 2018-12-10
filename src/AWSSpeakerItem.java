import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class AWSSpeakerItem {
	String start_time;
	String speaker_label;
	String end_time;
	
	AWSSpeakerItem(JSONObject itemJSON){
		start_time = (String) itemJSON.getOrDefault("start_time", null);
		end_time = (String) itemJSON.getOrDefault("end_time", null);
		speaker_label = (String) itemJSON.getOrDefault("speaker_label", null);
	}

	public AWSSpeakerItem(String start_time2, String end_time2, String speaker_label2) {
		start_time = start_time2;
		end_time = end_time2;
		speaker_label = speaker_label2;
	}
}
