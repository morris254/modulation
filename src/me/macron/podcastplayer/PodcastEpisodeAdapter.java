package me.macron.podcastplayer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class PodcastEpisodeAdapter extends ArrayAdapter<Podcast> {
   private ArrayList<Podcast> items;
   private Context context;
   private Calendar calendarNow;
   private Podcasts mPodcastStore;

   public PodcastEpisodeAdapter(Context context, int textViewResourceId, Podcasts podcastStore, ArrayList<Podcast> items) {
      super(context, textViewResourceId, items);
      this.items = items;
      this.context = context;
      this.mPodcastStore = podcastStore;
      calendarNow = Calendar.getInstance();
   }

   @Override
   public View getView(int position, View convertView, ViewGroup parent) {
      View v = convertView;
      if (v == null) {
         LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
         v = vi.inflate(R.layout.podcast_episode, null);
      }
      Podcast episode = items.get(position);
      if (episode != null) {
         TextView tv;
         ImageView iv;
         Calendar episodeCalendar = Calendar.getInstance();
         
         tv = (TextView) v.findViewById(R.id.episodeTitle);
         if (tv != null) {
            tv.setText(episode.getTitle());
         }

         tv = (TextView) v.findViewById(R.id.artistName);
         if (tv != null) {
            // Cached version in the "episode" object is returning an empty
            // string sometimes for some reason.  Grab it directly from the database for now.
            // Hate to hammer on the database every time the user scrolls though...
            // String shortDescription = episode.getShortDescription();
            
            String shortDescription = mPodcastStore.getShortDescription(episode.getEpisodeId());
            if ((shortDescription == null) || (shortDescription.length() == 0)) {
               shortDescription = episode.getArtist();
            }
            tv.setText(shortDescription);
         }
              
         
         tv = (TextView) v.findViewById(R.id.episodeDate);
         if (tv != null){
            episodeCalendar.setTime(episode.getLastModified());
            tv.setText(DateFormatter.getInstance().formatDate(episodeCalendar));
         }

         iv = (ImageView) v.findViewById(R.id.episodeUnplayed);
         if (iv != null) {
            boolean unplayed = episode.getUnplayed();
            if (unplayed == true) {
               iv.setAlpha(255);
            } else {
               // Hide the unplayed indicator
               iv.setAlpha(0);               

            }
         }
      }
      
      return v;
   }
}
