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

    public FriendRequest sendFriendRequest(String senderToken, Long receiverId) {
        User sender = getUserByToken(senderToken);
        User receiver = getUserById(receiverId);

        if (sender.getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot send a friend request to yourself.");
        }

        if (sender.getFriends().contains(receiver)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You are already friends with this user.");
        }

        FriendRequest existingDirectRequest =
                friendRequestRepository.findBySenderAndReceiver(sender, receiver);
        if (existingDirectRequest != null &&
                existingDirectRequest.getStatus() == FriendRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending friend request already exists.");
        }

        FriendRequest existingReverseRequest =
                friendRequestRepository.findBySenderAndReceiver(receiver, sender);
        if (existingReverseRequest != null &&
                existingReverseRequest.getStatus() == FriendRequestStatus.PENDING) {
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

    public FriendRequest acceptFriendRequest(String receiverToken, Long requestId) {
        User receiver = getUserByToken(receiverToken);
        FriendRequest friendRequest = getFriendRequestById(requestId);

        if (!friendRequest.getReceiver().getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to accept this friend request.");
        }

        if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This friend request is no longer pending.");
        }

        User sender = friendRequest.getSender();

        sender.getFriends().add(receiver);
        receiver.getFriends().add(sender);

        friendRequest.setStatus(FriendRequestStatus.ACCEPTED);

        userRepository.save(sender);
        userRepository.save(receiver);
        friendRequestRepository.save(friendRequest);
        friendRequestRepository.flush();

        log.debug("Friend request {} accepted by user {}", requestId, receiver.getId());
        return friendRequest;
    }

    public FriendRequest declineFriendRequest(String receiverToken, Long requestId) {
        User receiver = getUserByToken(receiverToken);
        FriendRequest friendRequest = getFriendRequestById(requestId);

        if (!friendRequest.getReceiver().getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to decline this friend request.");
        }

        if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This friend request is no longer pending.");
        }

        friendRequest.setStatus(FriendRequestStatus.DECLINED);
        friendRequest = friendRequestRepository.save(friendRequest);
        friendRequestRepository.flush();

        log.debug("Friend request {} declined by user {}", requestId, receiver.getId());
        return friendRequest;
    }

    public List<FriendRequest> getIncomingPendingRequests(String userToken) {
        User user = getUserByToken(userToken);
        return friendRequestRepository.findAllByReceiverAndStatus(user, FriendRequestStatus.PENDING);
    }

    public List<FriendRequest> getOutgoingPendingRequests(String userToken) {
        User user = getUserByToken(userToken);
        return friendRequestRepository.findAllBySenderAndStatus(user, FriendRequestStatus.PENDING);
    }

    private User getUserByToken(String token) {
        User user = userRepository.findByToken(token);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token.");
        }

        return user;
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
}