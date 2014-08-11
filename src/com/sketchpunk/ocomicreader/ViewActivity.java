package com.sketchpunk.ocomicreader;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import sage.ui.ActivityUtil;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.sketchpunk.ocomicreader.enums.Direction;
import com.sketchpunk.ocomicreader.lib.ComicLoader;
import com.sketchpunk.ocomicreader.lib.ImgTransform;
import com.sketchpunk.ocomicreader.ui.GestureImageView;
import com.sketchpunk.ocomicreader.ui.GestureImageView.OnKeyDownListener;

//http://developer.android.com/training/displaying-bitmaps/cache-bitmap.html#disk-cache
//https://github.com/fhucho/simple-disk-cache/blob/master/SimpleDiskCache.java
//http://stackoverflow.com/questions/10185898/using-disklrucache-in-android-4-0-does-not-provide-for-opencache-method
//https://github.com/JakeWharton/DiskLruCache/tree/master/src/main/java/com/jakewharton/disklrucache

public class ViewActivity extends Activity implements ComicLoader.ComicLoaderListener, GestureImageView.OnImageGestureListener, OnKeyDownListener,
		DialogInterface.OnClickListener {

	// TODO, Some of these things need to exist in a RetainFragment.
	private GestureImageView mImageView; // Main display of image
	private ComicLoader mComicLoad; // Object that will manage streaming and
									// scaling images out of the archive file
	private Integer mComicID = null;
	private Toast mToast;
	private Boolean mPref_ShowPgNum = true;
	private Boolean mPref_ReadRight = true;
	private Boolean mPref_FullScreen = true;
	private Boolean mPref_openNextComicOnEnd = true;

	RuntimeExceptionDao<Comic, Integer> comicDao = null;
	Comic currentComic = null;

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
		mPref_ShowPgNum = prefs.getBoolean("showPageNum", true);
		mPref_FullScreen = prefs.getBoolean("fullScreen", true);
		mPref_ReadRight = prefs.getBoolean("readToRight", true);
		mPref_openNextComicOnEnd = prefs.getBoolean("openNextComicOnEnd", true);

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

			currentComic = comicDao.queryForId(mComicID);

			filePath = currentComic.getPath();
			currentPage = Math.max(currentComic.getPageCurrent(), 0);
		}// if

		// .........................................
		mImageView = (GestureImageView) this.findViewById(R.id.pageView);
		mImageView.setPanState((mPref_ReadRight) ? ImgTransform.INITPAN_LEFT : ImgTransform.INITPAN_RIGHT);
		mImageView.setScaleMode(scaleMode);
		registerForContextMenu(mImageView);

		// .........................................
		mComicLoad = new ComicLoader(this, mImageView);
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
		if (currentComic != null) {
			currentComic.setDateRead(new Date());
			comicDao.update(currentComic);
		}
	}

	private void getComicDao() {
		DatabaseHelper databaseHelper = OpenHelperManager.getHelper(getApplicationContext(), DatabaseHelper.class);
		comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
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

		switch (mImageView.getScaleMode()) {
		case ImgTransform.SCALE_NONE:
			menu.findItem(R.id.mnu_scalen).setChecked(true);
			break;
		case ImgTransform.SCALE_HEIGHT:
			menu.findItem(R.id.mnu_scaleh).setChecked(true);
			break;
		case ImgTransform.SCALE_WIDTH:
			menu.findItem(R.id.mnu_scalew).setChecked(true);
			break;
		case ImgTransform.SCALE_AUTO:
			menu.findItem(R.id.mnu_scalea).setChecked(true);
			break;
		}// switch

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
		case R.id.mnu_scaleh:
			mImageView.setScaleMode(ImgTransform.SCALE_HEIGHT);
			break;
		case R.id.mnu_scalew:
			mImageView.setScaleMode(ImgTransform.SCALE_WIDTH);
			break;
		case R.id.mnu_scalen:
			mImageView.setScaleMode(ImgTransform.SCALE_NONE);
			break;
		case R.id.mnu_scalea:
			mImageView.setScaleMode(ImgTransform.SCALE_AUTO);
			break;

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
			mPref_ReadRight = (!mPref_ReadRight);
			mImageView.setPanState((mPref_ReadRight) ? ImgTransform.INITPAN_LEFT : ImgTransform.INITPAN_RIGHT);
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

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO: add config menu to relate actions with keys

		switch (keyCode) {
		case KeyEvent.KEYCODE_PAGE_UP:
			progressPage(Direction.LEFT);
			break;
		case KeyEvent.KEYCODE_PAGE_DOWN:
			progressPage(Direction.RIGHT);
			break;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			openContextMenu(mImageView);
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			progressPage(Direction.LEFT);
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			progressPage(Direction.RIGHT);
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			turnPage(Direction.RIGHT);
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			turnPage(Direction.LEFT);
			break;
		case KeyEvent.KEYCODE_MENU:
			openContextMenu(mImageView);
			break;
		}

		return super.onKeyDown(keyCode, event);
	}

	// ------------------------------------------------------------------------
	// Paging Loading Events
	@Override
	public void onPageLoaded(boolean isSuccess, int currentPage) {
		if (isSuccess) { // Save reading progress.
			if (currentComic != null) {

				currentComic.setPageCurrent(currentPage);
				if (currentComic.getPageRead() < currentPage) {
					currentComic.setPageRead(currentPage);
				}
				comicDao.update(currentComic);
			}// if

			// ....................................
			// Display page number
			if (this.mPref_ShowPgNum)
				showToast(String.format("%d / %d", currentPage + 1, mComicLoad.getPageCount()), 0);
		}// if
	}// func

	@Override
	public void onImageGesture(int gType) {
		switch (gType) {
		// .....................................
		case GestureImageView.TWOFINGTAP:
			openContextMenu(mImageView);
			return;

			// .....................................
		case GestureImageView.TAPLEFT:
			progressPage(Direction.LEFT);
			break;

		case GestureImageView.TAPRIGHT:
			progressPage(Direction.RIGHT);
			break;

		// .....................................
		case GestureImageView.FLINGLEFT:
			turnPage(Direction.LEFT);
			break;
		case GestureImageView.FLINGRIGHT:
			turnPage(Direction.RIGHT);
			break;

		default:
			return; // Any other gestures not handling, exit right away.
		}// switch
	}// func

	private void turnPage(Direction direction) {
		if (direction != null) {
			int status = 0;
			if (direction.equals(Direction.LEFT)) {
				status = turnPageLeft();
			} else {
				status = turnPageRight();
			}
			processStatus(status, direction);
		}
	}

	private void progressPage(Direction direction) {
		if (direction != null) {
			int status = 2;
			// 2: progressed page
			// 1: turned page
			// 0: reached border page
			// -1: strill preloading
			if (direction.equals(Direction.LEFT)) {
				if (!progressPageLeft()) {
					status = turnPageLeft();
				}
			} else {
				if (!progressPageRight()) {
					status = turnPageRight();
				}
			}
			processStatus(status, direction);
		}
	}

	private void processStatus(int status, Direction direction) {
		if (status == 0) {
			boolean firstPage = (direction == Direction.LEFT && mPref_ReadRight || direction == Direction.RIGHT && !mPref_ReadRight);
			Integer comicToLoad = null;

			if (mPref_openNextComicOnEnd) {
				comicToLoad = determineComicToLoad(firstPage);
			}

			if (comicToLoad != null) {
				moveToAnotherComic(comicToLoad);
			} else {
				String msg = firstPage ? "FIRST PAGE" : "LAST PAGE";
				showToast(msg, 1);
			}
		} else if (status == -1) {
			showToast("Still Preloading, Try again in one second", 1);
		}
	}

	private void moveToAnotherComic(Integer comicId) {
		Intent intent = new Intent(this, ViewActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra("comicid", comicId);
		this.startActivity(intent);
	}

	private Integer determineComicToLoad(boolean firstPage) {
		Integer result = null;

		if (currentComic != null) {
			QueryBuilder<Comic, Integer> queryBuilder = comicDao.queryBuilder();

			try {
				if (firstPage) {
					queryBuilder.orderBy("issue", false).limit(1L).where().lt("issue", currentComic.getIssue()).and()
							.raw("LOWER(`series`) = '" + currentComic.getSeries().replace("'", "''").toLowerCase(Locale.getDefault()) + "'");
				} else {
					queryBuilder.orderBy("issue", true).limit(1L).where().gt("issue", currentComic.getIssue()).and()
							.raw("LOWER(`series`) = '" + currentComic.getSeries().replace("'", "''").toLowerCase(Locale.getDefault()) + "'");
				}

				List<Comic> comics = queryBuilder.query();

				if (comics == null || (comics != null && comics.size() == 0)) {
					queryBuilder = comicDao.queryBuilder();

					if (firstPage) {
						queryBuilder.orderByRaw("LOWER(`title`) DESC").limit(1L).where()
								.raw("LOWER(`title`) < '" + currentComic.getTitle().replace("'", "''").toLowerCase(Locale.getDefault()) + "'").and()
								.raw("LOWER(`series`) = '" + currentComic.getSeries().replace("'", "''").toLowerCase(Locale.getDefault()) + "'");
					} else {
						queryBuilder.orderByRaw("LOWER(`title`) ASC").limit(1L).where()
								.raw("LOWER(`title`) > '" + currentComic.getTitle().replace("'", "''").toLowerCase(Locale.getDefault()) + "'")
								.raw("LOWER(`series`) = '" + currentComic.getSeries().replace("'", "''").toLowerCase(Locale.getDefault()) + "'");
					}

					comics = queryBuilder.query();
				}

				if (comics != null && comics.size() > 0) {
					result = comics.get(0).getId();
				}
			} catch (SQLException e) {
				Log.e("sql", e.getMessage());
			}
		}

		return result;
	}

	private int turnPageRight() {
		if (this.mPref_ShowPgNum) {
			showToast("Loading Page...", 1);
		}
		return (mPref_ReadRight) ? mComicLoad.nextPage() : mComicLoad.prevPage();
	}

	private boolean progressPageRight() {
		boolean shifted;
		shifted = (mPref_ReadRight) ? mImageView.shiftRight() : mImageView.shiftLeft_rev();
		return shifted;
	}

	private int turnPageLeft() {
		if (this.mPref_ShowPgNum) {
			showToast("Loading Page...", 1);
		}
		return (mPref_ReadRight) ? mComicLoad.prevPage() : mComicLoad.nextPage();
	}

	private boolean progressPageLeft() {
		return (!mPref_ReadRight) ? mImageView.shiftLeft() : mImageView.shiftRight_rev();
	}

	// ------------------------------------------------------------------------
	// Helper Functions
	private void showToast(String msg, int duration) {
		mToast.setText(msg);
		mToast.setDuration(duration);
		mToast.show();
	}// func
}// cls
