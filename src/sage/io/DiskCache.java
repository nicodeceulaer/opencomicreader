package sage.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;

//http://developer.android.com/training/displaying-bitmaps/cache-bitmap.html#disk-cache
public class DiskCache {
	private DiskLruCache mCache;
	private static int VALUE_CNT = 1; // the number of values per cache entry. Must be positive.
	private static int VERSION = 1;
	private static int BUFFER_SIZE = 8 * 1024; // 8K

	public DiskCache(Context context, String fldName, long cacheSize) {
		String cachePath;

		// ...........................................
		// External or Internal Storage.
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable())
			cachePath = context.getExternalCacheDir().getPath();
		else
			cachePath = context.getCacheDir().getPath();

		// ...........................................
		try {
			mCache = DiskLruCache.open(new File(cachePath + "/" + fldName), VERSION, VALUE_CNT, cacheSize);
		} catch (IOException e) {
			Log.e("cache", "Error when dealing with cache" + e.getMessage());
		}// try
	}// func

	public long size() {
		return mCache.size();
	}

	public long maxSize() {
		return mCache.getMaxSize();
	}

	public void clear() {
		try {
			mCache.delete();
			Log.d("cache", "ClearCache");
		} catch (IOException e) {
			Log.e("cache", "Error when dealing with cache" + e.getMessage());
		}
	}// func

	public void close() {
		try {
			mCache.close();
		} catch (IOException e) {
			Log.e("cache", "Error when dealing with cache" + e.getMessage());
		}
	}// func

	public boolean putBitmap(String key, Bitmap bmp) {
		DiskLruCache.Editor editor = null;
		OutputStream oStream = null;
		boolean isOk = false;
		Log.d("cache", "PUTBITMAP " + key);
		Log.d("cache", hashKey(key));
		try {
			editor = mCache.edit(hashKey(key));
			if (editor == null)
				return false;

			// Push the image file to the cache
			oStream = new BufferedOutputStream(editor.newOutputStream(0), BUFFER_SIZE);
			isOk = bmp.compress(CompressFormat.JPEG, 70, oStream);
			oStream.close();
			oStream = null;
			//
			if (isOk) {
				editor.commit();
				mCache.flush();
			} else {
				editor.abort();
			}
			editor = null;
		} catch (IOException e) {
			Log.e("cache", "Error when dealing with cache" + e.getMessage());
		} finally {
			if (oStream != null) {
				try {
					oStream.close();
				} catch (IOException e) {
					Log.e("cache", "Error when dealing with cache" + e.getMessage());
				}
			}// if

			if (editor != null) {
				try {
					editor.abort();
				} catch (IOException e) {
					Log.e("cache", "Error when dealing with cache" + e.getMessage());
				}
			}// if
		}// try

		return isOk;
	}// func

	public Bitmap getBitmap(String key) {
		Bitmap bmp = null;
		DiskLruCache.Snapshot ss = null;
		InputStream iStream = null;

		try {
			System.gc(); // This does help, even though this is frowned upon.
			ss = mCache.get(hashKey(key));
			if (ss == null)
				return null;

			iStream = ss.getInputStream(0);
			if (iStream != null) {
				final BufferedInputStream biStream = new BufferedInputStream(iStream, BUFFER_SIZE);
				bmp = BitmapFactory.decodeStream(biStream);
				Log.d("cache", "Loading Bitmap from Cache " + key);
				Log.d("cache", hashKey(key));
			}// if

		} catch (IOException e) {
			Log.e("cache", "Error when dealing with cache" + e.getMessage());
		} catch (OutOfMemoryError e) {
			Log.d("cache", "Out of memory getting bitmap from cache.");
		} finally {
			if (ss != null)
				ss.close();
			if (iStream != null) {
				try {
					iStream.close();
				} catch (IOException e) {
					Log.e("cache", "Error when dealing with cache" + e.getMessage());
				}
			}// if
		}// try

		return bmp;
	}// func

	public boolean contrainsKey(String key) {
		boolean isOk = false;

		DiskLruCache.Snapshot ss = null;
		try {
			ss = mCache.get(hashKey(key));
			isOk = (ss != null);
		} catch (IOException e) {
			Log.e("cache", "Error when dealing with cache" + e.getMessage());
		} finally {
			if (ss != null)
				ss.close();
		}// try

		return isOk;
	}// func

	private String hashKey(String txt) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(txt.getBytes("UTF-8"));
			byte[] digest = md.digest();
			BigInteger bi = new BigInteger(1, digest);

			return bi.toString(16);

		} catch (NoSuchAlgorithmException e) {
			// throw new AssertionError();
		} catch (UnsupportedEncodingException e) {
			// throw new AssertionError();
		}
		return null;
	}// func
}// cls
