package ro.utcn.homewave.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.utcn.homewave.Dao.EmailDao;

@Service
public class EmailService {
    private final EmailDao emailDao;

    @Autowired
    public EmailService( EmailDao emailDao) {
        this.emailDao = emailDao;
    }
    public String sendEmail(String to, String subject, String text){
        return emailDao.sendMessage(to,subject,text);
    }
}
