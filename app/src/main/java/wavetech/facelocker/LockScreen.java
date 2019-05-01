package wavetech.facelocker;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.andrognito.patternlockview.PatternLockView;
import com.andrognito.patternlockview.listener.PatternLockViewListener;
import com.andrognito.patternlockview.utils.PatternLockUtils;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.List;

import wavetech.facelocker.utils.LockscreenIntentReceiver;
import wavetech.facelocker.utils.LockscreenService;
import wavetech.facelocker.utils.LockscreenUtils;
import wavetech.facelocker.utils.PasswordStore;

public class LockScreen extends AbstractCameraActivity
  implements
  LockscreenUtils.OnLockStatusChangedListener
{

  // User-interface
  private Button btnUnlock;

  // Member variables
  private LockscreenUtils mLockscreenUtils;

  private PatternLockViewListener patternLockViewListener;
  private PatternLockView mPatternLockView;
  private PasswordStore passwordStore;
  private EditText pinCodeInput;


  //All camera stuffs goes here
  /**
   * Now, this one is interesting! OpenCV orients the camera
   * to left by 90 degrees. So if the app is in portrait more,
   * camera will be in -90 or 270 degrees orientation. We fix that in the n
   * ext and the most important function. There you go!
   * @param inputFrame
   * @return
   */
  @Override
  public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
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
//              try{
//
//              }
//              catch (IOException e){
//                e.printStackTrace();
//                Log.e(TAG,"IO Exception: "+ e.getMessage());
//              }
//              catch (Exception e){
//                e.printStackTrace();
//                Log.e(TAG,"IO Exception: "+ e.getMessage());
//              }


              mOpenCvCameraView.disableView();
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




  private void initializeListeners(){
    patternLockViewListener=new PatternLockViewListener() {
      @Override
      public void onStarted() {
      /*Log.v(getClass().getName(), "Pattern drawing started");
      showToastMessage("Pattern drawing has started");*/

      }

      @Override
      public void onProgress(List<PatternLockView.Dot> progressPattern) {
      /*Log.v(getClass().getName(), "Pattern progress: " +
        PatternLockUtils.patternToString(mPatternLockView, progressPattern));
      showToastMessage("Pattern progress: " +
        PatternLockUtils.patternToString(mPatternLockView, progressPattern));*/
      }

      @Override
      public void onComplete(List<PatternLockView.Dot> pattern) {
//      showToastMessage("Pattern complete: " +
//        PatternLockUtils.patternToString(mPatternLockView, pattern));
        //Log.v(getClass().getName(), "Pattern complete: " +
          //PatternLockUtils.patternToString(mPatternLockView, pattern));
        String patternInput=PatternLockUtils.patternToString(mPatternLockView, pattern);
        if(passwordStore.getPatternCode().equals(patternInput))
          unlockDevice();
        else{
          Toast.makeText(getApplicationContext(),"Invalid pattern!",Toast.LENGTH_SHORT).show();
          mPatternLockView.clearPattern();
        }
      }

      @Override
      public void onCleared() {
        Log.v(getClass().getName(), "Pattern has been cleared");
        //showToastMessage("pattern cleard");
      }
    };
    mPatternLockView.addPatternLockListener(patternLockViewListener);

    pinCodeInput.setOnEditorActionListener(new EditText.OnEditorActionListener() {

      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          String pinCode=passwordStore.getPinCode();
          //Toast.makeText(getApplicationContext(),"Saved: " + pinCode+" Current: "+pinCodeInput.getText().toString(),Toast.LENGTH_SHORT).show();
          if(pinCode.equals(pinCodeInput.getText().toString()))
            unlockDevice();
          else{
            Toast.makeText(getApplicationContext(),"Incorrect Password!",Toast.LENGTH_SHORT).show();
            pinCodeInput.setText("");
          }
          return true;
        }
        return false;
      }
    });
  }

  public void showPatternView(View v){
    findViewById(R.id.patternLayout).setVisibility(View.VISIBLE);
    findViewById(R.id.pinCodeLayout).setVisibility(View.GONE);
  }
  public void showPinCodeView(View v){
    findViewById(R.id.pinCodeLayout).setVisibility(View.VISIBLE);
    findViewById(R.id.patternLayout).setVisibility(View.GONE);
  }

  // Set appropriate flags to make the screen appear over the keyguard
  @Override
  public void onAttachedToWindow() {
    /*this.getWindow().setType(
      WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
    this.getWindow().addFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
    );*/

    super.onAttachedToWindow();
    //this.getWindow().setType(LayoutParams.TYPE_KEYGUARD_DIALOG);
  }
  @Override
  public void onCreate(Bundle savedInstanceState) {
    this.getWindow().setType(
      WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
    this.getWindow().addFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
    );
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_lock_screen);
    passwordStore= new PasswordStore(getApplicationContext());
    mPatternLockView=findViewById(R.id.pattern_lock_view);
    pinCodeInput = findViewById(R.id.passwordEditText);
    //Hide the action bar
    getSupportActionBar().hide();
    initializeListeners();
    init();

    // unlock screen in case of app get killed by system
    if (getIntent() != null && getIntent().hasExtra("kill")
      && getIntent().getExtras().getInt("kill") == 1) {
      enableKeyguard();
      unlockHomeButton();
    } else {

      try {
        // disable keyguard
        disableKeyguard();

        // lock home button
        lockHomeButton();

        // start service for observing intents
        startService(new Intent(this, LockscreenService.class));

        // listen the events get fired during the call
        StateListener phoneStateListener = new StateListener();
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener,
          PhoneStateListener.LISTEN_CALL_STATE);

        initializeListeners();

      } catch (Exception e) {
        Log.e(CameraActivity.TAG, e.getMessage());
      }

    }
  }

  @Override
  public void onPause() {
    super.onPause();

    //The following are used to disable minimize button
    ActivityManager activityManager = (ActivityManager) getApplicationContext()
      .getSystemService(Context.ACTIVITY_SERVICE);

    activityManager.moveTaskToFront(getTaskId(), 0);
  }

  private void init() {
    mLockscreenUtils = new LockscreenUtils();
//    btnUnlock = (Button) findViewById(R.id.btnUnlock);
//    btnUnlock.setOnClickListener(new View.OnClickListener() {
//
//      @Override
//      public void onClick(View v) {
//        // unlock home button and then screen on button press
//        unlockDevice();
//      }
//    });
  }

  // Handle events of calls and unlock screen if necessary
  private class StateListener extends PhoneStateListener {
    @Override
    public void onCallStateChanged(int state, String incomingNumber) {

      super.onCallStateChanged(state, incomingNumber);
      switch (state) {
        case TelephonyManager.CALL_STATE_RINGING:
          unlockHomeButton();
          break;
        case TelephonyManager.CALL_STATE_OFFHOOK:
          break;
        case TelephonyManager.CALL_STATE_IDLE:
          break;
      }
    }
  };

  // Don't finish Activity on Back press
  @Override
  public void onBackPressed() {
    return;
  }

  // Handle button clicks
  @Override
  public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
    if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
      || (keyCode == KeyEvent.KEYCODE_POWER)
      || (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
      || (keyCode == KeyEvent.KEYCODE_CAMERA)) {
      return true;
    }
    if ((keyCode == KeyEvent.KEYCODE_HOME)) {
      Toast.makeText(getApplicationContext(),"Home  button pressed",Toast.LENGTH_LONG).show();
      return true;
    }

    return false;

  }

  // handle the key press events here itself
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
      || (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)
      || (event.getKeyCode() == KeyEvent.KEYCODE_POWER)) {
      return false;
    }
    if ((event.getKeyCode() == KeyEvent.KEYCODE_HOME)) {
      Toast.makeText(getApplicationContext(),"Home  button pressed",Toast.LENGTH_LONG).show();
      return false;
    }

    //Let the number keys work for the password text input
    if(event.getKeyCode() >= KeyEvent.KEYCODE_1 && event.getKeyCode() <= KeyEvent.KEYCODE_9)
    {
      return super.dispatchKeyEvent(event);
    }
    return false;
  }

  // Lock home button
  public void lockHomeButton() {
    mLockscreenUtils.lock(LockScreen.this);
  }

  // Unlock home button and wait for its callback
  public void unlockHomeButton() {
    mLockscreenUtils.unlock();
  }

  // Simply unlock device when home button is successfully unlocked
  @Override
  public void onLockStatusChanged(boolean isLocked) {
    if (!isLocked) {
      unlockDevice();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    unlockHomeButton();
  }

  @SuppressWarnings("deprecation")
  private void disableKeyguard() {
    KeyguardManager mKM = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    KeyguardManager.KeyguardLock mKL = mKM.newKeyguardLock("IN");
    mKL.disableKeyguard();
  }

  @SuppressWarnings("deprecation")
  private void enableKeyguard() {
    KeyguardManager mKM = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    KeyguardManager.KeyguardLock mKL = mKM.newKeyguardLock("IN");
    mKL.reenableKeyguard();
  }

  //Simply unlock device by finishing the activity
  private void unlockDevice()
  {
    finish();
  }
}
