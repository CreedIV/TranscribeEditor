//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

import javafx.animation.AnimationTimer;
import javafx.application.*;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.*;
import javafx.stage.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.event.*;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class TranscribeEditor extends Application {
	
	static final double SCROLL_DELTA = .001045001;
    static final double MP3_SPEED_DELTA = 0.05;
    static final double CONFIDENCE_LIMIT = .5;
    static final int SCENE_WIDTH = 1200;
    static final int TRANSCRIPT_HEIGHT = 600;
    static final int SCENE_HEIGHT = TRANSCRIPT_HEIGHT + 170;
    static final int BEHIND_LIMIT = 10;
	static final int VBOX_WIDTH = 70;
	static final int SCROLL_TOL = VBOX_WIDTH;
    static final long MIN_UPDATE_INTERVAL = 100000 ; // nanoseconds. Set to higher number to slow update.
    static final ScrollEvent FAKE_SCROLL = new ScrollEvent(null, 0, 0, 0, 0, false, false, false, false, false, false, 0, 1, 0, 0, 0, 0, null, 0, null, 0, 0, null);
	
    String mp3Filename = null; //"L1.mp3";
	String jsonFilename = null; //"Lesson1.json";
	
	AWSTranscriptObj awsTranscription = null;

	// is there a better way than to have all these floating out here?
	TextArea transcriptText = new TextArea();
	BorderPane rootNode;
	ScrollPane scrollPane = new ScrollPane();
	HBox scrollingHBox = new HBox();
	HBox outerHBox = new HBox();
	VBox bigVbox = new VBox();
	
	MediaPlayer mediaPlayer = null;
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
        
		transcriptText.deselect();
		transcriptText.selectNextWord();
		transcriptText.setWrapText(true);
		transcriptText.setPrefHeight(TRANSCRIPT_HEIGHT);

		scrollPane.setOnScroll((ScrollEvent event) -> { scrollPane.setHvalue(scrollPane.getHvalue() + (event.getDeltaY()/Math.abs(event.getDeltaY()))*SCROLL_DELTA); });
		scrollPane.setVbarPolicy(ScrollBarPolicy.NEVER);
		scrollPane.setHbarPolicy(ScrollBarPolicy.ALWAYS);
		scrollPane.setPannable(true);
		scrollPane.setFitToHeight(true);
		scrollPane.setContent(scrollingHBox);
        
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
		TextField tf = (TextField) vBox.getChildren().get(2); // start time is 3 element; content, confidence, start_time, end_time
		return tf.getText();
	}

	private void loadCenterFromJsonFile() {
		awsTranscription = AWSTranscriptObj.createFromFile(jsonFilename);
		transcriptText.setText(awsTranscription.results.transcripts[0].transcript);
		
		vBoxedItems = new ArrayList<VBox>();
		Integer i = 0;
		for(AWSTranscriptItem transItem : awsTranscription.results.items) {
			vBoxedItems.add(createVBoxItem(transItem, i.toString()));
			i++;
		}

		transcriptText.setText(awsTranscription.results.transcripts[0].transcript);
		scrollingHBox.getChildren().clear();
		bigVbox.getChildren().clear();
		scrollingHBox.getChildren().addAll(vBoxedItems);
		bigVbox.getChildren().addAll(transcriptText, scrollPane);
	}
	
	
	public VBox createVBoxItem(AWSTranscriptItem transItem, String id ) {
		VBox vbox = new VBox();
		
		vbox.setId(id);
		TextField content = new TextField(transItem.alternatives[0].content);
		TextField confidence = new TextField(transItem.alternatives[0].confidence);
		TextField start_time = new TextField(transItem.start_time);
		TextField end_time = new TextField(transItem.end_time);
		content.setPrefWidth(VBOX_WIDTH);
		confidence.setPrefWidth(VBOX_WIDTH);
		start_time.setPrefWidth(VBOX_WIDTH);
		end_time.setPrefWidth(VBOX_WIDTH);
		
        // Create an Edit popup menu, and menu items
		ContextMenu contextMenu = new ContextMenu();
        MenuItem insertBefore = new MenuItem("Insert Before");
        MenuItem insertAfter = new MenuItem("Insert After");
        MenuItem delete = new MenuItem("Delete");
       
        // set actions on menuitems
        insertBefore.setOnAction((ActionEvent ae)->{ insertColumn(vbox.getId()); });
        insertAfter.setOnAction((ActionEvent ae)->{ insertColumn( ((Integer)(Integer.parseInt(vbox.getId()) + 1)).toString() ); });
        delete.setOnAction((ActionEvent ae)->{ removeColumn(vbox.getId()); });
         
        // Add the menu items to the popup menu
        contextMenu.getItems().addAll(insertBefore, insertAfter, new SeparatorMenuItem(), delete);
        
        // add menu to vbox content
		content.setContextMenu(contextMenu);
		confidence.setContextMenu(contextMenu);
		start_time.setContextMenu(contextMenu);
		end_time.setContextMenu(contextMenu);
		
		// update transcription on edit...
		content.setOnKeyReleased((KeyEvent ke)->{ refreshTranscriptText(); }); 
		
		// flag items with low confidence
		if((transItem.alternatives[0].confidence != null) && (Double.parseDouble(transItem.alternatives[0].confidence) < CONFIDENCE_LIMIT)) {
			confidence.setStyle("-fx-background-color: red;");
		}
		vbox.getChildren().addAll(content, confidence, start_time, end_time);
		
		return vbox;
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
		updateTranscriptObj();
		transcriptText.setText(awsTranscription.results.transcripts[0].transcript);
	}
	
	private void updateTranscriptObj() {
		//update AwsTranscriptObj from vBoxedItems
		//completely replace items in AwsTranscriptObj with new items from vboxedItems and create new transcript in process
		
		AWSTranscriptItem[] newItems = new AWSTranscriptItem[vBoxedItems.size()];
		String newTranscript = "";
		int i = 0;
		for(VBox vboxItem : vBoxedItems) {
	        
			ObservableList<Node> vboxChildren = FXCollections.observableArrayList(vboxItem.getChildren());
			String content = ((TextField) vboxChildren.get(0)).getText();
			String confidence = ((TextField) vboxChildren.get(1)).getText();
			String start_time = ((TextField) vboxChildren.get(2)).getText();
			String end_time = ((TextField) vboxChildren.get(3)).getText();
			
			newItems[i++] = new AWSTranscriptItem(content, confidence, start_time, end_time);
			if(content.length() == 1 && Pattern.matches("[\\p{Punct}\\p{IsPunctuation}]", content))
				newTranscript += content;
			else
				newTranscript += " " + content;				
		}
		awsTranscription.results.items = newItems;
		awsTranscription.results.transcripts[0].transcript = newTranscript;
	}

	private MenuBar createMenus() {
        MenuBar mb = new MenuBar();
        
        Menu fileMenu = new Menu("_File");
        MenuItem openJson = new MenuItem("Open _JSON Transcription");
        MenuItem openMp3 = new MenuItem("Open _Mp3");
        MenuItem saveJson = new MenuItem("_Save JSON Transcription");
        MenuItem exit = new MenuItem("_Exit");
        
        openJson.setOnAction((ActionEvent ae) -> { if( (jsonFilename = TranscribeUtils.getJSONFile()) != null ) loadCenterFromJsonFile(); });
        openMp3.setOnAction((ActionEvent ae) -> { mp3Filename = TranscribeUtils.getMp3File(); });
        saveJson.setOnAction((ActionEvent ae)->{ 
        	if(awsTranscription != null) {
        		//updateTranscriptObj();
        		TranscribeUtils.saveJsonFile(awsTranscription);
        	}
        });
        exit.setOnAction((ActionEvent ae) -> {Platform.exit();});
        
        openJson.setAccelerator(KeyCombination.keyCombination("shortcut+J"));
        openMp3.setAccelerator(KeyCombination.keyCombination("shortcut+M"));
        saveJson.setAccelerator(KeyCombination.keyCombination("shortcut+S"));
        exit.setAccelerator(KeyCombination.keyCombination("shortcut+X"));
     
        fileMenu.getItems().addAll(openJson, openMp3, new SeparatorMenuItem(), saveJson, new SeparatorMenuItem(), exit);
        mb.getMenus().add(fileMenu);
        return mb;
    }

	private FlowPane createBottomPane() {
	    FlowPane bottomPane = new FlowPane();
	    bottomPane.setAlignment(Pos.CENTER);
	    
	    Button playButton = new Button("Play/Pause");
	    Button slowDown = new Button("Slower");
	    Button speedUp = new Button("Faster");
	    
	    playButton.setOnAction((ActionEvent ae)-> {playOrPause();});
	    slowDown.setOnAction((ActionEvent ae)-> { if(mediaPlayer != null)  mediaPlayer.setRate(mediaPlayer.getRate() - MP3_SPEED_DELTA); });
	    speedUp.setOnAction((ActionEvent ae)-> { if(mediaPlayer != null)  mediaPlayer.setRate(mediaPlayer.getRate() + MP3_SPEED_DELTA); });
	    
	    bottomPane.getChildren().addAll(slowDown, playButton, speedUp);
	    return bottomPane;
	}
	
	public void playOrPause() {
		if(mp3Filename == null) { // if no mp3 file opened, provide open dialog box
			mp3Filename = TranscribeUtils.getMp3File();
			if(mp3Filename == null)
				return;
		}
        if(mediaPlayer == null) { 
        	File file = new File(mp3Filename);
            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
        }
        if(mediaPlayer.statusProperty().getValue() == MediaPlayer.Status.PLAYING)
        	mediaPlayer.pause();
        else
        	mediaPlayer.play();
	}
	public void stop() {
        //System.out.println("Stop called");
    }
}