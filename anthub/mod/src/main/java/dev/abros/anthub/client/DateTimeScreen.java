package dev.abros.anthub.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
/** Calendar input used by event creation, rescheduling, polls and group tasks. */
final class DateTimeScreen extends Screen {
 private final Screen parent;private final Consumer<String> result;private LocalDate day;private YearMonth month;private int hour,minute;
 DateTimeScreen(Screen parent,String value,Consumer<String> result){super(Component.literal("Дата и время"));this.parent=parent;this.result=result;var initial=LocalDateTime.now().plusHours(1).withSecond(0);try{initial=LocalDateTime.parse(value,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));}catch(Exception ignored){}day=initial.toLocalDate();month=YearMonth.from(day);hour=initial.getHour();minute=initial.getMinute()/5*5;}
 private void button(String label,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,dev.abros.anthub.core.UiLayout.calendar(width,height).width(),Math.max(4,dev.abros.anthub.core.UiLayout.calendar(width,height).top()-10),dev.abros.anthub.core.UiLayout.calendar(width,height).time()+54);}
 @Override protected void init(){var layout=dev.abros.anthub.core.UiLayout.calendar(width,height);int w=layout.width(),x=layout.left(),y=layout.top(),cw=w/7;
  button("‹",x,y,28,()->{month=month.minusMonths(1);rebuildWidgets();});button("›",x+w-28,y,28,()->{month=month.plusMonths(1);rebuildWidgets();});
  int offset=month.atDay(1).getDayOfWeek().getValue()-1;
  for(int n=1;n<=month.lengthOfMonth();n++){final var date=month.atDay(n);int cell=n-1+offset;button((date.equals(day)?"[":"")+n+(date.equals(day)?"]":""),x+cell%7*cw,layout.gridTop()+cell/7*layout.cellHeight(),cw-2,()->{day=date;rebuildWidgets();});((Button)children().get(children().size()-1)).setHeight(layout.cellHeight()-2);}
  button("Сегодня",x,layout.shortcuts(),w/2-2,()->{day=LocalDate.now();month=YearMonth.from(day);rebuildWidgets();});button("Завтра",x+w/2+2,layout.shortcuts(),w/2-2,()->{day=LocalDate.now().plusDays(1);month=YearMonth.from(day);rebuildWidgets();});
  button("Час: "+String.format("%02d",hour),x,layout.time(),w/2-2,()->{hour=(hour+1)%24;rebuildWidgets();});button("Мин: "+String.format("%02d",minute),x+w/2+2,layout.time(),w/2-2,()->{minute=(minute+5)%60;rebuildWidgets();});
  button("Выбрать",x,layout.time()+26,w/2-2,()->{result.accept(day.atTime(hour,minute).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")));onClose();});button("Отмена",x+w/2+2,layout.time()+26,w/2-2,this::onClose);
 }
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);var layout=dev.abros.anthub.core.UiLayout.calendar(width,height);int top=layout.top(),w=layout.width(),left=layout.left();g.drawCenteredString(font,month.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE,java.util.Locale.forLanguageTag("ru"))+" "+month.getYear(),width/2,top+6,0xE2BE75);String[] days={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};for(int i=0;i<7;i++)g.drawString(font,days[i],left+i*(w/7)+6,top+27,0xCCCCCC);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
