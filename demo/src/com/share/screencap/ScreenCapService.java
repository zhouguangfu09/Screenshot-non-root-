package com.share.screencap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import pl.polidea.asl.IScreenshotProvider;
import pl.polidea.asl.ScreenshotService;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;


public class ScreenCapService extends Service  
{
	private String TAG = "ZGF";

	private String pUsername="phone";
	private String serverUrl="";
	private int serverPort=8888;

	private boolean isConnectServerBoolean = false;
	private boolean isSendScreen = false;	
	private boolean isASLReady = false;

	private boolean isStartService = false;


	//Call Native library
	public ServiceConnection aslServiceConn = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG,"ASL service has unexpectedly disconnected."); 
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG,"ASL service connected."); 
			aslProvider = IScreenshotProvider.Stub.asInterface(service);
			isASLReady = true;
		}
	};
	public IScreenshotProvider aslProvider = null;

	@Override  
	public void onCreate()  
	{  
		Log.i(TAG, "Service onCreate--->");          
		super.onCreate();  

		pUsername = getLocalIpAddress();
		isStartService = true;

		Intent intent = new Intent();
		intent.setClass(this, ScreenshotService.class);
		boolean result = bindService (intent, aslServiceConn, Context.BIND_AUTO_CREATE);
		if (!result) {
			Log.i(TAG,"ASL service bind failed."); 
		}else{
			Log.i(TAG,"ASL service bind successfully."); 
		}

	}

	@Override  
	public void onStart(Intent intent, int startId)  
	{ 	
		Log.i(TAG,"Screen capture service start.");
		if(isSendScreen){
			Log.i(TAG,"Screen capture service has already started.");
			return ;
		}

		Log.i(TAG,"Just wait 2s for ASL init.");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		SharedPreferences preferences=getSharedPreferences("configs", Context.MODE_PRIVATE);
		serverUrl = preferences.getString("IP", "");
		serverPort = preferences.getInt("port", 8888);
		Log.i(TAG, "serverUrl: "+serverUrl); 
		Log.i(TAG, "serverPort: "+serverPort); 

		StartConnect();

		MainThread mt = new MainThread();
		mt.start();		
	}  

	@Override  
	public void onDestroy()  
	{  
		Log.i(TAG,"----------------------------------------------------"); 
		Log.i(TAG,"SendScreenService stopped."); 
		Log.i(TAG,"Unbind ASL service."); 
		unbindService(aslServiceConn);	

		StopConnect();
		isStartService = false;
		isASLReady = false;
		isConnectServerBoolean = false;
		isSendScreen = false;
		super.onDestroy();  
	}  

	@Override 
	public IBinder onBind(Intent intent)  
	{  
		return null;  
	}  


	public void StartConnect(){
		if(!isConnectServerBoolean){
			Log.i(TAG,"Start connecting."); 
			isConnectServerBoolean = true;
			Thread th = new SendCommandThread("PHONECONNECT|"+pUsername+"|");
			th.start(); 
		}
	}

	public void StopConnect(){
		if(isConnectServerBoolean){
			Log.i(TAG,"Stop connecting."); 
			isConnectServerBoolean = false;
			Thread th = new SendCommandThread("PHONEDISCONNECT|"+pUsername+"|");
			th.start(); 	
		}
	}

	class SendCommandThread extends Thread{
		private String command;
		public SendCommandThread(String command){
			this.command=command;
		}

		public void run(){
			Log.i(TAG,"Send command thread started."); 
			try {
				Socket socket=new Socket(serverUrl,serverPort);
				PrintWriter out = new PrintWriter(socket.getOutputStream());
				out.println(command);
				out.flush();
				Log.i(TAG,"Send command thread stop."); 
				isSendScreen = true;
				
				out.close();
				socket.close();
				out = null;
				socket = null;
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}  
		}
	}

	class MainThread extends Thread{
		private byte byteBuffer[] = new byte[1024];
		private OutputStream outsocket = null;	
		private int i =0;

		public void run(){
			Log.i(TAG,"Send data to PC thread created."); 

			while(isStartService){
				//0 --- connect the socket.
				if(isConnectServerBoolean && isASLReady){
					if(isSendScreen){
						Log.i(TAG,"isscreen-----------------------."); 

						long startTime=System.currentTimeMillis();  
						
						if (aslProvider == null){
							Log.i(TAG,"Screenshot interface not (yet) available.");   
						} else
							try {
								if (!aslProvider.isAvailable()){
									Log.i(TAG,"Native service not found."); 
								}else {
									byte[] bt = aslProvider.writeImageOutputStream();
									Log.i(TAG,"------------"+i+"---------------");
									i++;
									
									Log.i(TAG,"Screenshot taken successfully."); 	
									long endTime=System.currentTimeMillis();  
									Log.i("ZGF","Get raw data---raw data£º "+(endTime-startTime)+"ms"); 

									//Send file thread start.
									Log.i(TAG,"Send file thread start."); 
									startTime=System.currentTimeMillis();  

									Socket tempSocket = new Socket(serverUrl, serverPort);
									outsocket = tempSocket.getOutputStream();

									String msg=java.net.URLEncoder.encode("PHONESCREEN|"+pUsername+"|","utf-8");
									byte[] buffer= msg.getBytes();
									outsocket.write(buffer);

									ByteArrayInputStream inputstream = new ByteArrayInputStream(bt);
									int amount;
									while ((amount = inputstream.read(byteBuffer)) != -1) {
										outsocket.write(byteBuffer, 0, amount);
									}
									tempSocket.close(); 
									Log.i(TAG,"Send file thread end."); 
									
									bt = null;
									inputstream = null;
									outsocket = null;
									System.gc();

									endTime=System.currentTimeMillis(); 
									Log.i("ZGF","Wifi send raw data ---raw data£º "+(endTime-startTime)+"ms"); 
									Log.i("ZGF","*********************************************************"); 
								}
							} catch (RemoteException e) {
								Log.i("ZGF","Remote exception occured."); 
							} catch (UnknownHostException e) {
								Log.i("ZGF","UnknownHost exception occured."); 
							} catch (UnsupportedEncodingException e) {
								Log.i("ZGF","UnsupportedEncoding exception occured."); 
							} catch (IOException e) {
								Log.i("ZGF","IOException occured."); 
							}
					}

				}
			}
		}
	}
	
	   public String getLocalIpAddress() {  
	        try {  
	            for (Enumeration<NetworkInterface> en = NetworkInterface  
	                    .getNetworkInterfaces(); en.hasMoreElements();) {  
	                NetworkInterface intf = en.nextElement();  
	                for (Enumeration<InetAddress> enumIpAddr = intf  
	                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {  
	                    InetAddress inetAddress = enumIpAddr.nextElement();  
	                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {  
	                        return inetAddress.getHostAddress().toString();  
	                    }  
	                }  
	            }  
	        } catch (SocketException ex) {  
	            Log.e("WifiPreference IpAddress", ex.toString());  
	        }  
	        return null;  
	    }
}

