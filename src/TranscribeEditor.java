//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import javafx.animation.AnimationTimer;
import javafx.application.*;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.*;
import javafx.stage.*;
import javafx.util.Duration;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.event.*;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioFileFormat.Type;

public class TranscribeEditor extends Application {
	
	static final double SCROLL_DELTA = .001045001;
    static final double MP3_SPEED_DELTA = 0.05;
    static final double CONFIDENCE_LIMIT = .5;
    static final int SCENE_WIDTH = 1200;
    static final int TRANSCRIPT_HEIGHT = 600;
    static final int SCENE_HEIGHT = TRANSCRIPT_HEIGHT + 220;
    static final int BEHIND_LIMIT = 10;
	static final int VBOX_WIDTH = 75;
	static final int SCROLL_TOL = 2*VBOX_WIDTH;
    static final long MIN_UPDATE_INTERVAL = 100000 ; // nanoseconds. Set to higher number to slow update.
    static final ScrollEvent FAKE_SCROLL = new ScrollEvent(null, 0, 0, 0, 0, false, false, false, false, false, false, 0, 1, 0, 0, 0, 0, null, 0, null, 0, 0, null);
	
    String audioFilename = null; //"L1.mp3";
	String jsonFilename = null; //"Lesson1.json";
	
	AWSTranscript awsTranscript = null;
	
	static MediaPlayer mediaPlayer = null;

	// is there a better way than to have all these floating out here?
	TextArea transcriptText = new TextArea();
	BorderPane rootNode;
	ScrollPane scrollPane = new ScrollPane();
	HBox scrollingHBox = new HBox();
	HBox outerHBox = new HBox();
	VBox bigVbox = new VBox();
	
	ArrayList<VBox> vBoxedItems;
	
    int currTransItem = 0; // should i be using bean properties for this?

    public static void main(String args[]) {
        launch(args);
    }

    // Override the start() method 
    public void start(Stage myStage) {
        myStage.setTitle("Transcription Editor");
    	rootNode = new BorderPane();
        Scene myScene = new Scene(rootNode, SCENE_WIDTH, SCENE_HEIGHT);
        
        myStage.setScene(myScene);
        
        MenuBar mb = createMenus();
        FlowPane bottomPane = createBottomPane();
		transcriptText.setWrapText(true);
		transcriptText.setEditable(false);
		transcriptText.setPrefHeight(TRANSCRIPT_HEIGHT);
		transcriptText.setText("If you load a non .wav audio file (like .mp3), there will be a slight delay when first playing single words. "
				+ "A temporary .wav file will be created to allow for easier word extraction. If you want faster single word play-back, use .wav files.");

		scrollPane.setOnScroll((ScrollEvent event) -> { scrollPane.setHvalue(scrollPane.getHvalue() + (event.getDeltaY()/Math.abs(event.getDeltaY()))*SCROLL_DELTA); });
		scrollPane.setVbarPolicy(ScrollBarPolicy.NEVER);
		scrollPane.setHbarPolicy(ScrollBarPolicy.ALWAYS);
		scrollPane.setPannable(true);
		scrollPane.setFitToHeight(true);
		scrollPane.setContent(scrollingHBox);
        
		bigVbox.getChildren().add(transcriptText);
        rootNode.setTop(mb);
        rootNode.setCenter(bigVbox);
        rootNode.setBottom(bottomPane);
        
        AnimationTimer timer = getTimer();
        timer.start();
        myStage.show();
    }
    
