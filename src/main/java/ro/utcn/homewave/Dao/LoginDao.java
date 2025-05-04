package ro.utcn.homewave.Dao;

public interface LoginDao {
    String login(String username,String password);
    String register(String username,String password,String gmail);
    String twofacodeEmail(String email);
    String logout(String uuid);
    String regenerateToken(String uuid);
    String existingUsername(String username);
    String verifyCode(String code,String uuid);
}
