package com.sketchpunk.ocomicreader;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class OpenGLESTestingActivity extends Activity {

	private GLSurfaceView mGLView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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
			int[] maxTextureSize = new int[1];
			int maxSize = 0;

			GLES10.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);

			maxSize = maxTextureSize[0]; // MaxTextureSize

			prefs.edit().putInt("maxTextureSize", maxSize).commit();

			finish();
		}

		@Override
		public void onDrawFrame(GL10 unused) {
			// Redraw background color
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		}

		@Override
		public void onSurfaceChanged(GL10 unused, int width, int height) {
			GLES20.glViewport(0, 0, width, height);
		}

	}
}
