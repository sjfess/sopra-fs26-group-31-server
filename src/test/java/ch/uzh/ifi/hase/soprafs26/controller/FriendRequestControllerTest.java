package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ch.uzh.ifi.hase.soprafs26.constant.FriendRequestStatus;
import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.FriendRequestPutDTO;
import ch.uzh.ifi.hase.soprafs26.service.FriendRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendRequestController.class)
public class FriendRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendRequestService friendRequestService;



    @Test
    public void sendFriendRequest_validInput_returnsCreated() throws Exception {
        User senderUser = new User();
        senderUser.setId(1L);
        senderUser.setUsername("alice");

        User receiverUser = new User();
        receiverUser.setId(2L);
        receiverUser.setUsername("bob");

        FriendRequest created = new FriendRequest();
        created.setId(10L);
        created.setSender(senderUser);
        created.setReceiver(receiverUser);
        created.setStatus(FriendRequestStatus.PENDING);
        created.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        FriendRequestPostDTO dto = new FriendRequestPostDTO();
        dto.setSenderId(1L);
        dto.setReceiverUsername("bob");

        given(friendRequestService.sendFriendRequest(1L, "bob")).willReturn(created);

        mockMvc.perform(post("/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.senderId", is(1)))
                .andExpect(jsonPath("$.senderUsername", is("alice")))
                .andExpect(jsonPath("$.receiverId", is(2)))
                .andExpect(jsonPath("$.receiverUsername", is("bob")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    public void sendFriendRequest_toSelf_returnsBadRequest() throws Exception {
        FriendRequestPostDTO dto = new FriendRequestPostDTO();
        dto.setSenderId(1L);
        dto.setReceiverUsername("alice");

        given(friendRequestService.sendFriendRequest(1L, "alice"))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You cannot send a friend request to yourself."));

        mockMvc.perform(post("/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void sendFriendRequest_alreadyFriends_returnsConflict() throws Exception {
        FriendRequestPostDTO dto = new FriendRequestPostDTO();
        dto.setSenderId(1L);
        dto.setReceiverUsername("bob");

        given(friendRequestService.sendFriendRequest(1L, "bob"))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "You are already friends with this user."));

        mockMvc.perform(post("/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    public void sendFriendRequest_receiverNotFound_returnsNotFound() throws Exception {
        FriendRequestPostDTO dto = new FriendRequestPostDTO();
        dto.setSenderId(1L);
        dto.setReceiverUsername("nobody");

        given(friendRequestService.sendFriendRequest(1L, "nobody"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User with username 'nobody' was not found."));

        mockMvc.perform(post("/friend-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isNotFound());
    }

    // ── PUT /friend-requests/{requestId} ─────────────────────────────────────

    @Test
    public void respondToFriendRequest_accept_returnsOkWithAcceptedStatus() throws Exception {
        User senderUser = new User();
        senderUser.setId(1L);
        senderUser.setUsername("alice");

        User receiverUser = new User();
        receiverUser.setId(2L);
        receiverUser.setUsername("bob");

        FriendRequest updated = new FriendRequest();
        updated.setId(10L);
        updated.setSender(senderUser);
        updated.setReceiver(receiverUser);
        updated.setStatus(FriendRequestStatus.ACCEPTED);
        updated.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        FriendRequestPutDTO dto = new FriendRequestPutDTO();
        dto.setReceiverId(2L);
        dto.setAction("ACCEPT");

        given(friendRequestService.respondToFriendRequest(10L, 2L, "ACCEPT")).willReturn(updated);

        mockMvc.perform(put("/friend-requests/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.senderId", is(1)))
                .andExpect(jsonPath("$.receiverId", is(2)));
    }

    @Test
    public void respondToFriendRequest_deny_returnsOkWithDeclinedStatus() throws Exception {
        User senderUser = new User();
        senderUser.setId(1L);
        senderUser.setUsername("alice");

        User receiverUser = new User();
        receiverUser.setId(2L);
        receiverUser.setUsername("bob");

        FriendRequest updated = new FriendRequest();
        updated.setId(10L);
        updated.setSender(senderUser);
        updated.setReceiver(receiverUser);
        updated.setStatus(FriendRequestStatus.DECLINED);
        updated.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        FriendRequestPutDTO dto = new FriendRequestPutDTO();
        dto.setReceiverId(2L);
        dto.setAction("DENY");

        given(friendRequestService.respondToFriendRequest(10L, 2L, "DENY")).willReturn(updated);

        mockMvc.perform(put("/friend-requests/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DECLINED")));
    }

    @Test
    public void respondToFriendRequest_wrongReceiver_returnsForbidden() throws Exception {
        FriendRequestPutDTO dto = new FriendRequestPutDTO();
        dto.setReceiverId(99L);
        dto.setAction("ACCEPT");

        given(friendRequestService.respondToFriendRequest(10L, 99L, "ACCEPT"))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not allowed to respond to this friend request."));

        mockMvc.perform(put("/friend-requests/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void respondToFriendRequest_invalidAction_returnsBadRequest() throws Exception {
        FriendRequestPutDTO dto = new FriendRequestPutDTO();
        dto.setReceiverId(2L);
        dto.setAction("MAYBE");

        given(friendRequestService.respondToFriendRequest(10L, 2L, "MAYBE"))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid action. Use 'ACCEPT' or 'DENY'."));

        mockMvc.perform(put("/friend-requests/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void respondToFriendRequest_requestNotFound_returnsNotFound() throws Exception {
        FriendRequestPutDTO dto = new FriendRequestPutDTO();
        dto.setReceiverId(2L);
        dto.setAction("ACCEPT");

        given(friendRequestService.respondToFriendRequest(99L, 2L, "ACCEPT"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Friend request with id 99 was not found."));

        mockMvc.perform(put("/friend-requests/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(dto)))
                .andExpect(status().isNotFound());
    }

    private String toJson(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The request body could not be serialized: " + e.getMessage());
        }
    }
}
