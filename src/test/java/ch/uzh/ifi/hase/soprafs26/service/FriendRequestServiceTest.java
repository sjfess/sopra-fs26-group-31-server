package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.FriendRequestStatus;
import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.FriendRequestRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FriendRequestServiceTest {

    private FriendRequestRepository friendRequestRepository;
    private UserRepository userRepository;
    private FriendRequestService friendRequestService;

    private User sender;
    private User receiver;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        friendRequestRepository = Mockito.mock(FriendRequestRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        friendRequestService = new FriendRequestService(friendRequestRepository, userRepository);

        sender = new User();
        sender.setId(1L);
        sender.setUsername("alice");
        sender.setFriends(new HashSet<>());

        receiver = new User();
        receiver.setId(2L);
        receiver.setUsername("bob");
        receiver.setFriends(new HashSet<>());

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(userRepository.findByUsername("bob")).thenReturn(receiver);
        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── sendFriendRequest ─────────────────────────────────────────────────────

    @Test
    public void sendFriendRequest_validInput_createsPendingRequest() {
        when(friendRequestRepository.findBySenderAndReceiverAndStatus(sender, receiver, FriendRequestStatus.PENDING))
                .thenReturn(null);
        when(friendRequestRepository.findBySenderAndReceiverAndStatus(receiver, sender, FriendRequestStatus.PENDING))
                .thenReturn(null);

        FriendRequest result = friendRequestService.sendFriendRequest(1L, "bob");

        assertNotNull(result);
        assertEquals(sender, result.getSender());
        assertEquals(receiver, result.getReceiver());
        assertEquals(FriendRequestStatus.PENDING, result.getStatus());
        assertNotNull(result.getCreatedAt());
        verify(friendRequestRepository, times(1)).save(any(FriendRequest.class));
        verify(friendRequestRepository, times(1)).flush();
    }

    @Test
    public void sendFriendRequest_senderNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.sendFriendRequest(99L, "bob")
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    public void sendFriendRequest_receiverNotFound_throwsNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(null);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.sendFriendRequest(1L, "unknown")
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    public void sendFriendRequest_toSelf_throwsBadRequest() {
        when(userRepository.findByUsername("alice")).thenReturn(sender);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.sendFriendRequest(1L, "alice")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    public void sendFriendRequest_alreadyFriends_throwsConflict() {
        sender.getFriends().add(receiver);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.sendFriendRequest(1L, "bob")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    public void sendFriendRequest_pendingRequestAlreadyExists_throwsConflict() {
        FriendRequest existing = new FriendRequest();
        existing.setSender(sender);
        existing.setReceiver(receiver);
        existing.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findBySenderAndReceiverAndStatus(sender, receiver, FriendRequestStatus.PENDING))
                .thenReturn(existing);
        when(friendRequestRepository.findBySenderAndReceiverAndStatus(receiver, sender, FriendRequestStatus.PENDING))
                .thenReturn(null);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.sendFriendRequest(1L, "bob")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    public void sendFriendRequest_reverseRequestAlreadyExists_throwsConflict() {
        FriendRequest reverseRequest = new FriendRequest();
        reverseRequest.setSender(receiver);
        reverseRequest.setReceiver(sender);
        reverseRequest.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findBySenderAndReceiverAndStatus(sender, receiver, FriendRequestStatus.PENDING))
                .thenReturn(null);
        when(friendRequestRepository.findBySenderAndReceiverAndStatus(receiver, sender, FriendRequestStatus.PENDING))
                .thenReturn(reverseRequest);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.sendFriendRequest(1L, "bob")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(friendRequestRepository, never()).save(any());
    }

    // ── respondToFriendRequest ────────────────────────────────────────────────

    @Test
    public void respondToFriendRequest_accept_addsFriendshipAndSetsAccepted() {
        FriendRequest request = new FriendRequest();
        request.setId(10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FriendRequest result = friendRequestService.respondToFriendRequest(10L, 2L, "ACCEPT");

        assertEquals(FriendRequestStatus.ACCEPTED, result.getStatus());
        assertTrue(sender.getFriends().contains(receiver));
        assertTrue(receiver.getFriends().contains(sender));
        verify(userRepository, times(1)).save(sender);
        verify(userRepository, times(1)).save(receiver);
        verify(friendRequestRepository, times(1)).flush();
    }

    @Test
    public void respondToFriendRequest_deny_setsDeclined() {
        FriendRequest request = new FriendRequest();
        request.setId(10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FriendRequest result = friendRequestService.respondToFriendRequest(10L, 2L, "DENY");

        assertEquals(FriendRequestStatus.DECLINED, result.getStatus());
        assertTrue(sender.getFriends().isEmpty());
        assertTrue(receiver.getFriends().isEmpty());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void respondToFriendRequest_acceptCaseInsensitive_works() {
        FriendRequest request = new FriendRequest();
        request.setId(10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(friendRequestRepository.save(any(FriendRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FriendRequest result = friendRequestService.respondToFriendRequest(10L, 2L, "accept");

        assertEquals(FriendRequestStatus.ACCEPTED, result.getStatus());
    }

    @Test
    public void respondToFriendRequest_invalidAction_throwsBadRequest() {
        FriendRequest request = new FriendRequest();
        request.setId(10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.respondToFriendRequest(10L, 2L, "MAYBE")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void respondToFriendRequest_wrongReceiver_throwsForbidden() {
        FriendRequest request = new FriendRequest();
        request.setId(10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.respondToFriendRequest(10L, 1L, "ACCEPT")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void respondToFriendRequest_notPending_throwsConflict() {
        FriendRequest request = new FriendRequest();
        request.setId(10L);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.ACCEPTED);

        when(friendRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.respondToFriendRequest(10L, 2L, "ACCEPT")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    public void respondToFriendRequest_requestNotFound_throwsNotFound() {
        when(friendRequestRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.respondToFriendRequest(99L, 2L, "ACCEPT")
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── getIncomingPendingRequests ─────────────────────────────────────────────

    @Test
    public void getIncomingPendingRequests_returnsList() {
        FriendRequest request = new FriendRequest();
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findAllByReceiverAndStatus(receiver, FriendRequestStatus.PENDING))
                .thenReturn(List.of(request));

        List<FriendRequest> result = friendRequestService.getIncomingPendingRequests(2L);

        assertEquals(1, result.size());
        assertEquals(sender, result.get(0).getSender());
    }

    @Test
    public void getIncomingPendingRequests_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.getIncomingPendingRequests(99L)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    public void getIncomingPendingRequests_noRequests_returnsEmptyList() {
        when(friendRequestRepository.findAllByReceiverAndStatus(receiver, FriendRequestStatus.PENDING))
                .thenReturn(List.of());

        List<FriendRequest> result = friendRequestService.getIncomingPendingRequests(2L);

        assertTrue(result.isEmpty());
    }

    // ── getOutgoingPendingRequests ─────────────────────────────────────────────

    @Test
    public void getOutgoingPendingRequests_returnsList() {
        FriendRequest request = new FriendRequest();
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(FriendRequestStatus.PENDING);

        when(friendRequestRepository.findAllBySenderAndStatus(sender, FriendRequestStatus.PENDING))
                .thenReturn(List.of(request));

        List<FriendRequest> result = friendRequestService.getOutgoingPendingRequests(1L);

        assertEquals(1, result.size());
        assertEquals(receiver, result.get(0).getReceiver());
    }

    @Test
    public void getOutgoingPendingRequests_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> friendRequestService.getOutgoingPendingRequests(99L)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
