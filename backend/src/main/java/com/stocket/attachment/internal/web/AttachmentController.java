package com.stocket.attachment.internal.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import com.stocket.attachment.AttachmentSummary;
import com.stocket.attachment.internal.domain.Attachment;
import com.stocket.attachment.internal.domain.AttachmentProblem;
import com.stocket.attachment.internal.domain.AttachmentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service){this.service=service;}
    @PostMapping public ResponseEntity<AttachmentSummary> upload(@RequestParam String ownerType,@RequestParam UUID ownerId,
            @RequestParam String purpose,@RequestPart MultipartFile file,@RequestHeader(value="X-Request-Id",required=false) String requestId) throws IOException {
        String id=requestId!=null&&requestId.matches("[A-Za-z0-9._-]{8,80}")?requestId:UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(ownerType,ownerId,purpose,file.getOriginalFilename(),file.getContentType(),file.getInputStream(),id));
    }
    @GetMapping public List<AttachmentSummary> list(@RequestParam String ownerType,@RequestParam UUID ownerId){return service.list(ownerType,ownerId);}
    @GetMapping("/{id}") public AttachmentSummary get(@PathVariable UUID id){return service.summary(id);}
    @GetMapping("/{id}/content") public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id,@RequestHeader(value="Range",required=false) String range) throws IOException {
        Attachment attachment=service.get(id); long size=attachment.getSizeBytes(); long start=0,end=size-1; HttpStatus status=HttpStatus.OK;
        HttpHeaders headers=new HttpHeaders();
        if(range!=null){
            if(!range.matches("bytes=\\d+-\\d*")) throw new AttachmentProblem(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,"ATTACHMENT_RANGE_INVALID");
            String[] p=range.substring(6).split("-",-1);
            try { start=Long.parseLong(p[0]); end=p[1].isBlank()?end:Math.min(end,Long.parseLong(p[1])); }
            catch(NumberFormatException error){throw new AttachmentProblem(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,"ATTACHMENT_RANGE_INVALID");}
            if(start<0||start>end||start>=size)throw new AttachmentProblem(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,"ATTACHMENT_RANGE_INVALID");
            status=HttpStatus.PARTIAL_CONTENT;headers.set("Content-Range","bytes "+start+"-"+end+"/"+size);
        }
        InputStream input=service.content(attachment); long offset=start,length=end-start+1;
        StreamingResponseBody body=output->{try(input){input.skipNBytes(offset);byte[] buffer=new byte[8192];long remaining=length;while(remaining>0){int read=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(read<0)break;output.write(buffer,0,read);remaining-=read;}}};
        headers.setContentType(MediaType.parseMediaType(attachment.getDetectedMediaType()));headers.setContentLength(length);
        headers.set("X-Content-Type-Options","nosniff");headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.set(HttpHeaders.ACCEPT_RANGES,"bytes");
        String disposition=attachment.getDetectedMediaType().startsWith("image/")?"inline":"attachment";
        headers.set(HttpHeaders.CONTENT_DISPOSITION,disposition+"; filename*=UTF-8''"+URLEncoder.encode(attachment.getOriginalFilename(),StandardCharsets.UTF_8).replace("+","%20"));
        return new ResponseEntity<>(body,headers,status);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) throws IOException {service.delete(id);}
    @ExceptionHandler(AttachmentProblem.class) ProblemDetail problem(AttachmentProblem error){ProblemDetail p=ProblemDetail.forStatus(error.status());p.setTitle("Attachment error");p.setProperty("code",error.code());p.setProperty("retryable",false);return p;}
}
