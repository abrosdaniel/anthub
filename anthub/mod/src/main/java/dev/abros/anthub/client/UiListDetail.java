package dev.abros.anthub.client;
import dev.abros.anthub.core.NativeLayout;
/** Shared adaptive list/detail decision and column geometry. */
record UiListDetail(boolean split,NativeLayout.Box list,NativeLayout.Box detail){
 static UiListDetail fit(NativeLayout.Box area,int preferredList,boolean enoughHeight){boolean split=area.width()>=480&&enoughHeight;if(!split)return new UiListDetail(false,area,area);var columns=NativeLayout.row(area,12,NativeLayout.Track.fixed(Math.min(preferredList,area.width()/3)),NativeLayout.Track.flex(1));return new UiListDetail(true,columns.get(0),columns.get(1));}
}
