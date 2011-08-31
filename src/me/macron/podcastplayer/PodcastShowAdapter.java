package me.macron.podcastplayer;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class PodcastShowAdapter extends ArrayAdapter<PodcastShow> {
   private ArrayList<PodcastShow> mItems;
   private Context mContext;

   public PodcastShowAdapter(Context context, int textViewResourceId, ArrayList<PodcastShow> items) {
      super(context, textViewResourceId, items);
      this.mItems = items;
      this.mContext = context;
   }

   @Override
   public View getView(int position, View convertView, ViewGroup parent) {
      View v = convertView;
      if (v == null) {
         LayoutInflater vi = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
         v = vi.inflate(R.layout.podcast_show, null);
      }
      PodcastShow show = mItems.get(position);
      if (show != null) {
         TextView tv;
         ImageView iv;
         Resources res = mContext.getResources();
         
         tv = (TextView) v.findViewById(R.id.titleText);
         if (tv != null) {
            tv.setText(show.getName());
         }
              
         tv = (TextView) v.findViewById(R.id.episodeText);
         if (tv != null){
            int count = show.getEpisodeCount();
            tv.setText(res.getQuantityString(R.plurals.episode_counter, count, count));            
         }

         iv = (ImageView) v.findViewById(R.id.thumbnailImage);
         if (iv != null) {
            Bitmap bitmap = show.getThumbnailImage();
            if (bitmap == null) {
               bitmap = BitmapFactory.decodeResource(res, R.drawable.artwork_placeholder);
            }
            iv.setImageBitmap(bitmap);
         }
         
         iv = (ImageView) v.findViewById(R.id.showUnplayed);
         if (iv != null) {
            if (show.hasUnplayed() == true) {
               iv.setAlpha(255);
            } else {
               // Hide the unplayed indicator
               iv.setAlpha(0);               
            }
         }
         
         tv = (TextView) v.findViewById(R.id.showDate);
         if (tv != null){
            
            String lastUpdated = show.getLastUpdatedDisplayName();
            
            tv.setText(lastUpdated);
         }
      }
      
      return v;
   }
}
