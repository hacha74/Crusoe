package com.crusoe.nav;

import android.content.Context;
import android.view.View;
import android.widget.TabHost.TabContentFactory;

public class CrusoeTabFactory implements TabContentFactory {

    private final Context mContext;

    public CrusoeTabFactory(Context context) {
        mContext = context;
    }

    public View createTabContent(String tag) {
        View v = new View(mContext);
        v.setMinimumWidth(0);
        v.setMinimumHeight(0);
        return v;
    }
}
