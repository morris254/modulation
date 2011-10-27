package me.macron.podcastplayer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import org.cmc.music.metadata.ImageData;
import org.cmc.music.metadata.MusicMetadata;
import org.cmc.music.metadata.MusicMetadataSet;
import org.cmc.music.myid3.MyID3;
import org.cmc.music.myid3.MyID3Listener;
import org.cmc.music.myid3.MyID3v2Frame;
import org.cmc.music.myid3.MyID3v2FrameText;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

// Wrapper class for retrieving meta data from a media file.
// Supports only MP3 id3 v2 tags using MyID3 library.
// Will support other formats in the future by integrating other
// meta data libraries (mp4 atoms for example)
//

// Credits:
//    MyID3 Java ID3 Tag Library by Charles M. Chen, charlesmchen@gmail.com. 
//       http://www.fightingquaker.com/myid3/
//    
//    MyID3 ported to Android 
//       http://sites.google.com/site/eternalsandbox/myid3-for-android
//
//    Joda-Time - Java date and time API
//       http://joda-time.sourceforge.net/
//
public class MediaMetaData {
   
   private abstract class ID3TagParser {
      public abstract void parseFrame(MyID3v2Frame frame);
   };
   
   // Parser for mp3 id3 v2.3 tags
   private class ID3v23TagParser extends ID3TagParser {
      public void parseFrame(MyID3v2Frame frame) {
         String frameId = frame.frame_id;
         
         if (frameId.equals("COMM")) {
            // Skip comments
         } else if (frameId.equals("TPE1")) {
            mArtist = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TALB")) {
            mAlbum = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TIT2")) {
            mTitle = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TCON")) {
            mGenre = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TDES")) {
            mDescription = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TIT3")) {
            mShortDescription = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("PCST")) {
            mIsPodcast = true;
         } else if (frameId.equals("TDRL")) {
            parseReleaseDate(((MyID3v2FrameText)frame).value);
         }
      }
   }
   
   // Parser for mp3 id3 v2.2 tags
   private class ID3v22TagParser extends ID3TagParser {
      public void parseFrame(MyID3v2Frame frame) {
         String frameId = frame.frame_id;
         
         // ID3v2.2
         if (frameId.equals("TP1")) {
            mArtist = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TAL")) {
            mAlbum = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TCO")) {
            mGenre = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TT2")) {
            mTitle = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("TDS")) {
            mDescription = ((MyID3v2FrameText)frame).value;
         } else if (frameId.equals("PCS")) {
            mIsPodcast = true;
         } else if (frameId.equals("TDR")) {
            parseReleaseDate(((MyID3v2FrameText)frame).value);
         }                
      }
   }
      
   private MyID3 mId3;
   private MusicMetadata mMetaData;
   private String mAlbum;
   private String mArtist;
   private String mTitle;
   private String mGenre;
   private String mShortDescription;
   private String mDescription;
   private boolean mIsPodcast;
   private Date mReleaseDate;
   
   public MediaMetaData() {
      mId3 = new MyID3();
   }
   
   public boolean setSource(String fileName) {
      File file = new File(fileName);
      return setSource(file);
   }

   private void parseReleaseDate(String releaseDate) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
      
