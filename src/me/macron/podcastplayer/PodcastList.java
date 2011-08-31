package me.macron.podcastplayer;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class PodcastList extends ListActivity implements OnItemClickListener {
   private Podcasts mPodcastStore = new Podcasts(this);
   private ArrayList<Podcast> mPodcastEpisodes;
   private int mPosition;
   private PodcastEpisodeAdapter mAdapter;

   public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      Intent i = new Intent(this, PodcastPlayback.class);
      Podcast podcast = mPodcastEpisodes.get(position);
      if (podcast != null) {
         mPosition = position;
         i.putExtra("episodeId", podcast.getEpisodeId());
         startActivity(i);
      }
   }
   
   // Called when the activity is first created.
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      Bundle bundle = getIntent().getExtras();
      long showId = bundle.getLong("showId");
      String showName = bundle.getString("showName");
      
      mPosition = -1;
      
      // Change the window title to the show name
      this.setTitle(showName);
      
      mPodcastEpisodes = mPodcastStore.getEpisodeList(showId);
      if (mPodcastEpisodes != null) {
         mAdapter = new PodcastEpisodeAdapter((Context)this, R.layout.podcast_episode, mPodcastStore, mPodcastEpisodes);
         
         setListAdapter(mAdapter);
    
         ListView lv = getListView();
         lv.setTextFilterEnabled(true);
         lv.setOnItemClickListener(this);
         lv.setOnScrollListener(mScrollListener);
         lv.setVerticalScrollBarEnabled(false);
         
      }
   }
   
   // Called when the activity is resumed.
   @Override
   public void onResume() {
      super.onResume();
      
      if (mPosition != -1) {
         Podcast podcast = mPodcastEpisodes.get(mPosition);
         if (podcast != null) {
            long episodeId = podcast.getEpisodeId();
            
            // Reload podcast data (such as unplayed flag) from databases
            mPodcastEpisodes.set(mPosition, mPodcastStore.getEpisode(episodeId));
            
            // Refresh the list
            mAdapter.notifyDataSetChanged();
         }
      }
   }
   
   private AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
      }
      
      public void onScrollStateChanged(AbsListView view, int scrollState) {
         view.setVerticalScrollBarEnabled(false);
      }
   };
}
