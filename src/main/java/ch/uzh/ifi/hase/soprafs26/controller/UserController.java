package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPutDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import ch.uzh.ifi.hase.soprafs26.service.FriendRequestService;


import java.util.ArrayList;
import java.util.List;

@RestController
public class UserController {

    private final UserService userService;
    private final FriendRequestService friendRequestService;

    UserController(UserService userService, FriendRequestService friendRequestService) {
        this.userService = userService;
        this.friendRequestService = friendRequestService;
    }

    @GetMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<UserGetDTO> getAllUsers() {
        List<User> users = userService.getUsers();
        List<UserGetDTO> userGetDTOs = new ArrayList<>();

        for (User user : users) {
            userGetDTOs.add(DTOMapper.INSTANCE.convertEntityToUserGetDTO(user));
        }
        return userGetDTOs;
    }

    @GetMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public UserGetDTO getUser(@PathVariable Long userId) {
        User user = userService.getUserById(userId);
        return DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public UserGetDTO createUser(@RequestBody UserPostDTO userPostDTO) {
        User userInput = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);
        User createdUser = userService.createUser(userInput);
        return DTOMapper.INSTANCE.convertEntityToUserGetDTO(createdUser);
    }

    @PutMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUser(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token,
            @RequestBody UserPutDTO userPutDTO) {

        userService.updateUserProfile(
                token,
                userId,
                userPutDTO.getUsername(),
                userPutDTO.getBio()
        );
    }

    @GetMapping("/users/{userId}/friends")
    @ResponseStatus(HttpStatus.OK)
    public List<UserGetDTO> getFriends(@PathVariable Long userId) {
        List<User> friends = userService.getFriends(userId);
        return friends.stream()
                .map(DTOMapper.INSTANCE::convertEntityToUserGetDTO)
                .toList(); }


    @DeleteMapping("/users/{userId}/friends/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(@PathVariable Long userId, @PathVariable Long friendId) {
        userService.removeFriend(userId, friendId);
    }


    @GetMapping("/users/{userId}/friend-requests")
    @ResponseStatus(HttpStatus.OK)
    public List<FriendRequestGetDTO> getFriendRequests(@PathVariable Long userId) {
        return friendRequestService.getIncomingPendingRequests(userId)
                .stream()
                .map(DTOMapper.INSTANCE::convertEntityToFriendRequestGetDTO)
                .toList();
    }
}