package ro.utcn.homewave.Dao;

public interface UserDao {
    public String userInfo(String uuid);
    public String passwordResetEmail(String uuid);
    String verifyCode(String code,String uuid);
    String resetPassword(String uuid,String newPassword);
    String changePassword(String uuid,String newPassword,String oldPassword);
    String changeEmail(String uuid,String email);
}
