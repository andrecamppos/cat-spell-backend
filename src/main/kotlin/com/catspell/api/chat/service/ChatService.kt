package com.catspell.api.chat.service

import com.catspell.api.auth.model.UserRepository
import com.catspell.api.cat.model.CatPhotoRepository
import com.catspell.api.cat.model.CatProfileRepository
import com.catspell.api.chat.model.*
import com.catspell.api.common.exception.ResourceNotFoundException
import com.catspell.api.match.model.Match
import com.catspell.api.match.model.MatchCatSummary
import com.catspell.api.match.model.MatchRepository
import com.catspell.api.match.model.MatchUserSummary
import com.catspell.api.profile.model.UserPhotoRepository
import com.catspell.api.profile.model.UserProfileRepository
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ChatService(
    private val conversationRepository: ConversationRepository,
    private val conversationParticipantRepository: ConversationParticipantRepository,
    private val messageRepository: MessageRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userPhotoRepository: UserPhotoRepository,
    private val catProfileRepository: CatProfileRepository,
    private val catPhotoRepository: CatPhotoRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    @Transactional
    fun sendMessage(senderId: UUID, request: SendMessageRequest): ChatMessageResponse {
        val conversation = when {
            request.conversationId != null -> {
                val conv = conversationRepository.findById(request.conversationId).orElseThrow {
                    ResourceNotFoundException("Conversation not found")
                }
                if (!conversationParticipantRepository.existsByConversationIdAndUserId(conv.id!!, senderId)) {
                    throw IllegalArgumentException("Not a participant of this conversation")
                }
                conv
            }
            request.matchId != null -> {
                val match = matchRepository.findById(request.matchId).orElseThrow {
                    ResourceNotFoundException("Match not found")
                }
                if (match.user1.id != senderId && match.user2.id != senderId) {
                    throw IllegalArgumentException("Not a participant of this match")
                }
                findOrCreateConversation(match)
            }
            else -> throw IllegalArgumentException("Either conversationId or matchId must be provided")
        }

        val sender = userRepository.getReferenceById(senderId)
        val message = messageRepository.save(
            Message(
                conversation = conversation,
                sender = sender,
                content = request.content
            )
        )

        conversation.lastMessageAt = Instant.now()
        conversationRepository.save(conversation)

        val senderProfile = userProfileRepository.findByUserId(senderId)
        val senderName = senderProfile?.displayName ?: "Unknown"

        val response = ChatMessageResponse(
            messageId = message.id!!,
            conversationId = conversation.id!!,
            senderId = senderId,
            senderName = senderName,
            content = message.content,
            createdAt = message.createdAt
        )

        messagingTemplate.convertAndSend("/topic/chat/${conversation.id}", response)

        val otherUserId = getOtherUserId(conversation, senderId)
        messagingTemplate.convertAndSendToUser(
            otherUserId.toString(),
            "/queue/notifications",
            ChatNotification(
                conversationId = conversation.id!!,
                messageId = message.id!!,
                senderName = senderName,
                preview = message.content.take(100)
            )
        )

        return response
    }

    @Transactional(readOnly = true)
    fun getMessages(userId: UUID, conversationId: UUID, cursor: Instant?, size: Int = 30): MessagePageResponse {
        if (!conversationParticipantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw IllegalArgumentException("Not a participant of this conversation")
        }

        val pageable = PageRequest.of(0, size)
        val messages = if (cursor != null) {
            messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                conversationId, cursor, pageable
            )
        } else {
            messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
        }

        val messageResponses = messages.map { msg ->
            val senderProfile = userProfileRepository.findByUserId(msg.sender.id!!)
            ChatMessageResponse(
                messageId = msg.id!!,
                conversationId = conversationId,
                senderId = msg.sender.id!!,
                senderName = senderProfile?.displayName ?: "Unknown",
                content = msg.content,
                createdAt = msg.createdAt
            )
        }

        return MessagePageResponse(
            messages = messageResponses,
            nextCursor = messages.lastOrNull()?.createdAt,
            hasMore = messages.size == size
        )
    }

    @Transactional
    fun findOrCreateConversation(match: Match): Conversation {
        conversationRepository.findByMatchId(match.id!!)?.let { return it }

        val conversation = conversationRepository.save(
            Conversation(match = match)
        )

        conversationParticipantRepository.save(
            ConversationParticipant(
                conversation = conversation,
                user = match.user1
            )
        )
        conversationParticipantRepository.save(
            ConversationParticipant(
                conversation = conversation,
                user = match.user2
            )
        )

        return conversation
    }

    @Transactional(readOnly = true)
    fun getConversations(userId: UUID): ConversationListResponse {
        val conversations = conversationRepository.findConversationsByUserId(userId)

        val responses = conversations.map { conv ->
            val otherId = getOtherUserId(conv, userId)

            val otherProfile = userProfileRepository.findByUserId(otherId)
            val otherPhotoThumbnail = userPhotoRepository.findByUserIdOrderByDisplayOrderAsc(otherId)
                .firstOrNull { it.status == "ACTIVE" }
                ?.thumbnailS3Key

            val otherCats = catProfileRepository.findByUserId(otherId).map { cp ->
                val catPhoto = catPhotoRepository.findByCatProfileIdOrderByDisplayOrderAsc(cp.id!!)
                    .firstOrNull { it.status == "ACTIVE" }
                MatchCatSummary(
                    name = cp.name,
                    photoThumbnail = catPhoto?.thumbnailS3Key
                )
            }

            val lastMsg = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.id!!)
            val lastMessagePreview = lastMsg?.let {
                LastMessagePreview(
                    content = it.content.take(100),
                    sentAt = it.createdAt,
                    sentByMe = it.sender.id == userId
                )
            }

            val participant = conversationParticipantRepository.findByConversationIdAndUserId(conv.id!!, userId)
            val unreadCount = if (participant?.lastReadAt != null) {
                messageRepository.countByConversationIdAndSenderIdNotAndCreatedAtAfter(
                    conv.id!!, userId, participant.lastReadAt!!
                ).toInt()
            } else {
                messageRepository.countByConversationIdAndSenderIdNot(conv.id!!, userId).toInt()
            }

            ConversationResponse(
                conversationId = conv.id!!,
                matchId = conv.match.id!!,
                otherUser = MatchUserSummary(
                    userId = otherId,
                    displayName = otherProfile?.displayName ?: "Unknown",
                    photoThumbnail = otherPhotoThumbnail
                ),
                otherUserCats = otherCats,
                lastMessage = lastMessagePreview,
                unreadCount = unreadCount
            )
        }

        return ConversationListResponse(conversations = responses)
    }

    @Transactional
    fun markRead(userId: UUID, conversationId: UUID) {
        val participant = conversationParticipantRepository.findByConversationIdAndUserId(conversationId, userId)
            ?: throw ResourceNotFoundException("Not a participant of this conversation")
        participant.lastReadAt = Instant.now()
        conversationParticipantRepository.save(participant)
    }

    private fun getOtherUserId(conversation: Conversation, currentUserId: UUID): UUID {
        val match = conversation.match
        return if (match.user1.id == currentUserId) match.user2.id!! else match.user1.id!!
    }
}
