package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class HistoryScreen extends ScrollScreen {
 private final Screen parent;private final List<RepositoryClient.Release> releases;private final List<InstallationHistory.Entry> entries;private final String latest;
 public HistoryScreen(Screen parent,List<RepositoryClient.Release> releases,List<InstallationHistory.Entry> entries,String latest){super(Client.tr("history"));this.parent=parent;this.releases=releases;this.entries=entries;this.latest=latest;}
 protected void init(){scrollArea(releases.size(),35,height-66,24,width/2+153);for(int i=firstRow;i<Math.min(releases.size(),firstRow+visibleRows);i++){var release=releases.get(i);String version=release.manifest().version();boolean active=Client.hub.activeHash().equals(Hashes.sha256(release.bytes()));addRenderableWidget(Button.builder(Component.literal(version+(active?" · "+Client.tr("project.active").getString():"")),b->detail(release)).bounds(width/2-150,35+(i-firstRow)*24,300,20).build());}
  addRenderableWidget(Button.builder(Client.tr("history.journal"),b->{StringBuilder text=new StringBuilder();for(var entry:entries){text.append(entry.at()).append(" · ").append(entry.version()).append(" · ").append(Client.tr("history.status."+entry.status()).getString()).append("\n");for(var c:entry.changes())text.append(c.after()==null?"− ":c.before()==null?"+ ":"↻ ").append(c.path()).append("\n");text.append("\n");}minecraft.setScreen(new TextScreen(this,Client.tr("history.journal"),text.isEmpty()?Client.tr("history.empty").getString():text.toString()));}).bounds(width/2-150,height-54,300,20).build());
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-26,200,20).build());}
 private void detail(RepositoryClient.Release release){String version=release.manifest().version();String runtime=Client.hub.incompatibility(release.manifest());StringBuilder text=new StringBuilder(Client.tr("history.compatibility",runtime.isEmpty()?Client.tr("history.runtime.ok").getString():runtime).getString());
  text.append("\n\n").append(Client.tr(latest.isEmpty()?"history.server.unknown":latest.equals(version)?"history.server.latest":"history.server.old",latest).getString());
  for(var entry:entries)if(entry.version().equals(version)){text.append("\n\n").append(entry.at()).append(" · ").append(Client.tr("history.status."+entry.status()).getString());for(var c:entry.changes())text.append("\n").append(c.after()==null?"− ":c.before()==null?"+ ":"↻ ").append(c.path());}
  if(runtime.isEmpty())minecraft.setScreen(new ReviewScreen(this,Component.literal(version),text.toString(),Client.tr("history.restore"),()->minecraft.setScreen(new ComponentsScreen(this,release))));else minecraft.setScreen(new TextScreen(this,Component.literal(version),text.toString()));
 }
 public void render(GuiGraphics g,int x,int y,float t){super.render(g,x,y,t);g.drawCenteredString(font,title,width/2,12,0xE2BE75);if(releases.isEmpty())g.drawCenteredString(font,Client.tr("history.empty"),width/2,50,0xBBBBBB);}
 public void onClose(){minecraft.setScreen(parent);}
}
