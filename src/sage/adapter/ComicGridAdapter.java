package sage.adapter;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import sage.data.domain.Comic;
import sage.loader.LoadImageView;
import sage.ui.ProgressCircle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;

public class ComicGridAdapter extends ArrayAdapter<Comic> implements LoadImageView.OnImageLoadedListener {
	private final Context context;
	private final int layoutResourceId;
	private final List<Comic> data;
	private int mThumbHeight = 300;

	public ComicGridAdapter(Context context, int layoutResourceId, List<Comic> data) {
		super(context, layoutResourceId, data);
		this.layoutResourceId = layoutResourceId;
		this.context = context;
		this.data = data;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		try {
			this.mThumbHeight = prefs.getInt("libCoverHeight", 300);
		} catch (Exception e) {
			Log.e("prefs", "Error loading Library prefs");
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;

		if (convertView == null) {
			LayoutInflater inflater = ((Activity) context).getLayoutInflater();
			convertView = inflater.inflate(layoutResourceId, parent, false);
			holder = new ViewHolder();

			holder.imageCover = (ImageView) convertView.findViewById(R.id.imgCover);
			holder.imageCover.getLayoutParams().height = mThumbHeight;
			holder.libraryTitle = (TextView) convertView.findViewById(R.id.lblTitle);
			holder.issueNumber = (TextView) convertView.findViewById(R.id.issueNumber);
			holder.readingProgress = (ProgressCircle) convertView.findViewById(R.id.pcProgress);

			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		Comic comic = data.get(position);
		if (holder.id == null || holder.id != comic.getId() || holder.bitmap == null || holder.bitmap.get() == null) {

			holder.imageCover.setImageBitmap(null);
			File file = new File(ComicLibrary.getThumbCachePath() + comic.getId() + ".jpg");
			if (file.exists()) {
				LoadImageView.loadImage(file.getPath(), holder.imageCover, context);
			}

			holder.libraryTitle.setText(comic.getTitle());
			holder.issueNumber.setText(Integer.toString(comic.getIssue()));
			holder.readingProgress.setProgress((float) (comic.getPageRead() < comic.getPageCount() - 1 ? comic.getPageRead() : comic.getPageCount())
					/ (float) comic.getPageCount());

			holder.id = comic.getId();
			holder.seriesName = comic.getSeries();
		}

		return convertView;
	}

	public class ViewHolder {
		public Integer id = null;
		public WeakReference<Bitmap> bitmap = null;
		public String seriesName = null;
		public TextView libraryTitle;
		public TextView issueNumber;
		public ImageView imageCover;
		public ProgressCircle readingProgress;
	}

	@Override
	public void onImageLoaded(boolean isSuccess, Bitmap bmp, View view) {
		if (view == null)
			return;
		ImageView iv = (ImageView) view;

		if (!isSuccess)
			iv.setImageBitmap(null); // release reference, if cover didn't load
										// show that it didn't.

		ViewHolder itmRef = (ViewHolder) iv.getTag();
		if (itmRef.bitmap != null && itmRef.bitmap.get() != null) {
			itmRef.bitmap.get().recycle();
			itmRef.bitmap = null;
		}

		itmRef.bitmap = new WeakReference<Bitmap>(bmp); // keeping reference to make sure to clear it out
		// when its not needed
	}

}