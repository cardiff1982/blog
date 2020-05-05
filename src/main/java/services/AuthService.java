package services;

import lombok.Data;
import model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import repositories.UserRepository;
import response.AuthResponseBody;
import response.UserBody;

import javax.persistence.EntityManager;
import javax.persistence.MapKey;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Component
@Data
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private HashMap<String, Integer> sessions = new HashMap<>();

    public AuthResponseBody logonUser(String email, String password) {
        User currentUser = userRepository.findByEmail(email);
        if (currentUser == null || !currentUser.getPassword().equals(password)) {
            return AuthResponseBody.builder().result(false).build();
        } else {
            HttpSession currentUserSession = getSession();
            sessions.put(currentUserSession.toString(), currentUser.getId());
            return generateSuccessResponse(currentUser);
        }
    }

    public AuthResponseBody checkAuth() {
        HttpSession currentUserSession = getSession();
        String key = currentUserSession.toString();
        Integer userId;
        if (sessions.containsKey(key)) {
            userId = sessions.get(key);
            Query currentUserQuery = entityManager.createQuery("" +
                    "from User u where u.id = :id", User.class).setParameter("id", userId);
            User curruntUser = (User) currentUserQuery.getResultList().get(0);
            return generateSuccessResponse(curruntUser);
        } else {
            return AuthResponseBody.builder().result(false).build();
        }

    }

    public AuthResponseBody logout() {
        HttpSession session = getSession();
        String key = session.toString();
        if (sessions.containsKey(key)) {
            sessions.remove(key);
        }
        return AuthResponseBody.builder().result(true).build();

    }

    private AuthResponseBody generateSuccessResponse(User user) {
        boolean moderator = false;
        boolean settings = false;
        long moderationsCount = 0;
        if (user.getIsModerator() == 1) {
            moderator = true;
            settings = true;
            Query query = entityManager.createQuery("select count(*) from Post p where p.moderationStatus = 'NEW' " +
                    "and p.moderatorId = :moderator").setParameter("moderator", user.getId());
            moderationsCount = (long) query.getSingleResult();
        }
        UserBody userBody = UserBody.builder().id(user.getId()).name(user.getName()).photo(user.getPhoto())
                .email(user.getEmail()).moderation(moderator).moderationCount(moderationsCount).settings(settings).build();
        AuthResponseBody authResponseBody =  AuthResponseBody.builder().result(true).user(userBody).build();
        return authResponseBody;
    }

    private HttpSession getSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attr.getRequest().getSession(true); // true == allow create
    }



}
