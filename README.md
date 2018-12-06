# TranscribeEditor
#//Copyright 2018, Creed Alexander Erickson IV, All rights reserved.

This is an editor for AWS Transcribe results. It allows you to load a JSON file which as been produced by AWS Transcribe, 
modify the transcription, including adding and deleting from the items array in the JSON. You can then save the edits as 
a json file which follows the AWS Transcribe JSON format.

You can also load and play the orignal mp3 sound file which generated the transcription while you edit. The text will hightlight
what is currently playing in the sound file to allow easy verification. 

The order of content for each item is, from the top, content; confidence, start_time, end_time. you can modify all of these.
To add or delete items, right-click on the items array at the bottom.

