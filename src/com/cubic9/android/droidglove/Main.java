package com.cubic9.android.droidglove;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.illposed.osc.OSCListener;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPort;
import com.illposed.osc.OSCPortIn;
import com.illposed.osc.OSCPortOut;

public class Main extends Activity implements SensorEventListener {
	/** numbers for average because of flicker */
	private static final int AVERAGE_AMOUNT = 15;
	/** IP address key for preference */
	private static final String PREF_KEY_IP = "ip";
	/** vibration duration time key for preference */
	private static final String PREF_KEY_VIBRATION_TIME = "vibration_time";
	/** default vibration duration time */
	private static final int DEFALUT_VIBRATION_TIME = 50;
	/** OSC address for sending message from phone to PC */
	private static final String OSC_ADDRESS_TO_PC = "/droidglove_to_pc";
	/** OSC address for sending message from PC to phone */
	private static final String OSC_ADDRESS_TO_PHONE = "/droidglove_to_phone";

	/** view for message to user */
	private TextView mTextViewMessage;
	/** view for IP address */
	private TextView mTextViewIp;

	/*
	 * // for debug
	 * private TextView textViewX; private TextView textViewY; private TextView
	 * textViewZ; private TextView textViewGrip;
	 */
	
	// sensors
	/** Sensor Manager for detect angle */
	private SensorManager mSensorManager;
	/** Wake Lock for preventing sleep */
	private WakeLock mWakeLock;
	/** Vibrator for haptics */
	private Vibrator mVibrator;

	// settings
	/** IP address of PC */
	private String ip;
	/** vibration duration time */
	private int userVibrationTime;

	// sensors are not active soon after launch
	/** flag for sensor is initialized or not  */
	private boolean mInitirizedSensor = false;
	/** counter for initializing sensor */ 
	private int mCountBeforeInit = 0;

	// for getting value of sensors
	/** geomagnetic value */
	private float[] geomagnetic = new float[3];
	/** gravity value */
	private float[] gravity = new float[3];

	// for detecting angles
	/** original angles of phone */
	private float[][] mOrigAngles = new float[3][AVERAGE_AMOUNT];
	/** yaw angle when user calibrate sensor */
	private float initYaw;

	// for detecting grip
	/** point when finger down */
	private float mYPointWhenDown = 0;
	/** grip amount */
	private float mYDiff = 0;

	// for OSC send/receive value
	/** OSC Sender for motion */
	private OSCPortOut mSender;
	/** OSC Receiver for vibration */
	private OSCPortIn mReceiver;

	public Main() {
		super();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// prevent sleep
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
				"DroidGlove");

		// vibrator
		mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

