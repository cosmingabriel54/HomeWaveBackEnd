package ro.utcn.homewave.Controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.utcn.homewave.Entity.LoginRequest;
import ro.utcn.homewave.Entity.RegisterRequest;
import ro.utcn.homewave.Service.LoginService;

@Api(
        tags = {"Login"}
)
@RestController
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:5173"})
public class LoginController {
    public final LoginService loginService;

    @Autowired
    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }
    @ApiOperation("Login")
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest loginRequest) {
        System.out.println("testlogin");
        String usernameOrEmail = loginRequest.getUsername(); // Get username or email
        String password = loginRequest.getPassword();
        try {
            String response = loginService.login(usernameOrEmail, password); // Pass username or email
            if (response.contains("Eroare")) {
                return ResponseEntity.status(401).body(response); // Return error response
            } else {
                return ResponseEntity.ok(response); // Return successful response
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Eroare: " + e.getMessage()); // Handle server error
        }
    }

    @ApiOperation("Existing Username")
    @GetMapping("/existingusername")
    public ResponseEntity<String> existingUsername(@RequestParam String username) {
        try{
        String response=loginService.existingUsername(username);
        if(response.contains("Eroare")){
            return ResponseEntity.status(401).body(response);
        }
        else{
            return ResponseEntity.ok(response);
        }
        }catch (Exception e){
            return ResponseEntity.status(500).body("Eroare: "+e.getMessage());
        }
    }

    @ApiOperation("Logout")
    @PostMapping("/logout")
    public String logout(@RequestBody String uuid) {
        return loginService.logout(uuid);
    }
    @ApiOperation("Regenerate Token")
    @PutMapping("/regeneratetoken")
    public String regenerateToken(@RequestBody String uuid) {
        return loginService.regenerateToken(uuid);
    }
    @ApiOperation("Register")
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest registerRequest) {
        try {
            String response = loginService.register(
                    registerRequest.getUsername(),
                    registerRequest.getPassword(),
                    registerRequest.getEmail(),
                    registerRequest.getPhoneNumber());

            if (response.contains("Eroare")) {
                return ResponseEntity.status(401).body(response);
            } else {
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Eroare: " + e.getMessage());
        }
    }

}
