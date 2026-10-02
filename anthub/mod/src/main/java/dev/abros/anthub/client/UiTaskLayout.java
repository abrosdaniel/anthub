package dev.abros.anthub.client;
import dev.abros.anthub.core.NativeLayout;
import static dev.abros.anthub.core.NativeLayout.Track.*;
/** Responsive list/detail composition, reusable by other record pages. */
record UiTaskLayout(UiWorkspace workspace,boolean split,boolean showList,boolean showDetail,NativeLayout.Box list,NativeLayout.Box search,NativeLayout.Box filters,NativeLayout.Box create,NativeLayout.Box rows,NativeLayout.Box pagination,NativeLayout.Box detail,NativeLayout.Box detailHeader,NativeLayout.Box tabs,NativeLayout.Box body,NativeLayout.Box status) {
 static UiTaskLayout fit(int width,int height,boolean selected,boolean editing){
  var shell=UiWorkspace.fit(width,height);var page=shell.page();var master=UiListDetail.fit(page,220,page.height()>=230);boolean split=master.split();
  var list=master.list();var detail=master.detail();var listSlots=NativeLayout.column(list,6,fixed(20),fixed(20),fixed(20),flex(1),fixed(20));
  var detailSlots=editing?NativeLayout.column(detail,6,fixed(28),fixed(0),flex(1)):NativeLayout.column(detail,6,fixed(42),fixed(20),flex(1));
  var footer=UiPageFooter.fit(shell.footer());
  return new UiTaskLayout(shell,split,split||!selected&&!editing,split||selected||editing,list,listSlots.get(0),listSlots.get(1),listSlots.get(2),listSlots.get(3),listSlots.get(4),detail,detailSlots.get(0),detailSlots.get(1),detailSlots.get(2).inset(6),footer.status());
 }
}
