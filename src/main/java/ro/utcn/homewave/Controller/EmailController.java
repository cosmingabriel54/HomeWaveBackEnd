package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.utcn.homewave.Service.EmailService;

@RequestMapping({"/email"})
@RestController
@Api(
        tags = {"Email"}
)
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173"})
public class EmailController {
    private final EmailService emailService;
    @Autowired
    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }
    @ApiOperation("SendEmail")
    @GetMapping("/sendemail")
    public ResponseEntity<String> sendEmail(String to, String subject, String text ){
        String response=emailService.sendEmail(to,subject,text);
        if(!response.contains("Failed")){
            return ResponseEntity.status(200).body(response);
        }else {
            return ResponseEntity.status(401).body(response);
        }
    }
}
