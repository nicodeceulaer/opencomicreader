package com.sketchpunk.ocomicreader;

import java.util.Date;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import sage.ui.ActivityUtil;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.micabyte.android.graphics.BitmapSurfaceRenderer;
import com.micabyte.android.graphics.MicaSurfaceView;
import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.lib.ComicLoader;
import com.sketchpunk.ocomicreader.ui.ImageViewController;

//http://developer.android.com/training/displaying-bitmaps/cache-bitmap.html#disk-cache
//https://github.com/fhucho/simple-disk-cache/blob/master/SimpleDiskCache.java
//http://stackoverflow.com/questions/10185898/using-disklrucache-in-android-4-0-does-not-provide-for-opencache-method
//https://github.com/JakeWharton/DiskLruCache/tree/master/src/main/java/com/jakewharton/disklrucache

public class ViewActivity extends Activity implements ComicLoader.ComicLoaderListener, DialogInterface.OnClickListener {

	// TODO, Some of these things need to exist in a RetainFragment.
	private MicaSurfaceView mImageView; // Main display of image
	private ComicLoader mComicLoad; // Object that will manage streaming and
									// scaling images out of the archive file
	private Integer mComicID = null;
	private Toast mToast;
	private Boolean mPref_ShowPgNum = true;
	private Boolean mPref_ReadRight = true;
	private Boolean mPref_FullScreen = true;

	private final ImageViewController controller = new ImageViewController();

	RuntimeExceptionDao<Comic, Integer> comicDao = null;

	// ------------------------------------------------------------------------
	// Activty Events
	@SuppressLint("ShowToast")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// TODO Make sure scale is set from Preferences
		// ........................................
		// Get preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		controller.mPref_ShowPgNum = mPref_ShowPgNum = prefs.getBoolean("showPageNum", true);
		mPref_FullScreen = prefs.getBoolean("fullScreen", true);
		controller.mPref_ReadRight = mPref_ReadRight = prefs.getBoolean("readToRight", true);
		controller.mPref_openNextComicOnEnd = prefs.getBoolean("openNextComicOnEnd", true);

		int scaleMode = Integer.parseInt(prefs.getString("scaleMode", "3"));

		// Set activity features
		int features = 0;
		if (mPref_FullScreen)
			features |= ActivityUtil.FEATURE_FULLSCREEN;
		if (prefs.getBoolean("keepScreenOn", true))
			features |= ActivityUtil.FEATURE_KEEPSCREENON;
		if (features > 0)
			ActivityUtil.setFeatures(this, features);

		// Apply preferred orientation
		int so = Integer.parseInt(prefs.getString("screenOrientation", "0"));
		if (so != 0)
			ActivityUtil.setScreenOrientation(this, so);

		// .........................................
		this.overridePendingTransition(R.anim.fadein, R.anim.fadeout);
		setContentView(R.layout.activity_view);

