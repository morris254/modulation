package me.macron.podcastplayer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PodcastScanner {
   public static final int MSG_THUMBNAIL_UPDATED = 0;
   public static final int MSG_SCAN_COMPLETED = 1;
   public static final int THUMBNAIL_WIDTH = 200;

   private static final int IO_BUFFER_SIZE = 4 * 1024;
   public static final String TAG = "PodcastPlayer";

   private Context mContext;
   private Podcasts mPodcastStore;
   private ArrayList<String> mAddList;
   private PodcastScannerListener mListener;
   
   public PodcastScanner(Context context) {
      mContext = context;
      mAddList = new ArrayList<String>();
   }

   // Clean up non-existent podcasts
   public void removeOldPodcasts(Podcasts podcastStore) {
      ArrayList<PodcastShow> showList = podcastStore.getShowList();
      if (showList != null) {
         int indexShow;
         int numShows = showList.size();
         for (indexShow = 0; indexShow < numShows; indexShow++) {
            long showId = showList.get(indexShow).getShowId();
            ArrayList<Podcast> podcastEpisodes = podcastStore
                  .getEpisodeList(showId);
            if ((podcastEpisodes == null) || (podcastEpisodes.size() == 0)) {
               podcastStore.removeShow(showId);
            } else {
               int index = 0;
               int numPodcasts = podcastEpisodes.size();
               for (index = 0; index < numPodcasts; index++) {
                  Podcast podcast = (Podcast) podcastEpisodes.get(index);
                  File file = new File(podcast.getLocation());
                  if (file.exists() == false) {
                     podcastStore.removeEpisode(podcast.getEpisodeId());
                  }
               }
            }
         }
      }
   }

   private final String getThumbnailFolderName() {
      if (Integer.valueOf(android.os.Build.VERSION.SDK) >= 8) {
         return mContext.getExternalFilesDir(null) + "/thumbnails";
      } else { // Android 2.1 compatibility
         String packageName;
         
         try {
            packageName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), PackageManager.GET_META_DATA).applicationInfo.packageName;
         } catch (NameNotFoundException e) {
            packageName = "me.macron.podcastplayer";
         }
         
         
         return Environment.getExternalStorageDirectory() + "/Android/data/" + packageName + "/files/thumbnails";
         
      }
   }

   public void removeOldThumbnails(Podcasts podcastStore) {
      ArrayList<String> thumbnailList;

      File dir = new File(getThumbnailFolderName());
      File[] fileList = dir.listFiles();
      if (fileList.length == 0) {
         return;
      }

      thumbnailList = podcastStore.getThumbnailList();
      if (thumbnailList == null) {
         return;
      }

      if (thumbnailList.isEmpty() == true) {
         // Remove all cached thumbnails
         for (int fileIndex = 0; fileIndex < fileList.length; fileIndex++) {
            fileList[fileIndex].delete();
         }
      } else {
         // Clean up orphaned thumbnails
         for (int fileIndex = 0; fileIndex < fileList.length; fileIndex++) {
            int pos = Collections.binarySearch(thumbnailList,
                  fileList[fileIndex].getAbsolutePath());
            if (pos < 0) {
               // No references to this thumbnail file
               fileList[fileIndex].delete();
            }
         }
      }
   }

      
   public void scanMedia(Podcasts podcastStore) {
      mPodcastStore = podcastStore;
      Thread thread = new Thread(new Runnable() {
         public void run() {
            handleScanMediaRequest(mPodcastStore);
         }
      });     
      thread.start();
   }
   
   public void handleScanMediaRequest(Podcasts podcastStore) {
      File dir;
      File file;
      ArrayList<File> podcastList = new ArrayList<File>(10);
      ArrayList<String> traverseFolders = new ArrayList<String>(10);
      String folderName;
      long scanStart = System.currentTimeMillis();
      boolean podcastAdded = false;

      // Make sure thumbnail folder exists
      file = new File(getThumbnailFolderName());
      if (file.exists() == false) {
         file.mkdirs();
      }

      if (file.exists() == false) {
         Log.e(TAG, "Could not create thumbnail folder");
         notifyScanCompleted();
         return;
      }

      // JSON test
      //Podcasts.exportJSON(null, mContext);
      //Podcasts.importJSON(null, mContext);
      
      removeOldPodcasts(podcastStore);

      // List of folders to scan
      traverseFolders = podcastStore.getFolderList();

      while (traverseFolders.size() != 0) {
         folderName = traverseFolders.remove(0);
         Log.d(TAG, "traverse " + folderName);
         dir = new File(folderName);
         File[] fileList = dir.listFiles();
         if (fileList != null) {
            for (int i = 0; i < fileList.length; i++) {
               file = fileList[i];
               if (file.isDirectory()) {
                  // Log.d(TAG, "found directory " + file.getAbsolutePath() +
                  // "/");
                  traverseFolders.add(file.getAbsolutePath());
               } else {
                  // Only support mp3 files
                  if (file.getName().toLowerCase().endsWith(".mp3")) {
                     podcastList.add(file);
                  }
               }
            }
         }
      }

      if (podcastList.size() == 0) {
         Log.d(TAG, "no podcasts found");
      } else {
         ArrayList<GenreData> genreList = podcastStore.getGenreList();
         int genreListSize = genreList.size();

         // Filter out non-visibles from genre list
         int iterator = 0;
         while (iterator < genreListSize) {
            GenreData genreData = genreList.get(iterator);
            if (genreData.isVisible() == false) {
               genreList.remove(iterator);
               genreListSize--;
            } else {
               iterator++;
            }
         }

         Log.d(TAG, "found " + podcastList.size() + " audio files");
         Log.d(TAG, "traverseFolders "
               + (System.currentTimeMillis() - scanStart) / 1000 + " seconds");

         mAddList.clear();
         
         int podcastListSize = podcastList.size();
         int count = 0;
         for (int i = 0; i < podcastListSize; i++) {
            file = podcastList.get(i);
            Podcast podcast;
            //Log.d(TAG, "  audio file " + file.getAbsolutePath());
            // Check if the podcast is already current in the database

            podcast = podcastStore.getEpisode(file.getAbsolutePath());
            if (podcast != null) {
               if (podcast.getDateAdded().getTime() == file.lastModified()) {
                  // Skip this audio file
                  continue;
               }
            }

            MediaMetaData metaData = new MediaMetaData();
            if (metaData.setSource(file) == true) {
               String genre;
               String artist;
               String album;
               String name;
               long releaseDate;

               genre = metaData.getGenre();
               if (genre == null) {
                  genre = "None";
               }
               //Log.d(TAG, "    genre: " + genre);

               boolean scanIt = false;
               for (iterator = 0; (iterator < genreListSize)
                     && (scanIt == false); iterator++) {
                  GenreData genreData = genreList.get(iterator);
                  if (genre.equals(genreData.getName())) {
                     mAddList.add(file.getAbsolutePath());
                     scanIt = true;
                  }
               }

               if (scanIt) {
                  album = metaData.getAlbum();
                  if (album == null) {
                     album = "No Name";
                  }
                  //Log.d(TAG, "    album: " + album);

                  artist = metaData.getArtist();
                  if (artist == null) {
                     artist = "Unknown";
                  }

                  //Log.d(TAG, "    artist: " + artist);

                  name = metaData.getTitle();
                  if (name == null) {
                     // If no song name is present than use filename instead
                     name = file.getName();
                  }
                  //Log.d(TAG, "    song title: " + name);

                  releaseDate = metaData.getReleaseDate().getTime();
                  
                  long addPodcastStart = System.currentTimeMillis();
                  
                  podcastStore.addEpisode(genre, artist, album, name,
                        metaData.getShortDescription(), metaData.getDescription(),
                        file.length(), file.lastModified(), releaseDate, 
                        file.getAbsolutePath(), 0);
                  podcastAdded = true;
                  
                  Log.d(TAG, "Add podcast " + name + " (" + (System.currentTimeMillis() - addPodcastStart) + " ms)");
                  
                  // TEST TEST
                  count++;
                  if (count >= 2) {
                     //break;
                  }
               }
            }
         }
      }
      
      Log.d(TAG, "Scan took " + (System.currentTimeMillis() - scanStart)
            / 1000 + " seconds");

      // podcastStore.setLastScan(System.currentTimeMillis());
      notifyScanCompleted();
         
      if (podcastAdded) {
         updateThumbnails(podcastStore);
      }
      
      removeOldThumbnails(podcastStore);
   }

   public void setListener(PodcastScannerListener listener) {
      mListener = listener;
   }

   private void updateThumbnails(Podcasts podcastStore) {
      ArrayList<PodcastShow> showList = podcastStore.getShowList();
      if (showList == null) {
         return;
      }

      int showListSize = showList.size();
      if (showListSize == 0) {
         return;
      }

      MediaMetaData metaData = new MediaMetaData();
      PodcastShow podcastShow;
      long showId;
      
      for (int i = 0; i < showListSize; i++) {
         podcastShow = showList.get(i);
         showId = podcastShow.getShowId();
         ArrayList<Podcast> podcastList = podcastStore
               .getEpisodeList(showId, 1);
         if (podcastList != null) {
            if (metaData.setSource(podcastList.get(0).getLocation()) == true) {
               processThumbnail(podcastStore, podcastShow, metaData);
            }
         }
      }
   }

   private boolean writeSmallThumbnail(MediaMetaData metaData, String imageFileName) {
      Bitmap bitmap = metaData.getArtworkImage(THUMBNAIL_WIDTH);
      
      if (bitmap == null) {
         return false;
      }
      
      File file = new File(imageFileName);         
      OutputStream os = null;
      
      try {
         os = new FileOutputStream(file);
      } catch (FileNotFoundException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      
      bitmap.compress(Bitmap.CompressFormat.PNG, 0, os);
      
      bitmap.recycle();
      
      return true;
   }
   
   private void processThumbnail(Podcasts podcastStore, PodcastShow show, MediaMetaData metaData) {
      String title = metaData.getTitle();
      
      if ((title == null) || (title.length() == 0)) {
         return;
      }

      String album = metaData.getAlbum();
      if (album.length() == 0) {
         return;
      }

      // Create image file
      String imageFileName = getThumbnailFolderName() + "/" + md5(title);
      File thumbnailFile = new File(imageFileName);

      if (thumbnailFile.exists() == false) {
         if (writeSmallThumbnail(metaData, imageFileName) == false) {
            imageFileName = "default";
         }
      }

      // Update podcast DB if thumbnail file was created successfully
      if (imageFileName.equals("default") || (thumbnailFile.exists() == true)) {
         String thumbnailLocation = show.getThumbnailLocation();
         if ((thumbnailLocation == null) || (show.getThumbnailLocation().equals(imageFileName) == false)) { 
            podcastStore.setShowThumbnail(album, imageFileName, "image/png");
            
            notifyThumbnailUpdated(show.getShowId(), imageFileName);
         }
      }
   }

   private void notifyThumbnailUpdated(long showId, String location) {
      Message msg = mHandler.obtainMessage();
      Bundle bundle = new Bundle();
      msg.what = MSG_THUMBNAIL_UPDATED;
      bundle.putLong("showId", showId);
      bundle.putString("location", location);
      msg.setData(bundle);
      mHandler.sendMessage(msg);      
   }


   private void notifyScanCompleted() {
      Message msg = mHandler.obtainMessage();
      msg.what = MSG_SCAN_COMPLETED;
      mHandler.sendMessage(msg);      
   }   
   private Handler mHandler = new Handler() {
      public void handleMessage(Message msg) {
         if (msg.what == PodcastScanner.MSG_THUMBNAIL_UPDATED) {
            if (mListener != null) {
               Bundle bundle = msg.getData();
               mListener.onThumbnailUpdated(bundle.getLong("showId"), bundle.getString("location"));
            }
         } else if (msg.what == PodcastScanner.MSG_SCAN_COMPLETED) {
            if (mListener != null) {
               mListener.onScanComplete();
            }
         }
      }
   };

   // From  http://androidsnippets.com/create-a-md5-hash-and-dump-as-a-hex-string

   private String md5(String s) {
      try {
         // Create MD5 Hash
         MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
         digest.update(s.getBytes());
         byte messageDigest[] = digest.digest();

         // Create Hex String
         StringBuffer hexString = new StringBuffer();
         for (int i = 0; i < messageDigest.length; i++)
            hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
         return hexString.toString();

      } catch (NoSuchAlgorithmException e) {
         e.printStackTrace();
      }
      return "";
   }

}
