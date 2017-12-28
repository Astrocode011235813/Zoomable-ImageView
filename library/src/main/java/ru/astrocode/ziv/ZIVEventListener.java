package ru.astrocode.ziv;

/**
 * Created by Astrocode on 19.12.2017.
 */

public interface ZIVEventListener {

    void onSmoothZoomStarted(float currentZoom,float destinationZoom,float px,float py);
    void onSmoothZoomEnded(float currentZoom);

    void onPinchZoomStarted(float currentZoom,float px,float py);
    void onPinchZoom(float currentZoom,float zoomFactor);
    void onPinchZoomEnded(float currentZoom);

    void onScroll(int oldX,int oldY,int newX,int newY);
    void onFling(int oldX,int oldY,int newX,int newY);

}
