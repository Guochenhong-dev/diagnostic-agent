package com.guo.diagnostic;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static com.guo.diagnostic.Domain.*;
@Service
public class DockerRunner {
 private final boolean enabled;private final String image;private final int timeout;private final Path work;private final FilesService files;
 public DockerRunner(@Value("${app.runner.enabled}")boolean enabled,@Value("${app.runner.image}")String image,@Value("${app.runner.timeout}")int timeout,@Value("${app.work}")String work,FilesService f){this.enabled=enabled;this.image=image;this.timeout=timeout;this.work=Path.of(work).toAbsolutePath().normalize();files=f;}
 public boolean enabled(){return enabled;}
 public Verification verify(Run r)throws Exception {
  if(!enabled)throw new IllegalArgumentException("Docker验证未启用；诊断建议不等于测试通过");
  files.patchTarget(r.project,r.diagnosis.patch());
  Files.createDirectories(work);Path job=Files.createTempDirectory(work,r.id+"-");
  Path before=job.resolve("before"),after=job.resolve("after");copy(files.project(r.project),before);copy(files.project(r.project),after);
  Patch p=r.diagnosis.patch();Path target=files.safe(after,p.path());String source=Files.readString(target);
  if(source.indexOf(p.before())<0||source.indexOf(p.before())!=source.lastIndexOf(p.before()))throw new IllegalArgumentException("副本补丁锚点发生变化");
  Files.writeString(target,source.replace(p.before(),p.after()));
  TestResult b=run(before),a=run(after);
  boolean verified=!b.timeout()&&!a.timeout()&&b.tests()>0&&b.failures()+b.errors()>0&&a.exitCode()==0&&a.tests()==b.tests()&&a.failures()==0&&a.errors()==0;
  String diff="--- "+p.path()+"\n+++ "+p.path()+"\n- "+p.before()+"\n+ "+p.after();
  return new Verification(b,a,verified?"VERIFIED：原测试失败、修复后相同数量测试通过；仅代表当前测试覆盖范围":"NOT_VERIFIED：测试失败、超时或基线不满足；请检查测试输出",diff);
 }
 private void copy(Path base,Path dest)throws IOException {
  Files.createDirectories(dest);long bytes=0;int count=0;
  try(var walk=Files.walk(base,15)){
   for(Path f:walk.toList()){
    Path rel=base.relativize(f);if(rel.toString().isEmpty())continue;
    String first=rel.getName(0).toString();if(!first.equals("src")&&!rel.toString().equals("pom.xml"))continue;
    if(Files.isSymbolicLink(f))throw new IllegalArgumentException("项目包含符号链接");
    if(Files.isDirectory(f)){Files.createDirectories(dest.resolve(rel));continue;}
    bytes+=Files.size(f);if(++count>1000||bytes>10_000_000)throw new IllegalArgumentException("验证项目超过1000文件或10MB限制");
    Files.createDirectories(dest.resolve(rel).getParent());Files.copy(f,dest.resolve(rel));
   }
  }
 }
 private TestResult run(Path project)throws Exception {
  String name="diag-"+UUID.randomUUID();Path output=project.resolve("runner-output.log");
  var cmd=List.of("docker","run","--rm","--name",name,"--network","none","--memory","512m","--cpus","1","--pids-limit","128","--read-only","--cap-drop","ALL","--security-opt","no-new-privileges","--tmpfs","/tmp:rw,nosuid,size=128m","--mount","type=bind,source="+project+",target=/work","-w","/work",image,"mvn","-o","-B","-ntp","-Dmaven.repo.local=/opt/m2","test");
  Process proc=new ProcessBuilder(cmd).redirectErrorStream(true).start();
  // Continue draining after the retained 512KB limit, so full pipes never deadlock Maven.
  Thread drain=new Thread(()->{try(var in=proc.getInputStream();var out=Files.newOutputStream(output)){byte[] buf=new byte[4096];int n,total=0;while((n=in.read(buf))!=-1){int keep=Math.min(n,Math.max(0,512000-total));if(keep>0)out.write(buf,0,keep);total+=keep;}}catch(IOException ignored){}});drain.setDaemon(true);drain.start();
  boolean done=proc.waitFor(timeout,TimeUnit.SECONDS);
  if(!done){Process kill=new ProcessBuilder("docker","rm","-f",name).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();kill.waitFor(10,TimeUnit.SECONDS);proc.destroyForcibly();}
  drain.join(3000);String text=Files.exists(output)?FilesService.redact(Files.readString(output)):"";
  int[] counts=counts(project.resolve("target/surefire-reports"));
  return new TestResult(done?proc.exitValue():-1,!done,counts[0],counts[1],counts[2],text.length()>12000?text.substring(text.length()-12000):text);
 }
 static int[] counts(Path dir)throws Exception {
  int[] n={0,0,0};if(!Files.isDirectory(dir))return n;
  var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setExpandEntityReferences(false);
  try(var s=Files.list(dir)){for(Path p:s.filter(f->f.getFileName().toString().startsWith("TEST-")&&f.toString().endsWith(".xml")).toList()){
   var e=factory.newDocumentBuilder().parse(p.toFile()).getDocumentElement();n[0]+=Integer.parseInt(e.getAttribute("tests"));n[1]+=Integer.parseInt(e.getAttribute("failures"));n[2]+=Integer.parseInt(e.getAttribute("errors"));
  }}return n;
 }
}
