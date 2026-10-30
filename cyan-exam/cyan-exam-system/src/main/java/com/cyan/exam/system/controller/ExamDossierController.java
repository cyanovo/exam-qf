package com.cyan.exam.system.controller;
import com.cyan.exam.common.core.domain.AjaxResult;
import com.cyan.exam.system.service.ExamDossierService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/exam/dossier")
public class ExamDossierController {
    private final ExamDossierService service;
    public ExamDossierController(ExamDossierService service) {this.service=service;}
    @GetMapping("/{examId}")
    public AjaxResult detail(@PathVariable Long examId) {return AjaxResult.success(service.detail(examId));}
    @PostMapping("/{examId}/incidents")
    public AjaxResult create(@PathVariable Long examId,@RequestBody ExamDossierService.IncidentInput body) {return AjaxResult.success(service.create(examId,body));}
    @PostMapping("/{examId}/incidents/{id}/{action}")
    public AjaxResult action(@PathVariable Long examId,@PathVariable Long id,@PathVariable String action,@RequestBody ExamDossierService.Action body) {
        service.action(examId,id,action,body);return AjaxResult.success();
    }
    @PostMapping(value="/{examId}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult upload(@PathVariable Long examId,@RequestParam Long userId,@RequestParam(required=false) Long taskId,
            @RequestParam(required=false) Long incidentId,@RequestParam String purpose,@RequestParam MultipartFile file) throws Exception {
        return AjaxResult.success(service.upload(examId,new ExamDossierService.Upload(userId,taskId,incidentId,purpose),file));
    }
    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws Exception {
        var f=service.download(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.name(),StandardCharsets.UTF_8).build().toString()).body(f.content());
    }
    @DeleteMapping("/files/{id}")
    public AjaxResult purge(@PathVariable Long id) throws Exception {service.purge(id);return AjaxResult.success();}
}
