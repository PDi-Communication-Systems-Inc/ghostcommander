package com.ghostsq.commander;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class LockableScrollView extends HorizontalScrollView {
    private boolean mScrollable = false;
    public LockableScrollView( Context context ) {
        super( context );
    }

    public  LockableScrollView( Context context, AttributeSet attrs ) {
        super( context, attrs );
    }

    public void setScrollable( boolean scrollable ) {
        mScrollable = scrollable;
    }

    @Override
    public boolean onTouchEvent( MotionEvent ev) {
        return false;
        /*
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // if we can scroll pass the event to the superclass
        if (mScrollable) return super.onTouchEvent(ev);
        // only continue to handle the touch event if scrolling enabled
        return mScrollable; // mScrollable is always false at this point
        default:
            return super.onTouchEvent(ev);
            */
    }
}
