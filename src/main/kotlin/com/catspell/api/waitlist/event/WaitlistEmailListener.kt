package com.catspell.api.waitlist.event

import com.catspell.api.email.service.EmailSendStatus
import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.WaitlistConfirmEmailRenderer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Delivers the waitlist confirmation email (WAIT-02). Runs asynchronously and only AFTER the join transaction
 * commits: `@Async` (enabled by `@EnableAsync` on CatSpellApplication) keeps mail I/O off the request thread so a
 * new/pending join is not measurably slower than a no-op one, and the send is swallow-logged so a mail outage never
 * changes the constant 202 (RESEARCH Pattern 5). Logs carry fixed text only — never the token, the address, or an
 * exception message that might embed the address (D-08).
 */
@Component
class WaitlistEmailListener(
    private val emailSender: EmailSender,
    private val waitlistConfirmEmailRenderer: WaitlistConfirmEmailRenderer
) {

    private val log = LoggerFactory.getLogger(WaitlistEmailListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onConfirmationRequested(event: WaitlistConfirmationRequestedEvent) {
        try {
            val result = emailSender.send(waitlistConfirmEmailRenderer.render(event.email, event.rawToken))
            if (result.status != EmailSendStatus.SUCCESS) {
                log.warn("Waitlist confirmation email was not accepted by the email provider")
            }
        } catch (e: Exception) {
            log.warn("Waitlist confirmation email send failed: {}", e.javaClass.simpleName)
        }
    }
}
