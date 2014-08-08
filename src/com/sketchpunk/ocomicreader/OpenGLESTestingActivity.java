package com.sketchpunk.ocomicreader;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.opengl.GLES10;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class OpenGLESTestingActivity extends Activity {

	private GLSurfaceView mGLView;
	private boolean isTest = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = this.getIntent();
		Bundle b = intent.getExtras();
		isTest = b.getBoolean("isTest");

		mGLView = new MyGLSurfaceView(this);
		setContentView(mGLView);
	}

	class MyGLSurfaceView extends GLSurfaceView {

		public MyGLSurfaceView(Context context) {
			super(context);

			setRenderer(new MyGLRenderer(context));
		}
	}

	class MyGLRenderer implements GLSurfaceView.Renderer {
		SharedPreferences prefs;

		public MyGLRenderer(Context context) {
			prefs = PreferenceManager.getDefaultSharedPreferences(context);
		}

		@Override
		public void onSurfaceCreated(GL10 unused, EGLConfig config) {
			if (isTest) {
				int[] maxTextureSize = new int[1];
				int maxSize = 0;

				GLES10.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);

				maxSize = maxTextureSize[0]; // MaxTextureSize

				prefs.edit().putInt("maxTextureSize", maxSize).commit();

				finish();
			}
		}

		int i = 0;

		@Override
		public void onDrawFrame(GL10 unused) {

			unused.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);// white

			unused.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
			if (i < 15) {
				i++;
			} else {
				finish();
			}
		}

		@Override
		public void onSurfaceChanged(GL10 unused, int width, int height) {
			unused.glViewport(0, 0, width, height);
		}

	}
}
