package ro.utcn.homewave.Dao;

public interface EmailDao {
    String sendMessage(String to, String subject, String text) throws RuntimeException;
}


