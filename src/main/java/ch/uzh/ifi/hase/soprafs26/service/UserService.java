package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.util.PasswordUtil;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    public UserService(@Qualifier("userRepository") UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return this.userRepository.findAll();
    }

    public User createUser(User newUser) {
        checkIfUsernameExists(newUser.getUsername());

        String salt = PasswordUtil.generateSalt();
        String hashedPassword = PasswordUtil.hash(newUser.getPassword(), salt);
        newUser.setSalt(salt);
        newUser.setPassword(hashedPassword);

        newUser.setToken(UUID.randomUUID().toString());
        newUser.setStatus(UserStatus.ONLINE);
        newUser.setCreationDate(Instant.now());

        if (newUser.getBio() == null) {
            newUser.setBio("");
        }

        if (newUser.getTotalGamesPlayed() == null) {
            newUser.setTotalGamesPlayed(0);
        }

        if (newUser.getTotalWins() == null) {
            newUser.setTotalWins(0);
        }

        if (newUser.getTotalPoints() == null) {
            newUser.setTotalPoints(0);
        }

        newUser = userRepository.save(newUser);
        userRepository.flush();

        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found"));
    }

    private void checkIfUsernameExists(String username) {
        User existingUser = userRepository.findByUsername(username);

        if (existingUser != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The username provided is not unique. Therefore, the user could not be created!");
        }
    }
    public void updateUserProfile(String token, Long userId, String username, String bio) {
        User requester = userRepository.findByToken(token);

        if (requester == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token.");
        }

        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found."));

        if (!requester.getId().equals(userToUpdate.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to edit this profile.");
        }

        if (username != null && !username.isBlank() && !username.equals(userToUpdate.getUsername())) {
            User existingUser = userRepository.findByUsername(username);
            if (existingUser != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The username provided is not unique.");
            }
            userToUpdate.setUsername(username);
        }

        if (bio != null) {
            userToUpdate.setBio(bio);
        }

        userRepository.save(userToUpdate);
        userRepository.flush();
    }
    public List<User> getFriends(Long userId) {
        User user = getUserById(userId);
        return new ArrayList<>(user.getFriends());
    }

    public void removeFriend(Long userId, Long friendId) {
        User user = getUserById(userId);
        User friend = getUserById(friendId);

        if (!user.getFriends().contains(friend)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Friendship not found.");
        }
        user.getFriends().remove(friend);
        friend.getFriends().remove(user);
        userRepository.save(user);
        userRepository.save(friend);
    }

}