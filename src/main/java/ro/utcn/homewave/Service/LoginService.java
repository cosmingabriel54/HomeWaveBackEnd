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
    public String register(String username,String password,String email,String phonenumber){
        return loginDao.register(username,password,email,phonenumber);
    }

    public String existingUsername(String username){
        return loginDao.existingUsername(username);
    }
}
