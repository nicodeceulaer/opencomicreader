package com.sketchpunk.ocomicreader.lib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Callable;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import sage.io.Path;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.ThumbnailService;
import com.sketchpunk.ocomicreader.lib.ComicLibrary.ComicFindFilter;

public class LibrarySync implements Runnable {
	private Context mContext;
	private final String mCachePath; // Thumbnail path.
	private final String mSyncFld1; // First Sync Folder Setting
	private final String mSyncFld2; // Second Sync Folder Setting
	private boolean mIncImgFlds = false; // When crawling for comic files, Include folders that have images in them.
	private final boolean mSkipCrawl = false; // Skip finding new comics, just process the current library.
	private boolean mUseFldForSeries = false; // User can choose to force series name from the parent folder name instead of using the series parser.
	private RuntimeExceptionDao<Comic, Integer> comicDao = null;
	private SeriesParser sParser = null;

	public LibrarySync(Context context) {
		mContext = context;
		mCachePath = ComicLibrary.getThumbCachePath();

		// Get sync preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		mSyncFld1 = prefs.getString("syncfolder1", "");
		mSyncFld2 = prefs.getString("syncfolder2", "");
		mIncImgFlds = prefs.getBoolean("syncImgFlds", false);
		mUseFldForSeries = prefs.getBoolean("syncFldForSeries", false);

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
		long longopstart = System.nanoTime();
		File file;
		String comicPath;
		Integer comicID;
		iComicArchive archive;

		List<Comic> allComics = comicDao.queryForAll();
		List<Comic> comicsToDelete = new ArrayList<Comic>();

		for (Comic comic : allComics) {
			comicID = comic.getId();
			comicPath = comic.getPath();
			file = new File(comicPath);

			// .........................................
			// if file does not exist, remove from library.
			if (!file.exists()) {
				sendProgress(mContext.getString(R.string.deleting_comic) + comic.getTitle() + " " + comic.getIssue());
				comicsToDelete.add(comic);

				continue;
			}// if

			if (!coverExists(comic)) {
				createThumbnailInBackground(comicPath, comic.getId());
			}

			if (isLackingData(comic)) {
				archive = ComicLoader.getArchiveInstance(comicPath);
				if (archive == null) {
					Log.e("archive", String.format("Archive %1$s couldn't be read. Possibly wrong format or broken file.", comicPath));
					// TODO: if the file format is wrong, but file itself is alright maybe we could read correct format from file metadata?
				}

				if (determinePageCount(comic, archive) == 0) {
					comicsToDelete.add(comic);
				} else {
					generateSeriesData(comic, archive);
				}
			}
		}// for

		// .........................................
		// if there is a list of items to delete, do it now in one swoop.
		if (comicsToDelete.size() > 0) {
			sendProgress(mContext.getString(R.string.cleaning_library));
			for (Comic comicToDelete : comicsToDelete) {
				deleteThumbnail(comicToDelete);
			}
			comicDao.delete(comicsToDelete);
		}// if

		long longopend = System.nanoTime();
		Log.d("speed", "Library processed in " + ((longopend - longopstart) / 1000000000.0));
	}// func

	private boolean isLackingData(Comic comic) {
		return isSeriesLacking(comic) || isPagesZero(comic);
	}

	private boolean isPagesZero(Comic comic) {
		return comic.getPageCount() == 0;
	}

	private boolean isSeriesLacking(Comic comic) {
		return comic.getSeries() == null || comic.getSeries().equals(ComicLibrary.UKNOWN_SERIES) || comic.getSeries().equals("");
	}

	private void deleteThumbnail(Comic comicToDelete) {
		File file = new File(mCachePath + comicToDelete.getId() + ".jpg");
		if (file.exists() && !file.isDirectory()) {
			file.delete();
		}
	}

	private boolean coverExists(Comic comic) {
		File file = new File(mCachePath + comic.getId() + ".jpg");
		return file.exists();
	}

	private void createThumbnailInBackground(String comicPath, int comicId) {
		Intent mServiceIntent = new Intent(mContext, ThumbnailService.class);
		mServiceIntent.putExtra("comicPath", comicPath);
		mServiceIntent.putExtra("comicId", comicId);
		((Activity) mContext).startService(mServiceIntent);
	}

	private int determinePageCount(Comic comic, iComicArchive archive) {
		if (isPagesZero(comic)) {
			String comicPath = comic.getPath();
			String[] comicInfo = { "", "", "" };// Page Count,Path to Cover Entry,Path to Meta Data

			sendProgress(mContext.getString(R.string.analyzing_comic) + comicPath);
			archive.getLibraryData(comicInfo);

			comic.setPageCount(Integer.parseInt(comicInfo[0]));
		}

		return comic.getPageCount();
	}

	private void generateSeriesData(Comic comic, iComicArchive archive) {
		if (isSeriesLacking(comic)) {
			if (getSeriesDataFromMeta(comic, archive)) {
				return;
			}
			getSeriesDataFromPath(comic);
		}
	}

	private void getSeriesDataFromPath(Comic comic) {
		String comicPath = comic.getPath();
		String seriesName = comic.getSeries();
		int seriesIssue = 0;
		if (seriesName == null || seriesName.isEmpty() || seriesName.compareToIgnoreCase(ComicLibrary.UKNOWN_SERIES) == 0) {
			if (mUseFldForSeries)
				seriesName = Path.getParentName(comicPath);
			else {
				if (sParser == null) {
					sParser = new SeriesParser(); // JIT
				}
				seriesName = sParser.getSeriesName(comicPath.replaceAll("'", ""));

				// if seriesName ends up being the path, use the parent folder as the series name.
				if (seriesName.contains("/")) {
					seriesName = sParser.getSeriesName(comicPath.replaceAll("'", "") + ".xxx"); // Parsers rely on 3 char extension being there.
					if (seriesName.contains("/")) {
						seriesName = Path.getParentName(comicPath);
					} else {
						seriesIssue = sParser.getSeriesIssue(comicPath.replaceAll("'", "") + ".xxx");
					}
				} else {
					seriesIssue = sParser.getSeriesIssue(comicPath.replaceAll("'", ""));
				}
			}// if

			if (!seriesName.isEmpty()) {
				comic.setSeries(seriesName);
				comic.setIssue(seriesIssue);
				comicDao.update(comic);
			}
		}// if
	}

	private boolean getSeriesDataFromMeta(Comic comic, iComicArchive archive) {
		String[] comicMeta; // Title,Series,Volume,Issue
		comicMeta = archive.getMeta(); // Title,Series,Volume,Issue
		boolean comicChanged = false;
		if (comicMeta != null) {
			if (!comicMeta[0].isEmpty()) {
				comic.setTitle(comicMeta[0]);
				comicChanged = true;
			}
			if (!comicMeta[1].isEmpty()) {
				comic.setSeries(comicMeta[1]);
				comicChanged = true;
			}
			if (!comicMeta[3].isEmpty()) {
				comic.setIssue(Integer.parseInt(comicMeta[3]));
				comicChanged = true;
			} else if (!comicMeta[2].isEmpty()) {
				comic.setIssue(Integer.parseInt(comicMeta[2]));
				comicChanged = true;
			}
		}// if}

		// Save information to the db.
		if (comicChanged) {
			comicDao.update(comic);
		}
		return comicChanged;
	}

}// cls
