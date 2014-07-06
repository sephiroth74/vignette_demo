package com.example.alessandro.vignette;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.example.alessandro.vignette.log.LoggerFactory;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ObjectAnimator;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

public class ImageViewVignette extends ImageViewTouch {

	private static final String TAG = "ImageViewVignette";
	private static final LoggerFactory.Logger logger = LoggerFactory.getLogger(TAG);
	private float mFeather = 0.7f;

	static enum TouchState {
		None, Center, Left, Top, Right, Bottom, TopLeft, TopRight, BottomLeft, BottomRight
	}

	/** lenght of the control point arc */
	private static final int SWEEP_ANGLE = 8;

	/** position of the control point on the ellipse */
	private static final float RAD = (float) Math.toRadians(45);

	private static final int FADEOUT_DELAY = 3000;

	private float sControlPointTolerance = 20;
	private float sControlPointSize = 12;
	private float sArcDistance = 10;
	private float sGradientInset = 100;

	private final RectF pBitmapRect = new RectF();

	private GestureDetector mGestureDetector;

	private Paint mVignettePaint;
	private Paint mControlPointPaint;
	private Paint mBlackPaint;
	private final Paint mPaint = new Paint();

	private RectF mVignetteRect;
	private TouchState mTouchState;

	final RectF tempRect = new RectF();
	final RectF tempRect2 = new RectF();

	private RadialGradient mGradientShader;
	private Paint mPaintShader;
	private Matrix mGradientMatrix;

	Animator mFadeInAnimator;
	Animator mFadeOutAnimator;

