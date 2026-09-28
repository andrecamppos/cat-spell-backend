package com.catspell.api.moderation.event

import com.catspell.api.email.service.EmailSender
import com.catspell.api.email.service.ReportEmailRenderer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Delivers the out-of-band operator notification for a filed report. Runs asynchronously and only
 * AFTER the report transaction commits (MOD-07, D-02): `@Async` (enabled by `@EnableAsync` on
 * CatSpellApplication) moves the mail I/O off the request/persistence thread, and the send is
 * swallow-logged so a mail outage never propagates back to — or rolls back — the persisted report.
 * The email is addressed solely to the operator; the reported user is never a recipient (D-11).
 */
@Component
class ReportNotificationListener(
    private val emailSender: EmailSender,
    private val reportEmailRenderer: ReportEmailRenderer,
    @Value("\${app.report.operator-email}") private val operatorEmail: String
) {

    private val log = LoggerFactory.getLogger(ReportNotificationListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReportCreated(event: ReportCreatedEvent) {
        try {
            emailSender.send(reportEmailRenderer.render(operatorEmail, event))
        } catch (e: Exception) {
            log.warn("Operator report email failed for report {}: {}", event.reportId, e.message)
        }
    }
}