	private AnimationTimer getTimer() {
        final LongProperty lastUpdate = new SimpleLongProperty();
        final LongProperty itemsBehind = new SimpleLongProperty();
        
		AnimationTimer timer = new AnimationTimer() {

			// this turned into a monster and is not very robust, it cant recover from manual moving of cursor in text, rewrite would be nice...
			// it would probably be best to link the mp3 media to the vbox elements via play time, and link the vbox elements to the translation text by a counter
			// then we can allow all to move in sync 
            @Override
            public void handle(long now) { 
            	if(jsonFilename == null)
            		return;
                if (now - lastUpdate.get() > MIN_UPDATE_INTERVAL) {
            		if(mediaPlayer != null && mediaPlayer.statusProperty().getValue() == MediaPlayer.Status.PLAYING) {
            			int skip = 1;
            			if(currTransItem + skip < vBoxedItems.size()) { 
            			    String lookAhead_startTime = getStartTime(vBoxedItems.get(currTransItem + skip));
            				while((lookAhead_startTime == null || lookAhead_startTime.equals("")) && currTransItem + (++skip) < vBoxedItems.size()) // handle case where an item doesnt have a start_time, move to next item
            					lookAhead_startTime = getStartTime(vBoxedItems.get(currTransItem + skip));
            				if(lookAhead_startTime == null ) // if we cant find a item with a time, then return, we must have shown everything
            					return;
            				
            				Double itemTime = Double.parseDouble(lookAhead_startTime);
            				Double playTime = mediaPlayer.getCurrentTime().toSeconds();
            				if(playTime > itemTime) { // we have passed the start time of this item, so highlight it and scroll if needed
            					itemsBehind.set(itemsBehind.get() + skip); // record how many items since last scroll
            					currTransItem += skip;
            					if(itemsBehind.get() >= BEHIND_LIMIT) { // if we have not scrolled for enough items, scroll to get up-to-date
            						Bounds boundsInScene = vBoxedItems.get(currTransItem).localToScene(vBoxedItems.get(currTransItem).getBoundsInLocal());
                					while(boundsInScene.getMinX() > SCROLL_TOL) {
                						scrollPane.getOnScroll().handle(FAKE_SCROLL);
                						boundsInScene = vBoxedItems.get(currTransItem).localToScene(vBoxedItems.get(currTransItem).getBoundsInLocal());
                						itemsBehind.set(0);
                					}
            					}
            					transcriptText.deselect();
            					transcriptText.selectNextWord();
            					transcriptText.selectEndOfNextWord();
            					transcriptText.requestFocus();
        						vBoxedItems.get(currTransItem).setStyle("-fx-border-width: 2px;   -fx-border-style: solid;"); // highlight item
            				}
            			}
            		}
                    lastUpdate.set(now);
                }
            }
        };		
        return timer;
	}

	protected String getStartTime(VBox vBox) {
		TextField tf = (TextField) vBox.getChildren().get(3); // start time is 4 element; content, speaker confidence, start_time, end_time
		return tf.getText();
	}

	private void loadCenterFromJsonFile() {
		awsTranscript = AWSTranscript.createFromFile(jsonFilename);
		transcriptText.setText(awsTranscript.results.transcripts[0].transcript);
		
		vBoxedItems = new ArrayList<VBox>();
		Integer i = 0;
		for(AWSTranscriptItem transItem : awsTranscript.results.items) {
			vBoxedItems.add(createVBoxItem(transItem, i.toString()));
			i++;
		}
		transcriptText.setText(awsTranscript.results.transcripts[0].transcript);
		transcriptText.deselect();
		transcriptText.selectNextWord();
		
		scrollingHBox.getChildren().clear();
		bigVbox.getChildren().clear();
		outerHBox.getChildren().clear();
		scrollingHBox.getChildren().addAll(vBoxedItems);
		
		VBox labels = createLabelsVBox();
		labels.setMinWidth(1.25*VBOX_WIDTH);
		outerHBox.getChildren().addAll(labels, scrollPane);
		bigVbox.getChildren().addAll(transcriptText, outerHBox);
	}
	

