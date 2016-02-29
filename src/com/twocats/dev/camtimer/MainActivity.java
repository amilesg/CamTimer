package com.twocats.dev.camtimer;

/**
 * A simple camera application that implements a self-timer, which the stock
 * camera app did not feature.
 * 
 * Many thanks to all the folks on stackoverflow and github, and of course
 * developer.android.com.
 * 
 * @author  Andrew Gillcrist
 * @version 1.0
 * @since   2015-07-21
 *
 */

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;


// TODO:
//
//  smooth rotation


public class MainActivity extends Activity {
/**
 * This main activity responds to the application lifecycle and handles
 * the following tasks:
 * 
 *   - Obtaining and releasing the camera in onResume and onPause
 *   - Instantiating the camera preview object
 *   - Selection of menu items
 *   - Managing the shutter timer
 *   - Taking the picture and saving the image
 *   - Playing sounds
 * 
 */
 
 // TODO:  explain about not restarting the activity
 
	private final String TAG = "CamTimer";        // For logging purposes

	private Context        mActContext;           // Holds the Activity context
	private CamPreview     mPreview;              // Handles the camera preview
	private Camera         mCamera = null;        // Hardware camera (one of possibly several)
	private CountDownTimer mTimer;                // Shutter timer
	private boolean        mTiming = false;       // True if timer is running
	private long           milliSeconds = 5000L;  // Default delay is 5 seconds
	private int            mNumCameras  = 0;      // Number of cameras on this device
	private int            mWhichCamera = 0;      // The camera we're currently using (0 to numCameras-1)
	private appSounds      mTimerSounds;          // Handles the ticking sound
	private SurfaceView    mSurfaceView;          // Store our SurfaceView, share with the preview

	private Camera.CameraInfo mCamInfo;           // Receives current camera general info.

	//
	// Lifecycle methods.
	//
	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		Log.d( TAG, "onCreate" );
		super.onCreate( savedInstanceState );

		mActContext = this;

		// Before we proceed, make sure we have at least one camera.
		mNumCameras = Camera.getNumberOfCameras();
		if ( mNumCameras == 0 ) {
			Toast.makeText( mActContext, mActContext.getString( R.string.cam_not_found ), Toast.LENGTH_LONG ).show();
			finish();
		}

		// Create a CameraInfo object for use when we need to getCameraInfo().
		mCamInfo = new Camera.CameraInfo();

		// Load our sounds -- we just have one, the timer tick.
		mTimerSounds = new appSounds();

		// Load our layout.
		setContentView( R.layout.activity_main );

