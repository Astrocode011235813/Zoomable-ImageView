# Zoomable ImageView

Android ImageView that supports double tap zoom,pinch zoom,fling.Supports **API 10** and above.

## Features

- Double tap zoom;
- Pinch zoom;
- Scrolling, smooth fling;
- Works in such ViewGroups as ViewPager, ScrollView, NestedScrollView;
- Event listener(onStartZoom,onScroll, ... etc.);
- Custom xml attributes (min zoom,max zoom, ... etc.).

## Usage

Example in xml:

    <RelativeLayout 
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        xmlns:app="http://schemas.android.com/apk/res-auto">
        <ru.astrocode.ziv.ZIVImageView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="fitCenter"
            android:src="@drawable/sample"
            app:maxZoom="3.0"
            app:minZoom="1"
            app:maxOverZoom="0.5"
            app:minOverZoom="0.5"
            app:overScrollDistance="50dp"
            app:animationDurationDoubleTap="300"
            app:animationDurationOverZoom="250"/>
    </RelativeLayout >

## License

Copyright 2018 Astrocode011235813

   Licensed under the Apache License, Version 2.0 (the "License");  
   you may not use this file except in compliance with the License.  
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software  
   distributed under the License is distributed on an "AS IS" BASIS,  
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
   See the License for the specific language governing permissions and  
   limitations under the License.