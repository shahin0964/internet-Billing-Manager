package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.IspDatabase
import com.example.data.database.SmsDatabase
import com.example.data.model.IspPackageEntity
import com.example.data.repository.IspRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserPartitioningRobolectricTest {

    @Test
    fun `different users have distinct database names`() {
        val nameA = IspDatabase.getDatabaseNameForUser("101")
        val nameB = IspDatabase.getDatabaseNameForUser("202")
        val guestName = IspDatabase.getDatabaseNameForUser(null)

        assertNotEquals(nameA, nameB)
        assertNotEquals(nameA, guestName)
        assertNotEquals(nameB, guestName)
        assertTrue(nameA.startsWith("isp_user_101_"))
        assertTrue(nameB.startsWith("isp_user_202_"))
        assertEquals("isp_control_center_guest.db", guestName)
    }

    @Test
    fun `different users have distinct sms database names`() {
        val nameA = SmsDatabase.getDatabaseNameForUser("101")
        val nameB = SmsDatabase.getDatabaseNameForUser("202")
        val guestName = SmsDatabase.getDatabaseNameForUser(null)

        assertNotEquals(nameA, nameB)
        assertNotEquals(nameA, guestName)
        assertNotEquals(nameB, guestName)
        assertTrue(nameA.startsWith("isp_sms_user_101_"))
        assertTrue(nameB.startsWith("isp_sms_user_202_"))
        assertEquals("isp_sms_features_guest.db", guestName)
    }

    @Test
    fun `user A records are completely isolated from user B`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val userA = "tenant_alpha"
        val userB = "tenant_beta"

        val repoA = IspRepository.create(context, userA)
        val repoB = IspRepository.create(context, userB)

        // Ensure clean state
        repoA.db.packageDao().getAllPackagesList().forEach { repoA.db.packageDao().deletePackage(it) }
        repoB.db.packageDao().getAllPackagesList().forEach { repoB.db.packageDao().deletePackage(it) }

        // Insert package only in User A's database
        val pkgA = IspPackageEntity(
            id = 8881L,
            name = "Alpha 50Mbps Plan",
            speedMbps = 50,
            monthlyPrice = 1200.0
        )
        repoA.db.packageDao().insertPackage(pkgA)

        // Verify User A has the package
        val listA = repoA.db.packageDao().getAllPackagesList()
        assertTrue("User A must have the inserted package", listA.any { it.name == "Alpha 50Mbps Plan" })

        // Verify User B DOES NOT have the package (isolated physical database)
        val listB = repoB.db.packageDao().getAllPackagesList()
        assertTrue("User B's database must not contain User A's package", listB.none { it.name == "Alpha 50Mbps Plan" })

        // Cleanup
        IspDatabase.closeDatabase(userA)
        IspDatabase.closeDatabase(userB)
    }
}
