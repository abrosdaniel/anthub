package dev.abros.anthub.core;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
public final class Cache {
    private static final System.Logger LOG=System.getLogger(Cache.class.getName());
    private final Path root;private final Remote remote;
    public Cache(Path game,Remote remote)throws IOException{root=game.resolve("anthub/cache/objects");for(Path p=root;p!=null&&!p.equals(game);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symlink in cache path");Files.createDirectories(root);this.remote=remote;}
    public Path path(String hash){Hashes.check(hash);Path p=root.resolve(hash.substring(0,2)).resolve(hash);if(Files.isSymbolicLink(p)||Files.isSymbolicLink(p.getParent()))throw new IllegalArgumentException("Symlink in cache path");return p;}
    public boolean contains(String hash)throws IOException{Path p=path(hash);return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Hashes.sha256(p).equals(hash);}
    public Path obtain(Manifest.FileEntry f,AtomicBoolean cancel)throws IOException,InterruptedException{return obtain(f,cancel,index->{});}
    public Path obtain(Manifest.FileEntry f,AtomicBoolean cancel,IntConsumer sourceChanged)throws IOException,InterruptedException{
        return obtain(f,cancel,sourceChanged,null);
    }
    public Path obtain(Manifest.FileEntry f,AtomicBoolean cancel,IntConsumer sourceChanged,java.util.function.LongConsumer progress)throws IOException,InterruptedException{
        if(cancel.get())throw new IOException("Cancelled");
        Path p=path(f.sha256());if(contains(f.sha256()))return p;Files.createDirectories(p.getParent());
        IOException failures=new IOException("All download sources failed: "+f.path());
        for(int index=0;index<f.urls().size();index++){
            if(cancel.get())throw new IOException("Cancelled");
            // Source-specific partials permit safe resume without mixing different mirrors.
            String url=f.urls().get(index);
            Path tmp=p.resolveSibling(p.getFileName()+"."+Hashes.sha256(url.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".part");
            Path etag=tmp.resolveSibling(tmp.getFileName()+".etag");
            sourceChanged.accept(index);
            try{
                if(progress==null)remote.download(url,tmp,f.size(),cancel);else remote.download(url,tmp,f.size(),cancel,progress);
                if(cancel.get())throw new IOException("Cancelled");
                if(Files.size(tmp)!=f.size()||!Hashes.sha256(tmp).equals(f.sha256()))throw new IOException("HASH_MISMATCH: "+f.path());
                Json.move(tmp,p);return p;
            }catch(IOException ex){
                if(cancel.get())throw ex;
                failures.addSuppressed(ex);
                LOG.log(System.Logger.Level.WARNING,"Download source "+(index+1)+" failed for "+f.path()+" ("+Remote.https(url).getHost()+")",ex);
            }finally{
                Files.deleteIfExists(tmp);Files.deleteIfExists(etag);
            }
        }
        throw failures;
    }
}
