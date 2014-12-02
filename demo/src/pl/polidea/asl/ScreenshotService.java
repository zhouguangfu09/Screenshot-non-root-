package pl.polidea.asl;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.*;
import java.nio.channels.SocketChannel;
import java.security.InvalidParameterException;
import java.util.UUID;

import pl.polidea.asl.IScreenshotProvider;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Matrix;
import android.os.*;
import android.util.Log;
import android.view.*;

public class ScreenshotService extends Service {

	/*
	 * Action name for intent used to bind to service.
	 */
	public static final String BIND = "pl.polidea.asl.ScreenshotService.BIND";  

	/*
	 * Name of the native process.
	 */
	private static final String NATIVE_PROCESS_NAME = "asl-native"; 

	/*
	 * Port number used to communicate with native process.
	 */
	private static final int PORT = 42380;

	/*
	 * Timeout allowed in communication with native process.
	 */
	private static final int TIMEOUT = 1000;  

	/*
	 * Directory where screenshots are being saved.
	 */
	private static String SCREENSHOT_FOLDER = "/sdcard/screens/";


	/*
	 * An implementation of interface used by clients to take screenshots.
	 */
	private final IScreenshotProvider.Stub mBinder = new IScreenshotProvider.Stub() {

		@Override
		public String takeScreenshot() throws RemoteException {
			try {
				return ScreenshotService.this.takeScreenshot();
			}
			catch(Exception e) { return null; }
		}

		@Override
		public boolean isAvailable() throws RemoteException {
			return isNativeRunning();
		}

		@Override
		public byte[] writeImageOutputStream() throws RemoteException {
			return ScreenshotService.this.writeImageOutputStream();
		}
	};

	@Override
	public void onCreate() {
		Log.i("service", "Screenshot Service created."); 
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}


	/*
	 * Checks whether the internal native application is running,
	 */
	private boolean isNativeRunning() {
		try {
			Socket sock = new Socket();
			sock.connect(new InetSocketAddress("localhost", PORT), 10);	// short timeout
		}
		catch (Exception e) {
			return false;
		}
		return true;
		//		ActivityManager am = (ActivityManager)getSystemService(Service.ACTIVITY_SERVICE);
		//		List<ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
		//
		//		if (am != null) {
		//			for (ActivityManager.RunningAppProcessInfo rapi : ps) {
		//				if (rapi.processName.contains(NATIVE_PROCESS_NAME))
		//					// native application found
		//					return true;
		//			}
		//
		//		}
		//		return false;
	}


	/*
	 * Internal class describing a screenshot.
	 */
	class Screenshot {
		public Buffer pixels;
		public int width;
		public int height;
		public int bpp;

		public boolean isValid() {
			if (pixels == null || pixels.capacity() == 0 || pixels.limit() == 0) return false;
			if (width <= 0 || height <= 0)	return false;
			return true;
		}
	}


	/*
	 * Determines whether the phone's screen is rotated.
	 */
	private int getScreenRotation()  {
		WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
		Display disp = wm.getDefaultDisplay();

		// check whether we operate under Android 2.2 or later
		try {
			Class<?> displayClass = disp.getClass();
			Method getRotation = displayClass.getMethod("getRotation");
			int rot = ((Integer)getRotation.invoke(disp)).intValue();

			switch (rot) {
			case Surface.ROTATION_0:	return 0;
			case Surface.ROTATION_90:	return 90;
			case Surface.ROTATION_180:	return 180;
			case Surface.ROTATION_270:	return 270;
			default:					return 0;
			}
		} catch (NoSuchMethodException e) {
			// no getRotation() method -- fall back to dispation()
			int orientation = disp.getOrientation();

			// Sometimes you may get undefined orientation Value is 0
			// simple logic solves the problem compare the screen
			// X,Y Co-ordinates and determine the Orientation in such cases
			if(orientation==Configuration.ORIENTATION_UNDEFINED){

				Configuration config = getResources().getConfiguration();
				orientation = config.orientation;

				if(orientation==Configuration.ORIENTATION_UNDEFINED){
					//if height and widht of screen are equal then
					// it is square orientation
					if(disp.getWidth()==disp.getHeight()){
						orientation = Configuration.ORIENTATION_SQUARE;
					}else{ //if widht is less than height than it is portrait
						if(disp.getWidth() < disp.getHeight()){
							orientation = Configuration.ORIENTATION_PORTRAIT;
						}else{ // if it is not any of the above it will defineitly be landscape
							orientation = Configuration.ORIENTATION_LANDSCAPE;
						}
					}
				}
			}

			return orientation == 1 ? 0 : 90; // 1 for portrait, 2 for landscape
		} catch (Exception e) {
			return 0; // bad, I know ;P
		}
	}


