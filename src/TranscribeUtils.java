//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioFileFormat.Type;

import com.google.gson.Gson;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class TranscribeUtils {
	
	static File tempWavFile = null;
	
	static AudioInputStream createClip(String audioFilename, String start_timeStr, String end_timeStr) {
		try {
			if(audioFilename.endsWith(".mp3") && tempWavFile == null) {
				tempWavFile = makeTempWavFile(audioFilename);
			}	
			// convert times given by  user to millsec
			Double start_timeDbl = Double.parseDouble(start_timeStr);
			Double end_timeDbl = Double.parseDouble(end_timeStr);
			Long start_timeL = Math.round(start_timeDbl*1_000_000);
			Long end_timeL = Math.round(end_timeDbl*1_000_000);
		
	    	File file = (tempWavFile == null) ? new File(audioFilename) : tempWavFile;	
	    	AudioInputStream sound = AudioSystem.getAudioInputStream(file);
	    	AudioFormat format = sound.getFormat();
			Clip clip = AudioSystem.getClip();
			clip.open(sound);
	
			// get the frames of the desired start and end times
			clip.setMicrosecondPosition(start_timeL);
			int start_frame = clip.getFramePosition();
			clip.setMicrosecondPosition(end_timeL);
			int end_frame = clip.getFramePosition();
	
			// get size of desired portion in bytes
			int bytesPerFrame = format.getFrameSize();
			//int sampleByteSize = bytesPerFrame*(end_frame-start_frame); // i'm not sure why this is not right.... I swore it use to work
			int sampleByteSize = (end_frame-start_frame);
	
			// open file stream to desired portion
			// we can make this work so that the users can play mp3, we convert internal to wav and save wav clips...  currently lets just assume wav files only
			FileInputStream fileStream = new FileInputStream(file); 
			fileStream.skip(bytesPerFrame*start_frame);
			
			// create audio stream of desired portion
	        return new AudioInputStream(fileStream, format, sampleByteSize);
        
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	

	static File makeTempWavFile(String audioFilename) {
		try {
			File file = new File(audioFilename);
		    AudioInputStream in= AudioSystem.getAudioInputStream(file);
		    AudioFormat baseFormat = in.getFormat();
		    AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
		                                                                                  baseFormat.getSampleRate(),
		                                                                                  16,
		                                                                                  baseFormat.getChannels(),
		                                                                                  baseFormat.getChannels() * 2,
		                                                                                  baseFormat.getSampleRate(),
		                                                                                  false);
		    AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);
	    	File outfile = File.createTempFile("temp", ".wav"); 	    	   
		    outfile.deleteOnExit();
	    	AudioSystem.write(din, Type.WAVE, outfile);
	    	return outfile;
	    	
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static public void updateTranscriptObj(ArrayList<VBox> vBoxedItems, AWSTranscript awsTranscription) {
		//update AwsTranscript from vBoxedItems, completely replace all content of AWSTranscriptResults by
		//building AWSTranscriptItem[] and speaker_labels from vboxedItems in GUI and create new transcript in process
		
		// not that we create the minimum number of reasonable segments, there seems to be little rhyme or reason to where AWS separates segments.
		// the only consistency is that segments have only one speaker. so we create segments at speaker change boundries.
		
		AWSTranscriptItem[] newItems = new AWSTranscriptItem[vBoxedItems.size()];
		
		String newTranscript = "";
		Set<String> speakers = new HashSet<String>();
		ArrayList<Integer> segItemCounts = new ArrayList<Integer>();
		int segItemCount = 1;
		int numSegments = 0;
		String currSpeaker = null;
		Pattern punctuation_pattern = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}]");

		for(int i = 0; i < vBoxedItems.size(); i++) {
	        
			ObservableList<Node> vboxChildren = FXCollections.observableArrayList(vBoxedItems.get(i).getChildren());
			String content = ((TextField) vboxChildren.get(0)).getText();
			String speaker_label = ((TextField) vboxChildren.get(1)).getText();
			String confidence = ((TextField) vboxChildren.get(2)).getText();
			String start_time = ((TextField) vboxChildren.get(3)).getText();
			String end_time = ((TextField) vboxChildren.get(4)).getText();
			
			// track speaker changes recreate speakerLabel segments, what a mess I made :(
			if(currSpeaker == null) {
				if(speaker_label != null) // first time we see a speaker, record that speaker_label.
					currSpeaker = speaker_label;				
			}else if(speaker_label == null) { // no speaker label, no speaker change, no new segment... or segment item..
				;
			}else if(!speaker_label.equals("") && !speaker_label.equals(currSpeaker)){ // new speaker => new segment
			    segItemCounts.add(numSegments++, segItemCount); // record end of last segment by storing its item count
				currSpeaker = speaker_label;
			    speakers.add(currSpeaker); // add any potential new speakers, to speaker set to record # speakers
				segItemCount = 1;
			}else { // if its not a new segment and an item which has a speaker_label, increment the segments item count...
				segItemCount++;
			}
			
			Boolean isPunctuation = punctuation_pattern.matcher(content).matches();
			String type = (isPunctuation) ? "punctuation" : "pronunciation";
			
			newItems[i] = new AWSTranscriptItem(content, speaker_label, confidence, start_time, end_time, type);
			
			if(isPunctuation)
				newTranscript += content;
			else
				newTranscript += " " + content;				
		}
		segItemCounts.add(numSegments++, segItemCount);
		
		if(speakers.size() > 0)   
			awsTranscription.results.speaker_labels = createSpeakerLabels(speakers.size(), numSegments, segItemCounts, newItems);
		awsTranscription.results.items = newItems;
		awsTranscription.results.transcripts[0].transcript = newTranscript;
	}
	
	private static AWSSpeakerLabels createSpeakerLabels(int numSpeakers, int numSegments, ArrayList<Integer> segItemCount, AWSTranscriptItem[] transcript_items) {
		AWSSpeakerLabels speaker_labels = new AWSSpeakerLabels(numSpeakers);
		speaker_labels.segments = new AWSSpeakerSegment[numSegments];
		
		String currSpeaker = null;
		int seg_idx = 0;
		int seg_item_idx = 0;
		AWSSpeakerSegment currSegment = null;
		
		// go through all transcript items and if it is a pronunciated item, create a corresponding speaker_label item, and if needed a new speaker label segment
		for(int item_idx = 0; item_idx < transcript_items.length; item_idx++) { // index of transcript item
			
			String end_time = transcript_items[item_idx].end_time; // get items end time, it might or might not be segment endtime....
			String start_time = transcript_items[item_idx].start_time;    // only set start time and speaker_label when creating new segment, but update end_time until new segment
			String speaker_label = transcript_items[item_idx].speaker_label;
			String type = transcript_items[item_idx].type;
			
			if(!type.equals("pronunciation"))
				continue;
			
			if(currSpeaker == null || !currSpeaker.equals(speaker_label)) { // if we have a new speaker in this transcript_item, then we have a new segment
				currSpeaker = speaker_label;
				
				currSegment = new AWSSpeakerSegment(speaker_label, start_time, end_time);
				currSegment.items = new AWSSpeakerItem[segItemCount.get(seg_idx)];
				speaker_labels.segments[seg_idx++] = currSegment;
				seg_item_idx=0;
				
				if(seg_idx >= segItemCount.size()) //this fixes problem where  punctuation comes at end of transcript and no speaker labels, hence no segment items are present for last transcript items
					break; // all segments are done, no speaker labels on remaining items. 
				
			}else { // not a new segment, so same speaker, but update segment end_time
				currSegment.end_time = end_time;
			}
			// new segment or not, each pronunciation transcript_items are corresponds to a segment item in the current segment
			AWSSpeakerItem speaker_item = new AWSSpeakerItem(start_time, end_time, speaker_label);
			currSegment.items[seg_item_idx++] = speaker_item;
		}
		return speaker_labels;
	}


	static void saveJsonFile(AWSTranscript transcriptObj) {		        
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

	static public String getWavFile() {    
		FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV", "*.wav"));
        return getFile(fileChooser);
	}
	
	static public String getAudioFile() {    
		FileChooser fileChooser = new FileChooser();
        //fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3", "*.mp3"));
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
