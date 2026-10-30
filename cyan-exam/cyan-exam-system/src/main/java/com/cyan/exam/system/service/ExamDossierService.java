package com.cyan.exam.system.service;

import com.cyan.exam.common.config.RuoYiConfig;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.system.domain.CyanExamInfo;
import com.cyan.exam.system.mapper.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.io.*;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExamDossierService {
    private final ExamDossierMapper mapper;
    private final CyanExamInfoMapper exams;
    private final ExamStudentMapper students;
    private final OnsiteMapper onsite;
    private final ExamResultMapper results;
    private final ExamManagementMapper audit;
    private final ExamSessionMapper sessions;
    @Value("${exam.files.max-bytes:5242880}") private long maxBytes;
    @Value("${exam.files.max-count:20}") private int maxCount;
    public ExamDossierService(ExamDossierMapper mapper,CyanExamInfoMapper exams,ExamStudentMapper students,
            OnsiteMapper onsite,ExamResultMapper results,ExamManagementMapper audit,ExamSessionMapper sessions) {
        this.mapper=mapper;this.exams=exams;this.students=students;this.onsite=onsite;this.results=results;this.audit=audit;
        this.sessions=sessions;
    }
    private Long actor() { return SecurityUtils.getUserId(); }
    private boolean manager(CyanExamInfo e) {
        return SecurityUtils.isAdmin(actor()) || (actor().equals(e.getOwnerUserId()) && SecurityUtils.hasPermi("exam:exam:list"))
                || (results.isReviewer(e.getExamId(),actor())==1 && SecurityUtils.hasPermi("exam:results:review"));
    }
    private boolean judge(Long exam,Long task) {
        var e=exams.selectCyanExamInfoByExamId(exam);
        return e!=null && e.getPublishedAt()!=null && task!=null && onsite.tasks(exam).stream().anyMatch(t->task.equals(number(t,"id"))
                && (SecurityUtils.isAdmin(actor()) || onsite.canJudge(actor())) && !"DRAFT".equals(t.get("state")));
    }
    private CyanExamInfo exam(Long id,boolean write) {
        var e=write?exams.lockByExamId(id):exams.selectCyanExamInfoByExamId(id);
        if(e==null) throw new ServiceException("考试不存在");
        if(write && "ARCHIVED".equals(e.getLifecycle())) throw new ServiceException("已归档，只允许查询");
        return e;
    }
    private boolean visible(CyanExamInfo e,Map<String,Object> row) {
        return manager(e) || (actor().equals(number(row,"userId")) && students.selectExamIdsByUserId(actor()).contains(e.getExamId()))
                || judge(e.getExamId(),number(row,"taskId"));
    }
    private void target(CyanExamInfo e,Long user,Long task) {
        if(!students.selectUserIdsByExamId(e.getExamId()).contains(user)) throw new ServiceException("考生不在本场名单");
        if(task!=null && onsite.tasks(e.getExamId()).stream().noneMatch(t->task.equals(number(t,"id"))))
            throw new ServiceException("任务不属于本考试");
        if(!manager(e) && !actor().equals(user) && !judge(e.getExamId(),task)) throw new ServiceException("无权访问该考生材料",403);
    }
    public Map<String,Object> detail(Long id) {
        var e=exam(id,false);
        boolean manage=manager(e);
        var tasks=onsite.tasks(id);
        boolean participant=students.selectExamIdsByUserId(actor()).contains(id);
        if(!manage && !participant && tasks.stream().noneMatch(t->judge(id,number(t,"id"))))
            throw new ServiceException("无权访问材料",403);
        var incidents=mapper.incidents(id).stream().filter(r->visible(e,r)).toList();
        for(var row:incidents) row.put("events",mapper.events(number(row,"id")));
        var files=mapper.files(id).stream().filter(r->visible(e,r)).map(r->{r.remove("storageKey");return r;}).toList();
        var taskOptions=tasks.stream().filter(t->manage || judge(id,number(t,"id")) || (participant && !"DRAFT".equals(t.get("state"))))
                .map(t->Map.of("id",t.get("id"),"title",t.get("title"))).toList();
        return Map.of("examName",e.getExamName(),"incidents",incidents,"files",files,"tasks",taskOptions,
                "roster",manage || !participant ? onsite.roster(id) : onsite.roster(id).stream().filter(r->actor().equals(number(r,"userId"))).toList(),
                "canProcess",manage,"canApprove",results.isReviewer(id,actor())==1 && SecurityUtils.hasPermi("exam:results:review"),
                "archived","ARCHIVED".equals(e.getLifecycle()),"limits",Map.of("bytes",maxBytes,"count",maxCount,"retentionDays",ExamPolicy.read(e).retentionDays()));
    }
    public record IncidentInput(Long userId,Long taskId,String category,String title,String description) {}
    @Transactional
    public Long create(Long id,IncidentInput input) {
        var e=exam(id,true);
        if(input==null) throw new ServiceException("参数不能为空");
        target(e,input.userId(),input.taskId());
        if(input.category()==null || !Set.of("INFRASTRUCTURE","DEVICE","GRADING","OTHER").contains(input.category()))
            throw new ServiceException("异常类型无效");
        Map<String,Object> row=new HashMap<>();
        row.put("examId",id);row.put("userId",input.userId());row.put("taskId",input.taskId());row.put("actor",actor());
        row.put("category",input.category());row.put("title",text(input.title(),150));row.put("description",text(input.description(),5000));
        mapper.insertIncident(row);
        Long incident=number(row,"id");
        mapper.event(incident,actor(),"CREATE",input.description(),"","OPEN");
        return incident;
    }
    public record Action(int revision,String opinion) {}
    @Transactional
    public void action(Long examId,Long id,String action,Action body) {
        var e=exam(examId,true);
        if(!manager(e)) throw new ServiceException("无权处理异常",403);
        var row=mapper.incident(id);
        if(row==null || !examId.equals(number(row,"examId"))) throw new ServiceException("异常记录不存在");
        String before=(String)row.get("state"), after;
        if(body==null || body.revision()!=((Number)row.get("revision")).intValue()) throw new ServiceException("工单已变化，请刷新");
        String opinion=text(body.opinion(),5000);
        switch(action) {
            case "process" -> { require(before,"OPEN");after="PROCESSING"; }
            case "resolve" -> { require(before,"PROCESSING");after="RESOLVED"; }
            case "approve", "reject" -> {
                require(before,"RESOLVED");
                var events=mapper.events(id);
                if(results.isReviewer(examId,actor())!=1 || !SecurityUtils.hasPermi("exam:results:review")
                        || actor().equals(number(row,"createdBy"))
                        || events.stream().anyMatch(v->"resolve".equals(v.get("action")) && actor().equals(number(v,"actorId"))))
                    throw new ServiceException("须由未提报、未处理该工单的绑定复核员审批",403);
                after="approve".equals(action)?"APPROVED":"PROCESSING";
            }
            case "reopen" -> { require(before,"APPROVED");after="OPEN"; }
            default -> throw new ServiceException("不支持的操作");
        }
        if(mapper.state(id,body.revision(),after)!=1) throw new ServiceException("工单已变化");
        mapper.event(id,actor(),action,opinion,before,after);
    }
    public record Upload(Long userId,Long taskId,Long incidentId,String purpose) {}
    @Transactional(rollbackFor=Exception.class)
    public Long upload(Long id,Upload input,MultipartFile file) throws Exception {
        if(input==null) throw new ServiceException("参数不能为空");
        var e=exam(id,true);target(e,input.userId(),input.taskId());
        if(input.purpose()==null || !Set.of("EVIDENCE","OUTPUT","RESOURCE").contains(input.purpose())) throw new ServiceException("材料类型无效");
        if("RESOURCE".equals(input.purpose()) && !manager(e)) throw new ServiceException("仅管理人员可上传资源",403);
        if("OUTPUT".equals(input.purpose()) && !manager(e)) {
            if(e.getPublishedAt()==null || !"RUNNING".equals(ExamPolicy.state(e))) throw new ServiceException("成果提交已截止");
            var session=sessions.lockByExamAndUser(id,input.userId());
            if(session==null || !"IN_PROGRESS".equals(session.getStatus()) || !new Date().before(ExamPolicy.deadline(e,session)))
                throw new ServiceException("请先进入考试，且须在个人作答时间内提交成果");
        }
        if(input.incidentId()!=null) {
            var incident=mapper.incident(input.incidentId());
            if(incident==null || !id.equals(number(incident,"examId")) || !input.userId().equals(number(incident,"userId"))
                    || !Objects.equals(input.taskId(),number(incident,"taskId")) || "APPROVED".equals(incident.get("state")))
                throw new ServiceException("证据工单归属不匹配或已审批");
        }
        long count=mapper.files(id).stream().filter(r->input.userId().equals(number(r,"userId"))
                && Objects.equals(input.taskId(),number(r,"taskId")) && ((Number)r.get("deleted")).intValue()==0).count();
        if(count>=maxCount || file==null || file.isEmpty() || file.getSize()>maxBytes) throw new ServiceException("文件数量或大小超限");
        String name=file.getOriginalFilename();
        if(name==null || name.length()>180 || name.contains("/") || name.contains("\\") || name.contains("..")
                || name.chars().anyMatch(c->c<32)) throw new ServiceException("文件名不安全");
        byte[] bytes=sanitize(name,file.getBytes());
        if(bytes.length>maxBytes) throw new ServiceException("处理后的文件过大");
        String key=id+"/"+(input.taskId()==null?"general":input.taskId())+"/"+input.userId()+"/"+UUID.randomUUID();
        Path path=path(key);Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) try {Files.deleteIfExists(path);} catch(IOException ignored) {} }
        });
        Map<String,Object> row=new HashMap<>();
        row.put("examId",id);row.put("taskId",input.taskId());row.put("userId",input.userId());row.put("incidentId",input.incidentId());
        row.put("actor",actor());row.put("purpose",input.purpose());row.put("name",name);row.put("storageKey",key);
        row.put("mediaType","application/octet-stream");row.put("size",bytes.length);
        row.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        row.put("retainUntil",new Date(Math.max(System.currentTimeMillis(),e.getEndTime().getTime())+ExamPolicy.read(e).retentionDays()*86400000L));
        mapper.insertFile(row);
        audit.event(id,actor(),"FILE_UPLOAD",name,String.valueOf(row.get("id")));
        return number(row,"id");
    }
    // Strict allowlist: no archives, executables, HTML, SVG, or active documents.
    // Images are decoded and re-encoded; text is served only as an attachment.
    public static byte[] sanitize(String name,byte[] bytes) throws IOException {
        int dot=name.lastIndexOf('.');
        String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        if(Set.of("png","jpg","jpeg").contains(ext)) {
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);
                if(!readers.hasNext()) throw new ServiceException("图片内容无效");
                var reader=readers.next();
                try {
                    reader.setInput(stream);
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(!(ext.equals("png")?format.equals("png"):format.equals("jpeg"))
                            || (long)reader.getWidth(0)*reader.getHeight(0)>16000000L)
                        throw new ServiceException("图片类型不匹配或分辨率过大");
                    var image=reader.read(0);var output=new ByteArrayOutputStream();
                    if(!ImageIO.write(image,format,output)) throw new ServiceException("图片无法转换");
                    return output.toByteArray();
                } finally {reader.dispose();}
            }
        }
        if(!Set.of("txt","csv","json","py").contains(ext)) throw new ServiceException("仅允许 PNG/JPEG/TXT/CSV/JSON/PY");
        String value;
        try {value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException ex) {throw new ServiceException("文本必须为UTF-8");}
        if(value.chars().anyMatch(c->c==0 || c<9 || (c>13 && c<32))) throw new ServiceException("文件包含二进制或控制字符");
        if(ext.equals("json")) {
            if(value.isBlank()) throw new ServiceException("JSON文件内容不能为空");
            try {if(new com.fasterxml.jackson.databind.ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value)==null) throw new IOException("empty JSON");}
            catch(Exception ex) {throw new ServiceException("JSON文件内容无效");}
        }
        return bytes;
    }
    public record Download(String name,byte[] content) {}
    public Download download(Long id) throws IOException {
        var row=mapper.file(id);
        if(row==null || ((Number)row.get("deleted")).intValue()!=0) throw new ServiceException("文件不存在");
        var e=exam(number(row,"examId"),false);
        if(!visible(e,row)) throw new ServiceException("无权下载文件",403);
        byte[] bytes=Files.readAllBytes(path((String)row.get("storageKey")));
        audit.event(e.getExamId(),actor(),"FILE_DOWNLOAD",String.valueOf(row.get("name")),String.valueOf(id));
        return new Download((String)row.get("name"),bytes);
    }
    @Transactional(rollbackFor=Exception.class)
    public void purge(Long id) throws IOException {
        if(!SecurityUtils.isAdmin(actor())) throw new ServiceException("仅管理员可清理到期材料",403);
        var row=mapper.file(id);
        if(row==null || ((Number)row.get("deleted")).intValue()!=0) throw new ServiceException("文件不存在");
        var e=exam(number(row,"examId"),false);
        exams.lockByExamId(e.getExamId());
        if(!"ARCHIVED".equals(e.getLifecycle()) || ((Date)row.get("retainUntil")).after(new Date()) || mapper.openIncidents(e.getExamId())>0)
            throw new ServiceException("仅归档、保留期届满且无未结工单的材料可以清理");
        mapper.deleteFile(id);
        audit.event(e.getExamId(),actor(),"FILE_EXPIRE","保留期届满",String.valueOf(id));
        // Commit the tombstone before deleting bytes; a failed physical deletion never loses audit metadata.
        Path file=path((String)row.get("storageKey"));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCommit() {try {Files.deleteIfExists(file);} catch(IOException ex) {
                org.slf4j.LoggerFactory.getLogger(ExamDossierService.class).warn("Expired file cleanup failed: {}",id);
            }}
        });
    }
    private Path path(String key) {
        Path root=Path.of(RuoYiConfig.getProfile()+"-exam-private").toAbsolutePath().normalize();
        Path target=root.resolve(key).normalize();
        if(!target.startsWith(root)) throw new ServiceException("文件路径无效");
        return target;
    }
    static Long number(Map<String,Object> row,String key) {return row.get(key)==null?null:((Number)row.get(key)).longValue();}
    private static String text(String value,int limit) {if(value==null||value.isBlank()||value.length()>limit) throw new ServiceException("必填内容为空或过长");return value.trim();}
    private static void require(String state,String expected) {if(!expected.equals(state)) throw new ServiceException("当前工单状态不允许该操作");}
}
