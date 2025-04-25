package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.utcn.homewave.Service.EmailService;
import ro.utcn.homewave.Service.UserService;

import java.util.Map;
@Api(
        tags = {"User"}
)
@RestController
public class UserController {
    private final UserService userService;
    private final EmailService emailService;

    @Autowired
    public UserController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }
    @PostMapping("/userinfo")
    @ApiOperation("User Info")
    public ResponseEntity<String> getUserInfo(@RequestBody String uuid){
        String response=userService.getUserInfo(uuid);
        if(response==null || response.isEmpty()){
            return ResponseEntity.status(401).body("Eroare user info");
        }
        else{
            return ResponseEntity.status(200).body(response);
        }
    }
    @PostMapping("/passwordResetEmail")
    @ApiOperation("Send Password Email")
    public ResponseEntity<String> passwordResetEmail(@RequestBody String email){
        String response=userService.passwordResetEmail(email);
        System.out.println(response);
        if(response.contains("Eroare")){
            return ResponseEntity.status(401).body(response);
        }
        else{
            return ResponseEntity.status(200).body(response);
        }
    }
    @PostMapping("/verifycode")
    @ApiOperation("Verify code")
    public ResponseEntity<String> verifyCode(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        String uuid = payload.get("uuid");
        String response=userService.verifyCode(code,uuid);
        if(response.contains("Cod incorect")){
            return ResponseEntity.status(401).body("Error on backend");
        }
        else if(response.contains("Error")){
            return ResponseEntity.status(500).body(response);
        }
        else{
            return ResponseEntity.status(200).body(response);
        }
    }
    @PostMapping("/resetPassword")
    @ApiOperation("Reset Password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> payload){
        String code = payload.get("code");
        String newPassword = payload.get("newPassword");
        String response=userService.resetPassword(code,newPassword);
        if(response.contains("Cod incorect")){
            return ResponseEntity.status(401).body("Error on backend");
        }
        else if(response.contains("Error")){
            return ResponseEntity.status(500).body(response);
        }
        else{
            return ResponseEntity.status(200).body(response);
        }
    }
    @PostMapping("/changePassword")
    @ApiOperation("Change password")
    public ResponseEntity<String> changePassword(@RequestBody Map<String, String> payload){
        String uuid = payload.get("uuid");
        String newPassword = payload.get("newPassword");
        String oldPassword = payload.get("oldPassword");
        String response=userService.changePassword(uuid,newPassword,oldPassword);
        if(response.contains("Parola incorect")){
            return ResponseEntity.status(401).body("Error on backend");
        }
        else if(response.contains("Error")){
            return ResponseEntity.status(500).body(response);
        }
        else{
            return ResponseEntity.status(200).body(response);
        }
    }
    @PostMapping("/changeEmail")
    @ApiOperation("Change Email")
    public ResponseEntity<String> changeEmail(@RequestBody Map<String, String> payload){
        String uuid = payload.get("uuid");
        String email = payload.get("email");
        String response=userService.changeEmail(uuid,email);
        if(response.contains("Cod incorect")){
            return ResponseEntity.status(401).body("Error on backend");
        }
        else if(response.contains("Error")){
            return ResponseEntity.status(500).body(response);
        }
        else{
            return ResponseEntity.status(200).body(response);
        }
    }
}
