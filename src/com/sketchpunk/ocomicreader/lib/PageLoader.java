package com.sketchpunk.ocomicreader.lib;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import sage.Util;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.AsyncTask;

public class PageLoader {
	public static interface CallBack {
		public void onImageLoaded(String errMsg, Bitmap bmp, String imgPath, int imgType);

		public void onImageLoadStarted(boolean isPreloading);
	}// interface

	/*
	 * ========================================================
	 */
	private LoadingTask mTask;
	private final CallBack mCallBack;
	private static int width;
	private static int height;

	public PageLoader(CallBack cb) {
		mCallBack = cb;
	}// func

	public void loadImage(String imgPath, int maxSize, iComicArchive archive, int imgType, boolean isPreloading) {
		if (mTask != null) {
			if (mTask.getStatus() != AsyncTask.Status.FINISHED) {
				if (mTask.imagePath != null && !mTask.imagePath.equals(imgPath))
					mTask.cancel(true);
				else
					return; // Already loaded that image.
			}// if
		}// if

		mTask = new LoadingTask(mCallBack, archive, imgType);
		mCallBack.onImageLoadStarted(isPreloading);
		mTask.execute(maxSize, imgPath);
	}// func

	public void close() {
		cancelTask();
	}// func

	public void cancelTask() {
		if (mTask != null) {
			if (mTask.getStatus() != AsyncTask.Status.FINISHED)
				mTask.cancel(true);
		}// if
	}// func

	public boolean isLoading() {
		if (mTask != null) {
			if (mTask.getStatus() == AsyncTask.Status.FINISHED)
				return false;
			else
				return true;
		}// if

		return false;
	}// func

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	// ************************************************************
	// Load Image through thread
	// ************************************************************
	protected static class LoadingTask extends AsyncTask<Object, Object, Bitmap> { // <Params, Progress, Result>
		private WeakReference<iComicArchive> mArchive = null;
		private WeakReference<CallBack> mCallBack = null;
		private String errMsg = null;
		public String imagePath = null;
		private final int mImgType;

		public LoadingTask(CallBack callback, iComicArchive archive, int imgType) {
			mArchive = new WeakReference<iComicArchive>(archive);
			mCallBack = new WeakReference<CallBack>(callback);
			mImgType = imgType;
		}// func

		@Override
		protected Bitmap doInBackground(Object... params) {
			// ...................................
			final iComicArchive archive = mArchive.get();
			if (archive == null)
				return null;

			// ...................................
			int maxTextureSize = ((Integer) params[0]).intValue();

			imagePath = (String) params[1];
			InputStream iStream = archive.getItemInputStream(imagePath);

			if (iStream == null)
				return null;

			// ...................................
			// Determine the size of the image
			System.gc();// This does help, even though this is frowned upon.
			Bitmap bmp = null;
			BitmapFactory.Options bmpOption = new BitmapFactory.Options();

			try {
				bmpOption.inJustDecodeBounds = true;
				BitmapFactory.decodeStream(iStream, null, bmpOption);
				iStream = resetArchiveStream(archive, iStream);
			} catch (Exception e) {
				System.out.println("Error Getting Image Size " + e.getMessage());
			}// try

			// ...................................
			// Load Up Image
			int iScale = 1;
			iScale = Util.calculateInSampleSize(bmpOption, width, height);

			for (int i = 0; i < 4; i++) {
				try {
					iStream = resetArchiveStream(archive, iStream);

					bmpOption.inJustDecodeBounds = false;
					bmpOption.inScaled = false;
					bmpOption.inSampleSize = iScale;

					bmp = BitmapFactory.decodeStream(iStream, null, bmpOption);
					if (bmp == null) {
						iScale *= 2;
						continue;
					}
					break; // exit loop, successful loading of image.
				} catch (OutOfMemoryError e) {
					System.out.println("-----Out of memory error " + Integer.toString(iScale));
					if (i < 3) {
						iScale *= 2;
					}

					if (i == 3)
						bmpOption.inPreferredConfig = Bitmap.Config.RGB_565; // Try to use up less memory

					// if forth attempt, give up.
					if (i == 4) {
						errMsg = "Out of memory loading image";
						System.out.println(errMsg);
						if (bmp != null)
							bmp.recycle();
						return null;
					}// if
				} catch (Exception e) {
					errMsg = "Error Loading Image " + e.getMessage();
					System.out.println(errMsg);
					return null;
				}// try
			}// for

			// ...................................
			// Scale down bitmap if over GL Texture Size
			int w = bmp.getWidth(), h = bmp.getHeight();
			if (w > maxTextureSize || h > maxTextureSize) {
				System.gc(); // This does help, even though this is frowned upon.
				Bitmap newBmp = null;

				float fScale = maxTextureSize / (float) Math.max(w, h);
				int newWidth = Math.round(w * fScale), newHeight = Math.round(h * fScale);

				Matrix scaleMatrix = new Matrix();
				scaleMatrix.setScale(fScale, fScale, 0, 0);

				Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
				paint.setAntiAlias(true);

				for (int i = 0; i < 2; i++) {
					try {
						if (i == 0)
							newBmp = Bitmap.createBitmap(newWidth, newHeight, bmp.getConfig());
						else
							newBmp = Bitmap.createBitmap(newWidth, newHeight, Config.RGB_565); // Use less memory

						Canvas canvas = new Canvas(newBmp);
						canvas.setMatrix(scaleMatrix);
						canvas.drawBitmap(bmp, 0, 0, paint);

						bmp.recycle(); // Clean up original bitmap
						bmp = newBmp; // Save reference of new image to the return image
						newBmp = null; // Clear reference so it doesn't get recycled.
						break;
					} catch (OutOfMemoryError e) {
						System.out.println("Out of memory rescaling");
						if (newBmp != null) {
							newBmp.recycle();
							newBmp = null;
						}

						if (i == 1) {
							bmp.recycle();
							bmp = null;
							errMsg = "Out of memory while rescaling image.";
							System.out.println(errMsg);
						}// if
					} catch (Exception e) {
						errMsg = "Error Rescaling Image for Display : " + e.getMessage();
						System.out.println(errMsg);

						if (bmp != null) {
							bmp.recycle();
							bmp = null;
						}
					}// if
				}// if
			}// if

			return bmp;
		}// func

		private InputStream resetArchiveStream(final iComicArchive archive, InputStream iStream) throws IOException {
			// Rar stream can be reset which is better, but zip's can not, new stream must be created.
			if (archive.isStreamResetable())
				iStream.reset();
			else {
				iStream.close();
				iStream = null;
				iStream = archive.getItemInputStream(imagePath);
			}// if
			return iStream;
		}

		@Override
		protected void onPostExecute(Bitmap bmp) {
			// --------------------------
			// if the task has been cancelled, don't bother doing anything else.
			if (this.isCancelled()) {
				if (bmp != null) {
					bmp.recycle();
					bmp = null;
				}// if
			}// if

			// .....................................
			// When done loading the image, alert parent
			if (mCallBack != null) {
				final CallBack cb = mCallBack.get();
				if (cb != null)
					cb.onImageLoaded(errMsg, bmp, imagePath, mImgType);
			}// if
		}// func
	}// cls
}// cls