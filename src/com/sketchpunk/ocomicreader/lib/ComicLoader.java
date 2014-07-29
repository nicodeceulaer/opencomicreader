package com.sketchpunk.ocomicreader.lib;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.opengles.GL10;

import sage.io.DiskCache;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES10;
import android.os.AsyncTask;
import android.widget.Toast;

import com.sketchpunk.ocomicreader.ui.GestureImageView;

public class ComicLoader implements PageLoader.CallBack {// LoadImageView.OnImageLoadingListener,LoadImageView.OnImageLoadedListener{
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
		o.setFileNameComparator(Strings.getNaturalComparator(false));
		if (o.loadFile(path)) {
			return o;
		}
		return null;
	}

	/*--------------------------------------------------------
	 */
	private static int CACHE_SIZE = 1024 * 1024 * 10; // 10mb

	private int mPageLen, mCurrentPage;

	private final int mMaxSize;
	private final int mPreloadSize = 2;
	private ComicLoaderListener mListener;

	private GestureImageView mImageView;
	private final PageLoader mPageLoader;
	private iComicArchive mArchive;
	private List<String> mPageList;
	private Context mContext = null;
	private final DiskCache mCache;
	private CacheLoader mCacheLoader = null;

	public ComicLoader(Context context, GestureImageView o) {
		mImageView = o;
		mContext = context;
		mCache = new DiskCache(context, "comicLoader", CACHE_SIZE);

		// ............................
		// Save Callback
		if (context instanceof ComicLoaderListener)
			mListener = (ComicLoaderListener) context;

		// ............................
		mPageLoader = new PageLoader(this);
		mCurrentPage = -1;

		// TODO: Save this to settings, shouldn't have to get this value every
		// time.
		android.opengl.GLSurfaceView mGLView = new android.opengl.GLSurfaceView(context);
		int[] maxTextureSize = new int[1];
		GLES10.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);

		mMaxSize = maxTextureSize[0]; // MaxTextureSize
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
			mPageLoader.close(); // cancel any tasks that may be running.

			if (mArchive != null) {
				mArchive.close();
				mArchive = null;
			}// if

			mListener = null;
			mImageView = null;

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

	/*--------------------------------------------------------
	Loading*/
	private void preloadNext() {
		String pgPath;
		for (int i = 1; i <= mPreloadSize; i++) {
			if (mCurrentPage + i >= mPageLen)
				break; // do not preload over the page limit.

			pgPath = mPageList.get(mCurrentPage + i);
			if (!mCache.contrainsKey(pgPath)) { // Preload next page if
												// available.
				System.out.println("Next Page is not cached " + Integer.toString(i));
				mPageLoader.loadImage(pgPath, mMaxSize, mArchive, 0);
				break;
			}// if
		}// for
	}// func

	private void loadToImageView(Bitmap bmp) {
		mImageView.setImageBitmap(bmp);
		if (mListener != null)
			mListener.onPageLoaded((bmp != null), mCurrentPage);
	}// func

	/*--------------------------------------------------------
	Page Loader Event, Getting images out of the archive.*/
	@Override
	public void onImageLoaded(String errMsg, Bitmap bmp, String imgPath, int imgType) {
		if (errMsg != null) {
			Toast.makeText(mContext, errMsg, Toast.LENGTH_LONG).show();
		}// if

		// ............................................
		// if we have a new image and an old image.
		if (bmp != null) {
			// Load Image Right Away.
			if (imgType == 1)
				loadToImageView(bmp);

			mCache.putBitmap(imgPath, bmp);
			if (imgType == 0) {
				bmp.recycle();
				bmp = null;
			} // No need to load right away, clear out memory.

			bmp = null;
			preloadNext(); // Check if we can preload the next page
		}// if
	}// func

	/*--------------------------------------------------------
	Task to load images out of the cache folder.*/
	protected class CacheLoader extends AsyncTask<Integer, Void, Bitmap> {
		@Override
		protected Bitmap doInBackground(Integer... params) {
			int pgIndex = params[0].intValue();

			String pgPath = mPageList.get(mCurrentPage);
			Bitmap bmp = mCache.getBitmap(pgPath);

			if (bmp == null) { // Not in cache, Call Page Loader
				mPageLoader.loadImage(pgPath, mMaxSize, mArchive, 1);
			} else {// Pass Image to View and check preloading the next image.
				preloadNext();
				return bmp;
			}// if

			return null;
		}// func

		@Override
		protected void onPostExecute(Bitmap bmp) {
			if (bmp != null) {
				loadToImageView(bmp);
				bmp = null;
			}
		}// func
	}// cls

}// cls
