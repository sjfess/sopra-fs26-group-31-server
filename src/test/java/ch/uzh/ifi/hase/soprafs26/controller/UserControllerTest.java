package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPutDTO;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import ch.uzh.ifi.hase.soprafs26.service.FriendRequestService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.constant.FriendRequestStatus;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FriendRequestService friendRequestService;

    @Test
    public void givenUsers_whenGetUsers_thenReturnJsonArray() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("firstname@lastname");
        user.setToken("token-1");
        user.setStatus(UserStatus.OFFLINE);
        user.setBio("hello");
        user.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        user.setTotalGamesPlayed(3);
        user.setTotalWins(2);
        user.setTotalPoints(25);

        List<User> allUsers = Collections.singletonList(user);
        given(userService.getUsers()).willReturn(allUsers);

        MockHttpServletRequestBuilder getRequest =
                get("/users").contentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(getRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].username", is(user.getUsername())))
                .andExpect(jsonPath("$[0].token", is(user.getToken())))
                .andExpect(jsonPath("$[0].status", is(user.getStatus().toString())))
                .andExpect(jsonPath("$[0].bio", is(user.getBio())))
                .andExpect(jsonPath("$[0].totalGamesPlayed", is(user.getTotalGamesPlayed())))
                .andExpect(jsonPath("$[0].totalWins", is(user.getTotalWins())))
                .andExpect(jsonPath("$[0].totalPoints", is(user.getTotalPoints())));
    }

    @Test
    public void givenUserId_whenGetUser_thenReturnJsonObject() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testUsername");
        user.setToken("token-1");
        user.setStatus(UserStatus.ONLINE);
        user.setBio("bio");
        user.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        user.setTotalGamesPlayed(5);
        user.setTotalWins(4);
        user.setTotalPoints(40);

        given(userService.getUserById(1L)).willReturn(user);

        mockMvc.perform(get("/users/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is(user.getUsername())))
                .andExpect(jsonPath("$.token", is(user.getToken())))
                .andExpect(jsonPath("$.status", is(user.getStatus().toString())))
                .andExpect(jsonPath("$.bio", is(user.getBio())))
                .andExpect(jsonPath("$.totalGamesPlayed", is(user.getTotalGamesPlayed())))
                .andExpect(jsonPath("$.totalWins", is(user.getTotalWins())))
                .andExpect(jsonPath("$.totalPoints", is(user.getTotalPoints())));
    }

    @Test
    public void createUser_validInput_userCreated() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testUsername");
        user.setPassword("testPassword");
        user.setToken("1");
        user.setStatus(UserStatus.OFFLINE);
        user.setBio("");
        user.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        user.setTotalGamesPlayed(0);
        user.setTotalWins(0);
        user.setTotalPoints(0);

        UserPostDTO userPostDTO = new UserPostDTO();
        userPostDTO.setUsername("testUsername");
        userPostDTO.setPassword("testPassword");

        given(userService.createUser(Mockito.any())).willReturn(user);

        MockHttpServletRequestBuilder postRequest = post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(userPostDTO));

        mockMvc.perform(postRequest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is(user.getUsername())))
                .andExpect(jsonPath("$.token", is(user.getToken())))
                .andExpect(jsonPath("$.status", is(user.getStatus().toString())))
                .andExpect(jsonPath("$.bio", is(user.getBio())))
                .andExpect(jsonPath("$.totalGamesPlayed", is(0)))
                .andExpect(jsonPath("$.totalWins", is(0)))
                .andExpect(jsonPath("$.totalPoints", is(0)));
    }

    @Test
    public void updateUser_validInput_returnsNoContent() throws Exception {
        UserPutDTO userPutDTO = new UserPutDTO();
        userPutDTO.setUsername("newUsername");
        userPutDTO.setBio("new bio");

        doNothing().when(userService).updateUserProfile("valid-token", 1L, "newUsername", "new bio");

        mockMvc.perform(put("/users/1")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userPutDTO)))
                .andExpect(status().isNoContent());
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("The request body could not be created. %s", e)
            );
        }
    }
    @Test
    public void getFriends_validUserId_returnsFriendList() throws Exception {
        User friend = new User();
        friend.setId(2L);
        friend.setUsername("friendUser");
        friend.setToken("token-2");
        friend.setStatus(UserStatus.ONLINE);
        friend.setBio("friend bio");
        friend.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        friend.setTotalGamesPlayed(1);
        friend.setTotalWins(0);
        friend.setTotalPoints(5);

        given(userService.getFriends(1L)).willReturn(Collections.singletonList(friend));

        mockMvc.perform(get("/users/1/friends")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(2)))
                .andExpect(jsonPath("$[0].username", is("friendUser")));
    }

    @Test
    public void removeFriend_validInput_returnsNoContent() throws Exception {
        doNothing().when(userService).removeFriend(1L, 2L);

        mockMvc.perform(delete("/users/1/friends/2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    public void getFriendRequests_validUserId_returnsRequestList() throws Exception {
        User sender = new User();
        sender.setId(10L);
        sender.setUsername("senderUser");
        sender.setToken("token-sender");
        sender.setStatus(UserStatus.ONLINE);
        sender.setBio("bio");
        sender.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        sender.setTotalGamesPlayed(0);
        sender.setTotalWins(0);
        sender.setTotalPoints(0);

        User receiver = new User();
        receiver.setId(1L);
        receiver.setUsername("receiverUser");
        receiver.setToken("token-receiver");
        receiver.setStatus(UserStatus.ONLINE);
        receiver.setBio("bio");
        receiver.setCreationDate(Instant.parse("2026-03-01T10:15:30Z"));
        receiver.setTotalGamesPlayed(0);
        receiver.setTotalWins(0);
        receiver.setTotalPoints(0);

        FriendRequest request = new FriendRequest();
        request.setId(1L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);
        request.setCreatedAt(Instant.parse("2026-03-01T10:15:30Z"));

        given(friendRequestService.getIncomingPendingRequests(1L))
                .willReturn(Collections.singletonList(request));

        mockMvc.perform(get("/users/1/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)));
    }
    @Test
    public void getUser_notFound_returnsNotFound() throws Exception {
        given(userService.getUserById(99L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        mockMvc.perform(get("/users/99").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void createUser_duplicateUsername_returnsConflict() throws Exception {
        UserPostDTO userPostDTO = new UserPostDTO();
        userPostDTO.setUsername("existingUser");
        userPostDTO.setPassword("password");

        given(userService.createUser(Mockito.any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userPostDTO)))
                .andExpect(status().isConflict());
    }

    @Test
    public void updateUser_wrongToken_returnsUnauthorized() throws Exception {
        UserPutDTO userPutDTO = new UserPutDTO();
        userPutDTO.setUsername("newUsername");
        userPutDTO.setBio("bio");

        Mockito.doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .when(userService).updateUserProfile("wrong-token", 1L, "newUsername", "bio");

        mockMvc.perform(put("/users/1")
                        .header("Authorization", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(userPutDTO)))
                .andExpect(status().isUnauthorized());
    }


    @Test
    public void getUsers_emptyList_returnsEmptyArray() throws Exception {
        given(userService.getUsers()).willReturn(Collections.emptyList());

        mockMvc.perform(get("/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void getFriends_emptyList_returnsEmptyArray() throws Exception {
        given(userService.getFriends(1L)).willReturn(Collections.emptyList());

        mockMvc.perform(get("/users/1/friends").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}