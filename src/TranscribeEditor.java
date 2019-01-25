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
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.event.*;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioFileFormat.Type;

public class TranscribeEditor extends Application {
	
	static double SCROLL_DELTA = .002; //.001045001;
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
    static final ScrollEvent FAKE_BACK_SCROLL = new ScrollEvent(null, 0, 0, 0, 0, false, false, false, false, false, false, 0, -1, 0, 0, 0, 0, null, 0, null, 0, 0, null);

    
    String audioFilename = null; //"L1.mp3";
	String jsonFilename = null; //"Lesson1.json";
	
	AWSTranscript awsTranscript = null;
	
	static MediaPlayer mediaPlayer = null;
	Clip clip = null;  
	boolean mediaPlaying = false;
	Duration skiptime = null;

	// is there a better way than to have all these floating out here?
    Scene myScene = null;
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
        myScene = new Scene(rootNode, SCENE_WIDTH, SCENE_HEIGHT);
        
        myStage.setScene(myScene);
        
        MenuBar mb = createMenus();
        FlowPane bottomPane = createBottomPane();
		transcriptText.setWrapText(true);
		transcriptText.setEditable(false);
		transcriptText.setPrefHeight(TRANSCRIPT_HEIGHT);
		transcriptText.setText("If you load a non .wav audio file (like .mp3), there will be a slight delay when first playing single words. "
				+ "A temporary .wav file will be created to allow for easier word extraction. If you want faster single word play-back, use .wav files.");

