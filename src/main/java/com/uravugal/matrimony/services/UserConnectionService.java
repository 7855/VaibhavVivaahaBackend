package com.uravugal.matrimony.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.MailboxUserDetail;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserConnectionResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ConnectionStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserConnectionEntity;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.UserConnectionRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
// import org.springframework.stereotype.Service;

@Service
public class UserConnectionService {
    
    private final UserConnectionRepository userConnectionRepository;
    private final UserRepository userRepository;
    private final UserDetailRepository userDetailRepository;
    
    public UserConnectionService(UserConnectionRepository userConnectionRepository, 
                                UserRepository userRepository, 
                                UserDetailRepository userDetailRepository) {
        this.userConnectionRepository = userConnectionRepository;
        this.userRepository = userRepository;
        this.userDetailRepository = userDetailRepository;
    }

    public ResultResponse getConnectionCount(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedUserId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedUserId);
            
            long count = userConnectionRepository.countByFollowerIdAndStatusAndIsActive(
                userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y
            ) + userConnectionRepository.countByFollowingIdAndStatusAndIsActive(
                userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y
            );
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Connection count fetched successfully");
            response.setData(count);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching connection count: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getAcceptedConnections(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedUserId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedUserId);
            
            // Get both followers and following connections
            List<UserConnectionEntity> followerConnections = userConnectionRepository.findByFollowerIdAndStatusAndIsActive(
                userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y
            );
            List<UserConnectionEntity> followingConnections = userConnectionRepository.findByFollowingIdAndStatusAndIsActive(
                userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y
            );

            List<MailboxUserDetail> userDetails = new ArrayList<>();
            
