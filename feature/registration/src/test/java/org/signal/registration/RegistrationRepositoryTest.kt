/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.content.Context
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.billing.OneTimePurchaseApi
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.network.api.RegistrationApiV2.LinkDeviceResponse
import org.signal.network.api.RegistrationApiV2.RegisterAsLinkedDeviceError
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.ReserveBackupIdError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import org.signal.registration.proto.AccountData
import org.signal.registration.proto.LinkedDeviceData
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [RegistrationRepository] behavior that isn't just a passthrough to a controller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationRepositoryTest {

  private lateinit var networkController: FakeNetworkController
  private lateinit var storageController: FakeStorageController
  private lateinit var repository: RegistrationRepository
  private lateinit var numberlessRepository: RegistrationRepository

  private val aep = AccountEntropyPool.generate()
  private val masterKey = aep.deriveMasterKey()
  private val svrCredentials = SvrCredentials(username = "user", password = "pass")
  private val backupInfo = GetBackupInfoResponse(cdn = 3, backupDir = "dir", mediaDir = "media", backupName = "backup", usedSpace = 1024L)

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())
    networkController = FakeNetworkController()
    storageController = FakeStorageController()
    repository = RegistrationRepository(
      context = mockk<Context>(relaxed = true),
      networkController = networkController,
      storageController = storageController,
      isLinkAndSyncAvailable = false,
      signalLoginPurchaseApi = OneTimePurchaseApi.Empty
    )
    numberlessRepository = RegistrationRepository(
      context = mockk<Context>(relaxed = true),
      networkController = networkController,
      storageController = storageController,
      isLinkAndSyncAvailable = false,
      isPhoneNumberlessRegistrationAvailable = true,
      signalLoginPurchaseApi = OneTimePurchaseApi.Empty
    )
  }

  // ==================== getAndMaybeHealRemoteBackupInfo ====================

  @Test
  fun `getAndMaybeHealRemoteBackupInfo does not re-commit the backup-id when the credential verifies`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.Success(backupInfo) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(0)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo does not re-commit the backup-id for unrelated errors`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.NoBackup) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.NonSuccess).error).isEqualTo(GetBackupInfoError.NoBackup)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(0)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo re-commits the backup-id and retries when the credential fails verification`() = runTest {
    var attempts = 0
    networkController.onGetRemoteBackupInfo = {
      attempts++
      if (attempts == 1) {
        RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed)
      } else {
        RequestResult.Success(backupInfo)
      }
    }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.Success).result).isEqualTo(backupInfo)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(1)
    assertThat(attempts).isEqualTo(2)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo only re-commits the backup-id once when the retry fails too`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.NonSuccess).error).isEqualTo(GetBackupInfoError.CredentialVerificationFailed)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(1)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo reports the original error when the backup-id cannot be re-committed`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.NonSuccess(ReserveBackupIdError.RateLimited(30.seconds)) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.NonSuccess).error).isEqualTo(GetBackupInfoError.CredentialVerificationFailed)
    assertThat(networkController.reserveBackupIdCount).isEqualTo(1)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo reports a retryable error when re-committing the backup-id hits the network`() = runTest {
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.RetryableNetworkError(IOException("no network")) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat(result).isInstanceOf(RequestResult.RetryableNetworkError::class)
  }

  @Test
  fun `getAndMaybeHealRemoteBackupInfo propagates an application error from re-committing the backup-id rather than reporting a credential problem`() = runTest {
    val cause = IllegalStateException("ACI not available")
    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.ApplicationError(cause) }

    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    assertThat((result as RequestResult.ApplicationError).cause).isEqualTo(cause)
  }

  // ==================== restoreMasterKeyFromSvr ====================

  @Test
  fun `restoreMasterKeyFromSvr commits the restored data when already registered`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.Success(MasterKeyResponse(masterKey)) }

    val result = repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = false, isRegistered = true)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(storageController.committedData).isNotNull()
    assertThat(storageController.committedData!!.pin).isEqualTo("1234")
  }

  @Test
  fun `restoreMasterKeyFromSvr does not commit the restored data when not yet registered`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.Success(MasterKeyResponse(masterKey)) }

    val result = repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = false, isRegistered = false)

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(storageController.committedData).isNull()
  }

  @Test
  fun `restoreMasterKeyFromSvr still records the in-progress data when not yet registered`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.Success(MasterKeyResponse(masterKey)) }

    repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = true, isRegistered = false)

    val inProgress = storageController.readInProgressRegistrationData()
    assertThat(inProgress.pin).isEqualTo("1234")
    assertThat(inProgress.masterKeyForInitialDataRestore?.toByteArray()?.toList()).isEqualTo(masterKey.serialize().toList())
    assertThat(inProgress.registrationLockEnabled).isTrue()
    assertThat(inProgress.svrCredentials.map { it.username }).isEqualTo(listOf("user"))
  }

  @Test
  fun `restoreMasterKeyFromSvr does not record or commit anything on failure`() = runTest {
    networkController.onRestoreMasterKeyFromSvr = { RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 5)) }

    val result = repository.restoreMasterKeyFromSvr(svrCredentials, pin = "1234", forRegistrationLock = false, isRegistered = true)

    assertThat(result).isInstanceOf(RequestResult.NonSuccess::class)
    assertThat(storageController.committedData).isNull()
    assertThat(storageController.readInProgressRegistrationData().pin).isEmpty()
  }

  // ==================== registerAccountWithoutPhoneNumber / reRegisterAccountWithoutPhoneNumber ====================

  @Test
  fun `reRegisterAccountWithoutPhoneNumber sends PNI key material when recovering an account by ACI`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }

    val result = numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    assertThat(result).isInstanceOf(RequestResult.Success::class)

    val pniPreKeys = networkController.lastRegisterAccountRequest?.pniPreKeys
    assertThat(pniPreKeys).isNotNull()
    assertThat(networkController.lastRegisterAccountRequest?.pniRegistrationId).isNotNull()
    assertThat(pniPreKeys!!.identityKey.publicKey.verifySignature(pniPreKeys.signedPreKey.keyPair.publicKey.serialize(), pniPreKeys.signedPreKey.signature)).isTrue()
    assertThat(pniPreKeys.identityKey.publicKey.verifySignature(pniPreKeys.lastResortKyberPreKey.keyPair.publicKey.serialize(), pniPreKeys.lastResortKyberPreKey.signature)).isTrue()
  }

  @Test
  fun `reRegisterAccountWithoutPhoneNumber does not keep the PNI key material it sends when recovering an account by ACI`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }

    numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    val accountData = storageController.committedData?.accountData
    assertThat(accountData).isNotNull()
    assertThat(accountData!!.pniIdentityKeyPair.size).isEqualTo(0)
    assertThat(accountData.pniRegistrationId).isEqualTo(0)
  }

  @Test
  fun `reRegisterAccountWithoutPhoneNumber keeps the PNI key material it sends when the reclaimed account has a phone number`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = "+15551234567")) }

    numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    val sentPniPreKeys = networkController.lastRegisterAccountRequest!!.pniPreKeys!!
    val accountData = storageController.committedData?.accountData
    assertThat(accountData).isNotNull()
    assertThat(IdentityKeyPair(accountData!!.pniIdentityKeyPair.toByteArray()).publicKey).isEqualTo(sentPniPreKeys.identityKey)
    assertThat(accountData.pniRegistrationId).isEqualTo(networkController.lastRegisterAccountRequest!!.pniRegistrationId)
  }

  @Test
  fun `reRegisterAccountWithoutPhoneNumber clears PNI key material left behind by an abandoned attempt`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }
    storageController.updateInProgressRegistrationData {
      accountData = AccountData(
        pniIdentityKeyPair = IdentityKeyPair.generate().serialize().toByteString(),
        pniSignedPreKey = "abandoned-signed-pre-key".toByteArray().toByteString(),
        pniLastResortKyberPreKey = "abandoned-kyber-pre-key".toByteArray().toByteString(),
        pniRegistrationId = 1234
      )
    }

    numberlessRepository.reRegisterAccountWithoutPhoneNumber(
      aci = ACI.from(UUID.randomUUID()),
      recoveryPassword = masterKey.deriveRegistrationRecoveryPassword(),
      aep = aep
    )

    val accountData = storageController.committedData?.accountData
    assertThat(accountData).isNotNull()
    assertThat(accountData!!.pniIdentityKeyPair.size).isEqualTo(0)
    assertThat(accountData.pniSignedPreKey.size).isEqualTo(0)
    assertThat(accountData.pniLastResortKyberPreKey.size).isEqualTo(0)
    assertThat(accountData.pniRegistrationId).isEqualTo(0)
  }

  @Test
  fun `registerAccountWithoutPhoneNumber does not send PNI key material when creating a brand new account`() = runTest {
    networkController.onRegisterAccount = { RequestResult.Success(networkController.registerAccountResponse(e164 = null)) }

    val result = numberlessRepository.registerAccountWithoutPhoneNumber(receiptCredentialPresentation = mockk(relaxed = true))

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat(networkController.lastRegisterAccountRequest?.pniPreKeys).isNull()
    assertThat(networkController.lastRegisterAccountRequest?.pniRegistrationId).isNull()
  }

  // ==================== registerAsLinkedDevice ====================

  @Test
  fun `provisioning advertises history transfer only when enabled and clean`() = runTest {
    for (enabled in listOf(false, true)) {
      for (clean in listOf(false, true)) {
        storageController.preExistingRegistrationData = if (clean) null else preExistingRegistrationData("+15551234567")
        var advertised: Boolean? = null
        networkController.onStartLinkDeviceProvisioning = {
          advertised = it
          emptyFlow()
        }

        linkingRepository(enabled).startLinkDeviceProvisioning().toList()

        assertThat(advertised).isEqualTo(enabled && clean)
      }
    }
  }

  @Test
  fun `disabled history transfer still commits a basic secondary account and ignores an unsolicited backup key`() = runTest {
    assertLinkedRegistrationHistory(enabled = false, clean = true)
  }

  @Test
  fun `enabled history transfer retains a backup key on a clean start`() = runTest {
    assertLinkedRegistrationHistory(enabled = true, clean = true)
  }

  @Test
  fun `enabled history transfer does not import over existing registration`() = runTest {
    assertLinkedRegistrationHistory(enabled = true, clean = false)
  }

  private suspend fun assertLinkedRegistrationHistory(enabled: Boolean, clean: Boolean) {
    storageController.preExistingRegistrationData = if (clean) null else preExistingRegistrationData("+15551234567")
    val message = linkProvisioningMessage(
      phoneNumberData = NetworkController.LinkDeviceProvisioningMessage.PhoneNumberData(
        e164 = "+15551234567",
        pni = PNI.from(UUID.randomUUID()).toString(),
        pniIdentityKeyPair = IdentityKeyPair.generate(),
      ),
      ephemeralBackupKey = ByteArray(32) { 1 }.toByteString(),
      mediaRootBackupKey = ByteArray(32) { 2 }.toByteString(),
    )
    var linkedCallbackCount = 0
    networkController.onRegisterAsLinkedDevice = { RequestResult.Success(LinkDeviceResponse(deviceId = 2)) }
    networkController.onLinkedDeviceRegisteredHandler = { linkedCallbackCount++ }

    val result = linkingRepository(enabled).registerAsLinkedDevice(message, "Secondary")

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat((result as RequestResult.Success).result.hasLinkAndSyncBackup).isEqualTo(enabled && clean)
    assertThat(storageController.committedData?.accountDataCommitted).isEqualTo(true)
    val account = storageController.committedData!!.accountData!!
    assertThat(account.aci).isEqualTo(message.aci)
    assertThat(account.aciIdentityKeyPair).isEqualTo(message.aciIdentityKeyPair.serialize().toByteString())
    assertThat(account.linkedDeviceData?.deviceId).isEqualTo(2)
    assertThat(account.linkedDeviceData?.ephemeralBackupKey).isEqualTo(message.ephemeralBackupKey.takeIf { enabled && clean })
    assertThat(account.linkedDeviceData?.mediaRootBackupKey).isEqualTo(message.mediaRootBackupKey)
    assertThat(linkedCallbackCount).isEqualTo(1)
  }

  @Test
  fun `disabled history transfer cannot resume an archive wait or restore from persisted metadata`() = runTest {
    storePendingHistoryKey()

    assertThat(repository.awaitLinkAndSyncArchive()).isEqualTo(LinkAndSyncWaitResult.ContinueWithoutBackup)
    assertThat(repository.restoreLinkAndSyncBackup().toList()).isEqualTo(listOf(LinkAndSyncProgress.Waiting, LinkAndSyncProgress.Complete))
  }

  @Test
  fun `enabled history transfer can still await a persisted archive`() = runTest {
    storePendingHistoryKey()
    val archive = LinkAndSyncWaitResult.ArchiveAvailable(cdn = 3, key = "archive-key")
    networkController.onAwaitLinkAndSyncArchive = { archive }

    assertThat(linkingRepository(enabled = true).awaitLinkAndSyncArchive()).isEqualTo(archive)
  }

  private suspend fun storePendingHistoryKey() {
    storageController.updateInProgressRegistrationData {
      accountData = AccountData(linkedDeviceData = LinkedDeviceData(ephemeralBackupKey = ByteArray(32) { 1 }.toByteString()))
    }
  }

  private fun linkingRepository(enabled: Boolean) = RegistrationRepository(
    context = mockk<Context>(relaxed = true),
    networkController = networkController,
    storageController = storageController,
    isLinkAndSyncAvailable = enabled,
    signalLoginPurchaseApi = OneTimePurchaseApi.Empty,
  )

  @Test
  fun `registerAsLinkedDevice advertises the optional phone number capability when the primary has no phone number`() = runTest {
    networkController.onRegisterAsLinkedDevice = { RequestResult.NonSuccess(RegisterAsLinkedDeviceError.MissingCapability) }

    numberlessRepository.registerAsLinkedDevice(linkProvisioningMessage(phoneNumberData = null), "Device")

    assertThat(networkController.lastRegisterAsLinkedDeviceAttributes?.capabilities?.optionalPhoneNumber).isEqualTo(true)
  }

  @Test
  fun `registerAsLinkedDevice does not advertise the optional phone number capability when the primary has a phone number`() = runTest {
    networkController.onRegisterAsLinkedDevice = { RequestResult.NonSuccess(RegisterAsLinkedDeviceError.MissingCapability) }

    val phoneNumberData = NetworkController.LinkDeviceProvisioningMessage.PhoneNumberData(
      e164 = "+15551234567",
      pni = PNI.from(UUID.randomUUID()).toString(),
      pniIdentityKeyPair = IdentityKeyPair.generate()
    )
    numberlessRepository.registerAsLinkedDevice(linkProvisioningMessage(phoneNumberData = phoneNumberData), "Device")

    assertThat(networkController.lastRegisterAsLinkedDeviceAttributes?.capabilities?.optionalPhoneNumber).isEqualTo(false)
  }

  // ==================== registerAccountWithSession ====================

  @Test
  fun `registerAccountWithSession re-uses the pre-existing key material when re-registering`() = runTest {
    networkController.onRegisterAccount = { request -> RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true)) }
    val preExisting = preExistingRegistrationData("+15551234567")

    val result = repository.registerAccountWithSession(
      e164 = "+15551234567",
      sessionId = "session-id",
      preExistingRegistrationData = preExisting
    )

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    assertThat((result as RequestResult.Success).result.keyMaterial.accountEntropyPool.value).isEqualTo(preExisting.aep.value)

    val committed = storageController.committedData
    assertThat(committed).isNotNull()
    assertThat(committed!!.accountEntropyPool).isEqualTo(preExisting.aep.value)

    val accountData = committed.accountData
    assertThat(accountData).isNotNull()
    assertThat(accountData!!.aciIdentityKeyPair).isEqualTo(preExisting.aciIdentityKeyPair.serialize().toByteString())
    assertThat(accountData.pniIdentityKeyPair).isEqualTo(preExisting.pniIdentityKeyPair.serialize().toByteString())
  }

  @Test
  fun `registerAccountWithSession generates fresh key material when there is no pre-existing registration`() = runTest {
    networkController.onRegisterAccount = { request -> RequestResult.Success(networkController.registerAccountResponse(request.e164)) }

    repository.registerAccountWithSession(e164 = "+15551234567", sessionId = "session-id")

    val committed = storageController.committedData
    assertThat(committed).isNotNull()
    assertThat(committed!!.accountEntropyPool.isNotEmpty()).isTrue()
    assertThat(committed.accountEntropyPool).isNotEqualTo(aep.value)
  }

  private fun linkProvisioningMessage(
    phoneNumberData: NetworkController.LinkDeviceProvisioningMessage.PhoneNumberData?,
    ephemeralBackupKey: okio.ByteString? = null,
    mediaRootBackupKey: okio.ByteString? = null,
  ): NetworkController.LinkDeviceProvisioningMessage {
    return NetworkController.LinkDeviceProvisioningMessage(
      provisioningCode = "code",
      aci = ACI.from(UUID.randomUUID()).toString(),
      aciIdentityKeyPair = IdentityKeyPair.generate(),
      phoneNumberData = phoneNumberData,
      profileKey = ByteArray(32),
      ephemeralBackupKey = ephemeralBackupKey,
      accountEntropyPool = aep.value,
      mediaRootBackupKey = mediaRootBackupKey,
      readReceipts = null,
      authCredentialSalt = null
    )
  }

  private fun preExistingRegistrationData(e164: String): PreExistingRegistrationData {
    return PreExistingRegistrationData(
      e164 = e164,
      aci = ACI.from(UUID.randomUUID()),
      pni = PNI.from(UUID.randomUUID()),
      servicePassword = "service-password",
      aep = AccountEntropyPool.generate(),
      registrationLockEnabled = false,
      unrestrictedUnidentifiedAccess = false,
      aciIdentityKeyPair = IdentityKeyPair.generate(),
      pniIdentityKeyPair = IdentityKeyPair.generate()
    )
  }
}