		// sensor
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		// show error dialog if Wi-Fi off and Tethering off.
		WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
		if ((!wifi.isWifiEnabled()) && (!isTethering(wifi))) {
			new AlertDialog.Builder(Main.this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(getString(R.string.dialog_error_title))
					.setMessage(getString(R.string.dialog_error_wifi_off))
					.setPositiveButton(R.string.dialog_error_ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									appEnd();
								}
							}).create().show();
		}

		// get IP address
		getPreferences();

		// check IP address
		if (ip.equals("")) {
			showToast(R.string.toast_error_ip_null);
			Intent intent = new Intent(this, Settings.class);
			startActivityForResult(intent, 0);
		} else if (!ip.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
			showToast(R.string.toast_error_ip_wrong);
			Intent intent = new Intent(this, Settings.class);
			startActivityForResult(intent, 0);
		}

		// init angle
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < AVERAGE_AMOUNT; j++) {
				mOrigAngles[i][j] = 0;
			}
		}

		// show screen
		setContentView(R.layout.main);

		// find views
		mTextViewMessage = (TextView) findViewById(R.id.textViewMessage);
		mTextViewIp = (TextView) findViewById(R.id.textViewIp);
		/*
		 * textViewX = (TextView) findViewById(R.id.textViewX); textViewY =
		 * (TextView) findViewById(R.id.textViewY); textViewZ = (TextView)
		 * findViewById(R.id.textViewZ); textViewGrip = (TextView)
		 * findViewById(R.id.textViewGrip);
		 */
		Button button = (Button) findViewById(R.id.buttonReset);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mInitirizedSensor = false;
				mTextViewMessage.setText(R.string.message_swipe_down);
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();

		// show IP address
		mTextViewIp.setText("IP: " + getIPAddress());

		// prevent sleep
		mWakeLock.acquire();

		// get values of sensors
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_GAME);
		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_GAME);

		// get network information
		InetAddress mRemoteIP = null;
		try {
			mRemoteIP = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {
			new AlertDialog.Builder(Main.this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(getString(R.string.dialog_error_title))
					.setMessage(getString(R.string.dialog_error_get_ip))
					.setPositiveButton(R.string.dialog_error_ok, null).create()
					.show();
			e.printStackTrace();
		}

		// Bring the IP Address and port together to form our OSC Sender
		try {
			mSender = new OSCPortOut(mRemoteIP, OSCPort.defaultSCOSCPort());
		} catch (SocketException e) {
			new AlertDialog.Builder(Main.this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(getString(R.string.dialog_error_title))
					.setMessage(getString(R.string.dialog_error_sender))
					.setPositiveButton(R.string.dialog_error_ok, null).create()
					.show();
			e.printStackTrace();
		}

		try {
			mReceiver = new OSCPortIn(OSCPort.defaultSCOSCPort());
		} catch (SocketException e) {
			new AlertDialog.Builder(Main.this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(getString(R.string.dialog_error_title))
					.setMessage(getString(R.string.dialog_error_receiver))
					.setPositiveButton(R.string.dialog_error_ok, null).create()
					.show();
			e.printStackTrace();
		}
		OSCListener listener = new OSCListener() {
			public void acceptMessage(java.util.Date time, OSCMessage message) {
				Object[] args = message.getArguments();
				int vibrationTime = (Integer) args[0];
				if (vibrationTime == 0) {
					mVibrator.vibrate(userVibrationTime);
				} else {
					mVibrator.vibrate(vibrationTime);
				}
			}
		};
		mReceiver.addListener(OSC_ADDRESS_TO_PHONE, listener);
		mReceiver.startListening();
	}

	@Override
	public void onPause() {
		super.onPause();
		mWakeLock.release();
		mReceiver.stopListening();
		mReceiver.close();
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mReceiver.close();
	}

	@Override
	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, Menu.FIRST, 0, R.string.menu_settings).setIcon(
				android.R.drawable.ic_menu_preferences);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Menu.FIRST:
			Intent intent = new Intent(Main.this, Settings.class);
			startActivityForResult(intent, 0);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		getPreferences();
		// IPアドレスのチェック
		if (ip.equals("")) {
			showToast(R.string.toast_error_ip_null);
		} else if (!ip.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
			showToast(R.string.toast_error_ip_wrong);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mYPointWhenDown = event.getY();
			break;
		case MotionEvent.ACTION_MOVE:
			mYDiff = event.getY() - mYPointWhenDown;
			if (mYDiff < 0) {
				mYDiff = 0;
				mYPointWhenDown = event.getY();
			} else if (mYDiff > 100) {
				mYDiff = 100;
				mYPointWhenDown = event.getY() - 100;
			}
			break;
		case MotionEvent.ACTION_UP:
			mYDiff = 0;
			break;
		default:
			break;
		}
		return true;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		Float[] avgAngles = new Float[3];
		float[] rotationMatrix = new float[9];
		float[] attitude = new float[3];

		switch (event.sensor.getType()) {
		case Sensor.TYPE_MAGNETIC_FIELD:
			geomagnetic = event.values.clone();
			break;
		case Sensor.TYPE_ACCELEROMETER:
			gravity = event.values.clone();
			break;
		}

		if (geomagnetic != null && gravity != null) {
			SensorManager.getRotationMatrix(rotationMatrix, null, gravity,
					geomagnetic);
			SensorManager.getOrientation(rotationMatrix, attitude);

			if (!mInitirizedSensor) {
				if (mCountBeforeInit > 20) {
					mInitirizedSensor = true;
					initYaw = (float) Math.toDegrees(attitude[0]);
				} else {
					mCountBeforeInit++;
				}
			}

			for (int i = 0; i < 3; i++) {
				for (int j = AVERAGE_AMOUNT - 1; j > 0; j--) {
					mOrigAngles[i][j] = mOrigAngles[i][j - 1];
				}
			}

			mOrigAngles[0][0] = (float) Math.toDegrees(attitude[0]) - initYaw;
			mOrigAngles[1][0] = (float) Math.toDegrees(attitude[1]);
			mOrigAngles[2][0] = (float) Math.toDegrees(attitude[2]);

			for (int i = 0; i < 3; i++) {
				avgAngles[i] = 0f;
				for (int j = 0; j < AVERAGE_AMOUNT; j++) {
					avgAngles[i] += mOrigAngles[i][j];
				}
				avgAngles[i] /= AVERAGE_AMOUNT;
			}

			/*
			 * textViewX.setText(avgAngles[0].toString());
			 * textViewY.setText(avgAngles[1].toString());
			 * textViewZ.setText(avgAngles[2].toString());
			 * textViewGrip.setText(Float.toString(mYDiff));
			 */

			// create message for send
			List<Object> valueList = new ArrayList<Object>();
			valueList.add(avgAngles[1]);
			valueList.add(avgAngles[0]);
			valueList.add(-avgAngles[2]);
			valueList.add(Integer.valueOf((int) mYDiff));
			OSCMessage message = new OSCMessage(OSC_ADDRESS_TO_PC, valueList);

			// send
			try {
				mSender.send(message);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	/**
	 * load preferences
	 */
	private void getPreferences() {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());

		ip = sharedPref.getString(PREF_KEY_IP, "");

		try {
			userVibrationTime = Integer.parseInt(sharedPref.getString(
					PREF_KEY_VIBRATION_TIME, ""));
		} catch (NumberFormatException e) {
			userVibrationTime = DEFALUT_VIBRATION_TIME;
			SharedPreferences.Editor editor = sharedPref.edit();
			editor.putString(PREF_KEY_VIBRATION_TIME,
					Integer.toString(DEFALUT_VIBRATION_TIME));
			editor.commit();
		}
	}

	/**
	 * get IP Address of phone
	 * @return IP address String or error message
	 */
	private String getIPAddress() {
		Enumeration<NetworkInterface> interfaces = null;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			new AlertDialog.Builder(Main.this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(getString(R.string.dialog_error_title))
					.setMessage(getString(R.string.dialog_error_get_ip))
					.setPositiveButton(R.string.dialog_error_ok, null).create()
					.show();
			e.printStackTrace();
		}

		while (interfaces.hasMoreElements()) {
			NetworkInterface network = interfaces.nextElement();
			Enumeration<InetAddress> addresses = network.getInetAddresses();

			while (addresses.hasMoreElements()) {
				String address = addresses.nextElement().getHostAddress();

				// If found not 127.0.0.1 and not 0.0.0.0, return it
				if (!"127.0.0.1".equals(address) && !"0.0.0.0".equals(address)
						&& InetAddressUtils.isIPv4Address(address)) {
					return address;
				}
			}
		}

		return "An error occured while getting IP address.";
	}

	/**
	 * detect tethering function is active or not
	 * @param manager WiFi manager
	 * @return active or not
	 */
	private boolean isTethering(final WifiManager manager) {
		Method method = null;
		try {
			method = manager.getClass().getDeclaredMethod("isWifiApEnabled");
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		method.setAccessible(true);
		try {
			return (Boolean) method.invoke(manager);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * show short message by Toast
	 * @param code message code
	 */
	private void showToast(int code) {
		Toast toast = Toast.makeText(Main.this, getString(code),
				Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}

	/**
	 * end app
	 */
	private void appEnd() {
		super.finish();
	}
}