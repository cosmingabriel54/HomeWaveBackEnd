package ro.utcn.homewave.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.LoginDao;

@Service
public class LoginService {
    public final LoginDao loginDao;

    @Autowired
    public LoginService(LoginDao loginDao) {
        this.loginDao = loginDao;
    }
    public String login(String UsernameorPassword,String password){
        return loginDao.login(UsernameorPassword,password);
    }
    public String logout(String uuid){
        return loginDao.logout(uuid);
    }
    public String regenerateToken(String uuid){
        return loginDao.regenerateToken(uuid);
    }
    public String twofacodeEmail(String email){
        return loginDao.twofacodeEmail(email);
    }
    public String verifyCode(String code,String uuid){
        return loginDao.verifyCode(code,uuid);
    }
    public String register(String username,String password,String email){
        return loginDao.register(username,password,email);
    }

    public String existingUsername(String username){
        return loginDao.existingUsername(username);
    }
}
