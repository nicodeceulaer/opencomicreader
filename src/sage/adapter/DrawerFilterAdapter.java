package sage.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.runeai.runereader.R;

public class DrawerFilterAdapter extends ArrayAdapter<String> {

	Context context;
	int layoutResourceId;
	String data[] = null;

	public DrawerFilterAdapter(Context context, int layoutResourceId, String[] data) {
		super(context, layoutResourceId, data);
		this.layoutResourceId = layoutResourceId;
		this.context = context;
		this.data = data;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row = convertView;
		FilterHolder holder = null;

		if (row == null) {
			LayoutInflater inflater = ((Activity) context).getLayoutInflater();
			row = inflater.inflate(layoutResourceId, parent, false);

			holder = new FilterHolder();
			holder.txtTitle = (TextView) row.findViewById(R.id.filter_list_item_name);

			row.setTag(holder);
		} else {
			holder = (FilterHolder) row.getTag();
		}

		String filter = data[position];
		holder.txtTitle.setText(filter);

		return row;
	}

	static class FilterHolder {
		TextView txtTitle;
	}
}
