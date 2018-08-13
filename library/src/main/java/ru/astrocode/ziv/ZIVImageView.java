package ru.astrocode.ziv;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.ScaleGestureDetectorCompat;
import android.support.v4.view.ScrollingView;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.OverScroller;

import java.util.ArrayList;

import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static android.widget.ImageView.ScaleType.MATRIX;

/**
 * {@link android.widget.ImageView} that supports double tap zoom,pinch zoom,fling.
 * <p>
 * Created by Astrocode on 06.11.2017.
 */

public class ZIVImageView extends AppCompatImageView implements ScrollingView, NestedScrollingChild {
    private final static String sErrorInvalidArgumentDuration = "Minimum animation duration value cannot be less than 0.";
    private final static String sErrorInvalidArgumentOverScrollDistance = "Minimum over scroll distance value cannot be less than 0.";
    private final static String sErrorInvalidArgumentMinScale = "Minimum scale value cannot be less than 0 or more than 1.";
    private final static String sErrorInvalidArgumentMinOverScale = "Minimum over scale value cannot be less than 0 or more than 1.";
    private final static String sErrorInvalidArgumentMaxScale = "Maximum scale value cannot be less than 1.";
    private final static String sErrorInvalidArgumentMaxOverScale = "Maximum over scale value cannot be less than 0";

    private static final int DEFAULT_DOUBLE_TAP_SCALE_ANIMATION_DURATION = 300;
    private static final int DEFAULT_OVER_SCALE_ANIMATION_DURATION = 300;
    private static final int DEFAULT_OVER_SCROLL_DISTANCE = 50;

    private static final float DEFAULT_MAX_SCALE = 3f;
    private static final float DEFAULT_MIN_SCALE = 1f;

    private static final float DEFAULT_MAX_OVER_SCALE = 0.25f;
    private static final float DEFAULT_MIN_OVER_SCALE = 0.25f;

    private ArrayList<ATIZoomInfo> mLastZooms = new ArrayList<>();
    private ATIZoomInfo mCurrentZoom;

    private int mDoubleTapAnimationDuration;
    private int mOverZoomAnimationDuration;

    private final int[] mScrollConsumed = new int[2];
    private final int[] mScrollOffset = new int[2];
    private int mNestedXOffset, mNestedYOffset;

    private int mOverScrollDistance;

    private int mCurrentXOverScroll, mCurrentYOverScroll;
    private int mLastX, mLastY;

    private int mMainPointerId;
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity, mMaximumVelocity;

    private int mDoubleTapDistance, mDoubleTapTimeout;
    private long mLastDownTime;

    private PointF mCenterPoint = new PointF();

    private RectF mViewBounds = new RectF();
    private RectF mNormalDrawableRect = new RectF(), mCurrentDrawableRect = new RectF();

    private float mMinZoom, mMaxZoom;
    private float mMinOverZoom, mMaxOverZoom;

    public enum State {DISABLE, NORMAL, PINCH_SCALE, SMOOTH_SCALE, FLING, SCROLL}

    private State mCurrentState = State.DISABLE;

    private boolean mIsInit;

    private ScaleType mNormalScaleType;
    private Matrix mNormalMatrix, mDrawMatrix = new Matrix(), mCurrentMatrix = new Matrix();

    private final float[] mNormalMatrixValues = new float[9], mCurrentMatrixValues = new float[9];

    private ScaleGestureDetector mScaleGestureDetector;

    private ATISmoothScale mSmoothScaleTask;
    private ATIFling mFlingTask;

    private NestedScrollingChildHelper mChildHelper;
    private ZIVEventListener mEventListener;

    public ZIVImageView(Context context) {
        this(context, null);
    }

