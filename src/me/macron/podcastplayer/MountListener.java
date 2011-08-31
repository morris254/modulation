package me.macron.podcastplayer;


// Todo: Detect media card insert/removal and rescan podcasts

public abstract class MountListener {
   abstract void onMounted();
   abstract void onUnmounted();
}
