package sage.loader;

import java.io.File;
import java.lang.ref.WeakReference;

import sage.Util;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class LoadImageView {
	public static final int taskTagID = 1357924680;

	public static interface OnImageLoadingListener {
		public Bitmap onImageLoading(String path);
	}// interface

	public static interface OnImageLoadedListener {
		public void onImageLoaded(boolean isSuccess, Bitmap bmp, View view);
	}// interface

	/*
	 * ========================================================
	 */
	// Starting Point, Load the image through a thread
	public static void loadImage(String imgPath, View view, Object context) {
		if (cancelRunningTask(imgPath, view)) {
			final LoadingTask task = new LoadingTask(view, context);
			view.setTag(taskTagID, task);
			task.execute(imgPath);
		}// if
	}// func

	// if a task is already running, cancel it.
	public static boolean cancelRunningTask(String imgPath, View view) {
		final LoadingTask task = getLoadingTask(view);

		if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
			final String taskImgPath = task.imagePath;

			if (task.imagePath != null && taskImgPath.equals(imgPath))
				return false; // Still loading this image.
			else
				task.cancel(true);
		}// if

		return true;
	}// func

	// Get the loading task from the imageview
	public static LoadingTask getLoadingTask(View view) {
		if (view != null) {
			final Object task = view.getTag(taskTagID);
			if (task != null && task instanceof LoadingTask) {
				return (LoadingTask) task;
			}// if
		}// if

		return null;
	}// func

	// ************************************************************
	// Load Image through thread
	// ************************************************************
	protected static class LoadingTask extends AsyncTask<String, Integer, Bitmap> {
		private WeakReference<View> mImgView = null;
		private WeakReference<OnImageLoadingListener> mOnImageLoading = null;
		private WeakReference<OnImageLoadedListener> mOnImageLoaded = null;

		public String imagePath; // used to compare if the same task

		public LoadingTask(View view, Object context) {
			mImgView = new WeakReference<View>(view);

			if (context != null) {
				if (context instanceof OnImageLoadingListener)
					mOnImageLoading = new WeakReference(context);
				if (context instanceof OnImageLoadedListener)
					mOnImageLoaded = new WeakReference(context);
			}// if
		}// func

		@Override
		protected Bitmap doInBackground(String... params) {
			try {
				imagePath = params[0];

				// .....................................
				// If callback exists, then we need to load the image in a special sort of way.
				if (mOnImageLoading != null) {
					final OnImageLoadingListener callback = mOnImageLoading.get();
					return (callback != null) ? callback.onImageLoading(imagePath) : null;
				}// if

				// .....................................
				// if no callback, then load the image ourselves
				File fImg = new File(imagePath);
				if (!fImg.exists()) {
					Log.e("reader", "Thumb File does not exist " + imagePath);
					// TODO: check if the memory card is inserted. If not then use ThumbnailService to recreate the thumbnail.
					return null;
				}

				BitmapFactory.Options bmpOption = new BitmapFactory.Options();

				int viewWidth = 200;
				int viewHeight = 200;

				try {
					bmpOption.inJustDecodeBounds = true;
					BitmapFactory.decodeFile(imagePath, bmpOption);

					if (mImgView != null && mImgView.get() != null) {
						View imgView = mImgView.get();
						if (imgView.getWidth() == 0 || imgView.getHeight() == 0) {
							bmpOption.inSampleSize = 1;
						} else {
							viewWidth = bmpOption.outWidth < imgView.getWidth() ? bmpOption.outWidth : imgView.getWidth();
							viewHeight = bmpOption.outHeight < imgView.getHeight() ? bmpOption.outHeight : imgView.getHeight();
							bmpOption.inSampleSize = Util.calculateInSampleSize(bmpOption, viewWidth, viewHeight);
						}
						Log.d("reader", "sample size for thumb:" + bmpOption.inSampleSize);
					}
				} catch (Exception e) {
					Log.e("reader", "Error Getting Image Size for thumbnail " + e.getMessage());
				}// try

				bmpOption.inJustDecodeBounds = false;

				return BitmapFactory.decodeFile(imagePath, bmpOption);
			} catch (OutOfMemoryError ex) {
				Log.e("memory", "Coudln't load thumbnail file for " + imagePath + " due to OutOfMemoryError " + ex.getMessage());
			} catch (Exception ex) {
				Log.e("memory", "Coudln't load thumbnail file for " + imagePath + " due to Other exception " + ex.getMessage());
			}
			return null;
		}// func

		@Override
		protected void onPostExecute(Bitmap bmp) {
			boolean isSuccess = false;

			// --------------------------
			// if the task has been cancelled, don't bother doing anything else.
			if (this.isCancelled()) {
				if (bmp != null) {
					bmp.recycle();
					bmp = null;
				}

				// --------------------------
				// if no callback, but we have an image and a view.
			} else if (mImgView != null && bmp != null) {
				final View view = mImgView.get();
				if (view != null && view instanceof ImageView && bmp != null) {
					((ImageView) view).setImageBitmap(bmp);
					isSuccess = true;
				}// if

				// --------------------------
				// incase imageview doesn't exist anymore but bmp was loaded.
			} else if (bmp != null) {
				bmp.recycle();
				bmp = null;
			}// if

			// .....................................
			// When done loading the image, alert parent
			if (mOnImageLoaded != null) {
				final OnImageLoadedListener callback = mOnImageLoaded.get();
				if (callback != null)
					callback.onImageLoaded(isSuccess, bmp, mImgView.get());
			}// if
		}// func
	}// cls
}// func