	/*
	 * Communicates with the native service and retrieves a screenshot from it
	 * as a 2D array of bytes.
	 */
	private Screenshot retreiveRawScreenshot() throws Exception {
		try {
			// connect to native application
			Socket s = new Socket();
			s.connect(new InetSocketAddress("localhost", PORT), TIMEOUT);

			// send command to take screenshot
			OutputStream os = s.getOutputStream();
			os.write("SCREEN".getBytes("ASCII"));

			// retrieve response -- first the size and BPP of the screenshot
			InputStream is = s.getInputStream();
			StringBuilder sb = new StringBuilder();
			int c;
			while ((c = is.read()) != -1) {
				if (c == 0) break;
				sb.append((char)c);
			}

			// parse it
			String[] screenData = sb.toString().split(" ");
			if (screenData.length >= 3) {
				Screenshot ss = new Screenshot();
				ss.width = Integer.parseInt(screenData[0]);
				ss.height = Integer.parseInt(screenData[1]);
				ss.bpp = Integer.parseInt(screenData[2]);

				// retreive the screenshot
				// (this method - via ByteBuffer - seems to be the fastest)
				ByteBuffer bytes = ByteBuffer.allocate (ss.width * ss.height * ss.bpp / 8);
				is = new BufferedInputStream(is);	// buffering is very important apparently
//				is.read(bytes.array());				// reading all at once for speed
//				bytes.position(0);					// reset position to the beginning of ByteBuffer
//				ss.pixels = bytes;
				
				int size, length=0, len;
				
				size = ss.width * ss.height * ss.bpp / 8;
				while((len=is.read(bytes.array(), length, size-length)) > 0)
					length += len;
				bytes.position(0);					// reset position to the beginning of ByteBuffer
				ss.pixels = bytes;

				return ss;
			}
		}
		catch (Exception e) {
			throw new Exception(e);
		}
		finally {}

		return null;
	}

//		private Screenshot retreiveRawScreenshot() throws Exception {
//			try {
//				// connect to native application			
//				//We use SocketChannel,because is more convenience and fast
//				SocketChannel socket = SocketChannel.open(new InetSocketAddress("localhost", PORT));
//				socket.configureBlocking(false);
//	
//				//Send Commd to take screenshot
//				ByteBuffer cmdBuffer = ByteBuffer.wrap("SCREEN".getBytes("ASCII"));
//				socket.write(cmdBuffer);
//	
//				//build a buffer to save the info of screenshot
//				//3 parts,width height cpp
//				byte[] info = new byte[3 + 3 + 2 + 2];//3 bytes width + 1 byte space + 3 bytes heigh + 1 byte space + 2 bytes bpp
//				ByteBuffer infoBuffer = ByteBuffer.wrap(info);
//	
//				//we must make sure all the data have been read
//				while(infoBuffer.position() != infoBuffer.limit())
//					socket.read(infoBuffer);
//	
//				//we must read one more byte,because after this byte,we will read the image byte
//				socket.read(ByteBuffer.wrap(new byte[1]));
//	
//				//set the position to zero that we can read it.
//				infoBuffer.position(0);
//	
//				StringBuffer sb = new StringBuffer();
//				for(int i = 0;i < (3 + 3 + 2 + 2);i++)
//				{
//					sb.append((char)infoBuffer.get());
//				}
//	
//				String[] screenData = sb.toString().split(" ");
//				if (screenData.length >= 3) {
//					Screenshot ss 	= new Screenshot();
//					ss.width 		= Integer.parseInt(screenData[0]);
//					ss.height 		= Integer.parseInt(screenData[1]);
//					ss.bpp = Integer.parseInt(screenData[2]);
//					
//					// retreive the screenshot
//					// (this method - via ByteBuffer - seems to be the fastest)
//					ByteBuffer bytes = ByteBuffer.allocate (ss.width * ss.height * ss.bpp / 8);
//	
//					while(bytes.position() != bytes.limit())
//					{
//						//in the cycle,we must make sure all the image data have been read,maybe sometime the socket will delay a bit time and return some invalid bytes.
//						socket.read(bytes);				// reading all at once for speed
//					}
//	
//					bytes.position(0);					// reset position to the beginning of ByteBuffer
//					ss.pixels = bytes;
//					return ss;
//				}
//			}
//			catch (Exception e) {
//				throw new Exception(e);
//			}
//			finally {}
//	
//			return null;
//		}



