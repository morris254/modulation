package me.macron.podcastplayer;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;

public class PodcastShow {
   private final int THUMBNAIL_WIDTH = 128;
   private final int THUMBNAIL_HEIGHT = 128;
   
   private long mId;
   private String mName;
   private String mArtist;
   private Bitmap mThumbnailBitmap = null;
   private String mArtwork;
   private int mUnplayed;
   private int mEpisodeCount;
   private Date mLastUpdated;
   private long mLastUpdatedMillis;
   private Context mContext;
   
   private String mLanguage;
   
   public PodcastShow(Context context, long showId, String name, String artist, String artwork, int unplayed, int episodeCount, long lastUpdated) {
      mContext = context;
      mId = showId;
      mName = name;
      mArtist = artist;
      mUnplayed = unplayed;
      mEpisodeCount = episodeCount;

      // Round last updated time back to midnight
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(lastUpdated);
      roundTimeToMidnight(calendar);
      mLastUpdated = new Date(calendar.getTimeInMillis());
      mLastUpdatedMillis = calendar.getTimeInMillis();
      
      mArtwork = artwork;
      
      mLanguage = Locale.getDefault().getLanguage();
   }
   
   public long getShowId() {
      return mId;
   }
   
   public String getName() {
      return mName;
   }
   
   public String getArtist() {
      return mArtist;
   }
   
   public String getThumbnailLocation() {
      return mArtwork;
   }

   public void setThumbnailLocation(String location) {
      mArtwork = location;
      if (mThumbnailBitmap != null) {
         mThumbnailBitmap.recycle();
         mThumbnailBitmap = null;
      }
   }
   
   public Bitmap getThumbnailImage() {
      if (mArtwork == null) {
         return null;
      }
      
      if (mThumbnailBitmap == null) {
         Options options = new BitmapFactory.Options();
         options.inScaled = false;
         
         Bitmap artwork;
         
         if (mArtwork.equals("default")) {
            artwork = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.podcast_icon);  
         } else {
            artwork = BitmapFactory.decodeFile(mArtwork, options);
         }
         
         mThumbnailBitmap = artwork;
      }
      return mThumbnailBitmap;
   }

   public boolean hasUnplayed() {
      if (mUnplayed > 0) {
         return true;
      } else {
         return false;
      }
   }
   
   public int getEpisodeCount() {
      return mEpisodeCount;
   }
   
   public Date getLastUpdated() {
      return mLastUpdated;
   }
   
   // Construct a string indicating how long ago the podcast was updated:
   //    Today
   //    Yesterday
   //    NN days ago
   //
   // Todo: Move this to a "Localization" class
   //
   public String getLastUpdatedDisplayName() {
      String displayName = "";
      Calendar calendarNow = Calendar.getInstance();
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(mLastUpdated);
      
      roundTimeToMidnight(calendarNow);
      
      long timeNowMillis = calendarNow.getTimeInMillis();
      long delta = timeNowMillis - mLastUpdatedMillis;
      
      if (delta == 0) {
         if (mLanguage.equals("en")) {
            displayName = "Today";
         } else if (mLanguage.equals("ja")) {
            displayName = "ç°ì˙";
         }
      } else if (delta <= 518400000) {
         long daysAgo = delta / 86400000;
         
         if (daysAgo > 1) {
            if (mLanguage.equals("en")) {
               displayName = daysAgo + " days ago";
            } else if (mLanguage.equals("ja")) {
               displayName = daysAgo + "ì˙ëO";
            }
         } else {
            if (mLanguage.equals("en")) {
               displayName = "Yesterday";
            } else if (mLanguage.equals("ja")) {
               displayName = "çì˙";
            }
         }
      }

      if (displayName.length() == 0) {
         // Show was updated more than 6 days ago, or the device language was
         // not recognized
         DateFormatter formatter = DateFormatter.getInstance();
         displayName = formatter.formatDate(calendar);
      }
      
      return displayName;
   }
   
   
   private void roundTimeToMidnight(Calendar calendar) {
      long millis = calendar.getTimeInMillis();
      
      millis -=  
         ((calendar.get(Calendar.HOUR_OF_DAY) * 3600000) + 
         (calendar.get(Calendar.MINUTE) * 60000 ) + 
         (calendar.get(Calendar.SECOND) * 1000) + 
         calendar.get(Calendar.MILLISECOND));
      calendar.setTimeInMillis(millis);
   }   
}