		// Create the camera preview object that does all the work.
		mSurfaceView = (SurfaceView) findViewById( R.id.surface_view ); 
		mPreview = new CamPreview( mActContext, mSurfaceView );

	}  // onCreate

	@Override
	protected void onPause() {
		Log.d( TAG, "onPause" );
		super.onPause();

		releaseCamera();  // releaseCamera also stops the preview first
	}
	
	@Override
	protected void onResume() {
		Log.d( TAG, "onResume" );
		super.onResume();

		obtainCamera( mWhichCamera, true );  // true means please also start the preview
	}

	@Override
	protected void onDestroy() {
		Log.d( TAG, "onDestroy" );

		mTimerSounds.allDone();

		super.onDestroy();
	}

	
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		getMenuInflater().inflate( R.menu.main, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
	/**
	 * Handle all our menu items.
	 * 
	 */
		int  id = item.getItemId();  // Which menu item was selected?
		long ms = milliSeconds;      // Save this so we can tell if it changed

		switch( id ) {
			case R.id.action_settings:
				return true;

			// Set user's choice of shutter delay.
			case R.id.settings_delay01:
				milliSeconds = 1000; break;
			case R.id.settings_delay02:
				milliSeconds = 2000; break;
			case R.id.settings_delay05:
				milliSeconds = 5000; break;
			case R.id.settings_delay10:
				milliSeconds = 10000; break;
			case R.id.settings_delay30:
				milliSeconds = 30000; break;

			// Cycle through all available cameras.
			case R.id.action_switch:
				if ( ++mWhichCamera == mNumCameras ) {
					mWhichCamera = 0;
				}
				switchToCamera( mWhichCamera );
				break;

			// Start Credits activity.
			case R.id.action_credits:
				break;

			// Exit the application.
			case R.id.action_exit:
				finish();
				break;

			default:
				break;
		}

		if ( milliSeconds != ms ) {
			// User changed the time delay value, so we must destroy the current
			// timer and create a new one in onTouchEvent() with the new value.
			mTimer = null;
		}

		return super.onOptionsItemSelected( item );
	}  // onOptionsItemSelected

	@Override
	public boolean onTouchEvent( MotionEvent event ) {
	/**
	 * Tapping the screen starts the shutter timer.
	 * Tapping the screen while the timer is running cancels it.
	 * 
	 */
		Log.d( TAG, "MotionEvent" );

		int action = event.getActionMasked();

		switch( action ) {
		case MotionEvent.ACTION_UP:  // User tapped our view.
	
			if ( mTiming == false ) {  // The timer is not running, so let's start it.
				Log.d( TAG, "Starting timer." );

				// Create the timer if necessary.  Since it's created with the user's choice
				// of delay time, if they change that we have to destroy and recreate the
				// timer with the new delay value.  See onOptionsItemSelected().

				if ( mTimer == null ) {
					Log.d( TAG, "(Creating timer.)" );

					mTimer = new CountDownTimer( milliSeconds, 1000L ) {

						public void onTick( long mSecLeft ) {
							// Play a tick sound each second.
							mTimerSounds.playSound();
						}

						public void onFinish() {
							// When timer finishes, take a picture.
							Log.d(TAG, "Timer finished, taking picture." );
							takePicture();
							mTiming = false;
						}
					};
				}
				
				// Start the timer.
				mTimer.start();
				mTiming = true;

				Toast.makeText( mActContext,
		                mActContext.getString( R.string.timer_started ), Toast.LENGTH_LONG ).show();
			}

			else {  // The timer is running, but the user wants to cancel it.
				Log.d( TAG, "Cancelling timer." );

				mTimer.cancel();
				mTiming = false;

				Toast.makeText( mActContext,
	                mActContext.getString( R.string.timer_cancelled ), Toast.LENGTH_LONG ).show();
			}

			return true;

		default:
			return super.onTouchEvent( event );
		}
	}  // onTouchEvent

	@Override
	public void onConfigurationChanged( Configuration newConfig ) {
		Log.d( TAG, "onConfigurationChanged" );
		super.onConfigurationChanged( newConfig );
	}  // onConfigurationChanged
	

	//
	// Public methods called by our parent to obtain and release the camera, switch
	// between cameras, and take a picture.
	//

	public void obtainCamera( int whichCamera, boolean startPreview ) {
	/**
	 *  Grabs the hardware camera (if possible) and prepares it for use,
	 *  optionally starting the preview for convenience.
	 *  
	 *  @param whichCamera   Zero-based index of all the device's cameras.
	 *  @param startPreview  If true, the camera preview will be started.
	 */
		Log.d( TAG, "obtainCamera( " + whichCamera + " )" );
		
		if ( mCamera != null ) return; 

		try {
			Log.d( TAG, "... opening camera" );
			mCamera = Camera.open( whichCamera );
		}
		catch( Exception e ) {
			Log.d( TAG, "... exception opening camera: " + e.getMessage() );
			return;  // nothing further to do
		}

		// Now we have the camera, so let's get some more info that the preview will need.
		// We'll use getCameraInfo() and getParameters() and set the results in the preview.

		Camera.Parameters camParams = mCamera.getParameters();
		boolean setParams = false;  // becomes true if we change anything

		// We want auto flash and auto focus if they are supported. 
		{
			List<String> supportedFocusModes = camParams.getSupportedFocusModes();
			List<String> supportedFlashModes = camParams.getSupportedFlashModes();

			final String focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
			final String flashMode = Camera.Parameters.FLASH_MODE_AUTO;

			if ( supportedFocusModes != null  // then there are some to check
		      && supportedFocusModes.contains( focusMode )) {
		    	Log.d( TAG, "Setting focus mode to " + focusMode );
		        camParams.setFocusMode( focusMode );
		        setParams = true;
			}

			if ( supportedFlashModes != null
		      && supportedFlashModes.contains( flashMode )) {
		    	Log.d( TAG, "Setting flash mode to " + flashMode );
		        camParams.setFlashMode( flashMode );
		        setParams = true;
			}
		}

		if ( setParams ) mCamera.setParameters( camParams );

		// Send the the camera object and the populated CameraInfo to the preview.

		Camera.getCameraInfo( whichCamera, mCamInfo );
		mPreview.setCameraInfo( mCamInfo );
		mPreview.setCamera( mCamera );

		if ( startPreview )
			mPreview.previewStart();

		mSurfaceView.setVisibility( View.GONE );     // TODO: Do we need this?
		mSurfaceView.setVisibility( View.VISIBLE );  // Force surfaceChanged()

	}  // obtainCamera


	public void releaseCamera() {
	/**
	 * Stop the preview and let go of the hardware camera.
	 * 
	 */
		Log.d( TAG, "releaseCamera()" );
		
		if ( mCamera == null ) return;  // No camera to release

		mPreview.previewStop();

		try {
			Log.d( TAG, "...rC: releasing camera" );
			mCamera.release();
		}
		catch( Exception e ) {
			Log.d( TAG, "... rC: exception releasing camera: " + e.getMessage() );
		}

		mCamera = null;
		mPreview.setCamera( null );

	}  // releaseCamera


	public void switchToCamera( int whichCamera ) {
	/**
	 * Releases the current camera (if any) and obtains the one specified.
	 * 
	 * @param whichCamera  Zero-based index of which device camera to use.
	 * 
	 */
		Log.d( TAG, "switchToCamera(" + whichCamera + ")" );

		if ( mCamera != null ) releaseCamera();

		obtainCamera ( whichCamera, true );  // True means please also start the preview.

		// Confirm to the user which camera they just switched to.
		Toast.makeText( mActContext, mActContext.getString(R.string.now_using) + " "

		  + ( whichCamera == 0
		    ? ( mActContext.getString( R.string.back_cam ))
		    : ( whichCamera == 1
		      ? ( mActContext.getString( R.string.front_cam ))
		      : ( mActContext.getString( R.string.other_cam ) + " (" + whichCamera + ")" )))

		  + " " + mActContext.getString( R.string.camera ), Toast.LENGTH_SHORT ).show();

	}  // switchToCamera


	//
	// Camera callback methods for shutter trip and image availability.
	//

	public void takePicture() {
	/**
	 * Tell the camera to actually take a picture, catching any exceptions.
	 * 
	 */
		Log.d( TAG, "takePicture()" );

		try {
			mCamera.takePicture( shutterCallback, null, null, jpegCallback );
		}
		catch( Exception e ) {
			Log.d( TAG, "takePicture: exception taking picture: " + e.getMessage() );
		}
	}  // takePicture


	public Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
		public void onShutter() {
			Log.d( TAG, "onShutter" );
		}
	};

	public Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
	/**
	 * Called by the camera layer when our image is available.
	 *
	 */
		public void onPictureTaken( byte[] data, Camera camera ) {
			Log.d( TAG, "onPictureTaken(jpeg)" );

			// Spin off a background task to save the image data so we
			// don't slow down the UI thread.

			new SaveImageTask().execute( data );

			camera.startPreview();  // Taking picture stops preview, we must restart it.
		}
	};

	private class SaveImageTask extends AsyncTask<byte[], Void, Void> {
	/**
	 * Background task to write the captured image data to a file.
	 *
	 */
		private File outFile;

		@Override
		protected Void doInBackground( byte[]... data ) {

			FileOutputStream outStream = null;

			try {
				// Get the name of the public pictures directory.

				File saveDir = Environment.getExternalStoragePublicDirectory(
				               Environment.DIRECTORY_PICTURES );

				// Build the file name and create the file.

				String fileName = String.format( "%d.jpg", System.currentTimeMillis() );

				outFile   = new File( saveDir, fileName );
				outStream = new FileOutputStream( outFile );

				// Write the image data to the file we just created.

				outStream.write( data[0] );
				outStream.flush();
				outStream.close();

				Log.d( TAG, "SaveImageTask: wrote bytes: " + data.length +
				            " to " + outFile.getAbsolutePath());

			} catch ( Exception e ) {
				Toast.makeText( mActContext, mActContext.getString( R.string.pic_not_saved ),
				                Toast.LENGTH_LONG ).show();
			}

			return null;
		}  // doInBackground

		@Override
		protected void onPostExecute( Void result ) {
		/**
		 * Inform the user that their picture has been saved, and ask the media scanner
		 * to pick it up so it'll show in the gallery.
		 * 
		 */
			Toast.makeText( mActContext, mActContext.getString( R.string.pic_saved ),
			                Toast.LENGTH_LONG ).show();

			// Construct and broadcast an Intent that the Media Scanner should hear and act on.

			File   f = new File( outFile.getAbsolutePath() );
			Uri    u = Uri.fromFile( f );
			Intent i = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, u );

			mActContext.sendBroadcast( i );

			Log.d( TAG, "onPostExecute: sent broadcast to media scanner: " + u );

		}  // onPostExecute
	}  // saveImageTask


	private class appSounds {
	/**
	 * The appSounds class uses a SoundPool to store and play our ticking sound.
	 * 
	 */
		private AudioManager audioManager;
		private SoundPool    soundPool;
		private float        volume;
		private int          soundID;
		private boolean      soundsLoaded = false;

		// Constructor.
		appSounds() {

			// Open the Audio Manager.
			audioManager = (AudioManager) getSystemService( AUDIO_SERVICE );

			// Get the current volume of the NOTIFICATION audio stream, so we
			// can set the tick volume to more or less match the shutter sound.
			volume = (float) audioManager.getStreamVolume( AudioManager.STREAM_NOTIFICATION );
			setVolumeControlStream( AudioManager.STREAM_NOTIFICATION );

			// Set up a SoundPool and its completion listener.  We must do this
			// differently depending on whether we're pre-Lollipop or not.

			if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {

				// If we're Lollipop+, first we'll build some AudioAttributes,
				// then build a SoundPool using them.  
				AudioAttributes audioAttributes = new AudioAttributes.Builder()
					.setContentType( AudioAttributes.CONTENT_TYPE_SONIFICATION )
					.setUsage( AudioAttributes.USAGE_GAME )
					.build();

				soundPool = new SoundPool.Builder()
					.setMaxStreams( 1 )
					.setAudioAttributes( audioAttributes )
					.build();

			} else {
				// If we're older than Lollipop, do it the old way.
				soundPool = new SoundPool( 10, AudioManager.STREAM_NOTIFICATION, 0 );
			}

			soundPool.setOnLoadCompleteListener( new OnLoadCompleteListener() {
				@Override
				public void onLoadComplete( SoundPool sp, int sampleID, int status ) {
					soundsLoaded = true;
				}
			} );

			soundID = soundPool.load( getApplicationContext(), R.raw.tick, 1 );
		}  // appSounds constructor

		public void playSound() {
			if ( soundsLoaded && soundPool != null )
				soundPool.play( soundID, volume, volume, 1, 0, 1f );
		}
		
		public void allDone() {
			if ( soundPool != null ) {
				soundPool.release();
				soundPool = null;
			}
		}
	}  // class appSounds

}  // class MainActivity