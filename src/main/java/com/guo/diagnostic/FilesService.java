package com.guo.diagnostic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import static com.guo.diagnostic.Domain.*;
@Service
public class FilesService {
 private final Path root;
 public FilesService(@Value("${app.projects}") String root)throws IOException {
  this.root=Path.of(root).toAbsolutePath().normalize(); Files.createDirectories(this.root);
 }
 public Path project(String id)throws IOException {
  if(!id.matches("[a-zA-Z0-9_-]{1,50}"))throw new IllegalArgumentException("非法项目标识");
  Path p=root.resolve(id);
  if(!Files.isDirectory(p)||Files.isSymbolicLink(p)||!p.toRealPath().startsWith(root.toRealPath()))throw new IllegalArgumentException("项目不存在或目录不允许访问");
  return p.toRealPath();
 }
 public Path safe(Path base,String relative)throws IOException {
  if(relative==null||relative.contains("\\")||Path.of(relative).isAbsolute())throw new IllegalArgumentException("非法路径");
  Path p=base.resolve(relative).normalize();
  if(!p.startsWith(base)||!Files.isRegularFile(p)||!p.toRealPath().startsWith(base.toRealPath())||Files.isSymbolicLink(p))throw new IllegalArgumentException("拒绝目录越界或符号链接");
  if(Files.size(p)>256000)throw new IllegalArgumentException("文件超过256KB");
  return p;
 }
 public String read(String id,String path)throws IOException {
  if(!(path.endsWith(".java")||path.endsWith(".properties")||path.endsWith(".xml")))throw new IllegalArgumentException("只读Java/配置/XML文件");
  if(path.contains("target/")||path.contains(".env"))throw new IllegalArgumentException("禁止访问此路径");
  return redact(Files.readString(safe(project(id),path)));
 }
 public static String redact(String s) {
  return s.replaceAll("(?i)(password|token|api[-_]?key|secret)(\\s*[=:]\\s*)[^\\s,;]+","$1$2[REDACTED]")
   .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+","Bearer [REDACTED]");
 }
 public List<String> projects()throws IOException {
  try(var stream=Files.list(root)){return stream.filter(Files::isDirectory).filter(p->!Files.isSymbolicLink(p)).map(p->p.getFileName().toString()).sorted().toList();}
 }
 public List<Evidence> evidence(String id,String log)throws IOException {
  Path base=project(id); var frames=new HashMap<String,Set<Integer>>();
  Matcher m=Pattern.compile("\\(([^():/]+\\.java):(\\d+)\\)").matcher(log);
  while(m.find())frames.computeIfAbsent(m.group(1),k->new HashSet<>()).add(Integer.parseInt(m.group(2)));
  var out=new ArrayList<Evidence>(); var parser=new JavaParser();
  try(var stream=Files.walk(base.resolve("src/main"),12)) {
   var files=stream.filter(p->Files.isRegularFile(p)&&p.toString().endsWith(".java")).limit(201).toList();
   if(files.size()>200)throw new IllegalArgumentException("演示索引最多200个Java文件");
   for(Path file:files) {
    String rel=base.relativize(file).toString().replace('\\','/');
    String source=Files.readString(safe(base,rel));
    var result=parser.parse(source);
    if(result.getResult().isEmpty())continue;
    for(var method:result.getResult().get().findAll(MethodDeclaration.class)) {
     int start=method.getBegin().orElseThrow().line,end=method.getEnd().orElseThrow().line;
     boolean hit=frames.getOrDefault(file.getFileName().toString(),Set.of()).stream().anyMatch(n->n>=start&&n<=end);
     if(!hit && !log.contains(method.getNameAsString()) && !log.contains(file.getFileName().toString().replace(".java","")))continue;
     String code=redact(method.toString()); if(code.length()>4000)code=code.substring(0,4000);
     out.add(new Evidence("E"+(out.size()+1),rel,start,end,method.getNameAsString(),code,
       method.findAll(MethodCallExpr.class).stream().map(MethodCallExpr::getNameAsString).distinct().sorted().toList().toString()));
     if(out.size()>=12)return out;
    }
   }
  }
  return out;
 }
 public Path patchTarget(String id,Patch p)throws IOException {
  if(p==null||p.path()==null||!p.path().startsWith("src/main/java/")||!p.path().endsWith(".java"))throw new IllegalArgumentException("补丁只允许修改src/main/java下的Java文件");
  if(p.before()==null||p.before().isBlank()||p.after()==null||p.before().equals(p.after())||p.after().length()>8000)throw new IllegalArgumentException("补丁为空或超过限制");
  Path file=safe(project(id),p.path());String source=Files.readString(file);
  if(source.indexOf(p.before())<0||source.indexOf(p.before())!=source.lastIndexOf(p.before()))throw new IllegalArgumentException("补丁锚点必须唯一匹配，源码可能已变化");
  return file;
 }
}
