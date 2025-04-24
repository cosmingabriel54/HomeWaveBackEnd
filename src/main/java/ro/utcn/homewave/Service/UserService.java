package ro.utcn.homewave.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.UserDao;

@Service
public class UserService {
    public final UserDao userDao;

    @Autowired
    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }
    public String getUserInfo(String uuid){
        return userDao.userInfo(uuid);
    }
    public String passwordResetEmail(String email){
        return userDao.passwordResetEmail(email);
    }
    public String verifyCode(String code,String uuid){
        return userDao.verifyCode(code,uuid);
    }
    public String resetPassword(String code,String newPassword){
        return userDao.resetPassword(code, newPassword);
    }
    public String changePassword(String uuid,String oldPassword,String newPassword){
        return userDao.changePassword(uuid,newPassword,oldPassword);
    }
    public String changeEmail(String uuid,String email){
        return userDao.changeEmail(uuid,email);
    }
}