    public ZIVImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZIVImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.ZIVImageView, defStyleAttr, 0);

        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        final int overScrollDistance = Math.round(DEFAULT_OVER_SCROLL_DISTANCE * dm.density);

        setOverScrollDistance(array.getDimensionPixelSize(R.styleable.ZIVImageView_overScrollDistance, overScrollDistance));

        setDoubleTapAnimationDuration(array.getInt(R.styleable.ZIVImageView_animationDurationDoubleTap, DEFAULT_DOUBLE_TAP_SCALE_ANIMATION_DURATION));

        setOverZoomAnimationDuration(array.getInt(R.styleable.ZIVImageView_animationDurationOverZoom, DEFAULT_OVER_SCALE_ANIMATION_DURATION));

        setMinZoom(array.getFloat(R.styleable.ZIVImageView_minZoom, DEFAULT_MIN_SCALE));

        setMaxZoom(array.getFloat(R.styleable.ZIVImageView_maxZoom, DEFAULT_MAX_SCALE));

        setMinOverZoom(array.getFloat(R.styleable.ZIVImageView_minOverZoom, DEFAULT_MIN_OVER_SCALE));

        setMaxOverZoom(array.getFloat(R.styleable.ZIVImageView_maxOverZoom, DEFAULT_MAX_OVER_SCALE));

        array.recycle();

        ScaleType currentScaleType = getScaleType();
        if (currentScaleType != ScaleType.MATRIX) {
            setScaleType(FIT_CENTER);
        }

        setNestedScrollingEnabled(true);

        mScaleGestureDetector = new ScaleGestureDetector(context, mOnScaleGestureListener);
        ScaleGestureDetectorCompat.setQuickScaleEnabled(mScaleGestureDetector, false);

        ViewConfiguration vc = ViewConfiguration.get(context);

        mDoubleTapDistance = vc.getScaledDoubleTapSlop();
        mDoubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
        mMinimumVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumVelocity = vc.getScaledMaximumFlingVelocity();

        mSmoothScaleTask = new ATISmoothScale();
        mFlingTask = new ATIFling();
    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        if (mChildHelper == null) {
            mChildHelper = new NestedScrollingChildHelper(this);
        }
        mChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mChildHelper != null && mChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public int computeHorizontalScrollRange() {
        return (int) mCurrentDrawableRect.width();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return getScrollX() - Math.round(mCurrentDrawableRect.left) + mCurrentXOverScroll;
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return getWidth();
    }

    @Override
    public int computeVerticalScrollRange() {
        return (int) mCurrentDrawableRect.height();
    }

    @Override
    public int computeVerticalScrollOffset() {
        return getScrollY() - Math.round(mCurrentDrawableRect.top) + mCurrentYOverScroll;
    }

    @Override
    public int computeVerticalScrollExtent() {
        return getHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed || !mIsInit) {
            mViewBounds.set(0, 0, right - left, bottom - top);
            if (initNormalMatrix()) {
                init();
            } else {
                mCurrentState = State.DISABLE;
            }
            mIsInit = true;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = super.onTouchEvent(event);

        if (mCurrentState != State.DISABLE && isEnabled()) {
            int pointerIndex, action = event.getActionMasked();

            MotionEvent copyEvent = MotionEvent.obtain(event);

            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                if (mCurrentState == State.SCROLL) {
                    mCurrentState = State.NORMAL;
                }
            } else if (action == MotionEvent.ACTION_DOWN) {
                mNestedXOffset = 0;
                mNestedYOffset = 0;
            }

            copyEvent.offsetLocation(mNestedXOffset, mNestedYOffset);

            if (event.getPointerCount() > 1) {
                ret = mScaleGestureDetector.onTouchEvent(event) || ret;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_UP:
                    pointerIndex = event.getActionIndex();
                    if (event.getPointerId(pointerIndex) == mMainPointerId) {
                        int newPointerIndex = pointerIndex == 0 ? 1 : 0;

                        mLastX = Math.round(event.getX(newPointerIndex));
                        mLastY = Math.round(event.getY(newPointerIndex));

                        mMainPointerId = event.getPointerId(newPointerIndex);
                    }

                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (mCurrentState == State.SCROLL) {

                        int currX = getScrollX();
                        int currY = getScrollY();

                        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                        int velocityX = Math.round(mVelocityTracker.getXVelocity(mMainPointerId));
                        int velocityY = Math.round(mVelocityTracker.getYVelocity(mMainPointerId));

                        if (Math.abs(velocityX) >= mMinimumVelocity || Math.abs(velocityY) >= mMinimumVelocity) {
                            int maxX = Math.round(mCurrentDrawableRect.right) - getWidth();
                            int maxY = Math.round(mCurrentDrawableRect.bottom) - getHeight();

                            int minX = Math.round(mCurrentDrawableRect.left);
                            int minY = Math.round(mCurrentDrawableRect.top);

                            boolean isXFling = mCurrentDrawableRect.width() > getWidth();
                            boolean isYFling = mCurrentDrawableRect.height() > getHeight();

                            boolean isFlingOrSpringBack;

                            if (!isXFling && !isYFling) {
                                isFlingOrSpringBack = false;
                            } else {
                                isFlingOrSpringBack =
                                        (!isXFling || (currX > minX && currX < maxX)) &&
                                                (!isYFling || (currY > minY && currY < maxY));
                            }

                            if (isFlingOrSpringBack) {
                                if (!dispatchNestedPreFling(-velocityX, -velocityY)) {
                                    dispatchNestedFling(-velocityX, -velocityY, true);

                                    int axis;

                                    if (mCurrentXOverScroll == 0) {
                                        axis = ATIFling.ONLY_Y;
                                    } else if (mCurrentYOverScroll == 0) {
                                        axis = ATIFling.ONLY_X;
                                    } else {
                                        axis = ATIFling.X_AND_Y;
                                    }

                                    mFlingTask.start(ATIFling.MODE_FLING, axis, currX, currY,
                                            -Math.round(velocityX), -Math.round(velocityY), minX, minY, maxX, maxY);

                                    ret |= true;

                                }
                            } else {
                                if (!springBackIfOverScroll()) {
                                    mCurrentState = State.NORMAL;
                                }
                            }
                        } else {
                            if (!springBackIfOverScroll()) {
                                mCurrentState = State.NORMAL;
                            }
                        }
                    }

                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }

                    stopNestedScroll();
                    break;
                case MotionEvent.ACTION_DOWN:
                    float currX = event.getX();
                    float currY = event.getY();

                    if (mCurrentDrawableRect.contains(currX, currY)) {

                        if (mVelocityTracker == null) {
                            mVelocityTracker = VelocityTracker.obtain();
                        } else {
                            mVelocityTracker.clear();
                        }
                        mVelocityTracker.addMovement(event);

                        mMainPointerId = event.getPointerId(event.getActionIndex());

                        if (isDoubleTap(event.getEventTime(), currX, currY)) {
                            if (mCurrentState == State.NORMAL) {
                                float targetScale = 1F, currentScale = getCurrentZoom();
                                float x = currX, y = currY;

                                boolean correctTranslate = false;

                                if (Math.abs(1f - currentScale) <= 0.025f) {
                                    targetScale = mMaxZoom / 2F;

                                    mLastZooms.add(new ATIZoomInfo(getCurrentZoom(), targetScale, x, y));
                                } else {
                                    if (currentScale > 1f) {
                                        if (Math.abs(getCurrentZoom() - mMaxZoom) <= 0.025f) {
                                            x = mCenterPoint.x;
                                            y = mCenterPoint.y;

                                            correctTranslate = true;
                                        } else {
                                            targetScale = mMaxZoom;

                                            mLastZooms.add(new ATIZoomInfo(getCurrentZoom(), targetScale, x, y));
                                        }
                                    } else {
                                        x = mCenterPoint.x;
                                        y = mCenterPoint.y;

                                        correctTranslate = true;
                                    }
                                }

                                mSmoothScaleTask.start(targetScale, x, y, mDoubleTapAnimationDuration,
                                        0, 0, correctTranslate);

                                ret |= true;
                            }
                        } else {
                            mLastDownTime = event.getEventTime();

                            mLastX = Math.round(event.getX());
                            mLastY = Math.round(event.getY());

                            if (mCurrentState == State.FLING) {
                                if (!mFlingTask.isOverScrolled()) {
                                    mFlingTask.stop();
                                }
                            }

                            ret |= true;
                        }

                        startNestedScroll(ViewCompat.SCROLL_AXIS_HORIZONTAL | ViewCompat.SCROLL_AXIS_VERTICAL);
                    } else {
                        if (mCurrentState == State.NORMAL) {
                            mMainPointerId = -1;
                            ret |= true;
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mMainPointerId != -1) {
                        pointerIndex = event.findPointerIndex(mMainPointerId);
                        if (event.getPointerCount() == 1) {

                            int dx = Math.round(mLastX - event.getX(pointerIndex));
                            int dy = Math.round(mLastY - event.getY(pointerIndex));

                            if (dispatchNestedPreScroll(dx, dy, mScrollConsumed, mScrollOffset)) {
                                dx -= mScrollConsumed[0];
                                dy -= mScrollConsumed[1];

                                copyEvent.offsetLocation(mScrollOffset[0], mScrollOffset[1]);

                                mNestedXOffset += mScrollOffset[0];
                                mNestedYOffset += mScrollOffset[1];
                            }

                            if ((dx != 0 || dy != 0) &&
                                    (mCurrentState == State.NORMAL || mCurrentState == State.SCROLL)) {

                                mCurrentState = State.SCROLL;

                                mLastX = Math.round(event.getX(pointerIndex) - mScrollOffset[0]);
                                mLastY = Math.round(event.getY(pointerIndex) - mScrollOffset[1]);

                                if (mCurrentDrawableRect.width() > getWidth()) {
                                    if (dx < 0) {
                                        mCurrentXOverScroll = mOverScrollDistance;
                                    } else {
                                        mCurrentXOverScroll = -mOverScrollDistance;
                                    }

                                } else {
                                    mCurrentXOverScroll = 0;
                                }

                                if (mCurrentDrawableRect.height() > getHeight()) {
                                    if (dy < 0) {
                                        mCurrentYOverScroll = mOverScrollDistance;
                                    } else {
                                        mCurrentYOverScroll = -mOverScrollDistance;
                                    }
                                } else {
                                    mCurrentYOverScroll = 0;
                                }

                                int consumedDx = getCurrentPossibleScroll(dx, computeHorizontalScrollOffset(),
                                        computeHorizontalScrollRange(), computeHorizontalScrollExtent());
                                int consumedDy = getCurrentPossibleScroll(dy, computeVerticalScrollOffset(),
                                        computeVerticalScrollRange(), computeVerticalScrollExtent());

                                final int oldX = getScrollX();
                                final int oldY = getScrollY();

                                scrollBy(consumedDx, consumedDy);

                                if (mEventListener != null) {
                                    mEventListener.onScroll(oldX, oldY, getScrollX(), getScrollY());
                                }

                                if (dispatchNestedScroll(consumedDx, consumedDy, dx - consumedDx, dy - consumedDy, mScrollOffset)) {
                                    mLastX -= mScrollOffset[0];
                                    mLastY -= mScrollOffset[1];

                                    copyEvent.offsetLocation(mScrollOffset[0], mScrollOffset[1]);

                                    mNestedXOffset += mScrollOffset[0];
                                    mNestedYOffset += mScrollOffset[1];
                                }

                                ret |= true;
                            }
                        } else {
                            mLastX = Math.round(event.getX(pointerIndex));
                            mLastY = Math.round(event.getY(pointerIndex));
                        }
                    }
                    break;
                default:
                    break;
            }

            if (mVelocityTracker != null) {
                mVelocityTracker.addMovement(copyEvent);
            }

            copyEvent.recycle();
        }

        return ret;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        super.setScaleType(MATRIX);

        mCurrentState = State.DISABLE;

        removeCallbacks(mSmoothScaleTask);
        removeCallbacks(mFlingTask);

        mIsInit = false;
        mNormalScaleType = scaleType;

        requestLayout();
        invalidate();
    }

    @Override
    public void setImageMatrix(Matrix matrix) {
        super.setImageMatrix(matrix);

        mCurrentState = State.DISABLE;

        removeCallbacks(mSmoothScaleTask);
        removeCallbacks(mFlingTask);

        mIsInit = false;

        if (mNormalMatrix == null) {
            mNormalMatrix = new Matrix(matrix);
        } else {
            mNormalMatrix.set(matrix);
        }

        requestLayout();
        invalidate();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);

        mCurrentState = State.DISABLE;

        removeCallbacks(mSmoothScaleTask);
        removeCallbacks(mFlingTask);

        mIsInit = false;

        requestLayout();
        invalidate();
    }

    @Override
    public void setImageURI(@Nullable Uri uri) {
        super.setImageURI(uri);

        mCurrentState = State.DISABLE;

        removeCallbacks(mSmoothScaleTask);
        removeCallbacks(mFlingTask);

        mIsInit = false;

        requestLayout();
        invalidate();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);

        mCurrentState = State.DISABLE;

        removeCallbacks(mSmoothScaleTask);
        removeCallbacks(mFlingTask);

        mIsInit = false;

        requestLayout();
        invalidate();
    }

    private final ScaleGestureDetector.SimpleOnScaleGestureListener mOnScaleGestureListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private void scaleTo(ATIZoomInfo scaleInfo, float futureScale) {
            float currentPivotX = mCurrentZoom.mPivotX;
            float currentPivotY = mCurrentZoom.mPivotY;

            float tmpScaleFactor;

            while (scaleInfo != null) {

                if (scaleInfo.mScaleTo != getCurrentZoom()) {
                    tmpScaleFactor = scaleInfo.mScaleTo / getCurrentZoom();
                    scale(tmpScaleFactor, currentPivotX, currentPivotY);
                }

                currentPivotX = scaleInfo.mPivotX;
                currentPivotY = scaleInfo.mPivotY;

                if (scaleInfo.mScaleFrom <= futureScale) {
                    tmpScaleFactor = futureScale / getCurrentZoom();
                    scale(tmpScaleFactor, currentPivotX, currentPivotY);

                    if (futureScale == scaleInfo.mScaleFrom) {
                        mLastZooms.remove(mLastZooms.size() - 1);
                        mCurrentZoom.mScaleFrom = scaleInfo.mScaleFrom;
                    } else {
                        scaleInfo.mScaleTo = getCurrentZoom();
                        mCurrentZoom.mScaleFrom = scaleInfo.mScaleTo;
                    }

                    scaleInfo = null;
                } else {
                    mLastZooms.remove(mLastZooms.size() - 1);
                    if (mLastZooms.size() > 0) {
                        scaleInfo = mLastZooms.get(mLastZooms.size() - 1);
                    } else {
                        scaleInfo = null;
                    }
                }
            }
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            final float scaleFactor = detector.getScaleFactor();
            final float scaleSubValue = Math.abs(1.0f - scaleFactor);

            if (scaleSubValue > 0.015f && scaleSubValue < 0.25f) {
                final float currentScale = getCurrentZoom();
                final float futureScale = currentScale * scaleFactor;

                if (futureScale > mMinZoom - mMinOverZoom && futureScale < mMaxZoom + mMaxOverZoom) {
                    if (mEventListener != null) {
                        mEventListener.onPinchZoom(currentScale, scaleFactor);
                    }
                    if (scaleFactor > 1F) {
                        if (futureScale <= 1.0f) {
                            scale(scaleFactor);
                        } else {
                            scale(scaleFactor, mCurrentZoom.mPivotX, mCurrentZoom.mPivotY);
                        }
                    } else {
                        if (futureScale <= 1.0f) {
                            if (mLastZooms.size() > 0) {
                                ATIZoomInfo scaleInfo = mLastZooms.get(mLastZooms.size() - 1);
                                scaleTo(scaleInfo, futureScale);

                                float tmpScaleFactor = futureScale / getCurrentZoom();
                                scale(tmpScaleFactor);

                                scrollTo(0, 0);
                            } else {
                                if (currentScale > 1f) {
                                    float tmpScaleFactor = 1f / currentScale;
                                    scale(tmpScaleFactor, mCurrentZoom.mPivotX, mCurrentZoom.mPivotY);

                                    tmpScaleFactor = futureScale / getCurrentZoom();
                                    scale(tmpScaleFactor);
                                } else {
                                    scale(scaleFactor);
                                }
                            }
                        } else {
                            if (mLastZooms.size() > 0) {
                                ATIZoomInfo scaleInfo = mLastZooms.get(mLastZooms.size() - 1);

                                if (futureScale > scaleInfo.mScaleTo) {
                                    scale(scaleFactor, mCurrentZoom.mPivotX, mCurrentZoom.mPivotY);
                                } else {
                                    scaleTo(scaleInfo, futureScale);
                                }

                                scrollTo(Math.round(getScrollX() * 0.8f), Math.round(getScrollY() * 0.8f));
                            } else {
                                scale(scaleFactor, mCurrentZoom.mPivotX, mCurrentZoom.mPivotY);
                            }
                        }
                    }

                }
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (mCurrentState == State.NORMAL || mCurrentState == State.SCROLL) {

                mCurrentState = State.PINCH_SCALE;

                mCurrentZoom = new ATIZoomInfo();

                mCurrentZoom.mScaleFrom = getCurrentZoom();
                if (mCurrentZoom.mScaleFrom < 1f) {
                    mCurrentZoom.mScaleFrom = 1f;
                }

                mCurrentZoom.mPivotX = detector.getFocusX();
                mCurrentZoom.mPivotY = detector.getFocusY();

                if (mEventListener != null) {
                    mEventListener.onPinchZoomStarted(mCurrentZoom.mScaleFrom, mCurrentZoom.mPivotX, mCurrentZoom.mPivotY);
                }

                return true;
            }

            return false;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            final float currentScale = getCurrentZoom();

            if (mEventListener != null) {
                mEventListener.onPinchZoomEnded(currentScale);
            }

            if (currentScale < mMinZoom) {
                mSmoothScaleTask.start(mMinZoom, mCenterPoint.x, mCenterPoint.y, mOverZoomAnimationDuration, 0, 0, false);
            } else if (currentScale > mMaxZoom) {

                if (mMaxZoom > mCurrentZoom.mScaleFrom) {
                    mCurrentZoom.mScaleTo = mMaxZoom;
                    mLastZooms.add(mCurrentZoom);
                }

                mSmoothScaleTask.start(mMaxZoom, mCurrentZoom.mPivotX, mCurrentZoom.mPivotY, mOverZoomAnimationDuration, 0, 0, false);
            } else {
                if (currentScale > mCurrentZoom.mScaleFrom) {
                    mCurrentZoom.mScaleTo = currentScale;
                    mLastZooms.add(mCurrentZoom);
                }

                if (mCurrentDrawableRect.width() <= getWidth()) {
                    mCurrentXOverScroll = 0;
                }

                if (mCurrentDrawableRect.height() <= getHeight()) {
                    mCurrentYOverScroll = 0;
                }

                if (!springBackIfOverScroll()) {
                    mCurrentState = State.NORMAL;
                }
            }
        }
    };

    private boolean isDoubleTap(long currTime, float currX, float currY) {
        return (currTime - mLastDownTime) <= mDoubleTapTimeout &&
                Math.sqrt(Math.pow(Math.abs(currX - mLastX), 2) + Math.pow(Math.abs(currY - mLastY), 2)) <= mDoubleTapDistance;
    }

    private int getCurrentPossibleScroll(int deltaValue, int scrollOffset, int scrollRange, int scrollExtent) {
        final int range = scrollRange - scrollExtent;
        int ret = 0, tmp;

        if (range <= 0) return ret;

        if (deltaValue < 0) {
            tmp = scrollOffset + deltaValue;
            if (tmp >= 0) {
                ret = deltaValue;
            } else {
                ret = deltaValue - tmp;
            }
        } else {
            tmp = range - scrollOffset;
            if (tmp >= deltaValue) {
                ret = deltaValue;
            } else {
                ret = tmp;
            }
        }

        return ret;
    }

    /**
     * Sets event listener.
     *
     * @param eventListener Event listener.
     */
    public void setEventListener(ZIVEventListener eventListener) {
        mEventListener = eventListener;
    }

    /**
     * Returns double tap animation duration.
     *
     * @return Animation duration(ms).
     */
    public int getDoubleTapAnimationDuration() {
        return mDoubleTapAnimationDuration;
    }

    /**
     * Sets double tap animation duration.
     *
     * @param doubleTapAnimationDuration Animation duration(ms).
     */
    public void setDoubleTapAnimationDuration(int doubleTapAnimationDuration) {
        if (doubleTapAnimationDuration < 0) {
            throw new IllegalArgumentException(sErrorInvalidArgumentDuration);
        }
        mDoubleTapAnimationDuration = doubleTapAnimationDuration;
    }

    /**
     * Returns over zooming animation duration.
     *
     * @return Animation duration(ms).
     */
    public int getOverZoomAnimationDuration() {
        return mOverZoomAnimationDuration;
    }

    /**
     * Sets over zooming animation duration.
     *
     * @param overZoomAnimationDuration Animation duration(ms).
     */
    public void setOverZoomAnimationDuration(int overZoomAnimationDuration) {
        if (overZoomAnimationDuration < 0) {
            throw new IllegalArgumentException(sErrorInvalidArgumentDuration);
        }
        mOverZoomAnimationDuration = overZoomAnimationDuration;
    }

    /**
     * Returns over scroll distance.
     *
     * @return Over scroll distance(dp).
     */
    public int getOverScrollDistance() {
        return mOverScrollDistance;
    }

    /**
     * Sets over scroll distance.
     *
     * @param overScrollDistance Over scroll distance(px).
     */
    public void setOverScrollDistance(int overScrollDistance) {
        if (overScrollDistance < 0) {
            throw new IllegalArgumentException(sErrorInvalidArgumentOverScrollDistance);
        }
        mOverScrollDistance = overScrollDistance;
    }

    /**
     * Sets the minimum zooming value.
     *
     * @param minZoom Minimum zooming value.
     */
    public void setMinZoom(float minZoom) {
        if (minZoom < 0 || minZoom > 1f) {
            throw new IllegalArgumentException(sErrorInvalidArgumentMinScale);
        }
        mMinZoom = minZoom;
    }

    /**
     * Sets the maximum zooming value.
     *
     * @param maxZoom Maximum zooming value.
     */
    public void setMaxZoom(float maxZoom) {
        if (maxZoom < 1) {
            throw new IllegalArgumentException(sErrorInvalidArgumentMaxScale);
        }
        mMaxZoom = maxZoom;
    }

    /**
     * Sets value that subtracts from the minimum zoom value.
     *
     * @param minOverZoom Over zooming value.
     */
    public void setMinOverZoom(float minOverZoom) {
        if (minOverZoom < 0 || minOverZoom > 1f) {
            throw new IllegalArgumentException(sErrorInvalidArgumentMinOverScale);
        }
        mMinOverZoom = minOverZoom;
    }

    /**
     * Sets value that adds to the maximum zoom value.
     *
     * @param maxOverZoom Over zooming value.
     */
    public void setMaxOverZoom(float maxOverZoom) {
        if (maxOverZoom < 0) {
            throw new IllegalArgumentException(sErrorInvalidArgumentMaxOverScale);
        }
        mMaxOverZoom = maxOverZoom;
    }

    /**
     * Returns the value that subtracts from the minimum zoom value.
     *
     * @return Over zoom value.
     */
    public float getMinOverZoom() {
        return mMinOverZoom;
    }

    /**
     * Returns the value that adds to the maximum zoom value.
     *
     * @return Over zoom value.
     */
    public float getMaxOverZoom() {
        return mMaxOverZoom;
    }

    /**
     * Returns minimum zoom value.
     *
     * @return Min zoom value.
     */
    public float getMinZoom() {
        return mMinZoom;
    }

    /**
     * Returns maximum zoom value.
     *
     * @return Max zoom value.
     */
    public float getMaxZoom() {
        return mMaxZoom;
    }

    /**
     * Returns current zoom value.
     *
     * @return Current zoom value.
     */
    public float getCurrentZoom() {
        return mCurrentState != State.DISABLE ? mCurrentMatrixValues[Matrix.MSCALE_X] : 1f;
    }

    /**
     * Zooming image with a pivot point at center to the zoomValue.
     *
     * @param zoomValue New zoom value.
     */
    public void setZoom(float zoomValue) {
        setZoom(zoomValue, mCenterPoint.x, mCenterPoint.y, false);
    }

    /**
     * Zooming image with a pivot point at center to the zoomValue.
     *
     * @param zoomValue New zoom value.
     * @param animate   If true animate zooming.
     */
    public void setZoom(float zoomValue, boolean animate) {
        setZoom(zoomValue, mCenterPoint.x, mCenterPoint.y, animate);
    }

    /**
     * Zooming image with a pivot point at (sx,sy) to the zoomValue.
     *
     * @param zoomValue New zoom value.
     * @param px        Pivot point x.
     * @param py        Pivot point y.
     * @param animate   If true animate zooming.
     */
    public void setZoom(float zoomValue, float px, float py, boolean animate) {
        if (mCurrentState == State.NORMAL && (zoomValue >= mMinZoom && zoomValue <= mMaxZoom)) {
            if (animate) {
                mSmoothScaleTask.start(zoomValue, px, py, mDoubleTapAnimationDuration, 0, 0, false);
            } else {
                scale(zoomValue / getCurrentZoom(), px, py);
            }
        }
    }

    /**
     * Resets all zooming.
     *
     * @param animate Animating the reset if true.
     */
    public void reset(boolean animate) {
        if (mCurrentState != State.DISABLE) {

            removeCallbacks(mSmoothScaleTask);
            removeCallbacks(mFlingTask);

            if (animate) {
                mSmoothScaleTask.start(1F, mCenterPoint.x, mCenterPoint.y, mDoubleTapAnimationDuration, 0, 0, true);
            } else {
                mCurrentState = State.NORMAL;

                mDrawMatrix.set(mNormalMatrix);

                mCurrentMatrix.reset();
                mCurrentMatrix.getValues(mCurrentMatrixValues);

                mCurrentDrawableRect.set(mNormalDrawableRect);

                mLastZooms.clear();

                mCurrentZoom = new ATIZoomInfo();
                mCurrentZoom.mPivotX = mCenterPoint.x;
                mCurrentZoom.mPivotY = mCenterPoint.y;

                scrollTo(0, 0);

                super.setImageMatrix(mDrawMatrix);
            }
        }
    }

    /**
     * Resets all zooming.
     */
    public void reset() {
        reset(false);
    }

    float getCurrentX() {
        return mCurrentMatrixValues[Matrix.MTRANS_X];
    }

    float getCurrentY() {
        return mCurrentMatrixValues[Matrix.MTRANS_Y];
    }

    private void init() {
        removeCallbacks(mSmoothScaleTask);
        removeCallbacks(mFlingTask);

        mCurrentState = State.NORMAL;

        mDrawMatrix.set(mNormalMatrix);

        mCurrentMatrix.reset();
        mCurrentMatrix.getValues(mCurrentMatrixValues);

        mNormalMatrix.getValues(mNormalMatrixValues);

        mNormalDrawableRect.set(getDrawable().getBounds());

        mNormalMatrix.mapRect(mNormalDrawableRect);

        if (!mViewBounds.contains(mNormalDrawableRect)) {
            mNormalDrawableRect.intersect(mViewBounds);
        }

        mCurrentDrawableRect.set(mNormalDrawableRect);

        mCurrentXOverScroll = 0;
        mCurrentYOverScroll = 0;

        mCenterPoint.x = Math.round(mNormalDrawableRect.centerX());
        mCenterPoint.y = Math.round(mNormalDrawableRect.centerY());

        mCurrentZoom = new ATIZoomInfo();
        mCurrentZoom.mPivotX = mCenterPoint.x;
        mCurrentZoom.mPivotY = mCenterPoint.y;

        mLastZooms.clear();

        scrollTo(0, 0);

        super.setImageMatrix(mDrawMatrix);
    }

    private boolean initNormalMatrix() {
        int viewWidth = getWidth(), viewHeight = getHeight();
        final Drawable drawable = getDrawable();

        if (drawable == null || viewWidth == 0 || viewHeight == 0) {
            mNormalMatrix = null;
            return false;
        }

        viewWidth = viewWidth - (getPaddingLeft() + getPaddingRight());
        viewHeight = viewHeight - (getPaddingTop() + getPaddingBottom());

        final int drawableWidth = drawable.getIntrinsicWidth(), drawableHeight = drawable.getIntrinsicHeight();

        if (drawableWidth > 0 && drawableHeight > 0) {

            if (mNormalMatrix == null) {
                mNormalMatrix = new Matrix();
            }

            if (drawableWidth != viewWidth || drawableHeight != viewHeight) {
                float sx, sy, tx = 0, ty = 0;
                boolean sxLarger = false;

                switch (mNormalScaleType) {
                    case FIT_CENTER:
                        sx = (float) viewWidth / (float) drawableWidth;
                        sy = (float) viewHeight / (float) drawableHeight;

                        if (sx > sy) {
                            sxLarger = true;
                            sx = sy;
                        } else sy = sx;

                        if (sxLarger) tx += Math.round((viewWidth - drawableWidth * sx) * 0.5f);
                        else ty += Math.round((viewHeight - drawableHeight * sy) * 0.5f);

                        mNormalMatrix.setScale(sx, sy);
                        mNormalMatrix.postTranslate(tx, ty);
                        break;
                    case CENTER_INSIDE:
                        sx = 1.0f;

                        if (viewHeight < drawableHeight || viewWidth < drawableWidth) {
                            sx = Math.min(((float) viewWidth / (float) drawableWidth), ((float) viewHeight / (float) drawableHeight));
                        }

                        tx = Math.round((viewWidth - drawableWidth * sx) * 0.5f);
                        ty = Math.round((viewHeight - drawableHeight * sx) * 0.5f);


                        mNormalMatrix.setScale(sx, sx);
                        mNormalMatrix.postTranslate(tx, ty);
                        break;
                    case CENTER_CROP:
                        int S1 = viewHeight * drawableWidth;
                        int S2 = viewWidth * drawableHeight;

                        if (S1 > S2) {
                            sx = ((float) viewHeight / (float) drawableHeight);
                        } else {
                            sx = ((float) viewWidth / (float) drawableWidth);
                        }

                        tx = Math.round((viewWidth - drawableWidth * sx) * 0.5f);
                        ty = Math.round((viewHeight - drawableHeight * sx) * 0.5f);

                        mNormalMatrix.setScale(sx, sx);
                        mNormalMatrix.postTranslate(tx, ty);
                        break;
                    case CENTER:
                        tx = Math.round((viewWidth - drawableWidth) * 0.5f);
                        ty = Math.round((viewHeight - drawableHeight) * 0.5f);

                        mNormalMatrix.setTranslate(tx, ty);
                        break;
                    case FIT_END:
                        sx = (float) viewWidth / (float) drawableWidth;
                        sy = (float) viewHeight / (float) drawableHeight;

                        if (sx > sy) {
                            sxLarger = true;
                            sx = sy;
                        } else sy = sx;

                        if (sxLarger) tx += Math.round((viewWidth - drawableWidth * sx));
                        else ty += Math.round((viewHeight - drawableHeight * sy));

                        mNormalMatrix.setScale(sx, sy);
                        mNormalMatrix.postTranslate(tx, ty);
                        break;
                    case FIT_START:
                        sx = (float) viewWidth / (float) drawableWidth;
                        sy = (float) viewHeight / (float) drawableHeight;

                        if (sx > sy) {
                            sx = sy;
                        } else sy = sx;

                        mNormalMatrix.setScale(sx, sy);
                        break;
                    case FIT_XY:
                        sx = (float) viewWidth / (float) drawableWidth;
                        sy = (float) viewHeight / (float) drawableHeight;

                        mNormalMatrix.setScale(sx, sy);
                        break;
                    default:
                        break;
                }
            }
        } else {
            mNormalMatrix = null;
            return false;
        }

        return true;
    }


    void scale(float scaleFactor) {
        scale(scaleFactor, mCenterPoint.x, mCenterPoint.y);
    }

    void scale(float scaleFactor, float pivotX, float pivotY) {
        mCurrentMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY);
        mCurrentMatrix.getValues(mCurrentMatrixValues);

        mCurrentDrawableRect.set(mNormalDrawableRect);
        mCurrentMatrix.mapRect(mCurrentDrawableRect);

        mDrawMatrix.set(mNormalMatrix);
        mDrawMatrix.postConcat(mCurrentMatrix);

        super.setImageMatrix(mDrawMatrix);
    }

    void translate(float dx, float dy) {
        mCurrentMatrix.postTranslate(dx, dy);
        mCurrentMatrix.getValues(mCurrentMatrixValues);

        mCurrentDrawableRect.set(mNormalDrawableRect);
        mCurrentMatrix.mapRect(mCurrentDrawableRect);

        mDrawMatrix.set(mNormalMatrix);
        mDrawMatrix.postConcat(mCurrentMatrix);

        super.setImageMatrix(mDrawMatrix);
    }

    private boolean springBackIfOverScroll() {
        boolean ret = false;

        final int scrollX = getScrollX();
        final int scrollY = getScrollY();

        if (mCurrentXOverScroll != 0 || mCurrentYOverScroll != 0) {
            int maxX, minX;
            int maxY, minY;

            if (mCurrentDrawableRect.contains(mViewBounds)) {
                maxX = Math.round(mCurrentDrawableRect.right) - getWidth();
                maxY = Math.round(mCurrentDrawableRect.bottom) - getHeight();

                minX = Math.round(mCurrentDrawableRect.left);
                minY = Math.round(mCurrentDrawableRect.top);
            } else {

                if (mCurrentXOverScroll != 0) {
                    maxX = Math.round(mCurrentDrawableRect.right) - getWidth();
                    minX = Math.round(mCurrentDrawableRect.left);
                } else {
                    maxX = 0;
                    minX = 0;
                }

                if (mCurrentYOverScroll != 0) {
                    maxY = Math.round(mCurrentDrawableRect.bottom) - getHeight();
                    minY = Math.round(mCurrentDrawableRect.top);
                } else {
                    maxY = 0;
                    minY = 0;
                }

            }

            if (mCurrentXOverScroll == 0) {
                ret = mFlingTask.start(ATIFling.MODE_SPRINGBACK, ATIFling.ONLY_Y, scrollX, scrollY, 0, 0, minX, minY, maxX, maxY);
            } else if (mCurrentYOverScroll == 0) {
                ret = mFlingTask.start(ATIFling.MODE_SPRINGBACK, ATIFling.ONLY_X, scrollX, scrollY, 0, 0, minX, minY, maxX, maxY);
            } else {
                ret = mFlingTask.start(ATIFling.MODE_SPRINGBACK, ATIFling.X_AND_Y, scrollX, scrollY, 0, 0, minX, minY, maxX, maxY);
            }
        }

        return ret;
    }

    private final static class ATIZoomInfo {
        float mScaleFrom, mScaleTo;
        float mPivotX, mPivotY;

        public ATIZoomInfo() {
            this(0, 0, 0, 0);
        }

        public ATIZoomInfo(float scaleFrom, float scaleTo, float pivotX, float pivotY) {
            mScaleFrom = scaleFrom;
            mScaleTo = scaleTo;
            mPivotX = pivotX;
            mPivotY = pivotY;
        }
    }

    private final class ATISmoothScale implements Runnable {
        private float mTargetScale;
        private float mPivotX, mPivotY;
        private float mDuration;

        private Interpolator mInterpolator = new AccelerateDecelerateInterpolator();

        private long mStartTime;

        private boolean mCorrectTranslate;
        private float mToX, mToY;

        public void start(float targetScale, float pivotX, float pivotY, float duration, float toX, float toY, boolean correctTranslate) {
            mTargetScale = targetScale;
            mStartTime = System.currentTimeMillis();
            mPivotX = pivotX;
            mPivotY = pivotY;
            mDuration = duration;
            mToX = toX;
            mToY = toY;
            mCorrectTranslate = correctTranslate;

            mCurrentState = State.SMOOTH_SCALE;

            if (mEventListener != null) {
                mEventListener.onSmoothZoomStarted(getCurrentZoom(), targetScale, pivotX, pivotY);
            }

            ViewCompat.postOnAnimationDelayed(ZIVImageView.this, this, 15L);
        }

        @Override
        public void run() {
            float timeValue = Math.min(mDuration, System.currentTimeMillis() - mStartTime) / mDuration;
            float interpolatedTimeValue = mInterpolator.getInterpolation(timeValue);
            float currentScaleFactor = findCurrentScaleFactor(interpolatedTimeValue, getCurrentZoom());

            final float oldX = getCurrentX();
            final float oldY = getCurrentY();

            scale(currentScaleFactor, mPivotX, mPivotY);

            if (mCorrectTranslate) {
                float newX = findCurrentTranslateTerm(interpolatedTimeValue, oldX, mToX);
                float newY = findCurrentTranslateTerm(interpolatedTimeValue, oldY, mToY);

                final float currentX = getCurrentX();
                final float currentY = getCurrentY();

                newX = newX - currentX;
                newY = newY - currentY;

                translate(newX, newY);
            }

            if (timeValue < 1f) {
                ViewCompat.postOnAnimationDelayed(ZIVImageView.this, this, 15L);
                if ((getCurrentZoom() < mMaxZoom && mTargetScale < getCurrentZoom()) || getCurrentZoom() < 1f) {
                    scrollTo((int) (getScrollX() * (1.0f - timeValue)), (int) (getScrollY() * (1.0f - timeValue)));
                }
            } else {
                mCurrentState = State.NORMAL;

                if (mEventListener != null) {
                    mEventListener.onSmoothZoomEnded(getCurrentZoom());
                }

                if (Math.abs(1f - getCurrentZoom()) <= 0.025f) {
                    reset();
                }
            }
        }

        private float findCurrentScaleFactor(float interpolatedTimeValue, float currentScale) {
            return interpolatedTimeValue != 0 ?
                    (currentScale + interpolatedTimeValue * (mTargetScale - currentScale)) / currentScale
                    : 1f;
        }

        private float findCurrentTranslateTerm(float interpolatedTimeValue, float translateValue, float toValue) {
            return interpolatedTimeValue != 0 ?
                    (translateValue + interpolatedTimeValue * (toValue - translateValue)) : 0;
        }

    }

    final class ATIFling implements Runnable {
        static final int MODE_FLING = 0;
        static final int MODE_SPRINGBACK = 1;

        static final int X_AND_Y = 0;
        static final int ONLY_X = 1;
        static final int ONLY_Y = 2;

        final private OverScroller mScroller = new OverScroller(getContext());


        boolean start(int mode, int axis, int startX, int startY, int velocityX, int velocityY, int minX, int minY, int maxX, int maxY) {
            boolean ret = true;

            mScroller.forceFinished(true);

            if (mode == MODE_SPRINGBACK) {
                // OverScroller bug : https://issuetracker.google.com/36959308
                mScroller.startScroll(startX, startY, 0, 0, 0);
                mScroller.computeScrollOffset();
                //------------

                if (axis == ONLY_X) {
                    ret = mScroller.springBack(startX, startY, minX, maxX, startY, startY);
                } else if (axis == ONLY_Y) {
                    ret = mScroller.springBack(startX, startY, startX, startX, minY, maxY);
                } else {
                    ret = mScroller.springBack(startX, startY, minX, maxX, minY, maxY);
                }
            } else {
                if (axis == ONLY_X) {
                    mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, startY, startY, mOverScrollDistance, 0);
                } else if (axis == ONLY_Y) {
                    mScroller.fling(startX, startY, velocityX, velocityY, startX, startX, minY, maxY, 0, mOverScrollDistance);
                } else {
                    mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, mOverScrollDistance, mOverScrollDistance);
                }
            }

            if (ret) {
                mCurrentState = State.FLING;

                ViewCompat.postOnAnimation(ZIVImageView.this, this);
            }

            return ret;
        }

        @Override
        public void run() {
            if (mScroller.computeScrollOffset()) {

                final int oldX = getScrollX();
                final int oldY = getScrollY();

                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());

                if (mEventListener != null) {
                    mEventListener.onFling(oldX, oldY, getScrollX(), getScrollY());
                }

                ViewCompat.postOnAnimationDelayed(ZIVImageView.this, this, 15L);
            } else {
                mCurrentState = State.NORMAL;
            }
        }

        void stop() {
            mScroller.forceFinished(true);
            mCurrentState = State.NORMAL;
        }

        boolean isOverScrolled() {
            return mScroller.isOverScrolled();
        }
    }
}