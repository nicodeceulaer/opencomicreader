package com.sketchpunk.ocomicreader.lib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Callable;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.sketchpunk.ocomicreader.lib.ComicLibrary.ComicFindFilter;

public class LibrarySync implements Runnable {
	private Context mContext;
	private final String mCachePath; // Thumbnail path.
	private final String mSyncFld1; // First Sync Folder Setting
	private final String mSyncFld2; // Second Sync Folder Setting
	private boolean mIncImgFlds = false; // When crawling for comic files, Include folders that have images in them.
	private final boolean mSkipCrawl = false; // Skip finding new comics, just process the current library.
	private boolean mUseFldForSeries = false; // User can choose to force series name from the parent folder name instead of using the series parser.
	private final int mCoverHeight, mCoverQuality;
	private RuntimeExceptionDao<Comic, Integer> comicDao = null;

	public LibrarySync(Context context) {
		mContext = context;
		mCachePath = ComicLibrary.getThumbCachePath();

		// Get sync preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		mSyncFld1 = prefs.getString("syncfolder1", "");
		mSyncFld2 = prefs.getString("syncfolder2", "");
		mIncImgFlds = prefs.getBoolean("syncImgFlds", false);
		mUseFldForSeries = prefs.getBoolean("syncFldForSeries", false);

		mCoverHeight = prefs.getInt("syncCoverHeight", 300);
		mCoverQuality = prefs.getInt("syncCoverQuality", 70);

	}// func

	@Override
	public void run() {
		// Check if folders have been setup for sync.
		if (mSyncFld1.isEmpty() && mSyncFld2.isEmpty()) {
			sendComplete(ComicLibrary.STATUS_NOSETTINGS);
			return;
		}// if

		// .....................................
		DatabaseHelper databaseHelper = OpenHelperManager.getHelper(mContext, DatabaseHelper.class);
		comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
		try {
			if (!mSkipCrawl) {
				TransactionManager.callInTransaction(comicDao.getConnectionSource(), new Callable<Void>() {

					@Override
					public Void call() throws Exception {
						crawlComicFiles();
						if (mIncImgFlds) {
							crawlComicFolders();
						}
						return null;
					}
				});
			}

			processLibrary();
		} catch (Exception e) {
			System.err.println("Sync " + e.getMessage());
			e.printStackTrace();
		}

		// .....................................
		// Complete
		OpenHelperManager.releaseHelper();

		sendComplete(ComicLibrary.STATUS_COMPLETE);
		mContext = null;
	}// func

	/*
	 * ======================================================== Send thread safe messages
	 */
	private void sendProgress(String txt) {
		Bundle rtn = new Bundle();
		rtn.putString("msg", txt);

		Message msg = new Message();
		msg.what = 1;
		msg.obj = mContext;
		msg.setData(rtn);

		ComicLibrary.EventHandler.sendMessage(msg);
	}// func

	private void sendComplete(int status) {
		Bundle rtn = new Bundle();
		rtn.putInt("status", status);

		Message msg = new Message();
		msg.what = 0;
		msg.obj = mContext;
		msg.setData(rtn);

		ComicLibrary.EventHandler.sendMessageDelayed(msg, 200);
	}// func

	/*
	 * ======================================================== Finding File/Folders to save into the database.
	 */
	public int crawlComicFolders() {
		// ....................................
		// Setup Variables
		Activity activity = (Activity) mContext;
		String[] cols = new String[] { MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN,
				MediaStore.MediaColumns.DATA };

		// ....................................
		// Determine the where clause for the media query
		String[] sWhereAry;
		String sWhere;

		if (!mSyncFld1.isEmpty() && !mSyncFld2.isEmpty()) {
			sWhereAry = new String[] { mSyncFld1 + "%", mSyncFld2 + "%" };
			sWhere = MediaStore.Images.Media.DATA + " like ? OR " + MediaStore.Images.Media.DATA + " like ?) GROUP BY (2";
		} else if (!mSyncFld1.isEmpty()) {
			sWhereAry = new String[] { mSyncFld1 + "%" };
			sWhere = MediaStore.Images.Media.DATA + " like ?) GROUP BY (2";
		} else if (!mSyncFld2.isEmpty()) {
			sWhereAry = new String[] { mSyncFld2 + "%" };
			sWhere = MediaStore.Images.Media.DATA + " like ?) GROUP BY (2";
		} else {
			return 0;
		}// if

		// ....................................
		// Query both External and Internal then merge results
		Cursor[] aryCur = new Cursor[2];
		Cursor eCur = activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, sWhere, sWhereAry, null); // "MAX(datetaken) DESC"
		Cursor iCur = activity.getContentResolver().query(MediaStore.Images.Media.INTERNAL_CONTENT_URI, cols, sWhere, sWhereAry, null);

		eCur.moveToFirst();
		aryCur[0] = eCur;
		iCur.moveToFirst();
		aryCur[1] = iCur;
		MergeCursor mcur = new MergeCursor(aryCur);

		// Loop through cursor
		if (mcur.moveToFirst()) {
			int dCol = mcur.getColumnIndex(MediaStore.MediaColumns.DATA); // .getColumnIndex(MediaStore.MediaColumns.DATA);
			String path = "";
			List<Comic> tmp;

			do {
				// ------------------------------
				// Check if already in library
				path = sage.io.Path.removeLast(mcur.getString(dCol)); // Remove Filename from the path
				sendProgress(path);

				tmp = comicDao.queryForEq("path", path.replace("'", "''"));

				if (tmp.isEmpty()) {
					saveNewComic(sage.io.Path.getLast(path), path);
				}
			} while (mcur.moveToNext());

			// ............................................
		}// if

