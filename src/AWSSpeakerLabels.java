import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class AWSSpeakerLabels {
	String speakers;
	AWSSpeakerSegment[] segments;
	
	public AWSSpeakerLabels(JSONObject labelsJSON) {
		speakers = (String) labelsJSON.getOrDefault("speakers", null);
		
	    JSONArray segmentsJSON = (JSONArray) labelsJSON.get("segments");
	    
	    int segments_size = segmentsJSON.size();
	    
	   segments = new AWSSpeakerSegment[segments_size];
	   for(int i = 0 ; i < segments_size; i++) {
		   segments[i] = new AWSSpeakerSegment((JSONObject) segmentsJSON.get(i));
	   }
	}

	public AWSSpeakerLabels(Integer numSpeakers) {
		speakers = numSpeakers.toString();
	}

}
