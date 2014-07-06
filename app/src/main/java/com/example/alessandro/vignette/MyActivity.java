package com.example.alessandro.vignette;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;

import java.io.IOException;
import java.io.InputStream;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;


public class MyActivity extends Activity implements SeekBar.OnSeekBarChangeListener {

	private static final String TAG = "MainActivity";
	ImageViewVignette mImageView;
	SeekBar mSeekBar1;
	SeekBar mSeekBar2;
	Bitmap mOriginaBitmap;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("MainActivity", "onCreate");

		setContentView(R.layout.activity_my);

		mImageView = (ImageViewVignette) findViewById(R.id.image);
		mSeekBar1 = (SeekBar) findViewById(R.id.seekBar);
		mSeekBar2 = (SeekBar) findViewById(R.id.seekBar2);

		mImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_IF_BIGGER);

		mSeekBar1.setOnSeekBarChangeListener(this);
		mSeekBar2.setOnSeekBarChangeListener(this);

		new TestTask().execute("image.jpg");
	}

	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
		Log.i("MainActivity", "onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.my, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
		if (! fromUser) return;

		if (seekBar.getId() == mSeekBar1.getId()) {
			int value = progress * 2 - 100;
			mImageView.setVignetteIntensity(value);
		}
		else {
			mImageView.setVignetteFeather((float) progress / 100);
		}
	}

	@Override
	public void onStartTrackingTouch(final SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(final SeekBar seekBar) {

	}

	class TestTask extends AsyncTask<String, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(final String... params) {

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			InputStream stream = null;
			Bitmap bitmap = null;
			try {
				stream = getResources().getAssets().open(params[0]);
				bitmap = BitmapFactory.decodeStream(stream);
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(final Bitmap bitmap) {
			mOriginaBitmap = bitmap;
			mImageView.setImageBitmap(bitmap);

			int intensity = mImageView.getVignetteIntensity();
			intensity = (intensity + 100) / 2;
			Log.d(TAG, "intensity: " + intensity);
			mSeekBar1.setProgress(intensity);

			float feather = mImageView.getVignetteFeather();
			mSeekBar2.setProgress((int) (feather*100));
		}
	}
}
