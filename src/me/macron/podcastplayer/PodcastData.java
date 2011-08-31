package me.macron.podcastplayer;

import static android.provider.BaseColumns._ID;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class PodcastData extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "podcasts.db";
	private static final int DATABASE_VERSION = 9;
	
   public static final String PODCASTS_TABLE_NAME = "podcasts";
   
   // podcasts database columns
   public static final String GENRE = "genre";
   public static final String ARTIST = "artist";
   public static final String ALBUM = "album";
   public static final String NAME = "name";
   public static final String SIZE = "size";
   public static final String DATE_MODIFIED = "date_modified";
   public static final String DATE_ADDED = "date_added";
   public static final String LOCATION = "location";
   public static final String PLAYBACK_POSITION = "playback_position";
   public static final String DURATION = "duration";
   public static final String UNPLAYED = "unplayed";
   public static final String THUMBNAIL = "thumbnail";
   public static final String THUMBNAIL_MIME = "thumbnail_mime";
   public static final String EPISODE_COUNT = "episode_count";
   public static final String LAST_PLAYED = "last_played";
   public static final String LAST_SCAN = "last_scan";
   public static final String INDEXABLE = "indexable";
   public static final String LAST_UPDATED = "last_updated";
   public static final String SHORT_DESCRIPTION = "short_description";
   public static final String DESCRIPTION = "description";
   
   public static final String GENRES_TABLE_NAME = "genres";
   public static final String ARTISTS_TABLE_NAME = "artists";
   public static final String ALBUMS_TABLE_NAME = "albums";
   public static final String HISTORY_TABLE_NAME = "history";
   public static final String FOLDERS_TABLE_NAME = "folders";
   
	public PodcastData(Context ctx) {
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	private void populateTables(SQLiteDatabase db) {
      ContentValues values = new ContentValues();

      // History table
      values.put(LAST_PLAYED, -1);
      db.insertOrThrow(HISTORY_TABLE_NAME, null, values);

      // Folders table
      values.clear();
      values.put(LOCATION, "/sdcard/Music");
      db.insertOrThrow(FOLDERS_TABLE_NAME, null, values);

      values.clear();
      values.put(LOCATION, "/sdcard/Podcasts");
      db.insertOrThrow(FOLDERS_TABLE_NAME, null, values);

      // Genres table
      values.clear();
      values.put(NAME, "Podcast");
      values.put(INDEXABLE, "1");
      db.insertOrThrow(GENRES_TABLE_NAME, null, values);
      
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + PODCASTS_TABLE_NAME + "(" 
				+ _ID + " INTEGER PRIMARY KEY AUTOINCREMENT"
				+ "," + GENRE + " NUMERIC"
				+ "," + ALBUM + " NUMERIC"
            + "," + ARTIST + " NUMERIC"
				+ "," + NAME + " TEXT NOT NULL"
				+ "," + SIZE + " NUMERIC"
				+ "," + DATE_MODIFIED + " NUMERIC"
				+ "," + DATE_ADDED + " NUMERIC"
				+ "," + LOCATION + " TEXT NOT NULL"
				+ "," + PLAYBACK_POSITION + " NUMERIC default 0"
            + "," + DURATION + " NUMERIC"
				+ "," + UNPLAYED + " NUMERIC"
            + "," + SHORT_DESCRIPTION + " TEXT"
            + "," + DESCRIPTION + " TEXT"
				+ ");");
		
		db.execSQL("CREATE TABLE " + GENRES_TABLE_NAME + "(" 
				+ _ID + " INTEGER PRIMARY KEY AUTOINCREMENT"
				+ "," + NAME + " TEXT NOT NULL"
            + "," + INDEXABLE + " NUMERIC"
			    + ");");
		
		db.execSQL("CREATE TABLE " + ARTISTS_TABLE_NAME + "("
				+ _ID + " INTEGER PRIMARY KEY AUTOINCREMENT"
				+ "," + NAME + " TEXT NOT NULL"
				+ ");");
		
		db.execSQL("CREATE TABLE " + ALBUMS_TABLE_NAME + "("
				+ _ID + " INTEGER PRIMARY KEY AUTOINCREMENT"
				+ "," + NAME + " TEXT NOT NULL"
            + "," + ARTIST + " NUMERIC"
				+ "," + THUMBNAIL + " TEXT"
				+ "," + THUMBNAIL_MIME + " TEXT"
            + "," + EPISODE_COUNT + " NUMERIC default 0"
            + "," + UNPLAYED + " NUMERIC default 0"
            + "," + LAST_UPDATED + " NUMERIC default 0"
				+ ");");

      db.execSQL("CREATE TABLE " + HISTORY_TABLE_NAME + "("
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT"
            + "," + LAST_PLAYED + " NUMERIC"
            + "," + LAST_SCAN + " NUMERIC"
            + ");");

      db.execSQL("CREATE TABLE " + FOLDERS_TABLE_NAME + "("
            + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT"
            + "," + LOCATION + " TEXT NOT NULL"
            + ");");

      populateTables(db);
	
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	   
	   // Todo: Use exportJSON() to save podcast positions and "unplayed" 
	   // state to JSON file then restore them with importJSON() after
	   // recreating the database.
	   // importJSON isn't working right now ...
	   
		db.execSQL("DROP TABLE IF EXISTS " + PODCASTS_TABLE_NAME);
		db.execSQL("DROP TABLE IF EXISTS " + GENRES_TABLE_NAME);
		db.execSQL("DROP TABLE IF EXISTS " + ARTISTS_TABLE_NAME);
		db.execSQL("DROP TABLE IF EXISTS " + ALBUMS_TABLE_NAME);
      db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE_NAME);
      db.execSQL("DROP TABLE IF EXISTS " + FOLDERS_TABLE_NAME);
		onCreate(db);
	}
}
