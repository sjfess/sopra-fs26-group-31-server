package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.FriendRequestStatus;
import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.FriendRequestRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class FriendRequestService {

    private final Logger log = LoggerFactory.getLogger(FriendRequestService.class);

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;

    public FriendRequestService(
            @Qualifier("friendRequestRepository") FriendRequestRepository friendRequestRepository,
            @Qualifier("userRepository") UserRepository userRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
    }

    public FriendRequest sendFriendRequest(Long senderId, String receiverUsername) {
        User sender = getUserById(senderId);
        User receiver = getUserByUsername(receiverUsername);

        if (sender.getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot send a friend request to yourself.");
        }

        if (sender.getFriends().contains(receiver)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You are already friends with this user.");
        }

        FriendRequest existingDirectRequest =
                friendRequestRepository.findBySenderAndReceiverAndStatus(
                        sender, receiver, FriendRequestStatus.PENDING);
        if (existingDirectRequest != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending friend request already exists.");
        }

        FriendRequest existingReverseRequest =
                friendRequestRepository.findBySenderAndReceiverAndStatus(
                        receiver, sender, FriendRequestStatus.PENDING);
        if (existingReverseRequest != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This user has already sent you a friend request.");
        }

        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setSender(sender);
        friendRequest.setReceiver(receiver);
        friendRequest.setStatus(FriendRequestStatus.PENDING);
        friendRequest.setCreatedAt(Instant.now());

        friendRequest = friendRequestRepository.save(friendRequest);
        friendRequestRepository.flush();

        log.debug("Friend request created from user {} to user {}", sender.getId(), receiver.getId());
        return friendRequest;
    }

    public FriendRequest respondToFriendRequest(Long requestId, Long receiverId, String action) {
        User receiver = getUserById(receiverId);
        FriendRequest friendRequest = getFriendRequestById(requestId);

        if (!friendRequest.getReceiver().getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to respond to this friend request.");
        }

        if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This friend request is no longer pending.");
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            User sender = friendRequest.getSender();
            sender.getFriends().add(receiver);
            receiver.getFriends().add(sender);
            userRepository.save(sender);
            userRepository.save(receiver);
            friendRequest.setStatus(FriendRequestStatus.ACCEPTED);
        } else if ("DENY".equalsIgnoreCase(action)) {
            friendRequest.setStatus(FriendRequestStatus.DECLINED);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid action. Use 'ACCEPT' or 'DENY'.");
        }

        friendRequest = friendRequestRepository.save(friendRequest);
        friendRequestRepository.flush();
        return friendRequest;
    }

    public List<FriendRequest> getIncomingPendingRequests(Long userId) {
        User user = getUserById(userId);
        return friendRequestRepository.findAllByReceiverAndStatus(user, FriendRequestStatus.PENDING);
    }

    public List<FriendRequest> getOutgoingPendingRequests(Long userId) {
        User user = getUserById(userId);
        return friendRequestRepository.findAllBySenderAndStatus(user, FriendRequestStatus.PENDING);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User with id " + userId + " was not found."));
    }

    private FriendRequest getFriendRequestById(Long requestId) {
        return friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Friend request with id " + requestId + " was not found."));
    }

    private User getUserByUsername(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User with username '" + username + "' was not found.");
        }
        return user;
    }
}