	public VBox createLabelsVBox() {
		VBox vbox = new VBox();
		
		Label savecontent = new Label("save word");
		TextField content = new TextField("content");
		TextField speaker_label = new TextField("speaker_label");
		TextField confidence = new TextField("confidence");
		TextField start_time = new TextField("start_time");
		TextField end_time = new TextField("end_time");
		content.setEditable(false);
		speaker_label.setEditable(false);
		confidence.setEditable(false);
		start_time.setEditable(false);
		end_time.setEditable(false);
		content.setStyle("-fx-background-color: gray;");
		speaker_label.setStyle("-fx-background-color: gray;");
		confidence.setStyle("-fx-background-color: gray;");
		start_time.setStyle("-fx-background-color: gray;");
		end_time.setStyle("-fx-background-color: gray;");
		
		vbox.getChildren().addAll(content, speaker_label, confidence, start_time, end_time, savecontent);
		return vbox;
	}
	
	public VBox createVBoxItem(AWSTranscriptItem transItem, String id ) {
		VBox vbox = new VBox();
		
		vbox.setId(id);
		CheckBox saveBox = new CheckBox();
		TextField content = new TextField(transItem.alternatives[0].content);
		TextField speaker_label = new TextField(transItem.speaker_label);
		TextField confidence = new TextField(transItem.alternatives[0].confidence);
		TextField start_time = new TextField(transItem.start_time);
		TextField end_time = new TextField(transItem.end_time);
		content.setPrefWidth(VBOX_WIDTH);
		speaker_label.setPrefWidth(VBOX_WIDTH);
		confidence.setPrefWidth(VBOX_WIDTH);
		start_time.setPrefWidth(VBOX_WIDTH);
		end_time.setPrefWidth(VBOX_WIDTH);
		
        // Create an Edit popup menu, and menu items
		ContextMenu contextMenu = new ContextMenu();
		MenuItem listen = new MenuItem("Listen to word");
		MenuItem playHere = new MenuItem("Play from here");
        MenuItem insertBefore = new MenuItem("Insert Before");
        MenuItem insertAfter = new MenuItem("Insert After");
        MenuItem delete = new MenuItem("Delete");
       
        // set actions on menuitems
        insertBefore.setOnAction((ActionEvent ae)->{ insertColumn(vbox.getId()); });
        insertAfter.setOnAction((ActionEvent ae)->{ insertColumn( ((Integer)(Integer.parseInt(vbox.getId()) + 1)).toString() ); });
        delete.setOnAction((ActionEvent ae)->{ removeColumn(vbox.getId()); });
        listen.setOnAction((ActionEvent ae)->{ playWord(vbox.getId()); });
        playHere.setOnAction((ActionEvent ae)->{ playFromHere(vbox.getId()); });
         
        // Add the menu items to the popup menu
        contextMenu.getItems().addAll(listen, playHere, new SeparatorMenuItem(), insertBefore, insertAfter, new SeparatorMenuItem(), delete);
        
        // add menu to vbox content
		content.setContextMenu(contextMenu);
		speaker_label.setContextMenu(contextMenu);
		confidence.setContextMenu(contextMenu);
		start_time.setContextMenu(contextMenu);
		end_time.setContextMenu(contextMenu);
		
		// update transcription on edit... keeps things up to date, in real time. better for sync, worse for performance
		content.setOnKeyReleased((KeyEvent ke)->{ refreshTranscriptText(); }); 
		speaker_label.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript); });
		confidence.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript);});
		start_time.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript); });
		end_time.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript); });
		
		// flag items with low confidence
		if((transItem.alternatives[0].confidence != null) && (Double.parseDouble(transItem.alternatives[0].confidence) < CONFIDENCE_LIMIT)) {
			confidence.setStyle("-fx-background-color: red;");
		}
		vbox.setAlignment(Pos.CENTER);
		vbox.getChildren().addAll(content, speaker_label, confidence, start_time, end_time, saveBox);
		
		return vbox;
	}

	private void playFromHere(String id) {
		VBox vbox = vBoxedItems.get(Integer.parseInt(id));
		String start_time = ((TextField)vbox.getChildren().get(3)).getText();
		if(start_time.equals(""))
			return;
		playOrPause(Double.parseDouble(start_time));
	}
	
	private void playWord(String id) {
		if(audioFilename == null)
			return;
		VBox vbox = vBoxedItems.get(Integer.parseInt(id));
		String start_time = ((TextField)vbox.getChildren().get(3)).getText();
		String end_time = ((TextField)vbox.getChildren().get(4)).getText();
		 
		try {
			AudioInputStream clipStream = TranscribeUtils.createClip(audioFilename, start_time, end_time);
			Clip clip = AudioSystem.getClip();
			clip.open(clipStream);
		    clip.setFramePosition(0);
		    clip.start();
		}catch(Exception e) {
			e.printStackTrace();
	    }
	}

	private void removeColumn(String id) {
		// subtract one to ids of all later vboxes
		int idx = Integer.parseInt(id);
		for(VBox vbox : vBoxedItems) {
			int vboxId = Integer.parseInt(vbox.getId());
			if(vboxId >= idx)
				vbox.setId("" + (vboxId - 1));
		}
		// remove old vbox
		vBoxedItems.remove(idx);
		
		refreshTranscriptText();
		scrollingHBox.getChildren().clear();
		scrollingHBox.getChildren().addAll(vBoxedItems);
	}

	private void insertColumn(String id) {
		// add one to ids of all later vboxes
		int insert_idx = Integer.parseInt(id);
		for(VBox vbox : vBoxedItems) {
			int vboxId = Integer.parseInt(vbox.getId());
			if(vboxId >= insert_idx)
				vbox.setId("" + (vboxId + 1));
		}
		// create and insert new vbox
		VBox newVbox = createVBoxItem(new AWSTranscriptItem(), id);
		vBoxedItems.add(insert_idx, newVbox);
		
		refreshTranscriptText();
		scrollingHBox.getChildren().clear();
		scrollingHBox.getChildren().addAll(vBoxedItems);
    }

	private void refreshTranscriptText() {
		TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript);
		transcriptText.setText(awsTranscript.results.transcripts[0].transcript);
	}
	


	private MenuBar createMenus() {
        MenuBar mb = new MenuBar();
        
        Menu fileMenu = new Menu("_File");
        MenuItem openJson = new MenuItem("Open _JSON Transcription");
        MenuItem openAudio = new MenuItem("Open _Audio");
        MenuItem saveJson = new MenuItem("_Save JSON Transcription");
        MenuItem exit = new MenuItem("_Exit");
        
        Menu helpMenu = new Menu("_Help");
        MenuItem about = new MenuItem("About");
        helpMenu.getItems().addAll(about);
        
        
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText(null);
        alert.setContentText("Copyright 2018, Creed Alexander Erickson IV, All rights reserved.");

        about.setOnAction((ActionEvent ae)-> { alert.showAndWait(); });
        
        openJson.setOnAction((ActionEvent ae) -> { if( (jsonFilename = TranscribeUtils.getJSONFile()) != null ) loadCenterFromJsonFile(); });
        openAudio.setOnAction((ActionEvent ae) -> { 
        	audioFilename = TranscribeUtils.getAudioFile(); 
        	if(mediaPlayer != null) {
        		mediaPlayer.dispose();
        		mediaPlayer = null;
        	}
        });
        saveJson.setOnAction((ActionEvent ae)->{ 
        	if(awsTranscript != null) {
        		//updateTranscriptObj(); // we shouldnt need to update since we keep things up-to-date in real time, but this might be needed if we have sync problems...
        		TranscribeUtils.saveJsonFile(awsTranscript);
        	}
        });
        exit.setOnAction((ActionEvent ae) -> {Platform.exit();});
        
        openJson.setAccelerator(KeyCombination.keyCombination("shortcut+J"));
        openAudio.setAccelerator(KeyCombination.keyCombination("shortcut+M"));
        saveJson.setAccelerator(KeyCombination.keyCombination("shortcut+S"));
        exit.setAccelerator(KeyCombination.keyCombination("shortcut+X"));
     
        fileMenu.getItems().addAll(openJson, openAudio, new SeparatorMenuItem(), saveJson, new SeparatorMenuItem(), exit);
        mb.getMenus().addAll(fileMenu,helpMenu);
        return mb;
    }

	private FlowPane createBottomPane() {
	    FlowPane bottomPane = new FlowPane();
	    bottomPane.setAlignment(Pos.CENTER);
	    
	    Button playButton = new Button("Play/Pause");
	    Button slowDown = new Button("Slower");
	    Button speedUp = new Button("Faster");   
	    
        Button save = new Button("Save selected words"); 
        
        
        save.setOnAction((ActionEvent ae)-> { saveWords();});
	    
	    playButton.setOnAction((ActionEvent ae)-> {playOrPause();});
	    slowDown.setOnAction((ActionEvent ae)-> { if(mediaPlayer != null)  mediaPlayer.setRate(mediaPlayer.getRate() - MP3_SPEED_DELTA); });
	    speedUp.setOnAction((ActionEvent ae)-> { if(mediaPlayer != null)  mediaPlayer.setRate(mediaPlayer.getRate() + MP3_SPEED_DELTA); });
	    
	    bottomPane.getChildren().addAll(slowDown, playButton, speedUp, save);
	    return bottomPane;
	}
	
	
	private void saveWords() {
		int savedWordCount = 0;
		
		if(audioFilename == null) { // if no mp3 file opened, provide open dialog box
			audioFilename = TranscribeUtils.getAudioFile();
			if(audioFilename == null)
				return;
		}
		
		Iterator<VBox> iter = vBoxedItems.iterator();
		while(iter.hasNext()) {
			VBox vbox = iter.next();
			CheckBox checkbox = (CheckBox) vbox.getChildren().get(5);
			if(checkbox.selectedProperty().getValue() == true) {
				String start_time = ((TextField)vbox.getChildren().get(3)).getText();
				String end_time = ((TextField)vbox.getChildren().get(4)).getText();
				while(iter.hasNext()) { // if consecutive vboxes are checked, find the end time by finding end_time of last box in the series
					vbox = iter.next();
				    checkbox = (CheckBox) vbox.getChildren().get(5);
				    if(checkbox.selectedProperty().getValue() == true) {
				    	end_time = ((TextField)vbox.getChildren().get(4)).getText();
				    }else {
				    	break;
				    }
				}
				String wordFilename = "word" + ++savedWordCount + ".wav";
				saveClip(wordFilename, start_time, end_time);	
				System.out.println("saved : " + wordFilename);
			}
		}
	}
	
	private void saveClip(String outfilename, String start_timeStr, String end_timeStr) {
		try {
			AudioInputStream startStream = TranscribeUtils.createClip(audioFilename, start_timeStr, end_timeStr);
	        File outfile = new File(outfilename);
	        AudioSystem.write(startStream, Type.WAVE, outfile);
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void playOrPause() {
		playOrPause(null);
	}
	
	public void playOrPause(Double start_time) {
    	System.out.println("playorPause");
		if(audioFilename == null) { // if no mp3 file opened, provide open dialog box
			audioFilename = TranscribeUtils.getAudioFile();
			if(audioFilename == null)
				return;
		}
        if(mediaPlayer == null) { 
        	System.out.println("play with null meidaPlayer");
        	File file = new File(audioFilename);
            Media media = new Media(file.toURI().toString());
            
            mediaPlayer = new MediaPlayer(media);
        }
        if(start_time != null) {
        	System.out.println("play from start time");
        	Duration skiptime = new Duration(start_time*1_000);
        	mediaPlayer.stop();
        	mediaPlayer.setStartTime(skiptime);
        	mediaPlayer.play();
        }else if(mediaPlayer.statusProperty().getValue() == MediaPlayer.Status.PLAYING) {
        	System.out.println("pausing");
        	mediaPlayer.pause();
        	mediaPlayer.setStartTime(mediaPlayer.getCurrentTime()); // try to fix odd mediaPlayer issue, there are bugs in mediaPlayer..... it doesnt work as it should
        }else {
        	System.out.println("playing, status : " +         	mediaPlayer.statusProperty().getValue());
        	mediaPlayer.play();
        }
	}
	
	public void stop() {
    }
}