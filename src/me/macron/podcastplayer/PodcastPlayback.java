package me.macron.podcastplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;

import me.macron.podcastplayer.PodcastService.PodcastMediaPlayer;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class PodcastPlayback extends Activity implements OnClickListener, OnLongClickListener, OnTouchListener {
   private final static int POSITION_UPDATE_INTERVAL = 5;
   private final static int SCRUB_RATE = 5;
   private static final int REFLECTION_MIN_WIDTH = 240;
   private static final int REFLECTION_MIN_SCREEN_WIDTH = 320;
   private static final int DEFAULT_BITMAP_WIDTH = 320;
   
   private Podcasts mPodcastStore;
   private Podcast mEpisode;
   private PodcastShow mShow;
   private long mEpisodeId;
   private boolean mIsActivityPaused;
   private Bitmap mPlayBitmap;
   private Bitmap mPlaySelectedBitmap;
   private Bitmap mPauseBitmap;
   private Bitmap mPauseSelectedBitmap;
   private PodcastService mService;
   private boolean mBound = false;
   private SeekBar mSeekBar;
   private TextView mPlaybackTime;
   private DateFormatter mFormatter;
   private boolean mFirstStart;
   private int mDuration;
   private boolean mIsPlaybackPaused;
   private int mSeekPosition;
   private int mLastSavedTime;
   private int mCurrentPosition;
   private Context mContext;
   private boolean mServiceDisconnected;
   private Rect mViewRect;
   private int mAlpha;
   private ImageView mArtworkView;
   private ImageView mArtworkReflectionView;
   private long mNextPodcast;
   private long mPreviousPodcast;
   private volatile Bitmap mArtworkBitmap;
   private volatile Bitmap mArtworkReflectionBitmap;
   private int mDeviceWidth;

   private void loadArtwork() {
      new LoadArtworkTask().execute(mEpisodeId);
   }

   private class LoadArtworkTask extends AsyncTask<Long, Integer, Bitmap> {
      @Override
      protected Bitmap doInBackground(Long... params) {
         return mEpisode.getArtworkImage();
      }
      
      @Override
      protected void onPostExecute(Bitmap bitmap) {
         if (bitmap != null) {
            mArtworkBitmap = bitmap;
            mArtworkView.setImageBitmap(mArtworkBitmap);
            
            if (mArtworkReflectionView != null) {
               // Add reflection to the image if it isn't small
               if ((mDeviceWidth >= REFLECTION_MIN_SCREEN_WIDTH) && (mArtworkBitmap.getWidth() >= REFLECTION_MIN_WIDTH)) {
                  // Add reflection to the artwork
                  mArtworkReflectionBitmap =  new BitmapReflector().reflect(mArtworkBitmap, 4);
                  mArtworkReflectionView.setImageBitmap(mArtworkReflectionBitmap);
                  mArtworkReflectionView.setVisibility(View.VISIBLE);
               } else {
                  mArtworkView.setScaleType(ScaleType.FIT_CENTER);
                  mArtworkReflectionView.setVisibility(View.GONE);
               }
            }

            fadeInArtwork();
         }
      }
  }
   
   private ServiceConnection mConnection = new ServiceConnection() {
      // Called when the connection with the service is established
      public void onServiceConnected(ComponentName className, IBinder service) {
          // Because we have bound to an explicit
          // service that is running in our own process, we can
          // cast its IBinder to a concrete class and directly access it.
          PodcastMediaPlayer binder = (PodcastMediaPlayer) service;
          mService = binder.getService();
          mBound = true;
          initialize();
      }

      // Called when the connection with the service disconnects unexpectedly
      public void onServiceDisconnected(ComponentName className) {
          Log.e("PodcastPlayer", "onServiceDisconnected");
          mBound = false;
          
          // Tell the user there's a problem
          Toast.makeText(mContext, R.string.service_disconnected, Toast.LENGTH_SHORT).show();
          mServiceDisconnected = true;
          finish();
      }      
   };
   
   private void togglePlayPauseButton(boolean isPlaying) {
      if (isPlaying == true) {
         // Switch to play button
         ImageView iv = (ImageView) findViewById(R.id.playPauseButton);
         if (iv != null) {
            iv.setImageBitmap(mPlayBitmap);
         }
      } else {
         // Switch to pause button
         ImageView iv = (ImageView) findViewById(R.id.playPauseButton);
         if (iv != null) {
            iv.setImageBitmap(mPauseBitmap);
         }
      }
   }

   public boolean onTouch(View v, MotionEvent event) {
      boolean handled = false;
      int action = event.getAction();
      int viewId = v.getId();
      int resourcePair[] = new int[2];
      
      boolean isPlaying = mService.isPlaying();
      
      int resourceId = -1;
      
      switch (viewId) {
      case R.id.playPauseButton:
         if (isPlaying == true) {
            resourcePair[0] = R.drawable.play_button_default;
            resourcePair[1] = R.drawable.pause_button_selected;
         } else {
            resourcePair[0] = R.drawable.pause_button_default;
            resourcePair[1] = R.drawable.play_button_selected;
         }
         break;
         
      case R.id.rewindButton:
         resourcePair[0] = R.drawable.rewind_button_default;
         resourcePair[1] = R.drawable.rewind_button_selected;
         break;
         
      case R.id.fforwardButton:
         resourcePair[0] = R.drawable.fforward_button_default;
         resourcePair[1] = R.drawable.fforward_button_selected;
         break;
         
      default:
         // Not a recognized button
         return false;
      }

      // Handle pointer movement
      
      switch (action) {

      case MotionEvent.ACTION_DOWN:
          // down event
          resourceId = resourcePair[1];
         
          mIsLongTouch = false;
          mIsClick = true;

          mTouchDownTime = event.getEventTime();

          mLongTouchView = v;
          
          mViewRect = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());

          // post a runnable
          mTouchHandler.sendEmptyMessageDelayed(LongTouchHandler.MESSAGE_LONG_TOUCH_WAIT, LONG_TOUCH_TIME);
          handled = true;
          break;

      case MotionEvent.ACTION_MOVE:
          // check to see if the user has moved their
          // finger too far
          if (mIsClick || mIsLongTouch) {
             int x = (int)event.getX() + mViewRect.left;
             int y = (int)event.getY() + mViewRect.top;
             if (!mViewRect.contains(x, y)) {
                 // cancel the current operation
                 mTouchHandler.removeMessages(LongTouchHandler.MESSAGE_LONG_TOUCH_WAIT);
                 mTouchHandler.removeMessages(LongTouchHandler.MESSAGE_LONG_TOUCH_ACTION);

                 mIsClick = false;
                 mIsLongTouch = false;
                  
                 if (viewId == R.id.playPauseButton) {
                    if (isPlaying == true)  {
                       resourceId = R.drawable.pause_button_default;
                    } else {
                       resourceId = R.drawable.play_button_default;
                    }
                 } else {
                    resourceId = resourcePair[0];
                 }
             }
          }
          handled = true;
          break;

      case MotionEvent.ACTION_CANCEL:
          mIsClick = false;
          // Fall through
      case MotionEvent.ACTION_UP:
         if (mViewRect.contains((int)event.getX() + mViewRect.left, (int)event.getY() + mViewRect.top)) {         
            resourceId = resourcePair[0];
         } else {
            // Lifting finger/pointer outside of the bounds of the button.  No image update.
            resourceId = -1;
         }
         
          // cancel any message
          mTouchHandler.removeMessages(LongTouchHandler.MESSAGE_LONG_TOUCH_WAIT);
          mTouchHandler.removeMessages(LongTouchHandler.MESSAGE_LONG_TOUCH_ACTION);

          long elapsedTime = event.getEventTime() - mTouchDownTime;
          if (mIsClick && elapsedTime < LONG_TOUCH_TIME) {
             onClick(v);
          }
          handled = true;
          break;

      }

      if (resourceId != -1) {
         ((ImageView)v).setImageResource(resourceId);
      }
      
      // we did not consume the event, pass it on
      // to the button
      return handled;       
   }

   private void play() {
      mService.play();
      mPodcastStore.setPlayed(mEpisodeId);
      ImageView iv = (ImageView)findViewById(R.id.playPauseButton);
      if (iv != null) {
         iv.setImageBitmap(mPauseBitmap);
      }
   }
   
   private void goNextPodcast() {
      mService.stop();
      if (setPodcast(mNextPodcast) == false) {
         finish();
         return;
      }
      initialize();
      if (!mService.isPlaying()) {
         play();
      }
   }
   
   private void goPreviousPodcast() {
      mService.stop();
      if (setPodcast(mPreviousPodcast) == false) {
         finish();
         return;
      }
      initialize();
      if (!mService.isPlaying()) {
         play();
      }
   }
   
   public void onClick(View view) {
      if (mBound == false) {
         return;
      }
      
      boolean isPlaying = mService.isPlaying();
      
      switch (view.getId()) {
      case R.id.playPauseButton:
         togglePlayPauseButton(isPlaying);
         if (isPlaying == true) {
            mService.stop();
            mPodcastStore.setPosition(mEpisodeId, mService.getCurrentPosition());
            
            // Tell the user we paused
            Toast.makeText(this, R.string.paused, Toast.LENGTH_SHORT).show();
            
            mIsPlaybackPaused = true;
            
         } else {
            mService.play();
            mIsPlaybackPaused = false;
         }
         break;
         
      case R.id.rewindButton:
         if (mPreviousPodcast != -1) {
            if (mBound) {
               mService.pause();
            }
            
            goPreviousPodcast();
         }
         break;
         
      case R.id.fforwardButton:
         if (mNextPodcast != -1) {
            if (mBound) {
               mService.pause();
            }
            
            goNextPodcast();
         }
         break;
      }
   }

   public boolean onLongClick(View view) {
      int position;
      
      if (mBound == false) {
         return false;
      }
      
      switch (view.getId()) {
      case R.id.rewindButton:
         position = mService.getCurrentPosition();
         position -= SCRUB_RATE * 1000;
         if (position < 0) {
            position = 0;
         }
         mService.seekTo(position);
         break;
         
      case R.id.fforwardButton:
         position = mService.getCurrentPosition();
         position += SCRUB_RATE * 1000;
         if (position > mDuration) {
            position = mDuration - 1000; // play the last second of audio
         }
         mService.seekTo(position);
         break;
      }
      
      return false;
   }
   
   private OnSeekBarChangeListener mSeekBarListener = new OnSeekBarChangeListener() { 

      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
         if (mBound == false) {
            return;
         }
         
         if (fromUser == true) {
            mPlaybackTime.setText(mFormatter.formatTime(progress));
            mSeekPosition = progress;
         }
      }
      
      public void onStartTrackingTouch (SeekBar seekBar) {
         if (mBound == false) {
            return;
         }
         
         if (mIsPlaybackPaused == false) {
            // Pause playback while user is moving the slider
            mService.pause();
         }
      }
      
      public void onStopTrackingTouch (SeekBar seekBar) {
         // Seek to new position
         mService.seekTo(mSeekPosition);
         
         if (mIsPlaybackPaused == false) {
            // Resume playback
            mService.play();
         }
      }
   };
   
   private Runnable mFaderTask;
   
   private void fadeInArtwork() {
      mAlpha = 8;
      mArtworkView.setAlpha(0);
      if (mArtworkReflectionView != null) {
         mArtworkReflectionView.setAlpha(0);
      }
      
      mFaderTask = new Runnable() {
         public void run() {
            if (mAlpha < 255) {
               mAlpha += 8;
               if (mAlpha > 255) {
                  mAlpha = 255;
               }
               mArtworkView.setAlpha(mAlpha);
               if (mArtworkReflectionView != null) {
                  mArtworkReflectionView.setAlpha(mAlpha);
               }
            }
            
            if (mAlpha < 255) {
               mHandler.postDelayed(mFaderTask, 33);
            }
         }
      };
      mHandler.postDelayed(mFaderTask, 500);
   }
   
   private void initialize() {
      if (mBound == false) {
         return;
      }
      
      int position = 0;
      
      mService.setPodcast(this, mPodcastStore, mEpisode, mHandler);
      mDuration = mService.getDuration();
      
      boolean isPlaying = mService.isPlaying();
   
      if (isPlaying == true) {
         position = mService.getCurrentPosition();
      } else {
         // Seek to last playback position
         position = mPodcastStore.getPosition(mEpisode.getEpisodeId());
         if (position != 0) {
            mService.seekTo(position);
         }
         
         if ((mFirstStart == true) && (mIsPlaybackPaused == false)) {
            // Start playback on fresh startup
            mService.play();
            isPlaying = true;
            mPodcastStore.setPlayed(mEpisodeId);
         }
      }

      mPodcastStore.setLastPlayed(mEpisodeId);

      mCurrentPosition = position;
      
      TextView tv;
      ImageView iv;
      View view;
      Calendar episodeCalendar = Calendar.getInstance();
   
      loadArtwork();
      
      tv = (TextView) findViewById(R.id.playbackTitle);
      if (tv != null) {
         tv.setText(mEpisode.getTitle());
      }

      tv = (TextView) findViewById(R.id.playbackArtist);
      if (tv != null) {
         tv.setText(mEpisode.getArtist());
      }
      
      tv = (TextView) findViewById(R.id.playbackAlbum);
      if (tv != null) {
         tv.setText(mShow.getName());
      }
           
      
      tv = (TextView) findViewById(R.id.playbackDate);
      if (tv != null){
         episodeCalendar.setTime(mEpisode.getLastModified());
         tv.setText(mFormatter.formatDate(episodeCalendar));
      }

      // Register click listeners on buttons
      iv = (ImageView)findViewById(R.id.playPauseButton);
      if (iv != null) {
         iv.setOnTouchListener(this);
         iv.setOnClickListener(this);
         if (isPlaying == true) {
            iv.setImageBitmap(mPauseBitmap);
         } else {
            iv.setImageBitmap(mPlayBitmap);
         }
      }

      view = findViewById(R.id.rewindButton);
      if (view != null) {
         view.setOnTouchListener(this);
      }

      view = findViewById(R.id.fforwardButton);
      if (view != null) {
         view.setOnTouchListener(this);
      }
      
      tv = (TextView) findViewById(R.id.playbackDuration);
      if (tv != null) {
         tv.setText(mFormatter.formatTime(mEpisode.getDuration()));
      }
      
      // Setup progress bar
      mSeekBar = (SeekBar)findViewById(R.id.playbackProgress);
      if (mSeekBar != null) {
         mSeekBar.setMax(mEpisode.getDuration());
         mSeekBar.setOnSeekBarChangeListener(mSeekBarListener);
      }
      
      mPlaybackTime = (TextView)findViewById(R.id.playbackTime);
      
      updateProgress(position);
   }
   
   private void buildPlaylist() {
      mPreviousPodcast = -1;
      mNextPodcast = -1;
      
      // Get all the episodes for this show
      ArrayList<Podcast> podcastList = mPodcastStore.getEpisodeList(mEpisode.getShowId());
      if (podcastList != null) {
         int index = 0;
         int podcastListSize = podcastList.size();
         
         // Start with the currently playing podcast at the top of the list
         while (index < podcastListSize) {
            long episodeId = podcastList.get(index).getEpisodeId();
            if (episodeId == mEpisodeId) {
               if ((index + 1) < podcastListSize) {
                  mNextPodcast = podcastList.get(index + 1).getEpisodeId();
               }
               break;
            } 
            
            mPreviousPodcast = episodeId;
            index++;
         }
      }
   }
   
   private boolean setPodcast(long episodeId) {
      mEpisodeId = episodeId;
      
      mEpisode = mPodcastStore.getEpisode(episodeId);
      if (mEpisode == null) {
         return false;
      }

      File file = new File(mEpisode.getLocation());
      if (file.exists() == false) {
         Toast.makeText(this, R.string.nosdcard, Toast.LENGTH_SHORT).show();
         return false;
      }
      
      mShow = mPodcastStore.getShow(mEpisode.getShowId());
      if (mShow == null) {
         return false;
      }

      buildPlaylist();
      
      return true;
      
   }

   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      long episodeId;
      super.onCreate(savedInstanceState);

      mContext = this;
      mPodcastStore = new Podcasts(mContext);      
      mTouchHandler = new LongTouchHandler();
      
      if (savedInstanceState == null) {
         Bundle bundle = getIntent().getExtras();
         episodeId = bundle.getLong("episodeId");
         mFirstStart = true;
      } else {
         // Restore previous state
         episodeId = savedInstanceState.getLong("episodeId");
         
         boolean isPlaying = savedInstanceState.getBoolean("isPlaying");
         if (isPlaying == false) {
            mIsPlaybackPaused = true;
         }
      }

      if (setPodcast(episodeId) == false) {
         finish();
         return;
      }
         
      mFormatter = DateFormatter.getInstance();
      mPlayBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.play_button_default);
      mPlaySelectedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.play_button_selected);
      mPauseBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pause_button_default);
      mPauseSelectedBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pause_button_selected);
      
      setContentView(R.layout.play);

      // Start the podcast service
      Intent intent = new Intent(this, PodcastService.class);
      startService(intent);
      
      //MountReceiver receiver = new MountReceiver();
      //receiver.setOnMountListener(mMountListener);
      //registerReceiver(receiver, receiver.getIntentFilter());
      
      mArtworkView = (ImageView) findViewById(R.id.playbackArtwork);
      if (mArtworkView == null) {
         Log.e("PodcastPlayer", "Error loading artwork view");
         finish();
      }
      
      mArtworkView.setScaleType(ScaleType.FIT_XY);
      
      mArtworkReflectionView = (ImageView) findViewById(R.id.playbackArtworkReflection);
      if (mArtworkReflectionView != null) {
         mArtworkReflectionView.setScaleType(ScaleType.FIT_XY);
      }
      
      DeviceSpec deviceSpec = DeviceSpec.getInstance(mContext);
      
      if (deviceSpec == null) {
         mDeviceWidth = DeviceSpec.DEFAULT_BITMAP_WIDTH;
      } else {
         mDeviceWidth = deviceSpec.getScreenWidth();
      }
   }
   
   private MountListener mMountListener = new MountListener() {
      void onMounted() {
         Log.i("PodcastPlayer", "SD card mounted");
      }
         
      void onUnmounted() {
         Log.i("PodcastPlayer", "SD card unmounted");
      }
   };
   
   @Override
   public void onSaveInstanceState(Bundle outState) {
      super.onSaveInstanceState(outState);
      
      outState.putLong("episodeId", mEpisodeId);
      
      if (mBound == true) {
         outState.putBoolean("isPlaying", mService.isPlaying());
      }
   }
   
   @Override 
   protected void onStart() {
      super.onStart();
      
      if (mBound == false) {
         // Bind to the podcast service
         
         Intent intent = new Intent(this, PodcastService.class);
         intent.putExtra("episodeId", mEpisodeId);
         intent.putExtra("episodeTitle", mEpisode.getTitle());
         intent.putExtra("episodeAlbum", mShow.getName());
         
         bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
      } else {
         initialize();
      }
   }

   @Override 
   protected void onStop() {
      super.onStop();
      
      // Unbind from the service
      if (mBound) {
         boolean isPlaying = mService.isPlaying();
         
         unbindService(mConnection);
         
         if (isPlaying == false) {
            stopService(new Intent(this, PodcastService.class));
         }
         
         mBound = false;
         
         if (mArtworkBitmap != null) {
            // Get rid of the large bitmap now to prevent VM from running
            // out of memory when loading another artwork image
            // Ack! Not working.  Sometimes when resuming the activity it
            // tries to access the recycled bitmap.
            //cleanupBitmaps();
         }
      }
      
      if (mServiceDisconnected) {
         stopService(new Intent(this, PodcastService.class));
      }
   }
   
   private void cleanupBitmaps() {
      if (mArtworkBitmap != null) {
         mArtworkView.setImageBitmap(null);
         mArtworkBitmap.recycle();
         mArtworkBitmap = null;
      }

      if (mArtworkReflectionBitmap != null) {
         mArtworkReflectionView.setImageBitmap(null);
         mArtworkReflectionBitmap.recycle();
         mArtworkReflectionBitmap = null;
      }
   }
   
   @Override
   public void onPause() {
      super.onPause();

      if (mBound == false) {
         return;
      }
      
      if (mService.isPlaying() == false) {
         return;
      }

      //cleanupBitmaps();
      
      mPodcastStore.setPosition(mEpisodeId, mService.getCurrentPosition());
      mIsActivityPaused = true;
   }
   
   @Override
   public void onResume() {
      super.onResume();

      mIsActivityPaused = false;
      
      if (mBound == false) {
         // Start the podcast service
         Intent intent = new Intent(this, PodcastService.class);
         startService(intent);
         return;
      }
      
      if (mService.isPlaying() == false) {
         return;
      }
      
      //loadArtwork();
      
      mPodcastStore.setPosition(mEpisodeId, mService.getCurrentPosition());
   }

   private void updateProgress(int position) {
      int newPosition = position;
      
      mSeekBar.setProgress(newPosition);
      
      mPlaybackTime.setText(mFormatter.formatTime(newPosition));
   }
   
   private Handler mHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         switch (msg.what) {
         case PodcastService.POSITION_UPDATE:
            int position = msg.arg1;
            
            if (mIsActivityPaused == false) {
               updateProgress(position);
            }
            
            if (position >= mDuration) {
               mPodcastStore.setPosition(mEpisodeId, 0);
               if (mNextPodcast == -1) { 
                  if (!mIsPlaybackPaused) {
                     // Reset to zero-position
                     updateProgress(0);
                     togglePlayPauseButton(true);
                     mService.setPodcast(mContext, mPodcastStore, mEpisode, mHandler);
                     mIsPlaybackPaused = true;
                  }
               } else {
                  goNextPodcast();
               }
            } else {
               mLastSavedTime++;
               if (mLastSavedTime > POSITION_UPDATE_INTERVAL) {
                  mPodcastStore.setPosition(mEpisodeId, position);
                  mLastSavedTime = 0;
               }
            }
            
            mCurrentPosition = position;
            break;
            
         case PodcastService.MEDIA_ERROR:
            if (msg.arg1 == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
               mPodcastStore.setPosition(mEpisodeId, mCurrentPosition < mDuration ? mCurrentPosition : 0);
               finish();
            }
            break;
            
         case PodcastService.MEDIA_STOP:
            Log.i("PodcastPlayer", "Media stop");
            togglePlayPauseButton(true);
            break;
         }
      }
   };
   
   // Touch listener implementation borrowed from Joseph Earl: 
   // http://stackoverflow.com/questions/5917909/how-to-implement-button-with-dual-short-and-continuous-press
   //
   
   /**
    * The time before we count the current touch as
    * a long touch
    */
   public static final long LONG_TOUCH_TIME = 500;

   /**
    * The interval before calling another action when the
    * users finger is held down
        */
   public static final long LONG_TOUCH_ACTION_INTERVAL = 500;

   /**
    * The time the user first put their finger down
    */
   private long mTouchDownTime;

   /**
    * Is the current touch event a long touch event
        */
   private boolean mIsLongTouch;

   /**
    * Is the current touch event a simple quick tap (click)
    */
   private boolean mIsClick;

   /**
    * Handlers to post UI events
    */
   private LongTouchHandler mTouchHandler;

   /**
    * Reference to the long-touched view
    */
   private View mLongTouchView;

   /**
    * Handler to run actions on UI thread
    */
   private class LongTouchHandler extends Handler {
       public static final int MESSAGE_LONG_TOUCH_WAIT = 1;
       public static final int MESSAGE_LONG_TOUCH_ACTION = 2;
       @Override
       public void handleMessage(Message msg) {
           switch (msg.what) {
               case MESSAGE_LONG_TOUCH_WAIT:
                   mIsLongTouch = true;
                   mIsClick = false;

                   // flow into next case
               case MESSAGE_LONG_TOUCH_ACTION:
                   if (!mIsLongTouch) return;

                   onLongClick(mLongTouchView); // call users function

                   // wait for a bit then update
                   sendEmptyMessageDelayed(MESSAGE_LONG_TOUCH_ACTION, LONG_TOUCH_ACTION_INTERVAL); 

                   break;
           }
       }
   }
}
