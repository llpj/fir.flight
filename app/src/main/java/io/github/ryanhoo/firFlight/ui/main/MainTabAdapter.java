package io.github.ryanhoo.firFlight.ui.main;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import io.github.ryanhoo.firFlight.ui.base.BaseFragment;

/**
 * Created with Android Studio.
 * User: ryan@whitedew.me
 * Date: 7/8/16
 * Time: 9:24 PM
 * Desc: MainTabAdapter
 */
public class MainTabAdapter extends FragmentPagerAdapter {

    private String[] mTitles;
    private BaseFragment[] mFragments;

    public MainTabAdapter(FragmentManager fm, String[] titles, BaseFragment[] fragments) {
        super(fm);
        mTitles = titles;
        mFragments = fragments;
    }

    @Override
    public Fragment getItem(int position) {
        return mFragments[position];
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mTitles[position];
    }

    @Override
    public int getCount() {
        if (mTitles == null) return 0;
        return mTitles.length;
    }
}
