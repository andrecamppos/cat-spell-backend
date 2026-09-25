package com.catspell.api.email.service

import com.catspell.api.moderation.event.ReportCreatedEvent
import org.springframework.stereotype.Component

@Component
class ReportEmailRenderer {

    fun render(operatorEmail: String, event: ReportCreatedEvent): EmailMessage {
        val subject = "New user report: ${event.category}"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
              <body>
                <p>A new user report has been filed.</p>
                <ul>
                  <li><strong>Report ID:</strong> ${event.reportId}</li>
                  <li><strong>Reporter ID:</strong> ${event.reporterId}</li>
                  <li><strong>Reported ID:</strong> ${event.reportedId}</li>
                  <li><strong>Category:</strong> ${event.category}</li>
                  <li><strong>Filed at:</strong> ${event.createdAt}</li>
                </ul>
                <p><strong>Details:</strong></p>
                <p>${event.details}</p>
              </body>
            </html>
        """.trimIndent()

        val textBody = """
            A new user report has been filed.

            Report ID:  ${event.reportId}
            Reporter ID: ${event.reporterId}
            Reported ID: ${event.reportedId}
            Category:    ${event.category}
            Filed at:    ${event.createdAt}

            Details:
            ${event.details}
        """.trimIndent()

        return EmailMessage(
            to = operatorEmail,
            subject = subject,
            htmlBody = htmlBody,
            textBody = textBody
        )
    }
}
