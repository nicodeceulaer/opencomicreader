package com.sketchpunk.ocomicreader.lib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Locale;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;

public class ComicLibrary {

	private static Thread mWorkerThread = null;
	private static RuntimeExceptionDao<Comic, Integer> comicDao = null;

	/*
	 * ======================================================== status constants
	 */
	public final static int STATUS_NOSETTINGS = 1;
	public final static int STATUS_COMPLETE = 0;
	public final static String UKNOWN_SERIES = "-unknown-";

	/*
	 * ======================================================== Thread safe messaging
	 */
	// Object used to handle threadsafe call backs
	public static Handler EventHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			ComicLibrary.onHandle(msg);
		}
	};

	// Execute the requested Call back.
	public static void onHandle(Message msg) {
		SyncCallback cb = (SyncCallback) msg.obj;
		Bundle data = msg.getData();

		switch (msg.what) {
		// ...............................
		case 1: // progress
			if (cb != null)
				cb.onSyncProgress(data.getString("msg"));
			break;

		// ...............................
		case 0: // complete
			if (cb != null)
				cb.onSyncComplete(data.getInt("status"));
			mWorkerThread = null;
			break;
		}// switch
	}// func

	/*
	 * ======================================================== sync methods
	 */
	public static boolean startSync(Context context) {
		// ...............................
		if (mWorkerThread != null) {
			if (mWorkerThread.isAlive())
				return false;
		}// func

		// ...............................
		mWorkerThread = new Thread(new LibrarySync(context));
		mWorkerThread.start();
		return true;
	}// func

	/*
	 * ======================================================== Static Methods
	 */
	public static String getThumbCachePath() {
		// ........................................
		// Make sure the cache folder exists.
		String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/OpenComicReader/thumbs/";
		File file = new File(path);
		if (!file.exists())
			file.mkdirs();

		// ........................................
		// Create nomedia file so thumbs aren't indexed for gallery
		file = new File(path, ".nomedia");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (Exception e) {
			}
		}// if

		return path;
	}// func

	/*
	 * ======================================================== Manage Comics
	 */
	public static void removeComic(Context context, Integer id, boolean delComic) {
		getComicDao(context);

		Comic comicToDelete = comicDao.queryForId(id);

		File file;
		if (comicToDelete == null) {
			return;
		}

		if (delComic) { // Delete comic from device
			if (comicToDelete.getPath() != null && !comicToDelete.getPath().isEmpty()) {
				file = new File(comicToDelete.getPath());
				if (file.exists())
					file.delete();
			}// if
		}// if

		// Delete comic from library
		if (comicDao.delete(comicToDelete) > 0) {
			file = new File(String.format("%1$s/OpenComicReader/thumbs/%2$s.jpg", Environment.getExternalStorageDirectory().getAbsolutePath(), id));
			if (file.exists())
				file.delete();
		}// if

		OpenHelperManager.releaseHelper();

	}// func

	public static void setComicProgress(Context context, Integer id, int state) {
		getComicDao(context);

		Comic comicToUpdate = comicDao.queryForId(id);
		if (state == 0) { // 0-Reset or 1-Mark as Read.
			comicToUpdate.setPageCurrent(0);
			comicToUpdate.setPageRead(0);
		} else {
			comicToUpdate.setPageCurrent(comicToUpdate.getPageCount());
			comicToUpdate.setPageRead(comicToUpdate.getPageCount());
		}
		comicDao.update(comicToUpdate);

		OpenHelperManager.releaseHelper();
	}// func

	public static void setSeriesProgress(Context context, Integer id, int state) {
		getComicDao(context);

		Comic comicFromSeries = comicDao.queryForId(id);

		try {
			UpdateBuilder<Comic, Integer> comicsUpdateQuery = comicDao.updateBuilder();
			if (state == 0) { // 0-Reset or 1-Mark as Read.
				comicsUpdateQuery.updateColumnValue("pageCurrent", 0);
				comicsUpdateQuery.updateColumnValue("pageRead", 0);
			} else {
				comicsUpdateQuery.updateColumnExpression("pageCurrent", "pageCount");
				comicsUpdateQuery.updateColumnExpression("pageRead", "pageCount");
			}
			comicsUpdateQuery.where().eq("series", comicFromSeries.getSeries());
			comicsUpdateQuery.update();
		} catch (SQLException e) {
			Log.e("sql", e.getLocalizedMessage());
		}

		OpenHelperManager.releaseHelper();
	}// func

	public static boolean clearAll(Context context) {
		// ................................................
		// Delete thumbnails
		String cachePath = getThumbCachePath();

		File fObj = new File(cachePath);
		File[] fList = fObj.listFiles(new ThumbFindFilter());
		if (fList != null) {
			for (File file : fList)
				file.delete();
		}// if

		// ................................................
		getComicDao(context);

		try {
			DeleteBuilder<Comic, Integer> deleteBuilder = comicDao.deleteBuilder();
			deleteBuilder.delete();
		} catch (SQLException e) {
			Log.e("sql", e.getLocalizedMessage());
		}
		OpenHelperManager.releaseHelper();
		return true;
	}// func

	/*
	 * ======================================================== Manage Covers
	 */
	public static boolean createThumb(int coverHeight, int coverQuality, iComicArchive archive, String coverPath, String saveTo) {
		boolean rtn = false;
		InputStream iStream = archive.getItemInputStream(coverPath);

		if (iStream != null) {
			Bitmap bmp = null;
			try {
				// ....................................
				// Get file dimension
				BitmapFactory.Options bmpOption = new BitmapFactory.Options();
				bmpOption.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(iStream, null, bmpOption);

				// calc scale
				int scale = (bmpOption.outHeight > coverHeight) ? Math.round((float) bmpOption.outHeight / coverHeight) : 0;
				bmpOption.inSampleSize = scale;
				bmpOption.inJustDecodeBounds = false;

				// ....................................
				// Load bitmap and rescale
				iStream.close(); // the first read should of closed the stream. Just do it just incase it didn't
				iStream = archive.getItemInputStream(coverPath);
				bmp = BitmapFactory.decodeStream(iStream, null, bmpOption);

				// ....................................
				// Save bitmap to file
				File file = new File(saveTo);
				FileOutputStream out = new FileOutputStream(file);
				bmp.compress(Bitmap.CompressFormat.JPEG, coverQuality, out);

				// ....................................
				out.close();
				bmp.recycle();
				bmp = null;

				rtn = true;
			} catch (Exception e) {
				System.err.println("Error creating thumb " + e.getMessage());
				if (bmp != null) {
					bmp.recycle();
					bmp = null;
				}// if
			}// try

			if (iStream != null) {
				try {
					iStream.close();
				} catch (Exception e) {
				}
			}// if
		}// if

		return rtn;
	}// func

	public static void clearCovers(Context context) {
		// ................................................
		// Delete thumbnails
		String cachePath = getThumbCachePath();

		File fObj = new File(cachePath);
		File[] fList = fObj.listFiles(new ThumbFindFilter());
		if (fList != null) {
			for (File file : fList)
				file.delete();
		}// if

		// ................................................
		sage.data.Sqlite.execSql(context, "UPDATE ComicLibrary SET isCoverExists=0", null);
	}// func

	/*
	 * ======================================================== Manage Series
	 */
	public static void setSeriesName(Context context, String comicID, String seriesName) {
		ContentValues cv = new ContentValues();
		cv.put("series", seriesName);
		sage.data.Sqlite.update(context, "ComicLibrary", cv, "comicID=?", new String[] { comicID });
	}// func

	public static void renameSeries(Context context, String oldSeries, String newSeries) {
		ContentValues cv = new ContentValues();
		cv.put("series", newSeries);
		sage.data.Sqlite.update(context, "ComicLibrary", cv, "series=?", new String[] { oldSeries });
	}// func

	public static void clearSeries(Context context) {
		ContentValues cv = new ContentValues();
		cv.put("series", ComicLibrary.UKNOWN_SERIES);
		sage.data.Sqlite.update(context, "ComicLibrary", cv, null, null);
	}// func

	// ************************************************************
	// Support Objects
	// ************************************************************
	public static interface SyncCallback {
		public void onSyncProgress(String txt);

		public void onSyncComplete(int status);
	}// interface

	protected static class ComicFindFilter implements java.io.FileFilter {
		private final String[] mExtList = new String[] { ".zip", ".cbz", ".rar", ".cbr" };

		@Override
		public boolean accept(File o) {
			if (o.isDirectory())
				return true; // Want to allow folders
			for (String extension : mExtList) {
				if (o.getName().toLowerCase(Locale.getDefault()).endsWith(extension))
					return true;
			}// for
			return false;
		}// func
	}// cls

	protected static class ThumbFindFilter implements java.io.FileFilter {
		@Override
		public boolean accept(File o) {
			if (o.isDirectory())
				return false;
			else if (o.getName().toLowerCase(Locale.getDefault()).endsWith(".jpg"))
				return true;
			return false;
		}// func
	}// cls

	private static void getComicDao(Context context) {
		if (comicDao == null) {
			DatabaseHelper databaseHelper = OpenHelperManager.getHelper(context, DatabaseHelper.class);
			comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
		}
	}

}// cls
