package me.macron.podcastplayer;


public abstract class PodcastScannerListener {
   abstract void onThumbnailUpdated(long showId, String location);
   abstract void onScanComplete();
}