	/*
	 * Saves given array of bytes into image file in the PNG format.
	 */
	private void writeImageFile(Screenshot ss, String file) {
		if (ss == null || !ss.isValid())		throw new IllegalArgumentException();
		if (file == null || file.length() == 0)	throw new IllegalArgumentException();

		// resolve screenshot's BPP to actual bitmap pixel format
		Bitmap.Config pf;
		switch (ss.bpp) {
		case 16:	pf = Config.RGB_565; break;
		case 32:	pf = Config.ARGB_8888; break;
		default:	pf = Config.ARGB_8888; break;
		}

		// create appropriate bitmap and fill it wit data
		Bitmap bmp = Bitmap.createBitmap(ss.width, ss.height, pf);
		bmp.copyPixelsFromBuffer(ss.pixels);

		// handle the screen rotation
		int rot = getScreenRotation();
		if (rot != 0) {
			Matrix matrix = new Matrix();
			matrix.postRotate(-rot);
			bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
		}

		// save it in PNG format
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			throw new InvalidParameterException();
		}
		bmp.compress(CompressFormat.PNG, 100, fos);

		if(bmp != null && !bmp.isRecycled()){
			bmp.recycle();
			bmp = null;
			System.gc();
		}
	}

	private  byte[] writeImageOutputStream() {
		int ImageQuality = 100;
		Screenshot ss = null;
		try {
			ss = retreiveRawScreenshot();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (ss == null || !ss.isValid())	
			throw new IllegalArgumentException();

		// resolve screenshot's BPP to actual bitmap pixel format
		Bitmap.Config pf;
		switch (ss.bpp) {
		case 16:	pf = Config.RGB_565; break;
		case 32:	pf = Config.ARGB_8888; break;
		default:	pf = Config.ARGB_8888; break;
		} 

		// create appropriate bitmap and fill it wit data
		Bitmap bmp = null;
		try {
			bmp = Bitmap.createBitmap(ss.width, ss.height, pf);
		} catch (OutOfMemoryError e) {
			Log.i("ZGF","Bitmap---Out of memory!");  
		}
		bmp.copyPixelsFromBuffer(ss.pixels);

		ss.pixels = null;
		ss = null;
		System.gc();

		// handle the screen rotation
		int rot = getScreenRotation();
		if (rot != 0) {
			Matrix matrix = new Matrix();
			matrix.postRotate(-rot);
			bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
		}
		long startTime=System.currentTimeMillis();
		// save it in PNG format
		ByteArrayOutputStream outStream = new ByteArrayOutputStream(); 
		bmp.compress(CompressFormat.PNG, ImageQuality, outStream);
		long endTime=System.currentTimeMillis();  
		Log.i("ZGF","pNG Bitmap cost time---raw data： "+(endTime-startTime)+"ms");  

		try {
			outStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(bmp != null && !bmp.isRecycled()){
			bmp.recycle();
			bmp = null;
			System.gc();
		}
		return outStream.toByteArray();
	}

	/*
	 * Takes screenshot and saves to a file.
	 */
	private String takeScreenshot() throws IOException {
		// make sure the path to save screens exists
		File screensPath = new File(SCREENSHOT_FOLDER);
		screensPath.mkdirs();

		// construct screenshot file name
		StringBuilder sb = new StringBuilder();
		sb.append(SCREENSHOT_FOLDER);
		sb.append(Math.abs(UUID.randomUUID().hashCode()));	// hash code of UUID should be quite random yet short
		sb.append(".png");
		String file = sb.toString();

		// fetch the screen and save it
		Screenshot ss = null;
		try {
			long startTime=System.currentTimeMillis();   //获取开始时间  
			ss = retreiveRawScreenshot();
			long endTime=System.currentTimeMillis(); //获取结束时间  
			Log.i("ZGF","Programe run time---raw data： "+(endTime-startTime)+"ms");  	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		writeImageFile(ss, file);

		return file;
	}
}
