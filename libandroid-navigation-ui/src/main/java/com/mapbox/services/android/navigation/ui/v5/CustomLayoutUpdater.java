package com.mapbox.services.android.navigation.ui.v5;

import android.support.v4.view.AsyncLayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import timber.log.Timber;

class CustomLayoutUpdater {

  private final AsyncLayoutInflater inflater;

  CustomLayoutUpdater(AsyncLayoutInflater inflater) {
    this.inflater = inflater;
  }

  void update(FrameLayout frame, int layoutResId, OnLayoutReplacedListener replacedListener) {
    frame.removeAllViews();
    inflater.inflate(layoutResId, frame, new CustomLayoutInflateFinishedListener(replacedListener));
  }

  void update(FrameLayout frame, View customView) {
    if (customView == null) {
      Timber.e("Custom layout update failed: null View was provided.");
      return;
    }
    frame.removeAllViews();
    frame.addView(customView);
  }
}
