package cn.edu.bistu.kebiao.importer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedHostPolicyTest {
    @Test
    fun allowsOnlyExpectedHttpsHosts() {
        assertTrue(AllowedHostPolicy.isAllowed("https://jwxt.bistu.edu.cn/jwapp/"))
        assertTrue(AllowedHostPolicy.isAllowed("https://wxjw.bistu.edu.cn/authserver/login"))
        assertFalse(AllowedHostPolicy.isAllowed("http://jwxt.bistu.edu.cn/"))
        assertFalse(AllowedHostPolicy.isAllowed("https://jwxt.bistu.edu.cn.evil.example/"))
        assertFalse(AllowedHostPolicy.isAllowed("https://example.com/"))
    }
}