	public ImageViewVignette(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ImageViewVignette(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		initialize(context);
	}

	private void initialize(Context context) {
		mGestureDetector = new GestureDetector(context, getGestureListener());

		final DisplayMetrics metrics = context.getResources().getDisplayMetrics();

		mVignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mVignettePaint.setColor(Color.WHITE);
		mVignettePaint.setStrokeWidth(dp2px(metrics.density, 0.75f));
		mVignettePaint.setStyle(Paint.Style.STROKE);
		mVignettePaint.setDither(true);

		mControlPointPaint = new Paint(mVignettePaint);
		mControlPointPaint.setStrokeWidth(dp2px(metrics.density, 1.5f));

		mBlackPaint = new Paint();
		mBlackPaint.setAntiAlias(true);
		mBlackPaint.setFilterBitmap(false);
		mBlackPaint.setDither(true);

		mGradientMatrix = new Matrix();
		mVignetteRect = new RectF();

		updateBackgroundMask(15);

		mPaintShader = new Paint();
		mPaintShader.setAntiAlias(true);
		mPaintShader.setFilterBitmap(false);
		mPaintShader.setDither(true);
		mPaintShader.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
		updateGradientShader(0.7f, mPaintShader);

		mTouchState = TouchState.None;

		sControlPointSize = dp2px(metrics.density, 4);
		sControlPointTolerance = sControlPointTolerance * 1.5f;

		sArcDistance = dp2px(metrics.density, 3);
		sGradientInset = dp2px(metrics.density, 0);

		setHardwareAccelerated(true);

		mFadeInAnimator = ObjectAnimator.ofFloat(this, "paintAlpha", 0, 255);
		mFadeOutAnimator = ObjectAnimator.ofFloat(this, "paintAlpha", 255, 0);
		mFadeOutAnimator.setStartDelay(FADEOUT_DELAY);

		logger.verbose("sArcDistance: %f", sArcDistance);
		logger.verbose("sControlPointSize: %f", sControlPointSize);
		logger.verbose("sControlPointTolerance: %f", sControlPointTolerance);
		logger.verbose("sGradientInset: %f", sGradientInset);
	}

	public void setHardwareAccelerated(boolean accelerated) {
		if (accelerated) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				if (isHardwareAccelerated()) {
					Paint hardwarePaint = new Paint();
					hardwarePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
					setLayerType(LAYER_TYPE_HARDWARE, hardwarePaint);
				}
				else {
					setLayerType(LAYER_TYPE_SOFTWARE, null);
				}
			}
			else {
				setDrawingCacheEnabled(true);
			}
		}
		else {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				setLayerType(LAYER_TYPE_SOFTWARE, null);
			}
			else {
				setDrawingCacheEnabled(true);
			}
		}
	}

	private float dp2px(final float density, float dp) {
		return density * dp;
	}

	public void setVignetteFeather(float value) {
		updateGradientShader(value, mPaintShader);
		postInvalidate();
	}

	public float getVignetteFeather() {
		return mFeather;
	}

	public void setVignetteIntensity(int value) {
		logger.info("setVignetteIntensity: %d", value);
		updateBackgroundMask(value);
		postInvalidate();
	}

	public int getVignetteIntensity() {
		int alpha = (int) (mBlackPaint.getAlpha() / 2.55);
		int red = Color.red(mBlackPaint.getColor());
		logger.log("alpha: %d, red: %d", alpha, red);
		if (red == 0) {
			return alpha;
		}
		else {
			return - alpha;
		}
	}

	@Override
	protected GestureDetector.OnGestureListener getGestureListener() {
		return new MyGestureListener();
	}

	public RectF getImageRect() {
		if (getDrawable() != null) {
			return new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
		}
		else {
			return null;
		}
	}

	@Override
	protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
		logger.info("onSizeChanged: %dx%d", w, h);
		super.onSizeChanged(w, h, oldw, oldh);
	}

	@Override
	protected void onLayoutChanged(final int left, final int top, final int right, final int bottom) {
		logger.info("onLayoutChanged: %d, %d, %d, %d", left, top, right, bottom);
		super.onLayoutChanged(left, top, right, bottom);
		updateBitmapRect();
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		super.onDraw(canvas);

		if (! mVignetteRect.isEmpty()) {

			// ------------
			// shader
			// ------------

			// save current status
			canvas.saveLayer(pBitmapRect, mPaint, Canvas.ALL_SAVE_FLAG);

			tempRect2.set(mVignetteRect);
			tempRect2.inset(- sGradientInset, - sGradientInset);

			// draw the black background
			canvas.drawRect(pBitmapRect, mBlackPaint);
			canvas.drawOval(tempRect2, mPaintShader);
			canvas.restore();


			// ------------------
			// rest of the UI
			// ------------------
			tempRect2.set(mVignetteRect);
			tempRect2.inset(- sArcDistance, - sArcDistance);

			// main ellipse
			canvas.drawOval(mVignetteRect, mVignettePaint);

			// control points
			mControlPointPaint.setStyle(Paint.Style.STROKE);

			// center control
			canvas.drawCircle(mVignetteRect.centerX(), mVignetteRect.centerY(), sControlPointSize, mControlPointPaint);

			canvas.drawArc(tempRect2, - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);
			canvas.drawArc(tempRect2, 90 - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);
			canvas.drawArc(tempRect2, 180 - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);
			canvas.drawArc(tempRect2, 270 - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);

			tempRect2.inset(sArcDistance * 2, sArcDistance * 2);

			canvas.drawArc(tempRect2, - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);
			canvas.drawArc(tempRect2, 90 - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);
			canvas.drawArc(tempRect2, 180 - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);
			canvas.drawArc(tempRect2, 270 - SWEEP_ANGLE / 2, SWEEP_ANGLE, false, mControlPointPaint);

			float rad = (float) Math.toRadians(45);

			final float radiusX = mVignetteRect.width() / 2 * FloatMath.cos(rad);
			final float radiusY = mVignetteRect.height() / 2 * FloatMath.sin(rad);

			mControlPointPaint.setStyle(Paint.Style.FILL);

			canvas.drawRect(
				mVignetteRect.centerX() - radiusX - sControlPointSize,
				mVignetteRect.centerY() - radiusY - sControlPointSize,
				mVignetteRect.centerX() - radiusX + sControlPointSize,
				mVignetteRect.centerY() - radiusY + sControlPointSize,
				mControlPointPaint
			);

			canvas.drawRect(
				mVignetteRect.centerX() + radiusX - sControlPointSize,
				mVignetteRect.centerY() - radiusY - sControlPointSize,
				mVignetteRect.centerX() + radiusX + sControlPointSize,
				mVignetteRect.centerY() - radiusY + sControlPointSize,
				mControlPointPaint
			);

			canvas.drawRect(
				mVignetteRect.centerX() + radiusX - sControlPointSize,
				mVignetteRect.centerY() + radiusY - sControlPointSize,
				mVignetteRect.centerX() + radiusX + sControlPointSize,
				mVignetteRect.centerY() + radiusY + sControlPointSize,
				mControlPointPaint
			);

			canvas.drawRect(
				mVignetteRect.centerX() - radiusX - sControlPointSize,
				mVignetteRect.centerY() + radiusY - sControlPointSize,
				mVignetteRect.centerX() - radiusX + sControlPointSize,
				mVignetteRect.centerY() + radiusY + sControlPointSize,
				mControlPointPaint
			);
		}
	}

	@SuppressWarnings ("unused")
	public void setPaintAlpha(float value) {
		mVignettePaint.setAlpha((int) value);
		mControlPointPaint.setAlpha((int) value);
		postInvalidate();
	}

	public float getPaintAlpha() {
		return mVignettePaint.getAlpha();
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {

		if (pBitmapRect.isEmpty()) return false;

		mGestureDetector.onTouchEvent(event);

		final int action = event.getAction();
		switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_UP:
				return onUp(event);
		}
		return true;
	}

	@Override
	public boolean onUp(final MotionEvent e) {
		logger.info("onUp");
		setTouchState(TouchState.None);

		mFadeOutAnimator.start();
		return true;
	}

	@Override
	public boolean onDown(final MotionEvent e) {
		logger.info("onDown");

		mFadeOutAnimator.cancel();

		if (getPaintAlpha() != 255) {
			mFadeInAnimator.start();
		}

		if (mVignetteRect.isEmpty()) return false;

		final float x = e.getX();
		final float y = e.getY();
		final RectF rect = new RectF();

		final float radiusX = mVignetteRect.width() / 2 * FloatMath.cos(RAD);
		final float radiusY = mVignetteRect.height() / 2 * FloatMath.sin(RAD);
		final float centerX = mVignetteRect.centerX();
		final float centerY = mVignetteRect.centerY();

		rect.set(
			centerX - radiusX - sControlPointTolerance,
			centerY - radiusY - sControlPointTolerance,
			centerX - radiusX + sControlPointTolerance,
			centerY - radiusY + sControlPointTolerance
		);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.TopLeft);
			return true;
		}

		rect.set(
			centerX + radiusX - sControlPointTolerance,
			centerY - radiusY - sControlPointTolerance,
			centerX + radiusX + sControlPointTolerance,
			centerY - radiusY + sControlPointTolerance
		);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.TopRight);
			return true;
		}

		rect.set(
			centerX + radiusX - sControlPointTolerance,
			centerY + radiusY - sControlPointTolerance,
			centerX + radiusX + sControlPointTolerance,
			centerY + radiusY + sControlPointTolerance
		);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.BottomRight);
			return true;
		}

		rect.set(
			centerX - radiusX - sControlPointTolerance,
			centerY + radiusY - sControlPointTolerance,
			centerX - radiusX + sControlPointTolerance,
			centerY + radiusY + sControlPointTolerance
		);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.BottomLeft);
			return true;
		}

		// left
		rect.set(mVignetteRect.left, mVignetteRect.centerY(), mVignetteRect.left, mVignetteRect.centerY());
		rect.inset(- sControlPointTolerance * 2, - sControlPointTolerance * 2);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.Left);
			return true;
		}

		// right
		rect.set(mVignetteRect.right, mVignetteRect.centerY(), mVignetteRect.right, mVignetteRect.centerY());
		rect.inset(- sControlPointTolerance * 2, - sControlPointTolerance * 2);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.Right);
			return true;
		}

		// top
		rect.set(mVignetteRect.centerX(), mVignetteRect.top, mVignetteRect.centerX(), mVignetteRect.top);
		rect.inset(- sControlPointTolerance * 2, - sControlPointTolerance * 2);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.Top);
			return true;
		}

		// bottom
		rect.set(mVignetteRect.centerX(), mVignetteRect.bottom, mVignetteRect.centerX(), mVignetteRect.bottom);
		rect.inset(- sControlPointTolerance * 2, - sControlPointTolerance * 2);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.Bottom);
			return true;
		}

		// center
		rect.set(mVignetteRect.centerX(), mVignetteRect.centerY(), mVignetteRect.centerX(), mVignetteRect.centerY());
		rect.inset(- sControlPointTolerance * 2, - sControlPointTolerance * 2);
		if (rect.contains(x, y)) {
			setTouchState(TouchState.Center);
			return true;
		}

		return true;
	}

	@Override

	public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
		//logger.info("onScroll, state: %s (%.2fx%.2f)", mTouchState, distanceX, distanceY);

		if (mVignetteRect.isEmpty()) return false;

		tempRect.set(mVignetteRect);

		float max;

		switch (mTouchState) {
			case None:
				break;

			case Center:

				if (pBitmapRect.contains(tempRect.centerX() - distanceX, tempRect.centerY() - distanceY)) {
					tempRect.offset(- distanceX, - distanceY);
				}
				break;

			case Left:
				tempRect.inset(- distanceX, 0);
				break;
			case Right:
				tempRect.inset(distanceX, 0);
				break;

			case Top:
				tempRect.inset(0, - distanceY);
				break;

			case Bottom:
				tempRect.inset(0, distanceY);
				break;

			case TopLeft:
				max = Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : distanceY;
				tempRect.inset(- max, - max);
				break;

			case TopRight:
				max = Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : - distanceY;
				tempRect.inset(max, max);
				break;

			case BottomLeft:
				max = Math.abs(distanceX) > Math.abs(distanceY) ? - distanceX : distanceY;
				tempRect.inset(max, max);
				break;

			case BottomRight:
				max = Math.abs(distanceX) > Math.abs(distanceY) ? distanceX : distanceY;
				tempRect.inset(max, max);
				break;
		}

		if (tempRect.width() > sControlPointTolerance && tempRect.height() > sControlPointTolerance) {
			mVignetteRect.set(tempRect);
		}

		updateGradientMatrix(mVignetteRect);


		ViewCompat.postInvalidateOnAnimation(this);
		return true;
	}

	private void updateGradientMatrix(RectF rect) {
		mGradientMatrix.reset();
		mGradientMatrix.postTranslate(rect.centerX(), rect.centerY());
		mGradientMatrix.postScale(rect.width() / 2, rect.height() / 2, rect.centerX(), rect.centerY());
		mGradientShader.setLocalMatrix(mGradientMatrix);
	}

	private void updateGradientShader(float value, final Paint paint) {
		logger.info("updateGradientShader: %f", value);
		mFeather = value;
		final int[] colors = new int[]{0xff000000, 0xff000000, 0};
		final float[] anchors = new float[]{0, mFeather, 1};

		mGradientShader = new android.graphics.RadialGradient(
			0, 0, 1, colors, anchors, Shader.TileMode.CLAMP
		);
		paint.setShader(mGradientShader);
		updateGradientMatrix(mVignetteRect);
	}

	private void updateBackgroundMask(int value) {
		if (value >= 0) {
			mBlackPaint.setColor(Color.BLACK);
		}
		else {
			mBlackPaint.setColor(Color.WHITE);
		}

		value = Math.max(Math.min(Math.abs(value), 100), 0);
		value *= 2.55;
		logger.log("setAlpha: %d", value);
		mBlackPaint.setAlpha(value);
	}

	private void setTouchState(TouchState newState) {
		if (newState != mTouchState) {
			logger.info("setTouchState: %s", newState);
			mTouchState = newState;
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		logger.info("onSaveInstanceState");
		logger.log("pBitmapRect: %s", pBitmapRect);

		SavedState state = new SavedState(super.onSaveInstanceState());
		state.mCurrentRect = pBitmapRect;
		return state;
	}

	@Override
	protected void onRestoreInstanceState(final Parcelable state) {
		logger.info("onRestoreInstanceState");
		SavedState savedState = (SavedState) state;

		super.onRestoreInstanceState(savedState.getSuperState());

		pBitmapRect.set(savedState.mCurrentRect);
		logger.log("pBitmapRect: %s", pBitmapRect);
	}

	@Override
	protected void onImageMatrixChanged() {
		logger.info("onImageMatrixChanged");
		super.onImageMatrixChanged();
	}

	@Override
	protected void onDrawableChanged(final Drawable drawable) {
		logger.info("onDrawableChanged");
		super.onDrawableChanged(drawable);
	}

	private void updateBitmapRect() {
		logger.info("updateBitmapRect");

		mTouchState = TouchState.None;

		if (null == getDrawable()) {
			mVignetteRect.setEmpty();
			pBitmapRect.setEmpty();
			return;
		}

		RectF rect = getBitmapRect();
		final boolean rect_changed = ! pBitmapRect.equals(rect);

		logger.log("rect: %s", rect);
		logger.log("pBitmapRect: %s", pBitmapRect);
		logger.log("pBitmapRect.isEmpty: %b", pBitmapRect.isEmpty());

		logger.log("rect_changed: %b", rect_changed);

		if (null != rect) {
			if (rect_changed) {
				if (! pBitmapRect.isEmpty()) {
					float old_left = pBitmapRect.left;
					float old_top = pBitmapRect.top;
					float old_width = pBitmapRect.width();
					float old_height = pBitmapRect.height();

					mVignetteRect.inset(- (rect.width() - old_width) / 2, - (rect.height() - old_height) / 2);
					mVignetteRect.offset(rect.left - old_left, rect.top - old_top);
					mVignetteRect.offset((rect.width() - old_width) / 2, (rect.height() - old_height) / 2);
				}
				else {
					mVignetteRect.set(rect);
					mVignetteRect.inset(sControlPointTolerance, sControlPointTolerance);
				}
			}
			pBitmapRect.set(rect);
		}
		else {
			// rect is null
			pBitmapRect.setEmpty();
			mVignetteRect.setEmpty();
		}

		logger.verbose("vignette: %s", mVignetteRect);
		logger.verbose("vignette.size: %.2fx%.2f", mVignetteRect.width(), mVignetteRect.height());

		updateGradientMatrix(mVignetteRect);

		setPaintAlpha(255);
		mFadeOutAnimator.start();
	}

	static float hypotenuse(RectF rect) {
		return FloatMath.sqrt(FloatMath.pow(rect.right - rect.left, 2) + FloatMath.pow(rect.bottom - rect.top, 2));
	}

	static float hypotenuse(float left, float top, float right, float bottom) {
		return FloatMath.sqrt(FloatMath.pow(right - left, 2) + FloatMath.pow(bottom - top, 2));
	}

	private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(final MotionEvent e) {
			return ImageViewVignette.this.onDown(e);
		}

		@Override
		public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
			return ImageViewVignette.this.onScroll(e1, e2, distanceX, distanceY);
		}

		@Override
		public boolean onSingleTapUp(final MotionEvent e) {return false;}

		@Override
		public boolean onFling(
			final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {return false;}

		@Override
		public boolean onDoubleTap(final MotionEvent e) {return false;}

		@Override
		public void onLongPress(final MotionEvent e) {}
	}


	public static class SavedState extends BaseSavedState {

		RectF mCurrentRect;

		public SavedState(final Parcelable superState) {
			super(superState);
		}

		private SavedState(Parcel in) {
			super(in);
			mCurrentRect = new RectF(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
		}

		@Override
		public void writeToParcel(final Parcel dest, final int flags) {
			super.writeToParcel(dest, flags);
			if (null != mCurrentRect) {
				dest.writeFloat(mCurrentRect.left);
				dest.writeFloat(mCurrentRect.top);
				dest.writeFloat(mCurrentRect.right);
				dest.writeFloat(mCurrentRect.bottom);
			}
			else {
				dest.writeFloat(0);
				dest.writeFloat(0);
				dest.writeFloat(0);
				dest.writeFloat(0);
			}
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {

			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}

		};
	}
}
