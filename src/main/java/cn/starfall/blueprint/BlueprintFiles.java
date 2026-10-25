package cn.starfall.blueprint;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** Resolve portable filenames against one consistently normalized instance directory. */
public final class BlueprintFiles {
    public static final int MAX_LISTED=256;
    public record Entry(String filename,String title,int blocks,String error){public boolean valid(){return error.isEmpty();}}
    private BlueprintFiles(){}
    public static Path directory(Path gameDirectory){return gameDirectory.toAbsolutePath().normalize().resolve("blueprints");}
    public static Path resolve(Path directory,String filename){
        String name=filename.trim();
        if(name.isEmpty()||name.contains("/")||name.contains("\\")||name.contains(":")||name.equals(".")||name.equals(".."))throw new IllegalArgumentException("请填写文件名，不要填写路径");
        if(!name.endsWith(".sfbp.json"))name+=".sfbp.json";
        Path root=directory.toAbsolutePath().normalize();Path file=root.resolve(name).normalize();
        if(!root.equals(file.getParent())||Files.isSymbolicLink(file))throw new IllegalArgumentException("无效的蓝图文件路径");
        return file;
    }
    public static ConstructionPlan read(Path directory,String filename)throws IOException{
        Path file=resolve(directory,filename);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("蓝图文件不存在或不是普通文件");
        int limit=ConstructionPlan.MAX_TEXT*4;
        try(var stream=Files.newInputStream(file)){
            byte[] bytes=stream.readNBytes(limit+1);
            if(bytes.length>limit)throw new IllegalArgumentException("蓝图文件过大");
            return ConstructionPlan.parse(new String(bytes,StandardCharsets.UTF_8));
        }
    }
    /** Scan only direct files; a broken design remains visible without hiding valid ones. */
    public static List<Entry> scan(Path directory)throws IOException{
        Path root=directory.toAbsolutePath().normalize();Files.createDirectories(root);
        List<String> names;
        try(var files=Files.list(root)){
            names=files.filter(p->p.getFileName().toString().endsWith(".sfbp.json"))
                    .filter(p->!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))
                    .map(p->p.getFileName().toString()).sorted(Comparator.comparing((String s)->s.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()))
                    .limit(MAX_LISTED).toList();
        }
        List<Entry> result=new ArrayList<>();
        for(String name:names){
            try{var plan=read(root,name);result.add(new Entry(name,plan.title(),plan.blocks().size(),""));}
            catch(Exception error){result.add(new Entry(name,name,0,error.getMessage()==null?"无法读取文件":error.getMessage()));}
        }
        return List.copyOf(result);
    }
}
