package ru.astrocode.sample;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.Stack;

import ru.astrocode.ziv.ZIVImageView;

/**
 * Created by Astrocode on 29.06.2016.
 */
public class ActivityMain extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AdapterViewPager adapterViewPager = new AdapterViewPager(this);

        ViewPager viewPager = (ViewPager)findViewById(R.id.viewPager);
        viewPager.setAdapter(adapterViewPager);
    }

    class AdapterViewPager extends PagerAdapter{
        Context mContext;
        int [] mData;

        Stack<View> mViewCache;

        public AdapterViewPager(Context context){
            mContext = context;
            mViewCache = new Stack<>();
            mData = new int[]{R.drawable.sample,R.drawable.sample_1,R.drawable.sample_8};
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ZIVImageView imageView;

            if(mViewCache.size() > 0){
                imageView = (ZIVImageView)mViewCache.pop();
            }else {
                imageView = new ZIVImageView(mContext);
            }

            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setImageResource(mData[position]);

            container.addView(imageView);

            return imageView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View)object);
            mViewCache.push((View)object);
        }

        @Override
        public int getCount() {
            return mData.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }
}
