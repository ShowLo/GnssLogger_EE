/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.location.gps.pseudolite;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The UI fragment that hosts a logging view. */
public class LoggerFragment extends Fragment {

  private TextView mLogView;
  private ScrollView mScrollView;
  private FileLogger mFileLogger;
  private UiLogger mUiLogger;
  private Button mStartLog;
  private Button mStopLog;

  private static boolean autoScroll = false;


  private final UIFragmentComponent mUiComponent = new UIFragmentComponent();

  public void setUILogger(UiLogger value) {
    mUiLogger = value;
  }

  public void setFileLogger(FileLogger value) {
    mFileLogger = value;
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View newView = inflater.inflate(R.layout.fragment_log, container, false /* attachToRoot */);

    mLogView = (TextView) newView.findViewById(R.id.log_view);
    mScrollView = (ScrollView) newView.findViewById(R.id.log_scroll);

    UiLogger currentUiLogger = mUiLogger;
    if (currentUiLogger != null) {
      currentUiLogger.setUiFragmentComponent(mUiComponent);
    }
    FileLogger currentFileLogger = mFileLogger;
    if (currentFileLogger != null) {
      currentFileLogger.setUiComponent(mUiComponent);
    }

    Button start = (Button) newView.findViewById(R.id.start_log);
    Button end = (Button) newView.findViewById(R.id.end_log);
    Button clear = (Button) newView.findViewById(R.id.clear_log);

    start.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            mScrollView.fullScroll(View.FOCUS_UP);
          }
        });

    end.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            mScrollView.fullScroll(View.FOCUS_DOWN);
          }
        });

    clear.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            mLogView.setText("");
          }
        });

    mStartLog = (Button) newView.findViewById(R.id.start_logs);
    mStopLog = (Button) newView.findViewById(R.id.stop_logs);

    enableOptions(true /* start */);

    mStartLog.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View view) {
            enableOptions(false /* start */);
            Toast.makeText(getContext(), R.string.start_message, Toast.LENGTH_LONG).show();
            mFileLogger.startNewLog();
            }
        });
    mStopLog.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            enableOptions(true);
            Toast.makeText(getContext(), R.string.stop_message, Toast.LENGTH_LONG).show();
            mFileLogger.stopLog();
          }
        }
    );

    return newView;
  }

  private void enableOptions(boolean start) {
    mStartLog.setEnabled(start);
    mStopLog.setEnabled(!start);
  }

  /**
   * A facade for UI and Activity related operations that are required for {@link GnssListener}s.
   */
  public class UIFragmentComponent {

    private static final int MAX_LENGTH = 42000;
    private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);

    public synchronized void logTextFragment(final String tag, final String text, int color) {
      final SpannableStringBuilder builder = new SpannableStringBuilder();
      builder.append(tag).append(" | ").append(text).append("\n");
      builder.setSpan(
          new ForegroundColorSpan(color),
          0 /* start */,
          builder.length(),
          SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE);

      Activity activity = getActivity();
      if (activity == null) {
        return;
      }
      activity.runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              mLogView.append(builder);
              SharedPreferences sharedPreferences = PreferenceManager.
                  getDefaultSharedPreferences(getActivity());
              Editable editable = mLogView.getEditableText();
              int length = editable.length();
              if (length > MAX_LENGTH) {
                editable.delete(0, length - LOWER_THRESHOLD);
              }
              if (sharedPreferences.getBoolean(SettingsFragment.PREFERENCE_KEY_AUTO_SCROLL,
                  false /*default return value*/)){
                mScrollView.post(new Runnable() {
                  @Override
                  public void run() {
                    mScrollView.fullScroll(View.FOCUS_DOWN);
                  }
                });
              }
            }
          });
    }

    public void startActivity(Intent intent) {
      getActivity().startActivity(intent);
    }
  }
}
