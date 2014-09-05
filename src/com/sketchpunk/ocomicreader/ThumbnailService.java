package com.sketchpunk.ocomicreader;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.sketchpunk.ocomicreader.lib.ComicLibrary;
import com.sketchpunk.ocomicreader.lib.ComicLoader;
import com.sketchpunk.ocomicreader.lib.iComicArchive;

public class ThumbnailService extends IntentService {

	private int mCoverHeight, mCoverWidth, mCoverQuality;
	private final String mCachePath = ComicLibrary.getThumbCachePath();

	public ThumbnailService() {
		this("ThumbnailService");
	}

	public ThumbnailService(String name) {
		super(name);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ThumbnailService.this);
		mCoverHeight = prefs.getInt("syncCoverHeight", 300);
		mCoverWidth = prefs.getInt("syncCoverWidth", 200);
		mCoverQuality = prefs.getInt("syncCoverQuality", 70);
	}

	@Override
	protected void onHandleIntent(Intent workIntent) {
		String comicPath = workIntent.getStringExtra("comicPath");
		int comicId = workIntent.getIntExtra("comicId", -1);

		if (comicPath != null && !comicPath.equals("") && comicId >= 0) {
			generateThumbnail(comicPath, comicId);
		}
	}

	private void generateThumbnail(String comicPath, int comicId) {
		Log.d("background", "Generating thumbnail for" + comicPath);
		iComicArchive archive = ComicLoader.getArchiveInstance(comicPath);

		String[] comicInfo = { "", "", "" };// Page Count,Path to Cover Entry,Path to Meta Data
		archive.getLibraryData(comicInfo);

		ComicLibrary.createThumb(mCoverHeight, mCoverWidth, mCoverQuality, archive, comicInfo[1], mCachePath + comicId + ".jpg");

		Log.d("background", "Generated thumbnail for" + comicPath);
	}
}