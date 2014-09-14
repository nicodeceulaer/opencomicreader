package com.sketchpunk.ocomicreader.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;

import com.sketchpunk.ocomicreader.ViewActivity;

public class ComicPageView extends View implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, OnScaleGestureListener {
	protected class Sizes {
		public float oWidth;
		public float oHeight;
		public float scale;
		public float sWidth;
		public float sHeight;
	}// cls

	public static interface CallBack {
		public void onComicPageGesture(int gestureID);
	}// interface

	public static final int ScaleNone = 0;
	public static final int ScaleToHeight = 1;
	public static final int ScaleToWidth = 2;
	public static final int ScaleAuto = 3;

	public static final int OrientationDevice = 0;
	public static final int OrientationPortrait = 1;
	public static final int OrientationLandscape = 2;

	public static final int FlingLeft = 0;
	public static final int FlingRight = 1;
	public static final int TapLeft = 2;
	public static final int TapRight = 3;
	public static final int LongPress = 4;

	private Bitmap mBitmap; // WeakReference Change to this
	private Matrix mMatrix;
	private Paint mPaint;
	private GestureDetector mGesture;
	private ScaleGestureDetector mScaleGesture;
	private ComicPageView.CallBack mCallBack;

	private int mScaleMode = 3;
	private int mScreenOrientation = 0;
	private RectF mBound;
	private Sizes mViewSize;
	private Sizes mImgSize;
	private boolean mIsScrollH = false;
	private boolean mIsScrollV = false;

	public ComicPageView(Context context) {
		super(context);
		init(context);
	}// func

