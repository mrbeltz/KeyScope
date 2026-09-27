package com.jonny.r5monitor

import com.jonny.r5monitor.ccapi.CcapiParse
import com.jonny.r5monitor.ccapi.Endpoints
import com.jonny.r5monitor.ccapi.Paths
import com.jonny.r5monitor.ccapi.SettingFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcapiParseTest {

    @Test
    fun endpointsComeFromAbsoluteUrls() {
        val body = """
            {"ver100":[
              {"path":"http://192.168.1.2:8080/ccapi/ver100/shooting/liveview/scroll","get":true,"put":false,"post":false,"delete":true},
              {"path":"http://192.168.1.2:8080/ccapi/ver100/shooting/control/recbutton","get":false,"put":false,"post":true,"delete":false}
            ],
             "ver110":[
              {"path":"http://192.168.1.2:8080/ccapi/ver110/devicestatus/batterylist","get":true,"put":false,"post":false,"delete":false}
            ]}
        """.trimIndent()
        val e = Endpoints.parse(body)
        assertEquals(3, e.count)
        assertTrue(e.has(Paths.LIVEVIEW_SCROLL, "GET"))
        assertTrue(e.has(Paths.LIVEVIEW_SCROLL, "DELETE"))
        assertFalse(e.has(Paths.LIVEVIEW_SCROLL, "POST"))
        assertTrue(e.has(Paths.REC_BUTTON, "POST"))
        assertFalse(e.has(Paths.REC_BUTTON, "GET"))
        assertTrue(e.has(Paths.BATTERY_LIST, "GET"))
        assertFalse(e.has(Paths.SHUTTER, "POST"))
    }

    @Test
    fun unknownEndpointsAllowEverything() {
        assertTrue(Endpoints.UNKNOWN.has(Paths.SHUTTER, "POST"))
    }

    @Test
    fun settingsKeepValuesAndOptionsAndSkipNestedOnes() {
        val json = JSONObject(
            """
            {"av":{"value":"f4.0","ability":["f2.8","f4.0","f5.6"]},
             "iso":{"value":"auto","ability":["auto","100","200"]},
             "colortemperature":{"value":5200,"ability":{"min":2500,"max":10000,"step":100}},
             "wbshift":{"value":{"ba":0,"mg":0},"ability":{}},
             "battery":{"level":"full"}}
            """
        )
        val s = CcapiParse.settings(json)
        assertEquals(setOf("av", "iso", "colortemperature"), s.keys)
        assertEquals("f4.0", s.getValue("av").value)
        assertEquals(listOf("f2.8", "f4.0", "f5.6"), s.getValue("av").options)
        assertEquals("5200", s.getValue("colortemperature").value)
        assertTrue(s.getValue("colortemperature").options.isEmpty())
    }

    @Test
    fun batteryInBothShapes() {
        val v100 = CcapiParse.battery(JSONObject("""{"name":"LP-E6NH","kind":"battery","level":"half","quality":"good"}"""))!!
        assertEquals(50, v100.percent)
        assertEquals("Half", v100.label)
        val v110 = CcapiParse.battery(JSONObject("""{"batterylist":[{"position":"camera","name":"LP-E6NH","level":"82"}]}"""))!!
        assertEquals(82, v110.percent)
        assertEquals("82%", v110.label)
        assertNull(CcapiParse.battery(JSONObject("""{"av":{"value":"f4.0"}}""")))
    }

    @Test
    fun storageReadsFreeSpace() {
        val list = CcapiParse.storage(
            JSONObject("""{"storagelist":[{"name":"card1","maxsize":128000000000,"spacesize":64000000000}]}""")
        )
        assertEquals(1, list.size)
        assertEquals(64_000_000_000L, list[0].freeBytes)
    }

    @Test
    fun recordingState() {
        assertEquals(true, CcapiParse.recording(JSONObject("""{"status":"start"}""")))
        assertEquals(false, CcapiParse.recording(JSONObject("""{"recbutton":{"status":"stop"}}""")))
        assertEquals(true, CcapiParse.recording(JSONObject("""{"recbutton":"start"}""")))
        assertNull(CcapiParse.recording(JSONObject("""{"av":{"value":"f4.0"}}""")))
    }

    @Test
    fun errorMessage() {
        assertEquals("Device busy", CcapiParse.errorMessage("""{"message":"Device busy"}"""))
        assertNull(CcapiParse.errorMessage("<html>"))
    }

    @Test
    fun formatsLikeTheCameraScreen() {
        assertEquals("±0", SettingFormat.exposure("+0_0"))
        assertEquals("+1 1/3", SettingFormat.exposure("+1_1/3"))
        assertEquals("-2/3", SettingFormat.exposure("-0_2/3"))
        assertEquals("-2", SettingFormat.exposure("-2_0"))
        assertEquals("F4.0", SettingFormat.value("av", "f4.0"))
        assertEquals("AWB", SettingFormat.value("wb", "auto"))
        assertEquals("5200K", SettingFormat.value("colortemperature", "5200"))
        assertEquals("1/125", SettingFormat.value("tv", "1/125"))
    }
}
