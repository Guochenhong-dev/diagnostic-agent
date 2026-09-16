package com.guo.diagnostic;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.guo.diagnostic.Domain.*;
@Service
public class RunService {
 private final RunStore store;private final FilesService files;private final DiagnosisEngine engine;private final DockerRunner runner;private final KnowledgeService knowledge;
 private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),new ThreadPoolExecutor.AbortPolicy());
 public RunService(RunStore s,FilesService f,DiagnosisEngine e,DockerRunner r,KnowledgeService k){store=s;files=f;engine=e;runner=r;knowledge=k;}
 public String create(Request req)throws Exception {
  files.project(req.project());Run r=new Run();r.id=UUID.randomUUID().toString();r.project=req.project();r.log=FilesService.redact(req.log());r.mode=req.useAi()?"LLM":"RULE_DEMO";r.status="QUEUED";r.createdAt=Instant.now().toString();store.save(r);
  submit(r,()->diagnose(r,req.useAi()));return r.id;
 }
 private void submit(Run r,Runnable job){try{pool.execute(job);}catch(RejectedExecutionException ex){r.status="FAILED";r.error="任务队列已满，请稍后重试";store.save(r);throw ex;}}
 private void diagnose(Run r,boolean useAi){long start=System.nanoTime();
  try{r.status="ANALYZING";store.save(r);
   r.evidence=files.evidence(r.project,r.log);trace(r,"SOURCE",start,"AST方法证据 "+r.evidence.size()+" 条");
   r.knowledge=knowledge.search(r.log);trace(r,"KNOWLEDGE",start,"知识参考 "+r.knowledge.size()+" 条");
   r.diagnosis=engine.analyze(r,useAi);
   if(r.diagnosis.patch()!=null)files.patchTarget(r.project,r.diagnosis.patch());
   trace(r,"DIAGNOSIS",start,r.mode+"诊断及补丁约束校验完成");r.status="DIAGNOSED";
  }catch(Exception ex){r.status="FAILED";r.error=FilesService.redact(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());}
  finally{r.elapsedMs=(System.nanoTime()-start)/1_000_000;store.save(r);}
 }
 private void trace(Run r,String step,long start,String text){r.trace.add(new Trace(step,(System.nanoTime()-start)/1_000_000,text));store.save(r);}
 public void verify(String id,boolean confirmed)throws Exception {
  if(!confirmed)throw new IllegalArgumentException("需要先确认补丁内容");Run r=store.get(id);
  if(!runner.enabled())throw new IllegalArgumentException("RUNNER_ENABLED=false；安装Docker并准备测试镜像后再启用");
  if(!"DIAGNOSED".equals(r.status)||r.diagnosis==null||r.diagnosis.patch()==null)throw new IllegalArgumentException("当前记录无可验证补丁");
  files.patchTarget(r.project,r.diagnosis.patch());if(!store.claim(id))throw new IllegalArgumentException("任务正在验证或已验证");
  r.status="VERIFYING";store.save(r);
  submit(r,()->{long start=System.nanoTime();try{r.verification=runner.verify(r);r.status="VERIFICATION_DONE";}catch(Exception e){r.status="VERIFY_FAILED";r.error=FilesService.redact(e.getMessage()==null?"验证执行失败":e.getMessage());}finally{trace(r,"VERIFY",start,"Docker验证结束");store.save(r);}});
 }
 @PreDestroy void close(){pool.shutdownNow();}
}
