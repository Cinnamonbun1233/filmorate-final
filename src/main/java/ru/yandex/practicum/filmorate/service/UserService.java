package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.EmailLoginAlreadyUsed;
import ru.yandex.practicum.filmorate.exception.ResourceNotFoundException;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.*;

@Service
public class UserService {

    @Autowired
    UserStorage userStorage;

    // USERS
    public User addUser(User user) {

        // checking
        String email = user.getEmail();
        String login = user.getLogin();

        boolean emailAlreadyUsed = userStorage.emailAlreadyUsed(email);
        boolean loginAlreadyUsed = userStorage.loginAlreadyUsed(login);

        if (emailAlreadyUsed || loginAlreadyUsed) {
            List<String> warningList = new ArrayList<>();
            if (emailAlreadyUsed) {
                warningList.add("E-mail: " + email + " is already used");
            }
            if (loginAlreadyUsed) {
                warningList.add("Login: " + login + " is already used");
            }
            String warning = String.join(", ", warningList);
            throw new EmailLoginAlreadyUsed(warning);
        }

        // correction
        setNameAsLogin(user);

        // add
        User result = userStorage.addUser(user);
        assert result != null;
        return result;

    }

    public User updateUser(User user) {

        // checking
        Long id = user.getId();
        String email = user.getEmail();
        String login = user.getLogin();

        boolean idNotFound = (userStorage.getUser(id) == null);
        if (idNotFound) {
            throw new ResourceNotFoundException("User", id);
        }

        boolean emailAlreadyUsed = userStorage.emailAlreadyUsed(email, user.getId());
        boolean loginAlreadyUsed = userStorage.loginAlreadyUsed(login, user.getId());
        if (emailAlreadyUsed || loginAlreadyUsed) {
            List<String> warningList = new ArrayList<>();
            if (emailAlreadyUsed) {
                warningList.add("E-mail: " + email + " is already used");
            }
            if (loginAlreadyUsed) {
                warningList.add("Login: " + login + " is already used");
            }
            String warning = String.join(", ", warningList);
            throw new EmailLoginAlreadyUsed(warning);
        }

        // correction
        setNameAsLogin(user);

        // update
        User result = userStorage.updateUser(user);
        assert result != null;
        return result;

    }

    public User getUser(Long id) {

        // checking
        User user = userStorage.getUser(id);
        if (user == null) {
            throw new ResourceNotFoundException("User", id);
        }

        // return
        return user;

    }

    public TreeSet<User> getUsers() {

        return userStorage.getUsers();

    }


    // FRIENDS
    public void addConnection(Long userId, Long friendId) {

        // checking
        User user = userStorage.getUser(userId);
        User friend = userStorage.getUser(friendId);
        Map<String, Long> notFoundResources = new HashMap<>();
        if (user == null) {
            notFoundResources.put("User", userId);
        }
        if (friend == null) {
            notFoundResources.put("Friend", friendId);
        }
        if (notFoundResources.size() > 0) {
            throw new ResourceNotFoundException(notFoundResources);
        }

        // add connection
        userStorage.addFriend(userId, friendId);
        userStorage.addFriend(friendId, userId);

    }

    public TreeSet<User> getUsersFriends(Long id) {

        // checking
        User user = userStorage.getUser(id);
        if (user == null) {
            throw new ResourceNotFoundException("User", id);
        }

        // return user's friends
        return userStorage.getFriends(id);
    }

    public TreeSet<User> getCommonFriends(Long userId, Long otherUserId) {

        // checking
        User user = userStorage.getUser(userId);
        User otherUser = userStorage.getUser(otherUserId);
        Map<String, Long> notFoundResources = new HashMap<>();
        if (user == null) {
            notFoundResources.put("User", userId);
        }
        if (otherUser == null) {
            notFoundResources.put("User", otherUserId);
        }
        if (notFoundResources.size() > 0) {
            throw new ResourceNotFoundException(notFoundResources);
        }

        // return common friends
        TreeSet<User> firstUsersFriends = getUsersFriends(userId);
        TreeSet<User> secondUsersFriends = getUsersFriends(otherUserId);
        firstUsersFriends.retainAll(secondUsersFriends);
        return firstUsersFriends;

    }

    public void deleteConnection(Long userId, Long friendId) {

        // checking
        User user = userStorage.getUser(userId);
        User friend = userStorage.getUser(friendId);
        Map<String, Long> notFoundResources = new HashMap<>();
        if (user == null) {
            notFoundResources.put("User", userId);
        }
        if (friend == null) {
            notFoundResources.put("Friend", friendId);
        }
        if (notFoundResources.size() > 0) {
            throw new ResourceNotFoundException(notFoundResources);
        }

        boolean hasConnection = userStorage.hasConnection(userId, friendId);
        if (!hasConnection) {
            throw new ResourceNotFoundException("User " + friendId + " is not a friend of user " + userId);
        }

        // delete connection
        boolean connection1Deleted = userStorage.deleteFriend(userId, friendId);
        boolean connection2Deleted = userStorage.deleteFriend(friendId, userId);
        assert connection1Deleted;
        assert connection2Deleted;

    }


    // PRIVATE
    private void setNameAsLogin(User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(user.getLogin());
        }
    }

}