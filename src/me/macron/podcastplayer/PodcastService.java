package me.macron.podcastplayer;

import java.io.IOException;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PodcastService extends Service implements Runnable {
   public final static int POSITION_UPDATE = 0;
   public final static int MEDIA_ERROR = 1;
   public final static int MEDIA_STOP = 2;
   
   private final static int IDLE_TIMEOUT = 15000;
   private final static int MIN_SLEEP = 100;
   
   private MediaPlayer mPlayer;
   private String mDataSource = "";
   private Podcasts mPodcastStore;
   private long mEpisodeId = -1;
   private String mEpisodeTitle;
   private String mEpisodeAlbum;
   private Handler mClientHandler;
   private Thread mThread;
   private int mDuration;
   private int mLastPosition = -1;
   private volatile boolean mMediaError = false;
   private long mIdle;
   private volatile long mIdleStart;
   private boolean mResumePlayback;
   private boolean mPhoneStateListenerActive;
   
   // Binder given to clients
   private final IBinder mBinder = new PodcastMediaPlayer();

   // Unique Identification Number for the Notification.
   // We use it on Notification start, and to cancel it.
   private int NOTIFICATION = R.string.local_service_id;

   // Class for clients to access.  Because we know this service always
   // runs in the same process as its clients, we don't need to deal with
   // IPC.
   public class PodcastMediaPlayer extends Binder {
      PodcastService getService() {
           return PodcastService.this;
       }
   }

   @Override
   public void onCreate() {
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      Log.i("PodcastService", "Received start id " + startId + ": " + intent);
      
      // We want this service to continue running until it is explicitly
      // stopped, so return sticky.
      return START_STICKY;
   }

   @Override
   public void onDestroy() {
      Log.i("PodcastService", "Destroy");

      if (mPlayer.isPlaying()) {
         notifyMediaStop();
      }

      if (!mResumePlayback) {
         cleanup();
      }
   }

   @Override
   public IBinder onBind(Intent intent) {
      return mBinder;
   }

   @Override
   public void onLowMemory() {
      Log.i("PodcastService", "Stopping due to low memory");
      
      if (mPlayer.isPlaying()) {
         notifyMediaStop();
      }
      
      cleanup();
   }
   
   private void showNotification() {
      // Set the icon, scrolling text and timestamp
      Notification notification = new Notification(R.drawable.podcast_notification, " ",
              System.currentTimeMillis());

      notification.flags |= 
          Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
       
      // The PendingIntent to launch our activity if the user selects this notification
      Intent intent = new Intent(this, PodcastPlayback.class);
      intent.putExtra("episodeId", mEpisodeId);
      intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
       
      // The intent extras won't change unless we specify FLAG_UPDATE_CURRENT 
      PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
       
      // Set the info for the views that show in the notification panel.
      notification.setLatestEventInfo(this, mEpisodeTitle, mEpisodeAlbum, contentIntent);

      // Send the notification.
      startForeground(NOTIFICATION, notification);
   }
   
   private void notifyPosition(int position) {
      if (mPlayer == null) {
         return;
      }
      
      if (mClientHandler == null) {
         return;
      }
      
      if (mMediaError == false) {
         Message msg = mClientHandler.obtainMessage();
         msg.what = POSITION_UPDATE;
         msg.arg1 = position;
         mClientHandler.sendMessage(msg);
      }
   }
   
   public void run() {
      int currentPosition= 0;
      mIdleStart = 0;
      while ((mPlayer != null) && (currentPosition < mDuration)) {
         try {
            currentPosition = mPlayer.getCurrentPosition();
         } catch (IllegalStateException e) {
            return;
         }
         if (currentPosition > mDuration) {
            currentPosition = mDuration;
         }
         
         if (mLastPosition != currentPosition) {
            if (mThread.isInterrupted() == false) {
               notifyPosition(currentPosition);
            }
         }
         
         mLastPosition = currentPosition;
         
         // Idle check
         if (mPlayer.isPlaying() == false) {
            if (mIdleStart == 0) {
               mIdleStart = System.currentTimeMillis();
               mIdle = 0;
            } else {
               mIdle = System.currentTimeMillis() - mIdleStart;
               //Log.i("PodcastService", "Idle " + mIdle);   
               if (mIdle >= IDLE_TIMEOUT) {
                  Log.i("PodcastService", "Idle timeout, thread exit");   
                  // stop thread
                  mThread = null;
                  return;
               }
            }
         } else {
            mIdleStart = 0;
         }
         
         try {
            int sleep = 1000 - (currentPosition % 1000);
            if (sleep < MIN_SLEEP) {
               sleep = MIN_SLEEP;
            }
            //Log.i("PodcastService", "Sleep " + sleep + " ms");              
            Thread.sleep(sleep);
         } catch (InterruptedException e) {
            return;
         } catch (Exception e){
            return;
         }   
      }
      
      Log.i("PodcastService", "Thread exit");
      mThread = null;
   }
   
   private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
      public void onCompletion(MediaPlayer mp) {
         Log.i("PodcastService", "Playback completed");
         
         if (mMediaError == false) {
            notifyPosition(mDuration);
         }
         
         // Cancel the persistent notification.
         stopForeground(true);
         
         mPlayer.release();
         mPlayer = null;
      }
   };
   
   
   private MediaPlayer.OnSeekCompleteListener mSeekListener = new MediaPlayer.OnSeekCompleteListener() {
      public void onSeekComplete(MediaPlayer mp) {
         //Log.i("PodcastService", "Seek complete");         
      }
   };   

   private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
      public boolean onError(MediaPlayer mp, int what, int extra) {
         Log.i("PodcastService", "Error");         
         
         mMediaError = true;
         
         killThread();
         
         notifyMediaError(what);
         
         return false;
      }
   };   
   
   private void notifyMediaError(int err) {
      if (mClientHandler != null) {
         Message msg = mClientHandler.obtainMessage();
         msg.what = MEDIA_ERROR;
         msg.arg1 = err;
         mClientHandler.sendMessage(msg);
      }
   }
   
   private void notifyMediaStop() {
      if (mClientHandler != null) {
         mClientHandler.sendEmptyMessage(MEDIA_STOP);
      }
   }
   
   private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
      @Override
      public void onCallStateChanged(int state, String incomingNumber) {
         switch (state) {
         case TelephonyManager.CALL_STATE_RINGING:
            // Pause playback
            if (mPlayer != null) {
               mPlayer.pause();
               killThread(); // Go ahead and free up resources for the call               
               notifyMediaStop();
               mResumePlayback = true;
            }
            break;

         case TelephonyManager.CALL_STATE_OFFHOOK:
            // Don't want to resume playback after a potentially long call has ended.
            // By then the user may have forgotten about this activity.
            mResumePlayback = false;
            cleanup();
            break;
            
         case TelephonyManager.CALL_STATE_IDLE:
            // Restart playback
            if (mResumePlayback) {
               play();
               mResumePlayback = false;
            }
            break;
         }
         
         super.onCallStateChanged(state, incomingNumber);
      }
   };
   
   private void killThread() {
      if (mThread != null) {
         if (mThread.isAlive()) {
            mThread.interrupt();
            mThread = null;
         }
      }
   }
   
   private void startThreadIfDead() {
      if ((mThread == null) || !mThread.isAlive()) {
         mThread = new Thread(this);
         if (mThread != null) {
            mThread.start();
         }               
      }
   }
   
   public void kill() {
      long threadId = Thread.currentThread().getId();
      Log.i("PodcastService", "Kill " + threadId);
      
   }
   
   private void cleanup() {
      // Cancel the persistent notification.
      stopForeground(true);
      
      // Terminate notification thread for previous playing podcast
      killThread();         
      
      if (mPlayer != null) {
         mPlayer.pause();
         if (mEpisodeId != -1) {
            int position = mPlayer.getCurrentPosition();
            if (position >= mDuration) {
               position = 0;
            }
            mPodcastStore.setPosition(mEpisodeId, position);
         }
         mPlayer.release();
         mPlayer = null;
      }
   }
   
   public void setPodcast(Context context, Podcasts podcastStore, Podcast podcast, Handler handler) {
      mClientHandler = handler;
      
      String source = podcast.getLocation();
      if ((mPlayer == null) || (source.equals(mDataSource) == false)) {
         cleanup();

         mPodcastStore = podcastStore;
         
         mEpisodeId = podcast.getEpisodeId();
         mEpisodeTitle = podcast.getTitle();
         
         PodcastShow show = mPodcastStore.getShow(podcast.getShowId());
         mEpisodeAlbum = show.getName();
         
         try {
            mPlayer = new MediaPlayer();
            mPlayer.setDataSource(source);
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.prepare();
            mPlayer.setOnCompletionListener(mCompletionListener);
            mPlayer.setOnSeekCompleteListener(mSeekListener);
            mPlayer.setOnErrorListener(mErrorListener);
            mMediaError = false;
         } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
         
         mDataSource = source;
      }
      
      mDuration = mPlayer.getDuration();
      mPodcastStore.setDuration(mEpisodeId, mDuration);
      podcast.setDuration(mDuration);
      
      // If necessary, restart the thread that sends out progress updates
      startThreadIfDead();
   }
   
   public void play() {
      if (mPlayer != null) {
         if (mPlayer.isPlaying() == true) {
            // Already playing, do nothing
            return;
         }
         
         startThreadIfDead();
         
         mPlayer.start();
         
         // Display "playing" notification
         showNotification();

         if (!mPhoneStateListenerActive) {
            TelephonyManager mgr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            if (mgr != null) {
               mgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
            mPhoneStateListenerActive = true;
         }
      }
   }
   
   public void pause() {
      if (mPlayer != null) {
         mPlayer.pause();
      }
   }

   public void stop() {
      if (mPlayer != null) {
         mPlayer.pause();
         
         // Cancel the persistent notification.
         stopForeground(true);
         
         TelephonyManager mgr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
         if (mgr != null) {
            mgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
         }
         mPhoneStateListenerActive = false;
      }
   }
   
   public int getDuration() {
      if (mPlayer != null) {
         return mPlayer.getDuration();
      }
      
      return 0;
   }
   
   public int getCurrentPosition() {
      if (mPlayer != null) {
         return mPlayer.getCurrentPosition();
      }
      
      return 0;
   }
   
   public void seekTo(int msec) {
      if (mPlayer != null) {
         mPlayer.seekTo(msec);
         mIdleStart = 0; // reset thread idle timeout
         startThreadIfDead();         
      }
   }
   
   public boolean isPlaying() {
      if (mPlayer == null) {
         return false;
      }
      
      if (mResumePlayback) {
         // We will resume playback soon so pretend we are still playing so we don't 
         // get shutdown
         return true;
      }
      
      return mPlayer.isPlaying();
   }
}
