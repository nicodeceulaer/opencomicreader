package sage.adapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import sage.data.domain.Comic;
import sage.ui.ProgressCircle;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.sketchpunk.ocomicreader.R;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;

public class ComicGridAdapter extends ArrayAdapter<Comic> {
	private final Context context;
	private final int layoutResourceId;
	private List<Comic> data = new ArrayList<Comic>();

	public ComicGridAdapter(Context context, int layoutResourceId, List<Comic> data) {
		super(context, layoutResourceId, data);
		this.layoutResourceId = layoutResourceId;
		this.context = context;
		this.data = data;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;

		if (convertView == null) {
			LayoutInflater inflater = ((Activity) context).getLayoutInflater();
			convertView = inflater.inflate(layoutResourceId, parent, false);
			holder = new ViewHolder();

			holder.imageCover = (ImageView) convertView.findViewById(R.id.imgCover);
			holder.libraryTitle = (TextView) convertView.findViewById(R.id.lblTitle);
			holder.readingProgress = (ProgressCircle) convertView.findViewById(R.id.pcProgress);

			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		Comic comic = data.get(position);

		if (comic.isCoverExists()) {
			File file = new File(ComicLibrary.getThumbCachePath() + comic.getId() + ".jpg");
			if (file.exists()) {
				Uri uri = Uri.fromFile(file);
				holder.imageCover.setImageURI(uri);

				Bitmap m_d = BitmapFactory.decodeFile(file.getPath());
				if (m_d != null) {
					Bitmap resizedBitmap = Bitmap.createScaledBitmap(m_d, 100, 100, true);
					holder.imageCover.setImageBitmap(resizedBitmap);
				}
			}
		}
		holder.libraryTitle.setText(comic.getTitle());
		holder.readingProgress.setProgress((float) comic.getPageRead() / (float) comic.getPageCount());

		return convertView;
	}

	static class ViewHolder {
		TextView libraryTitle;
		ImageView imageCover;
		ProgressCircle readingProgress;
	}

}