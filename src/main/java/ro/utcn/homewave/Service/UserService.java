package ro.utcn.homewave.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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
    public String generateAddUserCode(String houseid){
        return userDao.generateAddUserCode(houseid);
    }
    public String addUserWithCode(String uuid, String code){
        return userDao.addUserWithCode(uuid, code);
    }
    public String getUsers(String houseid){
        return userDao.getUsers(houseid);
    }
    public String uploadProfilePicture(MultipartFile file, String uuid){
        return userDao.uploadProfilePicture(file,uuid);
    }
    public String sendEmailVerificationCode(String email){
        return userDao.sendEmailVerificationCode(email);
    }
    public String verfiyEmail(String uuid,String code){
        return userDao.verfiyEmail(uuid,code);
    }
}
