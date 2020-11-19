package com.johnsonsu.rnsoundplayer;

import android.media.MediaPlayer;
import android.media.AudioManager;
import android.media.MediaPlayer.TrackInfo;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import java.io.File;

import java.io.IOException;
import javax.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;

import android.os.Build;

public class RNSoundPlayerModule extends ReactContextBaseJavaModule {

  public final static String EVENT_FINISHED_PLAYING = "FinishedPlaying";
  public final static String EVENT_FINISHED_LOADING = "FinishedLoading";
  public final static String EVENT_FINISHED_LOADING_FILE = "FinishedLoadingFile";
  public final static String EVENT_FINISHED_LOADING_URL = "FinishedLoadingURL";

  private final ReactApplicationContext reactContext;
  private MediaPlayer mediaPlayer;
  private float volume;

  private boolean isTrackReady = false;
  private AudioManager mAudioManager;
  private AudioManager.OnAudioFocusChangeListener afChangeListener;
  private AudioFocusRequest duckAudioRequestBuilder;
  private AudioAttributes mPlaybackAttributes;

  public RNSoundPlayerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.volume = 1.0f;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      this.mAudioManager = (AudioManager) this.reactContext.getSystemService(Context.AUDIO_SERVICE);
	
      this.afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
          switch (focusChange) {
          case AudioManager.AUDIOFOCUS_GAIN:
            // Set volume level to desired levels
            break;
          case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            // You have audio focus for a short time
            break;
          case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
            // Play over existing audio
            break;
          case AudioManager.AUDIOFOCUS_LOSS:
            break;
          case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            // Temporary loss of audio focus - expect to get it back - you can keep your resources around
            break;
          case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            // Lower the volume
            break;
          }
        }
      };
  
      this.mPlaybackAttributes = new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build();
  
      this.duckAudioRequestBuilder = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      .setAudioAttributes(this.mPlaybackAttributes)
      .setAcceptsDelayedFocusGain(false)
      .setWillPauseWhenDucked(false)
      .setOnAudioFocusChangeListener(this.afChangeListener)
      .build();
    }
  }

  @Override
  public String getName() {
    return "RNSoundPlayer";
  }

  @ReactMethod
  public void playSoundFile(String name, String type) throws IOException {
    mountSoundFile(name, type);
    this.resume();
  }

  @ReactMethod
  public void loadSoundFile(String name, String type) throws IOException {
    mountSoundFile(name, type);
  }

  @ReactMethod
  public void playUrl(String url) throws IOException {
    prepareUrl(url);
    this.resume();
  }

  @ReactMethod
  public void loadUrl(String url) throws IOException {
    prepareUrl(url);
  }

  @ReactMethod
  public void pause() throws IllegalStateException {
    if (this.mediaPlayer != null) {
      this.mediaPlayer.pause();
    }
  }

  @ReactMethod
  public void resume() throws IOException, IllegalStateException {
    if (this.mediaPlayer != null) {
      try {
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //   this.mAudioManager.requestAudioFocus(this.duckAudioRequestBuilder);
        // }
        this.setVolume(this.volume);
        this.mediaPlayer.start();
      }
      catch(Exception e) {
        //  Block of code to handle errors
      }
    }
  }

  @ReactMethod
  public void resetAudioVolume() {
    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.mediaPlayer != null) {
    //   this.mAudioManager.abandonAudioFocusRequest(this.duckAudioRequestBuilder);
    // }
  }

  private void setTrackReady (boolean isReady) {
    this.isTrackReady = isReady;
  }

  @ReactMethod
  public void stop() throws IllegalStateException {
    if (this.mediaPlayer != null) {
      this.mediaPlayer.stop();
    }
  }

  @ReactMethod
  public void seek(float seconds) throws IllegalStateException {
    if (this.mediaPlayer != null) {
      this.mediaPlayer.seekTo((int)seconds * 1000);
    }
  }

  @ReactMethod
  public void setVolume(float volume) throws IOException {
    this.volume = volume;
    if (this.mediaPlayer != null) {
      this.mediaPlayer.setVolume(volume, volume);
    }
  }

  @ReactMethod
  public void getInfo(
      Promise promise) {
    WritableMap map = Arguments.createMap();
    if (this.mediaPlayer != null && this.isTrackReady == true){
      map.putDouble("currentTime", this.mediaPlayer.getCurrentPosition() / 1000.0);
      map.putDouble("duration", this.mediaPlayer.getDuration() / 1000.0);
    } else {
      map.putDouble("currentTime", 0);
      map.putDouble("duration", 0);
    }
    promise.resolve(map);
  }

  private void sendEvent(ReactApplicationContext reactContext,
                       String eventName,
                       @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  private void mountSoundFile(String name, String type) throws IOException {
    this.setTrackReady(false);
    if (this.mediaPlayer == null) {
      int soundResID = getReactApplicationContext().getResources().getIdentifier(name, "raw", getReactApplicationContext().getPackageName());

      if (soundResID > 0) {
        this.mediaPlayer = MediaPlayer.create(getCurrentActivity(), soundResID);
      } else {
        this.mediaPlayer = MediaPlayer.create(getCurrentActivity(), this.getUriFromFile(name, type));
      }

      if (this.mediaPlayer != null) {
        this.mediaPlayer.setOnCompletionListener(
          new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer arg0) {
              WritableMap params = Arguments.createMap();
              params.putBoolean("success", true);
              sendEvent(getReactApplicationContext(), EVENT_FINISHED_PLAYING, params);
            }
        });
      }
    } else {
      Uri uri;
      int soundResID = getReactApplicationContext().getResources().getIdentifier(name, "raw", getReactApplicationContext().getPackageName());

      if (soundResID > 0) {
        uri = Uri.parse("android.resource://" + getReactApplicationContext().getPackageName() + "/raw/" + name);
      } else {
        uri = this.getUriFromFile(name, type);
      }

      // catch for audio files that does not exist
      try {
      	this.mediaPlayer.reset();
      	this.mediaPlayer.setDataSource(getCurrentActivity(), uri);
      	this.mediaPlayer.prepare();
        this.setTrackReady(true);
      } catch (Exception e) {
        this.setTrackReady(false); // used to return duration = 0 on getInfo
      } 
    }

    try {
      WritableMap params = Arguments.createMap();
      params.putBoolean("success", true);
      sendEvent(getReactApplicationContext(), EVENT_FINISHED_LOADING, params);
      WritableMap onFinishedLoadingFileParams = Arguments.createMap();
      onFinishedLoadingFileParams.putBoolean("success", true);
      onFinishedLoadingFileParams.putString("name", name);
      onFinishedLoadingFileParams.putString("type", type);
      sendEvent(getReactApplicationContext(), EVENT_FINISHED_LOADING_FILE, onFinishedLoadingFileParams);
    } catch (Exception e) {

    }
  }

  private Uri getUriFromFile(String name, String type) {
    String folder = getReactApplicationContext().getFilesDir().getAbsolutePath();
    String file = name + "." + type;

    // http://blog.weston-fl.com/android-mediaplayer-prepare-throws-status0x1-error1-2147483648
    // this helps avoid a common error state when mounting the file
    File ref = new File(folder + "/" + file);

    if (ref.exists()) {
      ref.setReadable(true, false);
    }

    return Uri.parse("file://" + folder + "/" + file);
  }

  private void prepareUrl(final String url) throws IOException {
    if (this.mediaPlayer == null) {
      Uri uri = Uri.parse(url);
      this.mediaPlayer = MediaPlayer.create(getCurrentActivity(), uri);
      this.mediaPlayer.setOnCompletionListener(
        new OnCompletionListener() {
          @Override
          public void onCompletion(MediaPlayer arg0) {
            WritableMap params = Arguments.createMap();
            params.putBoolean("success", true);
            sendEvent(getReactApplicationContext(), EVENT_FINISHED_PLAYING, params);
          }
      });
      this.mediaPlayer.setOnPreparedListener(
        new OnPreparedListener() {
          @Override
          public void onPrepared(MediaPlayer mediaPlayer) {
            WritableMap onFinishedLoadingURLParams = Arguments.createMap();
            onFinishedLoadingURLParams.putBoolean("success", true);
            onFinishedLoadingURLParams.putString("url", url);
            sendEvent(getReactApplicationContext(), EVENT_FINISHED_LOADING_URL, onFinishedLoadingURLParams);
          }
        }
      );
    } else {
      Uri uri = Uri.parse(url);
      this.mediaPlayer.reset();
      this.mediaPlayer.setDataSource(getCurrentActivity(), uri);
      this.mediaPlayer.prepareAsync();
    }
    WritableMap params = Arguments.createMap();
    params.putBoolean("success", true);
    sendEvent(getReactApplicationContext(), EVENT_FINISHED_LOADING, params);
  }
}
