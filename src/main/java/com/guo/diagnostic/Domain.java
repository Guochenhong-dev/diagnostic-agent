package com.guo.diagnostic;
import java.util.*;
import jakarta.validation.constraints.*;
public final class Domain {
 private Domain() {}
 public record Request(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,50}") String project,
   @NotBlank @Size(max=16000) String log, boolean useAi) {}
 public record Evidence(String id,String path,int start,int end,String method,String code,String calls) {}
 public record Patch(String path,String before,String after) {}
 public record Diagnosis(String cause,List<String> evidenceIds,List<String> nextSteps,Patch patch) {}
 public record VerifyRequest(boolean confirmed) {}
 public record Trace(String step,long elapsedMs,String detail) {}
 public record TestResult(int exitCode,boolean timeout,int tests,int failures,int errors,String output) {}
 public record Verification(TestResult before,TestResult after,String conclusion,String diff) {}
 public static class Run {
  public String id,project,status,mode,log,error,createdAt;
  public long elapsedMs;
  public Integer totalTokens;
  public List<Evidence> evidence=new ArrayList<>();
  public List<Trace> trace=new ArrayList<>();
  public List<String> knowledge=new ArrayList<>();
  public Diagnosis diagnosis;
  public Verification verification;
 }
}