      try {
         // Parse as GMT time
         sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
         sdf.parse(releaseDate);
         
         // Adjust to local time zone
         sdf.setTimeZone(TimeZone.getDefault());
         
         mReleaseDate = sdf.getCalendar().getTime();
      } catch (ParseException e) {
         try {
            // Fallback to joda, which has an initialization penalty around 0.5 seconds...
            DateTimeFormatter parser2 = ISODateTimeFormat.dateTimeNoMillis();
            mReleaseDate = parser2.parseDateTime(releaseDate).toDate();
         } catch (IllegalArgumentException ae) {
            // Bad date format, don't set the release date
         }
      }
  }
   
   public boolean setSource(File file) {
      MusicMetadataSet dataSet = null;

      if (file.getName().endsWith(".mp3")) {
         try {
            //dataSet = mId3.read(file, mID3Listener); // debug version
            dataSet = mId3.read(file); // read metadata
            if (dataSet == null) {
               return false;
            }
            
            mMetaData = (MusicMetadata)dataSet.getSimplified();
            if (mMetaData == null) {
               return false;
            }
            
            if (dataSet.id3v2Raw != null) {
               Vector<MyID3v2Frame> id3v2Frames = dataSet.id3v2Raw.frames;
               if ((id3v2Frames != null) && (id3v2Frames.size() > 1)) {
                  int numFrames = id3v2Frames.size();
                  int indexFrame = 0;
                  ID3TagParser parser;
                  
                  if (dataSet.id3v2Raw.version_major >= 3) {
                     parser = new ID3v23TagParser();
                  } else {
                     parser = new ID3v22TagParser();
                  }
                  
                  // Reset data
                  mIsPodcast = false;
                  mAlbum = "";
                  mArtist = "";
                  mTitle = "";
                  mGenre = "";
                  mShortDescription = "";
                  mDescription = "";
                  mReleaseDate = null;
                  
                  while (indexFrame < numFrames) {
                     parser.parseFrame((MyID3v2Frame)id3v2Frames.get(indexFrame));
                     indexFrame++;
                  }
               }
            }
            
            if (mReleaseDate == null) {
               // Use file date for the release date if not set
               mReleaseDate = new Date(file.lastModified());
            }
            
            if (mArtist == null) {
               // Use album name for artist
               mArtist = mAlbum;
            }
            
            return true;
         } catch (IOException exception) {
            Log.e("MediaMetaData", "error reading ID3 tags");
            exception.printStackTrace();
         }
      }

      return false;      
   }
   
   public String getAlbum() {
      return mAlbum;
   }
   
   public String getArtist() {
      return mArtist;
   }
   
   public String getTitle() {
      return mTitle;
   }
   
   public String getGenre() {
      if (mIsPodcast) {
         return "Podcast";
      } else {
         return mGenre;
      }
   }

   public Date getReleaseDate() {
      return mReleaseDate;
   }
   
   public String getShortDescription() {
      return mShortDescription;
   }

   public String getDescription() {
      return mDescription;
   }
   
   public Bitmap getArtworkImage(int maxWidth) {
      if (maxWidth == 0) {
         return null;
      }
      
      Vector<ImageData> pictureList = mMetaData.getPictureList();
      if ((pictureList == null) || (pictureList.size() == 0)) {
         return null;
      }
       
      ImageData imageData = (ImageData)pictureList.get(0);
      if (imageData == null) {
         return null;
      }
      
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      Bitmap bitmap = BitmapFactory.decodeByteArray(imageData.imageData, 0, imageData.imageData.length, opts);
      
      int scale=1;
      
      if ((maxWidth != -1) && (opts.outWidth > maxWidth)) {
         // Find the correct scale value. It should be the power of 2.
         int scaleWidth = opts.outWidth;
         while (scaleWidth > maxWidth) {
            scaleWidth /= 2;
            scale*=2;
         }
      }
      
      opts = new BitmapFactory.Options();
      opts.inSampleSize = scale;
      
      // recreate the new Bitmap
      bitmap = BitmapFactory.decodeByteArray(imageData.imageData, 0, imageData.imageData.length, opts);
      
      return bitmap;      
   }
   
   public InputStream getArtworkRaw() {
      Vector<ImageData> pictureList = mMetaData.getPictureList();
      if ((pictureList == null) || (pictureList.size() == 0)) {
         return null;
      }
       
      ImageData imageData = (ImageData)pictureList.get(0);
      if (imageData == null) {
         return null;
      }
      
      ByteArrayInputStream is = new ByteArrayInputStream(imageData.imageData);
      
      return is;
   }
   
   public class PodcastID3Listener extends MyID3Listener {

      public PodcastID3Listener() {
      }

      public void log(String s, Object o) {
         Log.d("ID3", s + " Object");
      }

      public void log(String s, int value) {
         Log.d("ID3", s + ", " + value);
      }

      public void log(String s, byte value) {
         Log.d("ID3", s + ", " + value);
      }

      public void log(String s, boolean value) {
         Log.d("ID3", s + ", " + value);
      }

      public void log(String s, long value) {
         Log.d("ID3", s + ", " + value);
      }

      public void log(String s, String value) {
         Log.d("ID3", s + ", " + value);
      }

      public void logWithLength(String s, String value) {
         Log.d("ID3", "logWithLength");
      }

      public void log(String s) {
         Log.d("ID3", s);
      }

      public void log() {
      }
   }

   private PodcastID3Listener mID3Listener = new PodcastID3Listener();   
}
