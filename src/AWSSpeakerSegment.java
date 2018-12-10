
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class AWSSpeakerSegment {
	String start_time;
	String speaker_label;
	String end_time;
	AWSSpeakerItem[] items; // if this serialized ok then I should not use raw arrays in these AWS classes....
	
	public AWSSpeakerSegment(JSONObject segmentJSON) {
		start_time = (String) segmentJSON.getOrDefault("start_time", null);
		end_time = (String) segmentJSON.getOrDefault("end_time", null);
		speaker_label = (String) segmentJSON.getOrDefault("speaker_label", null);
		
	    JSONArray itemsJSON = (JSONArray) segmentJSON.get("items");
	    
	    int items_size = itemsJSON.size();
	    
	   items = new AWSSpeakerItem[items_size];
	   for(int i = 0 ; i < items_size; i++) {
		   items[i] = new AWSSpeakerItem((JSONObject) itemsJSON.get(i));
	   }
	}

	public AWSSpeakerSegment(String speaker_label2, String start_time2, String end_time2) {
		this.start_time = start_time2;
		this.end_time = end_time2;
		this.speaker_label = speaker_label2;
	}
}
