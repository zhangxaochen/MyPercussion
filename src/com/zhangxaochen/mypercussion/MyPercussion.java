package com.zhangxaochen.mypercussion;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.widget.ImageView;

public class MyPercussion extends Activity {

	// 用于 debug：
	private ImageView _imageViewDebugDraw;

	private SensorManager _sm;
	private Sensor _laSensor;
	private Sensor _gSensor;
	private LinearAccListener _laListener = new LinearAccListener(this);

	// private GravityListener _gListener = new GravityListener();

	// UI 控件在此实例化
	void initWidgets() {
		_imageViewDebugDraw = (ImageView) findViewById(R.id.imageViewDebugDraw);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initWidgets();

		_sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		_laSensor = _sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		_gSensor = _sm.getDefaultSensor(Sensor.TYPE_GRAVITY);

		_laListener.enableDebugDraw(_imageViewDebugDraw);
	}// onCreate

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	protected void onResume() {
		System.out.println("onResume");
		super.onResume();

		_sm.registerListener(_laListener, _laSensor,
				SensorManager.SENSOR_DELAY_GAME);
		_sm.registerListener(_laListener, _gSensor,
				SensorManager.SENSOR_DELAY_GAME);

	}

	@Override
	protected void onPause() {
		System.out.println("onPause");
		super.onPause();
		_sm.unregisterListener(_laListener);
		// _sm.unregisterListener(_gListener);
	}

}// MainActivity

/**
 * <ol>
 * <li>
 * 只监听 Sensor.TYPE_LINEAR_ACCELERATION</li>
 * <li>
 * 可以 enableDebugDraw 开启调试绘制</li>
 * </ol>
 * 
 * @author zhangxaochen
 * 
 */
class LinearAccListener implements SensorEventListener {
	private boolean _beatEnabled = true;
	// 极小值：
	private float _minimumValue = 0;

	// 极小值处的时间戳：
	long _timeStamp;

	// 极大极小值时间差不超过：
	private final long _epsilonDt = 200;

	// 用于计算加速值的种类：
	private final int TYPE_TOTAL_LINEAR_ACC = 0;// 合加速度，非负数
	private final int TYPE_XAXIS = 1;// X负向分量，有正负

	// 调试绘制用：
	private boolean _debugDrawEnabled = false;
	private ImageView _debugDrawView;
	private float _viewWidth, _viewHeight;
	private Canvas _canvas = new Canvas();
	private Bitmap _bitmap;
	private float _maxX, _lastX;
	private int _speed = 1;
	private Paint _paint = new Paint();
	private static final int _colorBg = 0xffaaaaaa;

	// // 只监听此 type
	// private int _type = Sensor.TYPE_LINEAR_ACCELERATION;

	// 低于此阈值不判定
	private final float _accThreshold = 5;
	private float _lastAcc = 0;
	private float _last2Acc = 0; // 上上次 采样

	private final float _maxAcc = 20; // 手持最大加速度，不精确, 实测一般 <36

	/**
	 * 目前用于 MediaPlayer.create(<u><b>Context </b></u>...)
	 */
	private Context _ctx;

