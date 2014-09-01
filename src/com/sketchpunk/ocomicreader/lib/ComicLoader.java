package com.sketchpunk.ocomicreader.lib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

import sage.io.DiskCache;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.micabyte.android.graphics.BitmapSurfaceRenderer;
import com.sketchpunk.ocomicreader.OpenGLESTestingActivity;

public class ComicLoader {
	public static interface ComicLoaderListener {
		public void onPageLoaded(boolean isSuccess, int currentPage);
	}// interface

	public static iComicArchive getArchiveInstance(String path) {
		String ext = sage.io.Path.getExt(path).toLowerCase(Locale.getDefault());
		iComicArchive o = null;

		if (ext.equals("zip") || ext.equals("cbz")) {
			o = new ComicZip();
			return loadFile(path, o);
		} else if (ext.equals("rar") || ext.equals("cbr")) {
			o = new ComicRar();
			return loadFile(path, o);
		} else {
			if (new File(path).isDirectory()) {
				o = new ComicFld();
				return loadFile(path, o);
			}// if
		}// if

		return null;
	}// func

	private static iComicArchive loadFile(String path, iComicArchive o) {
		o.setFileNameComparator(Strings.getNaturalComparator(true));
		if (o.loadFile(path)) {
			return o;
		} else {
			return null;
		}
	}

	/*--------------------------------------------------------
	 */
	private static int CACHE_SIZE = 1024 * 1024 * 10; // 10mb

	private int mPageLen, mCurrentPage;

	private int mMaxSize;
	private WeakReference<ComicLoaderListener> mListener;

	private WeakReference<BitmapSurfaceRenderer> imageRenderer;
	private iComicArchive mArchive;
	private List<String> mPageList;
	private final DiskCache mCache;
	private CacheLoader mCacheLoader = null;

	public ComicLoader(Context context, BitmapSurfaceRenderer renderer) {
		imageRenderer = new WeakReference<BitmapSurfaceRenderer>(renderer);
		mCache = new DiskCache(context, "comicLoader", CACHE_SIZE);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		mMaxSize = prefs.getInt("maxTextureSize", 0);

		if (mMaxSize == 0) {
			Intent intent = new Intent(context, OpenGLESTestingActivity.class);
			intent.putExtra("isTest", true);
			context.startActivity(intent);

			mMaxSize = prefs.getInt("maxTextureSize", 2048); // 2048 should be safe for most devices
		}

		// ............................
		// Save Callback
		if (context instanceof ComicLoaderListener)
			mListener = new WeakReference<ComicLoader.ComicLoaderListener>((ComicLoaderListener) context);

		// ............................
		mCurrentPage = -1;

	}// func

	/*--------------------------------------------------------
	Getters*/
	// Since the event has been created, these getters plus the variables won't
	// be needed anymore.
	public int getCurrentPage() {
		return mCurrentPage;
	}

	public int getPageCount() {
		return mPageLen;
	}

	/*--------------------------------------------------------
	Methods*/
	public boolean close() {
		try {
			if (mArchive != null) {
				mArchive.close();
				mArchive = null;
			}// if

			mListener = null;
			imageRenderer = null;

			mCache.clear();
			mCache.close();
			return true;
		} catch (Exception e) {
			System.out.println("Error closing archive " + e.getMessage());
		}// func

		return false;
	}// func

	// Load a list of images in the archive file, need path to stream out the
	// file.
	public boolean loadArchive(String path) {
		try {
			mArchive = ComicLoader.getArchiveInstance(path);
			if (mArchive == null)
				return false;

			// Get page list
			mPageList = mArchive.getPageList();
			if (mPageList != null) {
				mPageLen = mPageList.size();
				return true;
			}// if

			// if non found, then just close the archive.
			mArchive.close();
			mArchive = null;
		} catch (Exception e) {
			System.err.println("LoadArchive " + e.getMessage());
		}// try

		return false;
	}// func

	/*--------------------------------------------------------
	Paging Methods*/
	public int gotoPage(int pos) {
		if (pos < 0 || pos >= mPageLen || pos == mCurrentPage)
			return 0;

		// Check if the cache loader is busy with a request.
		if (mCacheLoader != null && mCacheLoader.getStatus() != AsyncTask.Status.FINISHED) {
			System.out.println("Still Loading from Cache.");
			return -1;
		}// if

		// Load from cache on a thread.
		mCurrentPage = pos;
		mCacheLoader = new CacheLoader();
		mCacheLoader.execute(mCurrentPage);

		return 1;
	}// func

	public int nextPage() {
		if (mCurrentPage >= mPageLen)
			return 0;
		return gotoPage(mCurrentPage + 1);
	}// func

	public int prevPage() {
		if (mCurrentPage - 1 < 0)
			return 0;
		return gotoPage(mCurrentPage - 1);
	}// func

	private void loadToImageView(InputStream is) {
		if (imageRenderer.get() != null) {
			try {
				imageRenderer.get().setBitmap(is);
				imageRenderer.get().zoom(100.0f, new PointF());
				imageRenderer.get().invalidate();
			} catch (IOException e) {
				Log.e("micabyte", "Couldn't set bitmap for image renderer", e);
			}
			if (mListener != null && mListener.get() != null)
				mListener.get().onPageLoaded((is != null), mCurrentPage);
		}
	}// func

	/*--------------------------------------------------------
	Task to load images out of the cache folder.*/
	protected class CacheLoader extends AsyncTask<Integer, Void, InputStream> {
		@Override
		protected InputStream doInBackground(Integer... params) {
			String pgPath = mPageList.get(mCurrentPage);
			return mArchive.getItemInputStream(pgPath);
		}// func

		@Override
		protected void onPostExecute(InputStream inputStream) {
			if (inputStream != null) {
				loadToImageView(inputStream);
				inputStream = null;
			}
		}// func
	}// cls

}// cls