            // Add follower connections
            for (UserConnectionEntity connection : followerConnections) {
                MailboxUserDetail detail = new MailboxUserDetail();
                UserEntity user = userRepository.findById(connection.getFollowingId()).orElse(null);
                UserDetailEntity userDetail = userDetailRepository.findByUserId(connection.getFollowingId());
                
                if (user != null && userDetail != null) {
                    detail.setUserId(user.getUserId());
                    detail.setFirstName(user.getFirstName());
                    detail.setLastName(user.getLastName());
                    detail.setProfileImage(user.getProfileImage());
                    
                    if (userDetail != null) {
                        detail.setAge(user.getDob() != null ? 
                            (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                        detail.setDegree(userDetail.getDegree());
                        detail.setAnnualIncome(userDetail.getAnnualIncome());
                        detail.setOccupation(userDetail.getOccupation());
                        detail.setLocation(userDetail.getPresentAddress());
                    }
                    userDetails.add(detail);
                }
            }

            // Add following connections
            for (UserConnectionEntity connection : followingConnections) {
                MailboxUserDetail detail = new MailboxUserDetail();
                UserEntity user = userRepository.findById(connection.getFollowerId()).orElse(null);
                UserDetailEntity userDetail = userDetailRepository.findByUserId(connection.getFollowerId());
                
                if (user != null && userDetail != null) {
                    detail.setUserId(user.getUserId());
                    detail.setFirstName(user.getFirstName());
                    detail.setLastName(user.getLastName());
                    detail.setProfileImage(user.getProfileImage());
                    
                    if (userDetail != null) {
                        detail.setAge(user.getDob() != null ? 
                            (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                        detail.setDegree(userDetail.getDegree());
                        detail.setAnnualIncome(userDetail.getAnnualIncome());
                        detail.setOccupation(userDetail.getOccupation());
                        detail.setLocation(userDetail.getPresentAddress());
                    }
                    userDetails.add(detail);
                }
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Accepted connections fetched successfully");
            response.setData(userDetails);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching accepted connections: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse followUser(Long followerId, Long followingId) {
        ResultResponse response = new ResultResponse();
        try {
            UserEntity follower = userRepository.findById(followerId).orElse(null);
            UserEntity following = userRepository.findById(followingId).orElse(null);

            if (follower == null || following == null) {
                response.setCode(404);  
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }

            UserConnectionEntity connection = new UserConnectionEntity();
            connection.setFollowerId(followerId);
            connection.setFollowingId(followingId);
            connection.setStatus(ConnectionStatus.PENDING);

            userConnectionRepository.save(connection);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Follow request sent successfully");
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error following user: " + e.getMessage());
            return response;
        }
    }

    public ResultResponse getFollowersList(String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            List<UserConnectionEntity> connections = userConnectionRepository.findByFollowingIdAndStatusAndIsActive(userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y);
            
            if (connections.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No followers found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            List<UserConnectionResponse> followers = connections.stream()
                .map(connection -> {
                    UserEntity user = userRepository.findById(connection.getFollowerId()).orElse(null);
                    if (user != null) {
                        UserConnectionResponse response = new UserConnectionResponse();
                        response.setUserId(user.getUserId());
                        response.setMemberId(user.getMemberId());
                        response.setFirstName(user.getFirstName());
                        response.setLastName(user.getLastName());
                        response.setProfileImage(user.getProfileImage());
                        response.setStatus(connection.getStatus());
                        return response;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Followers list fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(followers);
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching followers: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public ResultResponse getFollowingList(String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            List<UserConnectionEntity> connections = userConnectionRepository.findByFollowerIdAndStatusAndIsActive(userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y);
            
            if (connections.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No following users found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            List<UserConnectionResponse> following = connections.stream()
                .map(connection -> {
                    UserEntity user = userRepository.findById(connection.getFollowingId()).orElse(null);
                    if (user != null) {
                        UserConnectionResponse response = new UserConnectionResponse();
                        response.setUserId(user.getUserId());
                        response.setMemberId(user.getMemberId());
                        response.setFirstName(user.getFirstName());
                        response.setLastName(user.getLastName());
                        response.setProfileImage(user.getProfileImage());
                        response.setStatus(connection.getStatus());
                        return response;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Following list fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(following);
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching following list: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public ResultResponse incrementViewCount(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setViewCount(user.getViewCount() + 1);
                userRepository.save(user);
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("View count updated");
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
            }
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating view count: " + e.getMessage());
            return response;
        }
    }

    public ResultResponse getUserConnectionCounts(String encodedId) {
        ResultResponse result = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                HashMap<String, Long> connectionCounts = new HashMap<>();
                // Count all accepted connections where user is either follower or following
                Long yourConnectionCount = userConnectionRepository.countByFollowerIdAndStatusAndIsActive(userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y) +
                                         userConnectionRepository.countByFollowingIdAndStatusAndIsActive(userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y);
                
                // Count all connections where user is the follower (interest sent)
                Long interestSentCount = userConnectionRepository.countByFollowerIdAndIsActive(userId, ActiveStatus.Y);
                
                // Count accepted connections where user is being followed (interest accepted)
                Long interestAcceptedCount = userConnectionRepository.countByFollowingIdAndStatusAndIsActive(userId, ConnectionStatus.ACCEPTED, ActiveStatus.Y);
                
                connectionCounts.put("YourConnection", yourConnectionCount);
                connectionCounts.put("InterestSent", interestSentCount);
                connectionCounts.put("InterestAccepted", interestAcceptedCount);
                connectionCounts.put("viewCount", user.getViewCount().longValue());
                result.setCode(200);
                result.setStatus(ResponseStatus.SUCCESS);
                result.setMessage("User connection counts fetched successfully");
                result.setData(connectionCounts);

            } else {
                result.setCode(404);
                result.setStatus(ResponseStatus.FAILURE);
                result.setMessage("User not found");
            }
            return result;
        } catch (Exception e) {
            result.setCode(500);
            result.setStatus(ResponseStatus.FAILURE);
            result.setMessage("Error fetching user connection counts: " + e.getMessage());
            return result;
        }
    }

    public ResultResponse unfollowUser(Long followerId, Long followingId) {
        ResultResponse response = new ResultResponse();
        try {
            UserEntity follower = userRepository.findById(followerId).orElse(null);
            UserEntity following = userRepository.findById(followingId).orElse(null);

            if (follower == null || following == null) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }

            Optional<UserConnectionEntity> connection = userConnectionRepository.findByFollowerIdAndFollowingId(followerId, followingId);
            if (connection.isPresent()) {
                userConnectionRepository.delete(connection.get());
                
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Unfollowed successfully");
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No connection found to unfollow");
            }
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error unfollowing user: " + e.getMessage());
            return response;
        }
    }

    public ResultResponse updateLastSeen(String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setLastSeen(LocalDateTime.now());
                user.setOnline(true);
                userRepository.save(user);
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Last seen updated");
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
            }
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating last seen: " + e.getMessage());
            return response;
        }
    }

    public ResultResponse getUserOnlineStatus(Long userId) {
        ResultResponse result = new ResultResponse();

        try {
            UserEntity user = userRepository.findById(userId).get();
            if (user != null) {
                Map<String, Object> status = new HashMap<>();
                status.put("userId", user.getUserId());
                status.put("lastSeen", user.getLastSeen());
                status.put("isOnline", user.isOnline());
                
                result.setData(status);
                result.setCode(200);
                result.setStatus(ResponseStatus.SUCCESS);
                result.setMessage("Online status fetched.");
            } else {

                result.setCode(404);
                result.setStatus(ResponseStatus.FAILURE);
                result.setMessage("User not found");
            }

        } catch (Exception e) {
            result.setCode(500);
            result.setStatus(ResponseStatus.FAILURE);
            result.setMessage("Error getting online status: " + e.getMessage());
            return result;
        }
        return result;
    }
}
