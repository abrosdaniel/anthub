package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ComponentsScreen extends ScrollScreen {
    private final Screen parent;private final RepositoryClient.Release release;private Set<String> selected;private volatile String status="";private volatile boolean busy;private final Map<Button,Boolean> enabled=new HashMap<>();private Button apply,back;private AtomicBoolean cancel=new AtomicBoolean();private final PackOperations operations;
    public ComponentsScreen(Screen parent,RepositoryClient.Release release){super(Client.tr("components"));this.parent=parent;this.release=release;operations=new PackOperations(Client.hub,Client.IO);selected=Client.hub.choices(release.manifest());}
    private Component action(){return Client.tr(Client.hub.active()!=null&&Client.hub.active().repository().equals(release.manifest().repository())?"update":"install");}
    @Override protected void init(){enabled.clear();var cs=release.manifest().components();scrollArea(cs.size(),35,height-90,24,width/2+153);
        for(int i=firstRow;i<Math.min(firstRow+visibleRows,cs.size());i++){var c=cs.get(i);var button=Button.builder(Component.literal((selected.contains(c.id())?"☑ ":"☐ ")+c.name()+" · "+Client.tr("kind."+c.kind()).getString()),b->{if(busy)return;Set<String> choice=new HashSet<>(selected);if(!choice.remove(c.id()))choice.add(c.id());else{boolean changed;do{changed=false;for(var dependent:cs)if(choice.contains(dependent.id())&&dependent.dependencies().stream().anyMatch(id->!choice.contains(id))){choice.remove(dependent.id());changed=true;}}while(changed);}try{selected=new Selection(release.manifest()).resolve(choice);status="";rebuildWidgets();}catch(Exception e){status=Errors.message(e);}}).bounds(width/2-150,35+(i-firstRow)*24,300,20).build();boolean allowed=!new Selection(release.manifest()).resolve(Set.of()).contains(c.id());button.active=allowed&&!busy;if(!allowed)button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(PackSummary.lockedReason(release.manifest(),c.id()))));enabled.put(button,allowed);addRenderableWidget(button);}
        apply=addRenderableWidget(Button.builder(action(),b->plan()).bounds(width/2-150,height-54,300,20).build());
        back=addRenderableWidget(Button.builder(Client.tr(busy?"cancel":"back"),b->onClose()).bounds(width/2-100,height-28,200,20).build());
    }
    @Override public void tick(){super.tick();enabled.forEach((button,allowed)->button.active=allowed&&!busy);apply.active=!busy&&Client.pending.isEmpty();back.setMessage(Client.tr(busy?"cancel":"back"));}
    private void plan(){
        if(busy)return;
        cancel=new AtomicBoolean();var request=cancel;busy=true;status=Client.tr("planning").getString();
        operations.review(release,selected,request).whenCompleteAsync((review,failure)->{
            busy=false;
            if(request.get()||minecraft.screen!=this)return;
            if(failure!=null){showFailure(failure);return;}
            if(review.observed().keySet().stream().anyMatch(Planner::configurable)){
                minecraft.setScreen(new ConfigChoiceScreen(this,operations,review,choices->{
                    Screen chooser=minecraft.screen;operations.review(release,selected,choices,request).whenCompleteAsync((resolved,error)->{
                        if(request.get()||minecraft.screen!=chooser)return;
                        if(error!=null){minecraft.setScreen(this);showFailure(error);return;}
                        showReview(resolved);
                    },minecraft);
                }));
            }else showReview(review);
        },minecraft);
    }
    private void showReview(PackOperations.Review review){
        var plan=review.plan();long added=plan.changes().stream().filter(c->c.before()==null).count(),removed=plan.changes().stream().filter(c->c.after()==null).count();long updated=plan.changes().size()-added-removed;
        StringBuilder summary=new StringBuilder(release.manifest().name()+" · "+release.manifest().version()+"\n\n");
        var changes=PackSummary.compare(Client.hub.active(),release.manifest());
        appendChanges(summary,"Добавлены",changes.added());appendChanges(summary,"Обновлены",changes.updated());appendChanges(summary,"Удалены",changes.removed());appendChanges(summary,"Теперь обязательны",changes.required());
        summary.append(Client.tr("review.counts",added,updated,removed).getString()).append("\n").append(Client.tr("review.download",String.format(java.util.Locale.ROOT,"%.1f",plan.downloadBytes()/1048576.0)).getString());
        boolean mods=plan.changes().stream().anyMatch(c->c.path().startsWith("mods/"));
        summary.append("\n\n").append(Client.tr(plan.changes().isEmpty()?"review.live":mods?"review.restart.mods":"review.restart.files").getString());
        if(!review.kept().isEmpty())summary.append("\n\n").append(Client.tr("config.keep").getString()).append("\n").append(String.join("\n",review.kept()));
        if(!review.replacements().isEmpty())summary.append("\n\n").append(Client.tr("config.backup").getString());
        summary.append("\n\n");for(var change:plan.changes())summary.append(change.after()==null?"− ":change.before()==null?"+ ":"↻ ").append(change.path()).append("\n");
        status="";minecraft.setScreen(new ReviewScreen(this,Client.tr("install.review"),summary.toString(),action(),()->{minecraft.setScreen(this);install(review);}));
    }
    private static void appendChanges(StringBuilder text,String title,List<String> names){if(!names.isEmpty())text.append(title).append(": ").append(String.join(", ",names)).append("\n\n");}
    private void install(PackOperations.Review review){
        if(busy)return;
        cancel=new AtomicBoolean();var request=cancel;busy=true;status=Client.tr("planning").getString();
        operations.install(review,request,message->minecraft.execute(()->{if(cancel==request)status=Errors.progress(message);})).whenCompleteAsync((id,failure)->{
            busy=false;
            if(failure!=null){if(!request.get()&&minecraft.screen==this)showFailure(failure);return;}
            Client.pending=id;Client.syncServers();
            if(minecraft.screen!=this)return;
            if(id.isEmpty()){
                try{var target=Client.hub.consumePendingConnection();minecraft.setScreen(target==null?new TextScreen(parent,Client.tr("done"),Client.tr("applied.live").getString()):new ConnectionCountdown(parent,target));}
                catch(Exception ex){showFailure(ex);}
            }
            else minecraft.setScreen(new RestartScreen(parent,id));
        },minecraft);
    }
    private void showFailure(Throwable failure){
        while(failure instanceof java.util.concurrent.CompletionException&&failure.getCause()!=null)failure=failure.getCause();
        status=failure instanceof Exception exception?Errors.message(exception):failure.toString();
        Client.error=status;
        minecraft.setScreen(new ReviewScreen(this,Client.tr("install.failed"),status,Client.tr("retry"),()->{minecraft.setScreen(this);plan();}));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);g.drawCenteredString(font,title,width/2,12,0xE2BE75);Ui.status(g,font,status,Math.max(10,width/2-150),height-84,Math.min(300,width-20),height-60);}
    @Override public void onClose(){cancel.set(true);minecraft.setScreen(parent);}
}
