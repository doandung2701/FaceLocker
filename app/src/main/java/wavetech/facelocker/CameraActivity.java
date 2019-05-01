package wavetech.facelocker;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
//OpenCV Java Classes
import com.kaopiz.kprogresshud.KProgressHUD;

import org.bytedeco.javacv.FrameFilter;
import org.opencv.android.JavaCameraView;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import wavetech.facelocker.utils.FaceRegister;
import wavetech.facelocker.utils.LockscreenService;
import wavetech.facelocker.utils.PasswordStore;


public class CameraActivity extends AppCompatActivity implements CvCameraViewListener2 {
  // Used for logging success or failure messages
  public static final String TAG = "OCVSample::Activity";

  // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
  private CameraBridgeViewBase mOpenCvCameraView;


  private FaceRegister faceRegister=new FaceRegister();

  // Used in Camera selection from menu (when implemented)
  private boolean              mIsJavaCamera = true;
  private MenuItem             mItemSwitchCamera = null;

  // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
  Mat mRgba;
  Mat mRgbaF;
  Mat mRgbaT;


  private CascadeClassifier cascadeClassifier;
  private Mat grayscaleImage;
  private int absoluteFaceSize;

  //Initialize the loader
  private KProgressHUD progressLoader;

//  static  {
//    System.loadLibrary("opencv_java3");
//  }

