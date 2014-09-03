package sage;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.SuppressLint;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

public class Util {

	private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

	/*
	 * Note: If Targeting API17+, can use View.generateViewId() else use this function Generate a value suitable for use in {@link #setId(int)}. This value will
	 * not collide with ID values generated at build time by aapt for R.id.
	 */
	public static int generateViewId() {
		for (;;) {
			final int result = sNextGeneratedId.get(); // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
			int newValue = result + 1;

			if (newValue > 0x00FFFFFF)
				newValue = 1; // Roll over to 1, not 0.
			if (sNextGeneratedId.compareAndSet(result, newValue))
				return result;
		}// for
	}// func

	public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	public static Point getRealSize(Display display) {
		Point outPoint = new Point();
		Method mGetRawH;
		try {
			mGetRawH = Display.class.getMethod("getRawHeight");
			Method mGetRawW = Display.class.getMethod("getRawWidth");
			outPoint.x = (Integer) mGetRawW.invoke(display);
			outPoint.y = (Integer) mGetRawH.invoke(display);
			return outPoint;
		} catch (Throwable e) {
			return null;
		}
	}

	@SuppressLint("NewApi")
	public static Point getSize(Display display) {
		if (Build.VERSION.SDK_INT >= 17) {
			Point outPoint = new Point();
			DisplayMetrics metrics = new DisplayMetrics();
			display.getRealMetrics(metrics);
			outPoint.x = metrics.widthPixels;
			outPoint.y = metrics.heightPixels;
			return outPoint;
		}
		if (Build.VERSION.SDK_INT >= 14) {
			Point outPoint = getRealSize(display);
			if (outPoint != null)
				return outPoint;
		}
		Point outPoint = new Point();
		if (Build.VERSION.SDK_INT >= 13) {
			display.getSize(outPoint);
		} else {
			outPoint.x = display.getWidth();
			outPoint.y = display.getHeight();
		}
		return outPoint;
	}

	// ...............................................................................
	// Conversion
	// public static int dp2px(float n){ return (int)
	// TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,n,zApp.getContext().getResources().getDisplayMetrics()); }//func
	// public static int sp2px(float n){ return (int)
	// TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,n,zApp.getContext().getResources().getDisplayMetrics()); }//func
}// cls
