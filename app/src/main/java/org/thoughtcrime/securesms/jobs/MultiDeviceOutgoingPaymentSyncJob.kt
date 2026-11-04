package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * Retains the serialized outgoing-payment job contract without running the retired wallet sync.
 */
class MultiDeviceOutgoingPaymentSyncJob private constructor(
  parameters: Parameters,
  private val uuid: UUID
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceOutgoingPaymentSyncJob::class.java)

    const val KEY = "MultiDeviceOutgoingPaymentSyncJob"

    private const val KEY_UUID = "uuid"
  }

  constructor(sentPaymentId: UUID) : this(
    Parameters.Builder()
      .setQueue("MultiDeviceOutgoingPaymentSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .build(),
    sentPaymentId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_UUID, uuid.toString())
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    Log.i(TAG, "Outgoing payment sync is disabled in Molly.")
    return Result.failure()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to sync sent payment!")
  }

  class Factory : Job.Factory<MultiDeviceOutgoingPaymentSyncJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceOutgoingPaymentSyncJob {
      val data = JsonJobData.deserialize(serializedData)
      return MultiDeviceOutgoingPaymentSyncJob(parameters, UUID.fromString(data.getString(KEY_UUID)))
    }
  }
}
