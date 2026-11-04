package org.thoughtcrime.securesms.conversation

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.keyvalue.LabsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

class MenuStateTest {
  @get:Rule
  val signalStore = MockSignalStoreRule()

  @Test
  fun `failed historical payments cannot be retried while ordinary failed messages still can`() {
    val labs = mockk<LabsValues>()
    every { SignalStore.labs } returns labs
    every { labs.starredMessages } returns false

    val recipient = mockk<Recipient>(relaxed = true)
    for ((notification, tombstone) in listOf(true to false, false to true, false to false)) {
      val record = mockk<MessageRecord>(relaxed = true)
      every { record.body } returns ""
      every { record.isFailed } returns true
      every { record.isPaymentNotification } returns notification
      every { record.isPaymentTombstone } returns tombstone
      val message = mockk<ConversationMessage>(relaxed = true)
      every { message.messageRecord } returns record
      every { message.originalMessage } returns record
      val part = MultiselectPart.Message(message)
      every { message.multiselectCollection.toSet() } returns setOf(part)

      val menu = MenuState.getMenuState(recipient, setOf(part), false, false, false)

      assertFalse(menu.shouldShowPaymentDetails())
      if (notification || tombstone) {
        assertFalse(menu.shouldShowResendAction())
      } else {
        assertTrue(menu.shouldShowResendAction())
      }
    }
  }
}
