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
import com.catspell.api.moderation.service.BlockService
import com.catspell.api.profile.model.UserPhotoRepository
import com.catspell.api.profile.model.UserProfileRepository
import com.catspell.api.push.event.MessageSentEvent
import org.springframework.context.ApplicationEventPublisher
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
    private val messagingTemplate: SimpMessagingTemplate,
    private val blockService: BlockService,
    private val eventPublisher: ApplicationEventPublisher
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

        // Enforce block + ended-match lock on BOTH entry paths (covered here after conversation
        // resolution). Both return the pretend-not-exist 404 shape, revealing nothing (D-02/D-03).
        val otherUserId = getOtherUserId(conversation, senderId)
        if (blockService.isBlockedEitherWay(senderId, otherUserId)) {
            throw ResourceNotFoundException("Conversation not found")
        }
        if (conversation.match.endedAt != null) {
            throw ResourceNotFoundException("Conversation not found")
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

        // Published inside this @Transactional method so the AFTER_COMMIT listener binds to it (D-07).
        // Reuses the same recipient, sender name, and 100-char preview computed above.
        eventPublisher.publishEvent(
            MessageSentEvent(
                recipientId = otherUserId,
                conversationId = conversation.id!!,
                messageId = message.id!!,
                senderId = senderId,
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

        // A locked/hidden thread's history is not shown to either party: block + ended-match
        // guards mirror sendMessage, returning the pretend-not-exist 404 shape (D-04).
        val conversation = conversationRepository.findById(conversationId).orElseThrow {
            ResourceNotFoundException("Conversation not found")
        }
        val otherUserId = getOtherUserId(conversation, userId)
        if (blockService.isBlockedEitherWay(userId, otherUserId)) {
            throw ResourceNotFoundException("Conversation not found")
        }
        if (conversation.match.endedAt != null) {
            throw ResourceNotFoundException("Conversation not found")
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
        val matchId = match.id!!
        conversationRepository.findByMatchId(matchId)?.let { return it }

        // Concurrent first messages race here. The loser's insert waits for the winner's commit, so the
        // participants are always written in the same transaction as the conversation row (by whoever inserted it).
        val inserted = conversationRepository.insertIfAbsent(matchId, Instant.now())
        val conversation = conversationRepository.findByMatchId(matchId)
            ?: throw IllegalStateException("Conversation for match $matchId missing after insert")
        if (inserted == 0) return conversation

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

    /**
     * Reconnect redelivery: pushes one notification preview for each undelivered message addressed
     * to [userId] in a visible conversation, marks each pushed message delivered, and returns the
     * pushed count.
     *
     * Suppression point 1 (here): undelivered messages in a conversation that is hidden at reconnect
     * time (ended match, or a block either way, resolved by one set-based query per D-05) are marked
     * delivered without being pushed.
     *
     * Suppression point 2 (`MatchService.createMatch`, reactivation branch): reactivating an ended
     * match marks every still-undelivered message of its conversation delivered in one UPDATE
     * ([MessageRepository.markAllDeliveredForMatch]) before the match is visible again. This covers a
     * recipient who never reconnected while the conversation was hidden (G-18-1).
     *
     * Only the two together keep messages from before a block or unmatch from resurfacing as
     * previews after a rematch (D-04). `sendMessage` pushes live without setting delivered, so a
     * message received live is pushed once more on the next reconnect while its conversation is
     * visible (pre-existing behavior). There is no push-time block re-check, because the send path
     * already rejects a blocked or ended pair (D-06), and message rows are kept as evidence.
     *
     * The ordering of the two reads below is load-bearing: the hidden set is resolved BEFORE the
     * undelivered rows are read. A concurrent rematch (`MatchService.createMatch`) resets `endedAt`
     * and sweeps every undelivered row in one transaction. Reading hidden-first means any commit of
     * that transaction that lands between the two reads (or before either) is observed consistently:
     * either the conversation still counts as hidden (and the pre-sweep rows are suppressed), or the
     * undelivered read already reflects the sweep (so there are no stale rows to push). Reading
     * undelivered-first would leave a window where the undelivered read sees stale `delivered = false`
     * rows while the hidden read then sees the conversation as already reactivated, pushing a stale
     * pre-hide preview (G-18-1 / D-04).
     *
     * @return the number of notifications pushed (visible conversations only)
     */
    @Transactional
    fun deliverUnreadMessages(userId: UUID): Int {
        val participations = conversationParticipantRepository.findByUserId(userId)
        val conversationIds = participations.mapNotNull { it.conversation.id }
        if (conversationIds.isEmpty()) return 0

        // Resolve hidden BEFORE reading undelivered rows: see the KDoc note above on why this
        // ordering is load-bearing for the reconnect-vs-rematch race (G-18-1 / D-04 / WR-02).
        val hidden = conversationRepository.findHiddenConversationIdsForUser(userId).toSet()

        val undelivered = messageRepository.findByConversationIdInAndDeliveredFalseAndSenderIdNotOrderByCreatedAtAsc(
            conversationIds, userId
        )
        if (undelivered.isEmpty()) return 0

        val (suppressed, visible) = undelivered.partition { it.conversation.id in hidden }

        suppressed.forEach { it.delivered = true }
        messageRepository.saveAll(suppressed)

        for (msg in visible) {
            val senderProfile = userProfileRepository.findByUserId(msg.sender.id!!)
            val senderName = senderProfile?.displayName ?: "Unknown"

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                ChatNotification(
                    conversationId = msg.conversation.id!!,
                    messageId = msg.id!!,
                    senderName = senderName,
                    preview = msg.content.take(100)
                )
            )

            msg.delivered = true
            messageRepository.save(msg)
        }

        return visible.size
    }

    private fun getOtherUserId(conversation: Conversation, currentUserId: UUID): UUID {
        val match = conversation.match
        return if (match.user1.id == currentUserId) match.user2.id!! else match.user1.id!!
    }
}
