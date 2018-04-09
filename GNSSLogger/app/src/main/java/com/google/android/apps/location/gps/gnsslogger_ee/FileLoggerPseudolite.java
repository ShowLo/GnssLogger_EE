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

package com.google.android.apps.location.gps.gnsslogger_ee;

import android.content.Context;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.location.gps.gnsslogger_ee.PseudoliteFragment.UiPseudoliteFragmentComponent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A GNSS logger to store information to a file.
 */
public class FileLoggerPseudolite {

  private static final String TAG = "FileLoggerPseudolite";
  private static final String FILE_PREFIX = "gnss_log_pseudolite";
  private static final String ERROR_WRITING_FILE = "Problem writing to file.";

  private final Context mContext;

  private final Object mFileLock = new Object();
  private BufferedWriter mFileWriter;
  private File mFile;

  private boolean enableWrite = false;

  private UiPseudoliteFragmentComponent mUiComponent;

  public synchronized UiPseudoliteFragmentComponent getUiComponent() {
    return mUiComponent;
  }

  public synchronized void setUiComponent(UiPseudoliteFragmentComponent value) {
    mUiComponent = value;
  }

  public void setEnableWrite(boolean value) {
    enableWrite = value;
  }

  public boolean getEnableWrite() {
    return enableWrite;
  }

  public FileLoggerPseudolite(Context context) {
    this.mContext = context;
  }

  /**
   * Start a new file logging process.
   */
  public void startLogPseudolite() {
    synchronized (mFileLock) {
      File baseDirectory;
      String state = Environment.getExternalStorageState();
      if (Environment.MEDIA_MOUNTED.equals(state)) {
        baseDirectory = new File(Environment.getExternalStorageDirectory(), FILE_PREFIX);
        baseDirectory.mkdirs();
      } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
        logError("Cannot write to external storage.");
        return;
      } else {
        logError("Cannot read external storage.");
        return;
      }

      SimpleDateFormat formatter = new SimpleDateFormat("yyy_MM_dd_HH_mm_ss");
      Date now = new Date();
      String fileName = String.format("%s_%s.txt", FILE_PREFIX, formatter.format(now));
      File currentFile = new File(baseDirectory, fileName);
      String currentFilePath = currentFile.getAbsolutePath();
      BufferedWriter currentFileWriter;
      try {
        currentFileWriter = new BufferedWriter(new FileWriter(currentFile));
      } catch (IOException e) {
        logException("Could not open file: " + currentFilePath, e);
        return;
      }

      // initialize the contents of the file
      try {
        currentFileWriter.write(
            "Position calculated using Pseudolite");
        currentFileWriter.newLine();
      } catch (IOException e) {
        logException("Count not initialize file: " + currentFilePath, e);
        return;
      }

      if (mFileWriter != null) {
        try {
          mFileWriter.close();
        } catch (IOException e) {
          logException("Unable to close all file streams.", e);
          return;
        }
      }

      mFile = currentFile;
      mFileWriter = currentFileWriter;
      setEnableWrite(true);
      Toast.makeText(mContext, "File opened: " + currentFilePath, Toast.LENGTH_SHORT).show();

    }
  }

  public void writeNewMessage(String message) {
    synchronized (mFileLock) {
      if (mFileWriter != null) {
        try {
          mFileWriter.write(message);
        } catch (IOException e) {
          logException(ERROR_WRITING_FILE, e);
        }
      }
    }
  }

  /**
   * Stop the current log
   */
  public void stopLogPseudolite() {
    if (mFileWriter != null) {
      try {
        mFileWriter.flush();
        mFileWriter.close();
        mFileWriter = null;
        setEnableWrite(false);
      } catch (IOException e) {
        logException("Unable to close all file streams.", e);
        return;
      }
    }
  }

  private void logException(String errorMessage, Exception e) {
    Log.e(GnssContainer.TAG + TAG, errorMessage, e);
    Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
  }

  private void logError(String errorMessage) {
    Log.e(GnssContainer.TAG + TAG, errorMessage);
    Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();
  }
}
