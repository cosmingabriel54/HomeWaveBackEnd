package ro.utcn.homewave.Dao;

public interface UserDao {
    String userInfo(String uuid);
    String passwordResetEmail(String email);
    String verifyCode(String code,String uuid);
    String resetPassword(String uuid,String newPassword);
    String changePassword(String uuid,String newPassword,String oldPassword);
    String changeEmail(String uuid,String email);
}
