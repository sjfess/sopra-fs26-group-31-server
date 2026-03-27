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
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

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
}