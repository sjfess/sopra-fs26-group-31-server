package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.constant.FriendRequestStatus;
import ch.uzh.ifi.hase.soprafs26.entity.FriendRequest;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("friendRequestRepository")
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    FriendRequest findBySenderAndReceiver(User sender, User receiver);

    List<FriendRequest> findAllByReceiverAndStatus(User receiver, FriendRequestStatus status);

    List<FriendRequest> findAllBySenderAndStatus(User sender, FriendRequestStatus status);
}