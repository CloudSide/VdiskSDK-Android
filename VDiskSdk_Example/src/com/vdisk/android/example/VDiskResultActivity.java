package com.vdisk.android.example;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 例子程序各功能的结果显示页面
 * 
 * Result page for demo's functions.
 * 
 * @author sina
 * 
 */
public class VDiskResultActivity extends Activity {

	ListView mListView;

	ArrayList<String> list;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test_result);
		mListView = (ListView) findViewById(R.id.lv_result);
		list = getIntent().getStringArrayListExtra("result");

		ResultListAdapter adapter = new ResultListAdapter(this);
		mListView.setAdapter(adapter);

		if (list.isEmpty()) {
			Toast.makeText(this, getString(R.string.result_is_empty) + "！",
					Toast.LENGTH_SHORT).show();
		}

	}

	class ResultListAdapter extends BaseAdapter {

		private LayoutInflater mInflater;

		public ResultListAdapter(Context context) {
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public int getCount() {

			return list.size();
		}

		@Override
		public String getItem(int position) {
			return list.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			ViewHolder holder;
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.function_item, null);
				holder = new ViewHolder();
				holder.name = (TextView) convertView.findViewById(R.id.tv_name);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			holder.name.setText(list.get(position));

			return convertView;
		}
	}

	private class ViewHolder {
		public TextView name;
	}

}
