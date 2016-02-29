package com.twocats.dev.camtimer;

/**
 * The CamPreview class manages displaying the preview images from the camera.
 * It extends the SurfaceView class, adding methods to manipulate the camera and
 * start/stop the preview.
 *
 * 
 * @author  Andrew Gillcrist
 * @version 1.0
 * @since   2015-07-21
 *
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

// I used the older Camera API instead of the current Camera2 which appeared in API level 21.
@SuppressWarnings("deprecation")


public class CamPreview extends SurfaceView implements SurfaceHolder.Callback {
/**
 * Responds to the SurfaceView lifecycle events, starting and stopping the preview as
 * necessary.  The callback functions for this are surfaceCreated, surfaceDestroyed,
 * and surfaceChanged.
 *
 * TODO:  There's way too much going on in this class.  It should just deal with the
 * camera preview, but currently it also supplies methods for obtaining and releasing
 * the camera, taking the picture, and saving the image.
 *
 */
	private final String TAG = "CamTimer.CamPreview";  // For logging purposes

	private Context        mActContext;     // Save the Activity's context here

	private WindowManager  mWindowManager;  // For obtaining display information

	private SurfaceView    mSurfaceView;    // SurfaceView associated with our camera preview
	private SurfaceHolder  mSurfaceHolder;  // Holder for above

	private OrientationEventListener mOrientationListener;  // So we know when we're rotating

	private Camera            mCamera;                      // Current camera
	private Camera.CameraInfo mCamInfo;                     // Info about current camera

	private Size           mBestPreviewSize       = null;   // Best calculated preview size
	private List<Size>     mSupportedPreviewSizes = null;   // List of current camera's preview sizes


	//
	// Class constructor.
	//
	CamPreview( Context actContext, SurfaceView sView ) {
	/**
	 * Constructor sets up the SurfaceView and the OrientationListener.
	 *
	 * @param actContext    The Activity's context.
	 * @param sView         The SurfaceView we're going to be working with.
	 *
	 */
		super( actContext );
		Log.d( TAG, "CamPreview Constructor" );

		// Stash the following in member variables for convenience.

		mActContext  = actContext;
		mSurfaceView = sView;

		// We get the WindowManager here in the constructor so we don't have to keep
		// re-retrieving it every time we switch cameras and set up the preview. 

		mWindowManager = (WindowManager) mActContext.getApplicationContext()
		                                            .getSystemService( Context.WINDOW_SERVICE );

		// Create a new orientation listener.  This will get called whenever the display rotates
		// at all, not just for portrait/landscape transitions.

		mOrientationListener = new OrientationEventListener( mActContext, SensorManager.SENSOR_DELAY_NORMAL )
		{
			@Override
			public void onOrientationChanged( int orientation ) {
				// orientation in degrees, 0 - 359

				if ( orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
					// orientationChanged( orientation );
				}
			}
		};

		if ( mOrientationListener.canDetectOrientation() )
			mOrientationListener.enable();
		else
			mOrientationListener.disable();

		// Get SurfaceHolder from SurfaceView and register callbacks.

		if ( mSurfaceView != null ) {
			mSurfaceHolder = mSurfaceView.getHolder();

			mSurfaceHolder.addCallback( this );
			mSurfaceHolder.setKeepScreenOn( true );
		}
	}  // Constructor


	public void setCamera( Camera cam ) {
		mCamera = cam;

		mSupportedPreviewSizes = null;  // Cause these two values to be regenerated
		mBestPreviewSize = null;        // the first time through surfaceChanged().
		                                // TODO:  this is just wrong
	}

	public void setCameraInfo( Camera.CameraInfo camInfo ) {
		mCamInfo = camInfo;
	}


	/**
	 * SurfaceHolder callback interface.
	 *
	 *    surfaceCreated, surfaceDestroyed, surfaceChanged
	 *
	 */

	public void surfaceCreated( SurfaceHolder sHolder ) {
	/*
	 * Called when the Surface has just been created.  All we have to do is
	 * pass the SurfaceHolder to the camera so it knows where to draw the preview.
	 *
	 */
		Log.d( TAG, "surfaceCreated()" );

		if ( mCamera == null || sHolder == null ) return;
	
		// Tell the camera to draw its preview frames on our Surface.
		try {
			mCamera.setPreviewDisplay( sHolder );
		}
		catch( Exception e ) {
			Log.d( TAG, "surfaceCreated: exception from setPreviewDisplay(): " + e.getMessage() );
		}
	}  // surfaceCreated

	public void surfaceDestroyed( SurfaceHolder sHolder ) {
	/*
	 * Called when the Surface is about to be destroyed.  All that's necessary is
	 * to stop the preview so it won't crash when the Surface goes away.
	 *
	 */
		Log.d( TAG, "surfaceDestroyed()" );

		if ( mCamera != null ) previewStop();

	}  // surfaceDestroyed

	public void surfaceChanged( SurfaceHolder sHolder, int iFormat, int iWidth, int iHeight ) {
	/*
	 * Called whenever the Surface has just been changed, for instance when the user rotates
	 * their device from portrait to landscape or vice versa.  When the Surface changes, we
	 * first stop the preview, then determine and set up the display and image rotations and
	 * the best preview size, then restart the preview.
	 *
	 */
	 // TODO:  Fix this stupid function
	 
		Log.d( TAG, "surfaceChanged()" );

		if ( mCamera == null ) return;

		// Stop the preview before we begin changing stuff.

		previewStop();

		// Get the current camera parameters;  we will potentially change some and
		// set them later with setParameters().

		Camera.Parameters camParams = mCamera.getParameters();

		// To properly handle device orientation changes, we need two things:
		// the camera orientation as it's mounted on the device, and the device's
		// orientation as it's being held by the user.  We already retrieved the
		// camera orientation in obtainCamera() so now we just need the device
		// orientation. 

		int deviceOrientation = getDeviceOrientation();

		// From these, determine the display rotation (how the preview appears on the
		// display) and the image rotation (how the resulting photo is oriented).

		int displayRotation = getDisplayRotation( deviceOrientation, mCamInfo.orientation );
		int imageRotation   = getImageRotation  ( deviceOrientation, mCamInfo.orientation );

		mCamera.setDisplayOrientation( displayRotation );
		camParams.setRotation( imageRotation );

		// Finally, determine a good preview size based on our display dimensions and
		// and what sizes the camera supports, but only if we haven't calculated it
		// already for this camera.

		if ( mSupportedPreviewSizes == null )
			mSupportedPreviewSizes = camParams.getSupportedPreviewSizes();

		if ( mBestPreviewSize == null && mSupportedPreviewSizes != null ) {

			// We haven't yet determined the best size for this camera and orientation.
			// On subsequent surfacedChanged calls, we won't have to do this again.

			 mBestPreviewSize =
					 getOptimalPreviewSize( mSupportedPreviewSizes, iWidth, iHeight );
		}

		if ( mBestPreviewSize != null ) {
			camParams.setPreviewSize( mBestPreviewSize.width, mBestPreviewSize.height );
		}

		mCamera.setParameters( camParams );

		Log.d( TAG, "surfaceChanged: set the following:"
			 // + "  dspMode="   + (displayMode == INDEX_PORTRAIT ? "portrait" : "landscape" )
				+ "  camOrient=" + mCamInfo.orientation
				+ ", devOrient=" + deviceOrientation
				+ ", dspRot="    + displayRotation
				+ ", imgRot="    + imageRotation
				+ ", pSize.w="   + ( mBestPreviewSize != null ? mBestPreviewSize.width  : "null" )
				+ ", pSize.h="   + ( mBestPreviewSize != null ? mBestPreviewSize.height : "null" )
		);

		// We're finished changing things, so we can restart the preview.

		previewStart();

	}  // surfaceChanged


	//
	// Helpers to start and stop the preview with exception handling.
	//
	
	public void previewStart() {
	/**
	 * Start the preview and catch any resulting exceptions.
	 * 
	 * @param sHolder   SurfaceHolder to the SurfaceView we're displaying on.
	 *
	 */
		Log.d( TAG, "previewStart()" );

		try {
			mCamera.setPreviewDisplay( mSurfaceHolder );
			mCamera.startPreview();
		}
		catch (Exception e) {
			Log.d( TAG, "exception from startPreview(): " + e.getMessage() );
		}
	}

	public void previewStop() {
	/**
	 * Stop the preview and catch any resulting exceptions.
	 *
	 */
		Log.d( TAG, "previewStop()" );

		try {
			mCamera.stopPreview();
		}
		catch ( Exception e ) {
			Log.d( TAG, "exception from stopPreview(): " + e.getMessage() );
		}
	}


	//
	// Methods for preview size and orientation.
	//

	public void orientationChanged( int orientation ) {
	/**
	 * Called whenever the device detects it's being rotated by any amount.
	 * 
	 * @param orientation   The number of degrees rotated from "natural" orientation.
	 *
	 */
		// Log.d( TAG, "orientationChanged(" + orientation + ")" );
	}

	private int getDeviceOrientation() {
	/**
	 * Determine in which of four orientations the device is being held.
	 * 
	 * @return 0, 90, 180, or 270 degrees from "natural" orientation
	 * 
	 */
		Log.d( TAG,  "getDeviceOrientation()" );

		int rotation = 90;  // good default for handsets
		int degrees  = 0;

		if ( mWindowManager != null )
			rotation = mWindowManager.getDefaultDisplay().getRotation();

		switch( rotation ) {
		case Surface.ROTATION_0:    // Natural orientation
			degrees = 0;
			break;
		case Surface.ROTATION_90:   // Landscape left
			degrees = 90;
			break;
		case Surface.ROTATION_180:  // Upside down
			degrees = 180;
			break;
		case Surface.ROTATION_270:  // Landscape right
			degrees = 270;
			break;
		}

		Log.d( TAG, "...returning rotation=" + rotation + " (" + degrees + " degrees)" );
		return degrees;

	}  // getDeviceOrientation


	private int getDisplayRotation( int degrees, int offset ) {
	/**
	 * Determine the display rotation based on how the device is being held
	 * and how the camera is mounted on the device.
	 * 
	 * @param degrees  Device position (0, 90, 180, or 270)
	 * @param offset   Camera position (0, 90, 180, or 270)
	 * 
	 */
		Log.d( TAG, "getDisplayRotation( d=" + degrees + ", o=" + offset + " )" );

		int rotation = 0;

		if ( mCamInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ) {
			rotation = ( offset + degrees ) % 360;
			rotation = ( 360 - rotation ) % 360;  // flip for mirroring
		} else {
			rotation = (offset - degrees + 360 ) % 360;
		}

		Log.d( TAG, "...returning " + rotation );
		return rotation;

	}  // getDisplayRotation


	private int getImageRotation( int degrees, int offset ) {
	/**
	 * Determine the image rotation based on how the device is being held
	 * and how the camera is mounted on the device.
	 * 
	 * @param degrees  Device position (0, 90, 180, or 270)
	 * @param offset   Camera position (0, 90, 180, or 270)
	 * 
	 */
		Log.d( TAG, "getImageRotation()" );

		int rotation = 0;

		if ( mCamInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ) {
			rotation = ( offset + degrees ) % 360;
		} else {
			rotation = ( offset - degrees + 360 ) % 360;
		}

		Log.d( TAG, "...returning " + rotation );
		return rotation;

	}  // getImageRotation


	private Size getOptimalPreviewSize( List<Size> sizes, int pWidth, int pHeight )	{
	/**
	 * Run down the camera's list of supported preview sizes, looking for one whose
	 * size and aspect ratio are the best match for our display area as given by
	 * pWidth and pHeight.
	 * 
	 * My test device (Moto X) lists the preview sizes in landscape only, so if we're
	 * currently in portrait orientation (based on pWidth and pHeight), we just
	 * compute the target aspect ratio as h/w instead of w/h.
	 * 
	 * @param sizes     List of supported preview sizes.
	 * @param pWidth    Width of our display.
	 * @param pHeight   Height of our display.
	 *
	 */
		Log.d( TAG, "getOptimalPreviewSize()" );

		boolean isPortrait = pWidth < pHeight;

		int width  = isPortrait ? pHeight : pWidth;
		int height = isPortrait ? pWidth  : pHeight;

		double targetRatio = (double) width / (double) height;
		double ASPECT_TOLERANCE = 0.1;
	
		Log.d( TAG, "getOPS: pw=" + pWidth + ", ph=" + pHeight
				  + ":  w=" + width + ", h=" + height + ", tr=" + targetRatio );
		
		Size   optimalSize   = null;              // Remains null until we find one
		int    targetHeight  = height;            // We'll try to match this height
		double minDifference = Double.MAX_VALUE;  // Large value to start with

		// Run down the list of supported preview sizes, looking for one whose size and
		// aspect ratio matches our display most closely.

		for ( Size testSize : sizes ) {

			// testSize holds a candidate preview size.  What's its aspect ratio?

			double testRatio = (double) testSize.width / (double) testSize.height;

			Log.d( TAG, "getOPS: pass 1, trying w=" + testSize.width + ", h=" + testSize.height + ", tr=" + testRatio );

			// If its aspect ratio is too different from what we want, keep looking.

			if ( Math.abs( testRatio - targetRatio ) > ASPECT_TOLERANCE )
			    continue;

			// If the aspect ratio is close enough, see if it's the best size match so far.

			if ( Math.abs( testSize.height - targetHeight ) < minDifference ) {
				Log.d( TAG, "getOPS: pass 1 -- found one" );
				optimalSize   = testSize;
				minDifference = Math.abs( testSize.height - targetHeight );
			}
		}

		// If we can't find one that matches the aspect ratio within the specified
		// tolerance, then forget the aspect ratio requirement and try again, just
		// looking for the closest size.

		if ( optimalSize == null ) {
			Log.d( TAG, "getOPS: no size within aspect tolerance" );

			minDifference = Double.MAX_VALUE;

			for ( Size testSize: sizes ) {
				Log.d( TAG, "getOPS: pass 2, trying w=" + testSize.width + ", h=" + testSize.height );

				if ( Math.abs( testSize.height - targetHeight ) < minDifference ) {
					Log.d( TAG, "getOPS: pass 2 -- found one" );
					optimalSize = testSize;
					minDifference = Math.abs( testSize.height - targetHeight );
				}
			}
		}

		Log.d( TAG, "gOPS: returning " + ( optimalSize == null
		                                 ? "null"
		                                 : "w=" + optimalSize.width + ", h=" + optimalSize.height ));
		return optimalSize;

	}  // getOptimalPreviewSize

}  // class camPreview
