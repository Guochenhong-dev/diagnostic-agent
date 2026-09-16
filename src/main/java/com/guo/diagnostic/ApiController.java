package com.guo.diagnostic;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import java.util.*;
import static com.guo.diagnostic.Domain.*;
@RestController @RequestMapping("/api")
public class ApiController {
 private final FilesService files;private final RunService service;private final RunStore store;private final KnowledgeService knowledge;private final DockerRunner runner;private final boolean ai;
 public ApiController(FilesService f,RunService s,RunStore r,KnowledgeService k,DockerRunner d,@Value("${app.ai.enabled}")boolean ai){files=f;service=s;store=r;knowledge=k;runner=d;this.ai=ai;}
 @GetMapping("/config") public Object config(){return Map.of("aiEnabled",ai,"runnerEnabled",runner.enabled());}
 @GetMapping("/projects") public Object projects()throws Exception{return files.projects();}
 @GetMapping("/source") public Object source(@RequestParam String project,@RequestParam String path)throws Exception{return Map.of("content",files.read(project,path));}
 @GetMapping("/runs") public Object list(){return store.list();}
 @GetMapping("/runs/{id}") public Run get(@PathVariable String id){return store.get(id);}
 @PostMapping("/runs") @ResponseStatus(HttpStatus.ACCEPTED) public Object create(@Valid @RequestBody Request request)throws Exception{return Map.of("id",service.create(request));}
 @PostMapping("/runs/{id}/verify") @ResponseStatus(HttpStatus.ACCEPTED) public Object verify(@PathVariable String id,@RequestBody VerifyRequest request)throws Exception{service.verify(id,request.confirmed());return Map.of("id",id);}
 @PostMapping("/knowledge/index") public Object index()throws Exception{return Map.of("indexed",knowledge.index());}
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}
 @ExceptionHandler(java.util.concurrent.RejectedExecutionException.class) public ResponseEntity<?> busy(){return ResponseEntity.status(429).body(Map.of("error","任务队列已满"));}
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("error","请求字段或JSON格式不正确"));}
 @ExceptionHandler(Exception.class) public ResponseEntity<?> failure(Exception e){return ResponseEntity.status(500).body(Map.of("error","操作失败，请检查本地配置、项目文件和服务依赖"));}
}