	public ComicPageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public ComicPageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context context) {
		mMatrix = new Matrix();
		mGesture = new GestureDetector(context, this);
		mScaleGesture = new ScaleGestureDetector(context, this);

		if (context instanceof CallBack)
			mCallBack = (CallBack) context;

		mPaint = new Paint(Paint.FILTER_BITMAP_FLAG); // make scaled image less
														// pixelated

		mBound = new RectF();
		mBound.left = 0;
		mBound.top = 0;

		mViewSize = new Sizes();
		mImgSize = new Sizes();
		mImgSize.scale = 1.0f;

		// Get perferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String tmp = prefs.getString("scaleMode", "3");
		this.mScaleMode = Integer.parseInt(tmp);

		tmp = prefs.getString("screenOrientation", "0");
		this.mScreenOrientation = Integer.parseInt(tmp);
	}// func

	/*
	 * =======================================================
	 */
	private void doImageCalc() {
		if (mBitmap == null)
			return;
		mImgSize.sWidth = mImgSize.scale * mImgSize.oWidth;
		mImgSize.sHeight = mImgSize.scale * mImgSize.oHeight;

		mBound.right = (mImgSize.sWidth - mViewSize.oWidth) * -1;
		mBound.bottom = (mImgSize.sHeight - mViewSize.oHeight) * -1;

		mIsScrollH = (mImgSize.sWidth > mViewSize.oWidth);
		mIsScrollV = (mImgSize.sHeight > mViewSize.oHeight);
	}// func

	private void resetScale() {
		switch (mScaleMode) {
		case ComicPageView.ScaleToHeight:
			mImgSize.scale = mViewSize.oHeight / mImgSize.oHeight;
			break;
		case ComicPageView.ScaleToWidth:
			mImgSize.scale = mViewSize.oWidth / mImgSize.oWidth;
			break;
		case ComicPageView.ScaleNone:
			mImgSize.scale = 1f;
			break;
		case ComicPageView.ScaleAuto:
			mImgSize.scale = Math.min((mViewSize.oWidth / mImgSize.oWidth), (mViewSize.oHeight / mImgSize.oHeight));
			break;
		}// swtich

		mMatrix.reset();
		mMatrix.setScale(mImgSize.scale, mImgSize.scale);

		doImageCalc();
		this.invalidate();
	}// func

	private void resetScreenOrientation(ViewActivity view) {
		switch (mScreenOrientation) {
		case ComicPageView.OrientationDevice:
			view.setRequestedOrientation(view.getRequestedOrientation());
			break;
		case ComicPageView.OrientationPortrait:
			view.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
			break;
		case ComicPageView.OrientationLandscape:
			view.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		}// switch

	}// func

	/*
	 * ======================================================= Setters/Getters
	 */
	public int getScaleMode() {
		return mScaleMode;
	}

	public void setScaleMode(int mode) {
		mScaleMode = mode;
		resetScale();
	}// func

	public int getScreenOrientation() {
		return mScreenOrientation;
	}

	public void setScreenOrientation(int mode, ViewActivity view) {
		mScreenOrientation = mode;
		resetScreenOrientation(view);
		resetScale();
	}// func

	public void setImageBitmap(Bitmap bmp) {
		if (bmp == null) {
			mBitmap = null;
			return;
		}// func
		mBitmap = bmp;

		mViewSize.oWidth = this.getWidth();
		mViewSize.oHeight = this.getHeight();
		mImgSize.oWidth = mBitmap.getWidth();
		mImgSize.oHeight = mBitmap.getHeight();

		resetScale();
	}// func

	/*
	 * ======================================================== Events
	 */
	@Override
	public void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		mViewSize.oWidth = w;
		mViewSize.oHeight = h;

		resetScale();
	}// func

	/*
	 * ========================================================
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		mScaleGesture.onTouchEvent(e);
		mGesture.onTouchEvent(e);

		return true;
	}// func

	public int getStatusBarHeight() {
		int result = 0;
		int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = getResources().getDimensionPixelSize(resourceId);
		}
		Log.d("reader", "Statusbar height: " + Integer.toString(result));
		return result;
	}

	public int getNavBarHeight() {
		int result = 0;
		int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = getResources().getDimensionPixelSize(resourceId);
		}
		Log.d("reader", "Navbar height: " + Integer.toString(result));
		return result;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (mBitmap != null) {
			if (!mBitmap.isRecycled()) {
				canvas.drawBitmap(mBitmap, mMatrix, mPaint);
				Log.d("reader", "Bitmap Drawn");
			} else
				Log.d("reader", "Bitmap is Recycled");
		} else {
			Log.d("reader", "Bitmap is null");
		}// if
	}// func

	/*
	 * ========================================================
	 */
	@Override
	public boolean onDown(MotionEvent e) {// needed to return true to get most
											// of the gestures to work.
		return true;
	}// func

	/*
	 * ======================================================== Basic Gestures
	 */
	@Override
	public void onLongPress(MotionEvent e) {
		if (mCallBack != null)
			mCallBack.onComicPageGesture(ComicPageView.LongPress);
	}// func

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		if (mCallBack != null) {
			float x = e.getX();
			float area = mViewSize.oWidth / 6;

			if (x <= area)
				mCallBack.onComicPageGesture(ComicPageView.TapLeft);
			else if (x >= mViewSize.oWidth - area)
				mCallBack.onComicPageGesture(ComicPageView.TapRight);
		}// func
		return true;
	}// func

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		resetScale();
		return true;
	}// func

	// don't need it but it must be override.

	/*
	 * ======================================================== Complex Gestures
	 */
	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, final float xVelocity, final float yVelocity) {
		// ...................................
		// if going fast horizontally but very little change vertically, then
		// its a Left/Right Fling
		if (mCallBack != null) {
			if (Math.abs(xVelocity) > 1000 && Math.abs(e1.getRawY() - e2.getRawY()) < 200) {
				if (xVelocity > 0)
					mCallBack.onComicPageGesture(ComicPageView.FlingRight);
				else
					mCallBack.onComicPageGesture(ComicPageView.FlingLeft);

				return true;
			}// if
		}// if

		return true;
	}// func

	@Override
	public boolean onScale(ScaleGestureDetector dScale) {
		// ..................................
		float[] m = new float[9];
		mMatrix.getValues(m);
		float ratio = dScale.getScaleFactor();
		float prevScale = m[Matrix.MSCALE_X];
		float newScale = prevScale * ratio; // Calc new matrix scale to test
											// what the height will become.

		// ..................................
		// Limit how small image can scale
		float newHeight = mImgSize.oHeight * newScale;

		if (newHeight < mViewSize.oHeight) {
			ratio = mViewSize.oHeight / (mImgSize.oHeight * prevScale);
			newScale = prevScale * ratio;
		}// if

		mMatrix.postScale(ratio, ratio, dScale.getCurrentSpanX(), dScale.getCurrentSpanY());

		// ..................................
		// when scaled small, need to realign x,y : keep at top and left
		mMatrix.getValues(m);
		float ymatrix = m[Matrix.MTRANS_Y];
		float xmatrix = m[Matrix.MTRANS_X];

		ymatrix = (ymatrix > 0 || (mImgSize.oHeight * newScale) <= mViewSize.oHeight) ? ymatrix * -1 : 0;
		xmatrix = (xmatrix > 0 || (mImgSize.oWidth * newScale) <= mViewSize.oWidth) ? xmatrix * -1 : 0;

		if (ymatrix != 0 || xmatrix != 0)
			mMatrix.postTranslate(xmatrix, ymatrix);

		// ..................................
		mImgSize.scale = newScale;
		doImageCalc(); // Redo calcs since we scaled the image
		this.invalidate();
		return true;
	}// func

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float xDistance, float yDistance) {
		if (!mIsScrollH && !mIsScrollV)
			return true;
		xDistance *= -1;
		yDistance *= -1;

		// ..................................
		// Get current y,x
		float[] m = new float[9];
		mMatrix.getValues(m);
		float xmatrix = m[Matrix.MTRANS_X];
		float ymatrix = m[Matrix.MTRANS_Y];

		// ..................................
		// Horizontal Bound
		if (mIsScrollH) {
			if (xmatrix + xDistance > mBound.left)
				xDistance = (xmatrix > 0) ? xmatrix * -1 : 0;
			else if (xmatrix + xDistance < mBound.right)
				xDistance = mBound.right - xmatrix;
		} else
			xDistance = 0;// if

		// ..................................
		// Vertical Bound

		if (mIsScrollV) {
			if (ymatrix + yDistance > mBound.top)
				yDistance = (ymatrix < 0) ? ymatrix * -1 : 0;
			else if (ymatrix + yDistance < mBound.bottom)
				yDistance = (ymatrix < mBound.bottom) ? ymatrix * -1 : 0;
		} else
			yDistance = 0;

		// ..................................
		// don't refresh if there is not need.
		if (xDistance != 0 || yDistance != 0) {
			mMatrix.postTranslate(xDistance, yDistance);
			invalidate();
		}// if

		return true;
	}// func

	/*
	 * ======================================================== Unneeded Gestures
	 */
	@Override
	public boolean onDoubleTapEvent(MotionEvent e) {
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent arg0) {
		return false;
	}

	@Override
	public boolean onScaleBegin(ScaleGestureDetector arg0) {
		return true;
	}

	@Override
	public void onScaleEnd(ScaleGestureDetector arg0) {
	}
}// cls