  //Now, lets call OpenCV manager to help our app communicate with android phone to make OpenCV work
  private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
    @Override
    public void onManagerConnected(int status) {

      switch (status) {
        case LoaderCallbackInterface.SUCCESS:
        {
          Log.i(TAG, "OpenCV loaded successfully");
          initializeOpenCVDependencies();
//          mOpenCvCameraView.enableView();
          //System.loadLibrary("detection_based_tracker");

        } break;
        default:
        {
          super.onManagerConnected(status);
        } break;
      }
    }
  };

  private void showToastMessage(String msg){
    Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT).show();
  }
  private void initializeOpenCVDependencies() {

    try {
      // Copy the resource into a temp file so OpenCV can load it
      InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
      File cascadeDir = getDir("cascade", getApplicationContext().MODE_PRIVATE);
      File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
      FileOutputStream os = new FileOutputStream(mCascadeFile);


      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = is.read(buffer)) != -1) {
        os.write(buffer, 0, bytesRead);
      }
      is.close();
      os.close();

      // Load the cascade classifier
      cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
    } catch (Exception e) {
      Log.e("OpenCVActivity", "Error loading cascade", e);
    }

    // And we are ready to go
    mOpenCvCameraView.enableView();
  }
  private void startScreenLock(){
    startService(new Intent(this, LockscreenService.class));
  }
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera);
    //Hide the action bar
    getSupportActionBar().hide();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    //askForPermissions();
    mOpenCvCameraView = (JavaCameraView) findViewById(R.id.camera_view);
    mOpenCvCameraView.setCameraIndex(1);
    mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
    mOpenCvCameraView.setCvCameraViewListener(this);

    progressLoader = KProgressHUD.create(CameraActivity.this)
      .setStyle(KProgressHUD.Style.BAR_DETERMINATE)
      .setLabel("Registering Face")
      .setMaxProgress(100)
      .setCancellable(false)
      .show();

  }

  /*
  *  The following three functions handle the
   *  events when the app is Paused,
  *  Resumed and Closed/Destroyed
  *
  */
  @Override
  public void onPause()
  {
    super.onPause();
    if (mOpenCvCameraView != null)
      mOpenCvCameraView.disableView();
  }

  @Override
  public void onResume()
  {
    super.onResume();
    /*static {
    OpenCVLoader.initDebug();

  }*/

    if (!OpenCVLoader.initDebug()) {
      Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
      OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
    } else {
      //System.loadLibrary("opencv_java");
      Log.d(TAG, "OpenCV library found inside package. Using it!");
      mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
    }
  }

  public void onDestroy() {
    super.onDestroy();
    if (mOpenCvCameraView != null)
      mOpenCvCameraView.disableView();
  }


  /**
   * Now, this one is interesting! OpenCV orients the camera
   * to left by 90 degrees. So if the app is in portrait more,
   * camera will be in -90 or 270 degrees orientation. We fix that in the n
   * ext and the most important function. There you go!
   * @param inputFrame
   * @return
   */
  @Override
  public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
    mRgba = inputFrame.rgba();

    // Rotate mRgba 90 degrees
    Core.transpose(mRgba, mRgbaT);
    Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
    //Flip by 180 degrees
    Core.flip(mRgbaF, mRgba, -1 );

    // Create a grayscale image
    Imgproc.cvtColor(mRgba, grayscaleImage, Imgproc.COLOR_RGBA2RGB);

    MatOfRect faces = new MatOfRect();

    // Use the classifier to detect faces
    if (cascadeClassifier != null) {
      cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
        new Size(absoluteFaceSize, absoluteFaceSize), new Size());
    }

    // If there are any faces found, draw a rectangle around it
    Rect[] facesArray = faces.toArray();
    //So the rectangle won't show in the saved Image but only in the camera
    Mat duplicateMat=mRgba.clone();
    for (int i = 0; i <facesArray.length; i++) {
      Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 255, 0, 255), 3);

    }

    if(facesArray.length==1){
      try {
        faceRegister.debounceImageSaveCall(this,duplicateMat, 50);
        progressLoader.setProgress(faceRegister.getSavedImagesCount()*10);
        if(faceRegister.getSavedImagesCount()>=10){
          //faceRegister.trainModels();
          //Toast.makeText(getApplicationContext(),"",Toast.LENGTH_LONG).show();
          //finish();

          this.runOnUiThread(new Runnable() {
            public void run() {
              try{
                faceRegister.trainModels(CameraActivity.this.getApplicationContext());
                Log.v(TAG,"Finish training models");
              }
              catch (IOException e){
                e.printStackTrace();
                Log.e(TAG,"IO Exception: "+ e.getMessage());
              }
              catch (Exception e){
                e.printStackTrace();
                Log.e(TAG,"IO Exception: "+ e.getMessage());
              }

              AlertDialog alertDialog = new AlertDialog.Builder(CameraActivity.this).create();
              alertDialog.setTitle("Success");
              alertDialog.setMessage("Face registered successfully!");
              alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                    PasswordStore passwordStore = new PasswordStore(getApplicationContext());
                    passwordStore.setIsScreenLockEnabled(true);
                    passwordStore.save();

                    startScreenLock();

                    Intent intent=new Intent(CameraActivity.this,LockScreen.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    dialog.dismiss();

                  }
                });
              alertDialog.show();
              mOpenCvCameraView.disableView();

              /*Intent intent=new Intent(CameraActivity.this,MainActivity.class);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
              startActivity(intent);*/
            }
          });





        }
      }catch (IOException e){
        Log.e(TAG,"IO Error: "+ e.getMessage());
      }
      catch (Exception e){
        Log.e(TAG,"Exception: "+ e.getMessage());
      }
      //
    }


    return mRgba;
  }


  //Receive Image Data when the camera preview starts on your screen 😀
  @Override
  public void onCameraViewStarted(int width, int height) {
    mRgba = new Mat(height, width, CvType.CV_8UC4);
    mRgbaF = new Mat(height, width, CvType.CV_8UC4);
    mRgbaT = new Mat(width, width, CvType.CV_8UC4);

    grayscaleImage = new Mat(height, width, CvType.CV_8UC4);

    // The faces will be a 20% of the height of the screen
    absoluteFaceSize = (int) (height * 0.2);
  }
  //Destroy image data when you stop camera preview on your phone screen
  @Override
  public void onCameraViewStopped() {
    mRgba.release();
  }
}
