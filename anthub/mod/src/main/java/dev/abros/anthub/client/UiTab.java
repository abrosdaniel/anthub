package dev.abros.anthub.client;
/** Compatibility constructor: every page and modal uses the same tab component. */
final class UiTab extends TabButton {
 UiTab(String label,int x,int y,int width,boolean selected,Runnable change){super(x,y,width,label,selected,change);}
}
