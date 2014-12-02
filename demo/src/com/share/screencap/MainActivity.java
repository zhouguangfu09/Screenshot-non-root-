package com.share.screencap;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.os.*;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;

public class MainActivity extends Activity {

	/*
	 * The ImageView used to display taken screenshots.
	 */
	private String TAG = "ZGF";

	private Button startServiceBtn, stopServiceBtn; 
	private Button resetButton, saveinfoButton;
	private EditText IPEditText,portEditText;

	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		checkNetwork();

		startServiceBtn = (Button)findViewById(R.id.start_service); 
		stopServiceBtn = (Button)findViewById(R.id.stop_service);
		resetButton = (Button)findViewById(R.id.reset);
		saveinfoButton = (Button)findViewById(R.id.saveinfo);
		IPEditText = (EditText)findViewById(R.id.IPAddress);
		portEditText = (EditText)findViewById(R.id.port);
		stopServiceBtn.setEnabled(false);
		saveinfoButton.setEnabled(false);

		initConfig();
//		InstallNativeService();

		startServiceBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {	
				SharedPreferences preferences=getSharedPreferences("configs", Context.MODE_PRIVATE);
				boolean isSave = preferences.getBoolean("isSave", false);

				if(isSave){
					Intent intent = new Intent();
					intent.setClass(MainActivity.this, ScreenCapService.class);
					startService(intent);
					startServiceBtn.setEnabled(false);
					stopServiceBtn.setEnabled(true);

					IPEditText.setEnabled(false);
					portEditText.setEnabled(false);
					resetButton.setEnabled(false);
					saveinfoButton.setEnabled(false);
				}else{
					Toast.makeText(MainActivity.this, "IP's or Port's format is error!£¡", Toast.LENGTH_SHORT).show();
				}
			}
		});

		stopServiceBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				intent.setClass(MainActivity.this, ScreenCapService.class);
				stopService(intent);
				stopServiceBtn.setEnabled(false);
				startServiceBtn.setEnabled(true);

				IPEditText.setEnabled(true);
				portEditText.setEnabled(true);
				resetButton.setEnabled(true);
			}
		});


		resetButton.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				IPEditText.setText("");
				portEditText.setText("");

				SharedPreferences preferences=getSharedPreferences("configs",Context.MODE_PRIVATE);
				Editor editor=preferences.edit();
				editor.putString("IP", "");
				editor.putInt("port", 8888);
				editor.putBoolean("isSave", false);
				editor.commit();	

				resetButton.setEnabled(false);
				saveinfoButton.setEnabled(true);
				IPEditText.setEnabled(true);
				portEditText.setEnabled(true);
				startServiceBtn.setEnabled(false);
			}
		});

		saveinfoButton.setOnClickListener(new OnClickListener() {	
			@Override
			public void onClick(View v) {	
				String IPAddress = IPEditText.getText().toString();
				String port = portEditText.getText().toString();

				if(IPAddress.equals("") || port.equals("")){
					Toast.makeText(MainActivity.this, "IP or port cannot be empty!", Toast.LENGTH_SHORT).show();
				}else if(!isIPAddress(IPAddress) || !isNumeric(port)){
					Toast.makeText(MainActivity.this, "IP's or Port's format is error!", Toast.LENGTH_SHORT).show();
				}else{

					SharedPreferences preferences=getSharedPreferences("configs",Context.MODE_PRIVATE);
					Editor editor=preferences.edit();
					editor.putString("IP", IPAddress);
					editor.putInt("port", Integer.valueOf(port));
					editor.putBoolean("isSave", true);
					editor.commit();					
					Toast.makeText(MainActivity.this, "Save successfully...", Toast.LENGTH_SHORT).show();

					saveinfoButton.setEnabled(false);
					IPEditText.setEnabled(false);
					portEditText.setEnabled(false);
					resetButton.setEnabled(true);
					startServiceBtn.setEnabled(true);
				}
			}
		});
	}

	private void initConfig(){
		SharedPreferences preferences=getSharedPreferences("configs", Context.MODE_PRIVATE);
		String IPAddress = preferences.getString("IP", "");
		int port = preferences.getInt("port", 8888);
		boolean isSave = preferences.getBoolean("isSave", false);

		if(isSave){
			IPEditText.setText(IPAddress);
			portEditText.setText(String.valueOf(port));

			IPEditText.setEnabled(false);
			portEditText.setEnabled(false);
			startServiceBtn.setEnabled(true);
		}else{
			startServiceBtn.setEnabled(false);
			saveinfoButton.setEnabled(true);
			return ;
		}
	}

	public boolean isNumeric(String str) {
		Pattern pattern = Pattern.compile("[0-9]*");
		Matcher isNum = pattern.matcher(str);
		if (!isNum.matches()) {
			return false;
		}
		return true;
	}

	public boolean isIPAddress(String str) {		
		Pattern pattern = Pattern.compile("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?");
		Matcher isIPAddress = pattern.matcher(str);
		if (!isIPAddress.matches()) {
			return false;
		}
		return true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void checkNetwork(){
		ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		//wifi
		State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		if(wifi==State.CONNECTED||wifi==State.CONNECTING){
			return;
		}else{
			startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			AlertDialog.Builder mDialog = new AlertDialog.Builder(MainActivity.this);
			mDialog.setTitle("Exit");
			mDialog.setMessage("Sure to exit£¿The programe will run in background.");
			mDialog.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			});
			mDialog.setNegativeButton("Cancle", null);
			mDialog.create().show();
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId())
		{
		case 0:
			Toast.makeText(MainActivity.this, "About!", Toast.LENGTH_LONG).show();
			break;
		case 1:
			android.os.Process.killProcess(android.os.Process.myPid());
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	private void InstallNativeService()
	{

		if(isRoot()){
			java.lang.Process process;
			try {
				process = Runtime.getRuntime().exec("su");
				DataOutputStream os = new DataOutputStream(process.getOutputStream());
				os.writeBytes("chmod 0777 /data/local/asl-native" + "\n");
				os.writeBytes("/data/local/asl-native" + "\n");
				os.writeBytes("exit\n");
				os.flush();
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else{
			Log.i(TAG,"The device has not been root."); 
		}

	}

	public boolean isRoot(){
		boolean bool = false;

		try{
			if ((!new File("/system/bin/su").exists()) && (!new File("/system/xbin/su").exists())){
				bool = false;
			} else {
				bool = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return bool;
	}


}