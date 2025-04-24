package ro.utcn.homewave.Dao;

public interface LoginDao {
    public String login(String username,String password);
    public String register(String username,String password,String gmail,String phonenumber);
    public String logout(String uuid);
    public String regenerateToken(String uuid);
    public String existingUsername(String username);
}
