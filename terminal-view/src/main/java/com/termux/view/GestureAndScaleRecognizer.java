package com.termux.view;

import android.content.Context;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/** A combination of {@link GestureDetector} and {@link ScaleGestureDetector}. */
final class GestureAndScaleRecognizer {

    public interface Listener {
        boolean onSingleTapUp(MotionEvent e);

        boolean onDoubleTap(MotionEvent e);

        boolean onScroll(MotionEvent e2, float dx, float dy);

        boolean onFling(MotionEvent e, float velocityX, float velocityY);

        boolean onScale(float focusX, float focusY, float scale);

        boolean onDown(float x, float y);

        boolean onUp(MotionEvent e);

        void onLongPress(MotionEvent e);
    }

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleDetector;
    final Listener mListener;
    boolean isAfterLongPress;
    private long mLastScaleEndTime;

    public GestureAndScaleRecognizer(Context context, Listener listener) {
        mListener = listener;

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (isScaling() || (e2 != null && e2.getPointerCount() > 1) || (e1 != null && e1.getPointerCount() > 1)) {
                    return true;
                }
                return mListener.onScroll(e2, dx, dy);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isScaling() || (e2 != null && e2.getPointerCount() > 1) || (e1 != null && e1.getPointerCount() > 1)) {
                    return true;
                }
                return mListener.onFling(e2, velocityX, velocityY);
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return mListener.onDown(e.getX(), e.getY());
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (isScaling() || (e != null && e.getPointerCount() > 1)) return;
                mListener.onLongPress(e);
                isAfterLongPress = true;
            }
        }, null, true /* ignoreMultitouch */);

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isScaling() || (e != null && e.getPointerCount() > 1)) return true;
                return mListener.onSingleTapUp(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return mListener.onDoubleTap(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }
        });

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                return mListener.onScale(detector.getFocusX(), detector.getFocusY(), detector.getScaleFactor());
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                mLastScaleEndTime = SystemClock.uptimeMillis();
            }
        });
        mScaleDetector.setQuickScaleEnabled(false);
    }

    public void onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isAfterLongPress = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!isAfterLongPress && !isScaling()) {
                    // This behaviour is desired when in e.g. vim with mouse events, where we do not
                    // want to move the cursor when lifting finger after a long press.
                    mListener.onUp(event);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mListener.onUp(event);
                break;
        }
    }

    public boolean isInProgress() {
        return mScaleDetector.isInProgress();
    }

    public boolean isScaling() {
        return mScaleDetector.isInProgress() || (SystemClock.uptimeMillis() - mLastScaleEndTime < 250);
    }

}