		// .........................................
		// setup reuseable toast
		mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
		mToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);

		// .........................................
		int currentPage = 0;
		String filePath = "";

		getComicDao();

		Intent intent = this.getIntent();
		Uri uri = intent.getData();
		if (uri != null) {
			filePath = Uri.decode(uri.toString().replace("file://", ""));
		} else {
			Bundle b = intent.getExtras();
			mComicID = b.getInt("comicid");

			controller.currentComic = comicDao.queryForId(mComicID);

			filePath = controller.currentComic.getPath();
			currentPage = Math.max(controller.currentComic.getPageCurrent(), 0);
		}// if

		// .........................................
		mImageView = (MicaSurfaceView) this.findViewById(R.id.pageView);
		BitmapSurfaceRenderer renderer = new BitmapSurfaceRenderer(this);
		mImageView.setListener(controller);
		controller.renderer = renderer;
		controller.imageView = mImageView;
		controller.viewActivity = this;
		mImageView.setRenderer(renderer);
		Point center = new Point();
		renderer.getViewPosition(center);
		registerForContextMenu(mImageView);

		// .........................................
		controller.mComicLoad = mComicLoad = new ComicLoader(this, renderer);
		if (mComicLoad.loadArchive(filePath)) {
			if (mPref_ShowPgNum)
				showToast("Loading Page...", 1);
			mComicLoad.gotoPage(currentPage); // Continue where user left off
		} else {
			Toast.makeText(this, "Unable to load comic.", Toast.LENGTH_LONG).show();
		}// if

		updateReadingHistory();
	}// func

	private void updateReadingHistory() {
		if (controller.currentComic != null) {
			controller.currentComic.setDateRead(new Date());
			comicDao.update(controller.currentComic);
		}
	}

	private void getComicDao() {
		DatabaseHelper databaseHelper = OpenHelperManager.getHelper(getApplicationContext(), DatabaseHelper.class);
		controller.comicDao = comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
	}

	@Override
	public void onDestroy() {
		OpenHelperManager.releaseHelper();
		mComicLoad.close();
		super.onDestroy();
	}// func

	@Override
	public void onPause() {
		super.onPause();
	}// func

	@Override
	public void onResume() {
		super.onResume();

		if (comicDao == null) {
			getComicDao();
		}

		if (mPref_FullScreen)
			ActivityUtil.setImmersiveModeOn(this);
	}// func

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);

	}// func

	// ------------------------------------------------------------------------
	// Menu Events
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.activity_view, menu);
		menu.setHeaderTitle("Options");

		menu.findItem(R.id.mnu_readright).setChecked(mPref_ReadRight);

		// switch (mImageView.getScaleMode()) {
		// case ImgTransform.SCALE_NONE:
		// menu.findItem(R.id.mnu_scalen).setChecked(true);
		// break;
		// case ImgTransform.SCALE_HEIGHT:
		// menu.findItem(R.id.mnu_scaleh).setChecked(true);
		// break;
		// case ImgTransform.SCALE_WIDTH:
		// menu.findItem(R.id.mnu_scalew).setChecked(true);
		// break;
		// case ImgTransform.SCALE_AUTO:
		// menu.findItem(R.id.mnu_scalea).setChecked(true);
		// break;
		// }// switch

		switch (ActivityUtil.getScreenOrientation()) {
		case ActivityUtil.ORIENTATION_DEVICE:
			menu.findItem(R.id.mnu_orientationd).setChecked(true);
			break;
		case ActivityUtil.ORIENTATION_PORTRAIT:
			menu.findItem(R.id.mnu_orientationp).setChecked(true);
			break;
		case ActivityUtil.ORIENTATION_LANDSCAPE:
			menu.findItem(R.id.mnu_orientationl).setChecked(true);
			break;
		}// switch
	}// func

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// case R.id.mnu_scaleh:
		// mImageView.setScaleMode(ImgTransform.SCALE_HEIGHT);
		// break;
		// case R.id.mnu_scalew:
		// mImageView.setScaleMode(ImgTransform.SCALE_WIDTH);
		// break;
		// case R.id.mnu_scalen:
		// mImageView.setScaleMode(ImgTransform.SCALE_NONE);
		// break;
		// case R.id.mnu_scalea:
		// mImageView.setScaleMode(ImgTransform.SCALE_AUTO);
		// break;

		case R.id.mnu_orientationd:
			ActivityUtil.setScreenOrientation(this, ActivityUtil.ORIENTATION_DEVICE);
			break;
		case R.id.mnu_orientationp:
			ActivityUtil.setScreenOrientation(this, ActivityUtil.ORIENTATION_PORTRAIT);
			break;
		case R.id.mnu_orientationl:
			ActivityUtil.setScreenOrientation(this, ActivityUtil.ORIENTATION_LANDSCAPE);
			break;

		case R.id.mnu_goto:
			sage.ui.Dialogs.NumPicker(this, "Goto Page", 1, mComicLoad.getPageCount(), mComicLoad.getCurrentPage() + 1, this);
			break;
		case R.id.mnu_exit:
			this.finish();
			break;

		case R.id.mnu_readright:
			// mPref_ReadRight = (!mPref_ReadRight);
			// mImageView.setPanState((mPref_ReadRight) ? ImgTransform.INITPAN_LEFT : ImgTransform.INITPAN_RIGHT);
			break;

		case R.id.mnu_immersive:
			ActivityUtil.setImmersiveModeOn(this);
			return true;
		}// switch

		// Popup pulls the activity out of immersive mode, after action, turn it
		// back on.
		if (mPref_FullScreen)
			ActivityUtil.setImmersiveModeOn(this);
		return true;
	}// func

	// this is for the goto menu option and user clicks ok.
	@Override
	public void onClick(DialogInterface dialog, int which) {
		mComicLoad.gotoPage(which - 1);

		// Popup pulls the activity out of immersive mode, after action, turn it
		// back on.
		if (mPref_FullScreen)
			ActivityUtil.setImmersiveModeOn(this);
	}// func

	// ------------------------------------------------------------------------
	// Paging Loading Events
	@Override
	public void onPageLoaded(boolean isSuccess, int currentPage) {
		if (isSuccess) { // Save reading progress.
			if (controller.currentComic != null) {

				controller.currentComic.setPageCurrent(currentPage);
				if (controller.currentComic.getPageRead() < currentPage) {
					controller.currentComic.setPageRead(currentPage);
				}
				comicDao.update(controller.currentComic);
			}// if

			// ....................................
			// Display page number
			if (this.mPref_ShowPgNum)
				showToast(String.format("%d / %d", currentPage + 1, mComicLoad.getPageCount()), 0);
		}// if
	}// func

	// ------------------------------------------------------------------------
	// Helper Functions
	public void showToast(String msg, int duration) {
		mToast.setText(msg);
		mToast.setDuration(duration);
		mToast.show();
	}// func
}// cls
