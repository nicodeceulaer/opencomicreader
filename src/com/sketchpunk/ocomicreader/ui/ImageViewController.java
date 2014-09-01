package com.sketchpunk.ocomicreader.ui;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import sage.data.domain.Comic;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.micabyte.android.graphics.BitmapSurfaceRenderer;
import com.micabyte.android.graphics.MicaSurfaceView;
import com.micabyte.android.graphics.SurfaceListener;
import com.sketchpunk.ocomicreader.ViewActivity;
import com.sketchpunk.ocomicreader.enums.Direction;
import com.sketchpunk.ocomicreader.lib.ComicLoader;
import com.sketchpunk.ocomicreader.ui.GestureImageView.OnKeyDownListener;

public class ImageViewController implements SurfaceListener, OnKeyDownListener {

	public BitmapSurfaceRenderer renderer;
	public MicaSurfaceView imageView;
	public ViewActivity viewActivity;
	public Comic currentComic;
	public Boolean mPref_openNextComicOnEnd = true;
	public Boolean mPref_ShowPgNum = true;
	public Boolean mPref_ReadRight = true;
	public ComicLoader mComicLoad;
	public RuntimeExceptionDao<Comic, Integer> comicDao;

	@Override
	public void onTouchDown(int x, int y) {
	}

	@Override
	public void onTouchUp(int x, int y) {
		// Add game code
	}

	@Override
	public void onImageGesture(int gType) {
		switch (gType) {
		// .....................................
		case GestureImageView.TWOFINGTAP:
			viewActivity.openContextMenu(imageView);
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
		}
	}

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
				viewActivity.showToast(msg, 1);
			}
		} else if (status == -1) {
			viewActivity.showToast("Still Preloading, Try again in one second", 1);
		}
	}

	private void moveToAnotherComic(Integer comicId) {
		Intent intent = new Intent(viewActivity, ViewActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra("comicid", comicId);
		viewActivity.startActivity(intent);
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
								.raw("LOWER(`title`) > '" + currentComic.getTitle().replace("'", "''").toLowerCase(Locale.getDefault()) + "'").and()
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
			viewActivity.showToast("Loading Page...", 1);
		}
		return (mPref_ReadRight) ? mComicLoad.nextPage() : mComicLoad.prevPage();
	}

	private boolean progressPageRight() {
		// boolean shifted;
		// shifted = (mPref_ReadRight) ? mImageView.shiftRight() : mImageView.shiftLeft_rev();
		// return shifted;
		return false;
	}

	private int turnPageLeft() {
		if (this.mPref_ShowPgNum) {
			viewActivity.showToast("Loading Page...", 1);
		}
		return (mPref_ReadRight) ? mComicLoad.prevPage() : mComicLoad.nextPage();
	}

	private boolean progressPageLeft() {
		// return (!mPref_ReadRight) ? mImageView.shiftLeft() : mImageView.shiftRight_rev();
		return false;
	}

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
			viewActivity.openContextMenu(imageView);
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
			viewActivity.openContextMenu(imageView);
			break;
		}

		return viewActivity.onKeyDown(keyCode, event);
	}

}