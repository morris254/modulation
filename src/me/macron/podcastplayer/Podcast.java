package me.macron.podcastplayer;

import java.util.Date;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class Podcast {
   private long mId;
   private String mGenre;
   private String mArtist;
   private String mAlbum;
   private long mShowId;
   private String mTitle;
   private long mSize;
   private Date mDateModified;
   private Date mDateAdded;
   private String mLocation;
   private int mPlaybackPosition;
   private int mDuration;
   private boolean mUnplayed;
   private Bitmap mArtworkBitmap;
   private Context mContext;
   private int mDeviceWidth;
   private String mShortDescription;
   private String mDescription;
   
   private String trimTitle(String title) {
      String shortTitle = "";
      int index;
      
      if (title.startsWith(mAlbum)) {
         shortTitle = title.substring(mAlbum.length());
         shortTitle = shortTitle.trim();
      } else {
         // Remove parenthesized text at the end of the albumname (like "(MP3)") and try again
         index = mAlbum.length() - 1;
         int splitAt = -1;
         while (index > 0) {
            if (mAlbum.charAt(index) == ')') {
               index--;
               while (index > 0) {
                  if (mAlbum.charAt(index) == '(') {
                     // Done
                     splitAt = index;
                     index = 0;
                  } else {
                     index--;
                  }
               }
            } else {
               index--;
            }
         }
         
         if (splitAt > 0) {
            String shortAlbum = mAlbum.substring(0, splitAt);
            shortAlbum = shortAlbum.trim();
            if (title.startsWith(shortAlbum)) {
               shortTitle = title.substring(shortAlbum.length());
               shortTitle = shortTitle.trim();
            }
         }
      }    
      
      if (shortTitle.length() == 0) {
         shortTitle = title;
      } else {
         // Trim off leading punctuation
         index = 0;
         int titleLength = shortTitle.length();
         char currChar;
         while (index < titleLength) {
            currChar = shortTitle.charAt(index);
            if ((currChar == ':') || (currChar == '-') || (currChar == ' ')) {
               index++;
            } else {
               break;
            }
         }
         shortTitle = shortTitle.substring(index, titleLength);
      }
      
      return shortTitle;
   }
   
   public Podcast(Context context, long episodeId, String genre, String artist, String album, long showId, String title, String shortDescription, String description, long size, long dateModified, long dateAdded, String location, int playbackPosition, int duration, boolean unplayed) {
      mContext = context;
      mId = episodeId;
      mGenre = genre;
      mArtist = artist;
      mAlbum = album;
      mShowId = showId;
      mTitle = title;
      mShortDescription = shortDescription;
      mDescription = description;
      mSize = size;
      mDateModified = new Date(dateModified);
      mDateAdded = new Date (dateAdded);
      mLocation = location;
      mPlaybackPosition = playbackPosition;
      mDuration = duration;
      mUnplayed = unplayed;

      // Shorten the title by removing the show name from it
      mTitle = trimTitle(mTitle);
      
      DeviceSpec deviceSpec = DeviceSpec.getInstance(mContext);
      
      if (deviceSpec == null) {
         mDeviceWidth = DeviceSpec.DEFAULT_BITMAP_WIDTH;
      } else {
         mDeviceWidth = deviceSpec.getScreenWidth();
      }
      
   }
   
   public long getEpisodeId() {
      return mId;
   }
   
   public String getTitle() {
      return mTitle;
   }

   public boolean getUnplayed() {
      return mUnplayed;
   }
   
   public void setUnplayed(boolean unplayed) {
      mUnplayed = unplayed;
   }
   
   public Date getLastModified() {
      return mDateModified;
   }
   
   public Date getDateAdded() {
      return mDateAdded;
   }
   
   public String getArtist() {
      return mArtist;
   }
   
   public long getShowId() {
      return mShowId;
   }
   
   public String getLocation() {
      return mLocation;
   }
   
   public int getPosition() {
      return mPlaybackPosition;
   }
   
   public int getDuration() {
      return mDuration;
   }
   
   public void setDuration(int duration) {
      mDuration = duration;
   }
   
   public String getShortDescription() {
      return mShortDescription; 
   }

   public String getDescription() {
      return mDescription; 
   }
   
   public Bitmap getArtworkImage() {
      if (mArtworkBitmap == null) {
         MediaMetaData metaData = new MediaMetaData();
         
         Bitmap artwork = null;
         
         if (metaData.setSource(mLocation) == true) {
            artwork = metaData.getArtworkImage(mDeviceWidth);
         }
         
         if (artwork == null) {
            artwork = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.podcast_icon);
         }
         
         if (artwork == null) {
            return null;
         }
         
         mArtworkBitmap = artwork;
      }
      
      return mArtworkBitmap;
   }   
 }
