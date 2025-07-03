package ro.utcn.homewave.Dao;

import org.springframework.web.multipart.MultipartFile;

public interface UserDao {
    String userInfo(String uuid);
    String passwordResetEmail(String email);
    String verifyCode(String code,String uuid);
    String resetPassword(String uuid,String newPassword);
    String changePassword(String uuid,String newPassword,String oldPassword);
    String changeEmail(String uuid,String email);
    String generateAddUserCode(String houseid);
    String addUserWithCode(String uuid,String code);
    String getUsers(String houseid);
    String uploadProfilePicture(MultipartFile file, String uuid);
    String sendEmailVerificationCode(String email);
    String verfiyEmail(String uuid,String code);
}
