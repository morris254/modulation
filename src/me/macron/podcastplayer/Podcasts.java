package me.macron.podcastplayer;

import static android.provider.BaseColumns._ID;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

public class Podcasts {
	private static PodcastData podcasts = null;
	private Context mContext;
	
	public Podcasts(Context ctx) {
		if (podcasts == null) {
			podcasts = new PodcastData(ctx);
		}
      mContext = ctx;
	}

	private int incrementEpisodeCount(SQLiteDatabase db, long showId, int by) {
	   int episodeCount = -1;
	   
      final String[] FROM_EPISODE_COUNT = { PodcastData.EPISODE_COUNT };
      ContentValues values = new ContentValues();      
      Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, FROM_EPISODE_COUNT, _ID + "=" + showId, null, null, null, null);
      if (cursor == null) {
         return -1;
      }
      
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         
         episodeCount = cursor.getInt(0) + by;
         if (episodeCount < 0) {
            episodeCount = 0;
         }
      
         values.put(PodcastData.EPISODE_COUNT, episodeCount);
         db.update(PodcastData.ALBUMS_TABLE_NAME, values, _ID + "=" + showId, null);
      }
      
      cursor.close();
	
	   return episodeCount;
	}
	
	public void removeEpisode(long episodeId) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      long showId = -1;
      boolean unplayed = false;
      
      final String[] FROM_ALBUM_ID = { PodcastData.ALBUM, PodcastData.UNPLAYED };
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, FROM_ALBUM_ID, _ID + "=" + episodeId, null, null, null, null);
      
      // Get the show ID for this episode
      if (cursor == null) {
         return;
      }
      
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         showId = cursor.getLong(0);
         if (cursor.getInt(1) > 0) {
            unplayed = true;
         }
      }
      
      cursor.close();
      
      db.beginTransaction();
      
      try {
         db.delete(PodcastData.PODCASTS_TABLE_NAME, _ID + "=" + episodeId, null);
         
         if (showId != -1) {
            if (unplayed) {
               // Remove from the show's unplayed count
               incrementUnplayedCount(db, showId, -1);
            }
            
            // Update the show's episode count
            int episodeCount = incrementEpisodeCount(db, showId, -1);
            if (episodeCount == 0) {
               // Show has no more episodes, remove from database
               removeShow(showId);
            }
         }
         
         db.setTransactionSuccessful();
      } finally {
         db.endTransaction();
      }
	}
	
   private Podcast getEpisode(SQLiteDatabase db, Cursor cursor) {
      long episodeId;
      String genre = "";
      String artist = "";
      String album = "";
      long showId;
      String name;
      long size;
      long dateModified;
      long dateAdded;
      String location;
      int playbackPosition;
      int duration;
      boolean unplayed;
      long id = -1;
      String shortDescription;
      String description;
      
      cursor.moveToFirst();
      
      episodeId = cursor.getLong(0);
      
      final String[] nameSelect = { PodcastData.NAME };
      
      Cursor subCursor = db.query(PodcastData.GENRES_TABLE_NAME, nameSelect, _ID + "=" + cursor.getLong(1), null, null, null, null);
      if (subCursor != null) {
         if (subCursor.getCount() > 0) {
            subCursor.moveToFirst();
            genre = subCursor.getString(0);
         }
         subCursor.close();
      }

      final String[] albumSelect = { PodcastData.NAME, PodcastData.ARTIST };
      
      showId = cursor.getLong(2);
      subCursor = db.query(PodcastData.ALBUMS_TABLE_NAME, albumSelect, _ID + "=" + showId, null, null, null, null);
      if (subCursor != null) {
         if (subCursor.getCount() > 0) {
            subCursor.moveToFirst();
            album = subCursor.getString(0);
            id = subCursor.getLong(1);
         }
         subCursor.close();
      }
      
      if (id != -1) {
         subCursor = db.query(PodcastData.ARTISTS_TABLE_NAME, nameSelect, _ID + "=" + id, null, null, null, null);
         if (subCursor != null) {
            if (subCursor.getCount() > 0) {
               subCursor.moveToFirst();
               artist = subCursor.getString(0);
            }
            subCursor.close();
         }
      }

      subCursor = db.query(PodcastData.ARTISTS_TABLE_NAME, nameSelect, _ID + "=" + cursor.getLong(3), null, null, null, null);
      if (subCursor != null) {
         if (subCursor.getCount() > 0) {
            subCursor.moveToFirst();
            artist = subCursor.getString(0);
         }
         subCursor.close();
      }
      
      name = cursor.getString(4);
      size = cursor.getLong(5);
      dateModified = cursor.getLong(6);
      dateAdded = cursor.getLong(7);
      location = cursor.getString(8);
      playbackPosition = cursor.getInt(9);
      duration = cursor.getInt(10);
      if (cursor.getInt(11) == 0) {
         unplayed = false;
      } else {
         unplayed = true;
      }
      shortDescription = cursor.getString(12);
      description = cursor.getString(13);
      
      return new Podcast(mContext, episodeId, genre, artist, album, showId, name, shortDescription, description, size, dateModified, dateAdded, location, playbackPosition, duration, unplayed);
   }

	public Podcast getEpisode(String location) {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] podcastSelectArgs = { location };
      final String[] podcastSelect = { 
            _ID, 
            PodcastData.GENRE, 
            PodcastData.ALBUM, 
            PodcastData.ARTIST, 
            PodcastData.NAME,
            PodcastData.SIZE,
            PodcastData.DATE_MODIFIED, 
            PodcastData.DATE_ADDED,
            PodcastData.LOCATION,
            PodcastData.PLAYBACK_POSITION, 
            PodcastData.DURATION, 
            PodcastData.UNPLAYED,
            PodcastData.SHORT_DESCRIPTION,
            PodcastData.DESCRIPTION};

      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, podcastSelect, "location=?", podcastSelectArgs, null, null, null);
      if (cursor == null) {
         return null;
      }
      
      if (cursor.getCount() == 0) {
         return null;
      }
      
      Podcast podcast = getEpisode(db, cursor);
      cursor.close();
      return podcast;
	}

   public void setDateAdded(long episodeId, long dateAdded) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.DATE_ADDED, dateAdded);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values,  _ID + "=" + episodeId, null);
   }
	
   public void setReleaseDate(long episodeId, long releaseDate) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.DATE_MODIFIED, releaseDate);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values,  _ID + "=" + episodeId, null);
   }

   public void setShortDescription(long episodeId, String shortDescription) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.SHORT_DESCRIPTION, shortDescription);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values,  _ID + "=" + episodeId, null);
   }

   public String getShortDescription(long episodeId) {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromPodcasts = { PodcastData.SHORT_DESCRIPTION }; 
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, fromPodcasts, _ID + "=" + episodeId, null, null, null, null);
      if (cursor == null) {
         return "";
      }
      
      String shortDescription = "";
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         shortDescription = cursor.getString(0);
      }
      
      cursor.close();
      
      return shortDescription;
   }
   
   public void setDescription(long episodeId, String description) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.DESCRIPTION, description);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values,  _ID + "=" + episodeId, null);
   }
   
   public void setLastPlayed(long episodeId) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.LAST_PLAYED, episodeId);
      db.update(PodcastData.HISTORY_TABLE_NAME, values, null, null);
   }

   public long getLastPlayed() {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromHistory = { PodcastData.LAST_PLAYED }; 
      Cursor cursor = db.query(PodcastData.HISTORY_TABLE_NAME, fromHistory, null, null, null, null, null);
      if (cursor == null) {
         return -1;
      }
      
      long lastPlayed = -1;
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         lastPlayed = cursor.getLong(0);
      }
      
      cursor.close();
      
      return lastPlayed;
   }

   public void setPosition(long episodeId, int position) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.PLAYBACK_POSITION, position);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values, _ID + "=" + episodeId, null);
      
      if  (position == 0) {
         Log.i("PodcastPlayer", "Position 0");         
      }
      
   }

   public int getPosition(long episodeId) {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromPodcasts = { PodcastData.PLAYBACK_POSITION }; 
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, fromPodcasts, _ID + "=" + episodeId, null, null, null, null);
      if (cursor == null) {
         return 0;
      }
      
      int position = 0;
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         position = cursor.getInt(0);
      }
      
      cursor.close();
      
      return position;
   }

   public void setLastScan(long lastScan) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.LAST_SCAN, lastScan);
      db.update(PodcastData.HISTORY_TABLE_NAME, values, null, null);
   }
   

   public long getLastScan() {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromHistory = { PodcastData.LAST_SCAN }; 
      Cursor cursor = db.query(PodcastData.HISTORY_TABLE_NAME, fromHistory, null, null, null, null, null);
      if (cursor == null) {
         return -1;
      }
      
      long lastScan = -1;
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         lastScan = cursor.getLong(0);
      }
      
      cursor.close();
      
      return lastScan;
   }
   
   public void setDuration(long episodeId, int duration) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.DURATION, duration);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values, _ID + "=" + episodeId, null);
   }

   private int incrementUnplayedCount(SQLiteDatabase db, long showId, int by) {
      int unplayed = -1;
      final String[] fromShow = { PodcastData.UNPLAYED };
      Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, fromShow, _ID + "=" + showId, null, null, null, null);
      if (cursor == null) {
         return -1;
      }
      
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         unplayed = cursor.getInt(0) + by;
         if (unplayed < 0) {
            unplayed = 0;
         }
         
         ContentValues values = new ContentValues();
         
         values.put(PodcastData.UNPLAYED, unplayed);
         db.update(PodcastData.ALBUMS_TABLE_NAME, values, _ID + "=" + showId, null);
      }
      
      cursor.close();
      
      return unplayed;
   }
   
   private void setUnplayed(SQLiteDatabase db, long episodeId, int unplayed) {
      ContentValues values = new ContentValues(); 
      final String[] fromPodcasts = { PodcastData.UNPLAYED, PodcastData.ALBUM };
      int currentUnplayed;
      long showId;
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, fromPodcasts, _ID + "=" + episodeId, null, null, null, null);
      if ((cursor != null) && (cursor.getCount() > 0)) {
         cursor.moveToFirst();
         currentUnplayed = cursor.getInt(0);
         showId = cursor.getLong(1);

         if (currentUnplayed != unplayed) {
            values.put(PodcastData.UNPLAYED, unplayed);
            db.update(PodcastData.PODCASTS_TABLE_NAME, values, _ID + "=" + episodeId, null);
            
            // Update the show's unplayed count
            incrementUnplayedCount(db, showId, (unplayed == 0) ? -1 : 1);
         }
      }
      
      if (cursor != null) {
         cursor.close();
      }
   }
   
	public void setPlayed(long episodeId) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      
      setUnplayed(db, episodeId, 0);
	}
	
   public Podcast getEpisode(long episodeId) {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromPodcasts = { 
            _ID, 
            PodcastData.GENRE, 
            PodcastData.ALBUM, 
            PodcastData.ARTIST, 
            PodcastData.NAME, 
            PodcastData.SIZE, 
            PodcastData.DATE_MODIFIED, 
            PodcastData.DATE_ADDED, 
            PodcastData.LOCATION, 
            PodcastData.PLAYBACK_POSITION,
            PodcastData.DURATION,
            PodcastData.UNPLAYED,
            PodcastData.UNPLAYED,
            PodcastData.SHORT_DESCRIPTION,
            PodcastData.DESCRIPTION};

      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, fromPodcasts, _ID + "=" + episodeId, null, null, null, null);
      if (cursor == null) {
         return null;
      }
      
      Podcast episode = null;
      
      if (cursor.getCount() > 0) {
         episode = getEpisode(db, cursor);
      }
      
      cursor.close();
      
      return episode;
   }

   public long getShowLastUpdated(long showId) {
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromAlbums = { PodcastData.LAST_UPDATED }; 
      Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, fromAlbums, _ID + "=" + showId, null, null, null, null);
      if (cursor == null) {
         return -1;
      }
      
      long lastUpdated = -1;
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         lastUpdated = cursor.getLong(0);
      }
      
      cursor.close();
      
      return lastUpdated;
   }
   
   public void setShowLastUpdated(long showId, long lastUpdated) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.LAST_UPDATED, lastUpdated);
      db.update(PodcastData.ALBUMS_TABLE_NAME, values, _ID + "=" + showId, null);
   }
   
	public long addEpisode(String genre, String artist, String album, String name, String shortDescription, String description, long size, long dateAdded, long dateModified, String location, int duration) {
		ContentValues values = new ContentValues();
		long genreId;
		long artistId;
		long albumId;
		long removeEpisodeId = -1;
		
		artistId = getArtistId(artist);
		
      albumId = getAlbumId(album, artistId);
      if (albumId != -1) {
         values.put(PodcastData.ALBUM, albumId);
      }
		
		// Check if the podcast is already in the database
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] fromPodcasts = { _ID, PodcastData.SIZE };
      final String[] podcastSelectArgs = { name };
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, fromPodcasts, 
            PodcastData.ALBUM + "=" + albumId + " AND " + 
            PodcastData.ARTIST + "=" + artistId + " AND " + 
            PodcastData.NAME + "=?",
            podcastSelectArgs, null, null, null);
      
      if (cursor != null) {
         if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            if (cursor.getLong(1) >= size) {
               // Return id of the podcast that already exists
               cursor.close();
               return cursor.getLong(0);
            }
         }
         cursor.close();
      }

      // Remove existing podcast entry with the same filename.  
      // Its size is either smaller, or it's being updated with a newer
      // version of the file.
      final String[] removePodcastSelectArgs = { location };
      final String[] removePodcastSelect = { _ID }; 

      cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, removePodcastSelect, "location=?", removePodcastSelectArgs, null, null, null);
      if (cursor != null) {
         if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            removeEpisodeId = cursor.getLong(0);
         }
         
         cursor.close();
      }
      
      genreId = getGenreId(genre);
      if (genreId != -1) {
         values.put(PodcastData.GENRE, genreId);
      }

		values.put(PodcastData.ARTIST, artistId);
		values.put(PodcastData.NAME, name);
		values.put(PodcastData.SHORT_DESCRIPTION, shortDescription);
      values.put(PodcastData.DESCRIPTION, description);
		values.put(PodcastData.SIZE, size);
		values.put(PodcastData.DATE_MODIFIED, dateModified); // releaseDate
		values.put(PodcastData.DATE_ADDED, dateAdded);
		values.put(PodcastData.LOCATION, location);
      values.put(PodcastData.DURATION, duration);

      db.close();
		db = Podcasts.podcasts.getWritableDatabase();
		
		db.beginTransaction();
		
		long episodeId = -1;
		
		try {
   		episodeId = db.insertOrThrow(PodcastData.PODCASTS_TABLE_NAME, null, values);
   		
   		// Update the show's episode count
   		incrementEpisodeCount(db, albumId, 1);
   
   		// Update the unplayed count
   		setUnplayed(db, episodeId, 1);
   		
   		// Update the show's last_updated timestamp
   		long lastUpdated = getShowLastUpdated(albumId);
   		if (lastUpdated < dateModified) {
   		   setShowLastUpdated(albumId, dateModified);
   		}
   		
         if (removeEpisodeId != -1) {
            removeEpisode(removeEpisodeId);
         }
   		
   		db.setTransactionSuccessful();
		} finally {
		   db.endTransaction();
		}
		
		return episodeId;
	}
	
	public void setShowThumbnail(String genre, String thumbnail, String mime) {
		SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(PodcastData.THUMBNAIL, thumbnail);
		values.put(PodcastData.THUMBNAIL_MIME, mime);
      genre = genre.replace("'", "''");
      final String[] genreSelectArgs = { genre };
		db.update(PodcastData.ALBUMS_TABLE_NAME, values, "name=?", genreSelectArgs);		
	}

   public ArrayList<PodcastShow> getShowList() {
      return getShowList(-1);
   }

   public ArrayList<String> getThumbnailList() {
      final String[] FROM = {PodcastData.THUMBNAIL};
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, FROM, null, null, null, null, PodcastData.THUMBNAIL);
      
      if (cursor == null) {
         return null;
      }
      
      ArrayList<String> thumbnailList = null;
      
      int showCount = cursor.getCount();
      
      if (showCount > 0) {
         thumbnailList = new ArrayList<String>();
         
         String location;
         while (cursor.moveToNext()) {
            location = cursor.getString(0);
            if (location != null) {
               thumbnailList.add(location);
            }
         }
      }

      cursor.close();
      
      return thumbnailList;      
   }

	public ArrayList<PodcastShow> getShowList(int maxSize) {
		final String[] FROM = { 
		      _ID, 
		      PodcastData.NAME, 
		      PodcastData.ARTIST, 
		      PodcastData.THUMBNAIL, 
		      PodcastData.EPISODE_COUNT, 
		      PodcastData.UNPLAYED,
		      PodcastData.LAST_UPDATED};
		SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
		Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, FROM, null, null, null, null, PodcastData.NAME);
		
		if (cursor == null) {
		   return null;
		}
		
      ArrayList<PodcastShow> podcastShows = null;
      
      int showCount = cursor.getCount();
      
		if (showCount > 0) {
	      podcastShows = new ArrayList<PodcastShow>();
	      
         if (maxSize == 0) {
            return podcastShows;
         }
         
         if (maxSize == -1) {
            maxSize = showCount;
         }
         
         showCount = 0;	      
         while (cursor.moveToNext() && (showCount < maxSize)) {
            Cursor subCursor;
            
            // Get the artist name
            String artist = "";
            subCursor = getArtist(cursor.getLong(2));
            if ((subCursor != null) && (subCursor.getCount() > 0)) {
               subCursor.moveToFirst();
               artist = subCursor.getString(0);
            }
            
            if (subCursor != null) {
               subCursor.close();
            }
            
            podcastShows.add(new PodcastShow(mContext, cursor.getLong(0), cursor.getString(1), artist, cursor.getString(3), cursor.getInt(5), cursor.getInt(4), cursor.getLong(6)));
            showCount++;
         }
		}

		cursor.close();
		
		return podcastShows;
	}
	
	public void removeShow(long showId) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      db.delete(PodcastData.ALBUMS_TABLE_NAME, _ID + "=" + showId, null);
	}
	
	public PodcastShow getShow(long showId) {
      final String[] FROM = { 
            _ID, 
            PodcastData.NAME, 
            PodcastData.ARTIST, 
            PodcastData.THUMBNAIL, 
            PodcastData.EPISODE_COUNT, 
            PodcastData.UNPLAYED,
            PodcastData.LAST_UPDATED};
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, FROM, _ID + "=" + showId, null, null, null, null);
      
      if (cursor == null) {
         return null;
      }
      
      PodcastShow show = null;
      
      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         
         Cursor subCursor;
         
         // Get the artist name
         String artist = "";
         subCursor = getArtist(cursor.getLong(2));
         if ((subCursor != null) && (subCursor.getCount() > 0)) {
            subCursor.moveToFirst();
            artist = subCursor.getString(0);
         }
         
         if (subCursor != null) {
            subCursor.close();
         }
         
         show = new PodcastShow(mContext, cursor.getLong(0), cursor.getString(1), artist, cursor.getString(3), cursor.getInt(5), cursor.getInt(4), cursor.getLong(6));
      }
      
      cursor.close();
      
      return show ;
	}
	
   public void setArtist(long episodeId, String artist) {
      SQLiteDatabase db = Podcasts.podcasts.getWritableDatabase();
      ContentValues values = new ContentValues();      
      values.put(PodcastData.ARTIST, artist);
      db.update(PodcastData.PODCASTS_TABLE_NAME, values,  _ID + "=" + episodeId, null);
   }
   
	private Cursor getArtist(long id) {
      final String[] FROM = { PodcastData.NAME };
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      Cursor cursor = db.query(PodcastData.ARTISTS_TABLE_NAME, FROM, _ID + "=" + id, null, null, null, PodcastData.NAME);
      return cursor;
	}

   public ArrayList<String> getFolderList() {
      final String[] fromFolders = { PodcastData.LOCATION }; 
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      Cursor cursor = db.query(PodcastData.FOLDERS_TABLE_NAME, fromFolders, null, null, null, null, null);
      
      if (cursor == null) {
         return null;
      }

      ArrayList<String> folderList = new ArrayList<String>();

      if (cursor.getCount() > 0) {
         cursor.moveToFirst();
         do {
            folderList.add(cursor.getString(0));
         } while (cursor.moveToNext());
      }
      
      cursor.close();
      
      return folderList;
   }

   public ArrayList<Podcast> getEpisodeList(long showId) {
      return getEpisodeList(showId, -1);
   }

   public ArrayList<Podcast> getEpisodeList(long showId, int maxSize) {
      final String[] fromPodcasts = { 
            _ID, 
            PodcastData.GENRE, 
            PodcastData.NAME, 
            PodcastData.SIZE, 
            PodcastData.DATE_MODIFIED, 
            PodcastData.DATE_ADDED, 
            PodcastData.LOCATION, 
            PodcastData.PLAYBACK_POSITION, 
            PodcastData.DURATION, 
            PodcastData.UNPLAYED,
            PodcastData.SHORT_DESCRIPTION,
            PodcastData.DESCRIPTION};
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, fromPodcasts, "album=" + showId, null, null, null, PodcastData.DATE_MODIFIED);
      
      if (cursor == null) {
         return null;
      }
      
      ArrayList<Podcast> podcastEpisodes = null;
      
      int episodeCount = cursor.getCount();
      
      if (episodeCount > 0) {      
         podcastEpisodes = new ArrayList<Podcast>();
         
         if (maxSize == 0) {
            return podcastEpisodes;
         }
         
         if (maxSize == -1) {
            maxSize = episodeCount;
         }
         
         episodeCount = 0;
         
         // Enumerate episodes starting with the newest first
         cursor.moveToLast();
         do {
            long episodeId;
            String genre = "";
            String artist = "";
            String album = "";
            String name = "";
            String location = "";
            long size = 0;
            long dateModified = 0;
            long dateAdded = 0;
            int playbackPosition = 0;
            int duration = 0;
            boolean unplayed = false;
            long artistId = -1;
            String shortDescription = "";
            String description = "";
            
            final String[] fromName = { PodcastData.NAME };
            
            episodeId = cursor.getLong(0);
            
            Cursor subCursor = db.query(PodcastData.GENRES_TABLE_NAME, fromName, _ID + "=" + cursor.getLong(1), null, null, null, null);
            if (subCursor != null) {
               if (subCursor.getCount() > 0) {
                  subCursor.moveToFirst();
                  genre = subCursor.getString(0);
               }
               subCursor.close();
            }
   
            final String[] fromAlbum = { PodcastData.NAME, PodcastData.ARTIST };
            
            subCursor = db.query(PodcastData.ALBUMS_TABLE_NAME, fromAlbum, _ID + "=" + showId, null, null, null, null);
            if ((subCursor != null) && (subCursor.getCount() > 0)) {
               subCursor.moveToFirst();
               album = subCursor.getString(0);
               artistId = subCursor.getLong(1);
            }
            
            if (subCursor != null) {
               subCursor.close();
            }
            
            if (artistId != -1) {
               subCursor = db.query(PodcastData.ARTISTS_TABLE_NAME, fromName, _ID + "=" + artistId, null, null, null, null);
               if ((subCursor != null) && (subCursor.getCount() > 0)) {
                  subCursor.moveToFirst();
                  artist = subCursor.getString(0);
               }
               
               if (subCursor != null) {
                  subCursor.close();
               }
            }
   
            name = cursor.getString(2);
            size = cursor.getLong(3);
            dateModified = cursor.getLong(4);
            dateAdded = cursor.getLong(5);
            location = cursor.getString(6);
            playbackPosition = cursor.getInt(7);
            if (cursor.getInt(9) == 0) {
               unplayed = false;
            } else {
               unplayed = true;
            }
            shortDescription = cursor.getString(10);
            description = cursor.getString(11);
            
            podcastEpisodes.add(new Podcast(mContext, episodeId, genre, artist, album, showId, name, shortDescription, description, size, dateModified, dateAdded, location, playbackPosition, duration, unplayed));
            episodeCount++;
         } while (cursor.moveToPrevious() && (episodeCount < maxSize));
      }

      cursor.close();
      
      return podcastEpisodes;
   }
   
   public ArrayList<GenreData> getGenreList() {
      final String[] fromGenres = { PodcastData.NAME, PodcastData.INDEXABLE };
      SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      Cursor cursor = db.query(PodcastData.GENRES_TABLE_NAME, fromGenres, null, null, null, null, null);
      
      if (cursor == null) {
         return null;
      }
      
      ArrayList<GenreData> genreList = null;
      
      if (cursor.getCount() > 0) {      
         genreList = new ArrayList<GenreData>();
         // Enumerate genres starting with the newest first
         cursor.moveToFirst();
         do {
            genreList.add(new GenreData(cursor.getString(0), cursor.getInt(1)));
         } while (cursor.moveToNext());
      }

      cursor.close();
      
      return genreList;
   }
   
	private long getGenreId(String genre) {
		long genreId;
		final String[] genreFrom = { _ID };
		int count = 0;
		SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
      final String[] genreSelectArgs = { genre };
		Cursor cursor = db.query(PodcastData.GENRES_TABLE_NAME, genreFrom, "name=?", genreSelectArgs, null, null, null);
		if (cursor != null) {
			count = cursor.getCount();
		}
		if (count > 0) {
			cursor.moveToFirst();
			genreId = cursor.getLong(0);
		} else {
			// Add the genre
			db.close();
			db = Podcasts.podcasts.getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(PodcastData.NAME, genre);
			genreId = db.insertOrThrow(PodcastData.GENRES_TABLE_NAME, null, values);
		}
		
		if (cursor != null) {
		   cursor.close();
		}
		
		return genreId;
	}
	
	private long getArtistId(String artist) {
		long artistId;
		final String[] artistFrom = { _ID };
		int count = 0;
		SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
		Cursor cursor = db.query(PodcastData.ARTISTS_TABLE_NAME, artistFrom, "name=?", null, null, null, null);
		if (cursor != null) {
			count = cursor.getCount();
		}
		if (count > 0) {
			cursor.moveToFirst();
			artistId = cursor.getLong(0);
		} else {
			// Add the genre
			db.close();
			db = Podcasts.podcasts.getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(PodcastData.NAME, artist);
			artistId = db.insertOrThrow(PodcastData.ARTISTS_TABLE_NAME, null, values);
		}
		
      if (cursor != null) {
         cursor.close();
      }
      
		return artistId;
	}	

	private long getAlbumId(String album, long artistId) {
		long albumId;
		final String[] albumSelect = { _ID };
		int count = 0;
		SQLiteDatabase db = Podcasts.podcasts.getReadableDatabase();
		final String[] albumSelectArgs = { album };
		Cursor cursor = db.query(PodcastData.ALBUMS_TABLE_NAME, albumSelect, "name=?", albumSelectArgs, null, null, null);
		if (cursor != null) {
			count = cursor.getCount();
		}
		if (count > 0) {
			cursor.moveToFirst();
			albumId = cursor.getLong(0);
		} else {
			// Add the genre
			db.close();
			db = Podcasts.podcasts.getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(PodcastData.NAME, album);
			values.put(PodcastData.ARTIST, artistId);
			albumId = db.insertOrThrow(PodcastData.ALBUMS_TABLE_NAME, null, values);
		}
		
		if (cursor != null) {
		   cursor.close();
		}
		
		return albumId;
	}

   static public void exportJSON(SQLiteDatabase db, Context context) {
	   String exportedDataFolderName;
	   
      if (Integer.valueOf(android.os.Build.VERSION.SDK) >= 8) {
         exportedDataFolderName =  context.getExternalFilesDir(null) + "/export";
      } else { // Android 2.1 compatibility
         String packageName;
         
         try {
            packageName = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA).applicationInfo.packageName;
         } catch (NameNotFoundException e) {
            packageName = "me.macron.podcastplayer";
         }
         
         
         exportedDataFolderName = Environment.getExternalStorageDirectory() + "/Android/data/" + packageName + "/files/export";
         
      }
      
      File file = new File(exportedDataFolderName);
      if (file.exists() == false) {
         file.mkdirs();
      }
	   
	   file = new File(exportedDataFolderName + "/podcasts.json");
	   if (file.exists()) {
	      file.delete();
	   }

	   if (db == null) {
	      db = Podcasts.podcasts.getReadableDatabase();
	   }
	   
      final String[] podcastSelect = { _ID, PodcastData.LOCATION, PodcastData.PLAYBACK_POSITION, PodcastData.UNPLAYED };
      Cursor cursor = db.query(PodcastData.PODCASTS_TABLE_NAME, podcastSelect, null, null, null, null, null);
      
      if (cursor == null) {
         return;
      }
      
      if (cursor.getCount() == 0) {
         return;
      }

      JSONObject jPodcasts = new JSONObject();
      
      JSONArray jPodcastArray = new JSONArray(); 
      int index = 0;
      
      while (cursor.moveToNext()) {
         JSONObject jPodcast = new JSONObject();
         
         int unplayed = cursor.getInt(3);
         
         if (unplayed == 1) {
            // There is no position data to save.  Skip it.
            continue;
         }
         
         try {
            jPodcast.put("location", cursor.getString(1));
            jPodcast.put("position", Integer.toString(cursor.getInt(2)));
            jPodcast.put("unplayed", Integer.toString(unplayed));
            jPodcastArray.put(index, jPodcast);
         } catch (JSONException e) {
            Log.e("PodcastPlayer", "Error forming data at index" + cursor.getLong(0));
            return;
         }
         index++;
      }
	
      try {
         jPodcasts.put("podcasts", jPodcastArray);
      } catch (JSONException e) {
         Log.e("PodcastPlayer", "Error finalizing data at index" + cursor.getLong(0));
         return;
      }
      
      OutputStream os;
      
      try {
         os = new FileOutputStream(file);
      } catch (FileNotFoundException e) {
         Log.e("PodcastPlayer", "File not found error");
         return;
      }
      
      byte[] jsonBytes;
      
      try {
         jsonBytes = jPodcasts.toString(3).getBytes(); 
      } catch (JSONException e) {
         Log.e("PodcastPlayer", "Error exporting data at index" + cursor.getLong(0));
         return;
      }
      
      try {
         os.write(jsonBytes);
      } catch (IOException e) {
         Log.e("PodcastPlayer", "Error writing to file");
         return;
      }
      
      cursor.close();
	}
   
   static public void importJSON(SQLiteDatabase db, Context context) {
      String exportedDataFolderName;
      
      if (Integer.valueOf(android.os.Build.VERSION.SDK) >= 8) {
         exportedDataFolderName =  context.getExternalFilesDir(null) + "/export";
      } else { // Android 2.1 compatibility
         String packageName;
         
         try {
            packageName = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA).applicationInfo.packageName;
         } catch (NameNotFoundException e) {
            packageName = "me.macron.podcastplayer";
         }
         
         
         exportedDataFolderName = Environment.getExternalStorageDirectory() + "/Android/data/" + packageName + "/files/export";
         
      }
      
      File file = new File(exportedDataFolderName + "/podcasts.json");
      if (!file.exists()) {
         return;
      }
      
      InputStream is;
      
      try {
         is = new FileInputStream(file);
      } catch (FileNotFoundException e) {
         Log.e("PodcastPlayer", "Backup file not found");
         return;
      }

      byte[] jsonBytes = new byte[4096];
      StringBuilder jsonBuilder = new StringBuilder();
      
      try {
         while (is.read(jsonBytes) != -1) {
            jsonBuilder.append(jsonBytes);
         }
      } catch (IOException e) {
         Log.e("PodcastPlayer", "Error reading backup file");
         return;
      }
      
      JSONObject jPodcasts;
      
      try {
         jPodcasts = new JSONObject(jsonBuilder.toString());   
      } catch (JSONException e) {
         Log.e("PodcastPlayer", e.toString());
         e.printStackTrace();
         return;
      }
   }
}