		mcur.close();
		return ComicLibrary.STATUS_COMPLETE;
	}// func

	public int crawlComicFiles() {
		// Crawls the Two paths in settings for comic archive files.
		ComicFindFilter filter = new ComicFindFilter();
		Stack<String> stack = new Stack<String>();
		File[] fList;
		File fObj;
		List<Comic> tmp;
		String path;

		// ............................................
		// Set initial paths
		if (!mSyncFld1.isEmpty())
			stack.push(mSyncFld1);
		if (!mSyncFld2.isEmpty())
			stack.push(mSyncFld2);
		if (stack.size() == 0)
			return ComicLibrary.STATUS_NOSETTINGS;

		while (!stack.isEmpty()) {
			// get list and do some validation
			fObj = new File(stack.pop());
			if (!fObj.exists())
				continue;
			fList = fObj.listFiles(filter);
			if (fList == null)
				continue;

			// files/dir found.
			for (File file : fList) {
				if (file.isDirectory())
					stack.push(file.getPath()); // add to stack to continue to crawl.
				else {
					// ------------------------------
					// Check if already in library
					sendProgress(file.getName());
					path = file.getPath();

					tmp = comicDao.queryForEq("path", path.replace("'", "''"));
					if (tmp.isEmpty()) {
						// ------------------------------
						// Not found, add it to library.

						saveNewComic(sage.io.Path.removeExt(file.getName()), path);

					}
					//
				}// if
			}// for
		}// while

		// ............................................

		return ComicLibrary.STATUS_COMPLETE;
	}// func

	private void saveNewComic(String title, String path) {
		Comic comicToSave = new Comic();
		comicToSave.setTitle(title);
		comicToSave.setPath(path);
		comicToSave.setSeries(ComicLibrary.UKNOWN_SERIES);
		comicDao.create(comicToSave);
	}

	/*
	 * ======================================================== Process : Create Thumbs, GetPage Count, Remove not found file
	 */
	private void processLibrary() {
		String[] comicInfo = { "", "", "" };// Page Count,Path to Cover Entry,Path to Meta Data
		String[] comicMeta; // Title,Series,Volume,Issue
		File file;
		String comicPath, seriesName;
		Integer comicID;
		iComicArchive archive;

		List<Comic> allComics = comicDao.queryForAll();
		List<Comic> comicsToDelete = new ArrayList<Comic>();
		SeriesParser sParser = null;

		for (Comic comic : allComics) {
			comicID = comic.getId();
			comicPath = comic.getPath();
			file = new File(comicPath);

			// .........................................
			// if file does not exist, remove from library.
			if (!file.exists()) {
				sendProgress("Removing reference to " + comic.getTitle());
				comicsToDelete.add(comic);

				try { // delete thumbnail if available
					file = new File(mCachePath + comicID + ".jpg");
					if (file.exists())
						file.delete();
				} catch (Exception e) {
				}

				continue;
			}// if

			// .........................................
			// if thumb has not been generated.
			if (!comic.isCoverExists()) {
				sendProgress("Creating thumbnail for " + comicPath);
				archive = ComicLoader.getArchiveInstance(comicPath);
				archive.getLibraryData(comicInfo);

				comic.setPageCount(Integer.parseInt(comicInfo[0]));

				// No images in archive, then delete
				if (comic.getPageCount() == 0) {
					comicsToDelete.add(comic);
					continue;
				}// if

				// Create ThumbNail
				if (ComicLibrary.createThumb(mCoverHeight, mCoverQuality, archive, comicInfo[1], mCachePath + comicID + ".jpg")) {
					comic.setCoverExists(true);
				}

				// Get Meta Information
				comicMeta = archive.getMeta(); // Title,Series,Volume,Issue
				if (comicMeta != null) {
					if (!comicMeta[0].isEmpty()) {
						comic.setTitle(comicMeta[0].replaceAll("'", "''"));
					}
					if (!comicMeta[1].isEmpty()) {
						comic.setSeries(comicMeta[1].replaceAll("'", "''"));
					}
				}// if}

				// Save information to the db.
				comicDao.update(comic);
				if (comicMeta != null && comicMeta[1] != "")
					continue; // Since series was updated from meta, Don't continue the rest of the loop which handles the series
			}// if

			// .........................................
			// if series does not exist, create a series name.
			seriesName = comic.getTitle();
			if (seriesName == null || seriesName.isEmpty() || seriesName.compareToIgnoreCase(ComicLibrary.UKNOWN_SERIES) == 0) {
				if (mUseFldForSeries)
					seriesName = sage.io.Path.getParentName(comicPath);
				else {
					if (sParser == null)
						sParser = new SeriesParser(); // JIT
					seriesName = sParser.get(comicPath);

					// if seriesName ends up being the path, use the parent folder as the series name.
					if (seriesName == comicPath)
						seriesName = sage.io.Path.getParentName(comicPath);
				}// if

				if (!seriesName.isEmpty()) {
					comic.setSeries(seriesName.replace("'", "''"));
					comicDao.update(comic);
				}
			}// if
		}// for

		// .........................................
		// if there is a list of items to delete, do it now in one swoop.
		if (comicsToDelete.size() > 0) {
			sendProgress("Cleaning up library...");
			comicDao.delete(comicsToDelete);
		}// if
	}// func

}// cls
