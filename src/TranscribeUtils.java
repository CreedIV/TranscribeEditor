//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;

import javafx.stage.FileChooser;

public class TranscribeUtils {
	
	static void saveJsonFile(AWSTranscriptObj transcriptObj) {		        
        // write new json file
		Gson gson = new Gson();
        String filename = saveJSONfile();
        try (FileWriter file = new FileWriter(filename)) {
        	file.write(gson.toJson(transcriptObj));
        	file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	static public String getMp3File() {    
		FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3", "*.mp3"));
        return getFile(fileChooser);
	}
	
	static public String getJSONFile() {
	    FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return getFile(fileChooser);
	}
	
	private static String getFile(FileChooser fileChooser) {
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            return file.getAbsolutePath();
        }
        return null;
	}	
	
	static public String saveJSONfile() {
	    FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        return saveFile(fileChooser);
	}
	
	private static String saveFile(FileChooser fileChooser) {
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            return file.getAbsolutePath();
        }
        return null;
	}
}
