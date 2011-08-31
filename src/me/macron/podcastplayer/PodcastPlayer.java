package me.macron.podcastplayer;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class PodcastPlayer extends ListActivity {
   public static final String TAG = "PodcastPlayer";
   
   private static final int DIALOG_UPDATING_ID = 0;

   private PodcastScanner mScanner;
   private Podcasts mPodcastStore;
   private ArrayList<PodcastShow> mPodcastShows; 
   private int mPosition = -1;
   private PodcastShowAdapter mAdapter;
   private long mLastPlayed = -1;
   private TextView mNowPlayingTitle;
   private TextView mNowPlayingAlbum;
   private View mNowPlayingView;
   private Context mContext;
   private ProgressDialog mDialog;
   private boolean mScanComplete;
   
   private void initialize() {
      mPodcastShows = mPodcastStore.getShowList();
      
      // TEST CODE!
      //mPodcastShows.clear();
      
      if ((mPodcastShows == null) || (mPodcastShows.size() == 0)) {
         setContentView(R.layout.nopodcasts);
      } else {
         setContentView(R.layout.main);
   
         ListView lv = getListView();
         
         // Populate the podcast show list
         if (lv != null) {
            mAdapter = new PodcastShowAdapter((Context)this, R.layout.podcast_show, mPodcastShows);
            setListAdapter(mAdapter);
       
            lv.setTextFilterEnabled(true);
            lv.setOnItemClickListener(mItemClickListener);
            lv.setOnScrollListener(mScrollListener);
            lv.setVerticalScrollBarEnabled(false);
         }
   
         mNowPlayingTitle = (TextView)findViewById(R.id.nowPlayingTitle);
         mNowPlayingAlbum = (TextView)findViewById(R.id.nowPlayingAlbum);
         mNowPlayingView = findViewById(R.id.nowPlaying);
                 
         if (mNowPlayingView != null) {
            mNowPlayingView.setOnClickListener(mClickListener);
            mNowPlayingView.setOnTouchListener(mTouchListener);
         }
         
         updateNowPlaying();
      }      
   }
   
   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      
      Context applicationContext = getApplicationContext();
      mScanner = new PodcastScanner(applicationContext);
      mPodcastStore = new Podcasts(applicationContext);
      mContext = this;

      if (savedInstanceState != null) {
         mScanComplete = savedInstanceState.getBoolean("scanComplete");
      }
      
      if (mScanComplete == false) {
         String storageState = android.os.Environment.getExternalStorageState();
         if (!storageState.equals(android.os.Environment.MEDIA_MOUNTED)) {
            // Skip scan if external media is not mounted
            mScanComplete = true;
         }
      }

      if (mScanComplete == false) {
         // Scan for podcast files
         mScanner.setListener(mListener);
         
         // Start scan. Continue updating the UI when we get the "scanCompleted" callback.
         mScanner.scanMedia(mPodcastStore);
         
         mDialog = ProgressDialog.show(this, "", getResources().getText(R.string.updating_podcasts).toString(), true);
      } else {
         initialize();
      }
   }

   @Override
   public void onSaveInstanceState(final Bundle outState) {
      outState.putBoolean("scanComplete", mScanComplete);
   }
   
   // Called when the activity is paused
   @Override
   public void onPause() {
      super.onPause();
      
      if (mNowPlayingView != null) {
         mNowPlayingView.setBackgroundResource(R.drawable.now_playing);
      }
   }
   
   // Called when the activity is resumed
   @Override
   public void onResume() {
      super.onResume();
      
      if (mPosition != -1) {
         PodcastShow show = mPodcastShows.get(mPosition);
         if (show != null) {
            long showId = show.getShowId();
            
            // Reload show data (such as unplayed count) from database
            mPodcastShows.set(mPosition, mPodcastStore.getShow(showId));
            
            // Refresh the list
            mAdapter.notifyDataSetChanged();
         }
      }
      
      updateNowPlaying();
   }
   
   @Override
   public Dialog onCreateDialog(int id) {
      Dialog dialog = null;
      
      switch(id) {
      case DIALOG_UPDATING_ID:
         ProgressDialog.Builder builder = new ProgressDialog.Builder(this);
         builder.setMessage(getResources().getText(R.string.updating_podcasts).toString());
         dialog = builder.create();
         break;
      }
      
      return dialog;
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
     super.onCreateOptionsMenu(menu);
     MenuInflater inflater = getMenuInflater();
     inflater.inflate(R.menu.menu, menu);
     return true;
   }
   
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
     switch (item.getItemId()) {
     case R.id.about:
        AlertDialog builder;
        try {
           builder = AboutDialogBuilder.create(this);
           builder.show();
        } catch (NameNotFoundException e) {
           // TODO Auto-generated catch block
           e.printStackTrace();
        }        
        return true;
     }
     
     return false;
   }
   
   public void customTitleBar(String title) {
      getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
            R.layout.custom_title);
      TextView titleTvLeft = (TextView) findViewById(R.id.titleTvLeft);
      titleTvLeft.setText(title);
      ProgressBar titleProgressBar = (ProgressBar) findViewById(R.id.leadProgressBar);
      //hide the progress bar if it is not needed
      titleProgressBar.setVisibility(ProgressBar.GONE);       
   }
   
   private void updateNowPlaying() {
      long showId = -1;
      
      if (mNowPlayingTitle != null) {
         mLastPlayed = mPodcastStore.getLastPlayed();
         Podcast episode = null;
         if (mLastPlayed != -1) {
            episode = mPodcastStore.getEpisode(mLastPlayed);
         }
         
         if (episode == null) {
            // Nothing has been played yet.  Use the first episode
            // in the database instead
            ArrayList<PodcastShow> showList = mPodcastStore.getShowList(1);
            if (showList != null) {
               ArrayList<Podcast> episodeList = mPodcastStore.getEpisodeList(showList.get(0).getShowId(), 1);
               if (episodeList != null) {
                  episode = episodeList.get(0);
                  mLastPlayed = episode.getEpisodeId();
                  mPodcastStore.setLastPlayed(mLastPlayed);
               }
            }
         }
         
         if (episode != null) {
            mNowPlayingTitle.setText(episode.getTitle());
            showId = episode.getShowId();
         }
      }
      
      if (showId != -1) {
         if (mNowPlayingAlbum != null) {
            PodcastShow show = mPodcastStore.getShow(showId);
            if (show != null) {
               mNowPlayingAlbum.setText(show.getName());
            }
         }
      }
   }
   
   private OnItemClickListener mItemClickListener = new OnItemClickListener() {
      
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
         Intent i = new Intent(mContext, PodcastList.class);
         PodcastShow show = mPodcastShows.get(position);
         if (show != null) {
            mPosition = position;
            i.putExtra("showId", show.getShowId());
            i.putExtra("showName", show.getName());
            startActivity(i);
         }
      }
   };
   
   private OnClickListener mClickListener = new OnClickListener() {
      public void onClick(View view) {
         switch (view.getId()) {
         case R.id.nowPlaying:         
            if (mLastPlayed != -1) {
               Intent intent = new Intent(mContext, PodcastPlayback.class);
               intent.putExtra("episodeId", mLastPlayed);
               startActivity(intent);
            }
            break;
         }
      }
   };
   
   private OnTouchListener mTouchListener = new OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
         int action = event.getAction();
         
         switch (action) {
         case MotionEvent.ACTION_DOWN:
            mNowPlayingView.setBackgroundResource(R.drawable.now_playing_selected);
            break;
               
         case MotionEvent.ACTION_UP:
            mNowPlayingView.setBackgroundResource(R.drawable.now_playing);
            break;
         }
         
         return false;
      }
   };
   
   private AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
      }
      
      public void onScrollStateChanged(AbsListView view, int scrollState) {
         view.setVerticalScrollBarEnabled(false);
      }
   };
   
   private PodcastScannerListener mListener = new PodcastScannerListener() {
      void onThumbnailUpdated(long showId, String location) {
         // Find the show in our list and update its location
         for (int index = 0; index < mPodcastShows.size(); index++) {
            PodcastShow show = mPodcastShows.get(index);
            if (show.getShowId() == showId) {
               show.setThumbnailLocation(location);
               break;
            }
         }
         mAdapter.notifyDataSetInvalidated();
      }
      
      void onScanComplete() {
         if (mDialog != null) {
            try {
               mDialog.dismiss();
               mDialog = null;
            } catch (Exception e) {
               // Nothing
            }
         }
         mScanComplete = true;
         initialize();
      }      
   };
   
}