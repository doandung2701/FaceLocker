package wavetech.facelocker;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.andrognito.patternlockview.PatternLockView;
import com.andrognito.patternlockview.listener.PatternLockViewListener;
import com.andrognito.patternlockview.utils.PatternLockUtils;

import java.util.List;

import wavetech.facelocker.utils.PasswordStore;

public class PinCode extends AppCompatActivity {
  private PatternLockView mPatternLockView;
  private Button continueButton;
  private PasswordStore passwordStore;
  private PatternLockViewListener patternLockViewListener;
  private void showToastMessage(String msg){
    Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT).show();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_pin_code);
    passwordStore= new PasswordStore(getApplicationContext());
    continueButton=findViewById(R.id.continueBtn);
    mPatternLockView =  findViewById(R.id.pattern_lock_view);
    initializeListeners();


  }
  private void launchAlternativePincodeActivity(){
    Intent intent=new Intent(PinCode.this,PinCodeAlternative.class);
    startActivity(intent);
  }
  private void initializeListeners(){
    continueButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        passwordStore.save();
        launchAlternativePincodeActivity();
      }
    });
    patternLockViewListener=new PatternLockViewListener() {
      @Override
      public void onStarted() {
      /*Log.v(getClass().getName(), "Pattern drawing started");
      showToastMessage("Pattern drawing has started");*/
        continueButton.setVisibility(View.INVISIBLE);
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
        Log.v(getClass().getName(), "Pattern complete: " +
          PatternLockUtils.patternToString(mPatternLockView, pattern));
        passwordStore.setPatternCode(PatternLockUtils.patternToString(mPatternLockView, pattern));
        continueButton.setVisibility(View.VISIBLE);

      }

      @Override
      public void onCleared() {
        Log.v(getClass().getName(), "Pattern has been cleared");
        //showToastMessage("pattern cleard");

        continueButton.setVisibility(View.INVISIBLE);
      }
    };
    mPatternLockView.addPatternLockListener(patternLockViewListener);
  }
}