		scrollPane.setOnScroll((ScrollEvent event) -> { scrollPane.setHvalue(scrollPane.getHvalue() + (event.getDeltaY()/Math.abs(event.getDeltaY()))*SCROLL_DELTA ); });
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
            @Override // this will select text in the transcription to follow audio
            public void handle(long now) { 
            	if(jsonFilename == null)
            		return;
                if (Math.abs(now - lastUpdate.get()) > MIN_UPDATE_INTERVAL) {
            		if(mediaPlayer != null && mediaPlaying) {
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
            						double oldx = -1;
                					while(boundsInScene.getMinX() > SCROLL_TOL && (oldx != boundsInScene.getMinX())) {
                						oldx = boundsInScene.getMinX(); // if we hit the end, it wont scroll any further... check this
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

	private void loadCenterFromJsonFile(Boolean isGooglefile) {
		if(isGooglefile) {
		    GoogleTranscript googleTranscript = GoogleTranscript.createFromFile(jsonFilename);
		    awsTranscript = new AWSTranscript(googleTranscript);
		}else {
			awsTranscript = AWSTranscript.createFromFile(jsonFilename);
		}
		vBoxedItems = new ArrayList<VBox>();
		Integer i = 0;
		for(AWSTranscriptItem transItem : awsTranscript.results.items) {
			vBoxedItems.add(createVBoxItem(transItem, i.toString()));
			i++;
		}
		refreshTranscriptText();
		
		transcriptText.deselect();
		transcriptText.selectNextWord();
		transcriptText.selectEndOfNextWord();
		transcriptText.requestFocus();
		
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
		content.setOnKeyReleased((KeyEvent ke)->{ processContentTyping(ke, vbox.getId(), content); }); 
		speaker_label.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript); });
		confidence.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript);});
		start_time.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript); });
		end_time.setOnKeyReleased((KeyEvent ke)-> { TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript); });
		
		// add some editing conviences that I desire for faster editing
		EventHandler<DragEvent> dragOverHandler = new EventHandler <DragEvent>() {
            public void handle(DragEvent event){
                if (event.getGestureSource() != event.getGestureTarget() &&
                        event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                }
                event.consume();
            }
        };
        
        EventHandler<DragEvent> dragDroppedHandler = new EventHandler <DragEvent>() {
        	public void handle(DragEvent event){
	            Dragboard db = event.getDragboard();
	            boolean success = false;
	            if (db.hasString()) {
	            	((TextField)event.getGestureTarget()).setText(db.getString());
	                success = true;
	            }
	            event.setDropCompleted(success);
	            event.consume();
        	}
        };
        
        EventHandler<MouseEvent> dragDetectedHandler = new EventHandler <MouseEvent>() {
            public void handle(MouseEvent me){
	            Dragboard db = ((Node) me.getSource()).startDragAndDrop(TransferMode.ANY);
	            ClipboardContent data = new ClipboardContent();
	            data.putString(((TextField) me.getSource()).getText());
	            db.setContent(data);   
	            me.consume();
            }
        };
        
        content.setOnDragDetected(dragDetectedHandler);
        content.setOnDragOver(dragOverHandler);
        content.setOnDragDropped(dragDroppedHandler);
        start_time.setOnDragDetected(dragDetectedHandler);
        start_time.setOnDragOver(dragOverHandler);
        start_time.setOnDragDropped(dragDroppedHandler);
        end_time.setOnDragDetected(dragDetectedHandler);
        end_time.setOnDragOver(dragOverHandler);
        end_time.setOnDragDropped(dragDroppedHandler);
        speaker_label.setOnDragDetected(dragDetectedHandler);
        speaker_label.setOnDragOver(dragOverHandler);
        speaker_label.setOnDragDropped(dragDroppedHandler);
        
		speaker_label.setFocusTraversable(false);
		confidence.setFocusTraversable(false);
		start_time.setFocusTraversable(false);
		end_time.setFocusTraversable(false);
		saveBox.setFocusTraversable(false);
        
		
		// flag items with low confidence
		if(transItem.alternatives[0].confidence != null 
		    && !transItem.alternatives[0].confidence.equals("")
			&& (Double.parseDouble(transItem.alternatives[0].confidence) < CONFIDENCE_LIMIT)) 
		{
			confidence.setStyle("-fx-background-color: red;");
		}
		vbox.setAlignment(Pos.CENTER);
		vbox.getChildren().addAll(content, speaker_label, confidence, start_time, end_time, saveBox);
		
		return vbox;
	}



	private void playFromHere(String id) {
		// after many different attempts, i got good sync in transcript tracking with this code.. 
		Pattern punctuation_pattern = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}]");

		transcriptText.deselect();
		transcriptText.selectHome();
		transcriptText.positionCaret(0);
		currTransItem = Integer.parseInt(id); // update position for animation
		VBox vbox = null;
		for(VBox box : vBoxedItems) {
			if(box.getId().equals(id)) {
				vbox = box;
				break;
			}
			
			String content = ((TextField)box.getChildren().get(0)).getText();
			char contentChars[] = content.toCharArray();
			for(char contentchar : contentChars) {  // move forward to position in transcript character by character
				transcriptText.positionCaret(transcriptText.getCaretPosition()+1);
			}

			Boolean isPunctuation = punctuation_pattern.matcher(content).matches();
			if(!isPunctuation)			// forward past the next space for non punctuation
				transcriptText.positionCaret(transcriptText.getCaretPosition()+1);		
		}
		transcriptText.selectEndOfNextWord();
		
		String start_time = ((TextField)vbox.getChildren().get(3)).getText();
		if(start_time.equals(""))
			return;
		
		highlightPriorVboxes(id);
		playOrPause(Double.parseDouble(start_time));
	}
	
	private void highlightPriorVboxes(String id) {
		Boolean passedId = false;
		
		for(VBox vbox : vBoxedItems) {
			if(!passedId && vbox.getId() != null  && vbox.getId().equals(id)) {
				passedId = true;
				vbox.setStyle("-fx-border-width: 2px;   -fx-border-style: solid;"); // highlight selected
			}
			
			if(!passedId)
				vbox.setStyle("-fx-border-width: 2px;   -fx-border-style: solid;"); // highlight item
			else
				vbox.setStyle(null);
		}
		
		// scoll as needed
		int i = 0;
		Bounds boundsInScene = vBoxedItems.get(currTransItem).localToScene(vBoxedItems.get(currTransItem).getBoundsInLocal());
		if(boundsInScene.getMinX() > 0) {
			while(boundsInScene.getMinX() > SCROLL_TOL) {
				scrollPane.getOnScroll().handle(FAKE_SCROLL);
				boundsInScene = vBoxedItems.get(currTransItem).localToScene(vBoxedItems.get(currTransItem).getBoundsInLocal());
				if(i++ > 1000) // ugly fix for when near end and cant scroll further.
					return;
			}
		}else {
			while(boundsInScene.getMaxX() < SCROLL_TOL) {
				scrollPane.getOnScroll().handle(FAKE_BACK_SCROLL);
				boundsInScene = vBoxedItems.get(currTransItem).localToScene(vBoxedItems.get(currTransItem).getBoundsInLocal());
				if(i++ > 1000)
					return;
			}
		}
		
	}

	private void playWord(String id) {
		if(audioFilename == null)
			return;
		VBox vbox = vBoxedItems.get(Integer.parseInt(id));
		String start_time = ((TextField)vbox.getChildren().get(3)).getText();
		String end_time = ((TextField)vbox.getChildren().get(4)).getText();
		 
		try {
			AudioInputStream clipStream = TranscribeUtils.createClip(audioFilename, start_time, end_time);
		    if(clip != null && clip.isOpen())
		    	clip.close();
		    else if(clip == null)
				clip = AudioSystem.getClip();
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
		vBoxedItems.get(Integer.parseInt(id)).getChildren().get(0).requestFocus(); // put focus on next vbox content so typing can resume
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
		vBoxedItems.get(Integer.parseInt(id)).getChildren().get(0).requestFocus(); // put focus on new vbox content so typing can resume
    }
	
	private void processContentTyping(KeyEvent ke, String id, TextField content) {
		if(ke.getText().equals(" ")){ // i've decided to make spaces insert new vboxes. tab will get you to next vbox, space will create new subsequent vbox, this is all for editing convenience
			content.setText(content.getText().trim());  // remove the space that was typed
			Integer newVboxId = (Integer)(Integer.parseInt(id) + 1);
			insertColumn(newVboxId.toString() );  // create a subsequent vbox	
//			vBoxedItems.get(newVboxId).getChildren().get(0).requestFocus(); // put focus on new vbox content so typing can resume
		}
		if( ke.getCode().equals( KeyCode.DELETE )) {
			removeColumn(id);
		}
		if(ke.getCode().equals(KeyCode.COMMA) && ke.isControlDown()) { // this is a shortcut for coping endtime of next vbox to current vbox endtime//, and deleting next
			Integer nextVboxId = (Integer)(Integer.parseInt(id) + 1);
			String copiedEndTime =  ((TextField)vBoxedItems.get(nextVboxId).getChildren().get(4)).getText();
			((TextField)vBoxedItems.get(Integer.parseInt(id)).getChildren().get(4)).setText(copiedEndTime);
//			removeColumn(nextVboxId.toString());
		}
		if(ke.getCode().equals(KeyCode.P) && ke.isControlDown()) { // shortcut for playing current word
			playWord(id);
		}
		if(ke.getCode().equals(KeyCode.K) && ke.isControlDown()) { // shortcut for saving current word, "K" for keep
			((CheckBox)vBoxedItems.get(Integer.parseInt(id)).getChildren().get(5)).setSelected(true);
		}
		Integer id_int = Integer.parseInt(id);

		if(ke.getCode().equals(KeyCode.LEFT) && ke.isControlDown()) { // shortcut for moving to previous vbox
			if(id_int > 0)
				((TextField)vBoxedItems.get(id_int - 1).getChildren().get(0)).requestFocus();
		}
		if(ke.getCode().equals(KeyCode.RIGHT) && ke.isControlDown()) { // shortcut for moving to next vbox, tab does same thing
			if(id_int < vBoxedItems.size() -1)
				((TextField)vBoxedItems.get(id_int + 1).getChildren().get(0)).requestFocus();
		}
		
		refreshTranscriptText();
	}

	private void refreshTranscriptText() {
		TranscribeUtils.updateTranscriptObj(vBoxedItems, awsTranscript);
		transcriptText.setText(awsTranscript.results.transcripts[0].transcript);
	}
	


	private MenuBar createMenus() {
        MenuBar mb = new MenuBar();
        
        Menu fileMenu = new Menu("_File");
        MenuItem openAWSJson = new MenuItem("Open AWS Transcribe_JSON Transcription");
        MenuItem openGoogleJson = new MenuItem("Open GoogleSpeech JSON Transcript");
        MenuItem openAudio = new MenuItem("Open _Audio");
        MenuItem saveJson = new MenuItem("_Save as AWS JSON Transcription");
        MenuItem exit = new MenuItem("_Exit");
        
        Menu helpMenu = new Menu("_Help");
        MenuItem about = new MenuItem("About");
        helpMenu.getItems().addAll(about);
        
        
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText(null);
        alert.setContentText("Copyright 2018, Creed Alexander Erickson IV, All rights reserved.");

        about.setOnAction((ActionEvent ae)-> { alert.showAndWait(); });
        
        openAWSJson.setOnAction((ActionEvent ae) -> { if( (jsonFilename = TranscribeUtils.getJSONFile()) != null ) loadCenterFromJsonFile(false); });
        openGoogleJson.setOnAction((ActionEvent ae) -> { if( (jsonFilename = TranscribeUtils.getJSONFile()) != null ) loadCenterFromJsonFile(true); });

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
        
        openAWSJson.setAccelerator(KeyCombination.keyCombination("shortcut+J"));
        openAudio.setAccelerator(KeyCombination.keyCombination("shortcut+M"));
        saveJson.setAccelerator(KeyCombination.keyCombination("shortcut+S"));
        exit.setAccelerator(KeyCombination.keyCombination("shortcut+X"));
     
        fileMenu.getItems().addAll(openAWSJson, openGoogleJson, openAudio, new SeparatorMenuItem(), saveJson, new SeparatorMenuItem(), exit);
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
		//int savedWordCount = 0;
		
		if(audioFilename == null) { // if no mp3 file opened, provide open dialog box
			audioFilename = TranscribeUtils.getAudioFile();
			if(audioFilename == null)
				return;
		}
		
		Iterator<VBox> iter = vBoxedItems.iterator();
		String chineseword = null;  // assume we select both english and chinese words to save, and english comes first. we form the lessonData from these...
		while(iter.hasNext()) {
			VBox vbox = iter.next();
			CheckBox checkbox = (CheckBox) vbox.getChildren().get(5);
			if(checkbox.selectedProperty().getValue() == true) {
				String wordFilename = ((TextField)vbox.getChildren().get(0)).getText();
				String start_time = ((TextField)vbox.getChildren().get(3)).getText();
				String end_time = ((TextField)vbox.getChildren().get(4)).getText();
				while(iter.hasNext()) { // if consecutive vboxes are checked, find the end time by finding end_time of last box in the series
					vbox = iter.next();
				    checkbox = (CheckBox) vbox.getChildren().get(5);
				    if(checkbox.selectedProperty().getValue() == true) {
				    	if(((TextField)vbox.getChildren().get(4)).getText() != null && !((TextField)vbox.getChildren().get(4)).getText().equals("")) // if there is a new endtime, update it
				    		end_time = ((TextField)vbox.getChildren().get(4)).getText();
				    	wordFilename += " "  + ((TextField)vbox.getChildren().get(0)).getText();
				    }else {
				    	break;
				    }
				}
				if(chineseword == null) {
					chineseword = wordFilename;
					wordFilename = "chinese/" + wordFilename;
				}else{
					String section = "grammarBuilder2";
					System.out.println(section+".add(new String[] { \"" + chineseword + "\", \"" + wordFilename + "\"});");
					chineseword= null;
					wordFilename = "english/" + wordFilename;
				}
				wordFilename = wordFilename.toLowerCase().replaceAll("[ .?!,()]", "");
				wordFilename += ".wav";
				saveClip(wordFilename, start_time, end_time);	
			}
		}
	}
	
	private void saveClip(String outfilename, String start_timeStr, String end_timeStr) {
		try {
			AudioInputStream startStream = TranscribeUtils.createClip(audioFilename, start_timeStr, end_timeStr);
	        File outfile = new File(outfilename);
	        AudioSystem.write(startStream, Type.WAVE, outfile);
	        startStream.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void playOrPause() {
		playOrPause(null);
	}
	
	public void playOrPause(Double start_time) { // this turned messy due to mediaPlayer pause, play bug
		if(audioFilename == null) { // if no mp3 file opened, provide open dialog box
			audioFilename = TranscribeUtils.getAudioFile();
			if(audioFilename == null)
				return;
		}
		
		if(mediaPlayer != null) { // if there was a mediaplayer, kill old media players because .pause .play doenst work right
			if(start_time == null && mediaPlaying) {  // before disposing, if not asked to play at a time, and we where playing, record our current time.
				skiptime = mediaPlayer.getCurrentTime(); // record where we were playing
			}
			mediaPlayer.dispose();
		}

        if(start_time != null) { // if asked to play at a time, play at that time
        	skiptime = new Duration(start_time*1_000);
        	File file = new File(audioFilename);
            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
        	mediaPlayer.stop();
        	mediaPlayer.setStartTime(skiptime);
        	mediaPlayer.seek(skiptime);
        	mediaPlayer.play();
        	mediaPlaying=true;
        }else if(mediaPlaying) { // using my own playing status because built in status doesnt update correctly
        	//mediaPlayer.pause();
        	mediaPlaying=false;
        }else {
        	File file = new File(audioFilename);
            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            if(skiptime != null)
            	mediaPlayer.setStartTime(skiptime); // try to fix odd mediaPlayer issue, there are bugs in mediaPlayer..... it doesnt work as it should
        	mediaPlayer.play();
        	mediaPlaying=true;
        }
	}
	
	public void stop() {
    }
}