	public LinearAccListener(Context context) {
		_ctx = context;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// System.out.println("onSensorChanged");

		float curAcc = getAccByType(event.values, TYPE_XAXIS);
		switch (event.sensor.getType()) {
		case Sensor.TYPE_LINEAR_ACCELERATION:

			float base = _viewHeight / 2;
			float factor = (float) _viewHeight / 4 / _maxAcc;
			if (_debugDrawEnabled) {
				// 清屏，重置背景：
				if (_lastX >= _maxX) {
					System.out.println("_lastX >= _maxX: " + _lastX + ","
							+ _maxX);
					_lastX = 0;
					_canvas.drawColor(_colorBg);
					for (int i = 1; i <= 3; i++) {
						float hh = _viewHeight * i / 4;
						_canvas.drawLine(0, hh, _maxX, hh, _paint);
					}
				}

				// 绘制曲线：
				_paint.setColor(Color.BLUE);
				float newX = _lastX + _speed;
				_canvas.drawLine(_lastX, base + _lastAcc * factor, newX, base
						+ curAcc * factor, _paint);
				// System.out.println("_lastX, newX: "+_lastX+","+newX);
				_paint.reset();

				_lastX = newX;
			}

			if (-_lastAcc > _accThreshold
					&& isMinimum(_last2Acc, _lastAcc, curAcc)) {
				System.out.println("--------isMinimum");
				_beatEnabled = true;
				_minimumValue = _lastAcc;
				_timeStamp = SystemClock.uptimeMillis();
			}

			if (_lastAcc > _accThreshold
					&& isMaximum(_last2Acc, _lastAcc, curAcc) && _beatEnabled
					&& (SystemClock.uptimeMillis() - _timeStamp) < _epsilonDt) {
				System.out.println("+++++++++++isMaximum");
				// System.out.println("duration: "+(SystemClock.uptimeMillis()-_timeStamp));

				float vol = (Math.abs(_minimumValue) - _accThreshold) / _maxAcc;
				vol = (float) Math.sqrt(vol);
				System.out.println("vol:= " + vol);
				beatDrum(vol);

				if (_debugDrawEnabled) {
					_paint.setStyle(Style.STROKE);
					_paint.setColor(Color.RED);
					_paint.setStrokeWidth(3);

					float xx = _lastX, yy = base + curAcc * factor;
					_canvas.drawCircle(xx, yy, 20, _paint);
					_canvas.drawPoint(xx, yy, _paint);

					_paint.reset();
				}

				_beatEnabled = false;
			}

			// if (_lastAcc < 0 && curAcc > 0 && _beatEnabled) {
			// float vol = (Math.abs(_minimumValue) - _accThreshold) / _maxAcc;
			// vol = (float) Math.sqrt(vol);
			// System.out.println("vol:= " + vol);
			// beatDrum(vol);
			//
			// if (_debugDrawEnabled) {
			// _paint.setStyle(Style.STROKE);
			// _paint.setColor(Color.RED);
			// _paint.setStrokeWidth(3);
			//
			// float xx = _lastX, yy = base + curAcc * factor;
			// _canvas.drawCircle(xx, yy, 20, _paint);
			// _canvas.drawPoint(xx, yy, _paint);
			//
			// _paint.reset();
			// }
			//
			// _beatEnabled = false;
			// }

			_last2Acc = _lastAcc;
			_lastAcc = curAcc;

			// _debugDrawView.draw(_canvas);
			_debugDrawView.setImageBitmap(_bitmap);
			// _debugDrawView.setBackground(new BitmapDrawable(_bitmap));//no
			// such method error

			break;

		case Sensor.TYPE_GRAVITY:
			// System.out.println("case Sensor.TYPE_GRAVITY:");
			break;

		default:
			break;
		}// switch
	}// onSensorChanged

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	public void enableDebugDraw(ImageView debugDrawView) {
		_debugDrawEnabled = true;
		_debugDrawView = debugDrawView;

		// _viewWidth = _debugDrawView.getWidth();
		// _viewHeight = _debugDrawView.getHeight();
		_viewWidth = 333;
		_viewHeight = 555;
		_lastX = _maxX = _viewWidth;
		System.out.println("_viewWidth, height: " + _viewWidth + ","
				+ _viewHeight);
		_bitmap = Bitmap.createBitmap((int) _viewWidth, (int) _viewHeight,
				Bitmap.Config.RGB_565);
		// _bitmap=Bitmap.createBitmap((int)333, (int)555,
		// Bitmap.Config.RGB_565);
		_canvas.setBitmap(_bitmap);

		// debugDrawView.draw(canvas);
		// debugDrawView.setBackground(new BitmapDrawable())
	}

	void beatDrum(float volume) {
		MediaPlayer mp;
		mp = MediaPlayer.create(_ctx, R.raw.tom3);
		mp.setOnCompletionListener(new OnCompletionListener() {

			@Override
			public void onCompletion(MediaPlayer mp) {
				mp.release();
			}
		});
		mp.setVolume(volume, 0);
		mp.start();
	}

	private float getAccByType(float[] values, int type) {
		float res = 0;
		switch (type) {
		case TYPE_TOTAL_LINEAR_ACC:
			for (float v : values) {
				res += v * v;
			}
			res = (float) Math.sqrt(res);
			break;
		case TYPE_XAXIS:
			res = values[0];
		default:
			break;
		}
		return res;
	}

	/**
	 * 判定传入的第二参数 value 是否为极大（小）值
	 * 
	 * @param prevValue
	 *            前一个点
	 * @param value
	 *            当前待判定点
	 * @param succValue
	 *            后一个点
	 * @return
	 */
	private boolean isExtremum(float prevValue, float value, float succValue) {
		if ((value - prevValue) * (value - succValue) > 0)
			return true;
		else
			return false;
	}

	private boolean isMaximum(float prevValue, float value, float succValue) {
		if (value > prevValue && value > succValue)
			return true;
		return false;
	}

	private boolean isMinimum(float prevValue, float value, float succValue) {
		if (value < prevValue && value < succValue)
			return true;
		return false;
	}
}// LinearAccListener

